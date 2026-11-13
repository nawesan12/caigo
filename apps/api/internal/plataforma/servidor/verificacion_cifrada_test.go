package servidor_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io/fs"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/confianza"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
)

// ver pide un archivo privado como p (desde la IP dada) y devuelve la respuesta entera.
func (e *entorno) ver(p persona, id, ip string) *httptest.ResponseRecorder {
	e.t.Helper()
	r := httptest.NewRequest("GET", "/v1/archivos/"+id, nil)
	if p.token != "" {
		r.Header.Set("Authorization", "Bearer "+p.token)
	}
	if ip != "" {
		r.Header.Set("Cf-Connecting-Ip", ip)
	}
	w := httptest.NewRecorder()
	e.h.ServeHTTP(w, r)
	return w
}

func (e *entorno) accesos(archivoID string) int {
	e.t.Helper()
	var n int
	e.valor(`SELECT count(*) FROM accesos_verificacion WHERE archivo_id::text = $1`, &n, archivoID)
	return n
}

// La selfie y el DNI quedan cifrados en el disco; la dueña y el equipo los ven descifrados y sin
// caché, y cada vez que alguien del equipo abre uno ajeno queda anotado (la dueña no).
func TestVerificacionCifradaYAuditada(t *testing.T) {
	e := nuevoEntorno(t)
	mar, otro, equipo := e.persona("Martina"), e.persona("Otro"), e.persona("Nahuel")
	e.alEquipo(equipo)

	var selfie, propia struct{ ID string }
	e.pedir(mar, "POST", "/v1/archivos?tipo=selfie", jpeg, http.StatusCreated, &selfie)
	e.pedir(equipo, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusCreated, &propia)
	var ruta string
	e.valor(`SELECT ruta FROM archivos WHERE id = $1`, &ruta, selfie.ID)
	if !strings.HasPrefix(ruta, "selfie/") || !strings.HasSuffix(ruta, archivos.ExtCifrado) {
		t.Fatalf("ruta: %q", ruta)
	}
	crudo, err := os.ReadFile(filepath.Join(e.archivos, ruta))
	if err != nil || bytes.HasPrefix(crudo, jpeg[:3]) || !bytes.HasPrefix(crudo, []byte("CGV1")) {
		t.Fatalf("en el disco no está cifrada: %v", err)
	}

	// La dueña la ve, descifrada y sin caché, y no se anota.
	w := e.ver(mar, selfie.ID, "203.0.113.1")
	if w.Code != http.StatusOK || !bytes.Equal(w.Body.Bytes(), jpeg) || !strings.Contains(w.Header().Get("Cache-Control"), "no-store") ||
		w.Header().Get("Content-Type") != "image/jpeg" {
		t.Fatalf("la dueña: %d %q %q", w.Code, w.Header().Get("Cache-Control"), w.Header().Get("Content-Type"))
	}
	if n := e.accesos(selfie.ID); n != 0 {
		t.Fatalf("se anotó a la dueña: %d", n)
	}
	// Otra persona no la ve, ni queda nada anotado.
	if w := e.ver(otro, selfie.ID, ""); w.Code != http.StatusNotFound {
		t.Fatalf("otra persona: %d", w.Code)
	}
	if w := e.ver(persona{}, selfie.ID, ""); w.Code != http.StatusNotFound {
		t.Fatalf("sin sesión: %d", w.Code)
	}
	// El equipo la ve y queda anotado: quién, de quién, qué archivo, cuándo y la IP.
	w = e.ver(equipo, selfie.ID, "203.0.113.7")
	if w.Code != http.StatusOK || !bytes.Equal(w.Body.Bytes(), jpeg) || !strings.Contains(w.Header().Get("Cache-Control"), "no-store") {
		t.Fatalf("el equipo: %d", w.Code)
	}
	var quien, de, tipo, ip string
	var cuando time.Time
	e.valor(`SELECT equipo_id::text FROM accesos_verificacion WHERE archivo_id = $1`, &quien, selfie.ID)
	e.valor(`SELECT usuario_id::text FROM accesos_verificacion WHERE archivo_id = $1`, &de, selfie.ID)
	e.valor(`SELECT tipo FROM accesos_verificacion WHERE archivo_id = $1`, &tipo, selfie.ID)
	e.valor(`SELECT host(ip) FROM accesos_verificacion WHERE archivo_id = $1`, &ip, selfie.ID)
	e.valor(`SELECT visto_en FROM accesos_verificacion WHERE archivo_id = $1`, &cuando, selfie.ID)
	if quien != equipo.id || de != mar.id || tipo != "selfie" || ip != "203.0.113.7" || time.Since(cuando) > time.Minute {
		t.Fatalf("acceso: %s %s %s %s %v", quien, de, tipo, ip, cuando)
	}
	e.ver(equipo, selfie.ID, "203.0.113.7")
	if n := e.accesos(selfie.ID); n != 2 {
		t.Fatalf("cada vez que lo abre se anota: %d", n)
	}
	// Alguien del equipo mirando lo suyo no se anota.
	if w := e.ver(equipo, propia.ID, ""); w.Code != http.StatusOK {
		t.Fatalf("lo propio del equipo: %d", w.Code)
	}
	if n := e.accesos(propia.ID); n != 0 {
		t.Fatalf("se anotó lo propio: %d", n)
	}
	// La app no puede tocar el registro (sólo agregar, leer y la limpieza de 2 años).
	if _, err := e.app.Exec(context.Background(), `UPDATE accesos_verificacion SET ip = NULL`); err == nil {
		t.Fatal("la app pudo cambiar el registro de accesos")
	}
	// Las fotos públicas siguen igual: sin cifrar y con caché.
	var foto struct{ ID string }
	e.pedir(mar, "POST", "/v1/archivos?tipo=foto", jpeg, http.StatusCreated, &foto)
	e.valor(`SELECT ruta FROM archivos WHERE id = $1`, &ruta, foto.ID)
	if archivos.Cifrado(ruta) {
		t.Fatalf("la foto de perfil se cifró: %q", ruta)
	}
	if w := e.ver(persona{}, foto.ID, ""); w.Code != http.StatusOK || !strings.Contains(w.Header().Get("Cache-Control"), "immutable") {
		t.Fatalf("foto pública: %d %q", w.Code, w.Header().Get("Cache-Control"))
	}
}

// Sin CAIGO_CLAVE_VERIFICACION la API anda, pero no recibe ni muestra selfies ni DNI (503 claro).
func TestSinClaveDeVerificacion(t *testing.T) {
	con := nuevoEntorno(t)
	mar := con.persona("Martina")
	var selfie struct{ ID string }
	con.pedir(mar, "POST", "/v1/archivos?tipo=selfie", jpeg, http.StatusCreated, &selfie)

	sin := nuevoEntornoAjustado(t, func(d *servidor.Dependencias) {
		d.ClaveVerificacion = nil
		d.Archivos = con.archivos
	})
	ana, equipo := sin.persona("Ana"), sin.persona("Equipo")
	sin.alEquipo(equipo)
	var pr struct{ Code, Detail string }
	sin.pedir(ana, "POST", "/v1/archivos?tipo=dni", jpeg, http.StatusServiceUnavailable, &pr)
	if pr.Code != "sin_clave_verificacion" || pr.Detail == "" {
		t.Fatalf("subir sin clave: %+v", pr)
	}
	// Ver la selfie (cifrada) de Martina: 503, y no se anota un acceso que no pasó.
	w := sin.ver(equipo, selfie.ID, "")
	if w.Code != http.StatusServiceUnavailable {
		t.Fatalf("ver sin clave: %d", w.Code)
	}
	_ = json.Unmarshal(w.Body.Bytes(), &pr)
	if pr.Code != "sin_clave_verificacion" || sin.accesos(selfie.ID) != 0 {
		t.Fatalf("ver sin clave: %+v", pr)
	}
	// Lo demás anda: fotos públicas y el perfil.
	sin.pedir(ana, "POST", "/v1/archivos?tipo=foto", jpeg, http.StatusCreated, nil)
	sin.pedir(ana, "GET", "/v1/yo", nil, http.StatusOK, nil)
}

// caigoctl cifrar-verificaciones: --probar no escribe; la corrida real cifra, cambia la ruta y borra el
// original sólo después de verificar; repetirla no hace nada; y la purga borra el formato nuevo.
func TestCifrarVerificacionesIdempotente(t *testing.T) {
	e := nuevoEntorno(t)
	ctx := context.Background()
	lu, equipo := e.persona("Lu"), e.persona("Equipo")
	e.alEquipo(equipo)
	viejo := archivos.Disco{Raiz: e.archivos} // como se guardaba antes de cifrar
	ids := map[string]string{}
	for _, tipo := range []string{"selfie", "dni"} {
		ruta, contenido, n, err := viejo.Guardar(tipo, bytes.NewReader(jpeg))
		if err != nil {
			t.Fatal(err)
		}
		var id string
		e.valor(`INSERT INTO archivos (usuario_id, tipo, ruta, contenido, bytes) VALUES ($1, $2, $3, $4, $5) RETURNING id::text`,
			&id, lu.id, tipo, ruta, contenido, n)
		ids[id] = ruta
	}
	// Uno que está en la base y no en el disco.
	var falta string
	e.valor(`INSERT INTO archivos (usuario_id, tipo, ruta, contenido, bytes) VALUES ($1, 'dni', $2, 'image/jpeg', 10) RETURNING id::text`,
		&falta, lu.id, "dni/2026-01/no-esta-"+lu.id+".jpg")
	// Una foto de perfil vieja: no se toca.
	fotoRuta, _, _, _ := viejo.Guardar("foto", bytes.NewReader(jpeg))
	disco := archivos.Disco{Raiz: e.archivos, Clave: e.clave}

	inf, err := confianza.CifrarVerificaciones(ctx, e.migrador, disco, true)
	if err != nil || inf.Cifrados != 2 || !slices.Contains(inf.Faltan, falta) {
		t.Fatalf("probar: %+v %v", inf, err)
	}
	for id, ruta := range ids {
		var ahora string
		e.valor(`SELECT ruta FROM archivos WHERE id = $1`, &ahora, id)
		if ahora != ruta {
			t.Fatalf("probar cambió la base: %q", ahora)
		}
		if _, err := os.Stat(filepath.Join(e.archivos, ruta+archivos.ExtCifrado)); !errors.Is(err, fs.ErrNotExist) {
			t.Fatal("probar escribió en el disco")
		}
	}

	inf, err = confianza.CifrarVerificaciones(ctx, e.migrador, disco, false)
	if err != nil || inf.Cifrados != 2 || inf.Restos != 0 || !slices.Contains(inf.Faltan, falta) {
		t.Fatalf("cifrar: %+v %v", inf, err)
	}
	for id, ruta := range ids {
		var ahora string
		e.valor(`SELECT ruta FROM archivos WHERE id = $1`, &ahora, id)
		if ahora != ruta+archivos.ExtCifrado {
			t.Fatalf("ruta nueva: %q", ahora)
		}
		if _, err := os.Stat(filepath.Join(e.archivos, ruta)); !errors.Is(err, fs.ErrNotExist) {
			t.Fatal("quedó el original sin cifrar")
		}
		if w := e.ver(equipo, id, ""); w.Code != http.StatusOK || !bytes.Equal(w.Body.Bytes(), jpeg) {
			t.Fatalf("después de cifrar: %d", w.Code)
		}
	}
	if _, err := os.Stat(filepath.Join(e.archivos, fotoRuta)); err != nil {
		t.Fatal("tocó la foto de perfil")
	}

	// Otra vez: nada que hacer.
	inf, err = confianza.CifrarVerificaciones(ctx, e.migrador, disco, false)
	if err != nil || inf.Cifrados != 0 || inf.Restos != 0 {
		t.Fatalf("segunda corrida: %+v %v", inf, err)
	}
	// Un corte después de cambiar la base y antes de borrar dejaría el original al lado: lo borra.
	var unID, unaRuta string
	for unID, unaRuta = range ids {
		break
	}
	if err := os.WriteFile(filepath.Join(e.archivos, unaRuta), jpeg, 0o640); err != nil {
		t.Fatal(err)
	}
	inf, err = confianza.CifrarVerificaciones(ctx, e.migrador, disco, false)
	if err != nil || inf.Cifrados != 0 || inf.Restos != 1 {
		t.Fatalf("con un original suelto: %+v %v", inf, err)
	}
	if _, err := os.Stat(filepath.Join(e.archivos, unaRuta)); !errors.Is(err, fs.ErrNotExist) {
		t.Fatal("no borró el original suelto")
	}
	// Sin clave, el comando no arranca.
	if _, err := confianza.CifrarVerificaciones(ctx, e.migrador, archivos.Disco{Raiz: e.archivos}, false); !errors.Is(err, archivos.ErrSinClave) {
		t.Fatalf("sin clave: %v", err)
	}

	// La purga (sin la clave: el worker no la necesita) borra los cifrados del disco.
	e.pedir(lu, "DELETE", "/v1/cuenta", nil, http.StatusNoContent, nil)
	e.sistema(`UPDATE usuarios SET baja_en = now() - interval '31 days' WHERE id = $1`, lu.id)
	if _, err := confianza.Purgar(ctx, e.app, archivos.Disco{Raiz: e.archivos}, e.vetados, time.Now().AddDate(0, 0, -confianza.DiasParaPurgar)); err != nil {
		t.Fatal(err)
	}
	for _, ruta := range ids {
		if _, err := os.Stat(filepath.Join(e.archivos, ruta+archivos.ExtCifrado)); !errors.Is(err, fs.ErrNotExist) {
			t.Fatalf("la purga dejó %s", ruta)
		}
	}
	// El registro de quién miró queda (sin el archivo).
	if n := e.accesos(unID); n != 1 {
		t.Fatalf("accesos después de la purga: %d", n)
	}
}
