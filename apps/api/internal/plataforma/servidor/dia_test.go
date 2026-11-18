package servidor_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/avisos"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/correo"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/firma"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
)

// El día del plan, después del plan y el ritual semanal (informe del 18/11).

var argentina = func() *time.Location {
	l, err := time.LoadLocation("America/Argentina/Buenos_Aires")
	if err != nil {
		return time.FixedZone("ART", -3*3600)
	}
	return l
}()

// pasar corre los avisos programados (dos veces: tienen que ser idempotentes) con el reloj en t.
func (e *entorno) pasar(t time.Time) {
	e.t.Helper()
	pr := &avisos.Programados{App: e.app, Reloj: func() time.Time { return t }}
	for range 2 {
		if _, err := pr.Pasada(context.Background()); err != nil {
			e.t.Fatal(err)
		}
	}
}

type planMio struct {
	ID          string
	MiEstado    string
	Faltan      int
	Organizador struct {
		Caritas *struct {
			Promedio float64
			Cantidad int
		}
	}
	Mio *struct {
		Llegue, Confirme, TodoBien bool
		Carita                     *int
		Fotos                      int
		Espera                     *struct {
			Estado   string
			Posicion int
		}
	}
}

func (e *entorno) verPlan(p persona, id string) planMio {
	e.t.Helper()
	var out planMio
	e.pedir(p, "GET", "/v1/planes/"+id, nil, http.StatusOK, &out)
	return out
}

// «¿Seguís yendo?»: la víspera desde las 19, a quien estaba adentro y no confirmó. «Voy» lo confirma.
func TestVisperaSeguisYendo(t *testing.T) {
	e := nuevoEntorno(t)
	org, lu := e.persona("Org"), e.persona("Lu")
	e.verificar(lu, true)
	hoy := time.Now().In(argentina)
	manana := time.Date(hoy.Year(), hoy.Month(), hoy.Day()+1, 21, 30, 0, 0, argentina)
	p := e.planNuevo(org, map[string]any{"empiezaEn": manana})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.sistema(`UPDATE solicitudes SET respondida_en = now() - interval '1 day' WHERE plan_id = $1`, p.ID)

	vispera := func(t2 time.Time) int {
		e.pasar(t2)
		n := 0
		for _, ev := range e.eventos("plan.recordatorio", p.ID) {
			if strings.Contains(string(ev.Datos), "vispera") {
				n++
			}
		}
		return n
	}
	// A las 18 todavía no; a las 19:30 sí, una sola vez y sólo a Lu.
	if n := vispera(time.Date(hoy.Year(), hoy.Month(), hoy.Day(), 18, 0, 0, 0, argentina)); n != 0 {
		t.Fatalf("víspera a las 18: %d", n)
	}
	if n := vispera(time.Date(hoy.Year(), hoy.Month(), hoy.Day(), 19, 30, 0, 0, argentina)); n != 1 {
		t.Fatalf("víspera a las 19:30: %d", n)
	}
	push := nuevoPushFalso(t)
	tel := e.telefono(lu)
	env := &avisos.Enviador{App: e.app, FCM: push.cliente, Reloj: func() time.Time {
		return time.Date(hoy.Year(), hoy.Month(), hoy.Day(), 19, 30, 0, 0, argentina)
	}}
	e.correrEventos(env, "plan.recordatorio", p.ID)
	if got := push.a(tel); len(got) != 1 || !strings.Contains(got[0].Titulo, "¿Seguís yendo?") || !strings.HasPrefix(got[0].Titulo, "Mañana 21:30") {
		t.Fatalf("aviso de víspera: %+v", got)
	}
	// «Voy».
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/confirmo", nil, http.StatusNoContent, nil)
	if v := e.verPlan(lu, p.ID); v.Mio == nil || !v.Mio.Confirme {
		t.Fatalf("confirmó: %+v", v.Mio)
	}
	// Quien no está en el plan no confirma nada.
	fede := e.persona("Fede")
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/confirmo", nil, http.StatusNotFound, nil)
}

// Lista de espera: en un plan completo, «Anotame»; cuando alguien se baja, el primero tiene el lugar
// guardado 15 minutos; si no entra, pasa al siguiente.
func TestListaDeEspera(t *testing.T) {
	e := nuevoEntorno(t)
	push := nuevoPushFalso(t)
	env := &avisos.Enviador{App: e.app, FCM: push.cliente}
	org, lu, fede, ana, beto := e.persona("Org"), e.persona("Lu"), e.persona("Fede"), e.persona("Ana"), e.persona("Beto")
	for _, x := range []persona{lu, fede, ana, beto} {
		e.verificar(x, true)
	}
	p := e.planNuevo(org, map[string]any{"titulo": "Fulbito", "somos": 2, "faltan": 1})
	// Con lugar, no hay lista: se suma directo.
	if c := codigo(t, e, fede, "POST", "/v1/planes/"+p.ID+"/espera", map[string]any{}, http.StatusConflict); c != "hay_lugar" {
		t.Fatalf("espera con lugar: %q", c)
	}
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	var pos struct {
		Estado   string
		Posicion int
	}
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/espera", map[string]any{}, http.StatusOK, &pos)
	if pos.Estado != "esperando" || pos.Posicion != 1 {
		t.Fatalf("Fede en la lista: %+v", pos)
	}
	e.pedir(ana, "POST", "/v1/planes/"+p.ID+"/espera", map[string]any{}, http.StatusOK, &pos)
	if pos.Posicion != 2 {
		t.Fatalf("Ana en la lista: %+v", pos)
	}
	// Quien ya está no se anota.
	if c := codigo(t, e, lu, "POST", "/v1/planes/"+p.ID+"/espera", map[string]any{}, http.StatusConflict); c != "ya_estas" {
		t.Fatalf("Lu se anota: %q", c)
	}

	// Lu se baja: el lugar queda para Fede (aviso), y Beto no lo puede tomar.
	telFede := e.telefono(fede)
	e.pedir(lu, "DELETE", "/v1/planes/"+p.ID+"/sumarme", nil, http.StatusNoContent, nil)
	e.correrEventos(env, "solicitud.salio", p.ID)
	e.correrEventos(env, "solicitud.salio", p.ID) // un reintento no lo ofrece dos veces
	if n := len(e.eventos(planes.EventoLugarLibre, p.ID)); n != 1 {
		t.Fatalf("eventos lugar libre: %d", n)
	}
	e.correrEventos(env, planes.EventoLugarLibre, p.ID)
	if got := push.a(telFede); len(got) != 1 || got[0].Titulo != "Se liberó un lugar en Fulbito" {
		t.Fatalf("aviso a Fede: %+v", got)
	}
	if v := e.verPlan(fede, p.ID); v.Mio == nil || v.Mio.Espera == nil || v.Mio.Espera.Estado != "avisado" {
		t.Fatalf("Fede avisado: %+v", v.Mio)
	}
	if c := codigo(t, e, beto, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusConflict); c != "completo" {
		t.Fatalf("Beto entra al lugar guardado: %q", c)
	}
	// Fede entra con un toque.
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	if v := e.verPlan(fede, p.ID); v.MiEstado != "aceptada" || v.Mio.Espera != nil {
		t.Fatalf("Fede adentro: %+v", v)
	}

	// Fede se baja: le toca a Ana. No entra en 15 minutos: vence y, sin nadie más en la fila, el lugar queda libre.
	e.pedir(fede, "DELETE", "/v1/planes/"+p.ID+"/sumarme", nil, http.StatusNoContent, nil)
	e.correrEventos(env, "solicitud.salio", p.ID)
	if v := e.verPlan(ana, p.ID); v.Mio == nil || v.Mio.Espera == nil || v.Mio.Espera.Estado != "avisado" {
		t.Fatalf("Ana avisada: %+v", v.Mio)
	}
	e.pasar(time.Now().Add(16 * time.Minute))
	var estado string
	e.valor(`SELECT estado FROM espera WHERE plan_id = $1 AND usuario_id = $2`, &estado, p.ID, ana.id)
	if estado != "vencido" {
		t.Fatalf("aviso de Ana: %s", estado)
	}
	e.pedir(beto, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	// Bajarse de la lista.
	e.pedir(ana, "DELETE", "/v1/planes/"+p.ID+"/espera", nil, http.StatusNoContent, nil)
	e.pedir(ana, "DELETE", "/v1/planes/"+p.ID+"/espera", nil, http.StatusNotFound, nil)
}

// Después del plan: carita privada (promedio para quien organiza con 3 o más) y chat que se abre
// sólo si los dos dijeron que sí.
func TestCaritasYMutuo(t *testing.T) {
	e := nuevoEntorno(t)
	org, lu, fede, ana := e.persona("Org"), e.persona("Lu"), e.persona("Fede"), e.persona("Ana")
	for _, x := range []persona{lu, fede, ana} {
		e.verificar(x, true)
	}
	p := e.planNuevo(org, map[string]any{"somos": 1, "faltan": 3})
	for _, x := range []persona{lu, fede, ana} {
		e.pedir(x, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	}
	// Pasó hace dos días: el privado por compartir el plan ya se cerró.
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '2 days' WHERE id = $1`, p.ID)

	// Una carita fuera de rango no.
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/repetimos", map[string]any{"carita": 6}, http.StatusBadRequest, nil)

	var r struct {
		Mutuos []struct{ ID, Nombre string }
	}
	// Lu dice que sí a Fede: todavía no es mutuo, y el privado sigue cerrado.
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/repetimos", map[string]any{"carita": 5,
		"volveria": []map[string]any{{"usuarioId": fede.id, "si": true}}}, http.StatusOK, &r)
	if len(r.Mutuos) != 0 {
		t.Fatalf("mutuo antes de tiempo: %+v", r)
	}
	e.pedir(lu, "POST", "/v1/privados/"+fede.id, map[string]any{"texto": "Hola"}, http.StatusForbidden, nil)
	// Fede dice que sí a Lu: es mutuo, avisa a los dos y ya pueden hablar.
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/repetimos", map[string]any{"carita": 4,
		"volveria": []map[string]any{{"usuarioId": lu.id, "si": true}}}, http.StatusOK, &r)
	if len(r.Mutuos) != 1 || r.Mutuos[0].ID != lu.id {
		t.Fatalf("mutuo: %+v", r)
	}
	if n := len(e.eventos("interes.nuevo", fede.id)); n != 1 {
		t.Fatalf("aviso de mutuo: %d", n)
	}
	// Decirlo otra vez no vuelve a avisar.
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/repetimos", map[string]any{
		"volveria": []map[string]any{{"usuarioId": lu.id, "si": true}}}, http.StatusOK, nil)
	if n := len(e.eventos("interes.nuevo", fede.id)); n != 1 {
		t.Fatalf("aviso de mutuo repetido: %d", n)
	}
	e.pedir(lu, "POST", "/v1/privados/"+fede.id, map[string]any{"texto": "Hola"}, http.StatusCreated, nil)

	// Con 2 caritas el promedio no se muestra (se adivinaría quién puso cuál); con 3, sí.
	if v := e.verPlan(ana, p.ID); v.Organizador.Caritas != nil {
		t.Fatalf("caritas con 2: %+v", v.Organizador.Caritas)
	}
	e.pedir(ana, "POST", "/v1/planes/"+p.ID+"/repetimos", map[string]any{"carita": 3}, http.StatusOK, nil)
	v := e.verPlan(ana, p.ID)
	if v.Organizador.Caritas == nil || v.Organizador.Caritas.Cantidad != 3 || v.Organizador.Caritas.Promedio != 4 {
		t.Fatalf("caritas: %+v", v.Organizador.Caritas)
	}
	// Cada uno ve sólo la suya.
	if v.Mio == nil || v.Mio.Carita == nil || *v.Mio.Carita != 3 {
		t.Fatalf("mi carita: %+v", v.Mio)
	}
	// La del organizador no cuenta (no se califica a sí mismo).
	e.pedir(org, "POST", "/v1/planes/"+p.ID+"/repetimos", map[string]any{"carita": 1}, http.StatusOK, nil)
	if v := e.verPlan(ana, p.ID); v.Organizador.Caritas.Cantidad != 3 {
		t.Fatalf("caritas con la del organizador: %+v", v.Organizador.Caritas)
	}
}

// Fotos del plan: las suben y las ven sólo quienes fueron (con check-in, si el plan lo usó), por un
// link firmado para cada uno; un bloqueo o un reporte las esconden.
func TestFotosDelPlan(t *testing.T) {
	e := nuevoEntorno(t)
	org, lu, fede, otro := e.persona("Org"), e.persona("Lu"), e.persona("Fede"), e.persona("Otro")
	e.verificar(lu, true)
	e.verificar(fede, true)
	p := e.planNuevo(org, map[string]any{"somos": 1, "faltan": 3})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	// Antes de que empiece, no.
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/fotos", jpeg, http.StatusConflict, nil)
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '3 hours' WHERE id = $1`, p.ID)
	// Lu hizo check-in; Fede no: en este plan se usó el check-in, así que Fede no «fue».
	e.sistema(`UPDATE solicitudes SET checkin_en = now() - interval '2 hours', checkin_metodo = 'geo' WHERE plan_id = $1 AND usuario_id = $2`, p.ID, lu.id)

	var f struct{ ID, URL string }
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/fotos", jpeg, http.StatusCreated, &f)
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/fotos", jpeg, http.StatusNotFound, nil)
	e.pedir(fede, "GET", "/v1/planes/"+p.ID+"/fotos", nil, http.StatusNotFound, nil)
	e.pedir(otro, "GET", "/v1/planes/"+p.ID+"/fotos", nil, http.StatusNotFound, nil)
	// Una foto que no es una imagen, no.
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/fotos", []byte("hola"), http.StatusBadRequest, nil)

	var lista struct {
		Fotos []struct {
			ID, URL string
			Mia     bool
		}
	}
	// El organizador fue (organizó): la ve.
	e.pedir(org, "GET", "/v1/planes/"+p.ID+"/fotos", nil, http.StatusOK, &lista)
	if len(lista.Fotos) != 1 || lista.Fotos[0].Mia {
		t.Fatalf("fotos del organizador: %+v", lista)
	}
	if v := e.verPlan(org, p.ID); v.Mio == nil || v.Mio.Fotos != 1 {
		t.Fatalf("contador de fotos: %+v", v.Mio)
	}
	bajar := func(url string) int {
		r := httptest.NewRequest("GET", strings.TrimPrefix(url, "https://api.caigo.test"), nil)
		w := httptest.NewRecorder()
		e.h.ServeHTTP(w, r)
		return w.Code
	}
	if c := bajar(lista.Fotos[0].URL); c != http.StatusOK {
		t.Fatalf("bajar la foto con el link: %d", c)
	}
	// Sin el token, o con uno de otra foto, no.
	if c := bajar(strings.Split(lista.Fotos[0].URL, "?")[0]); c != http.StatusNotFound {
		t.Fatalf("foto sin token: %d", c)
	}
	// Un link firmado para alguien que no fue tampoco sirve.
	archivo := strings.TrimPrefix(strings.Split(lista.Fotos[0].URL, "?")[0], "https://api.caigo.test/v1/archivos/")
	tok := firma.Firmador{Clave: []byte("secreto-de-prueba")}.Firmar(planes.PropositoFoto, archivo+"~"+otro.id, time.Now().Add(time.Hour))
	if c := bajar("/v1/archivos/" + archivo + "?t=" + tok); c != http.StatusNotFound {
		t.Fatalf("foto con link de alguien que no fue: %d", c)
	}

	// Si el organizador bloquea a Lu, deja de ver sus fotos (y el link que tenía deja de andar).
	e.pedir(org, "PUT", "/v1/bloqueos/"+lu.id, nil, http.StatusNoContent, nil)
	if c := bajar(lista.Fotos[0].URL); c != http.StatusNotFound {
		t.Fatalf("foto con bloqueo: %d", c)
	}
	e.pedir(org, "GET", "/v1/planes/"+p.ID+"/fotos", nil, http.StatusOK, &lista)
	if len(lista.Fotos) != 0 {
		t.Fatalf("fotos con bloqueo: %+v", lista)
	}
	e.pedir(org, "DELETE", "/v1/bloqueos/"+lu.id, nil, http.StatusNoContent, nil)

	// El organizador la reporta: se oculta para los demás (Lu, que la subió, la sigue viendo) y va al equipo.
	e.pedir(org, "POST", "/v1/planes/"+p.ID+"/fotos/"+f.ID+"/reportar", nil, http.StatusNoContent, nil)
	e.pedir(org, "GET", "/v1/planes/"+p.ID+"/fotos", nil, http.StatusOK, &lista)
	if len(lista.Fotos) != 0 {
		t.Fatalf("foto reportada: %+v", lista)
	}
	var reportes int
	e.valor(`SELECT count(*) FROM reportes WHERE sobre_plan_id = $1 AND sobre_usuario_id = $2`, &reportes, p.ID, lu.id)
	if reportes != 1 {
		t.Fatalf("reportes: %d", reportes)
	}
	e.pedir(lu, "GET", "/v1/planes/"+p.ID+"/fotos", nil, http.StatusOK, &lista)
	if len(lista.Fotos) != 1 || !lista.Fotos[0].Mia {
		t.Fatalf("fotos de Lu: %+v", lista)
	}
	// Sólo quien la subió la borra.
	e.pedir(org, "DELETE", "/v1/planes/"+p.ID+"/fotos/"+f.ID, nil, http.StatusNotFound, nil)
	e.pedir(lu, "DELETE", "/v1/planes/"+p.ID+"/fotos/"+f.ID, nil, http.StatusNoContent, nil)
	var archivos int
	e.valor(`SELECT count(*) FROM archivos WHERE id::text = $1`, &archivos, archivo)
	if archivos != 0 || len(e.eventos("archivo.borrar", "plan/")) == 0 {
		t.Fatalf("archivo de la foto borrada: %d", archivos)
	}
}

// «¿Todo bien?»: si no hizo check-in a la media hora, le escribimos a su contacto (si es un mail) y le
// avisamos a ella. Con el check-in, se apaga y no queda nada guardado.
func TestTodoBien(t *testing.T) {
	e := nuevoEntorno(t)
	org, lu := e.persona("Org"), e.persona("Lu")
	e.verificar(lu, true)
	inicio := time.Now().Add(time.Hour)
	p := e.planNuevo(org, map[string]any{"titulo": "Truco en La Fonda", "empiezaEn": inicio})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)

	// Sin contactos en el perfil, no se puede.
	if c := codigo(t, e, lu, "POST", "/v1/planes/"+p.ID+"/todo-bien", map[string]any{"contacto": 0}, http.StatusUnprocessableEntity); c != "sin_contacto" {
		t.Fatalf("sin contacto: %q", c)
	}
	var yo struct {
		ContactoConfianza  string
		ContactosConfianza []struct{ Nombre, Contacto string }
		Avisos             struct{ Semana bool }
	}
	e.pedir(lu, "PATCH", "/v1/yo", map[string]any{"contactosConfianza": []map[string]string{
		{"nombre": "Mamá", "contacto": "mama@caigo.test"}, {"nombre": "Sofi", "contacto": "+54 9 223 555-0101"}}}, http.StatusOK, &yo)
	if len(yo.ContactosConfianza) != 2 || yo.ContactoConfianza != "mama@caigo.test" || !yo.Avisos.Semana {
		t.Fatalf("contactos: %+v", yo)
	}
	// Más de 3, no.
	e.pedir(lu, "PATCH", "/v1/yo", map[string]any{"contactosConfianza": []map[string]string{
		{"contacto": "a@caigo.test"}, {"contacto": "b@caigo.test"}, {"contacto": "c@caigo.test"}, {"contacto": "d@caigo.test"}}},
		http.StatusBadRequest, nil)

	var tb struct {
		Activo   bool
		Contacto *struct{ Nombre, Contacto string }
		AvisaEn  time.Time
	}
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/todo-bien", map[string]any{"contacto": 0}, http.StatusOK, &tb)
	if !tb.Activo || tb.Contacto == nil || tb.Contacto.Nombre != "Mamá" || tb.AvisaEn.Sub(inicio) != 30*time.Minute {
		t.Fatalf("todo bien: %+v", tb)
	}
	// Quien no está en el plan no lo ve.
	e.pedir(org, "GET", "/v1/planes/"+p.ID+"/todo-bien", nil, http.StatusOK, nil)
	fede := e.persona("Fede")
	e.pedir(fede, "GET", "/v1/planes/"+p.ID+"/todo-bien", nil, http.StatusNotFound, nil)

	// A los 20 minutos del inicio, nada; a los 31, el aviso (una vez).
	e.pasar(inicio.Add(20 * time.Minute))
	if n := len(e.eventos(planes.EventoTodoBien, p.ID)); n != 0 {
		t.Fatalf("todo bien temprano: %d", n)
	}
	e.pasar(inicio.Add(31 * time.Minute))
	if n := len(e.eventos(planes.EventoTodoBien, p.ID)); n != 1 {
		t.Fatalf("todo bien: %d", n)
	}
	push := nuevoPushFalso(t)
	tel := e.telefono(lu)
	mails := &correo.Memoria{}
	env := &avisos.Enviador{App: e.app, FCM: push.cliente, Correo: mails,
		Planes: &planes.Servicio{App: e.app, Firma: firma.Firmador{Clave: []byte("secreto-de-prueba")}, Sitio: "https://caigo.test"}}
	e.correrEventos(env, planes.EventoTodoBien, p.ID)
	m := mails.Para("mama@caigo.test")
	if len(m) != 1 || !strings.Contains(m[0].Asunto, "¿Todo bien con Lu?") || !strings.Contains(m[0].Texto, "https://caigo.test/vivo/") ||
		!strings.Contains(m[0].Texto, "Truco en La Fonda") {
		t.Fatalf("mail al contacto: %+v", m)
	}
	if got := push.a(tel); len(got) != 1 || !strings.Contains(got[0].Cuerpo, "Le escribimos a Mamá") {
		t.Fatalf("push a Lu: %+v", got)
	}

	// Con un teléfono no hay SMS: el push le pide a ella que avise.
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/todo-bien", map[string]any{"contacto": 1}, http.StatusOK, nil)
	e.sistema(`UPDATE todo_bien SET avisa_en = now() - interval '1 minute' WHERE plan_id = $1`, p.ID)
	e.pasar(time.Now())
	e.correrEventos(env, planes.EventoTodoBien, p.ID)
	if got := push.a(tel); len(got) == 0 || !strings.Contains(got[len(got)-1].Cuerpo, "Avisale a Sofi") {
		t.Fatalf("push con teléfono: %+v", got)
	}
	if len(mails.Para("mama@caigo.test")) != 1 {
		t.Fatal("volvió a escribirle a Mamá")
	}

	// Con el check-in se apaga y se borra.
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '10 minutes' WHERE id = $1`, p.ID)
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/todo-bien", map[string]any{"contacto": 0}, http.StatusOK, nil)
	var qr struct{ Codigo string }
	e.pedir(org, "GET", "/v1/planes/"+p.ID+"/qr", nil, http.StatusOK, &qr)
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/checkin", map[string]any{"metodo": "qr", "codigo": qr.Codigo}, http.StatusNoContent, nil)
	var filas int
	e.valor(`SELECT count(*) FROM todo_bien WHERE plan_id = $1`, &filas, p.ID)
	if filas != 0 {
		t.Fatalf("todo bien después del check-in: %d", filas)
	}
	if c := codigo(t, e, lu, "POST", "/v1/planes/"+p.ID+"/todo-bien", map[string]any{"contacto": 0}, http.StatusConflict); c != "llegaste" {
		t.Fatalf("activar después del check-in: %q", c)
	}
}

// «Esta semana en <ciudad>»: los lunes desde las 18, a quien tiene el aviso prendido y un teléfono, si en
// su ciudad hay 5 planes o más. Uno por semana.
func TestEstaSemana(t *testing.T) {
	hoy := time.Now().In(argentina)
	lunes := time.Date(hoy.Year(), hoy.Month(), hoy.Day(), 18, 30, 0, 0, argentina)
	for lunes.Weekday() != time.Monday || !lunes.After(time.Now()) {
		lunes = lunes.AddDate(0, 0, 1)
	}
	reloj := func() time.Time { return lunes }
	e := nuevoEntornoAjustado(t, func(d *servidor.Dependencias) { d.Reloj = reloj })
	ciudad, chica := ciudadUnica(), ciudadUnica()+" Chica"
	org, lu, sin, apagado := e.persona("Org"), e.persona("Lu"), e.persona("Sin"), e.persona("Apagado")
	for _, x := range []persona{lu, sin, apagado} {
		e.pedir(x, "PATCH", "/v1/yo", map[string]any{"ciudad": ciudad}, http.StatusOK, nil)
	}
	e.telefono(lu)
	e.telefono(apagado)
	e.pedir(apagado, "PATCH", "/v1/yo", map[string]any{"avisos": map[string]any{"semana": false}}, http.StatusOK, nil)
	for i := range 5 {
		e.planNuevo(org, map[string]any{"ciudad": ciudad, "empiezaEn": lunes.AddDate(0, 0, 1+i%3).Add(time.Hour)})
	}
	for range 4 {
		e.planNuevo(org, map[string]any{"ciudad": chica, "empiezaEn": lunes.AddDate(0, 0, 1).Add(time.Hour)})
	}
	chico := e.persona("Chico")
	e.pedir(chico, "PATCH", "/v1/yo", map[string]any{"ciudad": chica}, http.StatusOK, nil)
	e.telefono(chico)

	// El domingo, nada; el lunes a las 18:30, a Lu (no a quien no tiene teléfono ni a quien lo apagó).
	e.pasar(lunes.AddDate(0, 0, -1))
	if n := len(e.eventos("semana.aviso", lu.id)); n != 0 {
		t.Fatalf("aviso el domingo: %d", n)
	}
	e.pasar(lunes)
	e.pasar(lunes.Add(time.Hour))
	evs := e.eventos("semana.aviso", lu.id)
	if len(evs) != 1 {
		t.Fatalf("avisos a Lu: %d", len(evs))
	}
	var d struct{ Total int }
	_ = json.Unmarshal(evs[0].Datos, &d)
	if d.Total != 5 {
		t.Fatalf("total del aviso: %s", evs[0].Datos)
	}
	for _, x := range []persona{sin, apagado, chico} {
		if n := len(e.eventos("semana.aviso", x.id)); n != 0 {
			t.Fatalf("aviso de la semana a quien no tocaba: %d", n)
		}
	}
	push := nuevoPushFalso(t)
	tel := e.telefono(lu)
	e.correrEventos(&avisos.Enviador{App: e.app, FCM: push.cliente}, "semana.aviso", lu.id)
	if got := push.a(tel); len(got) != 1 || got[0].Titulo != "Esta semana en "+ciudad {
		t.Fatalf("push de la semana: %+v", got)
	}

	// La pantalla: total, días hasta el domingo y lo mejor primero.
	var sem struct {
		Ciudad string
		Total  int
		Dias   []struct {
			Dia      string
			Cantidad int
		}
		Planes []struct{ ID string }
	}
	e.pedir(lu, "GET", "/v1/semana", nil, http.StatusOK, &sem)
	if sem.Ciudad != ciudad || sem.Total != 5 || len(sem.Dias) != 7 || len(sem.Planes) != 5 {
		t.Fatalf("semana: %+v", sem)
	}
}

// Las versiones viejas de las apps mandan sólo contactoConfianza: cambia el primero y deja los demás.
func TestContactoViejoNoBorraLosDemas(t *testing.T) {
	e := nuevoEntorno(t)
	lu := e.persona("Lu")
	var yo struct {
		ContactoConfianza  string
		ContactosConfianza []struct{ Nombre, Contacto string }
	}
	e.pedir(lu, "PATCH", "/v1/yo", map[string]any{"contactosConfianza": []map[string]string{
		{"nombre": "Mamá", "contacto": "mama@caigo.test"}, {"nombre": "Sofi", "contacto": "sofi@caigo.test"}}}, http.StatusOK, &yo)
	e.pedir(lu, "PATCH", "/v1/yo", map[string]any{"contactoConfianza": "+5492235550101"}, http.StatusOK, &yo)
	if len(yo.ContactosConfianza) != 2 || yo.ContactosConfianza[0].Nombre != "Mamá" || yo.ContactosConfianza[0].Contacto != "+5492235550101" ||
		yo.ContactoConfianza != "+5492235550101" {
		t.Fatalf("contacto viejo: %+v", yo)
	}
	e.pedir(lu, "PATCH", "/v1/yo", map[string]any{"contactoConfianza": ""}, http.StatusOK, &yo)
	if len(yo.ContactosConfianza) != 1 || yo.ContactoConfianza != "sofi@caigo.test" {
		t.Fatalf("contacto viejo vacío: %+v", yo)
	}
}
