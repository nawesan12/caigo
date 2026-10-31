package planes

import (
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Rutas registra los handlers. Las rutas con sesión pasan por con (sesion.Exigir).
func Rutas(mux *http.ServeMux, s *Servicio, con func(http.Handler) http.Handler, log *slog.Logger) {
	h := func(f func(http.ResponseWriter, *http.Request, sesion.Persona)) http.Handler {
		return con(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			p, _ := sesion.De(r.Context())
			f(w, r, p)
		}))
	}
	mal := func(w http.ResponseWriter, err error) { respuesta.Error(w, log, err) }

	mux.Handle("POST /v1/planes", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		var e Entrada
		if !respuesta.Leer(w, r, &e) {
			return
		}
		plan, err := s.Crear(r.Context(), p.ID, e)
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusCreated, plan)
	}))
	mux.Handle("GET /v1/planes/ahora", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		f, err := filtro(r)
		if err != nil {
			mal(w, err)
			return
		}
		ps, err := s.Ahora(r.Context(), p.ID, f)
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, map[string]any{"planes": ps})
	}))
	mux.Handle("GET /v1/planes/explorar", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		f, err := filtro(r)
		if err != nil {
			mal(w, err)
			return
		}
		ps, err := s.Explorar(r.Context(), p.ID, f)
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, map[string]any{"planes": ps})
	}))
	mux.Handle("GET /v1/planes/mios", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		ps, err := s.Mios(r.Context(), p.ID, r.URL.Query().Get("cual"))
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, map[string]any{"planes": ps})
	}))
	mux.Handle("GET /v1/planes/{id}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		var punto *[2]float64
		if f, err := filtro(r); err == nil && f.Lat != nil && f.Lng != nil {
			punto = &[2]float64{*f.Lat, *f.Lng}
		}
		plan, err := s.Ver(r.Context(), p.ID, r.PathValue("id"), punto)
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, plan)
	}))
	mux.Handle("DELETE /v1/planes/{id}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		if err := s.Cancelar(r.Context(), p.ID, r.PathValue("id")); err != nil {
			mal(w, err)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	mux.Handle("POST /v1/planes/{id}/sumarme", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		var e struct {
			Cantidad int16  `json:"cantidad"`
			Mensaje  string `json:"mensaje"`
		}
		if !respuesta.Leer(w, r, &e) {
			return
		}
		estado, err := s.Sumarse(r.Context(), p.ID, r.PathValue("id"), e.Cantidad, e.Mensaje)
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, map[string]string{"estado": estado})
	}))
	mux.Handle("DELETE /v1/planes/{id}/sumarme", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		if err := s.Salir(r.Context(), p.ID, r.PathValue("id")); err != nil {
			mal(w, err)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	mux.Handle("GET /v1/planes/{id}/solicitudes", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		ss, err := s.Solicitudes(r.Context(), p.ID, r.PathValue("id"))
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, map[string]any{"solicitudes": ss})
	}))
	mux.Handle("POST /v1/solicitudes/{id}/{accion}", h(func(w http.ResponseWriter, r *http.Request, p sesion.Persona) {
		accion := r.PathValue("accion")
		if accion != "aceptar" && accion != "rechazar" {
			problema.Escribir(w, problema.NoEncontrado("Esa ruta no existe."))
			return
		}
		if _, err := s.Responder(r.Context(), p.ID, r.PathValue("id"), accion == "aceptar"); err != nil {
			mal(w, err)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	// Link público (sin sesión): lo que se ve en caigo.com.ar/p/<slug> antes de entrar.
	mux.HandleFunc("GET /v1/publico/planes/{slug}", func(w http.ResponseWriter, r *http.Request) {
		plan, err := s.Publico(r.Context(), r.PathValue("slug"))
		if err != nil {
			mal(w, err)
			return
		}
		respuesta.JSON(w, http.StatusOK, plan)
	})
}

// filtro lee ?lat&lng&radio&categoria&ciudad&desde&hasta&verificados&faltan.
func filtro(r *http.Request) (Filtro, error) {
	q := r.URL.Query()
	var f Filtro
	num := func(k string) (*float64, error) {
		v := q.Get(k)
		if v == "" {
			return nil, nil
		}
		x, err := strconv.ParseFloat(v, 64)
		if err != nil {
			return nil, problema.Invalido(k + " no es un número.")
		}
		return &x, nil
	}
	var err error
	if f.Lat, err = num("lat"); err != nil {
		return f, err
	}
	if f.Lng, err = num("lng"); err != nil {
		return f, err
	}
	if radio, err := num("radio"); err != nil {
		return f, err
	} else if radio != nil {
		f.RadioKm = min(max(*radio, 1), 100)
	}
	f.Categoria = q.Get("categoria")
	if _, ok := Categorias[f.Categoria]; f.Categoria != "" && !ok {
		return f, problema.Invalido("Esa categoría no existe.")
	}
	f.Ciudad = q.Get("ciudad")
	f.SoloVerificados = q.Get("verificados") == "1"
	if v := q.Get("faltan"); v != "" {
		f.FaltanMin, _ = strconv.Atoi(v)
	}
	for k, dst := range map[string]*time.Time{"desde": &f.Desde, "hasta": &f.Hasta} {
		if v := q.Get(k); v != "" {
			t, err := time.Parse(time.RFC3339, v)
			if err != nil {
				return f, problema.Invalido(k + " tiene que ser una fecha RFC 3339.")
			}
			*dst = t
		}
	}
	return f, nil
}
