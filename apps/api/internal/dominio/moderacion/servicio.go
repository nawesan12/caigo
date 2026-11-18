package moderacion

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"strconv"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/ia"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Servicio de moderación. Un Servicio nil (o sin IA) sigue moderando con el filtro: lo dudoso va
// directo a la cola del equipo.
type Servicio struct {
	App *db.Pool
	IA  *Clasificador
	Log *slog.Logger
	// Cuánto espera un plan a la IA (5 s). Pasado eso se publica y queda para revisar.
	EsperaPlan time.Duration
}

// Nuevo arma el servicio. cliente nil: sin IA.
func Nuevo(app *db.Pool, cliente *ia.Cliente, log *slog.Logger) *Servicio {
	return &Servicio{App: app, IA: &Clasificador{IA: cliente}, Log: log}
}

func (s *Servicio) log() *slog.Logger {
	if s != nil && s.Log != nil {
		return s.Log
	}
	return slog.Default()
}

func (s *Servicio) clasificador() *Clasificador {
	if s == nil {
		return nil
	}
	return s.IA
}

// Estados de un objeto (columna moderacion de planes, mensajes y privados).
const (
	EstadoOK        = "ok"
	EstadoPendiente = "pendiente"
	EstadoRevisar   = "revisar"
	EstadoOculto    = "oculto"
)

// Orígenes de una entrada de la cola.
const (
	OrigenFiltro = "filtro"
	OrigenIA     = "ia"
	OrigenSinIA  = "sin_ia"
)

// Resultado de revisar un plan antes de guardarlo.
type Resultado struct {
	// Estado: ok | revisar (se publica y va a la cola) | bloquear (no se guarda).
	Estado    string
	Origen    string
	Categoria string
	Motivo    string
}

// Bloqueado es el 422 de lo que no sale.
func Bloqueado(tipo, motivo string) *problema.Problema {
	que := "Ese mensaje no sale"
	if tipo == TipoPlan {
		que = "Tu plan no se puede publicar"
	}
	detalle := que + ": va contra las normas de Caigo"
	if motivo != "" {
		detalle += " (" + strings.ToLower(motivo[:1]) + motivo[1:] + ")"
	}
	return problema.NoProcesable("contenido_bloqueado", detalle+".")
}

// TextoPlan es lo que se revisa de un plan.
func TextoPlan(titulo, descripcion string) string {
	return strings.TrimSpace(strings.TrimSpace(titulo) + "\n\n" + strings.TrimSpace(descripcion))
}

// RevisarPlan decide sobre el texto de un plan. Espera a la IA hasta EsperaPlan; si no contesta (o
// está apagada) el plan se publica y queda para revisar. No llamar dentro de una transacción.
func (s *Servicio) RevisarPlan(ctx context.Context, texto string) Resultado {
	v := Filtrar(texto)
	switch v.Decision {
	case OK:
		return Resultado{Estado: EstadoOK}
	case Bloquear:
		return Resultado{Estado: Bloquear, Origen: OrigenFiltro, Categoria: v.Categoria, Motivo: v.Motivo}
	}
	espera := 5 * time.Second
	if s != nil && s.EsperaPlan > 0 {
		espera = s.EsperaPlan
	}
	ctx, cancel := context.WithTimeout(ctx, espera)
	defer cancel()
	c, err := s.clasificador().Clasificar(ctx, TipoPlan, texto)
	if err != nil {
		if !errors.Is(err, ia.ErrApagada) {
			s.log().Warn("moderación: la IA no respondió por un plan", "error", err)
		}
		return Resultado{Estado: EstadoRevisar, Origen: OrigenSinIA, Categoria: v.Categoria, Motivo: v.Motivo}
	}
	return desdeIA(c)
}

func desdeIA(c Clasificacion) Resultado {
	r := Resultado{Origen: OrigenIA, Categoria: c.Categoria, Motivo: c.Motivo}
	switch c.Decision {
	case IABloquear:
		r.Estado = Bloquear
	case IARevisar:
		r.Estado = EstadoRevisar
	default:
		r.Estado = EstadoOK
	}
	return r
}

// Entrada de la cola del equipo.
type Entrada struct {
	Tipo      string
	ObjetoID  string // vacío: no se publicó (lo frenó el filtro)
	PlanID    string
	UsuarioID string
	Texto     string
	Origen    string
	Categoria string
	Motivo    string
	// revision | oculto | bloqueado
	Estado string
}

// AnotarTx suma la entrada a la cola en tx. Si el objeto ya tiene una abierta (otra edición del mismo
// plan), la actualiza en vez de duplicarla.
func AnotarTx(ctx context.Context, tx pgx.Tx, e Entrada) error {
	var objeto, plan *string
	if e.ObjetoID != "" {
		objeto = &e.ObjetoID
	}
	if e.PlanID != "" {
		plan = &e.PlanID
	}
	if e.Categoria == "" {
		e.Categoria = "otro"
	}
	_, err := tx.Exec(ctx, `INSERT INTO moderaciones (tipo, objeto_id, plan_id, usuario_id, texto, origen, categoria, motivo, estado)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		ON CONFLICT (tipo, objeto_id) WHERE resuelta_en IS NULL AND objeto_id IS NOT NULL
		DO UPDATE SET texto = excluded.texto, origen = excluded.origen, categoria = excluded.categoria,
			motivo = excluded.motivo, estado = excluded.estado`,
		e.Tipo, objeto, plan, e.UsuarioID, recortar(e.Texto, 3000), e.Origen, recortar(e.Categoria, 30),
		recortar(e.Motivo, 300), e.Estado)
	return err
}

// AnotarBloqueo deja en la cola algo que el filtro no dejó salir, cuando igual lo tiene que ver una
// persona: alguien que dice ser menor de edad (la cuenta misma puede ser el problema). Lo demás que
// frena el filtro no se guarda (no se publicó y no hace falta guardar el texto).
func (s *Servicio) AnotarBloqueo(ctx context.Context, usuarioID, tipo, planID, texto string, v Veredicto) {
	if s == nil || s.App == nil || v.Categoria != "menores" {
		return
	}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := AnotarTx(ctx, tx, Entrada{Tipo: tipo, PlanID: planID, UsuarioID: usuarioID, Texto: texto,
			Origen: OrigenFiltro, Categoria: v.Categoria, Motivo: v.Motivo, Estado: "bloqueado"}); err != nil {
			return err
		}
		return outbox.Publicar(ctx, tx, EventoUrgente, map[string]string{"categoria": v.Categoria})
	})
	if err != nil {
		s.log().Warn("moderación: no se pudo anotar un bloqueo", "error", err)
	}
}

// Eventos del outbox.
const (
	// EventoRevisar: un mensaje dudoso que el worker manda a la IA.
	EventoRevisar = "moderacion.revisar"
	// EventoOculto: avisarle al autor que se ocultó algo suyo (push).
	EventoOculto = "moderacion.oculto"
	// EventoUrgente: algo que el equipo tiene que mirar ya (posible menor de edad).
	EventoUrgente = "moderacion.urgente"
)

// revisarDatos es el evento moderacion.revisar: el mensaje y lo que vio el filtro.
type revisarDatos struct {
	Tipo      string `json:"tipo"`
	ID        int64  `json:"id"`
	Categoria string `json:"categoria"`
	Motivo    string `json:"motivo"`
}

// PublicarRevision encola la revisión con IA de un mensaje dudoso (en la tx que lo guardó).
func PublicarRevision(ctx context.Context, tx pgx.Tx, tipo string, id int64, v Veredicto) error {
	return outbox.Publicar(ctx, tx, EventoRevisar, revisarDatos{Tipo: tipo, ID: id, Categoria: v.Categoria, Motivo: v.Motivo})
}

// EstadoMensaje: con qué estado se guarda un mensaje según el filtro (el bloqueo se corta antes).
func EstadoMensaje(v Veredicto) string {
	if v.Decision == Dudoso {
		return EstadoPendiente
	}
	return EstadoOK
}

// tabla de cada tipo de mensaje.
func tabla(tipo string) (string, string, bool) {
	switch tipo {
	case TipoMensaje:
		return "mensajes", "usuario_id", true
	case TipoPrivado:
		return "privados", "de_id", true
	}
	return "", "", false
}

// Manejador del worker para moderacion.revisar: le pregunta a la IA por el mensaje y aplica lo que
// diga. Idempotente: un mensaje que ya no está pendiente no se vuelve a mandar. Si la IA falla por algo
// pasajero se reintenta un par de veces; después (o si está apagada) va a la cola del equipo.
func (s *Servicio) Manejador(ctx context.Context, ev outbox.Evento) error {
	var d revisarDatos
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	t, autorCol, ok := tabla(d.Tipo)
	if !ok {
		return nil
	}
	var texto, estado string
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `SELECT texto, moderacion FROM `+t+` WHERE id = $1`, d.ID).Scan(&texto, &estado)
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return nil
	}
	if err != nil {
		return err
	}
	if estado != EstadoPendiente {
		return nil
	}
	// Fuera de toda transacción: la IA puede tardar.
	c, err := s.clasificador().Clasificar(ctx, d.Tipo, texto)
	var r Resultado
	if err != nil {
		if ia.Transitorio(err) && ev.Intentos < 2 {
			return err
		}
		if !errors.Is(err, ia.ErrApagada) {
			s.log().Warn("moderación: la IA no respondió por un mensaje", "tipo", d.Tipo, "id", d.ID, "error", err)
		}
		r = Resultado{Estado: EstadoRevisar, Origen: OrigenSinIA, Categoria: d.Categoria, Motivo: d.Motivo}
	} else {
		r = desdeIA(c)
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var autor string
		var plan *string
		planCol := "plan_id::text"
		if d.Tipo == TipoPrivado {
			planCol = "NULL::text"
		}
		err := tx.QueryRow(ctx, `SELECT `+autorCol+`::text, `+planCol+` FROM `+t+` WHERE id = $1 AND moderacion = 'pendiente' FOR UPDATE`,
			d.ID).Scan(&autor, &plan)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil // otro worker ya lo resolvió
		}
		if err != nil {
			return err
		}
		e := Entrada{Tipo: d.Tipo, ObjetoID: strconv.FormatInt(d.ID, 10), UsuarioID: autor, Texto: texto,
			Origen: r.Origen, Categoria: r.Categoria, Motivo: r.Motivo}
		if plan != nil {
			e.PlanID = *plan
		}
		switch r.Estado {
		case EstadoOK:
			return CambiarTx(ctx, tx, d.Tipo, e.ObjetoID, EstadoOK)
		case EstadoRevisar:
			e.Estado = "revision"
			if err := AnotarTx(ctx, tx, e); err != nil {
				return err
			}
			return CambiarTx(ctx, tx, d.Tipo, e.ObjetoID, EstadoRevisar)
		default: // bloquear: se oculta, avisa al autor y queda en la cola por si fue un error
			e.Estado = EstadoOculto
			if err := AnotarTx(ctx, tx, e); err != nil {
				return err
			}
			return CambiarTx(ctx, tx, d.Tipo, e.ObjetoID, EstadoOculto)
		}
	})
}

// CambiarTx cambia el estado de moderación de un objeto. Al ocultarlo avisa al autor (push) y, si es
// un mensaje, avisa en tiempo real para que desaparezca de las pantallas abiertas.
func CambiarTx(ctx context.Context, tx pgx.Tx, tipo, objetoID, estado string) error {
	switch tipo {
	case TipoPlan:
		var org, titulo string
		err := tx.QueryRow(ctx, `UPDATE planes SET moderacion = $2 WHERE id::text = $1 RETURNING organizador_id::text, titulo`,
			objetoID, estado).Scan(&org, &titulo)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		if err != nil || estado != EstadoOculto {
			return err
		}
		return outbox.Publicar(ctx, tx, EventoOculto, map[string]string{"usuarioId": org, "tipo": tipo, "planId": objetoID, "titulo": titulo})
	case TipoMensaje, TipoPrivado:
		t, autorCol, _ := tabla(tipo)
		otro := "NULL::text"
		if tipo == TipoMensaje {
			otro = "plan_id::text"
		} else {
			otro = "a_id::text"
		}
		var autor *string
		var donde string
		err := tx.QueryRow(ctx, `UPDATE `+t+` SET moderacion = $2 WHERE id::text = $1 RETURNING `+autorCol+`::text, `+otro,
			objetoID, estado).Scan(&autor, &donde)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		aviso, _ := json.Marshal(map[string]any{"tipo": "moderado_" + tipo, "id": objetoID})
		if _, err := tx.Exec(ctx, `SELECT pg_notify('caigo_tiempo_real', $1)`, string(aviso)); err != nil {
			return err
		}
		if estado != EstadoOculto || autor == nil {
			return nil
		}
		datos := map[string]string{"usuarioId": *autor, "tipo": tipo}
		if tipo == TipoMensaje {
			datos["planId"] = donde
		} else {
			datos["otroId"] = donde
		}
		return outbox.Publicar(ctx, tx, EventoOculto, datos)
	}
	return nil
}
