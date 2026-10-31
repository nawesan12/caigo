package confianza

import (
	"context"
	"errors"
	"io/fs"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
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

// Baja borra la cuenta: perfil anónimo, sin fotos, selfie, DNI ni teléfono; cancela sus planes
// (con avisos y reembolsos), le devuelve sus entradas y cancela los pedidos que no pasaron, y cierra todas las sesiones. Los pagos y el historial quedan atados al id
// (sin datos de la persona). La cuenta de Clerk no se toca: es la de todo Grupo Vimo.
func (s *Servicio) Baja(ctx context.Context, usuarioID string) error {
	var rutas []string
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT ruta FROM archivos WHERE usuario_id = $1`, usuarioID)
		if err != nil {
			return err
		}
		for rows.Next() {
			var r string
			if err := rows.Scan(&r); err != nil {
				rows.Close()
				return err
			}
			rutas = append(rutas, r)
		}
		rows.Close()
		if err := rows.Err(); err != nil {
			return err
		}
		if err := cancelarLoSuyo(ctx, tx, usuarioID); err != nil {
			return err
		}
		for _, q := range []string{
			`UPDATE solicitudes x SET estado = 'cancelada' FROM planes p WHERE p.id = x.plan_id AND x.usuario_id = $1
				AND x.estado IN ('pendiente', 'aceptada') AND p.empieza_en > now()`,
			`DELETE FROM mi_gente WHERE usuario_id = $1`,
			`DELETE FROM verificaciones_dni WHERE usuario_id = $1`,
			`DELETE FROM archivos WHERE usuario_id = $1`,
			`DELETE FROM telefono_codigos WHERE usuario_id = $1`,
			`UPDATE usuarios_privado SET telefono = NULL, telefono_verificado_en = NULL, nacimiento = NULL,
				contacto_confianza = '', genero = '', diversidad = false WHERE usuario_id = $1`,
			`DELETE FROM ubicaciones WHERE usuario_id = $1`,
			`DELETE FROM dispositivos WHERE usuario_id = $1`,
			`DELETE FROM sesiones WHERE usuario_id = $1`,
			`UPDATE usuarios SET clerk_id = 'user_baja' || replace(id::text, '-', ''), nombre = '', edad = NULL,
				zona = '', ciudad = '', bio = '', trabajo = '', intereses = '{}', intenciones = '{}', prompts = '[]',
				fotos = '{}', selfie_en = NULL, dni_en = NULL, baja_en = now(), actualizado_en = now() WHERE id = $1`,
		} {
			if _, err := tx.Exec(ctx, q, usuarioID); err != nil {
				return err
			}
		}
		return nil
	})
	if err != nil {
		return err
	}
	// Los archivos se borran del disco después de confirmar: si falla, quedan huérfanos, no al revés.
	for _, r := range rutas {
		if err := s.Disco.Borrar(r); err != nil && !errors.Is(err, fs.ErrNotExist) {
			return err
		}
	}
	return nil
}
