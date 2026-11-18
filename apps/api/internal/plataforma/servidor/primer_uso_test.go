package servidor_test

import (
	"fmt"
	"net/http"
	"strings"
	"testing"
	"time"
)

// Mirar sin DNI (18/11): quien entra sin el DNI ve planes, el detalle y perfiles; el DNI se pide recién
// para sumarse, crear, chatear o ver a la gente. Y la API lo exige, no sólo las apps.
func TestMirarSinDNI(t *testing.T) {
	e := nuevoEntorno(t)
	org, lu := e.persona("Org"), e.persona("Lu")
	e.verificar(lu, true)
	nueva := e.personaSinDNI("Nueva")
	ciudad := ciudadUnica()
	// La dirección a la vista (no la oculta): igual, sin DNI no se ve.
	p := e.planNuevo(org, map[string]any{"ciudad": ciudad, "direccion": "Paseo 105 nº 220", "ocultarDireccion": false})
	e.pedir(lu, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.pedir(org, "PATCH", "/v1/yo", map[string]any{"zona": "Centro", "trabajo": "Bar La Fonda"}, http.StatusOK, nil)

	// Mirar: las listas, el detalle y los perfiles.
	var lista struct{ Planes []plan }
	e.pedir(nueva, "GET", "/v1/planes/explorar?ciudad="+strings.ReplaceAll(ciudad, " ", "+"), nil, http.StatusOK, &lista)
	if len(lista.Planes) != 1 || lista.Planes[0].ID != p.ID {
		t.Fatalf("explorar sin DNI: %+v", lista)
	}
	// Sin DNI, de la gente del plan sólo el primer nombre y la foto: sin id no se llega a su perfil ni a
	// sus próximos planes (personas.SinDNI los esconde), ni a si ya llegó.
	var crudo struct {
		Planes []struct {
			Organizador map[string]any
			Van         []map[string]any
		}
	}
	e.pedir(nueva, "GET", "/v1/planes/explorar?ciudad="+strings.ReplaceAll(ciudad, " ", "+"), nil, http.StatusOK, &crudo)
	for _, x := range crudo.Planes {
		if x.Organizador["id"] != nil || len(x.Van) != 2 {
			t.Fatalf("explorar sin DNI muestra de más: %+v", x)
		}
		for _, v := range x.Van {
			if v["id"] != nil || v["llego"] != nil {
				t.Fatalf("explorar sin DNI con id o llegó: %+v", v)
			}
		}
	}
	e.pedir(nueva, "GET", "/v1/planes/ahora?ciudad="+strings.ReplaceAll(ciudad, " ", "+"), nil, http.StatusOK, nil)
	var visto plan
	e.pedir(nueva, "GET", "/v1/planes/"+p.ID, nil, http.StatusOK, &visto)
	if visto.Direccion != nil || len(visto.Van) != 2 || visto.Van[0].ID != "" || visto.Van[1].ID != "" {
		t.Fatalf("detalle sin DNI: %+v", visto)
	}
	// Con DNI, la misma dirección sí se ve (el plan no la oculta).
	var conDNI plan
	e.pedir(lu, "GET", "/v1/planes/"+p.ID, nil, http.StatusOK, &conDNI)
	if conDNI.Direccion == nil {
		t.Fatal("con DNI no se ve la dirección que el plan muestra")
	}
	// Del perfil ajeno, sin DNI no se ve el barrio, el trabajo ni dónde va a estar.
	var perfil map[string]any
	e.pedir(nueva, "GET", "/v1/personas/"+org.id, nil, http.StatusOK, &perfil)
	if perfil["nombre"] != "Org" || perfil["zona"] != "" || perfil["trabajo"] != "" {
		t.Fatalf("perfil sin DNI: %+v", perfil)
	}
	if px, _ := perfil["proximos"].([]any); len(px) != 0 {
		t.Fatalf("próximos sin DNI: %v", perfil["proximos"])
	}
	e.pedir(lu, "GET", "/v1/personas/"+org.id, nil, http.StatusOK, &perfil)
	if px, _ := perfil["proximos"].([]any); perfil["zona"] != "Centro" || len(px) != 1 {
		t.Fatalf("perfil con DNI: %+v", perfil)
	}
	// Lo propio que piden las pantallas al abrir (vacío) y lo de seguridad.
	for _, r := range []string{"/v1/planes/mios?cual=me_sume", "/v1/invitaciones", "/v1/pases", "/v1/plus",
		"/v1/gente", "/v1/privados", "/v1/bloqueos"} {
		e.pedir(nueva, "GET", r, nil, http.StatusOK, nil)
	}
	e.pedir(nueva, "PUT", "/v1/yo/ubicacion", map[string]any{"lat": -38.0, "lng": -57.55}, http.StatusNoContent, nil)
	e.pedir(nueva, "POST", "/v1/reportes", map[string]any{"usuarioId": org.id, "motivo": "falso"}, http.StatusNoContent, nil)
	// «Esta semana» también se mira (sin direcciones) y «No me interesa» es de quien mira.
	var sem struct{ Planes []plan }
	e.pedir(nueva, "GET", "/v1/semana?ciudad="+strings.ReplaceAll(ciudad, " ", "+"), nil, http.StatusOK, &sem)
	for _, x := range sem.Planes {
		if x.Direccion != nil {
			t.Fatalf("semana sin DNI con dirección: %+v", x)
		}
	}
	e.pedir(nueva, "POST", "/v1/planes/"+p.ID+"/ocultar", nil, http.StatusNoContent, nil)
	e.pedir(nueva, "DELETE", "/v1/planes/"+p.ID+"/ocultar", nil, http.StatusNoContent, nil)
	// «Traé a alguien»: la hoja (su link y el código de quien la invitó) anda antes del DNI.
	e.pedir(nueva, "GET", "/v1/yo/invitar", nil, http.StatusOK, nil)

	// Hacer algo con otra gente pide el DNI.
	crear := map[string]any{"categoria": "juego", "empiezaEn": time.Now().Add(2 * time.Hour), "ciudad": ciudad,
		"zona": "Centro", "lat": -37.2633, "lng": -56.9733, "somos": 2, "faltan": 2}
	for _, c := range []struct {
		metodo, ruta string
		cuerpo       any
	}{
		{"POST", "/v1/planes/" + p.ID + "/sumarme", map[string]any{}},
		{"POST", "/v1/planes", crear},
		{"GET", "/v1/planes/" + p.ID + "/mensajes", nil},
		{"POST", "/v1/planes/" + p.ID + "/mensajes", map[string]any{"texto": "hola"}},
		{"POST", "/v1/privados/" + org.id, map[string]any{"texto": "hola"}},
		{"GET", "/v1/gente/mazo?modo=gente", nil},
		{"POST", "/v1/gente/" + org.id + "/reaccion", map[string]any{"modo": "gente", "accion": "interesa"}},
		{"PUT", "/v1/gente/" + org.id, nil},
		{"POST", "/v1/planes/" + p.ID + "/entradas", map[string]any{"cantidad": 1}},
		{"POST", "/v1/planes/" + p.ID + "/checkin", map[string]any{}},
		{"GET", "/v1/tiempo-real", nil},
		{"POST", "/v1/planes/" + p.ID + "/espera", map[string]any{}},
		{"POST", "/v1/planes/" + p.ID + "/todo-bien", map[string]any{}},
		{"GET", "/v1/planes/" + p.ID + "/fotos", nil},
	} {
		if cod := codigo(t, e, nueva, c.metodo, c.ruta, c.cuerpo, http.StatusForbidden); cod != "falta_dni" {
			t.Fatalf("%s %s sin DNI: %q", c.metodo, c.ruta, cod)
		}
	}

	// Verificarse es un solo paso: frente, dorso y la selfie del DNI, sin teléfono ni selfie antes.
	var frente, dorso, selfie struct{ ID string }
	e.pedir(nueva, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusCreated, &frente)
	e.pedir(nueva, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusCreated, &dorso)
	e.pedir(nueva, "POST", "/v1/archivos?tipo=selfie", jpeg, http.StatusCreated, &selfie)
	e.pedir(nueva, "POST", "/v1/verificacion/dni", map[string]string{"frenteId": frente.ID, "dorsoId": dorso.ID,
		"selfieId": selfie.ID, "numero": fmt.Sprint(30000000 + time.Now().UnixNano()%60000000), "nacimiento": "1998-05-04"},
		http.StatusNoContent, nil)
	var estado struct {
		Selfie bool
		DNI    string
	}
	e.pedir(nueva, "GET", "/v1/verificacion", nil, http.StatusOK, &estado)
	if !estado.Selfie || estado.DNI != "aprobada" {
		t.Fatalf("verificación en un paso: %+v", estado)
	}
	// Y ya se suma, y ve la dirección.
	e.pedir(nueva, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.pedir(nueva, "GET", "/v1/planes/"+p.ID, nil, http.StatusOK, &visto)
	if visto.Direccion == nil || visto.MiEstado != "aceptada" {
		t.Fatalf("después del DNI: %+v", visto)
	}
}

// La confianza en la tarjeta y los filtros de seguridad, gratis.
func TestConfianzaYSoloChicas(t *testing.T) {
	e := nuevoEntorno(t)
	org, ana, beto := e.persona("Org"), e.persona("Ana"), e.persona("Beto")
	e.verificar(ana, true)
	e.verificar(beto, true)
	ciudad := ciudadUnica()
	chicas := e.planNuevo(org, map[string]any{"ciudad": ciudad, "genero": "mujeres", "somos": 1, "faltan": 3})
	mixto := e.planNuevo(org, map[string]any{"ciudad": ciudad, "somos": 1, "faltan": 3})
	// Con acompañantes sin cuenta («somos 3»), no son «todos verificados»: no tienen DNI.
	grupo := e.planNuevo(org, map[string]any{"ciudad": ciudad, "somos": 3, "faltan": 2})
	e.pedir(beto, "POST", "/v1/planes/"+mixto.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)

	type conConfianza struct {
		ID          string
		Organizador struct {
			Nombre     string
			Asistencia struct{ Fue, De int }
		}
		Confianza struct {
			TodosVerificados, SoloChicas bool
			Chicas                       *int
		}
	}
	var lista struct{ Planes []conConfianza }
	q := "/v1/planes/explorar?ciudad=" + strings.ReplaceAll(ciudad, " ", "+")
	ver := func() map[string]conConfianza {
		e.pedir(beto, "GET", q, nil, http.StatusOK, &lista)
		m := map[string]conConfianza{}
		for _, p := range lista.Planes {
			m[p.ID] = p
		}
		return m
	}
	m := ver()
	if c := m[chicas.ID].Confianza; !c.TodosVerificados || !c.SoloChicas || c.Chicas != nil {
		t.Fatalf("sólo chicas con una: %+v", c)
	}
	if c := m[mixto.ID].Confianza; !c.TodosVerificados || c.SoloChicas {
		t.Fatalf("mixto: %+v", c)
	}
	if c := m[grupo.ID].Confianza; c.TodosVerificados {
		t.Fatalf("grupo con acompañantes sin cuenta: %+v", c)
	}
	// Nunca se dice cuántas chicas van (junto al lugar y la hora, el número sirve para buscarlas).
	e.pedir(ana, "POST", "/v1/planes/"+chicas.ID+"/sumarme", map[string]any{"mujer": true}, http.StatusOK, nil)
	if c := ver()[chicas.ID].Confianza; c.Chicas != nil || !c.SoloChicas {
		t.Fatalf("van 2 chicas: %+v", c)
	}
	// Alguien sin DNI en el plan (cuentas de antes del 16/11): ya no son todos verificados.
	e.sistema(`UPDATE usuarios SET dni_en = NULL WHERE id = $1`, beto.id)
	e.pedir(ana, "GET", q, nil, http.StatusOK, &lista)
	for _, p := range lista.Planes {
		if p.ID == mixto.ID && p.Confianza.TodosVerificados {
			t.Fatal("con uno sin DNI dice «todos verificados»")
		}
	}

	// «Sólo chicas» y «verificados»: gratis (Beto no tiene Plus).
	e.pedir(ana, "GET", q+"&chicas=1", nil, http.StatusOK, &lista)
	if len(lista.Planes) != 1 || lista.Planes[0].ID != chicas.ID {
		t.Fatalf("sólo chicas: %+v", lista.Planes)
	}
	e.pedir(ana, "GET", q+"&verificados=1", nil, http.StatusOK, nil)
	// También sin cuenta (la web antes de entrar).
	e.pedir(persona{}, "GET", "/v1/publico/planes/explorar?ciudad="+strings.ReplaceAll(ciudad, " ", "+")+"&chicas=1", nil, http.StatusOK, &lista)
	if len(lista.Planes) != 1 || !lista.Planes[0].Confianza.SoloChicas {
		t.Fatalf("sólo chicas sin cuenta: %+v", lista.Planes)
	}
	// Los finos de comodidad siguen siendo de Plus.
	if c := codigo(t, e, ana, "GET", q+"&gratis=1", nil, http.StatusForbidden); c != "plus" {
		t.Fatalf("gratis sin Plus: %q", c)
	}
}
