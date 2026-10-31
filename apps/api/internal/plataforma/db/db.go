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
	"github.com/jackc/pgx/v5/pgxpool"
)

// Pool es la conexión de un rol.
type Pool struct{ *pgxpool.Pool }

// Abrir conecta con la URL de un rol y verifica que responda.
func Abrir(ctx context.Context, url string) (*Pool, error) {
	cfg, err := pgxpool.ParseConfig(url)
	if err != nil {
		return nil, fmt.Errorf("url de la base: %w", err)
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

func (p *Pool) escuchar(ctx context.Context, canal string, fn func(payload string)) error {
	c, err := p.Acquire(ctx)
	if err != nil {
		return err
	}
	defer c.Release()
	if _, err := c.Exec(ctx, "LISTEN "+pgx.Identifier{canal}.Sanitize()); err != nil {
		return err
	}
	for {
		n, err := c.Conn().WaitForNotification(ctx)
		if err != nil {
			// La conexión quedó en un estado desconocido: que el pool no la reuse.
			c.Conn().Close(context.WithoutCancel(ctx))
			return err
		}
		fn(n.Payload)
	}
}
