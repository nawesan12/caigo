package servidor_test

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/confianza"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/correo"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
)

// Con correo, el código del teléfono va al mail verificado de la cuenta y no vuelve en la respuesta.
func TestCodigoDelTelefonoPorMail(t *testing.T) {
	buzon := &correo.Memoria{}
	e := nuevoEntornoAjustado(t, func(d *servidor.Dependencias) { d.Correo = buzon })
	mar := e.persona("Martina")
	var envio struct{ Via, Destino, CodigoDePrueba string }
	e.pedir(mar, "POST", "/v1/verificacion/telefono", map[string]string{"telefono": "223 555 7788"}, http.StatusOK, &envio)
	if envio.Via != "mail" || envio.Destino != "M•••@caigo.test" || envio.CodigoDePrueba != "" {
		t.Fatalf("respuesta: %+v", envio)
	}
	mails := buzon.Para("Martina@caigo.test")
	if len(mails) != 1 {
		t.Fatalf("mails: %d", buzon.Cantidad())
	}
	codigo := regexp.MustCompile(`[0-9]{6}`).FindString(mails[0].Asunto)
	if codigo == "" || !strings.Contains(mails[0].HTML, codigo) || !strings.Contains(mails[0].Texto, "Vence en 10 minutos.") {
		t.Fatalf("mail: %+v", mails[0])
	}
	e.pedir(mar, "POST", "/v1/verificacion/telefono/confirmar", map[string]string{"codigo": codigo}, http.StatusNoContent, nil)

	// Sin mail verificado: problem+json claro y no se manda nada.
	sin := e.persona("Sinmail")
	for sub, u := range e.clerk.Usuarios {
		if u.Nombre == "Sinmail" {
			u.Verificado = false
			e.clerk.Usuarios[sub] = u
		}
	}
	e.pedir(sin, "POST", "/v1/verificacion/telefono", map[string]string{"telefono": "223 555 7789"}, http.StatusUnprocessableEntity, nil)
	if buzon.Cantidad() != 1 {
		t.Fatalf("mandó %d mails", buzon.Cantidad())
	}

	// Si el mail no sale, se puede volver a pedir enseguida.
	buzon.Fallar = errors.New("smtp caído")
	otra := e.persona("Otra")
	e.pedir(otra, "POST", "/v1/verificacion/telefono", map[string]string{"telefono": "223 555 7790"}, http.StatusInternalServerError, nil)
	buzon.Fallar = nil
	e.pedir(otra, "POST", "/v1/verificacion/telefono", map[string]string{"telefono": "223 555 7790"}, http.StatusOK, nil)
}

// Una "foto" mínima: alcanza con la firma de JPEG.
var jpeg = append([]byte{0xFF, 0xD8, 0xFF, 0xE0}, make([]byte, 64)...)

func (e *entorno) sistema(q string, args ...any) {
	e.t.Helper()
	if err := e.app.EnSistema(context.Background(), func(tx pgx.Tx) error {
		_, err := tx.Exec(context.Background(), q, args...)
		return err
	}); err != nil {
		e.t.Fatal(err)
	}
}

func (e *entorno) alEquipo(p persona) {
	e.t.Helper()
	if _, err := e.migrador.Exec(context.Background(), `UPDATE usuarios SET equipo = true WHERE id = $1`, p.id); err != nil {
		e.t.Fatal(err)
	}
}

func TestVerificacion(t *testing.T) {
	e := nuevoEntorno(t)
	mar, otro, equipo := e.persona("Martina"), e.persona("Otro"), e.persona("Nahuel")
	e.alEquipo(equipo)

	// Teléfono: con SMS simulado el código vuelve en la respuesta.
	var cod struct{ CodigoDePrueba string }
	e.pedir(mar, "POST", "/v1/verificacion/telefono", map[string]string{"telefono": "223 555 1234"}, http.StatusOK, &cod)
	if len(cod.CodigoDePrueba) != 6 {
		t.Fatalf("código: %q", cod.CodigoDePrueba)
	}
	e.pedir(mar, "POST", "/v1/verificacion/telefono/confirmar", map[string]string{"codigo": "000000x"}, http.StatusBadRequest, nil)
	e.pedir(mar, "POST", "/v1/verificacion/telefono/confirmar", map[string]string{"codigo": cod.CodigoDePrueba}, http.StatusNoContent, nil)

	// Selfie: un archivo que no es foto se rechaza; la foto sí.
	e.pedir(mar, "POST", "/v1/archivos?tipo=selfie", []byte("no soy una foto"), http.StatusBadRequest, nil)
	var selfie struct{ ID, URL string }
	e.pedir(mar, "POST", "/v1/archivos?tipo=selfie", jpeg, http.StatusCreated, &selfie)
	// Otro no puede usar la selfie de Martina, ni verla.
	e.pedir(otro, "POST", "/v1/verificacion/selfie", map[string]string{"archivoId": selfie.ID}, http.StatusBadRequest, nil)
	e.pedir(otro, "GET", "/v1/archivos/"+selfie.ID, nil, http.StatusNotFound, nil)
	e.pedir(mar, "GET", "/v1/archivos/"+selfie.ID, nil, http.StatusOK, nil)
	e.pedir(mar, "POST", "/v1/verificacion/selfie", map[string]string{"archivoId": selfie.ID}, http.StatusNoContent, nil)

	// DNI: frente, dorso y selfie + lo leído del PDF417 (el "sexo" lo mandan las versiones viejas: se descarta).
	var frente, dorso, s2 struct{ ID string }
	e.pedir(mar, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusCreated, &frente)
	e.pedir(mar, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusCreated, &dorso)
	e.pedir(mar, "POST", "/v1/archivos?tipo=selfie", jpeg, http.StatusCreated, &s2)
	dni := map[string]string{"frenteId": frente.ID, "dorsoId": dorso.ID, "selfieId": s2.ID, "numero": fmt.Sprint(30000000 + time.Now().UnixNano()%60000000),
		"apellido": "GOMEZ", "nombres": "MARTINA", "sexo": "F", "nacimiento": "1999-03-02"}
	e.pedir(mar, "POST", "/v1/verificacion/dni", dni, http.StatusNoContent, nil)
	e.pedir(mar, "POST", "/v1/verificacion/dni", dni, http.StatusConflict, nil)

	// Sólo el equipo ve la cola.
	e.pedir(mar, "GET", "/v1/equipo/dni", nil, http.StatusForbidden, nil)
	var cola struct {
		Pendientes []struct {
			ID, UsuarioID, Numero string
		}
	}
	e.pedir(equipo, "GET", "/v1/equipo/dni", nil, http.StatusOK, &cola)
	var id string
	for _, p := range cola.Pendientes {
		if p.UsuarioID == mar.id {
			id = p.ID
		}
	}
	if id == "" {
		t.Fatalf("no está en la cola: %+v", cola)
	}
	// El equipo ve las fotos del DNI.
	e.pedir(equipo, "GET", "/v1/archivos/"+frente.ID, nil, http.StatusOK, nil)
	e.pedir(equipo, "POST", "/v1/equipo/dni/"+id, map[string]any{"aprobar": true}, http.StatusNoContent, nil)
	// Resuelta: las fotos (frente, dorso y selfie) ya no están ni en la base ni para el equipo, y su
	// borrado del disco quedó encolado para el worker.
	for _, f := range []string{frente.ID, dorso.ID, s2.ID} {
		e.pedir(equipo, "GET", "/v1/archivos/"+f, nil, http.StatusNotFound, nil)
		e.pedir(mar, "GET", "/v1/archivos/"+f, nil, http.StatusNotFound, nil)
	}
	var refs int
	e.valor(`SELECT count(*) FROM verificaciones_dni WHERE id::text = $1 AND frente_id IS NULL AND dorso_id IS NULL AND selfie_id IS NULL`, &refs, id)
	var encolados int
	e.valor(`SELECT count(*) FROM outbox WHERE tipo = 'archivo.borrar' AND datos->>'ruta' LIKE 'dni/%' AND creado_en > now() - interval '1 minute'`, &encolados)
	if refs != 1 || encolados < 2 {
		t.Fatalf("fotos de la verificación resuelta: refs %d, encolados %d", refs, encolados)
	}

	var estado struct {
		Telefono, DNI string
		Selfie        bool
	}
	e.pedir(mar, "GET", "/v1/verificacion", nil, http.StatusOK, &estado)
	if estado.Telefono != "+5492235551234" || !estado.Selfie || estado.DNI != "aprobada" {
		t.Fatalf("estado: %+v", estado)
	}
	var yo struct {
		Verificada bool
		Edad       int
	}
	e.pedir(mar, "GET", "/v1/yo", nil, http.StatusOK, &yo)
	if !yo.Verificada || yo.Edad < 26 {
		t.Fatalf("perfil: %+v", yo)
	}
}

func TestCheckinRepetimosYEnVivo(t *testing.T) {
	e := nuevoEntorno(t)
	nico, lu, _, planID := planConLu(t, e)

	// Muy temprano para el check-in: el plan es en una hora, se abre media hora antes.
	lat, lng := -37.99, -57.55
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/checkin", map[string]any{"metodo": "geo", "lat": lat, "lng": lng}, http.StatusConflict, nil)
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '10 minutes' WHERE id = $1`, planID)
	// Lejos (2 km): no.
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/checkin", map[string]any{"metodo": "geo", "lat": lat + 0.02, "lng": lng}, http.StatusConflict, nil)
	// Con el QR del organizador sí.
	var qr struct{ Codigo string }
	// Lu recibe su QR personal (para la puerta de un lugar): no sirve como el del organizador.
	var propio struct{ Codigo string }
	e.pedir(lu, "GET", "/v1/planes/"+planID+"/qr", nil, http.StatusOK, &propio)
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/checkin", map[string]any{"metodo": "qr", "codigo": propio.Codigo}, http.StatusConflict, nil)
	e.pedir(nico, "GET", "/v1/planes/"+planID+"/qr", nil, http.StatusOK, &qr)
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/checkin", map[string]any{"metodo": "qr", "codigo": qr.Codigo + "x"}, http.StatusConflict, nil)
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/checkin", map[string]any{"metodo": "qr", "codigo": qr.Codigo}, http.StatusNoContent, nil)
	// Nico llega por ubicación.
	e.pedir(nico, "POST", "/v1/planes/"+planID+"/checkin", map[string]any{"metodo": "geo", "lat": lat, "lng": lng}, http.StatusNoContent, nil)

	// En vivo: Lu comparte el plan con su contacto de confianza.
	var link struct{ Link string }
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/en-vivo", nil, http.StatusOK, &link)
	tok := link.Link[strings.LastIndex(link.Link, "/")+1:]
	var vivo struct {
		Quien string
		Llego bool
		Con   []string
	}
	e.pedir(persona{}, "GET", "/v1/publico/vivo/"+tok, nil, http.StatusOK, &vivo)
	if vivo.Quien != "Lu" || !vivo.Llego || len(vivo.Con) != 1 || vivo.Con[0] != "Nico" {
		t.Fatalf("en vivo: %+v", vivo)
	}

	// Terminó: ¿Repetimos? Nico repite y Lu quiere volver a ver a Nico.
	e.sistema(`UPDATE planes SET empieza_en = now() - interval '3 hours' WHERE id = $1`, planID)
	var rep struct{ Nuevo *plan }
	e.pedir(nico, "POST", "/v1/planes/"+planID+"/repetimos", map[string]any{"repetir": true, "volveria": []map[string]any{{"usuarioId": lu.id, "si": true}}}, http.StatusOK, &rep)
	if rep.Nuevo == nil || rep.Nuevo.Somos != 3 || rep.Nuevo.ID == planID {
		t.Fatalf("repetir: %+v", rep.Nuevo)
	}
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/repetimos", map[string]any{"volveria": []map[string]any{{"usuarioId": nico.id, "si": true}}}, http.StatusOK, nil)
	var gente struct{ Gente []struct{ ID string } }
	e.pedir(lu, "GET", "/v1/gente", nil, http.StatusOK, &gente)
	if len(gente.Gente) != 1 || gente.Gente[0].ID != nico.id {
		t.Fatalf("mi gente: %+v", gente)
	}

	// La asistencia cuenta: Lu fue a 1 de 1.
	var perfil struct{ Asistencia struct{ Fue, De int } }
	e.pedir(nico, "GET", "/v1/personas/"+lu.id, nil, http.StatusOK, &perfil)
	if perfil.Asistencia.Fue != 1 || perfil.Asistencia.De != 1 {
		t.Fatalf("asistencia: %+v", perfil.Asistencia)
	}
}

func TestModeracion(t *testing.T) {
	e := nuevoEntorno(t)
	_, lu, fede, planID := planConLu(t, e)
	equipo := e.persona("Equipo")
	e.alEquipo(equipo)

	e.pedir(lu, "POST", "/v1/reportes", map[string]string{"usuarioId": fede.id, "planId": planID, "motivo": "acoso", "detalle": "Me escribió cosas feas"}, http.StatusNoContent, nil)
	var rs struct {
		Reportes []struct{ ID, SobreNombre string }
	}
	e.pedir(equipo, "GET", "/v1/equipo/reportes", nil, http.StatusOK, &rs)
	var id string
	for _, r := range rs.Reportes {
		if r.SobreNombre == "Fede" {
			id = r.ID
		}
	}
	if id == "" {
		t.Fatal("el reporte no aparece")
	}
	e.pedir(equipo, "POST", "/v1/equipo/reportes/"+id, map[string]any{"suspender": true, "resolucion": "Acoso confirmado"}, http.StatusNoContent, nil)
	// Fede queda afuera.
	e.pedir(fede, "GET", "/v1/yo", nil, http.StatusForbidden, nil)
}

func TestBajaDeCuenta(t *testing.T) {
	e, mp, ls, duena, lugarID := lugarConMP(t)
	nico, lu, fede, planID := planConLu(t, e)
	var foto struct{ ID, URL string }
	e.pedir(lu, "POST", "/v1/archivos?tipo=selfie", jpeg, http.StatusCreated, &foto)
	var s struct{ Token string }
	e.pedir(lu, "POST", "/v1/sesion?cliente=android", nil, http.StatusOK, &s)
	propia := persona{token: s.Token, id: lu.id}
	// Lu organiza otro plan y se da de baja.
	var suyo plan
	e.pedir(lu, "POST", "/v1/planes", map[string]any{"categoria": "juego", "empiezaEn": time.Now().Add(2 * time.Hour),
		"ciudad": "Mar del Plata", "zona": "Centro", "lat": -38.0, "lng": -57.55, "somos": 2, "faltan": 2, "ingreso": "libre"},
		http.StatusCreated, &suyo)
	e.pedir(fede, "POST", "/v1/planes/"+suyo.ID+"/sumarme", map[string]any{}, http.StatusOK, nil)
	// Y tiene una entrada paga para un plan de un lugar (precio $8.000 + $600 de cargo).
	var lp struct{ ID string }
	e.pedir(duena, "POST", "/v1/lugares/"+lugarID+"/planes", map[string]any{"titulo": "Noche de jazz", "categoria": "musica",
		"empiezaEn": time.Now().Add(3 * time.Hour), "cupo": 20, "cobro": "entrada", "precioCentavos": 800000, "cancelacion": "strict"},
		http.StatusCreated, &lp)
	e.pedir(lu, "POST", "/v1/planes/"+lp.ID+"/entradas", map[string]int{"cantidad": 1}, http.StatusOK, nil)
	if err := ls.ConciliarPago(context.Background(), mp.usuario, 99); err != nil {
		t.Fatal(err)
	}
	var entrada string
	e.valor(`SELECT id::text FROM entradas WHERE plan_id = $1 AND usuario_id = $2 AND estado = 'pagada'`, &entrada, lp.ID, lu.id)
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/mensajes", map[string]string{"texto": "¿Llevo hielo?"}, http.StatusCreated, nil)
	e.pedir(propia, "DELETE", "/v1/yo", nil, http.StatusNoContent, nil)
	// Su plan se cancela como si lo cancelara: aviso a Fede con el motivo.
	cancelado := e.eventos("plan.cancelado", suyo.ID)
	if len(cancelado) != 1 || !strings.Contains(string(cancelado[0].Datos), fede.id) ||
		!strings.Contains(string(cancelado[0].Datos), confianza.MotivoBaja) {
		t.Fatalf("plan.cancelado de la baja: %+v", cancelado)
	}
	var motivo string
	e.valor(`SELECT cancelado_motivo FROM planes WHERE id = $1`, &motivo, suyo.ID)
	if motivo != confianza.MotivoBaja {
		t.Fatalf("motivo: %q", motivo)
	}
	// Su entrada se le devuelve: el precio, sin el cargo (lo hace el worker; dos veces no duplica).
	evs := e.eventos("entrada.reembolsar", entrada)
	if len(evs) != 1 {
		t.Fatalf("entrada.reembolsar de la baja: %d", len(evs))
	}
	for range 2 {
		if err := ls.Manejadores()["entrada.reembolsar"](context.Background(), evs[0]); err != nil {
			t.Fatal(err)
		}
	}
	var estado string
	var devuelto int64
	e.valor(`SELECT estado FROM entradas WHERE id = $1`, &estado, entrada)
	e.valor(`SELECT reembolso_centavos FROM entradas WHERE id = $1`, &devuelto, entrada)
	if estado != "reembolsada" || devuelto != 800000 || len(mp.reembolsos) != 1 || mp.reembolsos[0] != `{"transactions":[{"amount":"8000.00","id":"tx-1"}]}` {
		// Pagada por el aviso payment (sin transacción guardada): la devolución la busca en la orden.
		t.Fatalf("reembolso de la baja: %q %d %q", estado, devuelto, mp.reembolsos)
	}
	// Las sesiones mueren y el ingreso (con el token de Clerk) responde cuenta_borrada.
	e.pedir(propia, "GET", "/v1/yo", nil, http.StatusUnauthorized, nil)
	var pr struct{ Code, Detail string }
	e.pedir(lu, "POST", "/v1/sesion?cliente=android", nil, http.StatusConflict, &pr)
	if pr.Code != "cuenta_borrada" || !strings.Contains(pr.Detail, "hola@caigo.com.ar") || !strings.Contains(pr.Detail, "30 días") {
		t.Fatalf("ingreso de una cuenta borrada: %+v", pr)
	}
	e.pedir(lu, "GET", "/v1/yo", nil, http.StatusConflict, nil)
	var n int
	e.valor(`SELECT count(*) FROM sesiones WHERE usuario_id = $1`, &n, lu.id)
	if n != 0 {
		t.Fatalf("sesiones después de la baja: %d", n)
	}
	// Su plan se cancela y deja el lugar en el de Nico.
	var p struct {
		Somos     int
		Cancelado bool
	}
	e.pedir(nico, "GET", "/v1/planes/"+suyo.ID, nil, http.StatusOK, &p)
	if !p.Cancelado {
		t.Fatal("el plan de Lu sigue activo")
	}
	e.pedir(nico, "GET", "/v1/planes/"+planID, nil, http.StatusOK, &p)
	if p.Somos != 3 {
		t.Fatalf("somos después de la baja: %d", p.Somos)
	}
	// Antes de los 30 días, la purga no la toca: sus datos siguen (por si la recupera).
	ctx := context.Background()
	if _, err := confianza.Purgar(ctx, e.app, archivos.Disco{Raiz: e.archivos}, e.vetados, time.Now().AddDate(0, 0, -confianza.DiasParaPurgar)); err != nil {
		t.Fatal(err)
	}
	var nombre string
	e.valor(`SELECT nombre FROM usuarios WHERE id = $1`, &nombre, lu.id)
	if nombre != "Lu" {
		t.Fatalf("purgada antes de tiempo: %q", nombre)
	}
	// A los 30 días: sin nombre, fotos, selfie ni teléfono; su mensaje queda firmado por «Cuenta borrada».
	if _, err := confianza.Purgar(ctx, e.app, archivos.Disco{Raiz: e.archivos}, e.vetados, time.Now().Add(time.Minute)); err != nil {
		t.Fatal(err)
	}
	var selfie, purgada bool
	e.valor(`SELECT nombre FROM usuarios WHERE id = $1`, &nombre, lu.id)
	e.valor(`SELECT selfie_en IS NOT NULL FROM usuarios WHERE id = $1`, &selfie, lu.id)
	e.valor(`SELECT purgada_en IS NOT NULL FROM usuarios WHERE id = $1`, &purgada, lu.id)
	if nombre != "Cuenta borrada" || selfie || !purgada {
		t.Fatalf("después de la purga: %q selfie=%v purgada=%v", nombre, selfie, purgada)
	}
	e.valor(`SELECT count(*) FROM archivos WHERE usuario_id = $1`, &n, lu.id)
	if n != 0 {
		t.Fatalf("archivos después de la purga: %d", n)
	}
	e.pedir(nico, "GET", "/v1/archivos/"+foto.ID, nil, http.StatusNotFound, nil)
	var ms struct {
		Mensajes []struct {
			Texto string
			Autor *struct{ Nombre string }
		}
	}
	e.pedir(nico, "GET", "/v1/planes/"+planID+"/mensajes", nil, http.StatusOK, &ms)
	firmado := false
	for _, m := range ms.Mensajes {
		if m.Texto == "¿Llevo hielo?" && m.Autor != nil {
			firmado = m.Autor.Nombre == "Cuenta borrada"
		}
	}
	if !firmado {
		t.Fatalf("mensajes después de la purga: %+v", ms.Mensajes)
	}
	// Purgada, si vuelve a entrar con la misma cuenta de Clerk arranca de cero.
	var yo struct{ ID, Nombre string }
	e.pedir(lu, "GET", "/v1/yo", nil, http.StatusOK, &yo)
	if yo.ID == lu.id || yo.Nombre != "Lu" {
		t.Fatalf("vuelta después de la baja: %+v", yo)
	}
}

// En los 30 días, la cuenta se recupera a mano (caigoctl recuperar-cuenta) por mail o id de Clerk.
func TestRecuperarCuentaBorrada(t *testing.T) {
	e := nuevoEntorno(t)
	ana := e.persona("Ana")
	// GET /v1/yo trae el mail de la cuenta: con él se confirma la baja.
	var y struct{ Email string }
	e.pedir(ana, "GET", "/v1/yo", nil, http.StatusOK, &y)
	if y.Email != "Ana@caigo.test" {
		t.Fatalf("mail en /v1/yo: %q", y.Email)
	}
	// Si viene el mail escrito, tiene que ser el de la cuenta.
	e.pedir(ana, "DELETE", "/v1/cuenta", map[string]string{"confirmarEmail": "otra@caigo.test"}, http.StatusUnprocessableEntity, nil)
	e.pedir(ana, "GET", "/v1/yo", nil, http.StatusOK, nil)
	e.pedir(ana, "DELETE", "/v1/cuenta", map[string]string{"confirmarEmail": " ana@CAIGO.test "}, http.StatusNoContent, nil)
	e.pedir(ana, "GET", "/v1/yo", nil, http.StatusConflict, nil)
	ctx := context.Background()
	if id, err := confianza.Recuperar(ctx, e.migrador, "nadie@caigo.test"); err != nil || id != "" {
		t.Fatalf("recuperó a otra: %q %v", id, err)
	}
	id, err := confianza.Recuperar(ctx, e.migrador, "ANA@caigo.test")
	if err != nil || id != ana.id {
		t.Fatalf("recuperar por mail: %q %v", id, err)
	}
	var yo struct{ ID string }
	e.pedir(ana, "GET", "/v1/yo", nil, http.StatusOK, &yo)
	if yo.ID != ana.id {
		t.Fatalf("después de recuperar: %+v", yo)
	}
	if id, _ := confianza.Recuperar(ctx, e.migrador, "Ana@caigo.test"); id != "" {
		t.Fatal("recuperó dos veces")
	}
}

func TestBotonDeArrepentimiento(t *testing.T) {
	e := nuevoEntorno(t)
	nadie := persona{}
	e.pedir(nadie, "POST", "/v1/publico/arrepentimiento", map[string]string{"nombre": "Ana", "email": "no-es-mail", "compra": "Plus"}, http.StatusBadRequest, nil)
	var r struct{ Codigo string }
	e.pedir(nadie, "POST", "/v1/publico/arrepentimiento", map[string]string{"nombre": "Ana", "email": "ana@caigo.test",
		"compra": "Caigo Plus mes, el 30/10, operación 123"}, http.StatusCreated, &r)
	if !strings.HasPrefix(r.Codigo, "ARR-") || len(r.Codigo) != 10 {
		t.Fatalf("código: %q", r.Codigo)
	}
	// Sólo el equipo lo ve y lo atiende.
	cualquiera, equipo := e.persona("Cualquiera"), e.persona("Moderación")
	e.alEquipo(equipo)
	e.pedir(cualquiera, "GET", "/v1/equipo/arrepentimientos", nil, http.StatusForbidden, nil)
	var l struct {
		Arrepentimientos []struct{ ID, Codigo, Email string }
	}
	e.pedir(equipo, "GET", "/v1/equipo/arrepentimientos", nil, http.StatusOK, &l)
	var id string
	for _, a := range l.Arrepentimientos {
		if a.Codigo == r.Codigo {
			id = a.ID
		}
	}
	if id == "" {
		t.Fatalf("no aparece el pedido: %+v", l)
	}
	e.pedir(equipo, "POST", "/v1/equipo/arrepentimientos/"+id, map[string]string{"nota": "Reembolsado en MP"}, http.StatusNoContent, nil)
	e.pedir(equipo, "POST", "/v1/equipo/arrepentimientos/"+id, map[string]string{"nota": "otra vez"}, http.StatusNotFound, nil)
}

func TestContactoDeConfianzaEnYo(t *testing.T) {
	e := nuevoEntorno(t)
	p := e.persona("Contacto")
	e.pedir(p, "PATCH", "/v1/yo", map[string]any{"contactoConfianza": "+54 9 223 555 0000"}, http.StatusOK, nil)
	var yo struct{ ContactoConfianza string }
	e.pedir(p, "GET", "/v1/yo", nil, http.StatusOK, &yo)
	if yo.ContactoConfianza != "+54 9 223 555 0000" {
		t.Fatalf("contacto: %q", yo.ContactoConfianza)
	}
}

// La purga del worker (confianza.PurgarFotosDNI) levanta lo que quedó de verificaciones resueltas, las
// fotos del DNI sueltas y los archivos sin fila en el disco. Otra vuelta no encuentra nada.
func TestPurgaFotosDNI(t *testing.T) {
	e := nuevoEntorno(t)
	ana := e.persona("Ana")
	var frente, dorso, selfie, suelta struct{ ID string }
	e.pedir(ana, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusCreated, &frente)
	e.pedir(ana, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusCreated, &dorso)
	e.pedir(ana, "POST", "/v1/archivos?tipo=selfie", jpeg, http.StatusCreated, &selfie)
	e.pedir(ana, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusCreated, &suelta)
	numero := fmt.Sprint(30000000 + time.Now().UnixNano()%60000000)
	e.pedir(ana, "POST", "/v1/verificacion/dni", map[string]string{"frenteId": frente.ID, "dorsoId": dorso.ID, "selfieId": selfie.ID,
		"numero": numero, "apellido": "LOPEZ", "nombres": "ANA", "nacimiento": "1995-01-02"}, http.StatusNoContent, nil)
	// Una resuelta de antes de este cambio (con las fotos todavía) y una foto del DNI que nunca llegó a
	// verificación, de hace dos días.
	e.sistema(`UPDATE verificaciones_dni SET estado = 'rechazada', motivo = 'borrosa', revisada_en = now() WHERE usuario_id = $1`, ana.id)
	if err := e.migrador.EnSistema(context.Background(), func(tx pgx.Tx) error {
		_, err := tx.Exec(context.Background(), `UPDATE archivos SET creado_en = now() - interval '2 days' WHERE id = $1`, suelta.ID)
		return err
	}); err != nil {
		t.Fatal(err)
	}
	// Un archivo en dni/ sin fila en la base, viejo.
	resto := filepath.Join(e.archivos, "dni", "2020-01", "resto.jpg.cif")
	if err := os.MkdirAll(filepath.Dir(resto), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(resto, []byte("x"), 0o640); err != nil {
		t.Fatal(err)
	}
	viejo := time.Now().Add(-48 * time.Hour)
	if err := os.Chtimes(resto, viejo, viejo); err != nil {
		t.Fatal(err)
	}

	disco := archivos.Disco{Raiz: e.archivos}
	inf, err := confianza.PurgarFotosDNI(context.Background(), e.app, disco)
	if err != nil {
		t.Fatal(err)
	}
	if inf.Resueltas < 3 || inf.Sueltas < 1 || inf.Disco < 1 {
		t.Fatalf("purga: %+v", inf)
	}
	var quedan int
	e.valor(`SELECT count(*) FROM archivos WHERE id::text = ANY ($1)`, &quedan, []string{frente.ID, dorso.ID, selfie.ID, suelta.ID})
	if quedan != 0 {
		t.Fatalf("quedaron %d archivos", quedan)
	}
	if _, err := os.Stat(resto); !os.IsNotExist(err) {
		t.Fatalf("el resto sigue en el disco: %v", err)
	}
	// Los archivos con fila no se tocan desde la purga del disco (los borra el evento).
	var otra struct{ ID string }
	e.pedir(ana, "POST", "/v1/archivos?tipo=selfie", jpeg, http.StatusCreated, &otra)
	var ruta string
	e.valor(`SELECT ruta FROM archivos WHERE id = $1`, &ruta, otra.ID)
	abs := filepath.Join(e.archivos, ruta)
	if err := os.Chtimes(abs, viejo, viejo); err != nil {
		t.Fatal(err)
	}
	// Idempotente: otra vuelta no encuentra nada de esto.
	inf, err = confianza.PurgarFotosDNI(context.Background(), e.app, disco)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(abs); err != nil {
		t.Fatalf("borró una selfie con fila: %v", err)
	}
	var refs int
	e.valor(`SELECT count(*) FROM verificaciones_dni WHERE usuario_id = $1 AND frente_id IS NULL`, &refs, ana.id)
	if refs != 1 {
		t.Fatalf("referencias: %d", refs)
	}
}
