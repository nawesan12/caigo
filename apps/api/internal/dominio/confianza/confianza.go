// Package confianza: las capas de verificación (teléfono por SMS, selfie con prueba de vida, DNI
// revisado por el equipo), los archivos subidos y la moderación. La prueba de vida y la lectura
// del PDF417 del DNI pasan en el teléfono (Vision / ML Kit); acá se guarda lo que manda y el
// equipo revisa a mano.
package confianza

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"errors"
	"fmt"
	"io"
	"math/big"
	"regexp"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sms"
)

// Servicio de confianza.
type Servicio struct {
	App   *db.Pool
	Disco archivos.Disco
	SMS   sms.Enviador
	// URL pública de la API, para armar la URL de las fotos.
	APIPublica string
	Reloj      func() time.Time
}

func (s *Servicio) ahora() time.Time {
	if s.Reloj != nil {
		return s.Reloj()
	}
	return time.Now()
}

// Archivo subido.
type Archivo struct {
	ID  string `json:"id"`
	URL string `json:"url"`
}

// Subir guarda un archivo de la persona. tipo: foto (pública) | selfie | dni (privadas).
func (s *Servicio) Subir(ctx context.Context, usuarioID, tipo string, r io.Reader) (Archivo, error) {
	if tipo != "foto" && tipo != "selfie" && tipo != "dni" {
		return Archivo{}, problema.Invalido("Tipo de archivo desconocido.")
	}
	ruta, contenido, n, err := s.Disco.Guardar(tipo, r)
	if errors.Is(err, archivos.ErrTipo) || errors.Is(err, archivos.ErrGrande) {
		return Archivo{}, problema.Invalido(strings.TrimPrefix(err.Error(), "archivos: "))
	}
	if err != nil {
		return Archivo{}, err
	}
	var a Archivo
	err = s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `INSERT INTO archivos (usuario_id, tipo, ruta, contenido, bytes)
			VALUES ($1, $2, $3, $4, $5) RETURNING id`, usuarioID, tipo, ruta, contenido, n).Scan(&a.ID)
	})
	a.URL = strings.TrimRight(s.APIPublica, "/") + "/v1/archivos/" + a.ID
	return a, err
}

// ArchivoGuardado para servir.
type ArchivoGuardado struct {
	Ruta, Contenido, Tipo, Dueno string
}

// Buscar devuelve el archivo para servirlo (como sistema: quién puede verlo lo decide el handler).
func (s *Servicio) Buscar(ctx context.Context, id string) (ArchivoGuardado, error) {
	var a ArchivoGuardado
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `SELECT ruta, contenido, tipo, usuario_id FROM archivos WHERE id::text = $1`, id).
			Scan(&a.Ruta, &a.Contenido, &a.Tipo, &a.Dueno)
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return a, problema.NoEncontrado("Ese archivo no existe.")
	}
	return a, err
}

var reTelefono = regexp.MustCompile(`^\+[1-9][0-9]{7,14}$`)

// normalizarTelefono pasa "223 555-1234" o "+54 9 223…" a E.164 (por defecto, Argentina +54 9).
func normalizarTelefono(t string) string {
	var b strings.Builder
	for _, r := range t {
		if r == '+' || (r >= '0' && r <= '9') {
			b.WriteRune(r)
		}
	}
	n := b.String()
	if !strings.HasPrefix(n, "+") {
		n = strings.TrimPrefix(n, "0")
		n = "+549" + strings.TrimPrefix(n, "15")
	}
	return n
}

func hashCodigo(usuarioID, codigo string) []byte {
	h := sha256.Sum256([]byte(usuarioID + ":" + codigo))
	return h[:]
}

// PedirCodigo manda el código por SMS. Devuelve el código sólo si el SMS es simulado.
func (s *Servicio) PedirCodigo(ctx context.Context, usuarioID, telefono string) (string, error) {
	telefono = normalizarTelefono(telefono)
	if !reTelefono.MatchString(telefono) {
		return "", problema.Invalido("Ese número no parece un celular.")
	}
	n, _ := rand.Int(rand.Reader, big.NewInt(1000000))
	codigo := fmt.Sprintf("%06d", n.Int64())
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var enviado time.Time
		err := tx.QueryRow(ctx, `SELECT enviado_en FROM telefono_codigos WHERE usuario_id = $1`, usuarioID).Scan(&enviado)
		if err == nil && s.ahora().Sub(enviado) < 30*time.Second {
			return problema.Conflicto("espera", "Esperá unos segundos antes de pedir otro código.")
		}
		if err != nil && !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		var otro bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM usuarios_privado WHERE telefono = $1
			AND telefono_verificado_en IS NOT NULL AND usuario_id <> $2)`, telefono, usuarioID).Scan(&otro); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `
			INSERT INTO telefono_codigos (usuario_id, telefono, codigo_hash, vence_en) VALUES ($1, $2, $3, $4)
			ON CONFLICT (usuario_id) DO UPDATE SET telefono = excluded.telefono, codigo_hash = excluded.codigo_hash,
				intentos = 0, enviado_en = now(), vence_en = excluded.vence_en`,
			usuarioID, telefono, hashCodigo(usuarioID, codigo), s.ahora().Add(10*time.Minute))
		return err
	})
	if err != nil {
		return "", err
	}
	if err := s.SMS.Enviar(ctx, telefono, "Tu código de Caigo es "+codigo+". No se lo pases a nadie."); err != nil {
		return "", err
	}
	if s.SMS.Simulado() {
		return codigo, nil
	}
	return "", nil
}

// ConfirmarCodigo verifica el código y deja el teléfono verificado. 5 intentos por código.
func (s *Servicio) ConfirmarCodigo(ctx context.Context, usuarioID, codigo string) error {
	mal := false
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var telefono string
		var guardado []byte
		var intentos int
		var vence time.Time
		err := tx.QueryRow(ctx, `SELECT telefono, codigo_hash, intentos, vence_en FROM telefono_codigos
			WHERE usuario_id = $1 FOR UPDATE`, usuarioID).Scan(&telefono, &guardado, &intentos, &vence)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.Conflicto("sin_codigo", "Pedí un código primero.")
		}
		if err != nil {
			return err
		}
		if intentos >= 5 || s.ahora().After(vence) {
			return problema.Conflicto("vencido", "El código venció. Pedí otro.")
		}
		if subtle.ConstantTimeCompare(guardado, hashCodigo(usuarioID, strings.TrimSpace(codigo))) != 1 {
			// El intento fallido tiene que quedar guardado: se confirma la transacción y se avisa después.
			mal = true
			_, err := tx.Exec(ctx, `UPDATE telefono_codigos SET intentos = intentos + 1 WHERE usuario_id = $1`, usuarioID)
			return err
		}
		if _, err := tx.Exec(ctx, `
			INSERT INTO usuarios_privado (usuario_id, telefono, telefono_verificado_en) VALUES ($1, $2, now())
			ON CONFLICT (usuario_id) DO UPDATE SET telefono = excluded.telefono, telefono_verificado_en = now()`,
			usuarioID, telefono); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `DELETE FROM telefono_codigos WHERE usuario_id = $1`, usuarioID)
		return err
	})
	if err == nil && mal {
		return errCodigoMal
	}
	return err
}

var errCodigoMal = problema.Invalido("Ese código no es. Revisalo y probá de nuevo.")

// Selfie registra la selfie con prueba de vida (el archivo ya subido como tipo selfie).
func (s *Servicio) Selfie(ctx context.Context, usuarioID, archivoID string) error {
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var ok bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM archivos WHERE id::text = $1 AND usuario_id = $2 AND tipo = 'selfie')`,
			archivoID, usuarioID).Scan(&ok); err != nil {
			return err
		}
		if !ok {
			return problema.Invalido("Subí la selfie primero.")
		}
		_, err := tx.Exec(ctx, `UPDATE usuarios SET selfie_en = now() WHERE id = $1`, usuarioID)
		return err
	})
}

// DatosDNI es lo que la app leyó del código PDF417 del frente.
type DatosDNI struct {
	FrenteID   string `json:"frenteId"`
	DorsoID    string `json:"dorsoId"`
	SelfieID   string `json:"selfieId"`
	Numero     string `json:"numero"`
	Apellido   string `json:"apellido"`
	Nombres    string `json:"nombres"`
	Sexo       string `json:"sexo"`
	Nacimiento string `json:"nacimiento"`
	Tramite    string `json:"tramite"`
}

var reDNI = regexp.MustCompile(`^[0-9]{7,9}$`)

// PedirDNI deja la verificación pendiente de revisión.
func (s *Servicio) PedirDNI(ctx context.Context, usuarioID string, d DatosDNI) error {
	d.Numero = strings.TrimLeft(strings.ReplaceAll(d.Numero, ".", ""), "0")
	nac, err := time.Parse(time.DateOnly, d.Nacimiento)
	switch {
	case !reDNI.MatchString(d.Numero):
		return problema.Invalido("No pudimos leer el número de DNI.")
	case err != nil:
		return problema.Invalido("No pudimos leer la fecha de nacimiento.")
	case strings.TrimSpace(d.Apellido) == "" || strings.TrimSpace(d.Nombres) == "":
		return problema.Invalido("No pudimos leer el nombre del DNI.")
	case nac.AddDate(18, 0, 0).After(s.ahora()):
		return problema.NoProcesable("menor", "Caigo es sólo para mayores de 18.")
	}
	if d.Sexo != "M" && d.Sexo != "F" && d.Sexo != "X" {
		d.Sexo = ""
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM archivos WHERE usuario_id = $1 AND (
			(id::text = $2 AND tipo = 'dni') OR (id::text = $3 AND tipo = 'dni') OR (id::text = $4 AND tipo = 'selfie'))`,
			usuarioID, d.FrenteID, d.DorsoID, d.SelfieID).Scan(&n); err != nil {
			return err
		}
		if n != 3 {
			return problema.Invalido("Faltan las fotos del frente, el dorso o la selfie.")
		}
		var pendiente bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM verificaciones_dni WHERE usuario_id = $1 AND estado = 'pendiente')`, usuarioID).Scan(&pendiente); err != nil {
			return err
		}
		if pendiente {
			return problema.Conflicto("pendiente", "Ya estamos revisando tu DNI.")
		}
		_, err := tx.Exec(ctx, `INSERT INTO verificaciones_dni (usuario_id, frente_id, dorso_id, selfie_id, numero,
			apellido, nombres, sexo, nacimiento, tramite) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
			usuarioID, d.FrenteID, d.DorsoID, d.SelfieID, d.Numero, strings.TrimSpace(d.Apellido),
			strings.TrimSpace(d.Nombres), d.Sexo, nac, strings.TrimSpace(d.Tramite))
		return err
	})
}

// Estado de la verificación de una persona.
type Estado struct {
	Telefono  string `json:"telefono,omitempty"`
	Selfie    bool   `json:"selfie"`
	DNI       string `json:"dni"` // ninguna | pendiente | aprobada | rechazada
	DNIMotivo string `json:"dniMotivo,omitempty"`
}

// VerEstado devuelve en qué está cada capa.
func (s *Servicio) VerEstado(ctx context.Context, usuarioID string) (Estado, error) {
	e := Estado{DNI: "ninguna"}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := tx.QueryRow(ctx, `SELECT u.selfie_en IS NOT NULL,
				coalesce((SELECT telefono FROM usuarios_privado p WHERE p.usuario_id = u.id AND p.telefono_verificado_en IS NOT NULL), '')
			FROM usuarios u WHERE u.id = $1`, usuarioID).Scan(&e.Selfie, &e.Telefono); err != nil {
			return err
		}
		err := tx.QueryRow(ctx, `SELECT estado, motivo FROM verificaciones_dni WHERE usuario_id = $1 ORDER BY creada_en DESC LIMIT 1`, usuarioID).Scan(&e.DNI, &e.DNIMotivo)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		return err
	})
	return e, err
}
