package servidor_test

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/confianza"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/clerk/clerkfalso"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/vetados"
)

// personaCon da de alta a alguien con un mail propio (los de persona() se repiten entre corridas).
// Devuelve también su sub de Clerk, para entrar con otra cuenta o volver con la misma.
func (e *entorno) personaCon(nombre, email string) (persona, string) {
	e.t.Helper()
	sub := e.cuentaClerk(nombre, email)
	p := persona{token: e.tokenDe(sub)}
	var yo struct{ ID string }
	e.pedir(p, "GET", "/v1/yo", nil, http.StatusOK, &yo)
	p.id = yo.ID
	// Como las apps: la sesión propia copia el mail de Clerk (con él se arma la huella).
	e.pedir(p, "POST", "/v1/sesion?cliente=ios", nil, http.StatusOK, nil)
	return p, sub
}

func (e *entorno) cuentaClerk(nombre, email string) string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	sub := "user_" + hex.EncodeToString(b)
	e.clerk.Usuarios[sub] = clerkfalso.Usuario{Nombre: nombre, Email: email, Verificado: true}
	return sub
}

func (e *entorno) tokenDe(sub string) string {
	return e.clerk.Token(e.t, clerkfalso.Reclamos{Sub: sub, Exp: time.Now().Add(time.Hour)})
}

// pedirDNI sube frente, dorso y selfie y manda el DNI con ese número.
func (e *entorno) pedirDNI(p persona, numero string, estado int) string {
	e.t.Helper()
	var frente, dorso, selfie struct{ ID string }
	e.pedir(p, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusCreated, &frente)
	e.pedir(p, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusCreated, &dorso)
	e.pedir(p, "POST", "/v1/archivos?tipo=selfie", jpeg, http.StatusCreated, &selfie)
	var pr struct{ Code string }
	var out any = &pr
	if estado == http.StatusNoContent {
		out = nil
	}
	e.pedir(p, "POST", "/v1/verificacion/dni", map[string]string{"frenteId": frente.ID, "dorsoId": dorso.ID, "selfieId": selfie.ID,
		"numero": numero, "apellido": "PEREZ", "nombres": "ROCIO", "nacimiento": "1998-05-04"}, estado, out)
	return pr.Code
}

// aprobarDNI: el equipo aprueba el DNI pendiente de p.
func (e *entorno) aprobarDNI(equipo, p persona) {
	e.t.Helper()
	var cola struct {
		Pendientes []struct{ ID, UsuarioID string }
	}
	e.pedir(equipo, "GET", "/v1/equipo/dni", nil, http.StatusOK, &cola)
	for _, x := range cola.Pendientes {
		if x.UsuarioID == p.id {
			e.pedir(equipo, "POST", "/v1/equipo/dni/"+x.ID, map[string]any{"aprobar": true}, http.StatusNoContent, nil)
			return
		}
	}
	e.t.Fatal("el DNI no está en la cola")
}

func (e *entorno) cuenta(q string, args ...any) int {
	e.t.Helper()
	var n int
	e.valor(q, &n, args...)
	return n
}

// Quien fue suspendido no vuelve: ni con otra cuenta con el mismo mail (aunque lo disfrace), ni
// verificando su DNI en otra cuenta, ni con la misma cuenta después de la purga. La lista guarda
// sólo huellas, y reactivar la limpia.
func TestVetados(t *testing.T) {
	e := nuevoEntorno(t)
	ctx := context.Background()
	eq := e.persona("Moderación")
	e.alEquipo(eq)
	marca := time.Now().UnixNano()
	mail := fmt.Sprintf("rocio.veto%d@gmail.com", marca)
	disfrazado := fmt.Sprintf("Rocio.Veto%d+caigo@googlemail.com", marca)
	numero := fmt.Sprint(20000000 + marca%70000000)
	huellas := [][]byte{e.vetados.Huella(vetados.Email, mail), e.vetados.Huella(vetados.DNI, numero)}
	t.Cleanup(func() { e.sistema(`DELETE FROM vetados WHERE huella = ANY ($1)`, huellas) })

	ro, sub := e.personaCon("Rocío", mail)
	e.pedirDNI(ro, numero, http.StatusNoContent)
	e.aprobarDNI(eq, ro)
	e.pedir(eq, "POST", "/v1/equipo/usuarios/"+ro.id+"/suspender", map[string]string{"motivo": "Acoso"}, http.StatusNoContent, nil)
	if n := e.cuenta(`SELECT count(*) FROM vetados WHERE usuario_id = $1 AND huella = ANY ($2) AND vence_en IS NULL`, ro.id, huellas); n != 2 {
		t.Fatalf("huellas anotadas: %d", n)
	}
	// Sólo huellas: nada de la tabla tiene el mail ni el número.
	if n := e.cuenta(`SELECT count(*) FROM vetados WHERE usuario_id = $1 AND (position($2::bytea IN huella) > 0
		OR position($3::bytea IN huella) > 0)`, ro.id, []byte(mail), []byte(numero)); n != 0 {
		t.Fatal("la lista tiene datos en claro")
	}

	// Otra cuenta con el mismo mail (con puntos, mayúsculas y +algo): entra suspendida.
	otra := persona{token: e.tokenDe(e.cuentaClerk("Ro", disfrazado))}
	e.pedir(otra, "GET", "/v1/yo", nil, http.StatusForbidden, nil)
	e.pedir(otra, "POST", "/v1/sesion?cliente=android", nil, http.StatusForbidden, nil)
	// Otra cuenta con otro mail y su DNI: no se verifica.
	tercera, _ := e.personaCon("Rocío", fmt.Sprintf("otra%d@caigo.test", marca))
	if c := e.pedirDNI(tercera, numero, http.StatusUnprocessableEntity); c != "dni_vetado" {
		t.Fatalf("DNI vetado: %q", c)
	}

	// Reactivada, sus huellas se borran y el mail vuelve a entrar.
	e.pedir(eq, "POST", "/v1/equipo/usuarios/"+ro.id+"/reactivar", map[string]string{}, http.StatusNoContent, nil)
	if n := e.cuenta(`SELECT count(*) FROM vetados WHERE usuario_id = $1`, ro.id); n != 0 {
		t.Fatalf("huellas después de reactivar: %d", n)
	}
	otra2 := persona{token: e.tokenDe(e.cuentaClerk("Ro", disfrazado))}
	e.pedir(otra2, "GET", "/v1/yo", nil, http.StatusOK, nil)

	// Se borra la cuenta para zafar y el equipo la suspende igual (todavía no se purgó).
	e.pedir(ro, "DELETE", "/v1/yo", nil, http.StatusNoContent, nil)
	e.pedir(eq, "POST", "/v1/equipo/usuarios/"+ro.id+"/suspender", map[string]string{"motivo": "Acoso"}, http.StatusNoContent, nil)
	e.sistema(`UPDATE usuarios SET baja_en = now() - interval '31 days' WHERE id = $1`, ro.id)
	if _, err := confianza.Purgar(ctx, e.app, archivos.Disco{Raiz: e.archivos}, e.vetados, time.Now().AddDate(0, 0, -confianza.DiasParaPurgar)); err != nil {
		t.Fatal(err)
	}
	// Purgada: sin DNI ni mail, y las huellas sueltas vencen a los 2 años.
	if n := e.cuenta(`SELECT count(*) FROM verificaciones_dni WHERE usuario_id = $1`, ro.id); n != 0 {
		t.Fatalf("DNI después de la purga: %d", n)
	}
	if n := e.cuenta(`SELECT count(*) FROM vetados WHERE huella = ANY ($1) AND usuario_id IS NULL
		AND vence_en BETWEEN now() + interval '729 days' AND now() + interval '731 days'`, huellas); n != 2 {
		t.Fatalf("huellas sueltas: %d", n)
	}
	// Con la misma cuenta de Clerk (el clerk_id quedó libre): suspendida otra vez, no arranca de cero.
	e.pedir(persona{token: e.tokenDe(sub)}, "GET", "/v1/yo", nil, http.StatusForbidden, nil)
	// Vencidas, el worker las borra.
	e.sistema(`UPDATE vetados SET vence_en = now() - interval '1 minute' WHERE huella = ANY ($1)`, huellas)
	e.plazos()
	if n := e.cuenta(`SELECT count(*) FROM vetados WHERE huella = ANY ($1)`, huellas); n != 0 {
		t.Fatalf("huellas vencidas: %d", n)
	}
}

// Una cuenta suspendida sin huellas (de antes de la lista) no se purga sin la clave: si no, su DNI
// y su mail se borrarían sin quedar vetados.
func TestPurgaDeSuspendidaSinClave(t *testing.T) {
	e := nuevoEntorno(t)
	ctx := context.Background()
	mail := fmt.Sprintf("vieja%d@caigo.test", time.Now().UnixNano())
	h := e.vetados.Huella(vetados.Email, mail)
	t.Cleanup(func() { e.sistema(`DELETE FROM vetados WHERE huella = $1`, h) })
	v, _ := e.personaCon("Vieja", mail)
	e.sistema(`UPDATE usuarios SET suspendido_en = now(), suspension_motivo = 'Spam', baja_en = now() - interval '31 days' WHERE id = $1`, v.id)
	antes := time.Now().AddDate(0, 0, -confianza.DiasParaPurgar)
	if _, err := confianza.Purgar(ctx, e.app, archivos.Disco{Raiz: e.archivos}, nil, antes); err != nil {
		t.Fatal(err)
	}
	var purgada bool
	e.valor(`SELECT purgada_en IS NOT NULL FROM usuarios WHERE id = $1`, &purgada, v.id)
	if purgada {
		t.Fatal("se purgó sin anotar sus huellas")
	}
	if _, err := confianza.Purgar(ctx, e.app, archivos.Disco{Raiz: e.archivos}, e.vetados, antes); err != nil {
		t.Fatal(err)
	}
	e.valor(`SELECT purgada_en IS NOT NULL FROM usuarios WHERE id = $1`, &purgada, v.id)
	if !purgada || e.cuenta(`SELECT count(*) FROM vetados WHERE huella = $1 AND usuario_id IS NULL`, h) != 1 {
		t.Fatal("con la clave tenía que purgarse y quedar vetada")
	}
}

// plazos corre las limpiezas del worker (confianza.Plazos).
func (e *entorno) plazos() {
	e.t.Helper()
	for _, q := range confianza.Plazos {
		e.sistema(q)
	}
}

// Después de la purga: los mensajes del plan quedan anónimos hasta el año de que terminó el plan,
// los privados, la asistencia y «¿Volverías?» hasta el año de la purga, y los reportes hasta los 2 años.
func TestPlazosDespuesDeLaPurga(t *testing.T) {
	e := nuevoEntorno(t)
	ctx := context.Background()
	marca := time.Now().UnixNano()
	ida, _ := e.personaCon("Ida", fmt.Sprintf("ida%d@caigo.test", marca))
	queda := e.persona("Queda")
	p := e.planNuevo(queda, nil)
	e.sistema(`INSERT INTO solicitudes (plan_id, usuario_id, estado, mensaje) VALUES ($1, $2, 'aceptada', 'Llevo la pelota')`, p.ID, ida.id)
	e.sistema(`INSERT INTO mensajes (plan_id, usuario_id, texto) VALUES ($1, $2, '¿Llevo hielo?')`, p.ID, ida.id)
	e.sistema(`INSERT INTO privados (de_id, a_id, texto) VALUES ($1, $2, 'Hola'), ($2, $1, 'Hola, ¿qué tal?')`, ida.id, queda.id)
	e.sistema(`INSERT INTO volveria (plan_id, usuario_id, otro_id, si) VALUES ($1, $2, $3, true)`, p.ID, queda.id, ida.id)
	e.sistema(`INSERT INTO reportes (usuario_id, sobre_usuario_id, motivo) VALUES ($1, $2, 'no_vino')`, queda.id, ida.id)
	e.pedir(ida, "DELETE", "/v1/yo", nil, http.StatusNoContent, nil)
	e.sistema(`UPDATE usuarios SET baja_en = now() - interval '31 days' WHERE id = $1`, ida.id)
	if _, err := confianza.Purgar(ctx, e.app, archivos.Disco{Raiz: e.archivos}, e.vetados, time.Now().AddDate(0, 0, -confianza.DiasParaPurgar)); err != nil {
		t.Fatal(err)
	}
	var mensaje string
	e.valor(`SELECT mensaje FROM solicitudes WHERE plan_id = $1 AND usuario_id = $2`, &mensaje, p.ID, ida.id)
	if mensaje != "" {
		t.Fatalf("el texto de la solicitud quedó: %q", mensaje)
	}
	cuantos := func() [5]int {
		return [5]int{
			e.cuenta(`SELECT count(*) FROM mensajes WHERE usuario_id = $1`, ida.id),
			e.cuenta(`SELECT count(*) FROM privados WHERE de_id = $1 OR a_id = $1`, ida.id),
			e.cuenta(`SELECT count(*) FROM solicitudes WHERE usuario_id = $1`, ida.id),
			e.cuenta(`SELECT count(*) FROM volveria WHERE otro_id = $1`, ida.id),
			e.cuenta(`SELECT count(*) FROM reportes WHERE sobre_usuario_id = $1`, ida.id),
		}
	}
	// Recién purgada, queda todo (anónimo).
	e.plazos()
	if c := cuantos(); c != [5]int{1, 2, 1, 1, 1} {
		t.Fatalf("recién purgada: %v", c)
	}
	// Al año de la purga, con el plan todavía por delante: el mensaje del plan sigue; lo demás, no.
	e.sistema(`UPDATE usuarios SET purgada_en = now() - interval '13 months' WHERE id = $1`, ida.id)
	e.plazos()
	if c := cuantos(); c != [5]int{1, 0, 0, 0, 1} {
		t.Fatalf("al año de la purga: %v", c)
	}
	// Un año después de que terminó el plan, el mensaje se va.
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '14 months' WHERE id = $1`, p.ID)
	e.plazos()
	if c := cuantos(); c != [5]int{0, 0, 0, 0, 1} {
		t.Fatalf("al año del plan: %v", c)
	}
	// A los 2 años de la purga, el reporte.
	e.sistema(`UPDATE usuarios SET purgada_en = now() - interval '25 months' WHERE id = $1`, ida.id)
	e.plazos()
	if c := cuantos(); c != [5]int{0, 0, 0, 0, 0} {
		t.Fatalf("a los 2 años: %v", c)
	}
}

// Los intereses duran 6 meses; un match, mientras se escriban (y 6 meses después).
func TestPlazoDeLosIntereses(t *testing.T) {
	e := nuevoEntorno(t)
	a, b, c, d, f := e.persona("Ana"), e.persona("Bruno"), e.persona("Caro"), e.persona("Dani"), e.persona("Fer")
	viejo, nuevo := "now() - interval '7 months'", "now() - interval '1 month'"
	interes := func(de, a persona, cuando string) {
		e.sistema(`INSERT INTO intereses (usuario_id, otro_id, modo, accion, creado_en) VALUES ($1, $2, 'citas', 'interesa', `+cuando+`)`, de.id, a.id)
	}
	interes(a, b, viejo) // de un lado, viejo: se va
	interes(a, c, nuevo) // de un lado, nuevo: queda
	interes(c, d, viejo) // match viejo sin mensajes: se va
	interes(d, c, viejo)
	interes(d, f, viejo) // match viejo, pero se escribieron hace un mes: queda
	interes(f, d, viejo)
	e.sistema(`INSERT INTO privados (de_id, a_id, texto, creado_en) VALUES ($1, $2, '¿Vamos el sábado?', now() - interval '1 month')`, f.id, d.id)
	e.plazos()
	hay := func(de, a persona) bool {
		return e.cuenta(`SELECT count(*) FROM intereses WHERE usuario_id = $1 AND otro_id = $2`, de.id, a.id) == 1
	}
	if hay(a, b) || !hay(a, c) || hay(c, d) || hay(d, c) || !hay(d, f) || !hay(f, d) {
		t.Fatalf("intereses: a→b %v, a→c %v, c↔d %v %v, d↔f %v %v", hay(a, b), hay(a, c), hay(c, d), hay(d, c), hay(d, f), hay(f, d))
	}
}

// Una foto que se saca del perfil o de un plan deja de verse por su URL en el momento, y el worker la
// borra del disco. Las sueltas (subidas que nunca se usaron) las barre a partir del día.
func TestFotosQueSeSacan(t *testing.T) {
	e := nuevoEntorno(t)
	ctx := context.Background()
	p := e.persona("Fotos")
	subir := func() struct{ ID, URL string } {
		var a struct{ ID, URL string }
		e.pedir(p, "POST", "/v1/archivos?tipo=foto", jpeg, http.StatusCreated, &a)
		return a
	}
	ruta := func(id string) string {
		var r string
		e.valor(`SELECT ruta FROM archivos WHERE id = $1`, &r, id)
		return r
	}
	disco := archivos.Disco{Raiz: e.archivos}
	borrarDelDisco := func(id, r string) {
		t.Helper()
		evs := e.eventos(archivos.EventoBorrar, r)
		if len(evs) != 1 {
			t.Fatalf("eventos de borrado de %s: %d", id, len(evs))
		}
		if err := disco.ManejadorBorrar(ctx, evs[0].Datos); err != nil {
			t.Fatal(err)
		}
		if _, err := os.Stat(filepath.Join(e.archivos, r)); !os.IsNotExist(err) {
			t.Fatalf("la foto %s sigue en el disco: %v", id, err)
		}
	}

	uno, dos := subir(), subir()
	e.pedir(p, "PATCH", "/v1/yo", map[string]any{"fotos": []string{uno.URL, dos.URL}}, http.StatusOK, nil)
	r1 := ruta(uno.ID)
	e.pedir(p, "PATCH", "/v1/yo", map[string]any{"fotos": []string{dos.URL}}, http.StatusOK, nil)
	e.pedir(p, "GET", "/v1/archivos/"+uno.ID, nil, http.StatusNotFound, nil)
	e.pedir(p, "GET", "/v1/archivos/"+dos.ID, nil, http.StatusOK, nil)
	borrarDelDisco(uno.ID, r1)

	// Un plan con foto, y otro que usa la misma (como uno repetido): sacarla de uno no la borra.
	tres := subir()
	pl := e.planNuevo(p, map[string]any{"foto": tres.URL})
	otro := e.planNuevo(p, map[string]any{"foto": tres.URL})
	r3 := ruta(tres.ID)
	e.pedir(p, "PATCH", "/v1/planes/"+pl.ID, map[string]any{"foto": ""}, http.StatusOK, nil)
	e.pedir(p, "GET", "/v1/archivos/"+tres.ID, nil, http.StatusOK, nil)
	e.pedir(p, "PATCH", "/v1/planes/"+otro.ID, map[string]any{"foto": ""}, http.StatusOK, nil)
	e.pedir(p, "GET", "/v1/archivos/"+tres.ID, nil, http.StatusNotFound, nil)
	borrarDelDisco(tres.ID, r3)

	// Sueltas: la de hace dos días se barre; la recién subida, no (todavía se puede estar guardando).
	vieja, recien := subir(), subir()
	if err := e.migrador.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `UPDATE archivos SET creado_en = now() - interval '2 days' WHERE id = $1`, vieja.ID)
		return err
	}); err != nil {
		t.Fatal(err)
	}
	rv := ruta(vieja.ID)
	if err := e.app.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := archivos.BarrerFotos(ctx, tx)
		return err
	}); err != nil {
		t.Fatal(err)
	}
	e.pedir(p, "GET", "/v1/archivos/"+vieja.ID, nil, http.StatusNotFound, nil)
	e.pedir(p, "GET", "/v1/archivos/"+recien.ID, nil, http.StatusOK, nil)
	e.pedir(p, "GET", "/v1/archivos/"+dos.ID, nil, http.StatusOK, nil)
	borrarDelDisco(vieja.ID, rv)
}
