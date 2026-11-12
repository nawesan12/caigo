package confianza

import (
	"context"
	"errors"
	"io/fs"
	"log/slog"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// MotivoBaja es lo que leen los que iban a los planes de quien se dio de baja.
const MotivoBaja = "La persona que organizaba se dio de baja"

// cancelarLoSuyo: sus planes futuros se cancelan como si los cancelara (avisos y reembolso entero de
// las entradas pagas), y sus propias entradas pagas de planes futuros se le devuelven (el precio,
// sin el cargo por servicio). Los planes de lugares no: son del lugar, no de quien los cargó.
func cancelarLoSuyo(ctx context.Context, tx pgx.Tx, usuarioID string) error {
	rows, err := tx.Query(ctx, `SELECT id::text FROM planes WHERE organizador_id = $1 AND lugar_id IS NULL
		AND cancelado_en IS NULL AND empieza_en > now() FOR UPDATE`, usuarioID)
	if err != nil {
		return err
	}
	ids, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return err
	}
	for _, id := range ids {
		if _, err := planes.CancelarTx(ctx, tx, id, MotivoBaja); err != nil {
			return err
		}
	}
	rows, err = tx.Query(ctx, `SELECT e.id::text, e.lugar_centavos::bigint FROM entradas e JOIN planes p ON p.id = e.plan_id
		WHERE e.usuario_id = $1 AND e.estado = 'pagada' AND p.empieza_en > now() AND p.cancelado_en IS NULL`, usuarioID)
	if err != nil {
		return err
	}
	type entrada struct {
		ID       string
		Centavos int64
	}
	es, err := pgx.CollectRows(rows, pgx.RowToStructByPos[entrada])
	if err != nil {
		return err
	}
	for _, x := range es {
		if err := outbox.Publicar(ctx, tx, "entrada.reembolsar", map[string]any{"entradaId": x.ID, "centavos": x.Centavos}); err != nil {
			return err
		}
	}
	return nil
}

// DiasParaPurgar: los días entre la baja y el borrado de los datos personales. En ese tiempo la
// cuenta se recupera a mano (caigoctl recuperar-cuenta) si la persona escribe a hola@.
const DiasParaPurgar = 30

// Baja borra la cuenta (DELETE /v1/yo; lo piden Google Play y la App Store). En el momento la
// desactiva: cierra todas las sesiones, borra los dispositivos de push, la ubicación y los códigos
// del teléfono, cancela sus planes futuros (con avisos y reembolsos), le devuelve sus entradas y la
// saca de los planes futuros donde estaba anotada. Desde ahí el ingreso responde cuenta_borrada.
// Sus datos personales los borra Purgar a los DiasParaPurgar días. La cuenta de Clerk no se toca:
// es la de todo Grupo Vimo.
//
// confirmar (opcional, lo manda la web): el mail que escribió la persona para confirmar. Si viene,
// tiene que ser el de su cuenta en Clerk; las apps confirman con un cartel y no lo mandan.
func (s *Servicio) Baja(ctx context.Context, usuarioID, clerkID, confirmar string) error {
	var email string
	if s.Casillas != nil && clerkID != "" {
		// Para encontrarla si escribe; si Clerk no responde, la baja se hace igual (salvo que haya
		// que confirmar el mail).
		if u, err := s.Casillas.Usuario(ctx, clerkID); err == nil {
			email = u.Email
		} else {
			s.log().Warn("baja: no se pudo leer el mail en Clerk", "error", err)
			if confirmar != "" {
				return problema.NoProcesable("sin_email", "No pudimos revisar tu mail. Probá de nuevo en un rato.")
			}
		}
	}
	if confirmar = strings.TrimSpace(confirmar); confirmar != "" && s.Casillas != nil && !strings.EqualFold(confirmar, email) {
		return problema.NoProcesable("email_distinto", "Ese no es el mail de tu cuenta. Escribí el mail con el que entrás a Caigo.")
	}
	if len(email) > 200 {
		email = ""
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := cancelarLoSuyo(ctx, tx, usuarioID); err != nil {
			return err
		}
		for _, q := range []string{
			`UPDATE solicitudes x SET estado = 'cancelada' FROM planes p WHERE p.id = x.plan_id AND x.usuario_id = $1
				AND x.estado IN ('pendiente', 'aceptada') AND p.empieza_en > now()`,
			`DELETE FROM telefono_codigos WHERE usuario_id = $1`,
			`DELETE FROM ubicaciones WHERE usuario_id = $1`,
			`DELETE FROM dispositivos WHERE usuario_id = $1`,
			`DELETE FROM sesiones WHERE usuario_id = $1`,
			// Si ya estaba dada de baja (dos pedidos a la vez), no cambia la fecha.
			`UPDATE usuarios SET baja_en = coalesce(baja_en, now()), actualizado_en = now() WHERE id = $1`,
		} {
			if _, err := tx.Exec(ctx, q, usuarioID); err != nil {
				return err
			}
		}
		if email == "" {
			return nil // queda el que se guardó al entrar
		}
		_, err := tx.Exec(ctx, `INSERT INTO usuarios_privado (usuario_id, email) VALUES ($1, $2)
			ON CONFLICT (usuario_id) DO UPDATE SET email = excluded.email`, usuarioID, email)
		return err
	})
}

// Purgar borra los datos personales de las cuentas dadas de baja antes de «antes» (ahora menos
// DiasParaPurgar días), de a tandas, y devuelve cuántas purgó. La fila de usuarios queda anónima para
// que el historial de planes, pagos y chats no se rompa: sin nombre, edad, bio, fotos, selfie, DNI,
// teléfono, mail ni intereses, y con el clerk_id liberado (si vuelve a entrar, arranca de cero). Sus
// mensajes quedan en los chats de los demás pero firmados por «Cuenta borrada». Los archivos se
// borran del disco después de confirmar: si falla, quedan huérfanos, no al revés.
func Purgar(ctx context.Context, app *db.Pool, disco archivos.Disco, antes time.Time) (int, error) {
	total := 0
	for {
		var rutas []string
		n := 0
		err := app.EnSistema(ctx, func(tx pgx.Tx) error {
			rows, err := tx.Query(ctx, `SELECT id::text FROM usuarios WHERE baja_en < $1 AND purgada_en IS NULL
				ORDER BY baja_en LIMIT 50 FOR UPDATE SKIP LOCKED`, antes)
			if err != nil {
				return err
			}
			ids, err := pgx.CollectRows(rows, pgx.RowTo[string])
			if err != nil {
				return err
			}
			for _, id := range ids {
				rows, err := tx.Query(ctx, `DELETE FROM archivos WHERE usuario_id = $1 RETURNING ruta`, id)
				if err != nil {
					return err
				}
				rs, err := pgx.CollectRows(rows, pgx.RowTo[string])
				if err != nil {
					return err
				}
				rutas = append(rutas, rs...)
				for _, q := range []string{
					`DELETE FROM mi_gente WHERE usuario_id = $1 OR otro_id = $1`,
					`DELETE FROM intereses WHERE usuario_id = $1 OR otro_id = $1`,
					`DELETE FROM invitaciones WHERE de_id = $1 OR a_id = $1`,
					`DELETE FROM bloqueos WHERE usuario_id = $1`,
					`DELETE FROM verificaciones_dni WHERE usuario_id = $1`,
					`DELETE FROM telefono_codigos WHERE usuario_id = $1`,
					`DELETE FROM ubicaciones WHERE usuario_id = $1`,
					`DELETE FROM avisos_enviados WHERE usuario_id = $1`,
					`DELETE FROM mp_estados WHERE usuario_id = $1`,
					// Lo que la baja ya borró, por si quedó algo (un pedido en el mismo instante).
					`DELETE FROM dispositivos WHERE usuario_id = $1`,
					`DELETE FROM sesiones WHERE usuario_id = $1`,
					`UPDATE usuarios_privado SET telefono = NULL, telefono_verificado_en = NULL, nacimiento = NULL,
						contacto_confianza = '', genero = '', diversidad = false, email = '' WHERE usuario_id = $1`,
					`UPDATE usuarios SET clerk_id = 'user_baja' || replace(id::text, '-', ''), nombre = 'Cuenta borrada', edad = NULL,
						zona = '', ciudad = '', bio = '', trabajo = '', intereses = '{}', intenciones = '{}', prompts = '[]',
						fotos = '{}', selfie_en = NULL, dni_en = NULL, purgada_en = now(), actualizado_en = now() WHERE id = $1`,
				} {
					if _, err := tx.Exec(ctx, q, id); err != nil {
						return err
					}
				}
			}
			n = len(ids)
			return nil
		})
		if err != nil {
			return total, err
		}
		total += n
		for _, r := range rutas {
			if err := disco.Borrar(r); err != nil && !errors.Is(err, fs.ErrNotExist) {
				return total, err
			}
		}
		if n < 50 {
			return total, nil
		}
	}
}

// Recuperar vuelve a activar una cuenta dada de baja y sin purgar (la persona escribió dentro de los
// 30 días), buscándola por clerk_id o por el mail que tenía. Los planes que la baja canceló siguen
// cancelados. Devuelve el id, o "" si no hay una baja que se pueda recuperar. Va con el dueño del
// esquema (caigoctl): la app no lo expone.
func Recuperar(ctx context.Context, pool *db.Pool, quien string) (string, error) {
	quien = strings.TrimSpace(quien)
	if quien == "" {
		return "", nil
	}
	var id string
	err := pool.EnSistema(ctx, func(tx pgx.Tx) error {
		err := tx.QueryRow(ctx, `UPDATE usuarios u SET baja_en = NULL, actualizado_en = now()
			WHERE u.id = (SELECT x.id FROM usuarios x LEFT JOIN usuarios_privado v ON v.usuario_id = x.id
				WHERE x.baja_en IS NOT NULL AND x.purgada_en IS NULL AND (x.clerk_id = $1 OR (v.email <> '' AND lower(v.email) = lower($1)))
				ORDER BY x.baja_en DESC LIMIT 1)
			RETURNING u.id::text`, quien).Scan(&id)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		return err
	})
	return id, err
}

func (s *Servicio) log() *slog.Logger {
	if s.Log != nil {
		return s.Log
	}
	return slog.Default()
}
