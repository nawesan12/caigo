package equipo

import (
	"log/slog"
	"net/http"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Rutas del backoffice (/v1/equipo/..., sólo equipo) y la lista de espera (pública).
func Rutas(mux *http.ServeMux, s *Servicio, ses *sesion.Resolvedor, log *slog.Logger) {
	h := func(f func(http.ResponseWriter, *http.Request) error) http.Handler {
		return ses.ExigirEquipo(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if err := f(w, r); err != nil {
				respuesta.Error(w, log, err)
			}
		}))
	}
	listo := func(w http.ResponseWriter) error { w.WriteHeader(http.StatusNoContent); return nil }
	motivo := func(w http.ResponseWriter, r *http.Request) (string, bool) {
		var in struct {
			Motivo string `json:"motivo"`
		}
		ok := respuesta.Leer(w, r, &in)
		return in.Motivo, ok
	}

	mux.Handle("GET /v1/equipo/usuarios", h(func(w http.ResponseWriter, r *http.Request) error {
		us, err := s.Usuarios(r.Context(), r.URL.Query().Get("q"))
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"usuarios": us})
		}
		return err
	}))
	for _, accion := range []string{"suspender", "reactivar"} {
		mux.Handle("POST /v1/equipo/usuarios/{id}/"+accion, h(func(w http.ResponseWriter, r *http.Request) error {
			m, ok := motivo(w, r)
			if !ok {
				return nil
			}
			if err := s.Suspender(r.Context(), r.PathValue("id"), accion == "suspender", m); err != nil {
				return err
			}
			return listo(w)
		}))
	}
	mux.Handle("GET /v1/equipo/planes", h(func(w http.ResponseWriter, r *http.Request) error {
		ps, err := s.Planes(r.Context(), r.URL.Query().Get("q"), r.URL.Query().Get("cual"))
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"planes": ps})
		}
		return err
	}))
	mux.Handle("POST /v1/equipo/planes", h(func(w http.ResponseWriter, r *http.Request) error {
		var in Semilla
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		p, err := s.CrearSemilla(r.Context(), in)
		if err == nil {
			respuesta.JSON(w, http.StatusCreated, p)
		}
		return err
	}))
	mux.Handle("POST /v1/equipo/planes/{id}/cancelar", h(func(w http.ResponseWriter, r *http.Request) error {
		m, ok := motivo(w, r)
		if !ok {
			return nil
		}
		if err := s.CancelarPlan(r.Context(), r.PathValue("id"), m); err != nil {
			return err
		}
		return listo(w)
	}))
	mux.Handle("GET /v1/equipo/metricas", h(func(w http.ResponseWriter, r *http.Request) error {
		ms, err := s.Metricas(r.Context())
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"semanas": ms})
		}
		return err
	}))
	mux.HandleFunc("POST /v1/publico/espera", func(w http.ResponseWriter, r *http.Request) {
		var in struct {
			Email  string `json:"email"`
			Ciudad string `json:"ciudad"`
		}
		if !respuesta.Leer(w, r, &in) {
			return
		}
		if err := s.Esperar(r.Context(), in.Email, in.Ciudad); err != nil {
			respuesta.Error(w, log, err)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})
}
