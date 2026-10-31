package servidor_test

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
)

// perfilCompleto deja a la persona lista para aparecer en el mazo.
func (e *entorno) perfilCompleto(p persona, ciudad string, intenciones ...string) {
	e.verificar(p, false)
	e.pedir(p, "PATCH", "/v1/yo", map[string]any{"ciudad": ciudad, "intenciones": intenciones,
		"intereses": []string{"Truco", "Pádel"}, "fotos": []string{"https://api.caigo.test/v1/archivos/x"}}, http.StatusOK, nil)
}

func ciudadUnica() string { return fmt.Sprintf("Ciudad %d", time.Now().UnixNano()) }

func TestGenteLimiteYPlus(t *testing.T) {
	e := nuevoEntorno(t)
	ciudad := ciudadUnica()
	yo := e.persona("Martina")
	e.perfilCompleto(yo, ciudad, "gente")
	var otros []persona
	for i := range 5 {
		o := e.persona(fmt.Sprintf("P%d", i))
		e.perfilCompleto(o, ciudad, "gente", "planes")
		otros = append(otros, o)
	}
	var mazo struct{ Personas []struct{ ID string } }
	e.pedir(yo, "GET", "/v1/gente/mazo?modo=gente", nil, http.StatusOK, &mazo)
	if len(mazo.Personas) != 5 {
		t.Fatalf("mazo: %d", len(mazo.Personas))
	}
	// Tres sumadas sin Plus; la cuarta pide Plus. Pasar no cuenta.
	e.pedir(yo, "POST", "/v1/gente/"+otros[4].id+"/reaccion", map[string]string{"modo": "gente", "accion": "paso"}, http.StatusOK, nil)
	for i := range 3 {
		e.pedir(yo, "POST", "/v1/gente/"+otros[i].id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusOK, nil)
	}
	e.pedir(yo, "POST", "/v1/gente/"+otros[3].id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusConflict, nil)
	// Lo que ya vi no vuelve al mazo.
	e.pedir(yo, "GET", "/v1/gente/mazo?modo=gente", nil, http.StatusOK, &mazo)
	if len(mazo.Personas) != 1 || mazo.Personas[0].ID != otros[3].id {
		t.Fatalf("mazo después: %+v", mazo)
	}
	// "ME PRENDO" la suma a mi gente.
	var gente struct{ Gente []struct{ ID string } }
	e.pedir(yo, "GET", "/v1/gente", nil, http.StatusOK, &gente)
	if len(gente.Gente) != 3 {
		t.Fatalf("mi gente: %d", len(gente.Gente))
	}
	// Del otro lado: "Te sumaron" bloqueado sin Plus (sólo la foto).
	var rec struct {
		Personas  []struct{ ID, Nombre, Foto string }
		Bloqueada bool
	}
	e.pedir(otros[0], "GET", "/v1/gente/recibidos?modo=gente", nil, http.StatusOK, &rec)
	if !rec.Bloqueada || len(rec.Personas) != 1 || rec.Personas[0].ID != "" || rec.Personas[0].Nombre != "" || rec.Personas[0].Foto == "" {
		t.Fatalf("recibidos sin Plus: %+v", rec)
	}
	// Con Plus no hay límite y se ve quién es.
	e.sistema(`INSERT INTO suscripciones (usuario_id, origen, externo, plan, estado, vence_en) VALUES ($1, 'apple', $2, 'mes', 'activa', now() + interval '30 days')`, yo.id, yo.id)
	e.sistema(`INSERT INTO suscripciones (usuario_id, origen, externo, plan, estado, vence_en) VALUES ($1, 'apple', $2, 'mes', 'activa', now() + interval '30 days')`, otros[0].id, otros[0].id)
	e.pedir(yo, "POST", "/v1/gente/"+otros[3].id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusOK, nil)
	e.pedir(otros[0], "GET", "/v1/gente/recibidos?modo=gente", nil, http.StatusOK, &rec)
	if rec.Bloqueada || rec.Personas[0].Nombre != "Martina" {
		t.Fatalf("recibidos con Plus: %+v", rec)
	}
	var est struct {
		Estado struct {
			Activo     bool
			SumadasHoy int
		}
	}
	e.pedir(yo, "GET", "/v1/plus", nil, http.StatusOK, &est)
	if !est.Estado.Activo || est.Estado.SumadasHoy != 4 {
		t.Fatalf("plus: %+v", est)
	}
}

func TestCitasMutuoAbreElChat(t *testing.T) {
	e := nuevoEntorno(t)
	ciudad := ciudadUnica()
	a, b, c := e.persona("Cami"), e.persona("Agus"), e.persona("Solo amistad")
	e.perfilCompleto(a, ciudad, "citas")
	e.perfilCompleto(b, ciudad, "citas", "planes")
	e.perfilCompleto(c, ciudad, "gente")
	// Quien no eligió Citas no ve el modo ni aparece en él.
	e.pedir(c, "GET", "/v1/gente/mazo?modo=citas", nil, http.StatusConflict, nil)
	var mazo struct{ Personas []struct{ ID string } }
	e.pedir(a, "GET", "/v1/gente/mazo?modo=citas", nil, http.StatusOK, &mazo)
	if len(mazo.Personas) != 1 || mazo.Personas[0].ID != b.id {
		t.Fatalf("mazo de citas: %+v", mazo)
	}
	// "Prendo" no va en Citas.
	e.pedir(a, "POST", "/v1/gente/"+b.id+"/reaccion", map[string]string{"modo": "citas", "accion": "prendo"}, http.StatusBadRequest, nil)
	var r struct{ Mutuo bool }
	e.pedir(a, "POST", "/v1/gente/"+b.id+"/reaccion", map[string]string{"modo": "citas", "accion": "interesa"}, http.StatusOK, &r)
	if r.Mutuo {
		t.Fatal("mutuo antes de tiempo")
	}
	e.pedir(a, "POST", "/v1/privados/"+b.id, map[string]string{"texto": "hola"}, http.StatusForbidden, nil)
	e.pedir(b, "POST", "/v1/gente/"+a.id+"/reaccion", map[string]string{"modo": "citas", "accion": "encanta"}, http.StatusOK, &r)
	if !r.Mutuo {
		t.Fatal("no detectó el mutuo")
	}
	e.pedir(a, "POST", "/v1/privados/"+b.id, map[string]string{"texto": "¡Hola!"}, http.StatusCreated, nil)
}

func TestInvitaciones(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu, fede, planID := planConLu(t, e)
	// El plan tiene 3+1 y Lu ya entró: se agranda para que Fede quepa.
	e.sistema(`UPDATE planes SET total = 6 WHERE id = $1`, planID)
	// Fede no está en el plan: no puede invitar.
	e.pedir(fede, "POST", "/v1/planes/"+planID+"/invitar", map[string]string{"usuarioId": lu.id}, http.StatusForbidden, nil)
	// Lu invita a alguien que ya está: no.
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/invitar", map[string]string{"usuarioId": nico.id}, http.StatusConflict, nil)
	// Nico (organizador) invita a Fede: al aceptar entra directo.
	e.pedir(nico, "POST", "/v1/planes/"+planID+"/invitar", map[string]string{"usuarioId": fede.id}, http.StatusNoContent, nil)
	var inv struct {
		Invitaciones []struct{ PlanID string }
		Bloqueada    bool
	}
	e.pedir(fede, "GET", "/v1/invitaciones", nil, http.StatusOK, &inv)
	if len(inv.Invitaciones) != 1 || inv.Invitaciones[0].PlanID != planID {
		t.Fatalf("invitaciones: %+v", inv)
	}
	var res struct{ Adentro bool }
	e.pedir(fede, "POST", "/v1/invitaciones/"+planID, map[string]bool{"aceptar": true}, http.StatusOK, &res)
	if !res.Adentro {
		t.Fatal("no quedó adentro")
	}
	var p plan
	e.pedir(fede, "GET", "/v1/planes/"+planID, nil, http.StatusOK, &p)
	if p.MiEstado != "aceptada" {
		t.Fatalf("estado: %q", p.MiEstado)
	}
}

func TestPlusConMercadoPago(t *testing.T) {
	// Mercado Pago falso: crea el preapproval y después lo devuelve autorizado.
	var referencia string
	mp := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == "POST" && r.URL.Path == "/preapproval":
			var in map[string]any
			_ = json.NewDecoder(r.Body).Decode(&in)
			referencia = in["external_reference"].(string)
			_, _ = w.Write([]byte(`{"id":"pre-1","init_point":"https://mp.test/pagar"}`))
		case r.Method == "GET" && r.URL.Path == "/preapproval/pre-1":
			fmt.Fprintf(w, `{"id":"pre-1","status":"authorized","external_reference":%q,"next_payment_date":%q}`,
				referencia, time.Now().Add(30*24*time.Hour).Format(time.RFC3339))
		default:
			w.WriteHeader(404)
		}
	}))
	defer mp.Close()
	cfg := &mercadopago.Config{AppID: "1", Secreto: "s", TokenPlataforma: "APP_USR-x", SecretoWebhook: "wh", Base: mp.URL}
	e := nuevoEntornoCon(t, &plus.Tiendas{MP: cfg, Sitio: "https://caigo.test"})
	yo := e.persona("Martu")

	var link struct{ Link string }
	e.pedir(yo, "POST", "/v1/plus/mercadopago", map[string]string{"plan": "mes"}, http.StatusOK, &link)
	if link.Link != "https://mp.test/pagar" || !strings.HasPrefix(referencia, yo.id) {
		t.Fatalf("link: %+v ref %q", link, referencia)
	}
	// Webhook sin firma: no.
	aviso := `{"type":"subscription_preapproval","data":{"id":"pre-1"}}`
	r := httptest.NewRequest("POST", "/v1/webhooks/mercadopago?data.id=pre-1", strings.NewReader(aviso))
	w := httptest.NewRecorder()
	e.h.ServeHTTP(w, r)
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("sin firma: %d", w.Code)
	}
	// Con firma: Plus activo.
	ts := fmt.Sprint(time.Now().UnixMilli())
	m := hmac.New(sha256.New, []byte("wh"))
	m.Write([]byte("id:pre-1;request-id:req-1;ts:" + ts + ";"))
	r = httptest.NewRequest("POST", "/v1/webhooks/mercadopago?data.id=pre-1", strings.NewReader(aviso))
	r.Header.Set("x-signature", "ts="+ts+",v1="+hex.EncodeToString(m.Sum(nil)))
	r.Header.Set("x-request-id", "req-1")
	w = httptest.NewRecorder()
	e.h.ServeHTTP(w, r)
	if w.Code != http.StatusOK {
		t.Fatalf("con firma: %d %s", w.Code, w.Body)
	}
	var est struct {
		Estado struct {
			Activo bool
			Plan   string
		}
	}
	e.pedir(yo, "GET", "/v1/plus", nil, http.StatusOK, &est)
	if !est.Estado.Activo || est.Estado.Plan != "mes" {
		t.Fatalf("plus: %+v", est)
	}
}

func TestPlusMeEncantaDestacadoYFiltros(t *testing.T) {
	e := nuevoEntorno(t)
	ciudad := ciudadUnica()
	yo, a, b := e.persona("Juli"), e.persona("Tomi"), e.persona("Rama")
	for _, p := range []persona{yo, a, b} {
		e.perfilCompleto(p, ciudad, "citas")
	}
	conPlus := func(p persona) {
		e.sistema(`INSERT INTO suscripciones (usuario_id, origen, externo, plan, estado, vence_en) VALUES ($1, 'apple', $2, 'mes', 'activa', now() + interval '30 days')`, p.id, p.id)
	}
	// Sin Plus: un "me encanta" por día; repetirlo sobre la misma persona no cuenta.
	e.pedir(yo, "POST", "/v1/gente/"+a.id+"/reaccion", map[string]string{"modo": "citas", "accion": "encanta"}, http.StatusOK, nil)
	e.pedir(yo, "POST", "/v1/gente/"+a.id+"/reaccion", map[string]string{"modo": "citas", "accion": "encanta"}, http.StatusOK, nil)
	e.pedir(yo, "POST", "/v1/gente/"+b.id+"/reaccion", map[string]string{"modo": "citas", "accion": "encanta"}, http.StatusConflict, nil)
	e.pedir(yo, "POST", "/v1/gente/"+b.id+"/reaccion", map[string]string{"modo": "citas", "accion": "interesa"}, http.StatusOK, nil)
	// Quien recibió el "me encanta" ve primero a quien se lo dio.
	var mazo struct{ Personas []struct{ ID string } }
	e.pedir(a, "GET", "/v1/gente/mazo?modo=citas", nil, http.StatusOK, &mazo)
	if len(mazo.Personas) != 2 || mazo.Personas[0].ID != yo.id {
		t.Fatalf("mazo de quien recibió el me encanta: %+v", mazo)
	}
	var est struct {
		Estado struct {
			EncantasHoy, LimiteEncantas int
			DestacarDesde               *time.Time
		}
	}
	e.pedir(yo, "GET", "/v1/plus", nil, http.StatusOK, &est)
	if est.Estado.EncantasHoy != 1 || est.Estado.LimiteEncantas != 1 {
		t.Fatalf("plus sin pagar: %+v", est)
	}

	// Planes: el destacado va primero. Sin Plus no se destaca ni se usan los filtros finos.
	crear := func(p persona, en time.Duration) string {
		var pl plan
		e.pedir(p, "POST", "/v1/planes", map[string]any{
			"categoria": "previa", "empiezaEn": time.Now().Add(en), "ciudad": ciudad,
			"zona": "Centro", "lat": -37.99, "lng": -57.55, "somos": 2, "faltan": 2, "ingreso": "libre",
		}, http.StatusCreated, &pl)
		return pl.ID
	}
	primero, segundo := crear(a, time.Hour), crear(yo, 2*time.Hour)
	e.pedir(yo, "POST", "/v1/planes/"+segundo+"/destacar", nil, http.StatusForbidden, nil)
	e.pedir(yo, "GET", "/v1/planes/explorar?ciudad="+strings.ReplaceAll(ciudad, " ", "+")+"&verificados=1", nil, http.StatusForbidden, nil)
	e.pedir(yo, "GET", "/v1/planes/explorar?ciudad="+strings.ReplaceAll(ciudad, " ", "+")+"&edadDesde=25", nil, http.StatusForbidden, nil)
	conPlus(yo)
	e.pedir(yo, "POST", "/v1/planes/"+primero+"/destacar", nil, http.StatusNotFound, nil)
	var d struct{ Destacado bool }
	e.pedir(yo, "POST", "/v1/planes/"+segundo+"/destacar", nil, http.StatusOK, &d)
	if !d.Destacado {
		t.Fatal("no quedó destacado")
	}
	// Uno por semana.
	tercero := crear(yo, 3*time.Hour)
	e.pedir(yo, "POST", "/v1/planes/"+tercero+"/destacar", nil, http.StatusConflict, nil)
	var lista struct{ Planes []struct{ ID string } }
	e.pedir(b, "GET", "/v1/planes/explorar?ciudad="+strings.ReplaceAll(ciudad, " ", "+"), nil, http.StatusOK, &lista)
	if len(lista.Planes) != 3 || lista.Planes[0].ID != segundo || lista.Planes[1].ID != primero {
		t.Fatalf("explorar con destacado: %+v", lista)
	}
	e.pedir(yo, "GET", "/v1/plus", nil, http.StatusOK, &est)
	if est.Estado.LimiteEncantas != plus.EncantasPlus || est.Estado.DestacarDesde == nil {
		t.Fatalf("plus pago: %+v", est)
	}
	// Con Plus, los filtros finos andan.
	e.pedir(yo, "GET", "/v1/planes/explorar?ciudad="+strings.ReplaceAll(ciudad, " ", "+")+"&gratis=1&faltan=2", nil, http.StatusOK, &lista)
	if len(lista.Planes) != 3 {
		t.Fatalf("filtros finos: %+v", lista)
	}
	// Y hay 5 "me encanta" por día.
	e.pedir(yo, "POST", "/v1/gente/"+b.id+"/reaccion", map[string]string{"modo": "citas", "accion": "encanta"}, http.StatusOK, nil)
}
