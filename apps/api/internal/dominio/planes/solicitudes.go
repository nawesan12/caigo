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
// conf: lo que confirma para un plan «Solo mujeres» / «Solo diversidades» (no se guarda).
func (s *Servicio) Sumarse(ctx context.Context, usuarioID, planID string, cantidad int16, mensaje string, conf Confirma) (string, error) {
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
			org, ingreso, cobro, visib string
			generoPlan                 string
			cancelado, grupos, activo  bool
			empieza                    time.Time
			total                      int16
			edadMin, edadMax, edad     *int16
			selfie, enGente, bloqueo   bool
		)
		// FOR UPDATE: dos personas que se suman al último lugar a la vez no pueden entrar las dos.
		err := tx.QueryRow(ctx, `
			SELECT p.organizador_id, p.ingreso, p.cancelado_en IS NOT NULL, p.acepta_grupos,
				p.empieza_en, p.total, p.edad_min, p.edad_max, p.cobro, p.visibilidad, p.genero, `+organizaActivo+`
			FROM planes p WHERE p.id = $1 FOR UPDATE`, planID).
			Scan(&org, &ingreso, &cancelado, &grupos, &empieza, &total, &edadMin, &edadMax, &cobro, &visib, &generoPlan, &activo)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Ese plan no existe.")
		}
		if err != nil {
			return err
		}
		if err := tx.QueryRow(ctx, `
			SELECT u.selfie_en IS NOT NULL, u.edad,
				EXISTS (SELECT 1 FROM mi_gente g WHERE g.usuario_id = $2 AND g.otro_id = u.id),
				EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = u.id AND b.bloqueado_id = $2)
					OR (b.usuario_id = $2 AND b.bloqueado_id = u.id))
			FROM usuarios u WHERE u.id = $1`, usuarioID, org).Scan(&selfie, &edad, &enGente, &bloqueo); err != nil {
			return err
		}
		switch {
		case org == usuarioID:
			return problema.Conflicto("es_tuyo", "Ya estás: es tu plan.")
		case cobro != "gratis":
			return problema.Conflicto("con_entrada", "A este plan se entra con entrada.")
		case bloqueo, !activo, visib == "mi_gente" && !enGente:
			return problema.NoEncontrado("Ese plan no existe.")
		case cancelado:
			return problema.Conflicto("cancelado", "Este plan se canceló.")
		case empieza.Add(ahoraAtras).Before(s.ahora()):
			return problema.Conflicto("paso", "Este plan ya arrancó.")
		case cantidad > 1 && !grupos:
			return problema.Conflicto("sin_grupos", "Este plan es de a una persona.")
		}
		// Lo de la persona (selfie, edad, género) lo comparten sumarse, la entrada gratis de un lugar y
		// la invitación del organizador. El DNI lo pide la sesión (falta_dni): «sólo verificados» ya no
		// deja a nadie afuera.
		if err := Admitir(selfie, edad, edadMin, edadMax, generoPlan, conf); err != nil {
			return err
		}
		if ingreso == "mi_gente" && !enGente {
			return problema.Conflicto("solo_mi_gente", "Este plan es sólo para la gente del organizador.")
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
			// Los lugares guardados para alguien de la lista de espera (avisado y a tiempo) no son de nadie más.
			if err := tx.QueryRow(ctx, `SELECT (SELECT coalesce(sum(cantidad), 0)::integer FROM solicitudes WHERE plan_id = $1 AND estado = 'aceptada')
				+ (SELECT coalesce(sum(cantidad), 0)::integer FROM espera WHERE plan_id = $1 AND estado = 'avisado'
					AND vence_en > $3 AND usuario_id <> $2)`, planID, usuarioID, s.ahora()).Scan(&sumados); err != nil {
				return err
			}
			if sumados+int(cantidad) > int(total) {
				return problema.Conflicto("completo", "No hay lugar para tantos: el plan se llenó.")
			}
			estado = "aceptada"
		}
		// Si estaba en la lista de espera, ya entró (o pidió entrar).
		if _, err := tx.Exec(ctx, `UPDATE espera SET estado = 'entro' WHERE plan_id = $1 AND usuario_id = $2
			AND estado IN ('esperando', 'avisado')`, planID, usuarioID); err != nil {
			return err
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
			// Con ingreso libre no hay nada que responder, pero quien organiza se entera de que se sumó alguien.
			if err := outbox.Publicar(ctx, tx, "solicitud.sumada", map[string]string{"planId": planID, "usuarioId": usuarioID}); err != nil {
				return err
			}
			return completo(ctx, tx, planID)
		}
		return outbox.Publicar(ctx, tx, "solicitud.nueva", map[string]string{"planId": planID, "usuarioId": usuarioID})
	})
	return estado, err
}

// Salir cancela la solicitud propia (pendiente o aceptada). Con una entrada paga no: se sale
// cancelando la entrada (DELETE /v1/entradas/{id}), que aplica la política de reembolso.
func (s *Servicio) Salir(ctx context.Context, usuarioID, planID string) error {
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var pago bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM entradas WHERE plan_id::text = $1 AND usuario_id = $2
			AND estado = 'pagada')`, planID, usuarioID).Scan(&pago); err != nil {
			return err
		}
		if pago {
			return problema.Conflicto("con_entrada", "Para irte, cancelá tu entrada.")
		}
		// El estado de antes: si estaba adentro, se liberó un lugar y quien organiza se entera.
		var antes string
		var vigente bool
		err := tx.QueryRow(ctx, `
			SELECT s.estado, p.cancelado_en IS NULL AND p.empieza_en + make_interval(mins => p.duracion_min) > now()
			FROM solicitudes s JOIN planes p ON p.id = s.plan_id
			WHERE s.plan_id::text = $1 AND s.usuario_id = $2 AND s.estado IN ('pendiente', 'aceptada')
				AND p.organizador_id <> $2
			FOR UPDATE OF s`, planID, usuarioID).Scan(&antes, &vigente)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("No estabas en ese plan.")
		}
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE solicitudes SET estado = 'cancelada', respondida_en = now()
			WHERE plan_id::text = $1 AND usuario_id = $2`, planID, usuarioID); err != nil {
			return err
		}
		if antes != "aceptada" || !vigente {
			return nil
		}
		return outbox.Publicar(ctx, tx, "solicitud.salio", map[string]string{"planId": planID, "usuarioId": usuarioID})
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
				-- Una pendiente con un bloqueo de por medio (de cualquiera de los dos) no se muestra.
				AND (s.estado = 'aceptada' OR NOT EXISTS (SELECT 1 FROM bloqueos b
					WHERE (b.usuario_id = $2 AND b.bloqueado_id = u.id) OR (b.usuario_id = u.id AND b.bloqueado_id = $2)))
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
		var cancelado, bloqueo bool
		var fin time.Time
		err := tx.QueryRow(ctx, `
			SELECT s.plan_id, p.organizador_id, s.estado, s.cantidad, s.usuario_id, p.cancelado_en IS NOT NULL,
				p.empieza_en + make_interval(mins => p.duracion_min),
				EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = p.organizador_id AND b.bloqueado_id = s.usuario_id)
					OR (b.usuario_id = s.usuario_id AND b.bloqueado_id = p.organizador_id))
			FROM solicitudes s JOIN planes p ON p.id = s.plan_id WHERE s.id = $1`, solicitudID).
			Scan(&planID, &org, &estado, &cantidad, &persona, &cancelado, &fin, &bloqueo)
		// Con un bloqueo de por medio la solicitud no existe para el organizador (no se ve ni se acepta).
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && (org != usuarioID || bloqueo)) {
			return problema.NoEncontrado("Esa solicitud no es de un plan tuyo.")
		}
		if err != nil {
			return err
		}
		switch {
		case estado != "pendiente":
			return problema.Conflicto("respondida", "Esa solicitud ya no está pendiente.")
		case cancelado:
			return problema.Conflicto("cancelado", "Este plan se canceló.")
		case aceptar && !fin.After(s.ahora()):
			return problema.Conflicto("paso", "Este plan ya terminó.")
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
	// Una sola vez por plan: si alguien se baja y otro completa el lugar, el chat no repite el aviso.
	if err := tx.QueryRow(ctx, `SELECT p.total <= (SELECT coalesce(sum(cantidad), 0) FROM solicitudes
		WHERE plan_id = p.id AND estado = 'aceptada')
		AND NOT EXISTS (SELECT 1 FROM mensajes m WHERE m.plan_id = p.id AND m.usuario_id IS NULL AND m.texto = $2)
		FROM planes p WHERE p.id = $1`, planID, textoCompleto).Scan(&lleno); err != nil || !lleno {
		return err
	}
	_, err := tx.Exec(ctx, `INSERT INTO mensajes (plan_id, texto) VALUES ($1, $2)`, planID, textoCompleto)
	return err
}

// textoCompleto: el mensaje del sistema cuando el plan se llena.
const textoCompleto = "Plan completo. A disfrutar."

// organizaActivo (con p = planes): quien organiza no está suspendido ni dado de baja. Los planes de
// una cuenta suspendida no salen en las listas, no admiten sumarse y la recurrencia no los repite (si
// la reactivan, vuelven). Un plan oculto por la moderación tampoco: ni link público, ni sumarse, ni se repite.
const organizaActivo = `(p.moderacion <> 'oculto' AND EXISTS (SELECT 1 FROM usuarios oa WHERE oa.id = p.organizador_id
	AND oa.suspendido_en IS NULL AND oa.baja_en IS NULL))`

// OrganizaActivo es organizaActivo para los otros paquetes (entradas, invitaciones).
const OrganizaActivo = organizaActivo

// Admitir revisa lo de la persona para entrar a un plan: la selfie, el rango de edad (sin edad
// cargada no pasa un plan con rango) y el género que confirma. El DNI ya lo pidió la sesión.
func Admitir(selfie bool, edad, edadMin, edadMax *int16, generoPlan string, conf Confirma) error {
	switch {
	case !selfie:
		return problema.NoProcesable("falta_selfie", "Antes de sumarte, verificá tu identidad con una selfie.")
	case edad == nil && (edadMin != nil || edadMax != nil):
		return problema.NoProcesable("falta_edad", "Este plan tiene un rango de edad: cargá tu fecha de nacimiento.")
	case edad != nil && ((edadMin != nil && *edad < *edadMin) || (edadMax != nil && *edad > *edadMax)):
		return problema.Conflicto("edad", "Este plan busca otro rango de edad.")
	}
	return ChequearGenero(generoPlan, conf)
}
