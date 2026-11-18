package servidor_test

import (
	"context"
	"net/http"
	"net/url"
	"strings"
	"testing"
	"time"
)

// Revisión de código del 18/11: lo que se cerró, con su prueba.

// El link «en vivo» deja de mostrar dirección y gente si a quien lo compartió la sacaron del plan o
// el plan se canceló.
func TestEnVivoSoloMientrasSigaEnElPlan(t *testing.T) {
	e := nuevoEntorno(t)
	_, lu, _, planID := planConLu(t, e)
	var link struct{ Link string }
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/en-vivo", nil, http.StatusOK, &link)
	ruta := "/v1/publico/vivo/" + link.Link[strings.LastIndex(link.Link, "/")+1:]
	e.pedir(persona{}, "GET", ruta, nil, http.StatusOK, nil)

	e.sistema(`UPDATE solicitudes SET estado = 'rechazada' WHERE plan_id = $1 AND usuario_id = $2`, planID, lu.id)
	e.pedir(persona{}, "GET", ruta, nil, http.StatusNotFound, nil)

	e.sistema(`UPDATE solicitudes SET estado = 'aceptada' WHERE plan_id = $1 AND usuario_id = $2`, planID, lu.id)
	e.sistema(`UPDATE planes SET cancelado_en = now() WHERE id = $1`, planID)
	e.pedir(persona{}, "GET", ruta, nil, http.StatusNotFound, nil)
}

// En el mazo sólo aparece gente con el DNI aprobado y sin suspender; un perfil suspendido no se ve.
func TestMazoSoloConDNIYSinSuspender(t *testing.T) {
	e := nuevoEntorno(t)
	ciudad := ciudadUnica()
	yo, con, sin, susp := e.persona("Yo"), e.persona("Con"), e.persona("Sin"), e.persona("Susp")
	e.perfilCompleto(yo, ciudad, "gente")
	for _, p := range []persona{con, sin, susp} {
		e.perfilCompleto(p, ciudad, "gente")
	}
	e.sistema(`UPDATE usuarios SET dni_en = NULL WHERE id = $1`, sin.id)
	e.sistema(`UPDATE usuarios SET suspendido_en = now() WHERE id = $1`, susp.id)
	var mazo struct{ Personas []struct{ ID string } }
	e.pedir(yo, "GET", "/v1/gente/mazo?modo=gente", nil, http.StatusOK, &mazo)
	if len(mazo.Personas) != 1 || mazo.Personas[0].ID != con.id {
		t.Fatalf("mazo: %+v", mazo)
	}
	e.pedir(yo, "GET", "/v1/personas/"+susp.id, nil, http.StatusNotFound, nil)
	e.pedir(yo, "GET", "/v1/personas/"+con.id, nil, http.StatusOK, nil)
}

// Quien pidió sumarse y después bloqueó al organizador: su solicitud ya no se ve ni se acepta.
func TestSolicitudConBloqueo(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu := e.persona("Nico"), e.persona("Lu")
	e.verificar(lu, true)
	p := e.planNuevo(nico, map[string]any{"ingreso": "aprobacion"})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	var solID string
	e.valor(`SELECT id::text FROM solicitudes WHERE plan_id = $1 AND usuario_id = $2`, &solID, p.ID, lu.id)
	var sols struct{ Solicitudes []struct{ ID string } }
	e.pedir(nico, "GET", "/v1/planes/"+p.ID+"/solicitudes", nil, http.StatusOK, &sols)
	if len(sols.Solicitudes) != 1 {
		t.Fatalf("antes del bloqueo: %+v", sols)
	}
	e.pedir(lu, "PUT", "/v1/bloqueos/"+nico.id, nil, http.StatusNoContent, nil)
	e.pedir(nico, "GET", "/v1/planes/"+p.ID+"/solicitudes", nil, http.StatusOK, &sols)
	if len(sols.Solicitudes) != 0 {
		t.Fatalf("con bloqueo se ve: %+v", sols)
	}
	e.pedir(nico, "POST", "/v1/solicitudes/"+solID+"/aceptar", nil, http.StatusNotFound, nil)
}

// En el chat del plan, dos personas bloqueadas entre sí no se leen; el resto ve todo.
func TestChatGrupalConBloqueo(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu, fede := e.persona("Nico"), e.persona("Lu"), e.persona("Fede")
	e.verificar(lu, true)
	e.verificar(fede, true)
	p := e.planNuevo(nico, map[string]any{"faltan": 3})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.pedir(fede, "PUT", "/v1/bloqueos/"+lu.id, nil, http.StatusNoContent, nil)
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/mensajes", map[string]string{"texto": "de Lu"}, http.StatusCreated, nil)
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/mensajes", map[string]string{"texto": "de Fede"}, http.StatusCreated, nil)
	textos := func(quien persona) []string {
		var ms struct{ Mensajes []struct{ Texto string } }
		e.pedir(quien, "GET", "/v1/planes/"+p.ID+"/mensajes", nil, http.StatusOK, &ms)
		var out []string
		for _, m := range ms.Mensajes {
			out = append(out, m.Texto)
		}
		return out
	}
	if got := strings.Join(textos(nico), "|"); got != "de Lu|de Fede" {
		t.Fatalf("nico: %s", got)
	}
	if got := strings.Join(textos(lu), "|"); got != "de Lu" {
		t.Fatalf("lu: %s", got)
	}
	if got := strings.Join(textos(fede), "|"); got != "de Fede" {
		t.Fatalf("fede: %s", got)
	}
}

// Con entradas pagas el lugar no endurece la política ni mueve la fecha (sí puede aflojarla), y no
// cancela un plan que ya terminó.
func TestLugarConEntradasPagas(t *testing.T) {
	e, mp, ls, duena, lugarID := lugarConMP(t)
	ana := e.persona("Ana")
	e.verificar(ana, true)
	empieza := time.Now().Add(48 * time.Hour).Truncate(time.Second)
	var c struct{ ID string }
	e.pedir(duena, "POST", "/v1/lugares/"+lugarID+"/planes", map[string]any{"titulo": "Noche", "categoria": "musica",
		"empiezaEn": empieza, "cupo": 20, "cobro": "entrada", "precioCentavos": 800000, "cancelacion": "mod"}, http.StatusCreated, &c)
	ruta := "/v1/lugares/" + lugarID + "/planes/" + c.ID
	// Sin entradas, se puede todo.
	e.pedir(duena, "PATCH", ruta, map[string]any{"cancelacion": "strict"}, http.StatusOK, nil)
	e.pedir(duena, "PATCH", ruta, map[string]any{"cancelacion": "mod"}, http.StatusOK, nil)
	e.pedir(ana, "POST", "/v1/planes/"+c.ID+"/entradas", map[string]int{"cantidad": 1}, http.StatusOK, nil)
	if err := ls.ConciliarOrden(context.Background(), mp.usuario, mp.ultimaOrden()); err != nil {
		t.Fatal(err)
	}
	if c := codigo(t, e, duena, "PATCH", ruta, map[string]any{"cancelacion": "strict"}, http.StatusConflict); c != "con_entradas" {
		t.Fatalf("endurecer: %s", c)
	}
	if c := codigo(t, e, duena, "PATCH", ruta, map[string]any{"empiezaEn": empieza.Add(-24 * time.Hour)}, http.StatusConflict); c != "con_entradas" {
		t.Fatalf("mover la fecha: %s", c)
	}
	e.pedir(duena, "PATCH", ruta, map[string]any{"cancelacion": "flex", "titulo": "Noche larga"}, http.StatusOK, nil)

	// Ya terminó: el lugar no lo cancela (no se devuelve lo que se usó).
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '1 day' WHERE id = $1`, c.ID)
	if c := codigo(t, e, duena, "DELETE", ruta, nil, http.StatusConflict); c != "paso" {
		t.Fatalf("cancelar uno pasado: %s", c)
	}
	if n := e.cuenta(`SELECT count(*) FROM entradas WHERE plan_id = $1 AND estado = 'pagada'`, c.ID); n != 1 {
		t.Fatalf("entradas pagas: %d", n)
	}
}

// Un cobro de la cuenta de Mercado Pago de otro lugar, con la referencia de una entrada ajena, no la
// deja pagada.
func TestCobroDeOtraCuentaNoAcredita(t *testing.T) {
	e, mp, ls, duena, lugarID := lugarConMP(t)
	ana := e.persona("Ana")
	e.verificar(ana, true)
	var p struct{ ID string }
	e.pedir(duena, "POST", "/v1/lugares/"+lugarID+"/planes", map[string]any{"titulo": "Noche ajena", "categoria": "musica",
		"empiezaEn": time.Now().Add(48 * time.Hour), "cupo": 20, "cobro": "entrada", "precioCentavos": 800000}, http.StatusCreated, &p)
	var c struct{ EntradaID string }
	e.pedir(ana, "POST", "/v1/planes/"+p.ID+"/entradas", map[string]int{"cantidad": 1}, http.StatusOK, &c)
	cuentaA := mp.usuario

	// Otro lugar conecta su propia cuenta de Mercado Pago.
	mp.usuario = cuentaA + 1
	otra, equipo := e.persona("Otra dueña"), e.persona("Equipo 2")
	e.alEquipo(equipo)
	var l struct{ ID string }
	e.pedir(otra, "POST", "/v1/lugares", map[string]any{"nombre": "Bar Trucho", "ciudad": "Villa Gesell", "zona": "Centro",
		"direccion": "Av. 2 y Paseo 104", "lat": -37.26, "lng": -56.97}, http.StatusCreated, &l)
	e.pedir(equipo, "POST", "/v1/equipo/lugares/"+l.ID, map[string]any{"aprobar": true}, http.StatusNoContent, nil)
	var con struct{ Link string }
	e.pedir(otra, "POST", "/v1/lugares/"+l.ID+"/mercadopago", nil, http.StatusOK, &con)
	u, _ := url.Parse(con.Link)
	e.pedir(otra, "POST", "/v1/mercadopago/vuelta", map[string]string{"codigo": "c", "estado": u.Query().Get("state")}, http.StatusOK, nil)

	// El pago 99 (con la referencia de la entrada de Ana) llega por la cuenta del otro lugar: no se aplica.
	if err := ls.ConciliarPago(context.Background(), mp.usuario, 99); err != nil {
		t.Fatal(err)
	}
	var estado string
	e.valor(`SELECT estado FROM entradas WHERE id = $1`, &estado, c.EntradaID)
	if estado != "pendiente" {
		t.Fatalf("con la cuenta de otro lugar: %q", estado)
	}
	// Por la cuenta del lugar del plan, sí.
	if err := ls.ConciliarPago(context.Background(), cuentaA, 99); err != nil {
		t.Fatal(err)
	}
	e.valor(`SELECT estado FROM entradas WHERE id = $1`, &estado, c.EntradaID)
	if estado != "pagada" {
		t.Fatalf("con la cuenta del lugar: %q", estado)
	}
}

// Quien ya estaba con «somos 3» y después acepta una invitación del organizador sigue siendo 3.
func TestInvitacionNoPisaLaCantidad(t *testing.T) {
	e := nuevoEntorno(t)
	nico, fede := e.persona("Nico"), e.persona("Fede")
	e.verificar(fede, true)
	p := e.planNuevo(nico, map[string]any{"faltan": 5})
	e.pedir(nico, "POST", "/v1/planes/"+p.ID+"/invitar", map[string]string{"usuarioId": fede.id}, http.StatusNoContent, nil)
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{"cantidad": 3}, http.StatusOK, nil)
	var res struct{ Adentro bool }
	e.pedir(fede, "POST", "/v1/invitaciones/"+p.ID, map[string]bool{"aceptar": true}, http.StatusOK, &res)
	var cantidad int
	e.valor(`SELECT cantidad FROM solicitudes WHERE plan_id = $1 AND usuario_id = $2`, &cantidad, p.ID, fede.id)
	if cantidad != 3 || !res.Adentro {
		t.Fatalf("cantidad: %d %+v", cantidad, res)
	}
}

// El plan de una cuenta suspendida no se ve por link, ni con sesión ni sin ella.
func TestPlanDeSuspendidaNoSeVe(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu := e.persona("Nico"), e.persona("Lu")
	p := e.planNuevo(nico, map[string]any{"visibilidad": "link"})
	e.pedir(lu, "GET", "/v1/planes/"+p.Slug, nil, http.StatusOK, nil)
	e.pedir(persona{}, "GET", "/v1/publico/planes/"+p.Slug, nil, http.StatusOK, nil)
	e.sistema(`UPDATE usuarios SET suspendido_en = now() WHERE id = $1`, nico.id)
	e.pedir(lu, "GET", "/v1/planes/"+p.Slug, nil, http.StatusNotFound, nil)
	e.pedir(persona{}, "GET", "/v1/publico/planes/"+p.Slug, nil, http.StatusNotFound, nil)
}

// «¿Repetimos?»: un plan cancelado no se republica, y a quien hay un bloqueo de por medio no se lo
// suma a mi gente.
func TestRepetimosCanceladoYBloqueo(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu, fede := e.persona("Nico"), e.persona("Lu"), e.persona("Fede")
	e.verificar(lu, true)
	e.verificar(fede, true)
	p := e.planNuevo(nico, map[string]any{"faltan": 3})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.pedir(fede, "PUT", "/v1/bloqueos/"+lu.id, nil, http.StatusNoContent, nil)
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '3 hours' WHERE id = $1`, p.ID)
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/repetimos", map[string]any{"volveria": []map[string]any{
		{"usuarioId": fede.id, "si": true}, {"usuarioId": nico.id, "si": true}}}, http.StatusOK, nil)
	if n := e.cuenta(`SELECT count(*) FROM mi_gente WHERE usuario_id = $1 AND otro_id = $2`, lu.id, fede.id); n != 0 {
		t.Fatal("pasó a mi gente alguien con bloqueo")
	}
	if n := e.cuenta(`SELECT count(*) FROM mi_gente WHERE usuario_id = $1 AND otro_id = $2`, lu.id, nico.id); n != 1 {
		t.Fatal("no pasó Nico a mi gente")
	}

	e.sistema(`UPDATE planes SET cancelado_en = now() WHERE id = $1`, p.ID)
	if c := codigo(t, e, nico, "POST", "/v1/planes/"+p.ID+"/repetimos", map[string]any{"repetir": true}, http.StatusConflict); c != "cancelado" {
		t.Fatalf("repetir un cancelado: %s", c)
	}
	if n := e.cuenta(`SELECT count(*) FROM planes WHERE serie_id = $1`, p.ID); n != 0 {
		t.Fatalf("se republicó: %d", n)
	}
}
