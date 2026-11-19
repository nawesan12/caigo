// Package db es el único lugar que habla con el pool de Postgres. Todo acceso a datos de una persona
// pasa por EnUsuario, que abre una transacción y fija a la persona con set_config(..., true): el
// valor vive sólo en esa transacción, así que un pool compartido nunca filtra a una persona en otra.
// Un olvido devuelve vacío (RLS), nunca datos ajenos.
package db

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Pool es la conexión de un rol.
type Pool struct{ *pgxpool.Pool }

// Topes de un pool. Cero: el valor por defecto de pgx (o sin tope).
type Topes struct {
	// Conexiones como máximo (pgx usa max(4, CPUs): en el VPS de 2 vCPU, 4).
	MaxConns int32
	// Lo más que puede tardar una consulta (statement_timeout) y una transacción abierta sin hacer
	// nada (idle_in_transaction_session_timeout): una consulta lenta o colgada no retiene la conexión.
	Consulta, Ociosa time.Duration
}

// Para la API: 12 conexiones, consultas de hasta 10 s. Para el worker: 5, sin tope por consulta (los
// plazos y las pasadas en lote). Los dos con 15 s de transacción ociosa. Con el migrador y el admin
// entran holgados en los 40 de max_connections del VPS.
var (
	TopesAPI    = Topes{MaxConns: 12, Consulta: 10 * time.Second, Ociosa: 15 * time.Second}
	TopesWorker = Topes{MaxConns: 5, Ociosa: 15 * time.Second}
)

// Abrir conecta con la URL de un rol y verifica que responda.
func Abrir(ctx context.Context, url string, topes ...Topes) (*Pool, error) {
	cfg, err := pgxpool.ParseConfig(url)
	if err != nil {
		return nil, fmt.Errorf("url de la base: %w", err)
	}
	for _, t := range topes {
		if t.MaxConns > 0 {
			cfg.MaxConns = t.MaxConns
		}
		if t.Consulta > 0 {
			cfg.ConnConfig.RuntimeParams["statement_timeout"] = fmt.Sprint(t.Consulta.Milliseconds())
		}
		if t.Ociosa > 0 {
			cfg.ConnConfig.RuntimeParams["idle_in_transaction_session_timeout"] = fmt.Sprint(t.Ociosa.Milliseconds())
		}
	}
	p, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, err
	}
	if err := p.Ping(ctx); err != nil {
		p.Close()
		return nil, fmt.Errorf("la base no responde: %w", err)
	}
	return &Pool{p}, nil
}

// ErrSinUsuario: se pidió un acceso por persona sin persona.
var ErrSinUsuario = errors.New("acceso a datos sin usuario")

// EnUsuario corre fn en una transacción con la persona fijada. Si fn devuelve error, se revierte.
func (p *Pool) EnUsuario(ctx context.Context, usuarioID string, fn func(pgx.Tx) error) error {
	if usuarioID == "" {
		return ErrSinUsuario
	}
	return p.enTx(ctx, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, "SELECT set_config('app.usuario_id', $1, true)", usuarioID); err != nil {
			return err
		}
		return fn(tx)
	})
}

// EnSistema corre fn como el sistema (worker, webhooks, moderación): las políticas lo dejan ver
// todo. No es una barrera contra la app (el rol es el mismo), es una marca explícita en el código.
func (p *Pool) EnSistema(ctx context.Context, fn func(pgx.Tx) error) error {
	return p.enTx(ctx, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, "SELECT set_config('app.sistema', 'si', true)"); err != nil {
			return err
		}
		return fn(tx)
	})
}

// EnTx corre fn en una transacción sin persona: sólo para lo que no depende de una (el alta).
func (p *Pool) EnTx(ctx context.Context, fn func(pgx.Tx) error) error {
	return p.enTx(ctx, fn)
}

func (p *Pool) enTx(ctx context.Context, fn func(pgx.Tx) error) (err error) {
	tx, err := p.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() {
		if err != nil {
			_ = tx.Rollback(context.WithoutCancel(ctx))
		}
	}()
	if err = fn(tx); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

// Escuchar hace LISTEN en canal y llama a fn con cada aviso hasta que se cancela el contexto. Si se
// corta la conexión, vuelve a conectar (con una pausa) sin perder la suscripción.
func (p *Pool) Escuchar(ctx context.Context, canal string, fn func(payload string)) {
	for ctx.Err() == nil {
		err := p.escuchar(ctx, canal, fn)
		if ctx.Err() != nil {
			return
		}
		_ = err
		select {
		case <-ctx.Done():
		case <-time.After(2 * time.Second):
		}
	}
}

// escuchar usa una conexión propia, fuera del pool: LISTEN la retiene para siempre y le sacaría una a
// todos los pedidos. Sin statement_timeout (esperar avisos no es una consulta lenta).
func (p *Pool) escuchar(ctx context.Context, canal string, fn func(payload string)) error {
	cc := p.Config().ConnConfig.Copy()
	delete(cc.RuntimeParams, "statement_timeout")
	c, err := pgx.ConnectConfig(ctx, cc)
	if err != nil {
		return err
	}
	defer c.Close(context.WithoutCancel(ctx))
	if _, err := c.Exec(ctx, "LISTEN "+pgx.Identifier{canal}.Sanitize()); err != nil {
		return err
	}
	for {
		n, err := c.WaitForNotification(ctx)
		if err != nil {
			return err
		}
		fn(n.Payload)
	}
}

// EsUnico dice si err es una violación de un índice único (23505): dos pedidos a la vez que
// quieren lo mismo, o un dato que ya es de otra fila.
func EsUnico(err error) bool {
	var pe *pgconn.PgError
	return errors.As(err, &pe) && pe.Code == "23505"
}

// EsReferencia dice si err es una violación de una clave foránea (23503): el id apunta a algo que no
// existe.
func EsReferencia(err error) bool {
	var pe *pgconn.PgError
	return errors.As(err, &pe) && pe.Code == "23503"
}
