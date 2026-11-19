package servidor_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
)

// Gente y Citas de vuelta en Ahora (19/11): sin DNI, lo propio se abre (recibidos, registrar una compra) y el
// mazo y las reacciones siguen pidiendo el DNI.
func TestGenteSinDNIRecibidosYCompras(t *testing.T) {
	e := nuevoEntorno(t)
	mar := e.personaSinDNI("Mar")
	e.pedir(mar, "GET", "/v1/gente/recibidos?modo=gente", nil, http.StatusOK, nil)
	if c := codigo(t, e, mar, "DELETE", "/v1/gente/recibidos/"+mar.id+"?modo=gente", nil, http.StatusForbidden); c != "falta_dni" {
		t.Fatalf("descartar sin DNI: %s", c)
	}
	if c := codigo(t, e, mar, "GET", "/v1/gente/mazo?modo=gente", nil, http.StatusForbidden); c != "falta_dni" {
		t.Fatalf("mazo sin DNI: %s", c)
	}
	// Registrar una compra de la tienda nunca falla por el DNI (falla por el JWS, que acá es basura).
	for _, r := range []string{"/v1/plus/apple", "/v1/plus/google"} {
		if w := e.crudo(mar, "POST", r, map[string]string{"jws": "x", "token": "x"}); w.Code == http.StatusForbidden {
			t.Fatalf("%s sin DNI: %d %s", r, w.Code, w.Body.String())
		}
	}
}

// Citas sólo entre quienes eligieron Citas; Gente desde el perfil sigue andando.
func TestReaccionarConsentimientoDeCitas(t *testing.T) {
	e := nuevoEntorno(t)
	ciudad := ciudadUnica()
	yo, conCitas, sinCitas := e.persona("Yo"), e.persona("ConCitas"), e.persona("SinCitas")
	e.perfilCompleto(yo, ciudad, "gente", "citas")
	e.perfilCompleto(conCitas, ciudad, "citas")
	e.perfilCompleto(sinCitas, ciudad, "gente")
	// A quien no está en Citas: como si no existiera.
	e.pedir(yo, "POST", "/v1/gente/"+sinCitas.id+"/reaccion", map[string]string{"modo": "citas", "accion": "interesa"}, http.StatusNotFound, nil)
	// Quien no está en Citas no puede reaccionar en Citas.
	if c := codigo(t, e, sinCitas, "POST", "/v1/gente/"+conCitas.id+"/reaccion", map[string]string{"modo": "citas", "accion": "interesa"}, http.StatusConflict); c != "sin_citas" {
		t.Fatalf("reaccionar sin Citas: %s", c)
	}
	// Gente: alcanza con que la otra persona tenga DNI.
	e.pedir(sinCitas, "POST", "/v1/gente/"+yo.id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusOK, nil)
	// «Les interesás» de quien no está en Citas viene vacío.
	var rec struct{ Personas []struct{ ID string } }
	e.pedir(sinCitas, "GET", "/v1/gente/recibidos?modo=citas", nil, http.StatusOK, &rec)
	if len(rec.Personas) != 0 {
		t.Fatalf("les interesás sin Citas: %+v", rec)
	}
	// Quien no está en Citas no ve que el otro busca Citas.
	var perfil struct{ Intenciones []string }
	e.pedir(sinCitas, "GET", "/v1/personas/"+conCitas.id, nil, http.StatusOK, &perfil)
	for _, i := range perfil.Intenciones {
		if i == "citas" {
			t.Fatalf("intenciones a la vista: %v", perfil.Intenciones)
		}
	}
}

// Repetir una reacción no gasta, no avisa de nuevo y no da 409 en el límite; el mutuo de Gente es Mi gente
// de los dos lados (la regla del privado).
func TestReaccionarIdempotenteYMutuoDeGente(t *testing.T) {
	e := nuevoEntorno(t)
	ciudad := ciudadUnica()
	yo, a, b, c, d := e.persona("Yo"), e.persona("A"), e.persona("B"), e.persona("C"), e.persona("D")
	for _, p := range []persona{yo, a, b, c, d} {
		e.perfilCompleto(p, ciudad, "gente")
	}
	for _, p := range []persona{a, b, c} {
		e.pedir(yo, "POST", "/v1/gente/"+p.id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusOK, nil)
	}
	// En el límite, repetir la misma da 200 y no publica otro aviso.
	antes := len(e.eventos("interes.nuevo", c.id))
	e.pedir(yo, "POST", "/v1/gente/"+c.id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusOK, nil)
	if n := len(e.eventos("interes.nuevo", c.id)); n != antes {
		t.Fatalf("repetir publicó %d avisos", n-antes)
	}
	var r struct{ Mutuo bool }
	// Caso 1: D le dio «me prendo» a A y después lo sacó de Mi gente: cuando A le reacciona, no es mutuo
	// (el privado no se abriría).
	e.pedir(d, "POST", "/v1/gente/"+a.id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusOK, nil)
	e.pedir(d, "DELETE", "/v1/gente/"+a.id, nil, http.StatusNoContent, nil)
	e.pedir(a, "POST", "/v1/gente/"+d.id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusOK, &r)
	if r.Mutuo {
		t.Fatal("D sacó a A de su gente: no es mutuo")
	}
	// Caso 2: B sumó a A desde el perfil después de un plan juntos (mi_gente, sin «me prendo»): cuando A le
	// reacciona, ya pueden hablar.
	e.sistema(`INSERT INTO mi_gente (usuario_id, otro_id) VALUES ($1, $2)`, b.id, a.id)
	e.pedir(a, "POST", "/v1/gente/"+b.id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusOK, &r)
	if !r.Mutuo {
		t.Fatal("A y B se tienen en Mi gente: es mutuo")
	}
}

// crudo hace el pedido y devuelve la respuesta sin mirar el estado.
func (e *entorno) crudo(p persona, metodo, ruta string, cuerpo any) *httptest.ResponseRecorder {
	e.t.Helper()
	b, _ := json.Marshal(cuerpo)
	r := httptest.NewRequest(metodo, ruta, bytes.NewReader(b))
	if p.token != "" {
		r.Header.Set("Authorization", "Bearer "+p.token)
	}
	w := httptest.NewRecorder()
	e.h.ServeHTTP(w, r)
	return w
}

// Plus por Mercado Pago: los 7 días gratis son una vez, y cancelar no corta lo ya pago.
func TestPlusPruebaUnaVezYCancelarGuardaElPeriodo(t *testing.T) {
	e := nuevoEntorno(t)
	yo := e.persona("Yo")
	var est struct{ PruebaDisponible bool }
	e.pedir(yo, "GET", "/v1/plus", nil, http.StatusOK, &struct{ Estado any }{&est})
	if !est.PruebaDisponible {
		t.Fatal("sin suscripciones, la prueba está disponible")
	}
	ps := &plus.Servicio{App: e.app}
	vence := time.Now().Add(10 * 24 * time.Hour).Truncate(time.Second)
	externo := "pre-" + yo.id
	if err := ps.Guardar(context.Background(), yo.id, "mercadopago", externo, "mes", "activa", vence, true); err != nil {
		t.Fatal(err)
	}
	// El aviso de cancelación llega sin vencimiento: el período pago sigue.
	if err := ps.Guardar(context.Background(), yo.id, "mercadopago", externo, "mes", "cancelada", time.Time{}, false); err != nil {
		t.Fatal(err)
	}
	var activo struct {
		Activo           bool
		PruebaDisponible bool
	}
	e.pedir(yo, "GET", "/v1/plus", nil, http.StatusOK, &struct{ Estado any }{&activo})
	if !activo.Activo || activo.PruebaDisponible {
		t.Fatalf("cancelada con período pago: %+v", activo)
	}
}

// El «me encanta» del día no se libera yendo y viniendo a «me interesa»: volver a darlo a la misma persona
// no gasta otro, y a otra persona da el límite.
func TestEncantaNoSeLiberaCambiandoAInteresa(t *testing.T) {
	e := nuevoEntorno(t)
	ciudad := ciudadUnica()
	yo, a, b := e.persona("Yo"), e.persona("A"), e.persona("B")
	for _, p := range []persona{yo, a, b} {
		e.perfilCompleto(p, ciudad, "citas")
	}
	reaccion := func(_ persona, accion string) map[string]string {
		return map[string]string{"modo": "citas", "accion": accion}
	}
	e.pedir(yo, "POST", "/v1/gente/"+a.id+"/reaccion", reaccion(a, "encanta"), http.StatusOK, nil)
	e.pedir(yo, "POST", "/v1/gente/"+a.id+"/reaccion", reaccion(a, "interesa"), http.StatusOK, nil)
	if c := codigo(t, e, yo, "POST", "/v1/gente/"+b.id+"/reaccion", reaccion(b, "encanta"), http.StatusConflict); c != "limite_encanta" {
		t.Fatalf("segundo me encanta del día sin Plus: %s", c)
	}
	antes := len(e.eventos("interes.nuevo", a.id))
	e.pedir(yo, "POST", "/v1/gente/"+a.id+"/reaccion", reaccion(a, "encanta"), http.StatusOK, nil)
	if n := len(e.eventos("interes.nuevo", a.id)); n != antes {
		t.Fatalf("volver a dar me encanta avisó %d veces", n-antes)
	}
}
