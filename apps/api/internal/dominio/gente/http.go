package gente

import (
	"log/slog"
	"net/http"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Rutas de Gente/Citas e invitaciones.
func Rutas(mux *http.ServeMux, s *Servicio, con func(http.Handler) http.Handler, log *slog.Logger) {
	h := func(f func(http.ResponseWriter, *http.Request, sesion.Persona) error) http.Handler {
		return con(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			p, _ := sesion.De(r.Context())
			if err := f(w, r, p); err != nil {
				respuesta.Error(w, log, err)
			}
		}))
	}
	mux.Handle("GET /v1/gente/mazo", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		ts, err := s.Mazo(r.Context(), p.ID, r.URL.Query().Get("modo"))
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"personas": ts})
		}
		return err
	}))
	mux.Handle("POST /v1/gente/{id}/reaccion", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Modo   string `json:"modo"`
			Accion string `json:"accion"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		res, err := s.Reaccionar(r.Context(), p.ID, r.PathValue("id"), in.Modo, in.Accion)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, res)
		}
		return err
	}))
	mux.Handle("GET /v1/gente/recibidos", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		rs, bloqueada, err := s.Recibidos(r.Context(), p.ID, r.URL.Query().Get("modo"))
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"personas": rs, "bloqueada": bloqueada})
		}
		return err
	}))
	mux.Handle("DELETE /v1/gente/recibidos/{id}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		if err := s.Descartar(r.Context(), p.ID, r.PathValue("id"), r.URL.Query().Get("modo")); err != nil {
			return err
		}
		w.WriteHeader(http.StatusNoContent)
		return nil
	}))
	mux.Handle("POST /v1/planes/{id}/invitar", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			UsuarioID string `json:"usuarioId"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		if err := s.Invitar(r.Context(), p.ID, in.UsuarioID, r.PathValue("id")); err != nil {
			return err
		}
		w.WriteHeader(http.StatusNoContent)
		return nil
	}))
	mux.Handle("GET /v1/invitaciones", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		vs, bloqueada, err := s.Invitaciones(r.Context(), p.ID)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"invitaciones": vs, "bloqueada": bloqueada})
		}
		return err
	}))
	mux.Handle("POST /v1/invitaciones/{planId}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Aceptar bool `json:"aceptar"`
			// Confirma, sólo para este pedido, que es parte de la diversidad (planes «Solo
			// diversidades»). No se guarda.
			Diversidad bool `json:"diversidad"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		adentro, err := s.ResponderInvitacion(r.Context(), p.ID, r.PathValue("planId"), in.Aceptar, in.Diversidad)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]bool{"adentro": adentro})
		}
		return err
	}))
}
