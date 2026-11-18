package servidor_test

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/avisos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/fcm"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/google"
)

// Revisión con ojos de usuario (18/11): lo que se arregló, con su prueba.

// pushFalso es FCM de mentira: guarda cada aviso que le llega (a quién, título y cuerpo).
type pushFalso struct {
	mu      sync.Mutex
	avisos  []pushRecibido
	srv     *httptest.Server
	cliente *fcm.Cliente
}

type pushRecibido struct{ Token, Titulo, Cuerpo string }

func nuevoPushFalso(t *testing.T) *pushFalso {
	t.Helper()
	f := &pushFalso{}
	f.srv = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/token" {
			_ = json.NewEncoder(w).Encode(map[string]string{"access_token": "tok"})
			return
		}
		var m struct {
			Message struct {
				Token        string
				Notification struct{ Title, Body string }
			}
		}
		_ = json.NewDecoder(r.Body).Decode(&m)
		f.mu.Lock()
		f.avisos = append(f.avisos, pushRecibido{m.Message.Token, m.Message.Notification.Title, m.Message.Notification.Body})
		f.mu.Unlock()
	}))
	t.Cleanup(f.srv.Close)
	clave, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	cuenta := &google.Cuenta{Proyecto: "p", Email: "fcm@p.iam.gserviceaccount.com", Clave: clave, URLToken: f.srv.URL + "/token", HTTP: f.srv.Client()}
	f.cliente = &fcm.Cliente{Cuenta: cuenta, URLEnvio: f.srv.URL + "/send"}
	return f
}

// a devuelve los avisos que le llegaron a un token y los olvida.
func (f *pushFalso) a(token string) []pushRecibido {
	f.mu.Lock()
	defer f.mu.Unlock()
	var out, resto []pushRecibido
	for _, a := range f.avisos {
		if a.Token == token {
			out = append(out, a)
		} else {
			resto = append(resto, a)
		}
	}
	f.avisos = resto
	return out
}

// telefono registra un Android para la persona; el token es su id.
func (e *entorno) telefono(p persona) string {
	e.t.Helper()
	tok := "android-" + p.id
	e.pedir(p, "PUT", "/v1/dispositivos", map[string]string{"plataforma": "android", "token": tok}, http.StatusNoContent, nil)
	return tok
}

// correrEventos pasa por el enviador los eventos del outbox de ese tipo que mencionan el id.
func (e *entorno) correrEventos(env *avisos.Enviador, tipo, id string) int {
	e.t.Helper()
	evs := e.eventos(tipo, id)
	for _, ev := range evs {
		if err := env.Manejadores()[tipo](context.Background(), ev); err != nil {
			e.t.Fatalf("%s: %v", tipo, err)
		}
	}
	return len(evs)
}

// Con ingreso libre no hay solicitud que responder: igual quien organiza se entera de quién se sumó y,
// si alguien se baja, de que se liberó un lugar.
func TestIngresoLibreAvisaAlOrganizador(t *testing.T) {
	e := nuevoEntorno(t)
	push := nuevoPushFalso(t)
	env := &avisos.Enviador{App: e.app, FCM: push.cliente}
	nico, lu := e.persona("Nico"), e.persona("Lu")
	e.verificar(lu, true)
	tel := e.telefono(nico)
	p := e.planNuevo(nico, map[string]any{"titulo": "Truco libre", "ingreso": "libre", "somos": 2, "faltan": 2})

	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	if n := e.correrEventos(env, "solicitud.sumada", p.ID); n != 1 {
		t.Fatalf("eventos sumada: %d", n)
	}
	got := push.a(tel)
	if len(got) != 1 || got[0].Titulo != "Lu se sumó" || got[0].Cuerpo != "Truco libre · Somos 3 de 4." {
		t.Fatalf("aviso de sumada: %+v", got)
	}

	e.pedir(lu, "DELETE", "/v1/planes/"+p.ID+"/sumarme", nil, http.StatusNoContent, nil)
	if n := e.correrEventos(env, "solicitud.salio", p.ID); n != 1 {
		t.Fatalf("eventos salió: %d", n)
	}
	got = push.a(tel)
	if len(got) != 1 || got[0].Titulo != "Lu se bajó" || !strings.Contains(got[0].Cuerpo, "Se liberó un lugar") {
		t.Fatalf("aviso de salió: %+v", got)
	}
	// Retirar un pedido pendiente no es un lugar que se libera: no avisa.
	q := e.planNuevo(nico, map[string]any{"ingreso": "aprobacion"})
	e.pedir(lu, "POST", "/v1/planes/"+q.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.pedir(lu, "DELETE", "/v1/planes/"+q.ID+"/sumarme", nil, http.StatusNoContent, nil)
	if n := len(e.eventos("solicitud.salio", q.ID)); n != 0 {
		t.Fatalf("salió de un pendiente avisó: %d", n)
	}
}

// «Plan completo. A disfrutar.» sale una sola vez aunque el plan se vacíe y se vuelva a llenar.
func TestPlanCompletoUnaSolaVez(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu, fede := e.persona("Nico"), e.persona("Lu"), e.persona("Fede")
	e.verificar(lu, true)
	e.verificar(fede, true)
	p := e.planNuevo(nico, map[string]any{"ingreso": "libre", "somos": 1, "faltan": 1})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.pedir(lu, "DELETE", "/v1/planes/"+p.ID+"/sumarme", nil, http.StatusNoContent, nil)
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	var n int
	e.valor(`SELECT count(*) FROM mensajes WHERE plan_id = $1 AND usuario_id IS NULL`, &n, p.ID)
	if n != 1 {
		t.Fatalf("mensajes de plan completo: %d", n)
	}
}

// En Gente, cuando los dos se suman, a los dos les llega «¡Es mutuo con…!» (antes al segundo le
// llegaba «Alguien te sumó a su gente», que llevaba a una lista donde esa persona ya no estaba).
func TestMutuoEnGenteAvisaConNombre(t *testing.T) {
	e := nuevoEntorno(t)
	push := nuevoPushFalso(t)
	env := &avisos.Enviador{App: e.app, FCM: push.cliente}
	ciudad := ciudadUnica()
	ana, beto := e.persona("Ana"), e.persona("Beto")
	e.perfilCompleto(ana, ciudad, "gente")
	e.perfilCompleto(beto, ciudad, "gente")
	telAna, telBeto := e.telefono(ana), e.telefono(beto)

	e.pedir(ana, "POST", "/v1/gente/"+beto.id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusOK, nil)
	e.pedir(beto, "POST", "/v1/gente/"+ana.id+"/reaccion", map[string]string{"modo": "gente", "accion": "prendo"}, http.StatusOK, nil)
	e.correrEventos(env, "interes.nuevo", beto.id)
	if got := push.a(telAna); len(got) != 1 || got[0].Titulo != "¡Es mutuo con Beto!" {
		t.Fatalf("a Ana: %+v", got)
	}
	got := push.a(telBeto)
	if len(got) != 2 || got[0].Titulo != "Alguien te sumó a su gente" || got[1].Titulo != "¡Es mutuo con Ana!" {
		t.Fatalf("a Beto: %+v", got)
	}
}

// En la carta del mazo («Va a…») no aparece un plan oculto por las normas.
func TestMazoSinPlanesOcultos(t *testing.T) {
	e := nuevoEntorno(t)
	ciudad := ciudadUnica()
	yo, otra := e.persona("Yo"), e.persona("Otra")
	e.perfilCompleto(yo, ciudad, "gente")
	e.perfilCompleto(otra, ciudad, "gente")
	visible := e.planNuevo(otra, map[string]any{"titulo": "Se ve"})
	oculto := e.planNuevo(otra, map[string]any{"titulo": "No se ve"})
	e.sistema(`UPDATE planes SET moderacion = 'oculto' WHERE id = $1`, oculto.ID)
	var mazo struct {
		Personas []struct {
			ID       string
			Proximos []struct{ ID string }
		}
	}
	e.pedir(yo, "GET", "/v1/gente/mazo?modo=gente", nil, http.StatusOK, &mazo)
	if len(mazo.Personas) != 1 || len(mazo.Personas[0].Proximos) != 1 || mazo.Personas[0].Proximos[0].ID != visible.ID {
		t.Fatalf("mazo: %+v", mazo)
	}
}

// Escanear el QR del organizador cuenta también la asistencia de quien organiza (lo muestra en el
// lugar). Antes su propio plan le quedaba como una falta: «Fue a 0 de 1 plan · 0 % asistencia».
func TestCheckinConQRCuentaAlOrganizador(t *testing.T) {
	ahora := time.Now()
	e := nuevoEntorno(t)
	nico, lu := e.persona("Nico"), e.persona("Lu")
	e.verificar(lu, true)
	p := e.planNuevo(nico, map[string]any{"empiezaEn": ahora.Add(10 * time.Minute)})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	var qr struct{ Codigo string }
	e.pedir(nico, "GET", "/v1/planes/"+p.ID+"/qr", nil, http.StatusOK, &qr)
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/checkin", map[string]string{"metodo": "qr", "codigo": qr.Codigo}, http.StatusNoContent, nil)
	var org bool
	e.valor(`SELECT checkin_en IS NOT NULL FROM solicitudes WHERE plan_id = $1 AND usuario_id = $2`, &org, p.ID, nico.id)
	if !org {
		t.Fatal("el organizador quedó sin check-in")
	}
}

// Un plan que terminó sin ningún check-in no cuenta como falta para nadie (el check-in es opcional):
// la asistencia sólo mira los planes donde alguien lo usó.
func TestAsistenciaSoloConCheckin(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu := e.persona("Nico"), e.persona("Lu")
	e.verificar(lu, true)
	p := e.planNuevo(nico, nil)
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '1 day' WHERE id = $1`, p.ID)
	asistencia := func() (int, int) {
		var perfil struct{ Asistencia struct{ Fue, De int } }
		e.pedir(nico, "GET", "/v1/personas/"+lu.id, nil, http.StatusOK, &perfil)
		return perfil.Asistencia.Fue, perfil.Asistencia.De
	}
	if fue, de := asistencia(); fue != 0 || de != 0 {
		t.Fatalf("sin check-in: %d de %d", fue, de)
	}
	e.sistema(`UPDATE solicitudes SET checkin_en = now(), checkin_metodo = 'geo' WHERE plan_id = $1 AND usuario_id = $2`, p.ID, nico.id)
	if fue, de := asistencia(); fue != 0 || de != 1 {
		t.Fatalf("con el check-in de otro: %d de %d", fue, de)
	}
}
