package outbox

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"math"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
)

// Evento es una fila del outbox tal como la recibe un manejador.
type Evento struct {
	ID       int64
	Tipo     string
	Datos    []byte
	Intentos int
}

// Manejador procesa un evento. Tiene que ser idempotente: la entrega es al menos una vez.
type Manejador func(context.Context, Evento) error

// MaxIntentos: después de esto el evento queda apartado (proximo_intento = infinity) con su error.
const MaxIntentos = 8

// Worker consume el outbox (como el sistema: los eventos no son de una persona).
type Worker struct {
	Pool        *db.Pool
	Manejadores map[string]Manejador
	Lote        int
	// Espera entre pasadas cuando no hay nada pendiente.
	Descanso time.Duration
	Log      *slog.Logger
}

// Correr procesa hasta que se cancela el contexto.
func (w *Worker) Correr(ctx context.Context) error {
	descanso := w.Descanso
	if descanso == 0 {
		descanso = 500 * time.Millisecond
	}
	for {
		n, err := w.Pasada(ctx)
		if err != nil && !errors.Is(err, context.Canceled) {
			w.log().Error("outbox: pasada fallida", "error", err)
		}
		if n > 0 && err == nil {
			continue
		}
		select {
		case <-ctx.Done():
			return nil
		case <-time.After(descanso):
		}
	}
}

// Toma: lo que un worker se reserva un evento mientras lo procesa. Si se cae a mitad de camino, otro
// lo vuelve a tomar después (la entrega es al menos una vez).
const Toma = 5 * time.Minute

// Pasada toma un lote de pendientes (SKIP LOCKED: varios workers no se pisan) y los procesa de a uno.
// La toma es una transacción corta que los reserva (proximo_intento = ahora + Toma); cada evento corre
// fuera de ella (los manejadores hablan por HTTP con APNs, FCM, Mercado Pago o el SMTP) y su resultado
// se guarda en su propia transacción: uno lento o fallido no traba ni deshace a los demás. Devuelve
// cuántos tomó.
func (w *Worker) Pasada(ctx context.Context) (int, error) {
	lote := w.Lote
	if lote == 0 {
		lote = 100
	}
	var eventos []Evento
	err := w.Pool.EnSistema(ctx, func(tx pgx.Tx) error {
		filas, err := tx.Query(ctx, `
			SELECT id, tipo, datos, intentos FROM outbox
			WHERE publicado_en IS NULL AND proximo_intento <= now()
			ORDER BY proximo_intento, id
			LIMIT $1
			FOR UPDATE SKIP LOCKED`, lote)
		if err != nil {
			return err
		}
		if eventos, err = pgx.CollectRows(filas, pgx.RowToStructByPos[Evento]); err != nil {
			return err
		}
		ids := make([]int64, len(eventos))
		for i, ev := range eventos {
			ids[i] = ev.ID
		}
		_, err = tx.Exec(ctx, `UPDATE outbox SET proximo_intento = now() + $2::interval WHERE id = ANY ($1)`, ids, Toma.String())
		return err
	})
	if err != nil {
		return 0, err
	}
	var errs []error
	for _, ev := range eventos {
		if ctx.Err() != nil {
			break // lo que quedó vuelve a estar disponible cuando vence la toma
		}
		res := w.correr(ctx, ev)
		if err := w.Pool.EnSistema(ctx, func(tx pgx.Tx) error { return w.guardar(ctx, tx, ev, res) }); err != nil {
			errs = append(errs, err)
		}
	}
	return len(eventos), errors.Join(errs...)
}

// guardar deja el resultado de un evento: publicado, a reintentar o apartado.
func (w *Worker) guardar(ctx context.Context, tx pgx.Tx, ev Evento, err error) error {
	if err == nil {
		_, e := tx.Exec(ctx, `UPDATE outbox SET publicado_en = now(), intentos = intentos + 1, ultimo_error = NULL WHERE id = $1`, ev.ID)
		return e
	}
	intentos := ev.Intentos + 1
	w.log().Warn("outbox: evento fallido", "id", ev.ID, "tipo", ev.Tipo, "intento", intentos, "error", err)
	if intentos >= MaxIntentos {
		// Apartado: ya no se reintenta solo. Queda en el log como error y el equipo lo ve en
		// GET /v1/equipo/apartados (un reembolso que no salió hay que hacerlo a mano).
		w.log().Error("outbox: evento apartado tras agotar los reintentos", "id", ev.ID, "tipo", ev.Tipo,
			"datos", string(ev.Datos), "error", err)
		_, e := tx.Exec(ctx, `UPDATE outbox SET intentos = $2, proximo_intento = 'infinity', ultimo_error = $3 WHERE id = $1`, ev.ID, intentos, err.Error())
		return e
	}
	_, e := tx.Exec(ctx, `UPDATE outbox SET intentos = $2, proximo_intento = now() + $3::interval, ultimo_error = $4 WHERE id = $1`,
		ev.ID, intentos, Espera(intentos).String(), err.Error())
	return e
}

// correr llama al manejador del tipo. Un panic cuenta como error del evento, no tira el worker.
func (w *Worker) correr(ctx context.Context, ev Evento) (err error) {
	m, ok := w.Manejadores[ev.Tipo]
	if !ok {
		return nil // nadie escucha este tipo todavía: se da por publicado
	}
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("panic: %v", r)
		}
	}()
	return m(ctx, ev)
}

// Espera creciente: 2, 4, 8… segundos, con techo de una hora.
func Espera(intentos int) time.Duration {
	s := math.Min(math.Pow(2, float64(intentos)), 3600)
	return time.Duration(s) * time.Second
}

func (w *Worker) log() *slog.Logger {
	if w.Log != nil {
		return w.Log
	}
	return slog.Default()
}
