package planes

import (
	"context"
	"sort"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
)

// «Esta semana en <ciudad>»: el ritual de los lunes (informe del 18/11, B2). Lo que queda de la
// semana (hasta el domingo a la noche, en Argentina) en la ciudad de la persona: cuántos planes hay,
// cuántos en lugares aliados y de su gente, día por día, y los mejores primero.

// MinSemana: con menos planes que esto en la ciudad, el aviso del lunes no sale (mostraría el vacío).
const MinSemana = 5

// Semana es la tarjeta y la pantalla «Esta semana».
type Semana struct {
	Ciudad    string    `json:"ciudad"`
	Desde     time.Time `json:"desde"`
	Hasta     time.Time `json:"hasta"`
	Total     int       `json:"total"`
	Aliados   int       `json:"aliados"`
	DeTuGente int       `json:"deTuGente"`
	Dias      []DiaSem  `json:"dias"`
	// Lo mejor de la semana (hasta 8): de tu gente, destacados, en lugares aliados, que se repiten y a
	// los que les falta poco.
	Planes []Plan `json:"planes"`
}

// DiaSem: cuántos planes hay un día (fecha en Argentina, AAAA-MM-DD).
type DiaSem struct {
	Dia      string `json:"dia"`
	Cantidad int    `json:"cantidad"`
}

// FinDeSemana es el lunes siguiente a las 0 (Argentina): hasta ahí llega «esta semana».
func FinDeSemana(t time.Time) time.Time {
	hoy, _ := plus.Dia(t)
	dias := (8 - int(hoy.Weekday())) % 7
	if dias == 0 {
		dias = 7
	}
	return hoy.AddDate(0, 0, dias)
}

// Semana arma «Esta semana» para la persona. ciudad vacía: la de su perfil.
func (s *Servicio) Semana(ctx context.Context, usuarioID, ciudad string) (Semana, error) {
	ahora := s.ahora()
	sem := Semana{Ciudad: ciudad, Desde: ahora, Hasta: FinDeSemana(ahora), Dias: []DiaSem{}, Planes: []Plan{}}
	var gente map[string]bool
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if sem.Ciudad == "" {
			if err := tx.QueryRow(ctx, `SELECT ciudad FROM usuarios WHERE id = $1`, usuarioID).Scan(&sem.Ciudad); err != nil {
				return err
			}
		}
		rows, err := tx.Query(ctx, `SELECT otro_id::text FROM mi_gente WHERE usuario_id = $1`, usuarioID)
		if err != nil {
			return err
		}
		ids, err := pgx.CollectRows(rows, pgx.RowTo[string])
		gente = map[string]bool{}
		for _, id := range ids {
			gente[id] = true
		}
		return err
	})
	if err != nil || sem.Ciudad == "" {
		return sem, err
	}
	ps, err := s.listar(ctx, usuarioID, Filtro{Ciudad: sem.Ciudad, Desde: ahora, Hasta: sem.Hasta, Limite: 100})
	if err != nil {
		return sem, err
	}
	porDia := map[string]int{}
	for _, p := range ps {
		sem.Total++
		if p.Lugar != nil {
			sem.Aliados++
		}
		if gente[p.Organizador.ID] {
			sem.DeTuGente++
		}
		porDia[plus.EnArgentina(p.EmpiezaEn).Format(time.DateOnly)]++
	}
	for d := ahora; d.Before(sem.Hasta); d = d.AddDate(0, 0, 1) {
		k := plus.EnArgentina(d).Format(time.DateOnly)
		sem.Dias = append(sem.Dias, DiaSem{Dia: k, Cantidad: porDia[k]})
	}
	// Los mejores primero; a igual puntaje, el que viene antes.
	puntos := func(p Plan) int {
		n := 0
		if gente[p.Organizador.ID] {
			n += 8
		}
		if p.Destacado {
			n += 4
		}
		if p.Lugar != nil {
			n += 2
		}
		if p.Recurrente {
			n++
		}
		if p.Faltan > 0 && p.Faltan <= 2 {
			n++
		}
		return n
	}
	// Los propios y en los que ya está no son «para descubrir».
	candidatos := []Plan{}
	for _, p := range ps {
		if p.MiEstado == "ninguno" && p.Faltan > 0 {
			candidatos = append(candidatos, p)
		}
	}
	sort.SliceStable(candidatos, func(i, j int) bool { return puntos(candidatos[i]) > puntos(candidatos[j]) })
	if len(candidatos) > 8 {
		candidatos = candidatos[:8]
	}
	sem.Planes = candidatos
	return sem, nil
}

// ProgramadoSemana es la consulta del worker para el aviso de los lunes ($1 = ahora): los lunes desde
// las 18 (Argentina), a quien tiene ciudad, el aviso prendido y un teléfono registrado, si en su ciudad
// hay MinSemana planes públicos o más hasta el domingo. Uno por persona y por semana.
var ProgramadoSemana = `
WITH ahora AS (SELECT $1::timestamptz AS t, ($1::timestamptz AT TIME ZONE 'America/Argentina/Buenos_Aires') AS l),
lunes AS (SELECT l::date AS dia, ((l::date + 7)::timestamp AT TIME ZONE 'America/Argentina/Buenos_Aires') AS hasta
	FROM ahora WHERE extract(isodow FROM l) = 1 AND extract(hour FROM l) BETWEEN 18 AND 22),
ciudades AS (
	SELECT lower(p.ciudad) AS ciudad, count(*)::integer AS total, count(p.lugar_id)::integer AS aliados
	FROM planes p, ahora, lunes
	WHERE p.cancelado_en IS NULL AND p.visibilidad = 'publico' AND p.moderacion <> 'oculto'
		AND p.empieza_en BETWEEN ahora.t AND lunes.hasta
		AND EXISTS (SELECT 1 FROM usuarios o WHERE o.id = p.organizador_id AND o.suspendido_en IS NULL AND o.baja_en IS NULL)
	GROUP BY lower(p.ciudad) HAVING count(*) >= ` + strconv.Itoa(MinSemana) + `),
n AS (
	INSERT INTO avisos_semana (usuario_id, semana, enviado_en)
	SELECT u.id, lunes.dia, ahora.t FROM usuarios u JOIN ciudades c ON c.ciudad = lower(u.ciudad)
	LEFT JOIN usuarios_privado up ON up.usuario_id = u.id, lunes, ahora
	WHERE u.baja_en IS NULL AND u.suspendido_en IS NULL AND u.dni_en IS NOT NULL AND coalesce(up.avisos_semana, true)
		AND EXISTS (SELECT 1 FROM dispositivos d WHERE d.usuario_id = u.id)
	ON CONFLICT DO NOTHING RETURNING usuario_id)
INSERT INTO outbox (tipo, datos)
SELECT 'semana.aviso', jsonb_build_object('usuarioId', n.usuario_id, 'ciudad', u.ciudad, 'total', c.total, 'aliados', c.aliados)
FROM n JOIN usuarios u ON u.id = n.usuario_id JOIN ciudades c ON c.ciudad = lower(u.ciudad)`
