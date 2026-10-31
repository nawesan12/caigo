// Package sesion resuelve quién hace cada pedido: verifica el token de Clerk (header Authorization
// en las apps, cookie __session en la web) y lo traduce a la persona de Caigo. La primera vez que
// alguien entra se le crea su fila en usuarios con el nombre que tenga en Clerk.
package sesion

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"strings"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/clerk"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Verificador es lo que se usa de Clerk (en los tests, el servidor falso).
type Verificador interface {
	Verificar(ctx context.Context, token string) (string, error)
	Usuario(ctx context.Context, id string) (clerk.Usuario, error)
}

// Persona es quien hace el pedido.
type Persona struct {
	ID     string // usuarios.id
	Clerk  string // user_…
	Nombre string
	Equipo bool
}

type clave struct{}

// De devuelve la persona del pedido. ok=false si el pedido no pasó por Exigir.
func De(ctx context.Context) (Persona, bool) {
	p, ok := ctx.Value(clave{}).(Persona)
	return p, ok
}

// Con pone la persona en el contexto (tests y el WebSocket, que la resuelve una vez).
func Con(ctx context.Context, p Persona) context.Context { return context.WithValue(ctx, clave{}, p) }

// Resolvedor traduce tokens a personas.
type Resolvedor struct {
	Clerk Verificador
	App   *db.Pool
	Log   *slog.Logger
}

// ErrSinToken: el pedido no trae token.
var ErrSinToken = errors.New("sesion: sin token")

// Token saca el token del pedido: primero el header, después la cookie de la web.
func Token(r *http.Request) string {
	if h := r.Header.Get("Authorization"); strings.HasPrefix(h, "Bearer ") {
		return strings.TrimSpace(strings.TrimPrefix(h, "Bearer "))
	}
	if c, err := r.Cookie("__session"); err == nil {
		return c.Value
	}
	return ""
}

// Resolver verifica el token y devuelve la persona, creándola si es la primera vez.
func (s *Resolvedor) Resolver(ctx context.Context, token string) (Persona, error) {
	if token == "" {
		return Persona{}, ErrSinToken
	}
	if s.Clerk == nil {
		return Persona{}, clerk.ErrToken
	}
	sujeto, err := s.Clerk.Verificar(ctx, token)
	if err != nil {
		return Persona{}, err
	}
	p := Persona{Clerk: sujeto}
	err = s.App.EnTx(ctx, func(tx pgx.Tx) error {
		err := tx.QueryRow(ctx, `SELECT id, nombre, equipo FROM usuarios WHERE clerk_id = $1 AND baja_en IS NULL`, sujeto).Scan(&p.ID, &p.Nombre, &p.Equipo)
		if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		u, err := s.Clerk.Usuario(ctx, sujeto)
		if err != nil {
			return err
		}
		nombre := strings.TrimSpace(u.Nombre)
		if len([]rune(nombre)) > 40 {
			nombre = string([]rune(nombre)[:40])
		}
		var fotos []string
		if u.Foto != "" {
			fotos = []string{u.Foto}
		}
		return tx.QueryRow(ctx, `
			INSERT INTO usuarios (clerk_id, nombre, fotos) VALUES ($1, $2, coalesce($3, '{}'::text[]))
			ON CONFLICT (clerk_id) DO UPDATE SET actualizado_en = usuarios.actualizado_en
			RETURNING id, nombre, equipo`, sujeto, nombre, fotos).Scan(&p.ID, &p.Nombre, &p.Equipo)
	})
	if err != nil {
		return Persona{}, err
	}
	return p, nil
}

// Exigir corta con 401 los pedidos sin una sesión válida.
func (s *Resolvedor) Exigir(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		p, err := s.Resolver(r.Context(), Token(r))
		if err != nil {
			if !errors.Is(err, ErrSinToken) && !errors.Is(err, clerk.ErrToken) {
				s.log().Warn("sesion: no se pudo resolver", "error", err)
				problema.Escribir(w, problema.Interno())
				return
			}
			problema.Escribir(w, problema.SinSesion())
			return
		}
		next.ServeHTTP(w, r.WithContext(Con(r.Context(), p)))
	})
}

// ExigirEquipo es Exigir y además que la persona sea del equipo de Caigo.
func (s *Resolvedor) ExigirEquipo(next http.Handler) http.Handler {
	return s.Exigir(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if p, _ := De(r.Context()); !p.Equipo {
			problema.Escribir(w, problema.SinPermiso("Esto es sólo para el equipo de Caigo."))
			return
		}
		next.ServeHTTP(w, r)
	}))
}

func (s *Resolvedor) log() *slog.Logger {
	if s.Log != nil {
		return s.Log
	}
	return slog.Default()
}
