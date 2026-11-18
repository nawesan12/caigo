package servidor_test

import (
	"context"
	"net/http"
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
