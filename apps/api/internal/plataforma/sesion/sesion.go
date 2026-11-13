// Package sesion resuelve quién hace cada pedido. Clerk es el proveedor de identidad: su token de
// sesión se canjea una vez (POST /v1/sesion) por un token propio de Caigo (cs_…, 60 días, se
// renueva con el uso), que es el que mandan la web y las apps en Authorization. También se acepta
// el JWT de Clerk directo. La primera vez que alguien entra se le crea su fila en usuarios con el
// nombre que tenga en Clerk.
package sesion

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/clerk"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/vetados"
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
	// Lista de vetados (huellas de los suspendidos): un alta con un mail vetado no entra. nil: no se
	// consulta.
	Vetados *vetados.Clave
}

// ErrSuspendida: la cuenta está suspendida por moderación.
var ErrSuspendida = errors.New("sesion: cuenta suspendida")

// ErrCuentaBorrada: la persona borró su cuenta y todavía no se purgó (30 días). No entra: el
// ingreso responde 409 cuenta_borrada. Purgada, el clerk_id queda libre y entra como cuenta nueva.
var ErrCuentaBorrada = errors.New("sesion: cuenta borrada")

// CuentaBorrada es lo que ve quien intenta entrar con una cuenta borrada.
func CuentaBorrada() *problema.Problema {
	return problema.Conflicto("cuenta_borrada",
		"Esta cuenta se borró. Si fue un error, escribinos a hola@caigo.com.ar dentro de los 30 días y la recuperamos.")
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

// Vida de una sesión propia (se renueva con el uso).
const vidaSesion = 60 * 24 * time.Hour

func hashToken(t string) []byte {
	h := sha256.Sum256([]byte(t))
	return h[:]
}

// Crear canjea un token de Clerk por una sesión de Caigo. Devuelve el token (cs_…).
func (s *Resolvedor) Crear(ctx context.Context, tokenClerk, cliente string) (string, Persona, error) {
	if cliente != "web" && cliente != "ios" && cliente != "android" {
		cliente = "web"
	}
	if strings.HasPrefix(tokenClerk, prefijo) {
		return "", Persona{}, clerk.ErrToken
	}
	p, err := s.Resolver(ctx, tokenClerk)
	if err != nil {
		return "", Persona{}, err
	}
	s.copiarEmail(ctx, p)
	b := make([]byte, 32)
	_, _ = rand.Read(b)
	tok := prefijo + base64.RawURLEncoding.EncodeToString(b)
	err = s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `INSERT INTO sesiones (token_hash, usuario_id, cliente, vence_en) VALUES ($1, $2, $3, now() + $4::interval)`,
			hashToken(tok), p.ID, cliente, vidaSesion.String())
		return err
	})
	return tok, p, err
}

// copiarEmail guarda el mail de Clerk en usuarios_privado (GET /v1/yo lo devuelve para confirmar la
// baja, y con él se encuentra una cuenta borrada). Una vez por ingreso; si Clerk no responde, sigue.
func (s *Resolvedor) copiarEmail(ctx context.Context, p Persona) {
	if s.Clerk == nil || p.Clerk == "" {
		return
	}
	u, err := s.Clerk.Usuario(ctx, p.Clerk)
	if err != nil || u.Email == "" || len(u.Email) > 200 {
		return
	}
	err = s.App.EnUsuario(ctx, p.ID, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `INSERT INTO usuarios_privado (usuario_id, email) VALUES ($1, $2)
			ON CONFLICT (usuario_id) DO UPDATE SET email = excluded.email WHERE usuarios_privado.email <> excluded.email`, p.ID, u.Email)
		return err
	})
	if err != nil {
		s.log().Warn("sesion: no se pudo guardar el mail", "error", err)
	}
}

// vetado dice si el mail está en la lista de vetados.
func (s *Resolvedor) vetado(ctx context.Context, email string) (bool, error) {
	if s.Vetados == nil || email == "" {
		return false, nil
	}
	var veto bool
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var err error
		veto, err = s.Vetados.Vetado(ctx, tx, vetados.Email, email)
		return err
	})
	return veto, err
}

// Cerrar borra la sesión del token.
func (s *Resolvedor) Cerrar(ctx context.Context, token string) error {
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `DELETE FROM sesiones WHERE token_hash = $1`, hashToken(token))
		return err
	})
}

const prefijo = "cs_"

// propia resuelve un token de sesión de Caigo.
func (s *Resolvedor) propia(ctx context.Context, token string) (Persona, error) {
	var p Persona
	var suspendida bool
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		err := tx.QueryRow(ctx, `
			UPDATE sesiones x SET usada_en = now(),
				vence_en = CASE WHEN x.usada_en < now() - interval '1 day' THEN now() + $2::interval ELSE x.vence_en END
			FROM usuarios u WHERE x.token_hash = $1 AND x.vence_en > now() AND u.id = x.usuario_id AND u.baja_en IS NULL
			RETURNING u.id, u.clerk_id, u.nombre, u.equipo, u.suspendido_en IS NOT NULL`, hashToken(token), vidaSesion.String()).
			Scan(&p.ID, &p.Clerk, &p.Nombre, &p.Equipo, &suspendida)
		if errors.Is(err, pgx.ErrNoRows) {
			return clerk.ErrToken
		}
		return err
	})
	if err == nil && suspendida {
		return Persona{}, ErrSuspendida
	}
	return p, err
}

// Resolver verifica el token y devuelve la persona, creándola si es la primera vez.
func (s *Resolvedor) Resolver(ctx context.Context, token string) (Persona, error) {
	if token == "" {
		return Persona{}, ErrSinToken
	}
	if strings.HasPrefix(token, prefijo) {
		return s.propia(ctx, token)
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
		var suspendida, borrada bool
		// Una baja sin purgar conserva el clerk_id (para poder recuperarla): no entra ni se crea otra.
		err := tx.QueryRow(ctx, `SELECT id, nombre, equipo, suspendido_en IS NOT NULL, baja_en IS NOT NULL FROM usuarios
			WHERE clerk_id = $1`, sujeto).Scan(&p.ID, &p.Nombre, &p.Equipo, &suspendida, &borrada)
		if borrada {
			return ErrCuentaBorrada
		}
		if suspendida {
			return ErrSuspendida
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		u, err := s.Clerk.Usuario(ctx, sujeto)
		if err != nil {
			return err
		}
		// Alguien suspendido que vuelve con una cuenta nueva (o con la misma, después de la purga):
		// entra como suspendido. La consulta va aparte, como sistema (sólo él lee la lista).
		if veto, err := s.vetado(ctx, u.Email); err != nil || veto {
			if err == nil {
				err = ErrSuspendida
			}
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
		if errors.Is(err, ErrCuentaBorrada) {
			problema.Escribir(w, CuentaBorrada())
			return
		}
		if errors.Is(err, ErrSuspendida) {
			problema.Escribir(w, problema.SinPermiso("Tu cuenta está suspendida. Escribinos a soporte@caigo.com.ar."))
			return
		}
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

// Rutas de la sesión: POST /v1/sesion canjea el token de Clerk (Authorization) por uno de Caigo;
// DELETE /v1/sesion la cierra.
func (s *Resolvedor) Rutas(mux *http.ServeMux) {
	mux.HandleFunc("POST /v1/sesion", func(w http.ResponseWriter, r *http.Request) {
		tok, p, err := s.Crear(r.Context(), Token(r), r.URL.Query().Get("cliente"))
		switch {
		case errors.Is(err, ErrCuentaBorrada):
			problema.Escribir(w, CuentaBorrada())
		case errors.Is(err, ErrSuspendida):
			problema.Escribir(w, problema.SinPermiso("Tu cuenta está suspendida. Escribinos a soporte@caigo.com.ar."))
		case errors.Is(err, ErrSinToken) || errors.Is(err, clerk.ErrToken):
			problema.Escribir(w, problema.SinSesion())
		case err != nil:
			s.log().Warn("sesion: no se pudo crear", "error", err)
			problema.Escribir(w, problema.Interno())
		default:
			w.Header().Set("Content-Type", "application/json")
			w.Header().Set("Cache-Control", "no-store")
			_ = json.NewEncoder(w).Encode(map[string]any{"token": tok, "usuarioId": p.ID, "equipo": p.Equipo})
		}
	})
	mux.HandleFunc("DELETE /v1/sesion", func(w http.ResponseWriter, r *http.Request) {
		if t := Token(r); strings.HasPrefix(t, prefijo) {
			_ = s.Cerrar(r.Context(), t)
		}
		w.WriteHeader(http.StatusNoContent)
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
