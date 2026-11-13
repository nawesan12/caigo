// Package vetados: la lista de quienes no pueden volver a Caigo (cuentas suspendidas por
// moderación). No guarda ningún dato en claro: sólo huellas, un HMAC-SHA256 del número de DNI y del
// mail con una clave que se deriva (HKDF) de CAIGO_CLAVE_VERIFICACION sólo para esto. Sin la clave,
// una huella no dice de quién es y no se puede probar contra un padrón.
//
// Mientras la cuenta suspendida existe, sus huellas quedan atadas a su fila (vence_en NULL): si el
// equipo la reactiva, se borran. Cuando la purga borra la cuenta, se sueltan (usuario_id NULL) y
// vencen a los Plazo (2 años, lo que dice la política de privacidad); el worker borra las vencidas.
// El alta (sesion) consulta el mail y la verificación del DNI (confianza), el número.
//
// Ojo: si cambia CAIGO_CLAVE_VERIFICACION, las huellas viejas dejan de coincidir (la lista queda
// vacía en la práctica). Rotarla es empezar la lista de cero.
package vetados

import (
	"context"
	"crypto/hkdf"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
)

// Tipos de huella.
const (
	DNI   = "dni"
	Email = "email"
)

// Plazo: lo que dura una huella después de la purga de la cuenta.
const Plazo = 2 * 365 * 24 * time.Hour

// ErrClave: la clave no es de 32 bytes en base64 (la misma regla que CAIGO_CLAVE_VERIFICACION).
var ErrClave = errors.New("vetados: CAIGO_CLAVE_VERIFICACION tiene que ser de 32 bytes en base64")

// propósito de la derivación: la misma clave maestra cifra las selfies y los DNI con otra clave.
const proposito = "caigo:vetados:v1"

// Clave de las huellas. nil: sin clave (no se anota ni se consulta nada).
type Clave struct{ k []byte }

// NuevaClave deriva la clave de las huellas de CAIGO_CLAVE_VERIFICACION (base64, 32 bytes).
func NuevaClave(b64 string) (*Clave, error) {
	m, err := base64.StdEncoding.DecodeString(strings.TrimSpace(b64))
	if err != nil || len(m) != 32 {
		return nil, ErrClave
	}
	k, err := hkdf.Key(sha256.New, m, nil, proposito, 32)
	if err != nil {
		return nil, err
	}
	return &Clave{k: k}, nil
}

// NormalizarDNI deja sólo los números, sin ceros adelante (como PedirDNI).
func NormalizarDNI(n string) string {
	var b strings.Builder
	for _, r := range n {
		if r >= '0' && r <= '9' {
			b.WriteRune(r)
		}
	}
	return strings.TrimLeft(b.String(), "0")
}

// NormalizarEmail: minúsculas, sin espacios y sin el «+algo» (nombre+otro@ es la misma casilla). En
// Gmail, además, sin puntos en el nombre (n.ahuel@ y nahuel@ son la misma).
func NormalizarEmail(e string) string {
	e = strings.ToLower(strings.TrimSpace(e))
	nombre, dominio, ok := strings.Cut(e, "@")
	if !ok || nombre == "" || dominio == "" {
		return e
	}
	nombre, _, _ = strings.Cut(nombre, "+")
	if dominio == "gmail.com" || dominio == "googlemail.com" {
		nombre = strings.ReplaceAll(nombre, ".", "")
		dominio = "gmail.com"
	}
	return nombre + "@" + dominio
}

func normalizar(tipo, valor string) string {
	if tipo == DNI {
		return NormalizarDNI(valor)
	}
	return NormalizarEmail(valor)
}

// Huella de un valor ("" si está vacío después de normalizarlo). El tipo va adentro: un DNI y un
// mail iguales no dan la misma huella.
func (c *Clave) Huella(tipo, valor string) []byte {
	v := normalizar(tipo, valor)
	if c == nil || v == "" {
		return nil
	}
	m := hmac.New(sha256.New, c.k)
	m.Write([]byte(tipo + ":" + v))
	return m.Sum(nil)
}

// Vetado dice si el valor está en la lista (vigente). tx tiene que ser como sistema (EnSistema).
// Sin clave, nunca.
func (c *Clave) Vetado(ctx context.Context, tx pgx.Tx, tipo, valor string) (bool, error) {
	return VetadaHuella(ctx, tx, tipo, c.Huella(tipo, valor))
}

// VetadaHuella es Vetado con la huella ya calculada (nil: nunca).
func VetadaHuella(ctx context.Context, tx pgx.Tx, tipo string, h []byte) (bool, error) {
	if h == nil {
		return false, nil
	}
	var ok bool
	err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM vetados WHERE huella = $1 AND tipo = $2
		AND (vence_en IS NULL OR vence_en > now()))`, h, tipo).Scan(&ok)
	return ok, err
}

// Anotar agrega las huellas de una persona suspendida: su mail (el de Clerk que se copió al
// entrar) y las huellas de los DNI que el equipo le aprobó (los rechazados pueden ser de otra
// persona). La verificación del DNI no guarda el número, sólo su huella (la misma de acá). Devuelve
// cuántas quedaron. Si ya estaban, no las repite. tx como sistema.
func (c *Clave) Anotar(ctx context.Context, tx pgx.Tx, usuarioID string) (int, error) {
	if c == nil {
		return 0, nil
	}
	type huella struct {
		Tipo string
		H    []byte
	}
	var hs []huella
	var email string
	err := tx.QueryRow(ctx, `SELECT email FROM usuarios_privado WHERE usuario_id = $1`, usuarioID).Scan(&email)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return 0, err
	}
	if h := c.Huella(Email, email); h != nil {
		hs = append(hs, huella{Email, h})
	}
	rows, err := tx.Query(ctx, `SELECT DISTINCT huella FROM verificaciones_dni WHERE usuario_id = $1 AND estado = 'aprobada'`, usuarioID)
	if err != nil {
		return 0, err
	}
	dnis, err := pgx.CollectRows(rows, pgx.RowTo[[]byte])
	if err != nil {
		return 0, err
	}
	for _, h := range dnis {
		hs = append(hs, huella{DNI, h})
	}
	n := 0
	for _, h := range hs {
		if _, err := tx.Exec(ctx, `INSERT INTO vetados (huella, tipo, usuario_id) VALUES ($1, $2, $3)
			ON CONFLICT (huella, usuario_id) DO NOTHING`, h.H, h.Tipo, usuarioID); err != nil {
			return n, err
		}
		n++
	}
	return n, nil
}

// Tiene dice si la persona ya tiene huellas anotadas (no hace falta la clave).
func Tiene(ctx context.Context, tx pgx.Tx, usuarioID string) (bool, error) {
	var ok bool
	err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM vetados WHERE usuario_id = $1)`, usuarioID).Scan(&ok)
	return ok, err
}

// Quitar borra las huellas de una persona que el equipo reactivó.
func Quitar(ctx context.Context, tx pgx.Tx, usuarioID string) error {
	_, err := tx.Exec(ctx, `DELETE FROM vetados WHERE usuario_id = $1`, usuarioID)
	return err
}

// Soltar: la purga borró la cuenta. Sus huellas se desatan de la fila y vencen a los Plazo desde
// ahora (si la misma huella ya estaba suelta por otra cuenta, queda la que vence más tarde).
func Soltar(ctx context.Context, tx pgx.Tx, usuarioID string, ahora time.Time) error {
	if _, err := tx.Exec(ctx, `DELETE FROM vetados s WHERE s.usuario_id IS NULL
		AND s.huella IN (SELECT huella FROM vetados WHERE usuario_id = $1)`, usuarioID); err != nil {
		return err
	}
	_, err := tx.Exec(ctx, `UPDATE vetados SET usuario_id = NULL, vence_en = $2 WHERE usuario_id = $1`,
		usuarioID, ahora.Add(Plazo))
	return err
}
