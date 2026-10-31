package servidor_test

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/clerk"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/clerk/clerkfalso"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/config"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
)

// entorno levanta la API contra el Postgres de desarrollo (infra/compose.dev.yml). Sin base, se saltea.
type entorno struct {
	t     *testing.T
	h     http.Handler
	clerk *clerkfalso.Servidor
	app   *db.Pool
}

func nuevoEntorno(t *testing.T) *entorno {
	t.Helper()
	t.Setenv("CAIGO_ENTORNO", "desarrollo")
	cfg, err := config.Leer()
	if err != nil {
		t.Fatal(err)
	}
	ctx := context.Background()
	if err := db.Migrar(ctx, cfg.BaseMigrador); err != nil {
		t.Skipf("sin base de desarrollo: %v", err)
	}
	app, err := db.Abrir(ctx, cfg.BaseApp)
	if err != nil {
		t.Skipf("sin base de desarrollo: %v", err)
	}
	t.Cleanup(app.Close)
	f := clerkfalso.Nuevo(t)
	c := &clerk.Cliente{Secreto: clerkfalso.Secreto, Emisor: clerkfalso.Emisor, API: f.URL, HTTP: f.Client()}
	return &entorno{t: t, h: servidor.Nuevo(servidor.Dependencias{App: app, Clerk: c}), clerk: f, app: app}
}

// persona da de alta a alguien en el Clerk falso y devuelve su token y su id de Caigo.
type persona struct {
	token, id string
}

func (e *entorno) persona(nombre string) persona {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	sub := "user_" + hex.EncodeToString(b)
	e.clerk.Usuarios[sub] = clerkfalso.Usuario{Nombre: nombre, Email: nombre + "@caigo.test", Verificado: true}
	p := persona{token: e.clerk.Token(e.t, clerkfalso.Reclamos{Sub: sub, Exp: time.Now().Add(time.Hour)})}
	var yo struct{ ID string }
	e.pedir(p, "GET", "/v1/yo", nil, http.StatusOK, &yo)
	p.id = yo.ID
	return p
}

// verificar marca selfie y DNI (lo que en producción hace el flujo de verificación).
func (e *entorno) verificar(p persona, dni bool) {
	err := e.app.EnSistema(context.Background(), func(tx pgx.Tx) error {
		_, err := tx.Exec(context.Background(), `UPDATE usuarios SET selfie_en = now(), dni_en = CASE WHEN $2 THEN now() END, edad = 26 WHERE id = $1`, p.id, dni)
		return err
	})
	if err != nil {
		e.t.Fatal(err)
	}
}

func (e *entorno) pedir(p persona, metodo, ruta string, cuerpo any, estado int, out any) {
	e.t.Helper()
	var buf bytes.Buffer
	if cuerpo != nil {
		_ = json.NewEncoder(&buf).Encode(cuerpo)
	}
	r := httptest.NewRequest(metodo, ruta, &buf)
	if p.token != "" {
		r.Header.Set("Authorization", "Bearer "+p.token)
	}
	w := httptest.NewRecorder()
	e.h.ServeHTTP(w, r)
	if w.Code != estado {
		e.t.Fatalf("%s %s: %d, quería %d: %s", metodo, ruta, w.Code, estado, w.Body.String())
	}
	if out != nil {
		if err := json.Unmarshal(w.Body.Bytes(), out); err != nil {
			e.t.Fatalf("%s %s: %v", metodo, ruta, err)
		}
	}
}

type plan struct {
	ID, Slug, Titulo, MiEstado string
	Somos, Faltan, Pendientes  int
	Direccion                  *struct{ Texto string }
	Van                        []struct{ ID string }
}

func TestSinSesion(t *testing.T) {
	e := nuevoEntorno(t)
	e.pedir(persona{}, "GET", "/v1/yo", nil, http.StatusUnauthorized, nil)
	e.pedir(persona{token: "basura"}, "GET", "/v1/planes/ahora", nil, http.StatusUnauthorized, nil)
}

func TestElCicloDelPlan(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu, fede := e.persona("Nico"), e.persona("Lu"), e.persona("Fede")

	// Nico arma un truco: somos 3, falta 1, con aprobación, dirección oculta (por defecto).
	var p plan
	e.pedir(nico, "POST", "/v1/planes", map[string]any{
		"categoria": "juego", "titulo": "Truco en un bar de Güemes", "empiezaEn": time.Now().Add(2 * time.Hour),
		"ciudad": "Mar del Plata", "zona": "Güemes", "lugarNombre": "Bar La Cumbre",
		"direccion": "Güemes 2850", "lat": -38.0129, "lng": -57.5401, "somos": 3, "faltan": 2,
	}, http.StatusCreated, &p)
	if p.Somos != 3 || p.Faltan != 2 || p.MiEstado != "organizador" || p.Direccion == nil {
		t.Fatalf("plan recién creado: %+v", p)
	}

	// Lu no verificó la selfie: no se puede sumar.
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusUnprocessableEntity, nil)
	// Con selfie pero sin DNI, tampoco (el plan es sólo para verificados).
	e.verificar(lu, false)
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusUnprocessableEntity, nil)
	e.verificar(lu, true)

	// Lu ve el plan pero no la dirección exacta.
	var visto plan
	e.pedir(lu, "GET", "/v1/planes/"+p.ID, nil, http.StatusOK, &visto)
	if visto.Direccion != nil {
		t.Fatal("Lu ve la dirección antes de que la acepten")
	}

	// Lu y Fede se suman como grupo ("somos 2"): queda pendiente.
	var est struct{ Estado string }
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{"cantidad": 2, "mensaje": "Somos dos, jugamos bien"}, http.StatusOK, &est)
	if est.Estado != "pendiente" {
		t.Fatalf("estado: %q", est.Estado)
	}

	// Fede arma su propio grupo de 1 y pide: también pendiente, pero entonces no van a entrar los dos.
	e.verificar(fede, true)
	e.pedir(fede, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, &est)

	// Nico ve 2 pendientes y acepta a Lu (2 personas): plan completo.
	var ss struct {
		Solicitudes []struct {
			ID      string
			Persona struct{ ID string }
		}
	}
	e.pedir(nico, "GET", "/v1/planes/"+p.ID+"/solicitudes", nil, http.StatusOK, &ss)
	if len(ss.Solicitudes) != 2 {
		t.Fatalf("solicitudes: %+v", ss)
	}
	var deLu, deFede string
	for _, s := range ss.Solicitudes {
		if s.Persona.ID == lu.id {
			deLu = s.ID
		} else {
			deFede = s.ID
		}
	}
	// Lu no puede aceptar solicitudes de un plan ajeno.
	e.pedir(lu, "POST", "/v1/solicitudes/"+deFede+"/aceptar", nil, http.StatusNotFound, nil)
	e.pedir(nico, "POST", "/v1/solicitudes/"+deLu+"/aceptar", nil, http.StatusNoContent, nil)
	// No hay lugar para Fede.
	e.pedir(nico, "POST", "/v1/solicitudes/"+deFede+"/aceptar", nil, http.StatusConflict, nil)

	e.pedir(lu, "GET", "/v1/planes/"+p.ID, nil, http.StatusOK, &visto)
	if visto.Direccion == nil || visto.Direccion.Texto != "Güemes 2850" || visto.Faltan != 0 || visto.MiEstado != "aceptada" {
		t.Fatalf("Lu aceptada: %+v", visto)
	}
	// Fede sigue sin ver la dirección.
	visto = plan{}
	e.pedir(fede, "GET", "/v1/planes/"+p.ID, nil, http.StatusOK, &visto)
	if visto.Direccion != nil {
		t.Fatal("Fede ve la dirección")
	}

	// En Ahora, cerca de Güemes, aparece con la distancia.
	var ahora struct{ Planes []plan }
	e.pedir(fede, "GET", "/v1/planes/ahora?lat=-38.0130&lng=-57.5400", nil, http.StatusOK, &ahora)
	encontrado := false
	for _, x := range ahora.Planes {
		encontrado = encontrado || x.ID == p.ID
	}
	if !encontrado {
		t.Fatal("el plan no aparece en Ahora")
	}

	// El link público no trae la dirección.
	var pub plan
	e.pedir(persona{}, "GET", "/v1/publico/planes/"+p.Slug, nil, http.StatusOK, &pub)
	if pub.Direccion != nil || pub.Titulo != "Truco en un bar de Güemes" {
		t.Fatalf("público: %+v", pub)
	}

	// Lu y Nico compartieron el plan: Lu lo puede sumar a su gente; a Fede no.
	e.pedir(lu, "PUT", "/v1/gente/"+nico.id, nil, http.StatusNoContent, nil)
	e.pedir(lu, "PUT", "/v1/gente/"+fede.id, nil, http.StatusConflict, nil)

	// Si Fede bloquea a Nico, deja de ver el plan.
	e.pedir(fede, "PUT", "/v1/bloqueos/"+nico.id, nil, http.StatusNoContent, nil)
	e.pedir(fede, "GET", "/v1/planes/"+p.ID, nil, http.StatusNotFound, nil)
}

func TestIngresoLibreRespetaElCupo(t *testing.T) {
	e := nuevoEntorno(t)
	org, a, b := e.persona("Martu"), e.persona("Ana"), e.persona("Beto")
	e.verificar(a, true)
	e.verificar(b, true)
	var p plan
	e.pedir(org, "POST", "/v1/planes", map[string]any{
		"categoria": "deporte", "empiezaEn": time.Now().Add(time.Hour), "ciudad": "Mar del Plata",
		"zona": "Punta Mogotes", "lat": -38.07, "lng": -57.55, "somos": 9, "faltan": 1, "ingreso": "libre",
	}, http.StatusCreated, &p)
	if p.Titulo != "Deporte en Punta Mogotes" {
		t.Errorf("título por defecto: %q", p.Titulo)
	}
	var est struct{ Estado string }
	e.pedir(a, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, &est)
	if est.Estado != "aceptada" {
		t.Fatalf("ingreso libre: %q", est.Estado)
	}
	e.pedir(b, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusConflict, nil)
}
