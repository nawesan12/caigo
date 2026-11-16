package servidor_test

import (
	"context"
	"net/http"
	"strings"
	"testing"
	"time"
)

// Ids mal formados y datos fuera de rango son 4xx, no 500 (los de producción del 16/11).
func TestDatosMalosSon4xx(t *testing.T) {
	e := nuevoEntorno(t)
	lu := e.persona("Lu")
	e.pedir(lu, "POST", "/v1/planes/no-uuid/sumarme", map[string]any{}, http.StatusNotFound, nil)
	e.pedir(lu, "GET", "/v1/personas/x", nil, http.StatusNotFound, nil)
	e.pedir(lu, "PUT", "/v1/gente/"+lu.id, nil, http.StatusBadRequest, nil)
	e.pedir(lu, "POST", "/v1/reportes", map[string]string{"usuarioId": "x", "motivo": "otro"}, http.StatusNotFound, nil)
	e.pedir(lu, "POST", "/v1/reportes", map[string]string{"usuarioId": "00000000-0000-0000-0000-000000000001", "motivo": "otro"},
		http.StatusNotFound, nil)
	e.pedir(lu, "PUT", "/v1/bloqueos/x", nil, http.StatusNotFound, nil)
	e.pedir(lu, "PUT", "/v1/bloqueos/00000000-0000-0000-0000-000000000001", nil, http.StatusNotFound, nil)
	// Sin DNI, la fecha se valida (con DNI se ignora).
	sin := e.personaSinDNI("Sin")
	e.pedir(sin, "PATCH", "/v1/yo", map[string]string{"nacimiento": "1800-01-01"}, http.StatusUnprocessableEntity, nil)
	// Topes de la tabla en el plan.
	e.pedir(lu, "POST", "/v1/planes", map[string]any{"categoria": "juego", "empiezaEn": time.Now().Add(2 * time.Hour),
		"ciudad": "Mar del Plata", "lat": -38.0, "lng": -57.55, "somos": 1, "faltan": 2, "nivel": strings.Repeat("x", 31)},
		http.StatusBadRequest, nil)
	// Una foto que no se subió a Caigo, no.
	e.pedir(lu, "POST", "/v1/planes", map[string]any{"categoria": "juego", "empiezaEn": time.Now().Add(2 * time.Hour),
		"ciudad": "Mar del Plata", "lat": -38.0, "lng": -57.55, "somos": 1, "faltan": 2, "foto": "https://otro.sitio/x.jpg"},
		http.StatusBadRequest, nil)
	e.pedir(lu, "PATCH", "/v1/yo", map[string]any{"fotos": []string{"https://api.caigo.test/v1/archivos/00000000-0000-0000-0000-000000000001"}},
		http.StatusBadRequest, nil)
	e.pedir(lu, "PATCH", "/v1/yo", map[string]any{"intereses": []string{strings.Repeat("x", 41)}}, http.StatusBadRequest, nil)
}

// Sin edad no se saltea el rango del plan; con rango y en el rango, sí entra. Las rutas públicas no
// dan ids ni edades.
func TestRangoDeEdadYPublico(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu := e.persona("Nico Pérez"), e.persona("Lu")
	e.verificar(nico, true)
	e.sistema(`UPDATE usuarios SET selfie_en = now(), edad = NULL WHERE id = $1`, lu.id)
	p := e.planNuevo(nico, map[string]any{"edadMin": 20, "edadMax": 30, "ingreso": "libre"})
	if c := codigo(t, e, lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusUnprocessableEntity); c != "falta_edad" {
		t.Fatalf("sin edad: %s", c)
	}
	e.verificar(lu, true)
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)

	var pub map[string]any
	e.pedir(persona{}, "GET", "/v1/publico/planes/"+p.Slug, nil, http.StatusOK, &pub)
	org := pub["organizador"].(map[string]any)
	if _, ok := org["id"]; ok || org["edad"] != nil || org["nombre"] != "Nico" {
		t.Fatalf("organizador público: %v", org)
	}
	for _, v := range pub["van"].([]any) {
		if _, ok := v.(map[string]any)["id"]; ok {
			t.Fatalf("van público con id: %v", v)
		}
	}
}

// El organizador bloquea a alguien aceptado: sale del plan y del chat.
func TestBloquearSacaDelPlan(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu, _, planID := planConLu(t, e)
	e.pedir(lu, "GET", "/v1/planes/"+planID+"/mensajes", nil, http.StatusOK, nil)
	e.pedir(nico, "PUT", "/v1/bloqueos/"+lu.id, nil, http.StatusNoContent, nil)
	e.pedir(lu, "GET", "/v1/planes/"+planID+"/mensajes", nil, http.StatusForbidden, nil)
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/mensajes", map[string]string{"texto": "hola"}, http.StatusForbidden, nil)
	var estado string
	e.valor(`SELECT estado FROM solicitudes WHERE plan_id = $1 AND usuario_id = $2`, &estado, planID, lu.id)
	if estado != "rechazada" {
		t.Fatalf("sigue en el plan: %s", estado)
	}
}

// Un plan que ya terminó no se cancela; un plan cancelado no acepta solicitudes.
func TestPlanPasadoYCancelado(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu := e.persona("Nico"), e.persona("Lu")
	e.verificar(lu, true)
	p := e.planNuevo(nico, map[string]any{"ingreso": "aprobacion"})
	var sol struct{ Estado string }
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, &sol)
	var solID string
	e.valor(`SELECT id::text FROM solicitudes WHERE plan_id = $1 AND usuario_id = $2`, &solID, p.ID, lu.id)
	e.sistema(`UPDATE planes SET cancelado_en = now() WHERE id = $1`, p.ID)
	if c := codigo(t, e, nico, "POST", "/v1/solicitudes/"+solID+"/aceptar", nil, http.StatusConflict); c != "cancelado" {
		t.Fatalf("aceptar en un cancelado: %s", c)
	}
	viejo := e.planNuevo(nico, nil)
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '1 day' WHERE id = $1`, viejo.ID)
	if c := codigo(t, e, nico, "DELETE", "/v1/planes/"+viejo.ID, nil, http.StatusConflict); c != "paso" {
		t.Fatalf("cancelar uno pasado: %s", c)
	}
	// «¿Repetimos? Sí» de un plan de hace un mes: el nuevo no queda en el pasado, y dos «Sí» dan el mismo.
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '30 days' WHERE id = $1`, viejo.ID)
	var r1, r2 struct {
		Nuevo struct {
			ID        string
			EmpiezaEn time.Time
		}
	}
	e.pedir(nico, "POST", "/v1/planes/"+viejo.ID+"/repetimos", map[string]any{"repetir": true}, http.StatusOK, &r1)
	e.pedir(nico, "POST", "/v1/planes/"+viejo.ID+"/repetimos", map[string]any{"repetir": true}, http.StatusOK, &r2)
	if r1.Nuevo.ID == "" || r1.Nuevo.ID != r2.Nuevo.ID || !r1.Nuevo.EmpiezaEn.After(time.Now()) {
		t.Fatalf("repetimos: %+v %+v", r1, r2)
	}
}

// Entradas: la compra nueva anula la pendiente anterior; si la anulada se paga igual (o el plan se
// canceló antes del pago), se devuelve entera. Con la entrada paga, «salir» pide cancelar la entrada.
func TestEntradaAnuladaPagadaSeDevuelve(t *testing.T) {
	e, mp, ls, duena, lugarID := lugarConMP(t)
	ana := e.persona("Ana")
	e.verificar(ana, true)
	var c struct{ ID string }
	e.pedir(duena, "POST", "/v1/lugares/"+lugarID+"/planes", map[string]any{"titulo": "Noche", "categoria": "musica",
		"empiezaEn": time.Now().Add(48 * time.Hour), "cupo": 20, "cobro": "entrada", "precioCentavos": 800000}, http.StatusCreated, &c)
	e.pedir(ana, "POST", "/v1/planes/"+c.ID+"/entradas", map[string]int{"cantidad": 1}, http.StatusOK, nil)
	primera := mp.ultimaOrden()
	e.pedir(ana, "POST", "/v1/planes/"+c.ID+"/entradas", map[string]int{"cantidad": 1}, http.StatusOK, nil)
	if n := e.cuenta(`SELECT count(*) FROM entradas WHERE plan_id = $1 AND usuario_id = $2 AND estado = 'pendiente'`, c.ID, ana.id); n != 1 {
		t.Fatalf("pendientes: %d", n)
	}
	// Llega el pago de la primera (ya anulada): se marca pagada y se encola la devolución entera.
	if err := ls.ConciliarOrden(context.Background(), mp.usuario, primera); err != nil {
		t.Fatal(err)
	}
	var anulada string
	e.valor(`SELECT id::text FROM entradas WHERE mp_orden = $1`, &anulada, primera)
	if evs := e.eventos("entrada.reembolsar", anulada); len(evs) != 1 {
		t.Fatalf("devolución de la anulada: %d", len(evs))
	}
	// Se paga la segunda: entra, y para irse tiene que cancelar la entrada.
	if err := ls.ConciliarOrden(context.Background(), mp.usuario, mp.ultimaOrden()); err != nil {
		t.Fatal(err)
	}
	if c := codigo(t, e, ana, "DELETE", "/v1/planes/"+c.ID+"/sumarme", nil, http.StatusConflict); c != "con_entrada" {
		t.Fatalf("salir con entrada: %s", c)
	}
	// Mercado Pago no se desconecta con entradas vivas.
	if c := codigo(t, e, duena, "DELETE", "/v1/lugares/"+lugarID+"/mercadopago", nil, http.StatusConflict); c != "con_entradas" {
		t.Fatalf("desconectar: %s", c)
	}
	var cod string
	e.valor(`SELECT codigo FROM entradas WHERE plan_id = $1 AND estado = 'pagada' AND usuario_id = $2`, &cod, c.ID, ana.id)
	if len(cod) != len("TK-123456") {
		t.Fatalf("código: %s", cod)
	}
}
