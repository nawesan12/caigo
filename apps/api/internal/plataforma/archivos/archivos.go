// Package archivos guarda lo que sube la gente en el disco del VPS (nada de buckets): fotos de
// perfil y de planes, selfies y fotos del DNI. El tipo se decide por los primeros bytes, no por
// lo que diga el cliente. Las selfies y las fotos del DNI van cifradas (ver verificacion.go).
package archivos

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// Maximo por archivo.
const Maximo = 8 << 20

// ErrTipo: no es una imagen que aceptemos.
var ErrTipo = errors.New("archivos: tiene que ser una foto JPG, PNG, WebP o HEIC")

// ErrGrande: pasa el máximo.
var ErrGrande = errors.New("archivos: la foto pesa más de 8 MB")

// Disco es la carpeta raíz. Clave cifra los archivos de verificación (selfie y DNI); nil: no se
// pueden guardar ni leer, pero las fotos públicas y la purga andan igual.
type Disco struct {
	Raiz  string
	Clave *Clave
}

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
	b, contenido, ext, err := leerFoto(r)
	if err != nil {
		return "", "", 0, err
	}
	ruta = nuevaRuta(carpeta, ext)
	if err := escribir(filepath.Join(d.Raiz, ruta), b); err != nil {
		return "", "", 0, err
	}
	return ruta, contenido, len(b), nil
}

// leerFoto lee hasta el máximo y decide el tipo por la firma.
func leerFoto(r io.Reader) (b []byte, contenido, ext string, err error) {
	b, err = io.ReadAll(io.LimitReader(r, Maximo+1))
	if err != nil {
		return nil, "", "", err
	}
	if len(b) > Maximo {
		return nil, "", "", ErrGrande
	}
	contenido, ext = Contenido(b[:min(len(b), 16)])
	if contenido == "" {
		return nil, "", "", ErrTipo
	}
	return b, contenido, ext, nil
}

func nuevaRuta(carpeta, ext string) string {
	azar := make([]byte, 16)
	_, _ = rand.Read(azar)
	return filepath.Join(carpeta, time.Now().UTC().Format("2006-01"), hex.EncodeToString(azar)+ext)
}

// escribir deja b en abs sin que nadie vea un archivo a medias: escribe al lado, sincroniza y
// renombra (en el mismo disco el rename es atómico).
func escribir(abs string, b []byte) error {
	if err := os.MkdirAll(filepath.Dir(abs), 0o750); err != nil {
		return fmt.Errorf("archivos: %w", err)
	}
	tmp, err := os.CreateTemp(filepath.Dir(abs), ".nuevo-*")
	if err != nil {
		return fmt.Errorf("archivos: %w", err)
	}
	defer os.Remove(tmp.Name()) // si llegó al rename, ya no existe
	if _, err := tmp.Write(b); err != nil {
		tmp.Close()
		return fmt.Errorf("archivos: %w", err)
	}
	if err := tmp.Chmod(0o640); err != nil {
		tmp.Close()
		return fmt.Errorf("archivos: %w", err)
	}
	if err := tmp.Sync(); err != nil {
		tmp.Close()
		return fmt.Errorf("archivos: %w", err)
	}
	if err := tmp.Close(); err != nil {
		return fmt.Errorf("archivos: %w", err)
	}
	if err := os.Rename(tmp.Name(), abs); err != nil {
		return fmt.Errorf("archivos: %w", err)
	}
	return nil
}

// Abrir abre un archivo guardado (ruta relativa, ya validada contra la base).
func (d Disco) Abrir(ruta string) (*os.File, error) {
	return os.Open(d.abs(ruta))
}

// Borrar saca un archivo del disco (ruta relativa, ya validada contra la base). También borra su
// otra versión, si quedó (el original sin cifrar de un .cif, o el .cif de un original): un corte a
// mitad de cifrar-verificaciones no deja una copia suelta que la purga no vea.
func (d Disco) Borrar(ruta string) error {
	otra := ruta + ExtCifrado
	if Cifrado(ruta) {
		otra = strings.TrimSuffix(ruta, ExtCifrado)
	}
	if err := os.Remove(d.abs(otra)); err != nil && !errors.Is(err, fs.ErrNotExist) {
		return err
	}
	return os.Remove(d.abs(ruta))
}

// abs arma la ruta absoluta sin dejar salir de la raíz.
func (d Disco) abs(ruta string) string {
	return filepath.Join(d.Raiz, filepath.Clean("/"+ruta))
}
