package mercadopago

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"
)

// Manejador de un tipo de aviso (subscription_preapproval, payment…). Un error hace que Mercado
// Pago reintente.
type Manejador func(r *http.Request, a Aviso) error

// Webhook registra POST /v1/webhooks/mercadopago: verifica la firma y reparte por tipo.
func Webhook(mux *http.ServeMux, c *Config, manejadores map[string]Manejador, log *slog.Logger) {
	mux.HandleFunc("POST /v1/webhooks/mercadopago", func(w http.ResponseWriter, r *http.Request) {
		var a Aviso
		if err := json.NewDecoder(io.LimitReader(r.Body, 64<<10)).Decode(&a); err != nil {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		// El id firmado es el de la URL (?data.id=); el que se procesa, el del cuerpo. Tienen que ser el
		// mismo (Mercado Pago firma los alfanuméricos en minúsculas): si no, una firma válida de un
		// recurso serviría para hacer procesar otro.
		id := r.URL.Query().Get("data.id")
		if id == "" {
			id = a.ID()
		}
		if !strings.EqualFold(id, a.ID()) {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		if c == nil || !FirmaValida(c.SecretoWebhook, r.Header.Get("x-signature"), r.Header.Get("x-request-id"), id, time.Now()) {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		if m, ok := manejadores[a.Type]; ok {
			if err := m(r, a); err != nil {
				log.Warn("mercadopago: aviso fallido", "tipo", a.Type, "error", err)
				w.WriteHeader(http.StatusInternalServerError)
				return
			}
		}
		w.WriteHeader(http.StatusOK)
	})
}
