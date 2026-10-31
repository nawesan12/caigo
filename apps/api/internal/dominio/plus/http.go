package plus

import (
	"encoding/base64"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"

	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Rutas de Plus y los avisos de las tiendas. email busca el mail de la persona (en Clerk).
func Rutas(mux *http.ServeMux, s *Servicio, t *Tiendas, ses *sesion.Resolvedor, claveGoogle string, log *slog.Logger) {
	h := func(f func(http.ResponseWriter, *http.Request, sesion.Persona) error) http.Handler {
		return ses.Exigir(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			p, _ := sesion.De(r.Context())
			if err := f(w, r, p); err != nil {
				respuesta.Error(w, log, err)
			}
		}))
	}
	mux.Handle("GET /v1/plus", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		e, err := s.Ver(r.Context(), p.ID)
		if err == nil {
			planes := []map[string]any{}
			for _, k := range []string{"semana", "mes", "temporada"} {
				planes = append(planes, map[string]any{"plan": k, "producto": Planes[k].Producto, "centavos": Planes[k].Centavos, "nombre": Planes[k].Nombre})
			}
			respuesta.JSON(w, http.StatusOK, map[string]any{"estado": e, "planes": planes})
		}
		return err
	}))
	mux.Handle("POST /v1/plus/apple", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			JWS string `json:"jws"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		e, err := s.Apple(r.Context(), t, p.ID, in.JWS)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, e)
		}
		return err
	}))
	mux.Handle("POST /v1/plus/google", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Token string `json:"token"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		e, err := s.Google(r.Context(), t, p.ID, in.Token)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, e)
		}
		return err
	}))
	mux.Handle("POST /v1/plus/mercadopago", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Plan string `json:"plan"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		var email string
		if ses.Clerk != nil {
			if u, err := ses.Clerk.Usuario(r.Context(), p.Clerk); err == nil {
				email = u.Email
			}
		}
		link, err := s.MercadoPago(r.Context(), t, p.ID, email, in.Plan)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]string{"link": link})
		}
		return err
	}))

	// App Store Server Notifications V2 (la URL se carga en App Store Connect).
	mux.HandleFunc("POST /v1/webhooks/apple", func(w http.ResponseWriter, r *http.Request) {
		var in struct {
			SignedPayload string `json:"signedPayload"`
		}
		if err := json.NewDecoder(io.LimitReader(r.Body, 256<<10)).Decode(&in); err != nil {
			problema.Escribir(w, problema.Invalido("Cuerpo inválido."))
			return
		}
		if err := s.AvisoApple(r.Context(), t, in.SignedPayload); err != nil {
			respuesta.Error(w, log, err)
			return
		}
		w.WriteHeader(http.StatusOK)
	})
	// Real-time Developer Notifications de Google Play, por Pub/Sub push. La clave en la URL corta
	// el ruido; igual nunca se cree el aviso: se vuelve a leer el estado en Google.
	mux.HandleFunc("POST /v1/webhooks/google", func(w http.ResponseWriter, r *http.Request) {
		if claveGoogle == "" || r.URL.Query().Get("clave") != claveGoogle {
			problema.Escribir(w, problema.NoEncontrado("Esa ruta no existe."))
			return
		}
		var in struct {
			Message struct {
				Data string `json:"data"`
			} `json:"message"`
		}
		if err := json.NewDecoder(io.LimitReader(r.Body, 64<<10)).Decode(&in); err != nil {
			problema.Escribir(w, problema.Invalido("Cuerpo inválido."))
			return
		}
		b, _ := base64.StdEncoding.DecodeString(in.Message.Data)
		var n struct {
			SubscriptionNotification *struct {
				PurchaseToken string `json:"purchaseToken"`
			} `json:"subscriptionNotification"`
		}
		_ = json.Unmarshal(b, &n)
		if n.SubscriptionNotification != nil {
			if err := s.AvisoGoogle(r.Context(), t, n.SubscriptionNotification.PurchaseToken); err != nil {
				respuesta.Error(w, log, err)
				return
			}
		}
		w.WriteHeader(http.StatusOK)
	})
}

// AvisoMP es el manejador de los avisos de Mercado Pago de tipo subscription_preapproval (lo llama
// el webhook general de Mercado Pago, que también atiende los pagos de los lugares).
func (s *Servicio) AvisoMP(t *Tiendas) func(r *http.Request, a mercadopago.Aviso) error {
	return func(r *http.Request, a mercadopago.Aviso) error {
		return s.ConciliarMP(r.Context(), t, a.ID())
	}
}
