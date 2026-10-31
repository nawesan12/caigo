package avisos

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
)

// Topes por persona y por día (de Argentina) de los avisos que no pidió.
const (
	TopeTuGente = 3
	TopeCerca   = 1
	// Plan cerca: ubicación de las últimas 6 h, a menos de 5 km, de un plan que empieza en < 3 h.
	cercaKm        = 5.0
	cercaUbicacion = 6 * time.Hour
	cercaEmpieza   = 3 * time.Hour
)

func (e *Enviador) ahora() time.Time {
	if e.Reloj != nil {
		return e.Reloj()
	}
	return time.Now()
}

// cuando: "Hoy 21:30", "Mañana 21:30" o "Vie 31/10 21:30" (hora de Argentina).
func cuando(t, ahora time.Time) string {
	t, ahora = plus.EnArgentina(t), plus.EnArgentina(ahora)
	hoy, _ := plus.Dia(ahora)
	hora := t.Format("15:04")
	switch d := t.Sub(hoy); {
	case d >= 0 && d < 24*time.Hour:
		return "Hoy " + hora
	case d >= 24*time.Hour && d < 48*time.Hour:
		return "Mañana " + hora
	}
	dias := []string{"Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"}
	return fmt.Sprintf("%s %s %s", dias[t.Weekday()], t.Format("02/01"), hora)
}

func datosPlan(planID, pantalla string) map[string]string {
	return map[string]string{"plan": planID, "pantalla": pantalla}
}

// planCorto lee lo que hace falta para escribir un aviso de un plan.
type planCorto struct {
	Titulo, Organizador, OrgNombre, Lugar string
	EmpiezaEn                             time.Time
}

func (e *Enviador) plan(ctx context.Context, planID string) (planCorto, error) {
	var p planCorto
	err := e.leer(ctx, `SELECT p.titulo, p.organizador_id, coalesce(l.nombre, u.nombre), coalesce(nullif(p.lugar_nombre, ''), p.zona), p.empieza_en
		FROM planes p JOIN usuarios u ON u.id = p.organizador_id LEFT JOIN lugares l ON l.id = p.lugar_id WHERE p.id::text = $1`,
		[]any{planID}, &p.Titulo, &p.Organizador, &p.OrgNombre, &p.Lugar, &p.EmpiezaEn)
	return p, err
}

// aVarios manda el mismo aviso a cada uno; junta los errores (el outbox reintenta todo el evento).
func (e *Enviador) aVarios(ctx context.Context, ids []string, a Aviso) error {
	var errs []error
	for _, id := range ids {
		errs = append(errs, e.Mandar(ctx, id, a))
	}
	return errors.Join(errs...)
}

func (e *Enviador) aceptados(ctx context.Context, planID, menos string) ([]string, error) {
	var ids []string
	err := e.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT usuario_id::text FROM solicitudes WHERE plan_id::text = $1 AND estado = 'aceptada'
			AND usuario_id::text <> $2`, planID, menos)
		if err != nil {
			return err
		}
		ids, err = pgx.CollectRows(rows, pgx.RowTo[string])
		return err
	})
	return ids, err
}

func (e *Enviador) planCancelado(ctx context.Context, ev outbox.Evento) error {
	var d struct {
		PlanID   string   `json:"planId"`
		Usuarios []string `json:"usuarios"`
		Motivo   string   `json:"motivo"`
	}
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	p, err := e.plan(ctx, d.PlanID)
	if err != nil {
		return ignorarSiNoEsta(err)
	}
	cuerpo := p.OrgNombre + " canceló el plan."
	if d.Motivo != "" {
		cuerpo = d.Motivo + "."
	}
	return e.aVarios(ctx, d.Usuarios, Aviso{Titulo: "Se canceló " + p.Titulo,
		Cuerpo: cuerpo + " Si pagaste una entrada, te devolvemos todo.",
		Datos:  datosPlan(d.PlanID, "detalle"), Colapso: "plan-" + d.PlanID})
}

func (e *Enviador) planCambiado(ctx context.Context, ev outbox.Evento) error {
	var d struct {
		PlanID string `json:"planId"`
	}
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	p, err := e.plan(ctx, d.PlanID)
	if err != nil {
		return ignorarSiNoEsta(err)
	}
	ids, err := e.aceptados(ctx, d.PlanID, p.Organizador)
	if err != nil {
		return err
	}
	return e.aVarios(ctx, ids, Aviso{Titulo: "Cambió " + p.Titulo,
		Cuerpo: "Ahora es " + cuando(p.EmpiezaEn, e.ahora()) + " · " + p.Lugar + ". Tocá para ver.",
		Datos:  datosPlan(d.PlanID, "detalle"), Colapso: "plan-" + d.PlanID})
}

func (e *Enviador) solicitudFuera(sacada bool) outbox.Manejador {
	return func(ctx context.Context, ev outbox.Evento) error {
		var d struct{ PlanID, UsuarioID string }
		if err := json.Unmarshal(ev.Datos, &d); err != nil {
			return nil
		}
		p, err := e.plan(ctx, d.PlanID)
		if err != nil {
			return ignorarSiNoEsta(err)
		}
		a := Aviso{Titulo: "Esta vez no", Cuerpo: p.OrgNombre + " no te sumó a " + p.Titulo + ". Hay más planes en Ahora.",
			Datos: map[string]string{"pantalla": "ahora"}}
		if sacada {
			a = Aviso{Titulo: "Ya no estás en " + p.Titulo, Cuerpo: p.OrgNombre + " te sacó del plan.",
				Datos: map[string]string{"pantalla": "ahora"}}
		}
		return e.Mandar(ctx, d.UsuarioID, a)
	}
}

func (e *Enviador) planRecordatorio(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PlanID, UsuarioID, Momento string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	p, err := e.plan(ctx, d.PlanID)
	if err != nil {
		return ignorarSiNoEsta(err)
	}
	if d.Momento == "checkin" {
		return e.Mandar(ctx, d.UsuarioID, Aviso{Titulo: "¿Llegaste? Hacé check-in", Cuerpo: p.Titulo + " · " + p.Lugar,
			Datos: datosPlan(d.PlanID, "checkin"), Colapso: "rec-" + d.PlanID})
	}
	return e.Mandar(ctx, d.UsuarioID, Aviso{Titulo: cuando(p.EmpiezaEn, e.ahora()) + ": " + p.Titulo + ". ¿Vas?",
		Cuerpo: p.Lugar + " · Tocá para ver el plan.", Datos: datosPlan(d.PlanID, "detalle"), Colapso: "rec-" + d.PlanID})
}

func (e *Enviador) planRepetimos(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PlanID, UsuarioID string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	p, err := e.plan(ctx, d.PlanID)
	if err != nil {
		return ignorarSiNoEsta(err)
	}
	return e.Mandar(ctx, d.UsuarioID, Aviso{Titulo: "¿Repetimos?", Cuerpo: "¿Cómo estuvo " + p.Titulo + "? Contanos y armamos el próximo.",
		Datos: datosPlan(d.PlanID, "repetimos"), Colapso: "rep-" + d.PlanID})
}

// planPublicado reparte un plan nuevo: a quienes tienen al organizador en su gente
// (plan.nuevo_de_tu_gente, máx. 3 por día) y a quienes están cerca con intereses en común si
// empieza en menos de 3 h (plan.cerca, máx. 1 por día). Respeta bloqueos, los avisos apagados del
// perfil y quién puede ver el plan. avisos_enviados lo hace idempotente: un reintento no repite.
func (e *Enviador) planPublicado(ctx context.Context, ev outbox.Evento) error {
	var d struct {
		PlanID string `json:"planId"`
	}
	if err := json.Unmarshal(ev.Datos, &d); err != nil || d.PlanID == "" {
		return nil
	}
	gente, cerca, err := Repartir(ctx, e.App, d.PlanID, e.ahora())
	if err != nil {
		return ignorarSiNoEsta(err)
	}
	if len(gente)+len(cerca) == 0 {
		return nil
	}
	p, err := e.plan(ctx, d.PlanID)
	if err != nil {
		return ignorarSiNoEsta(err)
	}
	cuandoEs := cuando(p.EmpiezaEn, e.ahora())
	// Ya quedaron anotados: un error de envío se registra pero no repite el reparto.
	if err := e.aVarios(ctx, gente, Aviso{Titulo: p.OrgNombre + " armó un plan", Cuerpo: p.Titulo + " · " + cuandoEs + ". ¿Te sumás?",
		Datos: datosPlan(d.PlanID, "detalle"), Colapso: "nuevo-" + d.PlanID}); err != nil {
		e.log().Warn("avisos: plan de tu gente", "plan", d.PlanID, "error", err)
	}
	if err := e.aVarios(ctx, cerca, Aviso{Titulo: "Cerca tuyo: " + p.Titulo, Cuerpo: cuandoEs + " · " + p.Lugar + ". ¿Caés?",
		Datos: datosPlan(d.PlanID, "detalle"), Colapso: "cerca-" + d.PlanID}); err != nil {
		e.log().Warn("avisos: plan cerca", "plan", d.PlanID, "error", err)
	}
	return nil
}

// Repartir anota (en avisos_enviados) a quiénes avisarles de un plan recién publicado y los
// devuelve: los de tu gente y los de cerca. Ver planPublicado.
func Repartir(ctx context.Context, app *db.Pool, planID string, ahora time.Time) (gente, cerca []string, err error) {
	desde, _ := plus.Dia(ahora)
	err = app.EnSistema(ctx, func(tx pgx.Tx) error {
		var org, visib, categoria string
		var empieza time.Time
		var cancelado bool
		var lat, lng float64
		var interesesOrg []string
		err := tx.QueryRow(ctx, `SELECT p.organizador_id::text, p.visibilidad, p.categoria, p.empieza_en, p.cancelado_en IS NOT NULL,
				p.lat, p.lng, u.intereses
			FROM planes p JOIN usuarios u ON u.id = p.organizador_id WHERE p.id::text = $1`, planID).
			Scan(&org, &visib, &categoria, &empieza, &cancelado, &lat, &lng, &interesesOrg)
		if err != nil {
			return err
		}
		if cancelado || visib == "link" || empieza.Before(ahora) {
			return nil
		}
		// Condiciones comunes a quien recibe ($1 plan, $2 organizador, $3 ahora, $4 inicio del día).
		const comun = `u.baja_en IS NULL AND u.suspendido_en IS NULL AND u.id::text <> $2
			AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = u.id AND b.bloqueado_id::text = $2)
				OR (b.usuario_id::text = $2 AND b.bloqueado_id = u.id))
			AND NOT EXISTS (SELECT 1 FROM solicitudes s WHERE s.plan_id = $1::uuid AND s.usuario_id = u.id)`
		rows, err := tx.Query(ctx, `
			INSERT INTO avisos_enviados (usuario_id, plan_id, tipo, enviado_en)
			SELECT u.id, $1::uuid, 'tu_gente', $3 FROM mi_gente g JOIN usuarios u ON u.id = g.usuario_id
			LEFT JOIN usuarios_privado up ON up.usuario_id = u.id
			WHERE g.otro_id::text = $2 AND coalesce(up.avisos_gente, true) AND `+comun+`
				AND ($5 <> 'mi_gente' OR EXISTS (SELECT 1 FROM mi_gente m WHERE m.usuario_id::text = $2 AND m.otro_id = u.id))
				AND (SELECT count(*) FROM avisos_enviados a WHERE a.usuario_id = u.id AND a.tipo = 'tu_gente' AND a.enviado_en >= $4) < $6
			ON CONFLICT DO NOTHING RETURNING usuario_id::text`, planID, org, ahora, desde, visib, TopeTuGente)
		if err != nil {
			return err
		}
		if gente, err = pgx.CollectRows(rows, pgx.RowTo[string]); err != nil {
			return err
		}
		if visib != "publico" || empieza.After(ahora.Add(cercaEmpieza)) {
			return nil
		}
		rows, err = tx.Query(ctx, `
			INSERT INTO avisos_enviados (usuario_id, plan_id, tipo, enviado_en)
			SELECT u.id, $1::uuid, 'cerca', $3 FROM ubicaciones ub JOIN usuarios u ON u.id = ub.usuario_id
			LEFT JOIN usuarios_privado up ON up.usuario_id = u.id
			WHERE ub.actualizada_en > $3::timestamptz - $5::interval AND coalesce(up.avisos_cerca, true) AND `+comun+`
				AND ub.lat BETWEEN $7 - $9 / 111.0 AND $7 + $9 / 111.0
				AND 12742 * asin(sqrt(power(sin(radians(ub.lat - $7) / 2), 2)
					+ cos(radians($7)) * cos(radians(ub.lat)) * power(sin(radians(ub.lng - $8) / 2), 2))) < $9
				AND EXISTS (SELECT 1 FROM unnest(u.intereses) i
					WHERE lower(i) = ANY (SELECT lower(x) FROM unnest($10::text[]) x))
				AND NOT EXISTS (SELECT 1 FROM avisos_enviados a WHERE a.usuario_id = u.id AND a.plan_id = $1::uuid)
				AND (SELECT count(*) FROM avisos_enviados a WHERE a.usuario_id = u.id AND a.tipo = 'cerca' AND a.enviado_en >= $4) < $6
			ON CONFLICT DO NOTHING RETURNING usuario_id::text`,
			planID, org, ahora, desde, cercaUbicacion.String(), TopeCerca, lat, lng, cercaKm,
			append(interesesOrg, categoria, planes.Categorias[categoria]))
		if err != nil {
			return err
		}
		cerca, err = pgx.CollectRows(rows, pgx.RowTo[string])
		return err
	})
	return gente, cerca, err
}

// Programados son los avisos que dependen de la hora: recordatorio 2 h antes, check-in abierto
// (30 min antes), "¿Repetimos?" al organizador 1 h después del final. Además borra las ubicaciones
// de más de 24 h. Corre cada minuto en el worker; avisos_enviados la hace idempotente, y cada
// aviso sale por el outbox (con reintentos).
type Programados struct {
	App   *db.Pool
	Reloj func() time.Time
}

// Pasada hace una vuelta. Devuelve cuántos avisos encoló.
func (p *Programados) Pasada(ctx context.Context) (int, error) {
	ahora := time.Now()
	if p.Reloj != nil {
		ahora = p.Reloj()
	}
	consultas := []string{
		// Recordatorio: 2 h antes, a los aceptados (no al organizador) que ya estaban adentro.
		`WITH n AS (
			INSERT INTO avisos_enviados (usuario_id, plan_id, tipo, enviado_en)
			SELECT s.usuario_id, p.id, 'recordatorio', $1 FROM planes p
			JOIN solicitudes s ON s.plan_id = p.id AND s.estado = 'aceptada' AND s.usuario_id <> p.organizador_id
			WHERE p.cancelado_en IS NULL AND p.empieza_en > $1::timestamptz + interval '30 minutes'
				AND p.empieza_en <= $1::timestamptz + interval '2 hours'
				AND coalesce(s.respondida_en, s.creada_en) < p.empieza_en - interval '2 hours'
			ON CONFLICT DO NOTHING RETURNING usuario_id, plan_id)
		INSERT INTO outbox (tipo, datos) SELECT 'plan.recordatorio',
			jsonb_build_object('planId', plan_id, 'usuarioId', usuario_id, 'momento', '2h') FROM n`,
		// Check-in abierto: 30 min antes, a todos los aceptados.
		`WITH n AS (
			INSERT INTO avisos_enviados (usuario_id, plan_id, tipo, enviado_en)
			SELECT s.usuario_id, p.id, 'checkin', $1 FROM planes p
			JOIN solicitudes s ON s.plan_id = p.id AND s.estado = 'aceptada' AND s.checkin_en IS NULL
			WHERE p.cancelado_en IS NULL AND p.empieza_en - interval '30 minutes' <= $1
				AND p.empieza_en + interval '30 minutes' > $1
			ON CONFLICT DO NOTHING RETURNING usuario_id, plan_id)
		INSERT INTO outbox (tipo, datos) SELECT 'plan.recordatorio',
			jsonb_build_object('planId', plan_id, 'usuarioId', usuario_id, 'momento', 'checkin') FROM n`,
		// ¿Repetimos?: al organizador, 1 h después del final (hasta 6 h después, por si el worker
		// estuvo parado). Los planes de lugares no.
		`WITH n AS (
			INSERT INTO avisos_enviados (usuario_id, plan_id, tipo, enviado_en)
			SELECT p.organizador_id, p.id, 'repetimos', $1 FROM planes p
			WHERE p.cancelado_en IS NULL AND p.lugar_id IS NULL
				AND p.empieza_en + make_interval(mins => p.duracion_min) + interval '1 hour' <= $1
				AND p.empieza_en + make_interval(mins => p.duracion_min) + interval '6 hours' > $1
			ON CONFLICT DO NOTHING RETURNING usuario_id, plan_id)
		INSERT INTO outbox (tipo, datos) SELECT 'plan.repetimos',
			jsonb_build_object('planId', plan_id, 'usuarioId', usuario_id) FROM n`,
	}
	total := 0
	var errs []error
	for _, q := range consultas {
		err := p.App.EnSistema(ctx, func(tx pgx.Tx) error {
			tag, err := tx.Exec(ctx, q, ahora)
			total += int(tag.RowsAffected())
			return err
		})
		errs = append(errs, err)
	}
	errs = append(errs, p.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `DELETE FROM ubicaciones WHERE actualizada_en < $1::timestamptz - interval '24 hours'`, ahora)
		return err
	}))
	return total, errors.Join(errs...)
}
