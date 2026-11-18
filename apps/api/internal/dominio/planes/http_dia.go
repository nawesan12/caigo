package planes

import (
	"net/http"
	"strings"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// rutasDia: el día del plan, la lista de espera, «¿Todo bien?», las fotos y «Esta semana».
func rutasDia(mux *http.ServeMux, s *Servicio, h func(func(http.ResponseWriter, *http.Request, sesion.Persona)) http.Handler,
	mal func(http.ResponseWriter, error)) {
	listo := func(w http.ResponseWriter, err error) {
		if err != nil {
			mal(w, err)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}
	// «¿Seguís yendo?» → Voy. («Me bajo» es DELETE /sumarme.)
	mux.Handle("POST /v1/planes/{id}/confirmo", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		listo(w, s.Confirmar(r.Context(), p.ID, r.PathValue("id")))
	}))
	mux.Handle("POST /v1/planes/{id}/espera", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		var e struct {
			Cantidad int16 `json:"cantidad"`
			Confirma
		}
		if !respuesta.Leer(w, r, &e) {
			return
		}
		out, err := s.Esperar(r.Context(), p.ID, r.PathValue("id"), e.Cantidad, e.Confirma)
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, out)
	}))
	mux.Handle("DELETE /v1/planes/{id}/espera", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		listo(w, s.DejarDeEsperar(r.Context(), p.ID, r.PathValue("id")))
	}))
	mux.Handle("GET /v1/planes/{id}/todo-bien", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		t, err := s.VerTodoBien(r.Context(), p.ID, r.PathValue("id"))
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, t)
	}))
	mux.Handle("POST /v1/planes/{id}/todo-bien", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		var e struct {
			Contacto int `json:"contacto"`
		}
		if !respuesta.Leer(w, r, &e) {
			return
		}
		t, err := s.ActivarTodoBien(r.Context(), p.ID, r.PathValue("id"), e.Contacto)
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, t)
	}))
	mux.Handle("DELETE /v1/planes/{id}/todo-bien", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		listo(w, s.ApagarTodoBien(r.Context(), p.ID, r.PathValue("id")))
	}))
	mux.Handle("GET /v1/planes/{id}/fotos", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		fs, err := s.Fotos(r.Context(), p.ID, r.PathValue("id"))
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, map[string]any{"fotos": fs})
	}))
	// Subida: cuerpo crudo (la foto), como POST /v1/archivos.
	mux.Handle("POST /v1/planes/{id}/fotos", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		f, err := s.SubirFoto(r.Context(), p.ID, r.PathValue("id"), http.MaxBytesReader(w, r.Body, archivos.Maximo+1))
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusCreated, f)
	}))
	mux.Handle("DELETE /v1/planes/{id}/fotos/{foto}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		listo(w, s.BorrarFoto(r.Context(), p.ID, r.PathValue("id"), r.PathValue("foto")))
	}))
	mux.Handle("POST /v1/planes/{id}/fotos/{foto}/reportar", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		// ?motivo=salgo_yo (quien aparece y no quiere estar) o inapropiada (por defecto).
		listo(w, s.ReportarFoto(r.Context(), p.ID, r.PathValue("id"), r.PathValue("foto"), r.URL.Query().Get("motivo")))
	}))
	mux.Handle("GET /v1/semana", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		sem, err := s.Semana(r.Context(), p.ID, strings.TrimSpace(r.URL.Query().Get("ciudad")))
		if err != nil {
			mal(w, err)
			return
		}
		// Se mira sin DNI (sesion.Mirar), como Ahora y Explorar: sin la dirección exacta.
		if !p.DNI {
			for i := range sem.Planes {
				SinDNI(&sem.Planes[i])
			}
		}
		respuesta.JSON(w, http.StatusOK, sem)
	}))
}
