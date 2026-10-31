package lugares

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/wallet"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Reembolsador devuelve pagos de Mercado Pago (mercadopago.Config; en los tests, el mismo contra un
// servidor falso). centavos nil: el pago entero.
type Reembolsador interface {
	Reembolsar(ctx context.Context, token string, pagoID int64, centavos *int64, idem string) error
}

func (s *Servicio) reembolsador() Reembolsador {
	if s.Reembolsos != nil {
		return s.Reembolsos
	}
	return s.MP
}

// EditarPlan cambia un plan del lugar que no empezó (PATCH: lo que no viene queda igual). Si ya hay
// entradas pagas, no se puede tocar el cobro ni el precio. Si cambia el horario, avisa a los que van.
func (s *Servicio) EditarPlan(ctx context.Context, usuarioID, lugarID, planID string, cuerpo []byte) (PlanDelLugar, error) {
	ahora := s.ahora()
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		var p PlanLugar
		var precio *int32
		var sena *int16
		var soloVerif, cancelado bool
		var genero string
		err := tx.QueryRow(ctx, `SELECT titulo, categoria, descripcion, foto, empieza_en, duracion_min, total, acepta_grupos,
				recurrente, cobro, precio_centavos, sena_pct, incluye, cancelacion, beneficio, solo_verificados, genero,
				cancelado_en IS NOT NULL
			FROM planes WHERE id::text = $1 AND lugar_id::text = $2 FOR UPDATE`, planID, lugarID).Scan(&p.Titulo, &p.Categoria,
			&p.Descripcion, &p.Foto, &p.EmpiezaEn, &p.DuracionMin, &p.Cupo, &p.AceptaGrupos, &p.Recurrente, &p.Cobro, &precio, &sena,
			&p.Incluye, &p.Cancelacion, &p.Beneficio, &soloVerif, &genero, &cancelado)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Ese plan no es de tu lugar.")
		}
		if err != nil {
			return err
		}
		if precio != nil {
			p.PrecioCentavos = *precio
		}
		if sena != nil {
			p.SenaPct = *sena
		}
		p.Quien = "cualquiera"
		switch {
		case genero == "mujeres":
			p.Quien = "mujeres"
		case soloVerif:
			p.Quien = "verificados"
		}
		switch {
		case cancelado:
			return problema.Conflicto("cancelado", "Este plan está cancelado.")
		case !p.EmpiezaEn.After(ahora):
			return problema.Conflicto("empezo", "Este plan ya empezó: no se puede editar.")
		}
		orig := p
		if err := planes.Superponer(cuerpo, &p); err != nil {
			return err
		}
		p.Recurrente = orig.Recurrente
		if err := p.validar(ahora); err != nil {
			return err
		}
		var sumados, pagas int
		var mp bool
		if err := tx.QueryRow(ctx, `SELECT
				coalesce((SELECT sum(cantidad) FROM solicitudes WHERE plan_id::text = $1 AND estado = 'aceptada'), 0)::integer,
				(SELECT count(*) FROM entradas WHERE plan_id::text = $1 AND estado = 'pagada')::integer,
				EXISTS (SELECT 1 FROM mp_conexiones WHERE lugar_id::text = $2)`, planID, lugarID).Scan(&sumados, &pagas, &mp); err != nil {
			return err
		}
		if int(p.Cupo) < sumados {
			return problema.Conflicto("cupo_menor", "Ya hay más confirmados que ese cupo.")
		}
		if pagas > 0 && (p.Cobro != orig.Cobro || p.PrecioCentavos != orig.PrecioCentavos || p.SenaPct != orig.SenaPct) {
			return problema.Conflicto("con_entradas", "Ya hay entradas pagas: no se puede cambiar el precio ni cómo se cobra.")
		}
		if p.Cobro != "gratis" && !mp {
			return problema.NoProcesable("sin_mp", "Para cobrar entradas, conectá Mercado Pago en Cobros.")
		}
		genero = "cualquiera"
		if p.Quien == "mujeres" {
			genero = "mujeres"
		}
		if _, err := tx.Exec(ctx, `UPDATE planes SET titulo = $2, categoria = $3, descripcion = $4, foto = $5, empieza_en = $6,
				duracion_min = $7, total = $8, acepta_grupos = $9, cobro = $10, precio_centavos = $11, sena_pct = $12,
				incluye = $13, cancelacion = $14, beneficio = $15, solo_verificados = $16, genero = $17
			WHERE id::text = $1`, planID, p.Titulo, categoriasLugar[p.Categoria], p.Descripcion, p.Foto, p.EmpiezaEn,
			p.DuracionMin, p.Cupo, p.AceptaGrupos, p.Cobro, nulo(p.PrecioCentavos), nulo(p.SenaPct), p.Incluye, p.Cancelacion,
			p.Beneficio, p.Quien == "verificados", genero); err != nil {
			return err
		}
		if p.EmpiezaEn.Equal(orig.EmpiezaEn) && p.DuracionMin == orig.DuracionMin {
			return nil
		}
		return outbox.Publicar(ctx, tx, "plan.cambiado", map[string]string{"planId": planID})
	})
	if err != nil {
		return PlanDelLugar{}, err
	}
	ps, err := s.Planes(ctx, usuarioID, lugarID, "", planID)
	if err != nil || len(ps) == 0 {
		return PlanDelLugar{}, err
	}
	return ps[0], nil
}

// CancelarPlan cancela un plan del lugar: avisa y devuelve el 100 % de las entradas pagas.
func (s *Servicio) CancelarPlan(ctx context.Context, usuarioID, lugarID, planID string) error {
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		var es bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM planes WHERE id::text = $1 AND lugar_id::text = $2)`,
			planID, lugarID).Scan(&es); err != nil {
			return err
		}
		if !es {
			return problema.NoEncontrado("Ese plan no es de tu lugar.")
		}
		ok, err := planes.CancelarTx(ctx, tx, planID, "")
		if err == nil && !ok {
			return problema.NoEncontrado("Ese plan ya está cancelado.")
		}
		return err
	})
}

// Reembolso de una entrada que cancela quien la compró, según la política del plan: flex devuelve
// todo hasta 24 h antes, mod hasta 72 h antes y strict nada. El cargo por servicio no se devuelve.
func reembolsoPorPolitica(politica string, faltan time.Duration, lugarCentavos int64) (int64, error) {
	switch politica {
	case "strict":
		return 0, problema.Conflicto("sin_reembolso", "Este plan no tiene reembolso si cancelás.")
	case "mod":
		if faltan >= 72*time.Hour {
			return lugarCentavos, nil
		}
	default: // flex
		if faltan >= 24*time.Hour {
			return lugarCentavos, nil
		}
	}
	return 0, nil
}

// CancelarEntrada: quien compró cancela su entrada. Devuelve cuánto se le reembolsa (en centavos).
// Si hay plata para devolver, el reembolso sale en el momento por Mercado Pago.
func (s *Servicio) CancelarEntrada(ctx context.Context, usuarioID, entradaID string) (int64, error) {
	var monto int64
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var dueno, estado, politica, planID string
		var lugarC int64
		var empieza time.Time
		var cancelado bool
		err := tx.QueryRow(ctx, `SELECT e.usuario_id, e.estado, e.lugar_centavos, e.plan_id, p.empieza_en, p.cancelacion,
				p.cancelado_en IS NOT NULL
			FROM entradas e JOIN planes p ON p.id = e.plan_id WHERE e.id::text = $1 FOR UPDATE OF e`, entradaID).
			Scan(&dueno, &estado, &lugarC, &planID, &empieza, &politica, &cancelado)
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && dueno != usuarioID) {
			return problema.NoEncontrado("Esa entrada no es tuya.")
		}
		if err != nil {
			return err
		}
		switch {
		case estado != "pagada":
			return problema.Conflicto("no_pagada", "Esa entrada no está activa.")
		case cancelado:
			return problema.Conflicto("cancelado", "El plan se canceló: te devolvemos todo sin que hagas nada.")
		case !empieza.After(s.ahora()):
			return problema.Conflicto("paso", "El plan ya empezó.")
		}
		monto, err = reembolsoPorPolitica(politica, empieza.Sub(s.ahora()), lugarC)
		if err != nil || monto > 0 {
			return err
		}
		// Sin plata para devolver: se cancela acá y libera el lugar.
		return cerrarEntrada(ctx, tx, entradaID, planID, usuarioID, "cancelada", 0)
	})
	if err != nil || monto == 0 {
		return monto, err
	}
	return monto, s.reembolsar(ctx, entradaID, &monto)
}

// cerrarEntrada deja la entrada cancelada o reembolsada y saca a la persona del plan.
func cerrarEntrada(ctx context.Context, tx pgx.Tx, entradaID, planID, usuarioID, estado string, reembolso int64) error {
	if _, err := tx.Exec(ctx, `UPDATE entradas SET estado = $2, reembolso_centavos = $3,
			reembolsada_en = CASE WHEN $2 = 'reembolsada' THEN now() END, actualizada_en = now()
		WHERE id::text = $1 AND estado IN ('pagada', 'reembolsada')`, entradaID, estado, reembolso); err != nil {
		return err
	}
	if err := wallet.Actualizado(ctx, tx, "e-"+entradaID); err != nil {
		return err
	}
	_, err := tx.Exec(ctx, `UPDATE solicitudes SET estado = 'cancelada', respondida_en = now()
		WHERE plan_id::text = $1 AND usuario_id::text = $2 AND estado = 'aceptada'`, planID, usuarioID)
	return err
}

// reembolsar devuelve una entrada pagada por Mercado Pago con el token del lugar que cobró:
// centavos nil devuelve el pago entero (cargo incluido). Idempotente: una entrada que ya no está
// pagada no se toca, y Mercado Pago recibe siempre la misma clave de idempotencia.
func (s *Servicio) reembolsar(ctx context.Context, entradaID string, centavos *int64) error {
	var estado, lugarID, planID, usuarioID string
	var pago *int64
	var total int64
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `SELECT e.estado, p.lugar_id::text, e.plan_id::text, e.usuario_id::text, e.mp_pago,
				e.lugar_centavos + e.cargo_centavos
			FROM entradas e JOIN planes p ON p.id = e.plan_id WHERE e.id::text = $1`, entradaID).
			Scan(&estado, &lugarID, &planID, &usuarioID, &pago, &total)
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return nil
	}
	if err != nil || estado != "pagada" {
		return err
	}
	if pago == nil {
		return errors.New("lugares: entrada pagada sin pago de Mercado Pago")
	}
	token, err := s.tokenDe(ctx, lugarID)
	if err != nil {
		return err
	}
	idem := "reembolso-" + entradaID
	devuelto := total
	if centavos != nil {
		devuelto = *centavos
	}
	if err := s.reembolsador().Reembolsar(ctx, token, *pago, centavos, idem); err != nil {
		return err
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		return cerrarEntrada(ctx, tx, entradaID, planID, usuarioID, "reembolsada", devuelto)
	})
}

// CheckinPuerta: el lugar marca la llegada de alguien aceptado en un plan suyo (planes gratis, o el
// QR de check-in que muestra la app).
func (s *Servicio) CheckinPuerta(ctx context.Context, usuarioID, lugarID, planID, personaID string) (Validacion, error) {
	var v Validacion
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		var empieza time.Time
		var ya bool
		err := tx.QueryRow(ctx, `SELECT u.nombre, s.cantidad, p.titulo, p.beneficio, p.empieza_en, s.checkin_en IS NOT NULL
			FROM solicitudes s JOIN planes p ON p.id = s.plan_id JOIN usuarios u ON u.id = s.usuario_id
			WHERE s.plan_id::text = $1 AND p.lugar_id::text = $2 AND s.usuario_id::text = $3 AND s.estado = 'aceptada'
				AND p.cancelado_en IS NULL
			FOR UPDATE OF s`, planID, lugarID, personaID).Scan(&v.Nombre, &v.Cantidad, &v.Plan, &v.Beneficio, &empieza, &ya)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Esa persona no está en este plan de tu lugar.")
		}
		if err != nil {
			return err
		}
		if d := empieza.Sub(s.ahora()); d > 12*time.Hour || d < -24*time.Hour {
			return problema.Conflicto("otro_dia", "Ese plan es otro día.")
		}
		if ya {
			v.Repetida = true
			return nil
		}
		_, err = tx.Exec(ctx, `UPDATE solicitudes SET checkin_en = now(), checkin_metodo = 'puerta'
			WHERE plan_id::text = $1 AND usuario_id::text = $2`, planID, personaID)
		return err
	})
	return v, err
}

// validarQR: el código es el QR de check-in de una persona (GET /v1/planes/{id}/qr). ok false si no
// es uno de esos.
func (s *Servicio) validarQR(ctx context.Context, usuarioID, lugarID, codigo string) (Validacion, bool, error) {
	dato, err := s.Firma.Verificar(planes.PropositoAsiste, strings.TrimSpace(codigo), s.ahora())
	if err != nil {
		return Validacion{}, false, nil
	}
	plan, persona, ok := strings.Cut(dato, "~")
	if !ok {
		return Validacion{}, false, nil
	}
	v, err := s.CheckinPuerta(ctx, usuarioID, lugarID, plan, persona)
	return v, true, err
}

// Todos los lugares (equipo), filtrados por estado si viene.
func (s *Servicio) Todos(ctx context.Context, estado string) ([]Lugar, error) {
	switch estado {
	case "", "pendiente", "aprobado", "rechazado", "suspendido":
	default:
		return nil, problema.Invalido("El estado es pendiente, aprobado, rechazado o suspendido.")
	}
	out := []Lugar{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT `+columnas+desde+` WHERE ($2 = '' OR l.estado = $2)
			ORDER BY l.estado = 'pendiente' DESC, l.creado_en DESC LIMIT 500`, "00000000-0000-0000-0000-000000000000", estado)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			l, err := escanear(rows)
			if err != nil {
				return err
			}
			out = append(out, l)
		}
		return rows.Err()
	})
	return out, err
}

// Suspender o reactivar un lugar (equipo). Suspendido no puede publicar planes.
func (s *Servicio) Suspender(ctx context.Context, lugarID string, suspender bool, motivo string) error {
	motivo = strings.TrimSpace(motivo)
	if suspender && motivo == "" {
		return problema.Invalido("Decí por qué se suspende (lo ve el lugar).")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		desdeEstado, hacia := "aprobado", "suspendido"
		if !suspender {
			desdeEstado, hacia = "suspendido", "aprobado"
		}
		tag, err := tx.Exec(ctx, `UPDATE lugares SET estado = $3, motivo = $4 WHERE id::text = $1 AND estado = $2`,
			lugarID, desdeEstado, hacia, recortarTexto(motivo, 300))
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problema.NoEncontrado("Ese lugar no está " + desdeEstado + ".")
		}
		return nil
	})
}

func recortarTexto(s string, n int) string {
	r := []rune(s)
	if len(r) <= n {
		return s
	}
	return string(r[:n])
}
