package planes

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Solicitud como la ve el organizador.
type Solicitud struct {
	ID       string    `json:"id"`
	Persona  Persona   `json:"persona"`
	Cantidad int16     `json:"cantidad"`
	Mensaje  string    `json:"mensaje"`
	Estado   string    `json:"estado"`
	CreadaEn time.Time `json:"creadaEn"`
}

// Sumarse pide entrar al plan, solo (cantidad 1) o en bloque ("somos 2"). Con ingreso libre entra
// directo si hay lugar; si no, queda pendiente hasta que el organizador acepte. Devuelve el estado.
// diversidad: la persona confirma en este pedido que es parte de la diversidad (para un plan «Solo
// diversidades»); no se guarda.
func (s *Servicio) Sumarse(ctx context.Context, usuarioID, planID string, cantidad int16, mensaje string, diversidad bool) (string, error) {
	if cantidad == 0 {
		cantidad = 1
	}
	if cantidad < 1 || cantidad > 10 {
		return "", problema.Invalido("Se pueden sumar de 1 a 10 personas juntas.")
	}
	if len([]rune(mensaje)) > 200 {
		return "", problema.Invalido("El mensaje puede tener hasta 200 letras.")
	}
	var estado string
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var (
			org, ingreso, cobro, visib    string
			generoPlan                    string
			soloVerif, cancelado          bool
			empieza                       time.Time
			total                         int16
			edadMin, edadMax, edad        *int16
			selfie, dni, enGente, bloqueo bool
		)
		// FOR UPDATE: dos personas que se suman al último lugar a la vez no pueden entrar las dos.
		err := tx.QueryRow(ctx, `
			SELECT p.organizador_id, p.ingreso, p.solo_verificados, p.cancelado_en IS NOT NULL,
				p.empieza_en, p.total, p.edad_min, p.edad_max, p.cobro, p.visibilidad, p.genero
			FROM planes p WHERE p.id = $1 FOR UPDATE`, planID).
			Scan(&org, &ingreso, &soloVerif, &cancelado, &empieza, &total, &edadMin, &edadMax, &cobro, &visib, &generoPlan)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Ese plan no existe.")
		}
		if err != nil {
			return err
		}
		if err := tx.QueryRow(ctx, `
			SELECT u.selfie_en IS NOT NULL, u.dni_en IS NOT NULL, u.edad,
				EXISTS (SELECT 1 FROM mi_gente g WHERE g.usuario_id = $2 AND g.otro_id = u.id),
				EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = u.id AND b.bloqueado_id = $2)
					OR (b.usuario_id = $2 AND b.bloqueado_id = u.id))
			FROM usuarios u WHERE u.id = $1`, usuarioID, org).Scan(&selfie, &dni, &edad, &enGente, &bloqueo); err != nil {
			return err
		}
		switch {
		case org == usuarioID:
			return problema.Conflicto("es_tuyo", "Ya estás: es tu plan.")
		case cobro != "gratis":
			return problema.Conflicto("con_entrada", "A este plan se entra con entrada.")
		case cancelado:
			return problema.Conflicto("cancelado", "Este plan se canceló.")
		case bloqueo, visib == "mi_gente" && !enGente:
			return problema.NoEncontrado("Ese plan no existe.")
		case empieza.Add(ahoraAtras).Before(s.ahora()):
			return problema.Conflicto("paso", "Este plan ya arrancó.")
		case !selfie:
			return problema.NoProcesable("falta_selfie", "Antes de sumarte, verificá tu identidad con una selfie.")
		case soloVerif && !dni:
			return problema.NoProcesable("falta_dni", "Este plan es sólo para personas con DNI verificado.")
		case ingreso == "mi_gente" && !enGente:
			return problema.Conflicto("solo_mi_gente", "Este plan es sólo para la gente del organizador.")
		case edad != nil && ((edadMin != nil && *edad < *edadMin) || (edadMax != nil && *edad > *edadMax)):
			return problema.Conflicto("edad", "Este plan busca otro rango de edad.")
		}
		if err := ChequearGenero(ctx, tx, usuarioID, generoPlan, diversidad); err != nil {
			return err
		}
		var previo string
		err = tx.QueryRow(ctx, `SELECT estado FROM solicitudes WHERE plan_id = $1 AND usuario_id = $2`, planID, usuarioID).Scan(&previo)
		switch {
		case err == nil && (previo == "pendiente" || previo == "aceptada"):
			estado = previo
			return nil
		case err == nil && previo == "rechazada":
			return problema.Conflicto("rechazada", "Esta vez el organizador no te sumó.")
		case err != nil && !errors.Is(err, pgx.ErrNoRows):
			return err
		}
		estado = "pendiente"
		if ingreso == "libre" {
			var sumados int
			if err := tx.QueryRow(ctx, `SELECT coalesce(sum(cantidad), 0) FROM solicitudes WHERE plan_id = $1 AND estado = 'aceptada'`, planID).Scan(&sumados); err != nil {
				return err
			}
			if sumados+int(cantidad) > int(total) {
				return problema.Conflicto("completo", "No hay lugar para tantos: el plan se llenó.")
			}
			estado = "aceptada"
		}
		if _, err = tx.Exec(ctx, `
			INSERT INTO solicitudes (plan_id, usuario_id, cantidad, mensaje, estado, respondida_en)
			VALUES ($1, $2, $3, $4, $5, CASE WHEN $5 = 'aceptada' THEN now() END)
			ON CONFLICT (plan_id, usuario_id) DO UPDATE SET cantidad = excluded.cantidad,
				mensaje = excluded.mensaje, estado = excluded.estado, respondida_en = excluded.respondida_en,
				creada_en = now()`, planID, usuarioID, cantidad, mensaje, estado); err != nil {
			return err
		}
		if estado == "aceptada" {
			return completo(ctx, tx, planID)
		}
		return outbox.Publicar(ctx, tx, "solicitud.nueva", map[string]string{"planId": planID, "usuarioId": usuarioID})
	})
	return estado, err
}

// Salir cancela la solicitud propia (pendiente o aceptada).
func (s *Servicio) Salir(ctx context.Context, usuarioID, planID string) error {
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `
			UPDATE solicitudes s SET estado = 'cancelada', respondida_en = now()
			FROM planes p WHERE p.id = s.plan_id AND s.plan_id = $1 AND s.usuario_id = $2
				AND s.estado IN ('pendiente', 'aceptada') AND p.organizador_id <> $2`, planID, usuarioID)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problema.NoEncontrado("No estabas en ese plan.")
		}
		return nil
	})
}

// Solicitudes del plan, para el organizador: primero las pendientes.
func (s *Servicio) Solicitudes(ctx context.Context, usuarioID, planID string) ([]Solicitud, error) {
	out := []Solicitud{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var org string
		err := tx.QueryRow(ctx, `SELECT organizador_id FROM planes WHERE id = $1`, planID).Scan(&org)
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && org != usuarioID) {
			return problema.NoEncontrado("Ese plan no es tuyo.")
		}
		if err != nil {
			return err
		}
		rows, err := tx.Query(ctx, `
			SELECT s.id, s.cantidad, s.mensaje, s.estado, s.creada_en,
				u.id, u.nombre, coalesce(u.fotos[1], ''), u.edad, u.dni_en IS NOT NULL, rep.fue, rep.de,
				EXISTS (SELECT 1 FROM mi_gente g WHERE g.usuario_id = $2 AND g.otro_id = u.id)
			FROM solicitudes s JOIN usuarios u ON u.id = s.usuario_id
			LEFT JOIN LATERAL app.asistencia(u.id) rep ON true
			WHERE s.plan_id = $1 AND s.usuario_id <> $2 AND s.estado IN ('pendiente', 'aceptada')
			ORDER BY s.estado = 'pendiente' DESC, s.creada_en`, planID, usuarioID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var x Solicitud
			if err := rows.Scan(&x.ID, &x.Cantidad, &x.Mensaje, &x.Estado, &x.CreadaEn, &x.Persona.ID,
				&x.Persona.Nombre, &x.Persona.Foto, &x.Persona.Edad, &x.Persona.Verificada,
				&x.Persona.Asistencia.Fue, &x.Persona.Asistencia.De, &x.Persona.EnMiGente); err != nil {
				return err
			}
			out = append(out, x)
		}
		return rows.Err()
	})
	return out, err
}

// Responder acepta o rechaza una solicitud (sólo el organizador). Aceptar respeta el cupo.
// Devuelve el usuario de la solicitud, para avisarle.
func (s *Servicio) Responder(ctx context.Context, usuarioID, solicitudID string, aceptar bool) (string, error) {
	var persona string
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var planID, org, estado string
		var cantidad int16
		err := tx.QueryRow(ctx, `
			SELECT s.plan_id, p.organizador_id, s.estado, s.cantidad, s.usuario_id
			FROM solicitudes s JOIN planes p ON p.id = s.plan_id WHERE s.id = $1`, solicitudID).
			Scan(&planID, &org, &estado, &cantidad, &persona)
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && org != usuarioID) {
			return problema.NoEncontrado("Esa solicitud no es de un plan tuyo.")
		}
		if err != nil {
			return err
		}
		if estado != "pendiente" {
			return problema.Conflicto("respondida", "Esa solicitud ya no está pendiente.")
		}
		nuevo := "rechazada"
		if aceptar {
			var total int16
			var sumados int
			if err := tx.QueryRow(ctx, `SELECT total FROM planes WHERE id = $1 FOR UPDATE`, planID).Scan(&total); err != nil {
				return err
			}
			if err := tx.QueryRow(ctx, `SELECT coalesce(sum(cantidad), 0) FROM solicitudes WHERE plan_id = $1 AND estado = 'aceptada'`, planID).Scan(&sumados); err != nil {
				return err
			}
			if sumados+int(cantidad) > int(total) {
				return problema.Conflicto("completo", "No hay lugar: el plan ya está completo.")
			}
			nuevo = "aceptada"
		}
		if _, err = tx.Exec(ctx, `UPDATE solicitudes SET estado = $2, respondida_en = now() WHERE id = $1`, solicitudID, nuevo); err != nil {
			return err
		}
		if !aceptar {
			return outbox.Publicar(ctx, tx, "solicitud.rechazada", map[string]string{"planId": planID, "usuarioId": persona})
		}
		if err := outbox.Publicar(ctx, tx, "solicitud.aceptada", map[string]string{"planId": planID, "usuarioId": persona}); err != nil {
			return err
		}
		return completo(ctx, tx, planID)
	})
	return persona, err
}

// completo deja el mensaje de sistema en el chat cuando el plan se llena.
func completo(ctx context.Context, tx pgx.Tx, planID string) error {
	var lleno bool
	if err := tx.QueryRow(ctx, `SELECT p.total <= (SELECT coalesce(sum(cantidad), 0) FROM solicitudes
		WHERE plan_id = p.id AND estado = 'aceptada') FROM planes p WHERE p.id = $1`, planID).Scan(&lleno); err != nil || !lleno {
		return err
	}
	_, err := tx.Exec(ctx, `INSERT INTO mensajes (plan_id, texto) VALUES ($1, 'Plan completo. A disfrutar.')`, planID)
	return err
}
