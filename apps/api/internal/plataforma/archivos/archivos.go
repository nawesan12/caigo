// Package archivos guarda lo que sube la gente en el disco del VPS (nada de buckets): fotos de
// perfil y de planes, selfies y fotos del DNI. El tipo se decide por los primeros bytes, no por
// lo que diga el cliente.
package archivos

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"time"
)

// Maximo por archivo.
const Maximo = 8 << 20

// ErrTipo: no es una imagen que aceptemos.
var ErrTipo = errors.New("archivos: tiene que ser una foto JPG, PNG, WebP o HEIC")

// ErrGrande: pasa el máximo.
var ErrGrande = errors.New("archivos: la foto pesa más de 8 MB")

// Disco es la carpeta raíz.
type Disco struct{ Raiz string }

// Contenido detecta el tipo por la firma del archivo. Vacío: no es una imagen aceptada.
func Contenido(cab []byte) (tipo, ext string) {
	switch {
	case bytes.HasPrefix(cab, []byte{0xFF, 0xD8, 0xFF}):
		return "image/jpeg", ".jpg"
	case bytes.HasPrefix(cab, []byte("\x89PNG\r\n\x1a\n")):
		return "image/png", ".png"
	case len(cab) >= 12 && bytes.Equal(cab[:4], []byte("RIFF")) && bytes.Equal(cab[8:12], []byte("WEBP")):
		return "image/webp", ".webp"
	case len(cab) >= 12 && bytes.Equal(cab[4:8], []byte("ftyp")) &&
		(bytes.Equal(cab[8:12], []byte("heic")) || bytes.Equal(cab[8:12], []byte("heix")) || bytes.Equal(cab[8:12], []byte("mif1"))):
		return "image/heic", ".heic"
	}
	return "", ""
}

// Guardar escribe el archivo en <raiz>/<carpeta>/<AAAA-MM>/<azar><ext> y devuelve la ruta relativa
// y el tipo de contenido.
func (d Disco) Guardar(carpeta string, r io.Reader) (ruta, contenido string, n int, err error) {
	b, err := io.ReadAll(io.LimitReader(r, Maximo+1))
	if err != nil {
		return "", "", 0, err
	}
	if len(b) > Maximo {
		return "", "", 0, ErrGrande
	}
	contenido, ext := Contenido(b[:min(len(b), 16)])
	if contenido == "" {
		return "", "", 0, ErrTipo
	}
	azar := make([]byte, 16)
	_, _ = rand.Read(azar)
	ruta = filepath.Join(carpeta, time.Now().UTC().Format("2006-01"), hex.EncodeToString(azar)+ext)
	abs := filepath.Join(d.Raiz, ruta)
	if err := os.MkdirAll(filepath.Dir(abs), 0o750); err != nil {
		return "", "", 0, err
	}
	if err := os.WriteFile(abs, b, 0o640); err != nil {
		return "", "", 0, fmt.Errorf("archivos: %w", err)
	}
	return ruta, contenido, len(b), nil
}

// Abrir abre un archivo guardado (ruta relativa, ya validada contra la base).
func (d Disco) Abrir(ruta string) (*os.File, error) {
	return os.Open(filepath.Join(d.Raiz, filepath.Clean("/"+ruta)))
}
