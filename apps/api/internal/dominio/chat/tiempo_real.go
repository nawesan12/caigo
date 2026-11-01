package chat

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"sync"
	"time"

	"github.com/coder/websocket"
	"github.com/jackc/pgx/v5"
)

// Canal de Postgres donde avisan los triggers de mensajes y privados.
const Canal = "caigo_tiempo_real"

// Evento que viaja por el WebSocket.
type Evento struct {
	Tipo    string   `json:"tipo"` // mensaje | privado | escribiendo | plan (cambió el plan o sus solicitudes)
	Mensaje *Mensaje `json:"mensaje,omitempty"`
	Privado *Privado `json:"privado,omitempty"`
	PlanID  string   `json:"planId,omitempty"`
	Quien   *Autor   `json:"quien,omitempty"`
	// "escribiendo" en un privado: id de quien escribe (viaja como "privado").
	EscribePrivado string `json:"-"`
}

// MarshalJSON: el "escribiendo" de un privado viaja como {"tipo":"escribiendo","privado":"<id>"}.
func (e Evento) MarshalJSON() ([]byte, error) {
	type plano Evento
	if e.Tipo == "escribiendo" && e.EscribePrivado != "" {
		return json.Marshal(struct {
			Tipo    string `json:"tipo"`
			Privado string `json:"privado"`
			Quien   *Autor `json:"quien,omitempty"`
		}{e.Tipo, e.EscribePrivado, e.Quien})
	}
	return json.Marshal(plano(e))
}

// Hub reparte los eventos a las conexiones abiertas de cada persona. Hay una sola API, así que
// alcanza con memoria; los avisos entre procesos (el worker) llegan por LISTEN/NOTIFY.
type Hub struct {
	Servicio *Servicio
	Log      *slog.Logger

	mu    sync.Mutex
	conns map[string]map[chan Evento]struct{}
}

func (h *Hub) log() *slog.Logger {
	if h.Log != nil {
		return h.Log
	}
	return slog.Default()
}

func (h *Hub) sumar(usuarioID string) chan Evento {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.conns == nil {
		h.conns = map[string]map[chan Evento]struct{}{}
	}
	if h.conns[usuarioID] == nil {
		h.conns[usuarioID] = map[chan Evento]struct{}{}
	}
	c := make(chan Evento, 32)
	h.conns[usuarioID][c] = struct{}{}
	return c
}

func (h *Hub) sacar(usuarioID string, c chan Evento) {
	h.mu.Lock()
	defer h.mu.Unlock()
	delete(h.conns[usuarioID], c)
	if len(h.conns[usuarioID]) == 0 {
		delete(h.conns, usuarioID)
	}
}

// Conectado dice si la persona tiene la app abierta (el worker no le manda push del chat).
func (h *Hub) Conectado(usuarioID string) bool {
	h.mu.Lock()
	defer h.mu.Unlock()
	return len(h.conns[usuarioID]) > 0
}

func (h *Hub) mandar(usuarioID string, ev Evento) {
	h.mu.Lock()
	defer h.mu.Unlock()
	for c := range h.conns[usuarioID] {
		select {
		case c <- ev:
		default: // una conexión lenta no frena a las demás: pierde el evento y recarga al volver
		}
	}
}

// Escuchar consume los NOTIFY de la base hasta que se cancela el contexto.
func (h *Hub) Escuchar(ctx context.Context) {
	h.Servicio.App.Escuchar(ctx, Canal, func(payload string) {
		var n struct {
			Tipo string `json:"tipo"`
			ID   int64  `json:"id"`
			Plan string `json:"plan"`
		}
		if json.Unmarshal([]byte(payload), &n) != nil {
			return
		}
		if n.Tipo == "plan" {
			if err := h.avisarPlan(ctx, n.Plan); err != nil {
				h.log().Warn("tiempo real: no se pudo avisar el plan", "plan", n.Plan, "error", err)
			}
			return
		}
		if err := h.repartir(ctx, n.Tipo, n.ID); err != nil {
			h.log().Warn("tiempo real: no se pudo repartir", "tipo", n.Tipo, "id", n.ID, "error", err)
		}
	})
}

func (h *Hub) repartir(ctx context.Context, tipo string, id int64) error {
	return h.Servicio.App.EnSistema(ctx, func(tx pgx.Tx) error {
		switch tipo {
		case "mensaje":
			m, err := escanearMensaje(tx.QueryRow(ctx, `
				SELECT m.id, m.plan_id, m.usuario_id, coalesce(u.nombre, ''), coalesce(u.fotos[1], ''), m.texto, m.creado_en
				FROM mensajes m LEFT JOIN usuarios u ON u.id = m.usuario_id WHERE m.id = $1`, id))
			if err != nil {
				return err
			}
			ids, err := aceptados(ctx, tx, m.PlanID)
			if err != nil {
				return err
			}
			for _, u := range ids {
				h.mandar(u, Evento{Tipo: "mensaje", Mensaje: &m})
			}
		case "privado":
			var p Privado
			if err := tx.QueryRow(ctx, `SELECT id, de_id, a_id, texto, creado_en FROM privados WHERE id = $1`, id).
				Scan(&p.ID, &p.DeID, &p.AID, &p.Texto, &p.CreadoEn); err != nil {
				return err
			}
			h.mandar(p.DeID, Evento{Tipo: "privado", Privado: &p})
			h.mandar(p.AID, Evento{Tipo: "privado", Privado: &p})
		}
		return nil
	})
}

// avisarPlan: cambió el plan o alguna de sus solicitudes (00016). Le llega al organizador y a todos
// los que pidieron sumarse (pendientes, aceptados o rechazados), que recargan lo que estén viendo.
func (h *Hub) avisarPlan(ctx context.Context, planID string) error {
	return h.Servicio.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT organizador_id FROM planes WHERE id = $1
			UNION SELECT usuario_id FROM solicitudes WHERE plan_id = $1`, planID)
		if err != nil {
			return err
		}
		ids, err := pgx.CollectRows(rows, pgx.RowTo[string])
		for _, u := range ids {
			h.mandar(u, Evento{Tipo: "plan", PlanID: planID})
		}
		return err
	})
}

func aceptados(ctx context.Context, tx pgx.Tx, planID string) ([]string, error) {
	rows, err := tx.Query(ctx, `SELECT usuario_id FROM solicitudes WHERE plan_id = $1 AND estado = 'aceptada'`, planID)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, pgx.RowTo[string])
}

// Conectar atiende el WebSocket de una persona ya autenticada. Del cliente sólo se aceptan avisos de
// "escribiendo" en un plan donde la persona está.
func (h *Hub) Conectar(w http.ResponseWriter, r *http.Request, usuarioID, nombre string, origenes []string) {
	c, err := websocket.Accept(w, r, &websocket.AcceptOptions{OriginPatterns: origenes})
	if err != nil {
		return
	}
	defer c.CloseNow()
	c.SetReadLimit(4 << 10)
	ctx, cancel := context.WithCancel(r.Context())
	defer cancel()
	eventos := h.sumar(usuarioID)
	defer h.sacar(usuarioID, eventos)

	go func() {
		defer cancel()
		var ultimo time.Time
		for {
			var in struct {
				Tipo    string `json:"tipo"`
				PlanID  string `json:"planId"`
				Privado string `json:"privado"`
			}
			_, b, err := c.Read(ctx)
			if err != nil {
				return
			}
			if json.Unmarshal(b, &in) != nil || in.Tipo != "escribiendo" || time.Since(ultimo) < 2*time.Second {
				continue
			}
			ultimo = time.Now()
			if in.Privado != "" {
				h.escribiendoPrivado(ctx, usuarioID, nombre, in.Privado)
				continue
			}
			_ = h.Servicio.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
				esta, abierto, err := participa(ctx, tx, in.PlanID, usuarioID, time.Now())
				if err != nil || !esta || !abierto {
					return err
				}
				ids, err := aceptados(ctx, tx, in.PlanID)
				for _, u := range ids {
					if u != usuarioID {
						h.mandar(u, Evento{Tipo: "escribiendo", PlanID: in.PlanID, Quien: &Autor{ID: usuarioID, Nombre: nombre}})
					}
				}
				return err
			})
		}
	}()

	latido := time.NewTicker(25 * time.Second)
	defer latido.Stop()
	for {
		select {
		case <-ctx.Done():
			c.Close(websocket.StatusNormalClosure, "")
			return
		case ev := <-eventos:
			b, _ := json.Marshal(ev)
			wctx, wcancel := context.WithTimeout(ctx, 10*time.Second)
			err := c.Write(wctx, websocket.MessageText, b)
			wcancel()
			if err != nil {
				return
			}
		case <-latido.C:
			pctx, pcancel := context.WithTimeout(ctx, 10*time.Second)
			err := c.Ping(pctx)
			pcancel()
			if err != nil {
				return
			}
		}
	}
}

// escribiendoPrivado avisa al otro que la persona le está escribiendo, si pueden hablar.
func (h *Hub) escribiendoPrivado(ctx context.Context, usuarioID, nombre, otroID string) {
	if otroID == usuarioID {
		return
	}
	var ok bool
	err := h.Servicio.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var err error
		ok, err = puedenHablar(ctx, tx, usuarioID, otroID)
		return err
	})
	if err == nil && ok {
		h.mandar(otroID, Evento{Tipo: "escribiendo", EscribePrivado: usuarioID, Quien: &Autor{ID: usuarioID, Nombre: nombre}})
	}
}
