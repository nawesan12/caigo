// Package servidor arma el http.Handler de la API: rutas de salud y las de cada dominio.
package servidor

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/avisos"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/chat"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/confianza"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/equipo"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/gente"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/lugares"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/personas"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/wallet"
	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/cifrado"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/firma"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/limite"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sms"
)

// Dependencias del servidor. App puede ser nil en tests que no tocan la base (y entonces sólo
// quedan las rutas de salud).
type Dependencias struct {
	App     *db.Pool
	Clerk   sesion.Verificador
	Log     *slog.Logger
	Version string
	// Reloj (tests). nil: time.Now.
	Reloj func() time.Time
	// Hub del tiempo real (main lo arranca con Escuchar). nil: uno sin escuchar (tests).
	Hub *chat.Hub
	// Orígenes web que pueden abrir el WebSocket.
	Origenes []string
	// Clave de las firmas, sitio público, URL pública de la API y carpeta de archivos.
	Secreto, Sitio, APIPublica, Archivos string
	// Tiendas de Plus (Apple, Google Play, Mercado Pago). nil: todas apagadas.
	Tiendas *plus.Tiendas
	// Clave del webhook de Google Play.
	ClaveGoogle string
	// Cifrado de secretos de terceros (tokens de Mercado Pago, claves de placas). nil: apagado.
	Cifra *cifrado.Cifrador
	// Panel de lugares (lugares.caigo.com.ar).
	Panel string
	// Pases de Wallet (Apple y Google). nil: apagados.
	Wallet *wallet.Servicio
	// Versión mínima de las apps (versionCode de Android, build de iOS). 0: cualquiera.
	MinAndroid, MinIos int
}

// Nuevo arma el handler con todas las rutas.
func Nuevo(d Dependencias) http.Handler {
	if d.Log == nil {
		d.Log = slog.Default()
	}
	mux := http.NewServeMux()
	// Vivo: el proceso responde. No toca la base, para que un corte de Postgres no reinicie la API.
	mux.HandleFunc("GET /salud/vivo", func(w http.ResponseWriter, _ *http.Request) {
		escribirJSON(w, http.StatusOK, map[string]string{"estado": "vivo"})
	})
	// Listo: puede atender pedidos (la base responde).
	mux.HandleFunc("GET /salud/listo", func(w http.ResponseWriter, r *http.Request) {
		if d.App == nil {
			problema.Escribir(w, problema.Interno())
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
		defer cancel()
		if err := d.App.Ping(ctx); err != nil {
			d.Log.Warn("salud: la base no responde", "error", err)
			w.Header().Set("Retry-After", "5")
			problema.Escribir(w, &problema.Problema{Estado: http.StatusServiceUnavailable, Codigo: "sin_base",
				Titulo: "No disponible", Detalle: "La base de datos no responde."})
			return
		}
		escribirJSON(w, http.StatusOK, map[string]string{"estado": "listo"})
	})
	mux.HandleFunc("GET /v1/version", func(w http.ResponseWriter, _ *http.Request) {
		escribirJSON(w, http.StatusOK, map[string]any{"version": d.Version, "minAndroid": d.MinAndroid, "minIos": d.MinIos})
	})
	if d.App != nil {
		ses := &sesion.Resolvedor{Clerk: d.Clerk, App: d.App, Log: d.Log}
		ses.Rutas(mux)
		pls := &planes.Servicio{App: d.App, Reloj: d.Reloj, Firma: firma.Firmador{Clave: []byte(d.Secreto)}, Sitio: d.Sitio}
		planes.Rutas(mux, pls, ses.Exigir, d.Log)
		equipo.Rutas(mux, &equipo.Servicio{App: d.App, Publicador: pls, Reloj: d.Reloj}, ses, d.Log)
		confianza.Rutas(mux, &confianza.Servicio{App: d.App, Disco: archivos.Disco{Raiz: d.Archivos}, SMS: sms.Simulado{Log: d.Log},
			APIPublica: d.APIPublica, Reloj: d.Reloj}, ses, d.Log)
		personas.Rutas(mux, &personas.Servicio{App: d.App}, ses.Exigir, d.Log)
		avisos.Rutas(mux, d.App, ses.Exigir, d.Log)
		if d.Hub == nil {
			d.Hub = &chat.Hub{Servicio: &chat.Servicio{App: d.App, Reloj: d.Reloj}, Log: d.Log}
		}
		chat.Rutas(mux, d.Hub.Servicio, d.Hub, ses, d.Origenes, d.Log)
		gente.Rutas(mux, &gente.Servicio{App: d.App, Reloj: d.Reloj}, ses.Exigir, d.Log)
		if d.Tiendas == nil {
			d.Tiendas = &plus.Tiendas{}
		}
		ps := &plus.Servicio{App: d.App, Reloj: d.Reloj}
		plus.Rutas(mux, ps, d.Tiendas, ses, d.ClaveGoogle, d.Log)
		ls := &lugares.Servicio{App: d.App, MP: d.Tiendas.MP, Cifra: d.Cifra, Firma: firma.Firmador{Clave: []byte(d.Secreto)},
			Sitio: d.Sitio, Panel: d.Panel, APIPublica: d.APIPublica, Reloj: d.Reloj}
		lugares.Rutas(mux, ls, ses, d.Log)
		if d.Wallet == nil {
			d.Wallet = &wallet.Servicio{}
		}
		d.Wallet.App = d.App
		wallet.Rutas(mux, d.Wallet, ses.Exigir, d.Log)
		mercadopago.Webhook(mux, d.Tiendas.MP, map[string]mercadopago.Manejador{
			"subscription_preapproval": ps.AvisoMP(d.Tiendas),
			"payment":                  ls.AvisoPago,
		}, d.Log)
	}
	mux.HandleFunc("/", func(w http.ResponseWriter, _ *http.Request) {
		problema.Escribir(w, problema.NoEncontrado("Esa ruta no existe."))
	})
	return recuperar(d.Log, limite.Envolver(mux, d.App, Reglas, d.Log))
}

// Reglas: topes por ruta (por sesión, o por IP si no hay sesión o la regla lo pide).
var Reglas = map[string]limite.Regla{
	"POST /v1/sesion":                          {Max: 20, Ventana: 10 * time.Minute, PorIP: true},
	"POST /v1/verificacion/telefono":           {Max: 5, Ventana: time.Hour},
	"POST /v1/verificacion/telefono/confirmar": {Max: 10, Ventana: time.Hour},
	"POST /v1/verificacion/dni":                {Max: 5, Ventana: 24 * time.Hour},
	"POST /v1/archivos":                        {Max: 60, Ventana: time.Hour},
	"POST /v1/planes":                          {Max: 20, Ventana: 24 * time.Hour},
	"POST /v1/planes/{id}/mensajes":            {Max: 60, Ventana: time.Minute},
	"POST /v1/privados/{id}":                   {Max: 30, Ventana: time.Minute},
	"POST /v1/reportes":                        {Max: 20, Ventana: 24 * time.Hour},
	"POST /v1/planes/{id}/invitar":             {Max: 60, Ventana: time.Hour},
	"POST /v1/planes/{id}/entradas":            {Max: 20, Ventana: time.Hour},
	"POST /v1/tap":                             {Max: 30, Ventana: time.Minute},
	"POST /v1/lugares":                         {Max: 5, Ventana: 24 * time.Hour},
	"POST /v1/lugares/{id}/validar":            {Max: 120, Ventana: time.Minute},
	"POST /v1/plus/mercadopago":                {Max: 10, Ventana: time.Hour},
	"GET /v1/publico/planes/{slug}":            {Max: 120, Ventana: time.Minute, PorIP: true},
	"GET /v1/publico/vivo/{token}":             {Max: 60, Ventana: time.Minute, PorIP: true},
	"POST /v1/publico/arrepentimiento":         {Max: 5, Ventana: time.Hour, PorIP: true},
	// Contrato del 31/10.
	"PATCH /v1/planes/{id}":                                   {Max: 60, Ventana: time.Hour},
	"DELETE /v1/planes/{id}/personas/{usuarioId}":             {Max: 60, Ventana: time.Hour},
	"DELETE /v1/entradas/{id}":                                {Max: 20, Ventana: time.Hour},
	"PUT /v1/yo/ubicacion":                                    {Max: 60, Ventana: time.Hour},
	"PATCH /v1/lugares/{id}/planes/{plan}":                    {Max: 120, Ventana: time.Hour},
	"POST /v1/lugares/{id}/planes/{plan}/checkin/{usuarioId}": {Max: 120, Ventana: time.Minute},
	"GET /v1/publico/zonas":                                   {Max: 120, Ventana: time.Minute, PorIP: true},
	"GET /v1/publico/planes/ahora":                            {Max: 120, Ventana: time.Minute, PorIP: true},
	"GET /v1/publico/planes/explorar":                         {Max: 120, Ventana: time.Minute, PorIP: true},
	"POST /v1/publico/espera":                                 {Max: 5, Ventana: time.Hour, PorIP: true},
	"POST /v1/lugares/{id}/placas/pedido":                     {Max: 10, Ventana: 24 * time.Hour},
}

// recuperar convierte un panic en un 500 con problem+json y lo registra, sin tirar el proceso.
func recuperar(log *slog.Logger, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if v := recover(); v != nil {
				if v == http.ErrAbortHandler {
					panic(v)
				}
				log.Error("panic en un handler", "ruta", r.URL.Path, "valor", v)
				problema.Escribir(w, problema.Interno())
			}
		}()
		next.ServeHTTP(w, r)
	})
}

func escribirJSON(w http.ResponseWriter, estado int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(estado)
	_ = json.NewEncoder(w).Encode(v)
}
