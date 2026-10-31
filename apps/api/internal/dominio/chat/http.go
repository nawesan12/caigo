package chat

import (
	"log/slog"
	"net/http"
	"strconv"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Rutas registra el chat. El WebSocket (/v1/tiempo-real) toma el token del header, de la cookie o
// de ?token= (el navegador no puede poner headers en un WebSocket).
func Rutas(mux *http.ServeMux, s *Servicio, hub *Hub, ses *sesion.Resolvedor, origenes []string, log *slog.Logger) {
	h := func(f func(http.ResponseWriter, *http.Request, sesion.Persona) error) http.Handler {
		return ses.Exigir(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			p, _ := sesion.De(r.Context())
			if err := f(w, r, p); err != nil {
				respuesta.Error(w, log, err)
			}
		}))
	}
	antes := func(r *http.Request) int64 {
		n, _ := strconv.ParseInt(r.URL.Query().Get("antes"), 10, 64)
		return n
	}
	type texto struct {
		Texto string `json:"texto"`
	}

	mux.Handle("GET /v1/planes/{id}/mensajes", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		ms, err := s.Mensajes(r.Context(), p.ID, r.PathValue("id"), antes(r))
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"mensajes": ms})
		}
		return err
	}))
	mux.Handle("POST /v1/planes/{id}/mensajes", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var t texto
		if !respuesta.Leer(w, r, &t) {
			return nil
		}
		m, err := s.Enviar(r.Context(), p.ID, r.PathValue("id"), t.Texto)
		if err == nil {
			respuesta.JSON(w, http.StatusCreated, m)
		}
		return err
	}))
	mux.Handle("GET /v1/privados", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		cs, err := s.Conversaciones(r.Context(), p.ID)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"conversaciones": cs})
		}
		return err
	}))
	mux.Handle("GET /v1/privados/{id}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		ms, err := s.Hilo(r.Context(), p.ID, r.PathValue("id"), antes(r))
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"privados": ms})
		}
		return err
	}))
	mux.Handle("POST /v1/privados/{id}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var t texto
		if !respuesta.Leer(w, r, &t) {
			return nil
		}
		m, err := s.EnviarPrivado(r.Context(), p.ID, r.PathValue("id"), t.Texto)
		if err == nil {
			respuesta.JSON(w, http.StatusCreated, m)
		}
		return err
	}))
	mux.HandleFunc("GET /v1/tiempo-real", func(w http.ResponseWriter, r *http.Request) {
		token := sesion.Token(r)
		if token == "" {
			token = r.URL.Query().Get("token")
		}
		p, err := ses.Resolver(r.Context(), token)
		if err != nil {
			problema.Escribir(w, problema.SinSesion())
			return
		}
		hub.Conectar(w, r, p.ID, p.Nombre, origenes)
	})
}
