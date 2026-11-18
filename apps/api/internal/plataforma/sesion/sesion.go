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
	"regexp"
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
	// DNI: tiene el DNI aprobado (usuarios.dni_en). Sin DNI, Exigir sólo deja pasar las rutas exentas
	// (ver Exenta): el perfil, la verificación, la baja, mirar planes y perfiles, bloquear y reportar,
	// el panel de lugares y el equipo.
	DNI bool
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
			RETURNING u.id, u.clerk_id, u.nombre, u.equipo, u.suspendido_en IS NOT NULL, u.dni_en IS NOT NULL`, hashToken(token), vidaSesion.String()).
			Scan(&p.ID, &p.Clerk, &p.Nombre, &p.Equipo, &suspendida, &p.DNI)
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
		err := tx.QueryRow(ctx, `SELECT id, nombre, equipo, suspendido_en IS NOT NULL, baja_en IS NOT NULL, dni_en IS NOT NULL
			FROM usuarios WHERE clerk_id = $1`, sujeto).Scan(&p.ID, &p.Nombre, &p.Equipo, &suspendida, &borrada, &p.DNI)
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

// UsuarioDe dice de qué persona es el token (para los topes por ruta), sin crearla ni renovar la
// sesión. Vacío si no es válido o no se sabe.
func (s *Resolvedor) UsuarioDe(ctx context.Context, token string) string {
	var id string
	var err error
	if strings.HasPrefix(token, prefijo) {
		err = s.App.EnSistema(ctx, func(tx pgx.Tx) error {
			return tx.QueryRow(ctx, `SELECT usuario_id::text FROM sesiones WHERE token_hash = $1 AND vence_en > now()`,
				hashToken(token)).Scan(&id)
		})
	} else if s.Clerk != nil {
		var sujeto string
		if sujeto, err = s.Clerk.Verificar(ctx, token); err == nil {
			err = s.App.EnSistema(ctx, func(tx pgx.Tx) error {
				return tx.QueryRow(ctx, `SELECT id::text FROM usuarios WHERE clerk_id = $1`, sujeto).Scan(&id)
			})
		}
	}
	if err != nil {
		return ""
	}
	return id
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
		if !p.DNI && !Exenta(r.Pattern) {
			problema.Escribir(w, FaltaDNI())
			return
		}
		if !idsValidos(r) {
			problema.Escribir(w, problema.NoEncontrado("Eso no existe."))
			return
		}
		next.ServeHTTP(w, r.WithContext(Con(r.Context(), p)))
	})
}

// FaltaDNI es lo que ve quien todavía no verificó su DNI y quiere sumarse, crear un plan, chatear o
// ver a la gente. Mirar planes y perfiles se puede sin DNI (Nahuel, 18/11: «Mirar sin DNI»; antes,
// desde el 16/11, sin DNI no se podía hacer nada). Las apps lo mapean a la pantalla del DNI.
func FaltaDNI() *problema.Problema {
	return &problema.Problema{Estado: http.StatusForbidden, Codigo: "falta_dni", Titulo: "Falta tu DNI",
		Detalle: "Para sumarte, armar un plan o chatear, verificá que seas vos con tu DNI. Es un minuto."}
}

// Exenta dice si una ruta con sesión (su patrón: "GET /v1/yo") se puede usar sin el DNI aprobado:
//   - el perfil propio, la verificación y sus fotos, la baja, la sesión y los dispositivos de push, el
//     panel de lugares (otra regla: lo usan comercios) y el backoffice del equipo;
//   - mirar (Mirar): las listas de planes, el detalle y los perfiles, y lo propio que esas pantallas
//     piden al abrir (Mis planes, Mi gente, privados, invitaciones, pases, Plus), que sin DNI
//     viene vacío;
//   - lo de seguridad: bloquear y reportar (siempre, con o sin DNI).
//
// Todo lo que hace algo con otra gente (sumarse, crear, chatear, privados, el mazo, invitar, entradas,
// check-in) pide el DNI.
func Exenta(patron string) bool {
	metodo, ruta, _ := strings.Cut(patron, " ")
	switch metodo + " " + ruta {
	case "GET /v1/yo", "PATCH /v1/yo", "DELETE /v1/yo", "DELETE /v1/cuenta",
		"POST /v1/archivos", "GET /v1/archivos/{id}", "POST /v1/sesion", "DELETE /v1/sesion",
		"PUT /v1/dispositivos", "DELETE /v1/dispositivos/{token}", "POST /v1/mercadopago/vuelta",
		"GET /v1/bloqueos", "PUT /v1/bloqueos/{id}", "DELETE /v1/bloqueos/{id}", "POST /v1/reportes":
		return true
	}
	if Mirar(patron) {
		return true
	}
	return ruta == "/v1/verificacion" || strings.HasPrefix(ruta, "/v1/verificacion/") ||
		ruta == "/v1/lugares" || strings.HasPrefix(ruta, "/v1/lugares/") || strings.HasPrefix(ruta, "/v1/equipo/")
}

// Mirar dice si la ruta es de las de mirar sin DNI. En esas, los handlers recortan lo que no se
// muestra sin DNI (ver planes.SinDNI y personas.SinDNI): la dirección exacta y, de una persona, el
// barrio, el trabajo y sus próximos planes.
func Mirar(patron string) bool {
	switch patron {
	case "GET /v1/planes/ahora", "GET /v1/planes/explorar", "GET /v1/planes/{id}", "GET /v1/planes/mios",
		"GET /v1/personas/{id}", "PUT /v1/yo/ubicacion", "GET /v1/invitaciones", "GET /v1/pases",
		"GET /v1/plus", "GET /v1/gente", "GET /v1/privados":
		return true
	}
	return false
}

// Comodines de las rutas que son ids (uuid). Uno mal formado es un 404 antes de llegar a la base
// (si no, Postgres falla al convertirlo y sale un 500). GET /v1/planes/{id} acepta también el slug.
var comodinesID = []string{"id", "usuarioId", "planId", "plan", "lugar"}

var reUUID = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

// EsUUID dice si s es un uuid.
func EsUUID(s string) bool { return reUUID.MatchString(s) }

func idsValidos(r *http.Request) bool {
	if r.Pattern == "GET /v1/planes/{id}" {
		return true
	}
	for _, k := range comodinesID {
		if v := r.PathValue(k); v != "" && !EsUUID(v) {
			return false
		}
	}
	return true
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
