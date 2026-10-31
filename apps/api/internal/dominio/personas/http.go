package personas

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Rutas registra los handlers de personas. Todas piden sesión.
func Rutas(mux *http.ServeMux, s *Servicio, con func(http.Handler) http.Handler, log *slog.Logger) {
	h := func(f func(http.ResponseWriter, *http.Request, sesion.Persona) error) http.Handler {
		return con(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			p, _ := sesion.De(r.Context())
			if err := f(w, r, p); err != nil {
				respuesta.Error(w, log, err)
			}
		}))
	}
	sinCuerpo := func(w http.ResponseWriter) error { w.WriteHeader(http.StatusNoContent); return nil }

	mux.Handle("GET /v1/yo", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		yo, err := s.Yo(r.Context(), p.ID)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, yo)
		}
		return err
	}))
	mux.Handle("PATCH /v1/yo", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var c Cambios
		if !respuesta.Leer(w, r, &c) {
			return nil
		}
		yo, err := s.Actualizar(r.Context(), p.ID, c, time.Now())
		if err == nil {
			respuesta.JSON(w, http.StatusOK, yo)
		}
		return err
	}))
	mux.Handle("GET /v1/personas/{id}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		otro, err := s.Ver(r.Context(), p.ID, r.PathValue("id"))
		if err == nil {
			respuesta.JSON(w, http.StatusOK, otro)
		}
		return err
	}))
	mux.Handle("GET /v1/gente", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		g, err := s.MiGente(r.Context(), p.ID)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"gente": g})
		}
		return err
	}))
	mux.Handle("PUT /v1/gente/{id}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		if err := s.SumarAGente(r.Context(), p.ID, r.PathValue("id")); err != nil {
			return err
		}
		return sinCuerpo(w)
	}))
	mux.Handle("DELETE /v1/gente/{id}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		if err := s.SacarDeGente(r.Context(), p.ID, r.PathValue("id")); err != nil {
			return err
		}
		return sinCuerpo(w)
	}))
	mux.Handle("PUT /v1/bloqueos/{id}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		if err := s.Bloquear(r.Context(), p.ID, r.PathValue("id")); err != nil {
			return err
		}
		return sinCuerpo(w)
	}))
	mux.Handle("POST /v1/reportes", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var rep Reporte
		if !respuesta.Leer(w, r, &rep) {
			return nil
		}
		if err := s.Reportar(r.Context(), p.ID, rep); err != nil {
			return err
		}
		return sinCuerpo(w)
	}))
}
