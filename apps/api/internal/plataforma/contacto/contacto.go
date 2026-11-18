// Package contacto valida los contactos de confianza (un mail o un teléfono) y arma la huella de un
// mail para los topes y la lista de supresión (nunca se guarda la dirección en claro).
package contacto

import (
	"crypto/sha256"
	"encoding/hex"
	"net/mail"
	"strings"
	"unicode"
)

// Min y Max: el largo del contacto (lo mismo que la columna todo_bien.contacto deja guardar).
const (
	Min = 6
	Max = 80
)

// EsMail: el contacto es una dirección de mail válida (le escribimos nosotros si no hay check-in).
func EsMail(c string) bool {
	c = strings.TrimSpace(c)
	if !strings.Contains(c, "@") {
		return false
	}
	a, err := mail.ParseAddress(c)
	return err == nil && a.Address == c && a.Name == ""
}

// Digitos de un teléfono escrito como sea («0223 15 592-6772» → «0223155926772»).
func Digitos(c string) string {
	var b strings.Builder
	for _, r := range c {
		if unicode.IsDigit(r) {
			b.WriteRune(r)
		}
	}
	return b.String()
}

// Valido: un mail válido o un teléfono de 8 a 15 dígitos, con el largo de la columna.
func Valido(c string) bool {
	c = strings.TrimSpace(c)
	n := len([]rune(c))
	if n < Min || n > Max {
		return false
	}
	if strings.Contains(c, "@") {
		return EsMail(c)
	}
	d := Digitos(c)
	return len(d) >= 8 && len(d) <= 15 && strings.Trim(c, "0123456789 +-().") == ""
}

// Huella de un mail (sha256 en hexa de la dirección en minúsculas).
func Huella(m string) string {
	h := sha256.Sum256([]byte(strings.ToLower(strings.TrimSpace(m))))
	return hex.EncodeToString(h[:])
}
