package servidor_test

import (
	"context"
	"net/http"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/moderacion"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/ia"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/ia/iafalso"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
)

// iaDePrueba decide por palabras clave (el filtro ya las marcó como dudosas): faso o CBU, bloquear;
// wsp, revisar; el resto, ok.
func iaDePrueba() *iafalso.Falso {
	return &iafalso.Falso{Responder: func(texto string) (any, error) {
		t := strings.ToLower(texto)
		switch {
		case strings.Contains(t, "faso"), strings.Contains(t, "cbu"):
			return moderacion.Clasificacion{Decision: "bloquear", Categoria: "drogas", Motivo: "Ofrece drogas o plata."}, nil
		case strings.Contains(t, "wsp"):
			return moderacion.Clasificacion{Decision: "revisar", Categoria: "contacto", Motivo: "Pasa datos de contacto."}, nil
		}
		return moderacion.Clasificacion{Decision: "ok", Categoria: "ninguna"}, nil
	}}
}

func entornoConIA(t *testing.T, f *iafalso.Falso) (*entorno, *moderacion.Servicio) {
	var cli *ia.Cliente
	e := nuevoEntornoAjustado(t, func(d *servidor.Dependencias) {
		if f != nil {
			cli = &ia.Cliente{Llamador: f, App: d.App}
			d.IA = cli
		}
	})
	return e, moderacion.Nuevo(e.app, cli, nil)
}

// revisarEnElWorker corre el manejador de moderacion.revisar del mensaje (como el worker, sin tocar
// los demás eventos de la base compartida).
func (e *entorno) revisarEnElWorker(m *moderacion.Servicio, tipo string, id int64) {
	e.t.Helper()
	var ev outbox.Evento
	err := e.app.EnSistema(context.Background(), func(tx pgx.Tx) error {
		return tx.QueryRow(context.Background(), `SELECT id, tipo, datos, intentos FROM outbox
			WHERE tipo = $1 AND datos->>'tipo' = $2 AND (datos->>'id')::bigint = $3`, moderacion.EventoRevisar, tipo, id).
			Scan(&ev.ID, &ev.Tipo, &ev.Datos, &ev.Intentos)
	})
	if err != nil {
		e.t.Fatalf("no se encoló la revisión de %s %d: %v", tipo, id, err)
	}
	if err := m.Manejador(context.Background(), ev); err != nil {
		e.t.Fatal(err)
	}
}

type itemCola struct {
	ID, Tipo, Texto, Origen, Categoria, Estado string
	ObjetoID                                   *string
	Autor                                      struct{ ID string }
}

func (e *entorno) cola(equipo persona, autor persona) []itemCola {
	e.t.Helper()
	var c struct{ Moderaciones []itemCola }
	e.pedir(equipo, "GET", "/v1/equipo/moderacion", nil, http.StatusOK, &c)
	var out []itemCola
	for _, i := range c.Moderaciones {
		if i.Autor.ID == autor.id {
			out = append(out, i)
		}
	}
	return out
}

type planMod struct {
	ID, Moderacion string
}

func TestModeracionDePlanes(t *testing.T) {
	f := iaDePrueba()
	e, _ := entornoConIA(t, f)
	nico, lu := e.persona("Nico"), e.persona("Lu")
	equipo := e.persona("Equipo")
	e.alEquipo(equipo)
	crear := func(titulo, descripcion string, estado int, out any) {
		e.pedir(nico, "POST", "/v1/planes", map[string]any{"titulo": titulo, "descripcion": descripcion,
			"categoria": "previa", "empiezaEn": time.Now().Add(2 * time.Hour), "ciudad": "Mar del Plata",
			"zona": "La Perla", "lat": -37.99, "lng": -57.55, "somos": 3, "faltan": 2, "ingreso": "libre"}, estado, out)
	}

	// Uno sano no pasa por la IA.
	var sano planMod
	crear("Fulbito en La Perla", "Traigan pechera", http.StatusCreated, &sano)
	if sano.Moderacion != "" || f.Cantidad() != 0 {
		t.Fatalf("sano: %+v, %d pedidos a la IA", sano, f.Cantidad())
	}
	// Bloqueado por el filtro (sin IA) y por la IA: no se crea.
	crear("Fulbito", "te voy a matar si no venís", http.StatusUnprocessableEntity, nil)
	crear("Fulbito", "Pasá la seña al CBU 0000003100", http.StatusUnprocessableEntity, nil)
	// Quien dice ser menor queda en la cola aunque el plan no se haya creado.
	crear("Fulbito", "tengo 16 años, me sumo igual", http.StatusUnprocessableEntity, nil)
	if c := e.cola(equipo, nico); len(c) != 1 || c[0].Estado != "bloqueado" || c[0].Categoria != "menores" || c[0].ObjetoID != nil {
		t.Fatalf("cola tras el menor: %+v", c)
	}

	// Dudoso para la IA: se publica en revisión (sólo el organizador lo sabe).
	var dudoso planMod
	crear("Fulbito", "pasame tu wsp y te sumo", http.StatusCreated, &dudoso)
	if dudoso.Moderacion != "revision" {
		t.Fatalf("en revisión: %+v", dudoso)
	}
	var visto planMod
	e.pedir(lu, "GET", "/v1/planes/"+dudoso.ID, nil, http.StatusOK, &visto)
	if visto.Moderacion != "" {
		t.Fatalf("lo ve otra persona con moderación: %+v", visto)
	}
	var item itemCola
	for _, i := range e.cola(equipo, nico) {
		if i.Tipo == "plan" && i.ObjetoID != nil && *i.ObjetoID == dudoso.ID {
			item = i
		}
	}
	if item.ID == "" || item.Origen != "ia" || item.Categoria != "contacto" || !strings.Contains(item.Texto, "wsp") {
		t.Fatalf("no está en la cola: %+v", item)
	}

	// Editar con algo bloqueado: no se guarda.
	e.pedir(nico, "PATCH", "/v1/planes/"+dudoso.ID, map[string]any{"descripcion": "transferime al CBU"}, http.StatusUnprocessableEntity, nil)

	// El equipo lo oculta: nadie más lo ve (ni por link ni en la página pública); el organizador, sí.
	e.pedir(equipo, "POST", "/v1/equipo/moderacion/"+item.ID, map[string]string{"accion": "ocultar"}, http.StatusNoContent, nil)
	e.pedir(equipo, "POST", "/v1/equipo/moderacion/"+item.ID, map[string]string{"accion": "ocultar"}, http.StatusNotFound, nil)
	e.pedir(nico, "GET", "/v1/planes/"+dudoso.ID, nil, http.StatusOK, &visto)
	if visto.Moderacion != "oculto" {
		t.Fatalf("el organizador no lo ve oculto: %+v", visto)
	}
	e.pedir(lu, "GET", "/v1/planes/"+dudoso.ID, nil, http.StatusNotFound, nil)
	e.pedir(persona{}, "GET", "/v1/publico/planes/"+dudoso.ID, nil, http.StatusNotFound, nil)
	e.pedir(lu, "POST", "/v1/planes/"+dudoso.ID+"/sumarme", map[string]any{}, http.StatusNotFound, nil)
	var lista struct{ Planes []planMod }
	e.pedir(lu, "GET", "/v1/planes/explorar?lat=-37.99&lng=-57.55", nil, http.StatusOK, &lista)
	for _, p := range lista.Planes {
		if p.ID == dudoso.ID {
			t.Fatal("un plan oculto sale en Explorar")
		}
	}
	var mios struct{ Planes []planMod }
	e.pedir(nico, "GET", "/v1/planes/mios?cual=organizo", nil, http.StatusOK, &mios)
	encontrado := false
	for _, p := range mios.Planes {
		encontrado = encontrado || (p.ID == dudoso.ID && p.Moderacion == "oculto")
	}
	if !encontrado {
		t.Fatal("el organizador no lo ve en Mis planes")
	}
	// Se avisa al autor.
	var avisos int
	e.leerUno(`SELECT count(*) FROM outbox WHERE tipo = 'moderacion.oculto' AND datos->>'planId' = $1`, []any{dudoso.ID}, &avisos)
	if avisos != 1 {
		t.Fatalf("avisos al autor: %d", avisos)
	}
	// Editarlo no lo vuelve a mostrar.
	e.pedir(nico, "PATCH", "/v1/planes/"+dudoso.ID, map[string]any{"descripcion": "Traigan pechera"}, http.StatusOK, &visto)
	if visto.Moderacion != "oculto" {
		t.Fatalf("editar lo destapó: %+v", visto)
	}
}

func (e *entorno) leerUno(q string, args []any, dst ...any) {
	e.t.Helper()
	if err := e.app.EnSistema(context.Background(), func(tx pgx.Tx) error {
		return tx.QueryRow(context.Background(), q, args...).Scan(dst...)
	}); err != nil {
		e.t.Fatal(err)
	}
}

type mensajeMod struct {
	ID         int64
	Texto      string
	Moderacion string
}

func TestModeracionDelChat(t *testing.T) {
	f := iaDePrueba()
	e, mod := entornoConIA(t, f)
	nico, lu, _, planID := planConLu(t, e)
	equipo := e.persona("Equipo")
	e.alEquipo(equipo)
	ruta := "/v1/planes/" + planID + "/mensajes"
	mensajes := func(p persona) map[int64]mensajeMod {
		var ms struct{ Mensajes []mensajeMod }
		e.pedir(p, "GET", ruta, nil, http.StatusOK, &ms)
		out := map[int64]mensajeMod{}
		for _, m := range ms.Mensajes {
			out[m.ID] = m
		}
		return out
	}

	// El filtro frena en el momento, sin IA.
	e.pedir(lu, "POST", ruta, map[string]string{"texto": "te voy a matar"}, http.StatusUnprocessableEntity, nil)
	// Lo sano sale sin pasar por la IA.
	var sano mensajeMod
	e.pedir(lu, "POST", ruta, map[string]string{"texto": "¿Llevo hielo?"}, http.StatusCreated, &sano)
	if f.Cantidad() != 0 || sano.Moderacion != "" {
		t.Fatalf("sano: %+v, %d pedidos", sano, f.Cantidad())
	}

	// Dudoso: sale enseguida (el chat no espera) y el worker lo revisa. La IA dice bloquear: se oculta.
	var droga mensajeMod
	e.pedir(lu, "POST", ruta, map[string]string{"texto": "alguien trae faso?"}, http.StatusCreated, &droga)
	if _, ok := mensajes(nico)[droga.ID]; !ok {
		t.Fatal("el dudoso no salió")
	}
	e.revisarEnElWorker(mod, "mensaje", droga.ID)
	if _, ok := mensajes(nico)[droga.ID]; ok {
		t.Fatal("el bloqueado se sigue viendo")
	}
	if m := mensajes(lu)[droga.ID]; m.Moderacion != "oculto" {
		t.Fatalf("el autor no lo ve oculto: %+v", m)
	}
	// Repetir el evento no hace nada (ya no está pendiente).
	e.revisarEnElWorker(mod, "mensaje", droga.ID)
	if f.Cantidad() != 1 {
		t.Fatalf("pedidos a la IA: %d", f.Cantidad())
	}

	// La IA dice revisar: se sigue viendo, el autor ve «revision» y va a la cola. El equipo lo aprueba.
	var dudoso mensajeMod
	e.pedir(lu, "POST", ruta, map[string]string{"texto": "pasame tu wsp"}, http.StatusCreated, &dudoso)
	e.revisarEnElWorker(mod, "mensaje", dudoso.ID)
	if m, ok := mensajes(nico)[dudoso.ID]; !ok || m.Moderacion != "" {
		t.Fatalf("el que está en revisión, para los demás: %+v %v", m, ok)
	}
	if m := mensajes(lu)[dudoso.ID]; m.Moderacion != "revision" {
		t.Fatalf("el autor: %+v", m)
	}
	var id string
	for _, i := range e.cola(equipo, lu) {
		if i.Tipo == "mensaje" && i.ObjetoID != nil && *i.ObjetoID == itoa(dudoso.ID) {
			id = i.ID
		}
		// Lo que la IA ocultó también queda en la cola (por si fue un error).
		if i.ObjetoID != nil && *i.ObjetoID == itoa(droga.ID) && i.Estado != "oculto" {
			t.Fatalf("el oculto en la cola: %+v", i)
		}
	}
	if id == "" {
		t.Fatal("el dudoso no está en la cola")
	}
	e.pedir(equipo, "POST", "/v1/equipo/moderacion/"+id, map[string]string{"accion": "aprobar"}, http.StatusNoContent, nil)
	if m := mensajes(lu)[dudoso.ID]; m.Moderacion != "" {
		t.Fatalf("aprobado: %+v", m)
	}

	// Privados: lo dudoso que la IA aprueba queda normal.
	var priv mensajeMod
	e.pedir(lu, "POST", "/v1/privados/"+nico.id, map[string]string{"texto": "seguime en insta"}, http.StatusCreated, &priv)
	e.revisarEnElWorker(mod, "privado", priv.ID)
	var estado string
	e.leerUno(`SELECT moderacion FROM privados WHERE id = $1`, []any{priv.ID}, &estado)
	if estado != "ok" {
		t.Fatalf("privado aprobado por la IA: %s", estado)
	}
	// Un privado oculto no le llega al otro (ni como último de la conversación).
	var oculto mensajeMod
	e.pedir(lu, "POST", "/v1/privados/"+nico.id, map[string]string{"texto": "te vendo faso"}, http.StatusCreated, &oculto)
	e.revisarEnElWorker(mod, "privado", oculto.ID)
	var hilo struct{ Privados []mensajeMod }
	e.pedir(nico, "GET", "/v1/privados/"+lu.id, nil, http.StatusOK, &hilo)
	for _, p := range hilo.Privados {
		if p.ID == oculto.ID {
			t.Fatal("el privado oculto le llega al otro")
		}
	}
	e.pedir(lu, "GET", "/v1/privados/"+nico.id, nil, http.StatusOK, &hilo)
	if ultimo := hilo.Privados[len(hilo.Privados)-1]; ultimo.ID != oculto.ID || ultimo.Moderacion != "oculto" {
		t.Fatalf("el autor ve su privado oculto: %+v", ultimo)
	}

	// Suspender desde la cola: la cuenta queda afuera.
	var otro mensajeMod
	e.pedir(lu, "POST", ruta, map[string]string{"texto": "dame tu wsp ya"}, http.StatusCreated, &otro)
	e.revisarEnElWorker(mod, "mensaje", otro.ID)
	id = ""
	for _, i := range e.cola(equipo, lu) {
		if i.ObjetoID != nil && *i.ObjetoID == itoa(otro.ID) {
			id = i.ID
		}
	}
	e.pedir(equipo, "POST", "/v1/equipo/moderacion/"+id, map[string]string{"accion": "suspender"}, http.StatusBadRequest, nil)
	e.pedir(equipo, "POST", "/v1/equipo/moderacion/"+id, map[string]string{"accion": "suspender", "motivo": "Acoso"}, http.StatusNoContent, nil)
	e.pedir(lu, "GET", "/v1/yo", nil, http.StatusForbidden, nil)
	if _, ok := mensajes(nico)[otro.ID]; ok {
		t.Fatal("suspender también oculta el mensaje")
	}
}

// Sin IA (sin clave, o pasado el tope): lo dudoso va directo a la cola, marcado «sin_ia».
func TestModeracionSinIA(t *testing.T) {
	e, mod := entornoConIA(t, nil)
	nico, lu, _, planID := planConLu(t, e)
	equipo := e.persona("Equipo")
	e.alEquipo(equipo)
	var m mensajeMod
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/mensajes", map[string]string{"texto": "pasame tu wsp"}, http.StatusCreated, &m)
	e.revisarEnElWorker(mod, "mensaje", m.ID)
	c := e.cola(equipo, lu)
	if len(c) != 1 || c[0].Origen != "sin_ia" || c[0].Categoria != "contacto" || c[0].Estado != "revision" {
		t.Fatalf("cola sin IA: %+v", c)
	}
	var p planMod
	e.pedir(nico, "POST", "/v1/planes", map[string]any{"titulo": "Previa", "descripcion": "escribime al wsp",
		"categoria": "previa", "empiezaEn": time.Now().Add(2 * time.Hour), "ciudad": "Mar del Plata",
		"zona": "La Perla", "lat": -37.99, "lng": -57.55, "somos": 3, "faltan": 1, "ingreso": "libre"}, http.StatusCreated, &p)
	if p.Moderacion != "revision" {
		t.Fatalf("plan sin IA: %+v", p)
	}
}

func itoa(n int64) string { return strconv.FormatInt(n, 10) }
