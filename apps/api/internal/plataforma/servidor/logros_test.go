package servidor_test

import (
	"context"
	"fmt"
	"net/http"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/logros"
)

type logrosJSON struct {
	Nivel     string
	Planes    int
	Siguiente string
	Faltan    int
	Racha     *int
	Comodin   *bool
	Anfitrion bool
	Ocultas   bool
	Insignias []struct{ ID, Nombre string }
}

// vieja: la cuenta tiene más de una semana (valida los check-ins de los demás).
func (e *entorno) vieja(ps ...persona) {
	for _, p := range ps {
		// creado_en no lo puede tocar la app: lo hace el migrador.
		if err := e.migrador.EnSistema(context.Background(), func(tx pgx.Tx) error {
			_, err := tx.Exec(context.Background(), `UPDATE usuarios SET creado_en = now() - interval '60 days' WHERE id = $1`, p.id)
			return err
		}); err != nil {
			e.t.Fatal(err)
		}
	}
}

// planPasado arma un plan que ya pasó (hace `dias` días) organizado por org, con los que van y los
// que hicieron check-in (el organizador incluido si está en llegan). Devuelve el id.
func (e *entorno) planPasado(org persona, dias float64, categoria string, van, llegan []persona, extra ...string) string {
	e.t.Helper()
	var id string
	cuando := time.Now().Add(-time.Duration(dias * 24 * float64(time.Hour)))
	lugar := ""
	if len(extra) > 0 {
		lugar = extra[0]
	}
	e.valor(`INSERT INTO planes (slug, organizador_id, titulo, categoria, empieza_en, duracion_min, ciudad, lat, lng, somos, total, lugar_id)
		VALUES ($1, $2, 'Plan', $3, $4, 60, 'Villa Gesell', -37.26, -56.97, 1, 20, nullif($5, '')::uuid) RETURNING id`, &id,
		fmt.Sprintf("pasado-%d", time.Now().UnixNano()), org.id, categoria, cuando, lugar)
	llego := map[string]bool{}
	for _, p := range llegan {
		llego[p.id] = true
	}
	for _, p := range append([]persona{org}, van...) {
		var checkin *time.Time
		if llego[p.id] {
			checkin = &cuando
		}
		metodo := "geo"
		if lugar != "" {
			metodo = "puerta"
		}
		e.sistema(`INSERT INTO solicitudes (plan_id, usuario_id, cantidad, estado, respondida_en, checkin_en, checkin_metodo)
			VALUES ($1, $2, 1, 'aceptada', now(), $3, CASE WHEN $3::timestamptz IS NULL THEN NULL ELSE $4 END)`, id, p.id, checkin, metodo)
	}
	return id
}

func (e *entorno) logros(quien, de persona) logrosJSON {
	e.t.Helper()
	var p struct{ Logros logrosJSON }
	ruta := "/v1/yo"
	if quien.id != de.id {
		ruta = "/v1/personas/" + de.id
	}
	e.pedir(quien, "GET", ruta, nil, http.StatusOK, &p)
	return p.Logros
}

func TestLogrosPremianIr(t *testing.T) {
	e := nuevoEntorno(t)
	ana, beto, cami, nueva := e.persona("Ana"), e.persona("Beto"), e.persona("Cami"), e.persona("Nueva")
	e.vieja(ana, beto, cami)

	// Solo con una cuenta nueva (menos de 7 días) no cuenta: las cuentas nuevas no validan a otros.
	e.planPasado(beto, 30, "juego", []persona{ana, nueva}, []persona{ana, nueva})
	if l := e.logros(ana, ana); l.Planes != 0 || l.Nivel != "Nuevo" || l.Siguiente != "Habitué" || l.Faltan != 5 {
		t.Fatalf("con una cuenta nueva: %+v", l)
	}
	// Con Beto (vieja y con DNI) sí. Y Beto, que organizaba, no suma: hubo un solo ajeno que valida.
	e.planPasado(beto, 29, "juego", []persona{ana}, []persona{ana, beto})
	if l := e.logros(ana, ana); l.Planes != 1 {
		t.Fatalf("Ana con Beto: %+v", l)
	}
	if l := e.logros(beto, beto); l.Planes != 0 {
		t.Fatalf("Beto organizó con un ajeno: %+v", l)
	}
	// El mismo grupo dos veces la misma semana: suma una sola.
	e.planPasado(beto, 15, "juego", []persona{ana}, []persona{ana, beto})
	e.planPasado(beto, 15.01, "juego", []persona{ana}, []persona{ana, beto})
	if l := e.logros(ana, ana); l.Planes != 2 {
		t.Fatalf("mismo grupo, misma semana: %+v", l)
	}
	// Sumarse y no ir no da nada (y es un faltazo).
	e.planPasado(beto, 8, "juego", []persona{ana, cami}, []persona{beto, cami})
	if l := e.logros(ana, ana); l.Planes != 2 {
		t.Fatalf("faltazo sumó: %+v", l)
	}

	// Cinco de juego: nivel Habitué e insignia «De la mesa». La racha es de la propia.
	for i := range 3 {
		e.planPasado(cami, float64(i)*7+0.5, "juego", []persona{ana, beto}, []persona{ana, beto, cami})
	}
	l := e.logros(ana, ana)
	if l.Planes != 5 || l.Nivel != "Habitué" || l.Siguiente != "De fierro" || l.Faltan != 10 {
		t.Fatalf("Ana Habitué: %+v", l)
	}
	if len(l.Insignias) != 1 || l.Insignias[0].ID != "mesa" {
		t.Fatalf("insignias: %+v", l.Insignias)
	}
	// Tres semanas seguidas como mínimo (los planes de hace 15 y 29 días pueden sumar según el día).
	if l.Racha == nil || *l.Racha < 3 || l.Comodin == nil {
		t.Fatalf("racha: %+v", l)
	}
	// Cami organizó 3 planes que se hicieron (2 ajenos cada uno): Anfitrión, en la insignia y en sus planes.
	if l := e.logros(ana, cami); !l.Anfitrion || l.Racha != nil || l.Comodin != nil {
		t.Fatalf("Cami vista por Ana: %+v", l)
	}
	futuro := e.planNuevo(cami, nil)
	var visto struct{ Organizador struct{ Anfitrion bool } }
	e.pedir(ana, "GET", "/v1/planes/"+futuro.ID, nil, http.StatusOK, &visto)
	if !visto.Organizador.Anfitrion {
		t.Fatal("el plan de Cami no dice Anfitrión")
	}
	e.pedir(persona{}, "GET", "/v1/publico/planes/"+futuro.Slug, nil, http.StatusOK, &visto)
	if !visto.Organizador.Anfitrion {
		t.Fatal("el link público no dice Anfitrión")
	}
	// Cancelar tarde resta: deja de ser Anfitrión (3 de 4).
	e.sistema(`UPDATE planes SET cancelado_en = empieza_en - interval '1 hour' WHERE id = $1`, futuro.ID)
	if l := e.logros(ana, cami); l.Anfitrion {
		t.Fatalf("cancelar tarde no restó: %+v", l)
	}

	// Ocultar mis insignias: los demás no las ven; ella sí.
	e.pedir(ana, "PATCH", "/v1/yo", map[string]any{"ocultarInsignias": true}, http.StatusOK, nil)
	if l := e.logros(beto, ana); len(l.Insignias) != 0 || l.Nivel != "Habitué" {
		t.Fatalf("Ana vista por Beto: %+v", l)
	}
	if l := e.logros(ana, ana); len(l.Insignias) != 1 || !l.Ocultas {
		t.Fatalf("Ana se ve: %+v", l)
	}
}

func TestFaltazosLosVeQuienOrganiza(t *testing.T) {
	e := nuevoEntorno(t)
	org, falta, otra := e.persona("Org"), e.persona("Falta"), e.persona("Otra")
	e.verificar(falta, true)
	e.verificar(otra, true)
	e.planPasado(org, 3, "juego", []persona{falta, otra}, []persona{org, otra})
	e.planPasado(org, 5, "juego", []persona{falta, otra}, []persona{org, otra})
	// Un plan donde nadie usó el check-in no es faltazo.
	e.planPasado(org, 6, "juego", []persona{otra}, nil)
	p := e.planNuevo(org, map[string]any{"ingreso": "aprobacion"})
	e.pedir(falta, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	e.pedir(otra, "POST", "/v1/planes/"+p.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	var r struct {
		Solicitudes []struct {
			Persona struct {
				ID       string
				Faltazos int
			}
		}
	}
	e.pedir(org, "GET", "/v1/planes/"+p.ID+"/solicitudes", nil, http.StatusOK, &r)
	sols := r.Solicitudes
	if len(sols) != 2 {
		t.Fatalf("solicitudes: %+v", sols)
	}
	for _, s := range sols {
		quiero := 0
		if s.Persona.ID == falta.id {
			quiero = 2
		}
		if s.Persona.Faltazos != quiero {
			t.Fatalf("faltazos de %s: %d", s.Persona.ID, s.Persona.Faltazos)
		}
	}
	// Si reportó a quien organiza (se sintió insegura y no fue), esos planes no son faltazos.
	e.pedir(falta, "POST", "/v1/reportes", map[string]any{"usuarioId": org.id, "motivo": "inseguro"}, http.StatusNoContent, nil)
	r.Solicitudes = nil
	e.pedir(org, "GET", "/v1/planes/"+p.ID+"/solicitudes", nil, http.StatusOK, &r)
	for _, s := range r.Solicitudes {
		if s.Persona.Faltazos != 0 {
			t.Fatalf("faltazos después del reporte: %+v", s.Persona)
		}
	}
}

func TestTopeDePlanesAbiertos(t *testing.T) {
	e := nuevoEntorno(t)
	org := e.persona("Org")
	for range logros.AjenosParaOrganizar + 1 {
		e.planNuevo(org, nil)
	}
	if c := codigo(t, e, org, "POST", "/v1/planes", map[string]any{"categoria": "juego", "empiezaEn": time.Now().Add(2 * time.Hour),
		"ciudad": "Villa Gesell", "lat": -37.2633, "lng": -56.9733, "somos": 1, "faltan": 1}, http.StatusConflict); c != "tope_planes" {
		t.Fatalf("cuarto plan: %q", c)
	}
	// Con Plus, hasta 5.
	e.sistema(`INSERT INTO suscripciones (usuario_id, origen, externo, plan, estado, vence_en) VALUES ($1, 'referido', $2, 'semana', 'activa', now() + interval '1 day')`,
		org.id, "prueba-"+org.id)
	e.planNuevo(org, nil)
	e.planNuevo(org, nil)
	codigo(t, e, org, "POST", "/v1/planes", map[string]any{"categoria": "juego", "empiezaEn": time.Now().Add(2 * time.Hour),
		"ciudad": "Villa Gesell", "lat": -37.2633, "lng": -56.9733, "somos": 1, "faltan": 1}, http.StatusConflict)
}

func TestNoMeInteresaYDeshacer(t *testing.T) {
	e := nuevoEntorno(t)
	org, ana := e.persona("Org"), e.persona("Ana")
	p := e.planNuevo(org, map[string]any{"empiezaEn": time.Now().Add(time.Hour)})
	esta := func() bool {
		var r struct{ Planes []struct{ ID string } }
		e.pedir(ana, "GET", "/v1/planes/ahora?lat=-37.2633&lng=-56.9733", nil, http.StatusOK, &r)
		for _, x := range r.Planes {
			if x.ID == p.ID {
				return true
			}
		}
		return false
	}
	if !esta() {
		t.Fatal("el plan no está en Ahora")
	}
	e.pedir(ana, "POST", "/v1/planes/"+p.ID+"/ocultar", nil, http.StatusNoContent, nil)
	if esta() {
		t.Fatal("«No me interesa» no lo ocultó")
	}
	// El link sigue andando.
	e.pedir(ana, "GET", "/v1/planes/"+p.ID, nil, http.StatusOK, nil)
	e.pedir(ana, "DELETE", "/v1/planes/"+p.ID+"/ocultar", nil, http.StatusNoContent, nil)
	if !esta() {
		t.Fatal("«Deshacer» no lo devolvió")
	}
	e.pedir(ana, "POST", "/v1/planes/00000000-0000-0000-0000-000000000000/ocultar", nil, http.StatusNotFound, nil)
}

func TestReferidosCuentanConElPrimerCheckin(t *testing.T) {
	e := nuevoEntorno(t)
	ana, beto, nuevo := e.persona("Ana"), e.persona("Beto"), e.personaSinDNI("Nuevo")
	e.vieja(ana, beto)
	var inv struct {
		Codigo, Link         string
		Invitados, Premiados int
	}
	e.pedir(ana, "GET", "/v1/yo/invitar", nil, http.StatusOK, &inv)
	if len(inv.Codigo) != 8 || inv.Link != "https://caigo.test/i/"+inv.Codigo {
		t.Fatalf("invitar: %+v", inv)
	}
	// El mismo código la segunda vez.
	var otra struct{ Codigo string }
	e.pedir(ana, "GET", "/v1/yo/invitar", nil, http.StatusOK, &otra)
	if otra.Codigo != inv.Codigo {
		t.Fatal("el código cambió")
	}
	// El propio no, uno inventado no, y una cuenta vieja tampoco.
	e.pedir(ana, "POST", "/v1/yo/invitado", map[string]string{"codigo": inv.Codigo}, http.StatusConflict, nil)
	e.pedir(nuevo, "POST", "/v1/yo/invitado", map[string]string{"codigo": "zzzzzzzz"}, http.StatusNotFound, nil)
	e.pedir(beto, "POST", "/v1/yo/invitado", map[string]string{"codigo": inv.Codigo}, http.StatusConflict, nil)
	e.pedir(nuevo, "POST", "/v1/yo/invitado", map[string]string{"codigo": " " + inv.Codigo + " "}, http.StatusNoContent, nil)
	e.pedir(nuevo, "POST", "/v1/yo/invitado", map[string]string{"codigo": inv.Codigo}, http.StatusConflict, nil)

	premiar := func() int {
		n, err := logros.PremiarReferidos(context.Background(), e.app, time.Now())
		if err != nil {
			t.Fatal(err)
		}
		return n
	}
	plusDe := func(p persona) int {
		var n int
		e.valor(`SELECT count(*) FROM suscripciones WHERE usuario_id = $1 AND origen = 'referido' AND vence_en > now() + interval '6 days'`, &n, p.id)
		return n
	}
	// Sin DNI ni check-in: nada.
	if premiar() != 0 {
		t.Fatal("premió sin DNI ni check-in")
	}
	// Con DNI pero un check-in que no cuenta (fue solo): nada.
	e.sistema(`UPDATE usuarios SET dni_en = now() WHERE id = $1`, nuevo.id)
	e.planPasado(nuevo, 1, "juego", nil, []persona{nuevo})
	if premiar() != 0 || plusDe(ana) != 0 {
		t.Fatal("premió con un check-in que no cuenta")
	}
	// Con un plan que cuenta (Beto, cuenta vieja, también llegó): una semana de Plus para los dos, una sola vez.
	e.planPasado(beto, 2, "juego", []persona{nuevo}, []persona{nuevo, beto})
	if premiar() != 1 || premiar() != 0 {
		t.Fatal("el premio no se dio una sola vez")
	}
	if plusDe(ana) != 1 || plusDe(nuevo) != 1 {
		t.Fatalf("plus: ana %d, nuevo %d", plusDe(ana), plusDe(nuevo))
	}
	var plus struct{ Estado struct{ Activo bool } }
	e.pedir(ana, "GET", "/v1/plus", nil, http.StatusOK, &plus)
	if !plus.Estado.Activo {
		t.Fatal("Ana no tiene Plus")
	}
	e.pedir(ana, "GET", "/v1/yo/invitar", nil, http.StatusOK, &inv)
	if inv.Invitados != 1 || inv.Premiados != 1 {
		t.Fatalf("invitar después: %+v", inv)
	}

	// Tope: con 10 premios este mes, quien invita no recibe más (el referido sí).
	for i := range logros.PremiosPorMes {
		x := e.persona(fmt.Sprintf("Relleno %d", i))
		e.sistema(`INSERT INTO referidos (referido_id, referente_id, premiado_en) VALUES ($1, $2, now())`, x.id, beto.id)
	}
	otro := e.persona("Otro")
	var deBeto struct{ Codigo string }
	e.pedir(beto, "GET", "/v1/yo/invitar", nil, http.StatusOK, &deBeto)
	e.pedir(otro, "POST", "/v1/yo/invitado", map[string]string{"codigo": deBeto.Codigo}, http.StatusNoContent, nil)
	e.planPasado(ana, 2, "juego", []persona{otro}, []persona{otro, ana})
	if premiar() != 1 {
		t.Fatal("no premió al referido de Beto")
	}
	if plusDe(otro) != 1 || plusDe(beto) != 0 {
		t.Fatalf("tope: otro %d, beto %d", plusDe(otro), plusDe(beto))
	}
}

func TestBeneficioPorNivelEnElLugar(t *testing.T) {
	e := nuevoEntorno(t)
	duenia, ana, beto := e.persona("Dueña"), e.persona("Ana"), e.persona("Beto")
	e.vieja(ana, beto)
	var lugarID string
	e.valor(`INSERT INTO lugares (slug, nombre, ciudad, direccion, lat, lng, estado, beneficio_nivel)
		VALUES ($1, 'La Fonda', 'Villa Gesell', 'Av. 3', -37.26, -56.97, 'aprobado', '10 % off') RETURNING id`, &lugarID,
		fmt.Sprintf("fonda-%d", time.Now().UnixNano()))
	e.sistema(`INSERT INTO lugar_miembros (lugar_id, usuario_id) VALUES ($1, $2)`, lugarID, duenia.id)
	// El lugar lo muestra en sus planes.
	hoy := func(quien persona) string {
		id := e.planPasado(duenia, 0.01, "previa", []persona{quien}, nil, lugarID)
		e.sistema(`UPDATE planes SET empieza_en = now() WHERE id = $1`, id)
		return id
	}
	checkin := func(planID string, quien persona) struct{ BeneficioNivel, Nivel string } {
		var v struct{ BeneficioNivel, Nivel string }
		e.pedir(duenia, "POST", "/v1/lugares/"+lugarID+"/planes/"+planID+"/checkin/"+quien.id, nil, http.StatusOK, &v)
		return v
	}
	// Beto todavía es Nuevo: no le toca.
	if v := checkin(hoy(beto), beto); v.BeneficioNivel != "" {
		t.Fatalf("Beto nuevo: %+v", v)
	}
	// Ana es Habitué (5 planes en el lugar, validados por el lugar): le toca una vez por día.
	for i := range 5 {
		e.planPasado(duenia, float64(i+2), "previa", []persona{ana}, []persona{ana}, lugarID)
	}
	if v := checkin(hoy(ana), ana); v.BeneficioNivel != "10 % off" || v.Nivel != "Habitué" {
		t.Fatalf("Ana Habitué: %+v", v)
	}
	if v := checkin(hoy(ana), ana); v.BeneficioNivel != "" {
		t.Fatalf("dos veces el mismo día: %+v", v)
	}
	var plan struct {
		Lugar struct{ BeneficioNivel string }
	}
	e.pedir(ana, "GET", "/v1/planes/"+hoy(beto), nil, http.StatusOK, &plan)
	if plan.Lugar.BeneficioNivel != "10 % off" {
		t.Fatalf("el plan no muestra el beneficio por nivel: %+v", plan)
	}
}
