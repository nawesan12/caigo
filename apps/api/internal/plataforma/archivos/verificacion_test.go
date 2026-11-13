package archivos_test

import (
	"bytes"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"io/fs"
	"os"
	"path/filepath"
	"testing"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
)

// Una "foto" mínima: alcanza con la firma de JPEG.
var jpeg = append([]byte{0xFF, 0xD8, 0xFF, 0xE0}, []byte("una selfie de prueba con algo de contenido")...)

func nuevaClave(t *testing.T) *archivos.Clave {
	t.Helper()
	k := make([]byte, 32)
	_, _ = rand.Read(k)
	c, err := archivos.NuevaClave(base64.StdEncoding.EncodeToString(k))
	if err != nil {
		t.Fatal(err)
	}
	return c
}

func TestClaveInvalida(t *testing.T) {
	for _, k := range []string{"", "corta", base64.StdEncoding.EncodeToString(make([]byte, 16))} {
		if _, err := archivos.NuevaClave(k); !errors.Is(err, archivos.ErrClave) {
			t.Fatalf("aceptó %q: %v", k, err)
		}
	}
}

func TestCifrarYDescifrarVerificacion(t *testing.T) {
	d := archivos.Disco{Raiz: t.TempDir(), Clave: nuevaClave(t)}
	ruta, contenido, n, err := d.GuardarCifrado("dni", bytes.NewReader(jpeg))
	if err != nil {
		t.Fatal(err)
	}
	if !archivos.Cifrado(ruta) || contenido != "image/jpeg" || n != len(jpeg) {
		t.Fatalf("guardado: %q %q %d", ruta, contenido, n)
	}
	// En el disco no queda nada legible: ni la firma de JPEG ni el contenido.
	crudo, _ := os.ReadFile(filepath.Join(d.Raiz, ruta))
	if !bytes.HasPrefix(crudo, []byte("CGV1")) || bytes.Contains(crudo, []byte("selfie de prueba")) || bytes.Contains(crudo, jpeg[:3]) {
		t.Fatalf("el archivo en el disco no está cifrado: %q", crudo)
	}
	if b, err := d.Leer(ruta); err != nil || !bytes.Equal(b, jpeg) {
		t.Fatalf("ida y vuelta: %v", err)
	}
	// Dos archivos iguales no salen iguales (nonce al azar por archivo).
	otra, _, _, _ := d.GuardarCifrado("dni", bytes.NewReader(jpeg))
	crudo2, _ := os.ReadFile(filepath.Join(d.Raiz, otra))
	if bytes.Equal(crudo[4:16], crudo2[4:16]) {
		t.Fatal("dos archivos con el mismo nonce")
	}
	// Con otra clave, no abre.
	if _, err := (archivos.Disco{Raiz: d.Raiz, Clave: nuevaClave(t)}).Leer(ruta); !errors.Is(err, archivos.ErrDescifrar) {
		t.Fatalf("abrió con otra clave: %v", err)
	}
	// Copiado encima de otro archivo, tampoco (el nombre va como dato asociado).
	if err := os.WriteFile(filepath.Join(d.Raiz, otra), crudo, 0o640); err != nil {
		t.Fatal(err)
	}
	if _, err := d.Leer(otra); !errors.Is(err, archivos.ErrDescifrar) {
		t.Fatalf("abrió un archivo cambiado de lugar: %v", err)
	}
	// Tocado, tampoco.
	crudo[len(crudo)-1] ^= 1
	_ = os.WriteFile(filepath.Join(d.Raiz, ruta), crudo, 0o640)
	if _, err := d.Leer(ruta); !errors.Is(err, archivos.ErrDescifrar) {
		t.Fatalf("abrió un archivo tocado: %v", err)
	}
	// Lo que no es foto se rechaza antes de cifrar.
	if _, _, _, err := d.GuardarCifrado("dni", bytes.NewReader([]byte("no soy una foto"))); !errors.Is(err, archivos.ErrTipo) {
		t.Fatalf("aceptó algo que no es foto: %v", err)
	}
}

func TestSinClaveDeVerificacion(t *testing.T) {
	con := archivos.Disco{Raiz: t.TempDir(), Clave: nuevaClave(t)}
	ruta, _, _, err := con.GuardarCifrado("selfie", bytes.NewReader(jpeg))
	if err != nil {
		t.Fatal(err)
	}
	sin := archivos.Disco{Raiz: con.Raiz}
	if _, _, _, err := sin.GuardarCifrado("selfie", bytes.NewReader(jpeg)); !errors.Is(err, archivos.ErrSinClave) {
		t.Fatalf("guardó sin clave: %v", err)
	}
	if _, err := sin.Leer(ruta); !errors.Is(err, archivos.ErrSinClave) {
		t.Fatalf("leyó sin clave: %v", err)
	}
	if _, err := sin.Cifrar("selfie/viejo.jpg", false); !errors.Is(err, archivos.ErrSinClave) {
		t.Fatalf("cifró sin clave: %v", err)
	}
	// Las fotos públicas andan igual, y la purga borra el cifrado sin la clave.
	foto, _, _, err := sin.Guardar("foto", bytes.NewReader(jpeg))
	if err != nil {
		t.Fatal(err)
	}
	if b, err := sin.Leer(foto); err != nil || !bytes.Equal(b, jpeg) {
		t.Fatalf("foto pública: %v", err)
	}
	if err := sin.Borrar(ruta); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(con.Raiz, ruta)); !errors.Is(err, fs.ErrNotExist) {
		t.Fatal("no borró el cifrado")
	}
}

// Cifrar pasa un archivo viejo al formato nuevo sin borrar el original; con probar no escribe nada.
func TestCifrarArchivoViejo(t *testing.T) {
	d := archivos.Disco{Raiz: t.TempDir(), Clave: nuevaClave(t)}
	vieja, _, _, err := d.Guardar("dni", bytes.NewReader(jpeg)) // como se guardaba antes
	if err != nil {
		t.Fatal(err)
	}
	if b, err := d.Leer(vieja); err != nil || !bytes.Equal(b, jpeg) {
		t.Fatalf("un viejo sin cifrar se sigue leyendo: %v", err)
	}
	nueva, err := d.Cifrar(vieja, true)
	if err != nil || nueva != vieja+archivos.ExtCifrado {
		t.Fatalf("probar: %q %v", nueva, err)
	}
	if _, err := os.Stat(filepath.Join(d.Raiz, nueva)); !errors.Is(err, fs.ErrNotExist) {
		t.Fatal("probar escribió el .cif")
	}
	if nueva, err = d.Cifrar(vieja, false); err != nil {
		t.Fatal(err)
	}
	if b, err := d.Leer(nueva); err != nil || !bytes.Equal(b, jpeg) {
		t.Fatalf("el cifrado no da el original: %v", err)
	}
	if ok, err := d.MismoOriginal(nueva); !ok || err != nil {
		t.Fatalf("el original tiene que seguir al lado: %v %v", ok, err)
	}
	// Cifrar lo ya cifrado no hace nada.
	if otra, err := d.Cifrar(nueva, false); err != nil || otra != nueva {
		t.Fatalf("recifró: %q %v", otra, err)
	}
	if err := d.BorrarOriginal(nueva); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(d.Raiz, vieja)); !errors.Is(err, fs.ErrNotExist) {
		t.Fatal("quedó el original")
	}
	// Un original distinto al lado del .cif no se borra a ciegas.
	_ = os.WriteFile(filepath.Join(d.Raiz, vieja), []byte("otra cosa"), 0o640)
	if _, err := d.MismoOriginal(nueva); err == nil {
		t.Fatal("no avisó que el original no coincide")
	}
	// Borrar (la purga) se lleva las dos versiones.
	if err := d.Borrar(nueva); err != nil {
		t.Fatal(err)
	}
	for _, r := range []string{vieja, nueva} {
		if _, err := os.Stat(filepath.Join(d.Raiz, r)); !errors.Is(err, fs.ErrNotExist) {
			t.Fatalf("la purga dejó %s", r)
		}
	}
}
