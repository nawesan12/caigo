package servidor_test

import (
	"context"
	"net/http"
	"testing"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/avisos"
)

func TestPedidoDePlacas(t *testing.T) {
	e := nuevoEntorno(t)
	duena, otra, equipo := e.persona("Dueña"), e.persona("Otra"), e.persona("Equipo")
	e.alEquipo(equipo)

	var l struct{ ID string }
	e.pedir(duena, "POST", "/v1/lugares", map[string]any{"nombre": "Bar Tap", "rubro": "Bar", "ciudad": "Mar del Plata",
		"zona": "Centro", "direccion": "San Martín 2500", "lat": -38.0, "lng": -57.55}, http.StatusCreated, &l)
	ruta := "/v1/lugares/" + l.ID + "/placas/pedido"
	pedido := map[string]any{"cantidad": 4, "tipo": "ntag424", "nota": "Dos para la barra"}

	// Sin aprobar no se piden placas; y sólo las pide quien maneja el lugar.
	if c := codigo(t, e, duena, "POST", ruta, pedido, http.StatusConflict); c != "sin_aprobar" {
		t.Fatalf("sin aprobar: %q", c)
	}
	e.pedir(equipo, "POST", "/v1/equipo/lugares/"+l.ID, map[string]any{"aprobar": true}, http.StatusNoContent, nil)
	e.pedir(otra, "POST", ruta, pedido, http.StatusNotFound, nil)
	e.pedir(otra, "GET", "/v1/lugares/"+l.ID+"/placas/pedidos", nil, http.StatusNotFound, nil)

	// Validación: de 1 a 20, común o NTAG 424.
	e.pedir(duena, "POST", ruta, map[string]any{"cantidad": 0, "tipo": "comun"}, http.StatusBadRequest, nil)
	e.pedir(duena, "POST", ruta, map[string]any{"cantidad": 21, "tipo": "comun"}, http.StatusBadRequest, nil)
	e.pedir(duena, "POST", ruta, map[string]any{"cantidad": 2, "tipo": "madera"}, http.StatusBadRequest, nil)

	var p struct {
		ID, Tipo, Nota string
		Cantidad       int
	}
	e.pedir(duena, "POST", ruta, pedido, http.StatusCreated, &p)
	if p.Cantidad != 4 || p.Tipo != "ntag424" || p.Nota != "Dos para la barra" {
		t.Fatalf("pedido: %+v", p)
	}

	// Se avisa al equipo por el outbox, y el manejador anda.
	evs := e.eventos("placas.pedido", p.ID)
	if len(evs) != 1 {
		t.Fatalf("placas.pedido: %d", len(evs))
	}
	if err := (&avisos.Enviador{App: e.app}).Manejadores()["placas.pedido"](context.Background(), evs[0]); err != nil {
		t.Fatal(err)
	}

	// El lugar ve su pedido pendiente.
	type pedidoLugar struct {
		ID, Respuesta string
		AtendidoEn    *string
	}
	var mios struct{ Pedidos []pedidoLugar }
	e.pedir(duena, "GET", "/v1/lugares/"+l.ID+"/placas/pedidos", nil, http.StatusOK, &mios)
	if len(mios.Pedidos) != 1 || mios.Pedidos[0].ID != p.ID || mios.Pedidos[0].AtendidoEn != nil {
		t.Fatalf("pedidos del lugar: %+v", mios)
	}

	// Sólo el equipo ve la cola, con el lugar y quién lo pidió.
	e.pedir(duena, "GET", "/v1/equipo/pedidos-placas", nil, http.StatusForbidden, nil)
	e.pedir(duena, "POST", "/v1/equipo/pedidos-placas/"+p.ID+"/atendido", map[string]string{"nota": "x"}, http.StatusForbidden, nil)
	var cola struct {
		Pedidos []struct {
			ID, LugarID, LugarNombre, PidioNombre string
			AtendidoEn                            *string
		}
	}
	e.pedir(equipo, "GET", "/v1/equipo/pedidos-placas", nil, http.StatusOK, &cola)
	var visto bool
	for _, x := range cola.Pedidos {
		if x.ID == p.ID {
			visto = x.LugarID == l.ID && x.LugarNombre == "Bar Tap" && x.PidioNombre == "Dueña" && x.AtendidoEn == nil
		}
	}
	if !visto {
		t.Fatalf("no aparece el pedido en la cola: %+v", cola)
	}
	e.pedir(equipo, "POST", "/v1/equipo/pedidos-placas/"+p.ID+"/atendido", map[string]string{"nota": "Salen el lunes por Andreani"}, http.StatusNoContent, nil)
	e.pedir(equipo, "POST", "/v1/equipo/pedidos-placas/"+p.ID+"/atendido", map[string]string{"nota": "otra vez"}, http.StatusNotFound, nil)

	e.pedir(duena, "GET", "/v1/lugares/"+l.ID+"/placas/pedidos", nil, http.StatusOK, &mios)
	if mios.Pedidos[0].AtendidoEn == nil || mios.Pedidos[0].Respuesta != "Salen el lunes por Andreani" {
		t.Fatalf("atendido: %+v", mios.Pedidos[0])
	}

	// Como mucho 3 pendientes a la vez.
	for range 3 {
		e.pedir(duena, "POST", ruta, map[string]any{"cantidad": 1, "tipo": "comun"}, http.StatusCreated, nil)
	}
	if c := codigo(t, e, duena, "POST", ruta, map[string]any{"cantidad": 1, "tipo": "comun"}, http.StatusConflict); c != "demasiados" {
		t.Fatalf("pendientes: %q", c)
	}
	// Y el tope por ruta: 10 por día, cuenten o no (ya van 9).
	e.pedir(duena, "POST", ruta, map[string]any{"cantidad": 1, "tipo": "comun"}, http.StatusConflict, nil)
	e.pedir(duena, "POST", ruta, map[string]any{"cantidad": 1, "tipo": "comun"}, http.StatusTooManyRequests, nil)
}
