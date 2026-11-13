package servidor_test

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/url"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/avisos"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/lugares"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/cifrado"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
)

// valor lee un dato de la base (como sistema).
func (e *entorno) valor(q string, dst any, args ...any) {
	e.t.Helper()
	if err := e.app.EnSistema(context.Background(), func(tx pgx.Tx) error {
		return tx.QueryRow(context.Background(), q, args...).Scan(dst)
	}); err != nil {
		e.t.Fatalf("%s: %v", q, err)
	}
}

// eventos devuelve los eventos del outbox de un tipo que mencionan un id (plan, entrada, persona).
func (e *entorno) eventos(tipo, id string) []outbox.Evento {
	e.t.Helper()
	var out []outbox.Evento
	if err := e.app.EnSistema(context.Background(), func(tx pgx.Tx) error {
		rows, err := tx.Query(context.Background(), `SELECT id, tipo, datos, intentos FROM outbox
			WHERE tipo = $1 AND datos::text LIKE '%' || $2 || '%' ORDER BY id`, tipo, id)
		if err != nil {
			return err
		}
		out, err = pgx.CollectRows(rows, pgx.RowToStructByPos[outbox.Evento])
		return err
	}); err != nil {
		e.t.Fatal(err)
	}
	return out
}

func (e *entorno) planNuevo(p persona, extra map[string]any) plan {
	e.t.Helper()
	// En Villa Gesell: los planes de estos tests no llenan el Ahora de Mar del Plata de los otros.
	cuerpo := map[string]any{"categoria": "juego", "empiezaEn": time.Now().Add(2 * time.Hour), "ciudad": "Villa Gesell",
		"zona": "Centro", "lat": -37.2633, "lng": -56.9733, "somos": 2, "faltan": 2, "ingreso": "libre", "soloVerificados": false}
	for k, v := range extra {
		cuerpo[k] = v
	}
	var out plan
	e.pedir(p, "POST", "/v1/planes", cuerpo, http.StatusCreated, &out)
	return out
}

func codigo(t *testing.T, e *entorno, p persona, metodo, ruta string, cuerpo any, estado int) string {
	t.Helper()
	var pr struct{ Code string }
	e.pedir(p, metodo, ruta, cuerpo, estado, &pr)
	return pr.Code
}

func TestGeneroEnLosPlanes(t *testing.T) {
	e := nuevoEntorno(t)
	org, ana, beto := e.persona("Org"), e.persona("Ana"), e.persona("Beto")
	e.verificar(ana, false)
	e.verificar(beto, false)
	mujeres := e.planNuevo(org, map[string]any{"genero": "mujeres"})
	diversas := e.planNuevo(org, map[string]any{"genero": "diversidades"})

	// «Solo mujeres»: sin confirmarlo en el pedido, no entra.
	if c := codigo(t, e, ana, "POST", "/v1/planes/"+mujeres.ID+"/sumarme", map[string]any{}, http.StatusUnprocessableEntity); c != "confirmar_mujer" {
		t.Fatalf("mujeres sin confirmar: %q", c)
	}
	// Las versiones viejas mandan "genero" y "diversidad" en el perfil: se aceptan y no se guardan.
	var yo map[string]any
	e.pedir(ana, "PATCH", "/v1/yo", map[string]any{"genero": "mujer", "diversidad": true}, http.StatusOK, &yo)
	if av, _ := yo["avisos"].(map[string]any); av["planesCerca"] != true || av["tuGente"] != true {
		t.Fatalf("yo: %+v", yo)
	}
	for _, k := range []string{"genero", "diversidad"} {
		if _, ok := yo[k]; ok {
			t.Fatalf("yo trae %s: %+v", k, yo)
		}
	}
	if c := codigo(t, e, ana, "POST", "/v1/planes/"+mujeres.ID+"/sumarme", map[string]any{}, http.StatusUnprocessableEntity); c != "confirmar_mujer" {
		t.Fatalf("género guardado: %q", c)
	}
	// Confirmar una cosa no sirve para la otra.
	if c := codigo(t, e, ana, "POST", "/v1/planes/"+diversas.ID+"/sumarme", map[string]any{"mujer": true}, http.StatusUnprocessableEntity); c != "confirmar_diversidad" {
		t.Fatalf("diversidades: %q", c)
	}
	// Lo confirma al sumarse: entra, y no queda guardado en ningún lado.
	e.pedir(ana, "POST", "/v1/planes/"+mujeres.ID+"/sumarme", map[string]any{"mujer": true}, http.StatusOK, nil)
	e.pedir(beto, "POST", "/v1/planes/"+diversas.ID+"/sumarme", map[string]any{"diversidad": true}, http.StatusOK, nil)

	// El género no se ve en el perfil ajeno.
	var otro map[string]any
	e.pedir(org, "GET", "/v1/personas/"+beto.id, nil, http.StatusOK, &otro)
	if _, ok := otro["genero"]; ok {
		t.Fatal("el género de Beto se ve desde afuera")
	}
	if px, _ := otro["proximos"].([]any); len(px) != 1 {
		t.Fatalf("próximos de Beto: %v", otro["proximos"])
	}
}

func TestEditarYSacarGente(t *testing.T) {
	e := nuevoEntorno(t)
	org, lu, fede := e.persona("Org"), e.persona("Lu"), e.persona("Fede")
	e.verificar(lu, false)
	e.verificar(fede, false)
	p := e.planNuevo(org, map[string]any{"direccion": "Güemes 2850"})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)

	// Van 3 (los 2 del organizador y Lu): el cupo no puede bajar de ahí.
	if c := codigo(t, e, org, "PATCH", "/v1/planes/"+p.ID, map[string]any{"total": 2}, http.StatusConflict); c != "cupo_menor" {
		t.Fatalf("cupo: %q", c)
	}
	e.pedir(lu, "PATCH", "/v1/planes/"+p.ID, map[string]any{"titulo": "Mío"}, http.StatusNotFound, nil)
	e.pedir(org, "PATCH", "/v1/planes/"+p.ID, map[string]any{"cualquiera": 1}, http.StatusBadRequest, nil)

	// Cambiar sólo el título no avisa; cambiar la hora sí.
	var editado struct {
		Titulo, Descripcion string
		Total               int
		EmpiezaEn           time.Time
		Direccion           *struct{ Texto string }
	}
	e.pedir(org, "PATCH", "/v1/planes/"+p.ID, map[string]any{"titulo": "Truco y pizza", "total": 5}, http.StatusOK, &editado)
	if editado.Titulo != "Truco y pizza" || editado.Total != 5 || editado.Direccion == nil || editado.Direccion.Texto != "Güemes 2850" {
		t.Fatalf("editado: %+v", editado)
	}
	if n := len(e.eventos("plan.cambiado", p.ID)); n != 0 {
		t.Fatalf("aviso sin cambio de hora: %d", n)
	}
	nueva := time.Now().Add(5 * time.Hour).Truncate(time.Second)
	e.pedir(org, "PATCH", "/v1/planes/"+p.ID, map[string]any{"empiezaEn": nueva}, http.StatusOK, &editado)
	if !editado.EmpiezaEn.Equal(nueva) || editado.Titulo != "Truco y pizza" {
		t.Fatalf("nueva hora: %+v", editado)
	}
	if n := len(e.eventos("plan.cambiado", p.ID)); n != 1 {
		t.Fatalf("avisos plan.cambiado: %d", n)
	}

	// Sacar a Lu: queda afuera y no puede volver a pedir.
	e.pedir(org, "DELETE", "/v1/planes/"+p.ID+"/personas/"+org.id, nil, http.StatusConflict, nil)
	e.pedir(lu, "DELETE", "/v1/planes/"+p.ID+"/personas/"+org.id, nil, http.StatusNotFound, nil)
	e.pedir(org, "DELETE", "/v1/planes/"+p.ID+"/personas/"+fede.id, nil, http.StatusNotFound, nil)
	e.pedir(org, "DELETE", "/v1/planes/"+p.ID+"/personas/"+lu.id, nil, http.StatusNoContent, nil)
	if n := len(e.eventos("solicitud.sacada", lu.id)); n != 1 {
		t.Fatalf("aviso solicitud.sacada: %d", n)
	}
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusConflict, nil)

	// Rechazar avisa.
	q := e.planNuevo(org, map[string]any{"ingreso": "aprobacion"})
	e.pedir(fede, "POST", "/v1/planes/"+q.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	var ss struct{ Solicitudes []struct{ ID string } }
	e.pedir(org, "GET", "/v1/planes/"+q.ID+"/solicitudes", nil, http.StatusOK, &ss)
	e.pedir(org, "POST", "/v1/solicitudes/"+ss.Solicitudes[0].ID+"/rechazar", nil, http.StatusNoContent, nil)
	if n := len(e.eventos("solicitud.rechazada", fede.id)); n != 1 {
		t.Fatalf("aviso solicitud.rechazada: %d", n)
	}

	// Cancelar avisa a los que iban (y a los pendientes).
	e.pedir(org, "DELETE", "/v1/planes/"+p.ID, nil, http.StatusNoContent, nil)
	e.pedir(org, "PATCH", "/v1/planes/"+p.ID, map[string]any{"titulo": "x"}, http.StatusConflict, nil)
	if n := len(e.eventos("plan.cancelado", p.ID)); n != 1 {
		t.Fatalf("aviso plan.cancelado: %d", n)
	}
}

// lugarConMP da de alta un lugar aprobado con Mercado Pago conectado (OAuth falso).
func lugarConMP(t *testing.T) (e *entorno, mp *mpFalso, ls *lugares.Servicio, duena persona, lugarID string) {
	t.Helper()
	mp = nuevoMP(t)
	cfg := &mercadopago.Config{AppID: "1", Secreto: "s", SecretoWebhook: "wh", Base: mp.URL}
	cifra, _ := cifrado.Nuevo(base64.StdEncoding.EncodeToString(make([]byte, 32)))
	e = nuevoEntornoAjustado(t, func(d *servidor.Dependencias) {
		d.Tiendas = &plus.Tiendas{MP: cfg}
		d.Cifra = cifra
	})
	duena, equipo := e.persona("Dueña"), e.persona("Equipo")
	e.alEquipo(equipo)
	var l struct{ ID string }
	e.pedir(duena, "POST", "/v1/lugares", map[string]any{"nombre": "Bar Cumbre", "ciudad": "Villa Gesell", "zona": "Centro",
		"direccion": "Av. 3 y Paseo 105", "lat": -37.26, "lng": -56.97}, http.StatusCreated, &l)
	e.pedir(equipo, "POST", "/v1/equipo/lugares/"+l.ID, map[string]any{"aprobar": true}, http.StatusNoContent, nil)
	var con struct{ Link string }
	e.pedir(duena, "POST", "/v1/lugares/"+l.ID+"/mercadopago", nil, http.StatusOK, &con)
	u, _ := url.Parse(con.Link)
	e.pedir(duena, "POST", "/v1/mercadopago/vuelta", map[string]string{"codigo": "c", "estado": u.Query().Get("state")}, http.StatusOK, nil)
	return e, mp, &lugares.Servicio{App: e.app, MP: cfg, Cifra: cifra}, duena, l.ID
}

func TestReembolsosYCancelaciones(t *testing.T) {
	e, mp, ls, duena, lugarID := lugarConMP(t)
	martina := e.persona("Martina")
	e.verificar(martina, false)
	crear := func(politica string, en time.Duration) string {
		var c struct{ ID string }
		e.pedir(duena, "POST", "/v1/lugares/"+lugarID+"/planes", map[string]any{"titulo": "Noche " + politica, "categoria": "musica",
			"empiezaEn": time.Now().Add(en), "cupo": 20, "aceptaGrupos": true, "cobro": "entrada", "precioCentavos": 800000,
			"cancelacion": politica}, http.StatusCreated, &c)
		return c.ID
	}
	// Compra 1 entrada y la paga (el worker concilia la última orden).
	comprar := func(planID string) string {
		e.pedir(martina, "POST", "/v1/planes/"+planID+"/entradas", map[string]int{"cantidad": 1}, http.StatusOK, nil)
		if err := ls.ConciliarOrden(context.Background(), mp.usuario, mp.ultimaOrden()); err != nil {
			t.Fatal(err)
		}
		var id string
		e.valor(`SELECT id::text FROM entradas WHERE plan_id = $1 AND usuario_id = $2 AND estado = 'pagada'`, &id, planID, martina.id)
		return id
	}
	type reembolso struct{ ReembolsoCentavos int64 }

	// flex, a 48 h: devuelve lo del lugar ($8.000), no el cargo.
	flex := comprar(crear("flex", 48*time.Hour))
	ordenFlex := mp.ultimaOrden()
	var r reembolso
	e.pedir(e.persona("Otra"), "DELETE", "/v1/entradas/"+flex, nil, http.StatusNotFound, nil)
	e.pedir(martina, "DELETE", "/v1/entradas/"+flex, nil, http.StatusOK, &r)
	// Devolución parcial de la orden, por su transacción.
	if r.ReembolsoCentavos != 800000 || len(mp.reembolsos) != 1 ||
		mp.reembolsos[0] != `{"transactions":[{"amount":"8000.00","id":"tx-`+strings.TrimPrefix(ordenFlex, "ord-")+`"}]}` {
		t.Fatalf("flex: %+v %q", r, mp.reembolsos)
	}
	var estado string
	e.valor(`SELECT estado FROM entradas WHERE id = $1`, &estado, flex)
	if estado != "reembolsada" {
		t.Fatalf("estado flex: %q", estado)
	}
	e.pedir(martina, "DELETE", "/v1/entradas/"+flex, nil, http.StatusConflict, nil)
	// Un aviso viejo de la orden (que en Mercado Pago sigue cobrada, devuelta en parte) no la reabre.
	if err := ls.ConciliarOrden(context.Background(), mp.usuario, ordenFlex); err != nil {
		t.Fatal(err)
	}
	e.valor(`SELECT estado FROM entradas WHERE id = $1`, &estado, flex)
	if estado != "reembolsada" {
		t.Fatalf("estado flex tras el aviso: %q", estado)
	}

	// mod, a 48 h (< 72 h): no se devuelve nada, pero libera el lugar.
	modPlan := crear("mod", 48*time.Hour)
	mod := comprar(modPlan)
	e.pedir(martina, "DELETE", "/v1/entradas/"+mod, nil, http.StatusOK, &r)
	e.valor(`SELECT estado FROM entradas WHERE id = $1`, &estado, mod)
	if r.ReembolsoCentavos != 0 || len(mp.reembolsos) != 1 || estado != "cancelada" {
		t.Fatalf("mod: %+v %q %q", r, estado, mp.reembolsos)
	}
	var sol string
	e.valor(`SELECT estado FROM solicitudes WHERE plan_id = $1 AND usuario_id = $2`, &sol, modPlan, martina.id)
	if sol != "cancelada" {
		t.Fatalf("solicitud mod: %q", sol)
	}

	// strict: sin reembolso.
	strict := comprar(crear("strict", 48*time.Hour))
	if c := codigo(t, e, martina, "DELETE", "/v1/entradas/"+strict, nil, http.StatusConflict); c != "sin_reembolso" {
		t.Fatalf("strict: %q", c)
	}

	// El lugar cancela un plan con entradas pagas: se devuelve el 100 % (cargo incluido).
	otroPlan := crear("flex", 30*time.Hour)
	otra := comprar(otroPlan)
	// No se puede cambiar el precio si ya hay entradas pagas; otros campos sí.
	if c := codigo(t, e, duena, "PATCH", "/v1/lugares/"+lugarID+"/planes/"+otroPlan, map[string]any{"precioCentavos": 900000}, http.StatusConflict); c != "con_entradas" {
		t.Fatalf("editar con entradas: %q", c)
	}
	var pl struct {
		Titulo string
		Cupo   int
	}
	e.pedir(duena, "PATCH", "/v1/lugares/"+lugarID+"/planes/"+otroPlan, map[string]any{"titulo": "Noche nueva", "cupo": 30}, http.StatusOK, &pl)
	if pl.Titulo != "Noche nueva" || pl.Cupo != 30 {
		t.Fatalf("plan del lugar editado: %+v", pl)
	}
	e.pedir(e.persona("Ajena"), "DELETE", "/v1/lugares/"+lugarID+"/planes/"+otroPlan, nil, http.StatusNotFound, nil)
	e.pedir(duena, "DELETE", "/v1/lugares/"+lugarID+"/planes/"+otroPlan, nil, http.StatusNoContent, nil)
	cancelado := e.eventos("plan.cancelado", otroPlan)
	if len(cancelado) != 1 || !json.Valid(cancelado[0].Datos) {
		t.Fatalf("plan.cancelado: %+v", cancelado)
	}
	var datos struct{ Usuarios []string }
	_ = json.Unmarshal(cancelado[0].Datos, &datos)
	if !slices.Contains(datos.Usuarios, martina.id) {
		t.Fatalf("plan.cancelado no avisa a Martina: %s", cancelado[0].Datos)
	}
	evs := e.eventos("entrada.reembolsar", otra)
	if len(evs) != 1 {
		t.Fatalf("entrada.reembolsar: %d", len(evs))
	}
	// Lo hace el worker (dos veces: es idempotente).
	for range 2 {
		if err := ls.Manejadores()["entrada.reembolsar"](context.Background(), evs[0]); err != nil {
			t.Fatal(err)
		}
	}
	e.valor(`SELECT estado FROM entradas WHERE id = $1`, &estado, otra)
	if estado != "reembolsada" || len(mp.reembolsos) != 2 || mp.reembolsos[1] != "" {
		t.Fatalf("reembolso total: %q %q", estado, mp.reembolsos)
	}
	var devuelto int64
	e.valor(`SELECT reembolso_centavos FROM entradas WHERE id = $1`, &devuelto, otra)
	if devuelto != 860000 {
		t.Fatalf("devuelto: %d", devuelto)
	}
	// Y el aviso de cancelado sale sin error (sin dispositivos no manda nada).
	env := &avisos.Enviador{App: e.app}
	if err := env.Manejadores()["plan.cancelado"](context.Background(), cancelado[0]); err != nil {
		t.Fatal(err)
	}
}

func TestCheckinEnElLugar(t *testing.T) {
	e, _, _, duena, lugarID := lugarConMP(t)
	ana, beto := e.persona("Ana"), e.persona("Beto")
	e.verificar(ana, false)
	e.verificar(beto, false)
	var c struct{ ID string }
	e.pedir(duena, "POST", "/v1/lugares/"+lugarID+"/planes", map[string]any{"titulo": "Taller gratis", "categoria": "taller",
		"empiezaEn": time.Now().Add(time.Hour), "cupo": 10, "cobro": "gratis", "beneficio": "Un café"}, http.StatusCreated, &c)
	// Planes gratis de un lugar: se "compra" sin pagar y quedan adentro.
	e.pedir(ana, "POST", "/v1/planes/"+c.ID+"/entradas", map[string]int{"cantidad": 1}, http.StatusOK, nil)
	e.pedir(beto, "POST", "/v1/planes/"+c.ID+"/entradas", map[string]int{"cantidad": 1}, http.StatusOK, nil)

	var as struct {
		Asistentes []struct {
			UsuarioID, Nombre string
			Checkin           *time.Time
		}
	}
	e.pedir(duena, "GET", "/v1/lugares/"+lugarID+"/planes/"+c.ID+"/asistentes", nil, http.StatusOK, &as)
	if len(as.Asistentes) != 2 || as.Asistentes[0].UsuarioID == "" {
		t.Fatalf("asistentes: %+v", as)
	}
	ruta := "/v1/lugares/" + lugarID + "/planes/" + c.ID + "/checkin/"
	e.pedir(ana, "POST", ruta+ana.id, nil, http.StatusNotFound, nil) // no es del lugar
	var v struct {
		Nombre, Beneficio string
		Repetida          bool
	}
	e.pedir(duena, "POST", ruta+ana.id, nil, http.StatusOK, &v)
	if v.Nombre != "Ana" || v.Beneficio != "Un café" || v.Repetida {
		t.Fatalf("checkin: %+v", v)
	}
	e.pedir(duena, "POST", ruta+ana.id, nil, http.StatusOK, &v)
	if !v.Repetida {
		t.Fatal("el segundo check-in no avisó")
	}
	e.pedir(duena, "POST", ruta+duena.id, nil, http.StatusNotFound, nil)

	// Beto muestra el QR de la app y el lugar lo valida.
	var qr struct{ Codigo string }
	e.pedir(beto, "GET", "/v1/planes/"+c.ID+"/qr", nil, http.StatusOK, &qr)
	e.pedir(duena, "POST", "/v1/lugares/"+lugarID+"/validar", map[string]string{"codigo": qr.Codigo}, http.StatusOK, &v)
	if v.Nombre != "Beto" || v.Repetida {
		t.Fatalf("validar QR: %+v", v)
	}
	var metodo string
	e.valor(`SELECT checkin_metodo FROM solicitudes WHERE plan_id = $1 AND usuario_id = $2`, &metodo, c.ID, beto.id)
	if metodo != "puerta" {
		t.Fatalf("método: %q", metodo)
	}
}

func TestBloqueosYBuscar(t *testing.T) {
	e := nuevoEntorno(t)
	yo, otro, org := e.persona("Yo"), e.persona("Otro"), e.persona("Org")
	e.pedir(yo, "PUT", "/v1/bloqueos/"+otro.id, nil, http.StatusNoContent, nil)
	var bs struct{ Bloqueos []struct{ ID, Nombre string } }
	e.pedir(yo, "GET", "/v1/bloqueos", nil, http.StatusOK, &bs)
	if len(bs.Bloqueos) != 1 || bs.Bloqueos[0].ID != otro.id || bs.Bloqueos[0].Nombre != "Otro" {
		t.Fatalf("bloqueos: %+v", bs)
	}
	e.pedir(yo, "DELETE", "/v1/bloqueos/"+otro.id, nil, http.StatusNoContent, nil)
	e.pedir(yo, "GET", "/v1/bloqueos", nil, http.StatusOK, &bs)
	if len(bs.Bloqueos) != 0 {
		t.Fatalf("después de desbloquear: %+v", bs)
	}

	// q (sin tildes ni mayúsculas) y zona.
	unico := "Ñandú " + time.Now().Format("150405.000")
	p := e.planNuevo(org, map[string]any{"titulo": "Truco " + unico, "zona": "Güemes"})
	esta := func(ruta string) bool {
		var ps struct{ Planes []plan }
		e.pedir(yo, "GET", ruta, nil, http.StatusOK, &ps)
		return slices.ContainsFunc(ps.Planes, func(x plan) bool { return x.ID == p.ID })
	}
	if !esta("/v1/planes/explorar?q=" + url.QueryEscape("nandu "+unico[len("Ñandú "):])) {
		t.Fatal("q no encuentra el plan")
	}
	if esta("/v1/planes/explorar?q=" + url.QueryEscape("no-existe-"+unico)) {
		t.Fatal("q encuentra cualquier cosa")
	}
	if !esta("/v1/planes/explorar?zona=guemes&q=" + url.QueryEscape(unico[len("Ñandú "):])) {
		t.Fatal("zona no encuentra el plan")
	}
	if esta("/v1/planes/explorar?zona=centro&q=" + url.QueryEscape(unico[len("Ñandú "):])) {
		t.Fatal("zona no filtra")
	}
	var pub struct{ Planes []plan }
	e.pedir(persona{}, "GET", "/v1/publico/planes/ahora?q="+url.QueryEscape(unico), nil, http.StatusOK, &pub)
	if len(pub.Planes) != 1 {
		t.Fatalf("q sin sesión: %d", len(pub.Planes))
	}
	var zs struct{ Zonas []string }
	e.pedir(persona{}, "GET", "/v1/publico/zonas?ciudad=Mar+del+Plata", nil, http.StatusOK, &zs)
	if !slices.Contains(zs.Zonas, "Güemes") || !slices.Contains(zs.Zonas, "Punta Mogotes") {
		t.Fatalf("zonas: %v", zs.Zonas)
	}

	// Versión mínima de las apps.
	var v struct{ MinAndroid, MinIos int }
	e.pedir(persona{}, "GET", "/v1/version", nil, http.StatusOK, &v)
}

func TestBackoffice(t *testing.T) {
	e := nuevoEntorno(t)
	nadie, eq := e.persona("Cualquiera"), e.persona("Moderación")
	e.alEquipo(eq)
	unico := "Persona" + time.Now().Format("150405999")
	sospechosa := e.persona(unico)
	for _, r := range []string{"/v1/equipo/usuarios", "/v1/equipo/planes", "/v1/equipo/lugares", "/v1/equipo/metricas"} {
		e.pedir(nadie, "GET", r, nil, http.StatusForbidden, nil)
	}
	var us struct{ Usuarios []struct{ ID, Nombre string } }
	e.pedir(eq, "GET", "/v1/equipo/usuarios?q="+unico, nil, http.StatusOK, &us)
	if len(us.Usuarios) != 1 || us.Usuarios[0].ID != sospechosa.id {
		t.Fatalf("usuarios: %+v", us)
	}
	e.pedir(nadie, "POST", "/v1/equipo/usuarios/"+sospechosa.id+"/suspender", map[string]string{"motivo": "x"}, http.StatusForbidden, nil)
	e.pedir(eq, "POST", "/v1/equipo/usuarios/"+sospechosa.id+"/suspender", map[string]string{}, http.StatusBadRequest, nil)
	e.pedir(eq, "POST", "/v1/equipo/usuarios/"+sospechosa.id+"/suspender", map[string]string{"motivo": "Spam"}, http.StatusNoContent, nil)
	e.pedir(sospechosa, "GET", "/v1/yo", nil, http.StatusForbidden, nil)
	e.pedir(eq, "POST", "/v1/equipo/usuarios/"+sospechosa.id+"/reactivar", map[string]string{}, http.StatusNoContent, nil)
	e.pedir(sospechosa, "GET", "/v1/yo", nil, http.StatusOK, nil)

	// Plan semilla a nombre de otra persona, y el equipo lo cancela.
	var p plan
	e.pedir(eq, "POST", "/v1/equipo/planes", map[string]any{"organizadorId": sospechosa.id, "categoria": "deporte",
		"titulo": "Fútbol " + unico, "empiezaEn": time.Now().Add(3 * time.Hour), "ciudad": "Villa Gesell", "zona": "Centro",
		"lat": -37.26, "lng": -56.97, "somos": 5, "faltan": 5}, http.StatusCreated, &p)
	var org struct{ Organizador struct{ ID string } }
	e.pedir(sospechosa, "GET", "/v1/planes/"+p.ID, nil, http.StatusOK, &org)
	if org.Organizador.ID != sospechosa.id {
		t.Fatalf("organizador de la semilla: %+v", org)
	}
	e.pedir(eq, "POST", "/v1/equipo/planes", map[string]any{"organizadorId": "00000000-0000-0000-0000-000000000000"}, http.StatusBadRequest, nil)
	var ps struct{ Planes []struct{ ID string } }
	e.pedir(eq, "GET", "/v1/equipo/planes?q="+url.QueryEscape("futbol "+unico), nil, http.StatusOK, &ps)
	if len(ps.Planes) != 1 || ps.Planes[0].ID != p.ID {
		t.Fatalf("planes: %+v", ps)
	}
	e.pedir(eq, "POST", "/v1/equipo/planes/"+p.ID+"/cancelar", map[string]string{"motivo": "Falso"}, http.StatusNoContent, nil)
	e.pedir(eq, "POST", "/v1/equipo/planes/"+p.ID+"/cancelar", map[string]string{"motivo": "Falso"}, http.StatusNotFound, nil)
	var motivo string
	e.valor(`SELECT cancelado_motivo FROM planes WHERE id = $1`, &motivo, p.ID)
	if motivo != "Falso" || len(e.eventos("plan.cancelado", p.ID)) != 1 {
		t.Fatalf("cancelado por el equipo: %q", motivo)
	}
	e.pedir(sospechosa, "POST", "/v1/reportes", map[string]any{"planId": p.ID, "motivo": "otro"}, http.StatusNoContent, nil)
	e.pedir(eq, "GET", "/v1/equipo/planes?cual=reportados&q="+p.ID, nil, http.StatusOK, &ps)
	if len(ps.Planes) != 1 {
		t.Fatalf("reportados: %+v", ps)
	}

	// Origen: se guarda una sola vez y cuenta en las métricas.
	e.pedir(sospechosa, "PATCH", "/v1/yo", map[string]any{"origen": "utm:instagram"}, http.StatusOK, nil)
	e.pedir(sospechosa, "PATCH", "/v1/yo", map[string]any{"origen": "p:otro-plan"}, http.StatusOK, nil)
	e.pedir(sospechosa, "PATCH", "/v1/yo", map[string]any{"origen": "con espacios"}, http.StatusBadRequest, nil)
	var origen string
	e.valor(`SELECT origen FROM usuarios WHERE id = $1`, &origen, sospechosa.id)
	if origen != "utm:instagram" {
		t.Fatalf("origen: %q", origen)
	}
	var ms struct {
		Semanas []struct {
			Desde          string
			Publicados     int
			AltasPorOrigen map[string]int
		}
	}
	e.pedir(eq, "GET", "/v1/equipo/metricas", nil, http.StatusOK, &ms)
	if len(ms.Semanas) != 8 || ms.Semanas[0].Publicados < 1 || ms.Semanas[0].AltasPorOrigen["utm:instagram"] < 1 {
		t.Fatalf("métricas: %+v", ms.Semanas[0])
	}

	// Lugares: todos o por estado; suspender sólo uno aprobado.
	var l struct{ ID string }
	e.pedir(nadie, "POST", "/v1/lugares", map[string]any{"nombre": "Lugar " + unico, "ciudad": "Mar del Plata",
		"direccion": "Colón 1", "lat": -38.0, "lng": -57.55}, http.StatusCreated, &l)
	var ls struct{ Lugares []struct{ ID, Estado string } }
	e.pedir(eq, "GET", "/v1/equipo/lugares?estado=pendiente", nil, http.StatusOK, &ls)
	if !slices.ContainsFunc(ls.Lugares, func(x struct{ ID, Estado string }) bool { return x.ID == l.ID }) {
		t.Fatal("el lugar pendiente no aparece")
	}
	e.pedir(eq, "POST", "/v1/equipo/lugares/"+l.ID+"/suspender", map[string]string{"motivo": "x"}, http.StatusNotFound, nil)
	e.pedir(eq, "POST", "/v1/equipo/lugares/"+l.ID, map[string]any{"aprobar": true}, http.StatusNoContent, nil)
	e.pedir(eq, "POST", "/v1/equipo/lugares/"+l.ID+"/suspender", map[string]string{"motivo": "Reclamos"}, http.StatusNoContent, nil)
	e.pedir(eq, "GET", "/v1/equipo/lugares?estado=suspendido", nil, http.StatusOK, &ls)
	if !slices.ContainsFunc(ls.Lugares, func(x struct{ ID, Estado string }) bool { return x.ID == l.ID }) {
		t.Fatal("el lugar suspendido no aparece")
	}
	e.pedir(eq, "POST", "/v1/equipo/lugares/"+l.ID+"/reactivar", map[string]string{}, http.StatusNoContent, nil)

	// Lista de espera (pública, con tope por IP).
	mail := unico + "@caigo.test"
	e.pedir(persona{}, "POST", "/v1/publico/espera", map[string]string{"email": mail, "ciudad": "Pinamar"}, http.StatusNoContent, nil)
	e.pedir(persona{}, "POST", "/v1/publico/espera", map[string]string{"email": mail, "ciudad": "Pinamar"}, http.StatusNoContent, nil)
	e.pedir(persona{}, "POST", "/v1/publico/espera", map[string]string{"email": "no-es-mail"}, http.StatusBadRequest, nil)
	var n int
	e.valor(`SELECT count(*) FROM lista_espera WHERE lower(email) = lower($1)`, &n, mail)
	if n != 1 {
		t.Fatalf("lista de espera: %d", n)
	}
}

func TestRecurrencia(t *testing.T) {
	e := nuevoEntorno(t)
	org := e.persona("Org")
	a := e.planNuevo(org, map[string]any{"recurrente": true, "titulo": "Fútbol de los jueves"})
	// El plan fue hace un día y una hora: la próxima fecha (a 6 días menos una hora) ya toca.
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '25 hours' WHERE id = $1`, a.ID)
	serie := func() int {
		var n int
		e.valor(`SELECT count(*) FROM planes WHERE coalesce(serie_id, id) = (SELECT coalesce(serie_id, id) FROM planes WHERE id = $1)`, &n, a.ID)
		return n
	}
	ahora := time.Now()
	ps := &planes.Servicio{App: e.app, Reloj: func() time.Time { return ahora }}
	for range 2 { // idempotente
		if _, err := ps.Recurrencia(context.Background()); err != nil {
			t.Fatal(err)
		}
	}
	if n := serie(); n != 2 {
		t.Fatalf("serie después de la primera vuelta: %d", n)
	}
	var b struct {
		ID       string
		Somos    int
		Recurr   bool `json:"recurrente"`
		MiEstado string
	}
	var bID string
	e.valor(`SELECT id::text FROM planes WHERE serie_id = (SELECT serie_id FROM planes WHERE id = $1) AND id <> $1`, &bID, a.ID)
	e.pedir(org, "GET", "/v1/planes/"+bID, nil, http.StatusOK, &b)
	if b.Somos != 2 || !b.Recurr || b.MiEstado != "organizador" {
		t.Fatalf("plan repetido: %+v", b)
	}
	var dif float64
	e.valor(`SELECT extract(epoch FROM b.empieza_en - a.empieza_en)::float8 FROM planes a, planes b WHERE a.id = $1 AND b.id = $2`, &dif, a.ID, bID)
	if dif != 7*24*3600 {
		t.Fatalf("la próxima fecha no es una semana después: %v s", dif)
	}
	if len(e.eventos("plan.publicado", bID)) != 1 {
		t.Fatal("el plan repetido no se publicó")
	}

	// Una semana después, sigue.
	ahora = time.Now().AddDate(0, 0, 7)
	if _, err := ps.Recurrencia(context.Background()); err != nil {
		t.Fatal(err)
	}
	if n := serie(); n != 3 {
		t.Fatalf("serie a la semana: %d", n)
	}
	// "¿Repetimos? No" corta la serie.
	e.pedir(org, "POST", "/v1/planes/"+a.ID+"/repetimos", map[string]any{"repetir": false}, http.StatusOK, nil)
	ahora = time.Now().AddDate(0, 0, 21)
	if _, err := ps.Recurrencia(context.Background()); err != nil {
		t.Fatal(err)
	}
	if n := serie(); n != 3 {
		t.Fatalf("serie cortada: %d", n)
	}
}

func TestAvisosProgramados(t *testing.T) {
	e := nuevoEntorno(t)
	org, lu := e.persona("Org"), e.persona("Lu")
	e.verificar(lu, false)
	inicio := time.Now()
	p := e.planNuevo(org, map[string]any{"empiezaEn": inicio.Add(3 * time.Hour), "duracionMin": 120})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)

	var ahora time.Time
	pr := &avisos.Programados{App: e.app, Reloj: func() time.Time { return ahora }}
	pasada := func(t2 time.Time) {
		ahora = t2
		for range 2 { // idempotente
			if _, err := pr.Pasada(context.Background()); err != nil {
				t.Fatal(err)
			}
		}
	}
	// Antes de las 2 h, nada.
	pasada(inicio.Add(30 * time.Minute))
	if n := len(e.eventos("plan.recordatorio", p.ID)); n != 0 {
		t.Fatalf("recordatorio temprano: %d", n)
	}
	// 1 h 30 antes: recordatorio a Lu (no al organizador).
	pasada(inicio.Add(90 * time.Minute))
	rec := e.eventos("plan.recordatorio", p.ID)
	if len(rec) != 1 {
		t.Fatalf("recordatorios: %d", len(rec))
	}
	var d struct{ UsuarioID, Momento string }
	_ = json.Unmarshal(rec[0].Datos, &d)
	if d.UsuarioID != lu.id || d.Momento != "2h" {
		t.Fatalf("recordatorio: %s", rec[0].Datos)
	}
	env := &avisos.Enviador{App: e.app, Reloj: func() time.Time { return ahora }}
	if err := env.Manejadores()["plan.recordatorio"](context.Background(), rec[0]); err != nil {
		t.Fatal(err)
	}
	// 15 min antes: se abre el check-in (los dos).
	pasada(inicio.Add(165 * time.Minute))
	if n := len(e.eventos("plan.recordatorio", p.ID)); n != 3 {
		t.Fatalf("recordatorios con check-in: %d", n)
	}
	// 1 h después del final: ¿Repetimos? al organizador.
	pasada(inicio.Add(3*time.Hour + 2*time.Hour + 61*time.Minute))
	rep := e.eventos("plan.repetimos", p.ID)
	if len(rep) != 1 {
		t.Fatalf("repetimos: %d", len(rep))
	}
	_ = json.Unmarshal(rep[0].Datos, &d)
	if d.UsuarioID != org.id {
		t.Fatalf("repetimos a: %s", rep[0].Datos)
	}

	// Ubicación: redondeada a ~1 km y borrada a las 24 h.
	e.pedir(lu, "PUT", "/v1/yo/ubicacion", map[string]float64{"lat": -38.01234, "lng": -57.54321}, http.StatusNoContent, nil)
	e.pedir(lu, "PUT", "/v1/yo/ubicacion", map[string]float64{"lat": 200}, http.StatusBadRequest, nil)
	var lat float64
	e.valor(`SELECT lat FROM ubicaciones WHERE usuario_id = $1`, &lat, lu.id)
	if lat != -38.01 {
		t.Fatalf("lat guardada: %v", lat)
	}
	pasada(time.Now().Add(25 * time.Hour))
	var n int
	e.valor(`SELECT count(*) FROM ubicaciones WHERE usuario_id = $1`, &n, lu.id)
	if n != 0 {
		t.Fatal("la ubicación no se borró a las 24 h")
	}
}

func TestAvisosDeTuGenteYCerca(t *testing.T) {
	e := nuevoEntorno(t)
	org, gente, cerca, bloqueo, callada := e.persona("Org"), e.persona("Gente"), e.persona("Cerca"), e.persona("Bloqueo"), e.persona("Callada")
	// Gente, Bloqueo y Callada tienen a Org en su gente; Bloqueo además lo bloqueó (después de sumarlo).
	for _, p := range []persona{gente, bloqueo, callada} {
		e.sistema(`INSERT INTO mi_gente (usuario_id, otro_id) VALUES ($1, $2)`, p.id, org.id)
	}
	e.sistema(`INSERT INTO bloqueos (usuario_id, bloqueado_id) VALUES ($1, $2)`, bloqueo.id, org.id)
	e.pedir(callada, "PATCH", "/v1/yo", map[string]any{"avisos": map[string]bool{"tuGente": false}}, http.StatusOK, nil)
	// Cerca está a ~1 km y le gusta el truco (como a Org).
	e.pedir(org, "PATCH", "/v1/yo", map[string]any{"intereses": []string{"Truco"}}, http.StatusOK, nil)
	e.pedir(cerca, "PATCH", "/v1/yo", map[string]any{"intereses": []string{"truco", "Surf"}}, http.StatusOK, nil)
	e.pedir(cerca, "PUT", "/v1/yo/ubicacion", map[string]float64{"lat": -37.27, "lng": -56.97}, http.StatusNoContent, nil)

	ahora := time.Now()
	repartir := func(id string) (g, c []string) {
		g, c, err := avisos.Repartir(context.Background(), e.app, id, ahora)
		if err != nil {
			t.Fatal(err)
		}
		return g, c
	}
	p1 := e.planNuevo(org, map[string]any{"empiezaEn": ahora.Add(time.Hour)})
	g, c := repartir(p1.ID)
	if !slices.Contains(g, gente.id) || slices.Contains(g, bloqueo.id) || slices.Contains(g, callada.id) {
		t.Fatalf("tu gente: %v", g)
	}
	if !slices.Contains(c, cerca.id) || slices.Contains(c, gente.id) {
		t.Fatalf("cerca: %v", c)
	}
	// Otra vuelta del mismo evento no repite.
	if g, c := repartir(p1.ID); len(g)+len(c) != 0 {
		t.Fatalf("repetido: %v %v", g, c)
	}
	// Topes: 3 de tu gente por día, 1 de cerca.
	for i := 2; i <= 4; i++ {
		p := e.planNuevo(org, map[string]any{"empiezaEn": ahora.Add(time.Hour)})
		g, c := repartir(p.ID)
		if slices.Contains(g, gente.id) != (i <= 3) {
			t.Fatalf("plan %d, tu gente: %v", i, g)
		}
		if slices.Contains(c, cerca.id) {
			t.Fatalf("plan %d: cerca pasó el tope", i)
		}
	}
	// Un plan que empieza en más de 3 h no avisa a los de cerca.
	otra := e.persona("Otra cerca")
	e.pedir(otra, "PATCH", "/v1/yo", map[string]any{"intereses": []string{"Truco"}}, http.StatusOK, nil)
	e.pedir(otra, "PUT", "/v1/yo/ubicacion", map[string]float64{"lat": -37.27, "lng": -56.97}, http.StatusNoContent, nil)
	lejos := e.planNuevo(org, map[string]any{"empiezaEn": ahora.Add(5 * time.Hour)})
	if _, c := repartir(lejos.ID); slices.Contains(c, otra.id) {
		t.Fatalf("cerca con un plan de dentro de 5 h: %v", c)
	}
	pronto := e.planNuevo(org, map[string]any{"empiezaEn": ahora.Add(2 * time.Hour)})
	if _, c := repartir(pronto.ID); !slices.Contains(c, otra.id) {
		t.Fatalf("cerca con un plan de dentro de 2 h: %v", c)
	}
	// El manejador del outbox lo hace sin errores (sin dispositivos no manda nada).
	evs := e.eventos("plan.publicado", p1.ID)
	if len(evs) != 1 {
		t.Fatalf("plan.publicado: %d", len(evs))
	}
	if err := (&avisos.Enviador{App: e.app}).Manejadores()["plan.publicado"](context.Background(), evs[0]); err != nil {
		t.Fatal(err)
	}
}
