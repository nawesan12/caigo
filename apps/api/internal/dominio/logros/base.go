package logros

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
)

// validan: los otros que llegaron al plan p y validan a $1 (cuenta con DNI, sin suspender y con la
// antigüedad mínima el día del plan). Sirve para quien fue y, con count, para quien organiza.
const validan = `
	SELECT x.usuario_id::text FROM solicitudes x JOIN usuarios ux ON ux.id = x.usuario_id
	WHERE x.plan_id = p.id AND x.usuario_id <> $1 AND x.estado = 'aceptada' AND x.checkin_en IS NOT NULL
		AND ux.dni_en IS NOT NULL AND ux.suspendido_en IS NULL AND ux.creado_en <= p.empieza_en - interval '7 days'`

// Asistidos lee los planes con check-in de la persona (los que no se cancelaron).
func Asistidos(ctx context.Context, tx pgx.Tx, usuarioID string) ([]Asistido, error) {
	rows, err := tx.Query(ctx, `
		SELECT p.id::text, p.empieza_en, p.categoria, coalesce(p.lugar_id::text, ''),
			p.lugar_id IS NOT NULL AND s.checkin_metodo IN ('puerta', 'nfc'), p.organizador_id = $1,
			array(`+validan+`)
		FROM solicitudes s JOIN planes p ON p.id = s.plan_id
		WHERE s.usuario_id = $1 AND s.estado = 'aceptada' AND s.checkin_en IS NOT NULL AND p.cancelado_en IS NULL
		ORDER BY p.empieza_en`, usuarioID)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (Asistido, error) {
		var a Asistido
		err := r.Scan(&a.PlanID, &a.Empieza, &a.Categoria, &a.LugarID, &a.PorLugar, &a.Organizo, &a.Validan)
		return a, err
	})
}

// Locales: los lugares donde la persona es «Local» (la que más días fue en 30, con LocalDias o más).
func Locales(ctx context.Context, tx pgx.Tx, usuarioID string, ahora time.Time) ([]LocalDe, error) {
	rows, err := tx.Query(ctx, `
		WITH v AS (
			SELECT p.lugar_id, s.usuario_id,
				count(DISTINCT (s.checkin_en AT TIME ZONE 'America/Argentina/Buenos_Aires')::date) AS dias
			FROM solicitudes s JOIN planes p ON p.id = s.plan_id
			WHERE p.lugar_id IS NOT NULL AND s.estado = 'aceptada' AND s.checkin_en > $2::timestamptz - interval '30 days'
				AND p.lugar_id IN (SELECT p2.lugar_id FROM solicitudes s2 JOIN planes p2 ON p2.id = s2.plan_id
					WHERE s2.usuario_id = $1 AND s2.checkin_en > $2::timestamptz - interval '30 days')
			GROUP BY 1, 2)
		SELECT l.id::text, l.nombre FROM v JOIN lugares l ON l.id = v.lugar_id
		WHERE v.usuario_id = $1 AND v.dias >= $3 AND v.dias >= (SELECT max(w.dias) FROM v w WHERE w.lugar_id = v.lugar_id)
		ORDER BY l.nombre`, usuarioID, ahora, LocalDias)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (LocalDe, error) {
		var l LocalDe
		err := r.Scan(&l.LugarID, &l.Nombre)
		return l, err
	})
}

// Anfitriones dice quiénes de ids son «Anfitrión».
func Anfitriones(ctx context.Context, tx pgx.Tx, ids []string, ahora time.Time) (map[string]bool, error) {
	out := map[string]bool{}
	if len(ids) == 0 {
		return out, nil
	}
	rows, err := tx.Query(ctx, `
		SELECT p.organizador_id::text, p.empieza_en, p.cancelado_en,
			EXISTS (SELECT 1 FROM solicitudes x WHERE x.plan_id = p.id AND x.checkin_en IS NOT NULL),
			(SELECT count(*) FROM solicitudes x JOIN usuarios ux ON ux.id = x.usuario_id
				WHERE x.plan_id = p.id AND x.usuario_id <> p.organizador_id AND x.estado = 'aceptada'
					AND x.checkin_en IS NOT NULL AND ux.dni_en IS NOT NULL AND ux.suspendido_en IS NULL
					AND ux.creado_en <= p.empieza_en - interval '7 days')::integer
		FROM planes p
		WHERE p.organizador_id = ANY($1::uuid[])
			AND (p.cancelado_en IS NOT NULL OR p.empieza_en + make_interval(mins => p.duracion_min) < $2)`, ids, ahora)
	if err != nil {
		return nil, err
	}
	por := map[string][]Organizado{}
	for rows.Next() {
		var id string
		var o Organizado
		if err := rows.Scan(&id, &o.Empieza, &o.CanceladoEn, &o.ConCheckin, &o.Ajenos); err != nil {
			rows.Close()
			return nil, err
		}
		por[id] = append(por[id], o)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return nil, err
	}
	for id, os := range por {
		if Anfitrion(os) {
			out[id] = true
		}
	}
	return out, nil
}

// Faltazos de los últimos 30 días de cada persona de ids: se sumó (no organizaba), el plan usó el
// check-in y no hizo el suyo ni se bajó. Devuelve sólo a quienes llegan a FaltazosAviso.
func Faltazos(ctx context.Context, tx pgx.Tx, ids []string, ahora time.Time) (map[string]int, error) {
	out := map[string]int{}
	if len(ids) == 0 {
		return out, nil
	}
	rows, err := tx.Query(ctx, `
		SELECT s.usuario_id::text, count(*)::integer FROM solicitudes s JOIN planes p ON p.id = s.plan_id
		WHERE s.usuario_id = ANY($1::uuid[]) AND s.estado = 'aceptada' AND s.checkin_en IS NULL
			AND p.organizador_id <> s.usuario_id AND p.cancelado_en IS NULL
			AND p.empieza_en + make_interval(mins => p.duracion_min) < $2
			AND p.empieza_en > $2::timestamptz - interval '30 days'
			AND EXISTS (SELECT 1 FROM solicitudes x WHERE x.plan_id = p.id AND x.checkin_en IS NOT NULL)
		GROUP BY 1 HAVING count(*) >= $3`, ids, ahora, FaltazosAviso)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var id string
		var n int
		if err := rows.Scan(&id, &n); err != nil {
			return nil, err
		}
		out[id] = n
	}
	return out, rows.Err()
}

// Logros como se muestran en un perfil. Racha y comodín sólo en el propio.
type Logros struct {
	Nivel string `json:"nivel"`
	// Planes que cuentan (con check-in validado).
	Planes    int    `json:"planes"`
	Siguiente string `json:"siguiente,omitempty"`
	Faltan    int    `json:"faltan,omitempty"`
	// Semanas seguidas con un plan (sólo para la persona).
	Racha *int `json:"racha,omitempty"`
	// El comodín de este mes está libre (sólo para la persona).
	Comodin   *bool      `json:"comodin,omitempty"`
	Insignias []Insignia `json:"insignias"`
	Anfitrion bool       `json:"anfitrion"`
	// La persona eligió ocultar sus insignias (sólo para ella).
	Ocultas bool `json:"ocultas,omitempty"`
}

// Calcular arma los logros de la persona. propio: los mira ella (con racha y todas las insignias);
// si no, nivel, «Anfitrión» y hasta InsigniasAjenas insignias (ninguna si las ocultó).
func Calcular(ctx context.Context, tx pgx.Tx, usuarioID string, ahora time.Time, propio bool) (Logros, error) {
	as, err := Asistidos(ctx, tx, usuarioID)
	if err != nil {
		return Logros{}, err
	}
	cuentan := Contar(as)
	l := Logros{Planes: len(cuentan)}
	l.Nivel, l.Siguiente, l.Faltan = Nivel(l.Planes)
	anf, err := Anfitriones(ctx, tx, []string{usuarioID}, ahora)
	if err != nil {
		return l, err
	}
	l.Anfitrion = anf[usuarioID]
	var ocultas bool
	if err := tx.QueryRow(ctx, `SELECT ocultar_insignias FROM usuarios WHERE id = $1`, usuarioID).Scan(&ocultas); err != nil {
		return l, err
	}
	locales, err := Locales(ctx, tx, usuarioID, ahora)
	if err != nil {
		return l, err
	}
	l.Insignias = Insignias(cuentan, locales, l.Anfitrion)
	if propio {
		fechas := make([]time.Time, len(cuentan))
		for i, a := range cuentan {
			fechas[i] = a.Empieza
		}
		r, c := Racha(fechas, ahora)
		l.Racha, l.Comodin, l.Ocultas = &r, &c, ocultas
		return l, nil
	}
	if ocultas {
		l.Insignias = []Insignia{}
	} else if len(l.Insignias) > InsigniasAjenas {
		l.Insignias = l.Insignias[:InsigniasAjenas]
	}
	return l, nil
}

// PlanesQueCuentan es Contar sobre lo que hay en la base (para los referidos y el nivel en la puerta).
func PlanesQueCuentan(ctx context.Context, tx pgx.Tx, usuarioID string) (int, error) {
	as, err := Asistidos(ctx, tx, usuarioID)
	return len(Contar(as)), err
}

// diaArgentina es la fecha de t en Argentina.
func diaArgentina(t time.Time) string { return t.In(argentina).Format(time.DateOnly) }

// BeneficioNivel: si el lugar tiene beneficio por nivel y la persona es Habitué o más, lo anota como
// canjeado hoy y lo devuelve ("" si no corresponde o ya lo usó hoy en ese lugar). Va en la misma
// transacción que el check-in en la puerta o en la placa.
func BeneficioNivel(ctx context.Context, tx pgx.Tx, lugarID, usuarioID string, ahora time.Time) (beneficio, nivel string, err error) {
	if err = tx.QueryRow(ctx, `SELECT beneficio_nivel FROM lugares WHERE id::text = $1`, lugarID).Scan(&beneficio); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return "", "", nil
		}
		return "", "", err
	}
	if beneficio == "" {
		return "", "", nil
	}
	n, err := PlanesQueCuentan(ctx, tx, usuarioID)
	if err != nil || n < Habitue {
		return "", "", err
	}
	nivel, _, _ = Nivel(n)
	tag, err := tx.Exec(ctx, `INSERT INTO canjes_nivel (lugar_id, usuario_id, dia, beneficio, nivel)
		VALUES ($1, $2, $3, $4, $5) ON CONFLICT DO NOTHING`, lugarID, usuarioID, diaArgentina(ahora), beneficio, nivel)
	if err != nil || tag.RowsAffected() == 0 {
		return "", "", err
	}
	return beneficio, nivel, nil
}
