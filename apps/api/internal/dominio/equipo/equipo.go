// Package equipo: el backoffice de Caigo (caigo.com.ar/equipo). Personas, planes, métricas y
// planes semilla. Corre como sistema; quién es del equipo lo decide la sesión (ExigirEquipo).
package equipo

import (
	"context"
	"regexp"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Servicio del backoffice.
type Servicio struct {
	App        *db.Pool
	Publicador *planes.Servicio
	Reloj      func() time.Time
}

func (s *Servicio) ahora() time.Time {
	if s.Reloj != nil {
		return s.Reloj()
	}
	return time.Now()
}

// patron arma el LIKE de una búsqueda (minúsculas, sin tildes, con comodines escapados).
func patron(q string) string {
	q = strings.NewReplacer(`\`, `\\`, "%", `\%`, "_", `\_`).Replace(strings.TrimSpace(q))
	return "%" + q + "%"
}

func sinTildes(expr string) string {
	return "translate(lower(" + expr + "), 'áéíóúüñ', 'aeiouun')"
}

// Usuario en el backoffice.
type Usuario struct {
	ID         string    `json:"id"`
	Nombre     string    `json:"nombre"`
	Foto       string    `json:"foto"`
	Ciudad     string    `json:"ciudad"`
	Creado     time.Time `json:"creado"`
	Verificada bool      `json:"verificada"`
	DNI        bool      `json:"dni"`
	Suspendido bool      `json:"suspendido"`
	Reportes   int       `json:"reportes"`
}

// Usuarios busca personas por nombre, ciudad o id (vacío: las últimas altas).
func (s *Servicio) Usuarios(ctx context.Context, q string) ([]Usuario, error) {
	out := []Usuario{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT u.id, u.nombre, coalesce(u.fotos[1], ''), u.ciudad, u.creado_en,
				u.selfie_en IS NOT NULL, u.dni_en IS NOT NULL, u.suspendido_en IS NOT NULL,
				(SELECT count(*) FROM reportes r WHERE r.sobre_usuario_id = u.id)::integer
			FROM usuarios u WHERE u.baja_en IS NULL AND ($1 = '' OR u.id::text = $1
				OR `+sinTildes("u.nombre")+` LIKE `+sinTildes("$2")+` OR `+sinTildes("u.ciudad")+` LIKE `+sinTildes("$2")+`)
			ORDER BY u.creado_en DESC LIMIT 100`, strings.TrimSpace(q), patron(q))
		if err != nil {
			return err
		}
		out, err = pgx.CollectRows(rows, pgx.RowToStructByPos[Usuario])
		if out == nil {
			out = []Usuario{}
		}
		return err
	})
	return out, err
}

// Suspender o reactivar a una persona. Suspendida, la API le contesta 403 en todo.
func (s *Servicio) Suspender(ctx context.Context, usuarioID string, suspender bool, motivo string) error {
	motivo = strings.TrimSpace(motivo)
	if suspender && motivo == "" {
		return problema.Invalido("Decí por qué se suspende.")
	}
	if len([]rune(motivo)) > 300 {
		return problema.Invalido("El motivo puede tener hasta 300 letras.")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		q := `UPDATE usuarios SET suspendido_en = now(), suspension_motivo = $2 WHERE id::text = $1 AND baja_en IS NULL AND NOT equipo`
		if !suspender {
			q = `UPDATE usuarios SET suspendido_en = NULL, suspension_motivo = left($2, 0) WHERE id::text = $1 AND baja_en IS NULL`
		}
		tag, err := tx.Exec(ctx, q, usuarioID, motivo)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problema.NoEncontrado("Esa persona no existe (o es del equipo).")
		}
		return nil
	})
}

// Plan en el backoffice.
type Plan struct {
	ID          string    `json:"id"`
	Slug        string    `json:"slug"`
	Titulo      string    `json:"titulo"`
	EmpiezaEn   time.Time `json:"empiezaEn"`
	Ciudad      string    `json:"ciudad"`
	Zona        string    `json:"zona"`
	Organizador struct {
		ID     string `json:"id"`
		Nombre string `json:"nombre"`
	} `json:"organizador"`
	Lugar     string `json:"lugar,omitempty"`
	Somos     int    `json:"somos"`
	Total     int16  `json:"total"`
	Cancelado bool   `json:"cancelado"`
	Reportes  int    `json:"reportes"`
}

// Planes: cual = proximos (los que no terminaron) | reportados (con reportes abiertos).
func (s *Servicio) Planes(ctx context.Context, q, cual string) ([]Plan, error) {
	cond := "p.cancelado_en IS NULL AND p.empieza_en + make_interval(mins => p.duracion_min) >= $3 ORDER BY p.empieza_en"
	switch cual {
	case "", "proximos":
	case "reportados":
		cond = "EXISTS (SELECT 1 FROM reportes r WHERE r.sobre_plan_id = p.id AND r.estado = 'abierto') ORDER BY p.empieza_en DESC"
	default:
		return nil, problema.Invalido("cual es proximos o reportados.")
	}
	out := []Plan{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT p.id, p.slug, p.titulo, p.empieza_en, p.ciudad, p.zona, o.id, o.nombre,
				coalesce(l.nombre, ''),
				coalesce((SELECT sum(cantidad) FROM solicitudes x WHERE x.plan_id = p.id AND x.estado = 'aceptada'), 0)::integer,
				p.total, p.cancelado_en IS NOT NULL,
				(SELECT count(*) FROM reportes r WHERE r.sobre_plan_id = p.id)::integer
			FROM planes p JOIN usuarios o ON o.id = p.organizador_id LEFT JOIN lugares l ON l.id = p.lugar_id
			WHERE ($1 = '' OR p.id::text = $1 OR p.slug = $1 OR `+sinTildes("p.titulo")+` LIKE `+sinTildes("$2")+`
				OR `+sinTildes("p.zona")+` LIKE `+sinTildes("$2")+` OR `+sinTildes("o.nombre")+` LIKE `+sinTildes("$2")+`)
				AND `+cond+` LIMIT 200`, strings.TrimSpace(q), patron(q), s.ahora())
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var p Plan
			if err := rows.Scan(&p.ID, &p.Slug, &p.Titulo, &p.EmpiezaEn, &p.Ciudad, &p.Zona, &p.Organizador.ID,
				&p.Organizador.Nombre, &p.Lugar, &p.Somos, &p.Total, &p.Cancelado, &p.Reportes); err != nil {
				return err
			}
			out = append(out, p)
		}
		return rows.Err()
	})
	return out, err
}

// CancelarPlan: el equipo cancela un plan como si fuera el organizador (avisa y devuelve).
func (s *Servicio) CancelarPlan(ctx context.Context, planID, motivo string) error {
	motivo = strings.TrimSpace(motivo)
	if motivo == "" {
		return problema.Invalido("Decí por qué se cancela.")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		ok, err := planes.CancelarTx(ctx, tx, planID, motivo)
		if err == nil && !ok {
			return problema.NoEncontrado("Ese plan no existe o ya está cancelado.")
		}
		return err
	})
}

// Semilla es el cuerpo de POST /v1/equipo/planes: un plan como POST /v1/planes, a nombre de otra
// persona (planes semilla de la web).
type Semilla struct {
	planes.Entrada
	OrganizadorID string `json:"organizadorId"`
}

// CrearSemilla publica un plan a nombre de organizadorId.
func (s *Servicio) CrearSemilla(ctx context.Context, in Semilla) (planes.Plan, error) {
	var existe bool
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM usuarios WHERE id::text = $1 AND baja_en IS NULL AND suspendido_en IS NULL)`,
			in.OrganizadorID).Scan(&existe)
	})
	if err != nil {
		return planes.Plan{}, err
	}
	if !existe {
		return planes.Plan{}, problema.Invalido("organizadorId no es una persona activa.")
	}
	return s.Publicador.Crear(ctx, in.OrganizadorID, in.Entrada)
}

// Semana de métricas (de lunes a domingo, hora de Argentina).
type Semana struct {
	Desde       string `json:"desde"` // YYYY-MM-DD
	Publicados  int    `json:"publicados"`
	Completados int    `json:"completados"`
	// Check-ins / aceptados de los planes que empezaron esa semana (nil: no hubo).
	Asistencia *float64 `json:"asistencia"`
	// Horas promedio desde que se publicó hasta que se llenó (planes completados).
	HorasHastaCompletar *float64 `json:"horasHastaCompletar"`
	// De quienes fueron a un plan esa semana, cuántos tuvieron otro en los 30 días siguientes.
	Repeticion30d        *float64       `json:"repeticion30d"`
	OrganizadoresActivos int            `json:"organizadoresActivos"`
	Altas                int            `json:"altas"`
	AltasPorOrigen       map[string]int `json:"altasPorOrigen"`
}

// Metricas de las últimas 8 semanas (la actual incluida), la más reciente primero.
func (s *Servicio) Metricas(ctx context.Context) ([]Semana, error) {
	out := []Semana{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `
			WITH sem AS (
				SELECT d::date AS dia, d AT TIME ZONE 'America/Argentina/Buenos_Aires' AS desde,
					(d + interval '1 week') AT TIME ZONE 'America/Argentina/Buenos_Aires' AS hasta
				FROM generate_series(date_trunc('week', $1::timestamptz AT TIME ZONE 'America/Argentina/Buenos_Aires') - interval '7 weeks',
					date_trunc('week', $1::timestamptz AT TIME ZONE 'America/Argentina/Buenos_Aires'), interval '1 week') d
			),
			jugados AS (
				SELECT p.id, p.creado_en, p.empieza_en, p.total,
					(SELECT coalesce(sum(cantidad), 0) FROM solicitudes x WHERE x.plan_id = p.id AND x.estado = 'aceptada') AS sumados,
					(SELECT max(coalesce(respondida_en, creada_en)) FROM solicitudes x WHERE x.plan_id = p.id AND x.estado = 'aceptada') AS lleno_en
				FROM planes p WHERE p.cancelado_en IS NULL AND p.empieza_en < $1
					AND p.empieza_en >= (SELECT min(desde) FROM sem)
			)
			SELECT sem.dia::text,
				(SELECT count(*) FROM planes p WHERE p.creado_en >= sem.desde AND p.creado_en < sem.hasta)::integer,
				(SELECT count(*) FROM jugados j WHERE j.empieza_en >= sem.desde AND j.empieza_en < sem.hasta AND j.sumados >= j.total)::integer,
				(SELECT count(s.checkin_en)::float8 / nullif(count(*), 0) FROM solicitudes s JOIN jugados j ON j.id = s.plan_id
					WHERE s.estado = 'aceptada' AND j.empieza_en >= sem.desde AND j.empieza_en < sem.hasta),
				(SELECT avg(extract(epoch FROM j.lleno_en - j.creado_en) / 3600)::float8 FROM jugados j
					WHERE j.empieza_en >= sem.desde AND j.empieza_en < sem.hasta AND j.sumados >= j.total),
				(SELECT count(DISTINCT s.usuario_id) FILTER (WHERE EXISTS (SELECT 1 FROM solicitudes s2 JOIN planes p2 ON p2.id = s2.plan_id
						WHERE s2.usuario_id = s.usuario_id AND s2.estado = 'aceptada' AND p2.cancelado_en IS NULL
							AND p2.empieza_en > j.empieza_en AND p2.empieza_en <= j.empieza_en + interval '30 days'))::float8
					/ nullif(count(DISTINCT s.usuario_id), 0)
					FROM solicitudes s JOIN jugados j ON j.id = s.plan_id
					WHERE s.estado = 'aceptada' AND j.empieza_en >= sem.desde AND j.empieza_en < sem.hasta),
				(SELECT count(DISTINCT p.organizador_id) FROM planes p WHERE p.lugar_id IS NULL
					AND p.creado_en >= sem.desde AND p.creado_en < sem.hasta)::integer,
				(SELECT coalesce(jsonb_object_agg(o, n), '{}'::jsonb) FROM (SELECT coalesce(nullif(u.origen, ''), 'directo') AS o,
					count(*)::integer AS n FROM usuarios u WHERE u.creado_en >= sem.desde AND u.creado_en < sem.hasta GROUP BY 1) a)
			FROM sem ORDER BY sem.dia DESC`, s.ahora())
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var x Semana
			if err := rows.Scan(&x.Desde, &x.Publicados, &x.Completados, &x.Asistencia, &x.HorasHastaCompletar,
				&x.Repeticion30d, &x.OrganizadoresActivos, &x.AltasPorOrigen); err != nil {
				return err
			}
			for _, n := range x.AltasPorOrigen {
				x.Altas += n
			}
			out = append(out, x)
		}
		return rows.Err()
	})
	return out, err
}

var reEmail = regexp.MustCompile(`^[^@\s]{1,100}@[^@\s]+\.[^@\s]{2,}$`)

// Esperar anota un mail en la lista de espera (landing). Un mail repetido no es error.
func (s *Servicio) Esperar(ctx context.Context, email, ciudad string) error {
	email, ciudad = strings.TrimSpace(email), strings.TrimSpace(ciudad)
	if len(email) > 200 || !reEmail.MatchString(email) {
		return problema.Invalido("Ese mail no parece válido.", problema.Campo{Ruta: "email", Mensaje: "Revisá el mail."})
	}
	if len([]rune(ciudad)) > 60 {
		return problema.Invalido("La ciudad es muy larga.")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `INSERT INTO lista_espera (email, ciudad) VALUES ($1, $2) ON CONFLICT DO NOTHING`, email, ciudad)
		return err
	})
}
