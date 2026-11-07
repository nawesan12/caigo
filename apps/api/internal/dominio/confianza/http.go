package confianza

import (
	"io"
	"log/slog"
	"net/http"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Rutas registra verificación, archivos y moderación.
func Rutas(mux *http.ServeMux, s *Servicio, ses *sesion.Resolvedor, log *slog.Logger) {
	h := func(con func(http.Handler) http.Handler, f func(http.ResponseWriter, *http.Request, sesion.Persona) error) http.Handler {
		return con(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			p, _ := sesion.De(r.Context())
			if err := f(w, r, p); err != nil {
				respuesta.Error(w, log, err)
			}
		}))
	}
	persona, equipo := ses.Exigir, ses.ExigirEquipo
	listo := func(w http.ResponseWriter) error { w.WriteHeader(http.StatusNoContent); return nil }

	// Subida: cuerpo crudo (la foto) con ?tipo=foto|selfie|dni. Más simple que multipart para las apps.
	mux.Handle("POST /v1/archivos", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		a, err := s.Subir(r.Context(), p.ID, r.URL.Query().Get("tipo"), http.MaxBytesReader(w, r.Body, archivos.Maximo+1))
		if err == nil {
			respuesta.JSON(w, http.StatusCreated, a)
		}
		return err
	}))
	// Las fotos son públicas (perfiles y planes); selfie y DNI, sólo para el dueño y el equipo.
	mux.HandleFunc("GET /v1/archivos/{id}", func(w http.ResponseWriter, r *http.Request) {
		a, err := s.Buscar(r.Context(), r.PathValue("id"))
		if err != nil {
			respuesta.Error(w, log, err)
			return
		}
		if a.Tipo != "foto" {
			p, err := ses.Resolver(r.Context(), sesion.Token(r))
			if err != nil || (p.ID != a.Dueno && !p.Equipo) {
				problema.Escribir(w, problema.NoEncontrado("Ese archivo no existe."))
				return
			}
			w.Header().Set("Cache-Control", "private, no-store")
		} else {
			w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
		}
		f, err := s.Disco.Abrir(a.Ruta)
		if err != nil {
			problema.Escribir(w, problema.NoEncontrado("Ese archivo no existe."))
			return
		}
		defer f.Close()
		w.Header().Set("Content-Type", a.Contenido)
		w.Header().Set("X-Content-Type-Options", "nosniff")
		_, _ = io.Copy(w, f)
	})

	mux.Handle("GET /v1/verificacion", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		e, err := s.VerEstado(r.Context(), p.ID)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, e)
		}
		return err
	}))
	mux.Handle("POST /v1/verificacion/telefono", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Telefono string `json:"telefono"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		// Con correo: {"via":"mail","destino":"n•••@gmail.com"}; simulado: {"codigoDePrueba":"…"}.
		envio, err := s.PedirCodigo(r.Context(), p.ID, p.Clerk, in.Telefono)
		if err == nil {
			respuesta.JSON(w, http.StatusOK, envio)
		}
		return err
	}))
	mux.Handle("POST /v1/verificacion/telefono/confirmar", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Codigo string `json:"codigo"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		if err := s.ConfirmarCodigo(r.Context(), p.ID, in.Codigo); err != nil {
			return err
		}
		return listo(w)
	}))
	mux.Handle("DELETE /v1/yo", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		if err := s.Baja(r.Context(), p.ID); err != nil {
			return err
		}
		return listo(w)
	}))
	mux.Handle("POST /v1/verificacion/selfie", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			ArchivoID string `json:"archivoId"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		if err := s.Selfie(r.Context(), p.ID, in.ArchivoID); err != nil {
			return err
		}
		return listo(w)
	}))
	mux.Handle("POST /v1/verificacion/dni", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var d DatosDNI
		if !respuesta.Leer(w, r, &d) {
			return nil
		}
		if err := s.PedirDNI(r.Context(), p.ID, d); err != nil {
			return err
		}
		return listo(w)
	}))

	// Equipo de Caigo.
	mux.Handle("GET /v1/equipo/dni", h(equipo, func(w http.ResponseWriter, r *http.Request, _ sesion.Persona) error {
		ds, err := s.DNIPendientes(r.Context())
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"pendientes": ds})
		}
		return err
	}))
	mux.Handle("POST /v1/equipo/dni/{id}", h(equipo, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Aprobar bool   `json:"aprobar"`
			Motivo  string `json:"motivo"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		if err := s.RevisarDNI(r.Context(), p.ID, r.PathValue("id"), in.Aprobar, in.Motivo); err != nil {
			return err
		}
		return listo(w)
	}))
	mux.HandleFunc("POST /v1/publico/arrepentimiento", func(w http.ResponseWriter, r *http.Request) {
		var in struct {
			Nombre string `json:"nombre"`
			Email  string `json:"email"`
			Compra string `json:"compra"`
		}
		if !respuesta.Leer(w, r, &in) {
			return
		}
		codigo, err := s.Arrepentirse(r.Context(), in.Nombre, in.Email, in.Compra)
		if err != nil {
			respuesta.Error(w, log, err)
			return
		}
		respuesta.JSON(w, http.StatusCreated, map[string]string{"codigo": codigo})
	})
	mux.Handle("GET /v1/equipo/arrepentimientos", h(equipo, func(w http.ResponseWriter, r *http.Request, _ sesion.Persona) error {
		as, err := s.Arrepentimientos(r.Context())
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"arrepentimientos": as})
		}
		return err
	}))
	mux.Handle("POST /v1/equipo/arrepentimientos/{id}", h(equipo, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Nota string `json:"nota"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		if err := s.AtenderArrepentimiento(r.Context(), p.ID, r.PathValue("id"), in.Nota); err != nil {
			return err
		}
		return listo(w)
	}))
	mux.Handle("GET /v1/equipo/reportes", h(equipo, func(w http.ResponseWriter, r *http.Request, _ sesion.Persona) error {
		rs, err := s.Reportes(r.Context())
		if err == nil {
			respuesta.JSON(w, http.StatusOK, map[string]any{"reportes": rs})
		}
		return err
	}))
	mux.Handle("POST /v1/equipo/reportes/{id}", h(equipo, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Descartar  bool   `json:"descartar"`
			Suspender  bool   `json:"suspender"`
			Resolucion string `json:"resolucion"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		if err := s.Resolver(r.Context(), p.ID, r.PathValue("id"), in.Descartar, in.Suspender, in.Resolucion); err != nil {
			return err
		}
		return listo(w)
	}))
}
