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
	Tipo    string   `json:"tipo"` // mensaje | privado | escribiendo
	Mensaje *Mensaje `json:"mensaje,omitempty"`
	Privado *Privado `json:"privado,omitempty"`
	PlanID  string   `json:"planId,omitempty"`
	Quien   *Autor   `json:"quien,omitempty"`
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
		}
		if json.Unmarshal([]byte(payload), &n) != nil {
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
				Tipo   string `json:"tipo"`
				PlanID string `json:"planId"`
			}
			_, b, err := c.Read(ctx)
			if err != nil {
				return
			}
			if json.Unmarshal(b, &in) != nil || in.Tipo != "escribiendo" || time.Since(ultimo) < 2*time.Second {
				continue
			}
			ultimo = time.Now()
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
