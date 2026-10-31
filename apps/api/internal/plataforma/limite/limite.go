// Package limite pone topes de pedidos por clave en una ventana de tiempo (tabla intentos), con un
// solo upsert atómico, como en Tu Local Web. La clave es la sesión (si el pedido trae token) o la IP.
package limite

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"log/slog"
	"net"
	"net/http"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Regla de una ruta: cuántos pedidos por ventana.
type Regla struct {
	Max     int
	Ventana time.Duration
	// PorIP cuenta por IP aunque el pedido traiga sesión (ingreso, rutas públicas).
	PorIP bool
}

// Permitido suma un intento a la clave y dice si sigue dentro del máximo de la ventana.
func Permitido(ctx context.Context, tx pgx.Tx, clave string, max int, ventana time.Duration) (bool, error) {
	var n int
	err := tx.QueryRow(ctx, `
		INSERT INTO intentos (clave, cantidad, desde) VALUES ($1, 1, now())
		ON CONFLICT (clave) DO UPDATE SET
			cantidad = CASE WHEN intentos.desde < now() - $2::interval THEN 1 ELSE intentos.cantidad + 1 END,
			desde = CASE WHEN intentos.desde < now() - $2::interval THEN now() ELSE intentos.desde END
		RETURNING cantidad`, clave, ventana.String()).Scan(&n)
	return n <= max, err
}

// IP del pedido. Detrás de Caddy (que corta todo lo que no venga de Cloudflare) vale
// Cf-Connecting-Ip; si no, la de la conexión.
func IP(r *http.Request) string {
	if ip := net.ParseIP(r.Header.Get("Cf-Connecting-Ip")); ip != nil {
		return ip.String()
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

// Envolver aplica las reglas por patrón de ruta ("POST /v1/sesion") antes de llegar al handler.
// Si la base falla, deja pasar (un tope no puede tirar la API) y lo registra.
func Envolver(mux *http.ServeMux, app *db.Pool, reglas map[string]Regla, log *slog.Logger) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, patron := mux.Handler(r)
		regla, ok := reglas[patron]
		if !ok || app == nil {
			mux.ServeHTTP(w, r)
			return
		}
		clave := "ip:" + IP(r)
		if t := sesion.Token(r); t != "" && !regla.PorIP {
			h := sha256.Sum256([]byte(t))
			clave = "s:" + hex.EncodeToString(h[:12])
		}
		var permitido bool
		err := app.EnSistema(r.Context(), func(tx pgx.Tx) error {
			var err error
			permitido, err = Permitido(r.Context(), tx, patron+"|"+clave, regla.Max, regla.Ventana)
			return err
		})
		if err != nil {
			log.Warn("limite: no se pudo contar", "error", err)
		} else if !permitido {
			w.Header().Set("Retry-After", strconv.Itoa(int(regla.Ventana.Seconds())))
			problema.Escribir(w, &problema.Problema{Estado: http.StatusTooManyRequests, Codigo: "demasiados",
				Titulo: "Pará un poco", Detalle: "Hiciste muchos pedidos seguidos. Probá de nuevo en un rato."})
			return
		}
		mux.ServeHTTP(w, r)
	})
}
