// Package firma firma y verifica tokens cortos con HMAC-SHA256: el QR de check-in de un plan, las
// placas de Caigo Tap y el link de "compartir mi plan en vivo". El token lleva su propósito, para
// que uno no sirva en lugar de otro, y una fecha de vencimiento.
package firma

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"strconv"
	"strings"
	"time"
)

// ErrToken: firma inválida, vencido o de otro propósito.
var ErrToken = errors.New("firma: token inválido")

// Firmador con la clave del servidor (CAIGO_SECRETO).
type Firmador struct{ Clave []byte }

func (f Firmador) mac(s string) string {
	m := hmac.New(sha256.New, f.Clave)
	m.Write([]byte(s))
	return base64.RawURLEncoding.EncodeToString(m.Sum(nil)[:18])
}

// Firmar devuelve "<dato>.<vence>.<mac>". vence cero: no vence.
func (f Firmador) Firmar(proposito, dato string, vence time.Time) string {
	v := "0"
	if !vence.IsZero() {
		v = strconv.FormatInt(vence.Unix(), 36)
	}
	cuerpo := dato + "." + v
	return cuerpo + "." + f.mac(proposito+":"+cuerpo)
}

// Verificar devuelve el dato si la firma es buena y no venció.
func (f Firmador) Verificar(proposito, token string, ahora time.Time) (string, error) {
	partes := strings.Split(token, ".")
	if len(partes) != 3 || len(token) > 300 {
		return "", ErrToken
	}
	cuerpo := partes[0] + "." + partes[1]
	if !hmac.Equal([]byte(f.mac(proposito+":"+cuerpo)), []byte(partes[2])) {
		return "", ErrToken
	}
	if partes[1] != "0" {
		v, err := strconv.ParseInt(partes[1], 36, 64)
		if err != nil || ahora.After(time.Unix(v, 0)) {
			return "", ErrToken
		}
	}
	return partes[0], nil
}
