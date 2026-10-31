package lugares

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Rutas del panel de lugares, de las entradas y del tap.
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
	ok := func(w http.ResponseWriter, estado int, v any) error { respuesta.JSON(w, estado, v); return nil }
	listo := func(w http.ResponseWriter) error { w.WriteHeader(http.StatusNoContent); return nil }

	mux.Handle("GET /v1/lugares", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		ls, err := s.Mios(r.Context(), p.ID)
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]any{"lugares": ls})
	}))
	mux.Handle("POST /v1/lugares", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var d Datos
		if !respuesta.Leer(w, r, &d) {
			return nil
		}
		l, err := s.Registrar(r.Context(), p.ID, d)
		if err != nil {
			return err
		}
		return ok(w, http.StatusCreated, l)
	}))
	mux.Handle("GET /v1/lugares/{id}", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		l, err := s.Ver(r.Context(), p.ID, r.PathValue("id"))
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, l)
	}))
	mux.Handle("PUT /v1/lugares/{id}", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var e Edicion
		if !respuesta.Leer(w, r, &e) {
			return nil
		}
		l, err := s.Editar(r.Context(), p.ID, r.PathValue("id"), e)
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, l)
	}))
	mux.Handle("GET /v1/lugares/{id}/planes", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		ps, err := s.Planes(r.Context(), p.ID, r.PathValue("id"), r.URL.Query().Get("cual"), "")
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]any{"planes": ps})
	}))
	mux.Handle("GET /v1/lugares/{id}/planes/{plan}", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		ps, err := s.Planes(r.Context(), p.ID, r.PathValue("id"), "", r.PathValue("plan"))
		if err != nil {
			return err
		}
		if len(ps) == 0 {
			return problema.NoEncontrado("Ese plan no es de tu lugar.")
		}
		return ok(w, http.StatusOK, ps[0])
	}))
	mux.Handle("POST /v1/lugares/{id}/planes", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var pl PlanLugar
		if !respuesta.Leer(w, r, &pl) {
			return nil
		}
		id, err := s.CrearPlan(r.Context(), p.ID, r.PathValue("id"), pl)
		if err != nil {
			return err
		}
		return ok(w, http.StatusCreated, map[string]string{"id": id})
	}))
	mux.Handle("PATCH /v1/lugares/{id}/planes/{plan}", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var cuerpo json.RawMessage
		if !respuesta.Leer(w, r, &cuerpo) {
			return nil
		}
		pl, err := s.EditarPlan(r.Context(), p.ID, r.PathValue("id"), r.PathValue("plan"), cuerpo)
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, pl)
	}))
	mux.Handle("DELETE /v1/lugares/{id}/planes/{plan}", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		if err := s.CancelarPlan(r.Context(), p.ID, r.PathValue("id"), r.PathValue("plan")); err != nil {
			return err
		}
		return listo(w)
	}))
	mux.Handle("POST /v1/lugares/{id}/planes/{plan}/checkin/{usuarioId}", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		v, err := s.CheckinPuerta(r.Context(), p.ID, r.PathValue("id"), r.PathValue("plan"), r.PathValue("usuarioId"))
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, v)
	}))
	mux.Handle("GET /v1/lugares/{id}/planes/{plan}/asistentes", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		as, err := s.Asistentes(r.Context(), p.ID, r.PathValue("id"), r.PathValue("plan"))
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]any{"asistentes": as})
	}))
	mux.Handle("POST /v1/lugares/{id}/validar", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Codigo string `json:"codigo"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		v, err := s.Validar(r.Context(), p.ID, r.PathValue("id"), in.Codigo)
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, v)
	}))
	mux.Handle("GET /v1/lugares/{id}/cobros", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		cs, err := s.Cobros(r.Context(), p.ID, r.PathValue("id"))
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]any{"cobros": cs})
	}))
	mux.Handle("POST /v1/lugares/{id}/mercadopago", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		link, err := s.ConectarMP(r.Context(), p.ID, r.PathValue("id"))
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]string{"link": link})
	}))
	mux.Handle("DELETE /v1/lugares/{id}/mercadopago", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		if err := s.DesconectarMP(r.Context(), p.ID, r.PathValue("id")); err != nil {
			return err
		}
		return listo(w)
	}))
	// La vuelta del OAuth la recibe el panel (lugares.caigo.com.ar/mercadopago/vuelta) y la pasa acá.
	mux.Handle("POST /v1/mercadopago/vuelta", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Codigo string `json:"codigo"`
			Estado string `json:"estado"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		id, err := s.VolverMP(r.Context(), p.ID, in.Codigo, in.Estado)
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]string{"lugarId": id})
	}))
	mux.Handle("GET /v1/lugares/{id}/placas", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		ps, err := s.Placas(r.Context(), p.ID, r.PathValue("id"))
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]any{"placas": ps})
	}))
	mux.Handle("POST /v1/lugares/{id}/placas", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Nombre string `json:"nombre"`
			Uso    string `json:"uso"`
			SUN    bool   `json:"sun"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		pl, err := s.ActivarPlaca(r.Context(), p.ID, r.PathValue("id"), in.Nombre, in.Uso, in.SUN)
		if err != nil {
			return err
		}
		return ok(w, http.StatusCreated, pl)
	}))
	mux.Handle("DELETE /v1/lugares/{id}/placas/{placa}", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		if err := s.DesactivarPlaca(r.Context(), p.ID, r.PathValue("id"), r.PathValue("placa")); err != nil {
			return err
		}
		return listo(w)
	}))
	mux.Handle("POST /v1/lugares/{id}/placas/pedido", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Cantidad int    `json:"cantidad"`
			Tipo     string `json:"tipo"`
			Nota     string `json:"nota"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		pe, err := s.PedirPlacas(r.Context(), p.ID, r.PathValue("id"), in.Cantidad, in.Tipo, in.Nota)
		if err != nil {
			return err
		}
		return ok(w, http.StatusCreated, pe)
	}))
	mux.Handle("GET /v1/lugares/{id}/placas/pedidos", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		ps, err := s.PedidosDelLugar(r.Context(), p.ID, r.PathValue("id"))
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]any{"pedidos": ps})
	}))

	// La persona.
	mux.Handle("POST /v1/planes/{id}/entradas", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Cantidad int16  `json:"cantidad"`
			Modo     string `json:"modo"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		c, err := s.Comprar(r.Context(), p.ID, r.PathValue("id"), in.Cantidad, in.Modo)
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, c)
	}))
	mux.Handle("DELETE /v1/entradas/{id}", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		monto, err := s.CancelarEntrada(r.Context(), p.ID, r.PathValue("id"))
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]int64{"reembolsoCentavos": monto})
	}))
	mux.Handle("GET /v1/pases", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		es, err := s.MisEntradas(r.Context(), p.ID)
		if err != nil {
			return err
		}
		ps, err := s.MisPases(r.Context(), p.ID)
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]any{"entradas": es, "socios": ps})
	}))
	mux.Handle("POST /v1/tap", h(persona, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var l Lectura
		if !respuesta.Leer(w, r, &l) {
			return nil
		}
		res, err := s.Tap(r.Context(), p.ID, l)
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, res)
	}))

	// Equipo.
	mux.Handle("GET /v1/equipo/lugares", h(equipo, func(w http.ResponseWriter, r *http.Request, _ sesion.Persona) error {
		ls, err := s.Todos(r.Context(), r.URL.Query().Get("estado"))
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]any{"lugares": ls})
	}))
	for _, accion := range []string{"suspender", "reactivar"} {
		mux.Handle("POST /v1/equipo/lugares/{id}/"+accion, h(equipo, func(w http.ResponseWriter, r *http.Request, _ sesion.Persona) error {
			var in struct {
				Motivo string `json:"motivo"`
			}
			if !respuesta.Leer(w, r, &in) {
				return nil
			}
			if err := s.Suspender(r.Context(), r.PathValue("id"), accion == "suspender", in.Motivo); err != nil {
				return err
			}
			return listo(w)
		}))
	}
	mux.Handle("GET /v1/equipo/pedidos-placas", h(equipo, func(w http.ResponseWriter, r *http.Request, _ sesion.Persona) error {
		ps, err := s.PedidosPlacas(r.Context())
		if err != nil {
			return err
		}
		return ok(w, http.StatusOK, map[string]any{"pedidos": ps})
	}))
	mux.Handle("POST /v1/equipo/pedidos-placas/{id}/atendido", h(equipo, func(w http.ResponseWriter, r *http.Request, p sesion.Persona) error {
		var in struct {
			Nota string `json:"nota"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		if err := s.AtenderPedidoPlacas(r.Context(), p.ID, r.PathValue("id"), in.Nota); err != nil {
			return err
		}
		return listo(w)
	}))
	mux.Handle("POST /v1/equipo/lugares/{id}", h(equipo, func(w http.ResponseWriter, r *http.Request, _ sesion.Persona) error {
		var in struct {
			Aprobar bool   `json:"aprobar"`
			Motivo  string `json:"motivo"`
		}
		if !respuesta.Leer(w, r, &in) {
			return nil
		}
		if err := s.Revisar(r.Context(), r.PathValue("id"), in.Aprobar, in.Motivo); err != nil {
			return err
		}
		return listo(w)
	}))
}

// AvisoPago encola un pago de Mercado Pago para que el worker lo concilie (con reintentos).
func (s *Servicio) AvisoPago(r *http.Request, a mercadopago.Aviso) error {
	id, err := strconv.ParseInt(a.ID(), 10, 64)
	if err != nil || a.Usuario() == 0 {
		return nil
	}
	return s.App.EnSistema(r.Context(), func(tx pgx.Tx) error {
		return outbox.Publicar(r.Context(), tx, "mp.pago", map[string]int64{"usuario": a.Usuario(), "pago": id})
	})
}

// Manejadores del outbox para el worker.
func (s *Servicio) Manejadores() map[string]outbox.Manejador {
	return map[string]outbox.Manejador{
		"mp.pago": func(ctx context.Context, ev outbox.Evento) error {
			var d struct{ Usuario, Pago int64 }
			if err := json.Unmarshal(ev.Datos, &d); err != nil {
				return nil
			}
			return s.ConciliarPago(ctx, d.Usuario, d.Pago)
		},
		// Plan cancelado (o pagado tarde): se devuelve la entrada entera. Baja: sólo el precio.
		"entrada.reembolsar": func(ctx context.Context, ev outbox.Evento) error {
			// {entradaId, total: true} devuelve todo; {entradaId, centavos} sólo ese monto (baja).
			var d struct {
				EntradaID string `json:"entradaId"`
				Centavos  *int64 `json:"centavos"`
			}
			if err := json.Unmarshal(ev.Datos, &d); err != nil || d.EntradaID == "" {
				return nil
			}
			return s.reembolsar(ctx, d.EntradaID, d.Centavos)
		},
	}
}
