package logros

import (
	"log/slog"
	"net/http"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Rutas de «Traé a alguien». Los logros van en GET /v1/yo y GET /v1/personas/{id}.
func Rutas(mux *http.ServeMux, s *Servicio, con func(http.Handler) http.Handler, log *slog.Logger) {
	mux.Handle("GET /v1/yo/invitar", con(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		p, _ := sesion.De(r.Context())
		inv, err := s.Invitar(r.Context(), p.ID)
		if err != nil {
			respuesta.Error(w, log, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, inv)
	})))
	mux.Handle("POST /v1/yo/invitado", con(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		p, _ := sesion.De(r.Context())
		var c struct {
			Codigo string `json:"codigo"`
		}
		if !respuesta.Leer(w, r, &c) {
			return
		}
		if err := s.UsarCodigo(r.Context(), p.ID, c.Codigo); err != nil {
			respuesta.Error(w, log, err)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})))
}
