package servidor_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/chat"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/clerk"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
)

// planConLu: Nico organiza (falta 1, ingreso libre) y Lu se suma. Fede queda afuera.
func planConLu(t *testing.T, e *entorno) (nico, lu, fede persona, planID string) {
	nico, lu, fede = e.persona("Nico"), e.persona("Lu"), e.persona("Fede")
	e.verificar(lu, true)
	e.verificar(fede, true)
	var p plan
	e.pedir(nico, "POST", "/v1/planes", map[string]any{
		"categoria": "previa", "empiezaEn": time.Now().Add(time.Hour), "ciudad": "Mar del Plata",
		"zona": "La Perla", "lat": -37.99, "lng": -57.55, "somos": 3, "faltan": 1, "ingreso": "libre",
	}, http.StatusCreated, &p)
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	return nico, lu, fede, p.ID
}

func TestChatDelPlan(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu, fede, planID := planConLu(t, e)

	e.pedir(lu, "POST", "/v1/planes/"+planID+"/mensajes", map[string]string{"texto": "¿Llevo hielo?"}, http.StatusCreated, nil)
	e.pedir(fede, "POST", "/v1/planes/"+planID+"/mensajes", map[string]string{"texto": "hola"}, http.StatusForbidden, nil)
	e.pedir(fede, "GET", "/v1/planes/"+planID+"/mensajes", nil, http.StatusForbidden, nil)

	var ms struct {
		Mensajes []struct {
			Texto string
			Autor *struct{ Nombre string }
		}
	}
	e.pedir(nico, "GET", "/v1/planes/"+planID+"/mensajes", nil, http.StatusOK, &ms)
	// Al llenarse queda el mensaje de sistema; después el de Lu.
	if len(ms.Mensajes) != 2 || ms.Mensajes[0].Autor != nil || ms.Mensajes[0].Texto != "Plan completo. A disfrutar." ||
		ms.Mensajes[1].Autor == nil || ms.Mensajes[1].Autor.Nombre != "Lu" {
		t.Fatalf("mensajes: %+v", ms)
	}

	// Privados: Lu y Nico compartieron el plan; Fede no comparte nada con Nico.
	e.pedir(lu, "POST", "/v1/privados/"+nico.id, map[string]string{"texto": "¡Gracias por sumarme!"}, http.StatusCreated, nil)
	e.pedir(fede, "POST", "/v1/privados/"+nico.id, map[string]string{"texto": "hola"}, http.StatusForbidden, nil)
	var cs struct {
		Conversaciones []struct {
			Con     struct{ ID string }
			SinLeer int
		}
	}
	e.pedir(nico, "GET", "/v1/privados", nil, http.StatusOK, &cs)
	if len(cs.Conversaciones) != 1 || cs.Conversaciones[0].Con.ID != lu.id || cs.Conversaciones[0].SinLeer != 1 {
		t.Fatalf("conversaciones: %+v", cs)
	}
	e.pedir(nico, "GET", "/v1/privados/"+lu.id, nil, http.StatusOK, nil)
	e.pedir(nico, "GET", "/v1/privados", nil, http.StatusOK, &cs)
	if cs.Conversaciones[0].SinLeer != 0 {
		t.Fatal("leer el hilo no marcó los privados como leídos")
	}
}

func TestTiempoReal(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu, _, planID := planConLu(t, e)

	// Un servidor de verdad (el WebSocket necesita conexión) con el hub escuchando la base.
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	c := &clerk.Cliente{Secreto: "sk_test_falso", Emisor: "https://clerk.falso.test", API: e.clerk.URL, HTTP: e.clerk.Client()}
	hub := &chat.Hub{Servicio: &chat.Servicio{App: e.app}}
	go hub.Escuchar(ctx)
	srv := httptest.NewServer(servidor.Nuevo(servidor.Dependencias{App: e.app, Clerk: c, Hub: hub}))
	defer srv.Close()

	ws, _, err := websocket.Dial(ctx, "ws"+strings.TrimPrefix(srv.URL, "http")+"/v1/tiempo-real?token="+nico.token, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer ws.CloseNow()
	// Que el LISTEN esté andando antes de mandar.
	deadline := time.Now().Add(3 * time.Second)
	for !hub.Conectado(nico.id) && time.Now().Before(deadline) {
		time.Sleep(20 * time.Millisecond)
	}
	time.Sleep(200 * time.Millisecond)

	e.pedir(lu, "POST", "/v1/planes/"+planID+"/mensajes", map[string]string{"texto": "Ya salgo"}, http.StatusCreated, nil)
	lctx, lcancel := context.WithTimeout(ctx, 5*time.Second)
	defer lcancel()
	_, b, err := ws.Read(lctx)
	if err != nil {
		t.Fatal(err)
	}
	var ev chat.Evento
	if err := json.Unmarshal(b, &ev); err != nil || ev.Tipo != "mensaje" || ev.Mensaje.Texto != "Ya salgo" {
		t.Fatalf("evento: %s", b)
	}
}
