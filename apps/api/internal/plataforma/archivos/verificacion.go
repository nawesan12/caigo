package archivos

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
)

// Las selfies y las fotos del DNI se guardan cifradas con AES-256-GCM y la clave de
// CAIGO_CLAVE_VERIFICACION (32 bytes en base64). Cada archivo es:
//
//	"CGV1" | nonce (12 bytes al azar) | foto cifrada + etiqueta de GCM
//
// y su nombre termina en .cif (dni/2026-11/<azar>.jpg.cif). El nombre sin .cif va como dato
// asociado: un archivo copiado encima de otro no descifra. Un volcado del disco o de las copias no
// alcanza para ver un DNI sin la clave, que vive sólo en api.conf.

// ExtCifrado marca los archivos cifrados (lo que no termina así es de antes de cifrar).
const ExtCifrado = ".cif"

// marca va al principio de cada archivo cifrado (versión del formato).
var marca = []byte("CGV1")

// ErrSinClave: no hay CAIGO_CLAVE_VERIFICACION, así que no se puede guardar ni leer una selfie o un DNI.
var ErrSinClave = errors.New("archivos: falta CAIGO_CLAVE_VERIFICACION, no se pueden guardar ni leer selfies ni DNI")

// ErrClave: la clave no es de 32 bytes en base64.
var ErrClave = errors.New("archivos: CAIGO_CLAVE_VERIFICACION tiene que ser de 32 bytes en base64 (openssl rand -base64 32)")

// ErrDescifrar: el archivo es de otra clave, no es de este nombre o se tocó.
var ErrDescifrar = errors.New("archivos: el archivo cifrado no se pudo abrir (otra clave, otro archivo o está dañado)")

// Clave de los archivos de verificación.
type Clave struct{ aead cipher.AEAD }

// NuevaClave recibe la clave en base64 (32 bytes).
func NuevaClave(b64 string) (*Clave, error) {
	k, err := base64.StdEncoding.DecodeString(strings.TrimSpace(b64))
	if err != nil || len(k) != 32 {
		return nil, ErrClave
	}
	b, err := aes.NewCipher(k)
	if err != nil {
		return nil, err
	}
	a, err := cipher.NewGCM(b)
	if err != nil {
		return nil, err
	}
	return &Clave{aead: a}, nil
}

// Cifrado dice si la ruta es de un archivo cifrado.
func Cifrado(ruta string) bool { return strings.HasSuffix(ruta, ExtCifrado) }

// asociado: el nombre del archivo sin .cif (no la carpeta: la ruta relativa no cambia, pero así
// tampoco depende de dónde esté montada la raíz).
func asociado(ruta string) []byte {
	return []byte(strings.TrimSuffix(filepath.Base(ruta), ExtCifrado))
}

// Sellar cifra plano para la ruta dada (con o sin .cif).
func (c *Clave) Sellar(ruta string, plano []byte) ([]byte, error) {
	nonce := make([]byte, c.aead.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return nil, err
	}
	out := make([]byte, 0, len(marca)+len(nonce)+len(plano)+c.aead.Overhead())
	out = append(append(out, marca...), nonce...)
	return c.aead.Seal(out, nonce, plano, asociado(ruta)), nil
}

// Abrir descifra lo que hizo Sellar para la misma ruta.
func (c *Clave) Abrir(ruta string, dato []byte) ([]byte, error) {
	n := len(marca) + c.aead.NonceSize()
	if len(dato) < n+c.aead.Overhead() || !bytes.Equal(dato[:len(marca)], marca) {
		return nil, ErrDescifrar
	}
	plano, err := c.aead.Open(nil, dato[len(marca):n], dato[n:], asociado(ruta))
	if err != nil {
		return nil, ErrDescifrar
	}
	return plano, nil
}

// GuardarCifrado es Guardar para la selfie y el DNI: valida la foto, la cifra y la escribe en
// <raiz>/<carpeta>/<AAAA-MM>/<azar><ext>.cif. n es el tamaño de la foto sin cifrar.
func (d Disco) GuardarCifrado(carpeta string, r io.Reader) (ruta, contenido string, n int, err error) {
	if d.Clave == nil {
		return "", "", 0, ErrSinClave
	}
	b, contenido, ext, err := leerFoto(r)
	if err != nil {
		return "", "", 0, err
	}
	ruta = nuevaRuta(carpeta, ext) + ExtCifrado
	sellado, err := d.Clave.Sellar(ruta, b)
	if err != nil {
		return "", "", 0, err
	}
	if err := escribir(d.abs(ruta), sellado); err != nil {
		return "", "", 0, err
	}
	return ruta, contenido, len(b), nil
}

// Leer devuelve el contenido entero de un archivo guardado, descifrado en memoria si es .cif (nunca
// se escribe descifrado en el disco). Los de antes de cifrar se leen tal cual.
func (d Disco) Leer(ruta string) ([]byte, error) {
	if Cifrado(ruta) && d.Clave == nil {
		return nil, ErrSinClave
	}
	b, err := os.ReadFile(d.abs(ruta))
	if err != nil || !Cifrado(ruta) {
		return b, err
	}
	return d.Clave.Abrir(ruta, b)
}

// Cifrar pasa un archivo viejo (sin cifrar) al formato nuevo: escribe <ruta>.cif, lo vuelve a leer
// del disco y confirma que descifra igual al original. Devuelve la ruta nueva. El original no se
// toca: lo borra quien llama, después de anotar la ruta nueva en la base. Si ruta ya es .cif, no
// hace nada. probar: hace todo en memoria y no escribe.
func (d Disco) Cifrar(ruta string, probar bool) (string, error) {
	if Cifrado(ruta) {
		return ruta, nil
	}
	if d.Clave == nil {
		return "", ErrSinClave
	}
	plano, err := os.ReadFile(d.abs(ruta))
	if err != nil {
		return "", err
	}
	nueva := ruta + ExtCifrado
	sellado, err := d.Clave.Sellar(nueva, plano)
	if err != nil {
		return "", err
	}
	if probar {
		if vuelta, err := d.Clave.Abrir(nueva, sellado); err != nil || !bytes.Equal(vuelta, plano) {
			return "", fmt.Errorf("archivos: %s no descifra igual al original", ruta)
		}
		return nueva, nil
	}
	if err := escribir(d.abs(nueva), sellado); err != nil {
		return "", err
	}
	if vuelta, err := d.Leer(nueva); err != nil || !bytes.Equal(vuelta, plano) {
		_ = os.Remove(d.abs(nueva))
		return "", fmt.Errorf("archivos: %s no descifra igual al original, se deja como estaba", ruta)
	}
	return nueva, nil
}

// MismoOriginal dice si al lado del .cif quedó su original sin cifrar y es igual a lo que descifra.
// Si quedó pero es distinto, es un error: no se borra nada a ciegas.
func (d Disco) MismoOriginal(rutaCifrada string) (bool, error) {
	if !Cifrado(rutaCifrada) {
		return false, nil
	}
	plano, err := os.ReadFile(d.abs(strings.TrimSuffix(rutaCifrada, ExtCifrado)))
	if errors.Is(err, fs.ErrNotExist) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	vuelta, err := d.Leer(rutaCifrada)
	if err != nil {
		return false, fmt.Errorf("%s: %w", rutaCifrada, err)
	}
	if !bytes.Equal(vuelta, plano) {
		return false, fmt.Errorf("archivos: %s no coincide con el original que quedó al lado, revisalo a mano", rutaCifrada)
	}
	return true, nil
}

// BorrarOriginal borra el original sin cifrar de un .cif (si ya no está, no es error).
func (d Disco) BorrarOriginal(rutaCifrada string) error {
	if !Cifrado(rutaCifrada) {
		return fmt.Errorf("archivos: %s no es un archivo cifrado", rutaCifrada)
	}
	err := os.Remove(d.abs(strings.TrimSuffix(rutaCifrada, ExtCifrado)))
	if errors.Is(err, fs.ErrNotExist) {
		return nil
	}
	return err
}

// BorrarCifrado borra sólo el .cif (cuando no llegó a la base); el original queda.
func (d Disco) BorrarCifrado(rutaCifrada string) error {
	if !Cifrado(rutaCifrada) {
		return fmt.Errorf("archivos: %s no es un archivo cifrado", rutaCifrada)
	}
	return os.Remove(d.abs(rutaCifrada))
}
