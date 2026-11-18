package planes

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/contacto"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// El día del plan (informe del 18/11): «¿Seguís yendo?» la víspera, la lista de espera de un plan
// completo, «¿Todo bien?» con un contacto de confianza y lo que ve cada uno de su lugar en el plan.

// Mio es lo de quien mira en un plan donde está (sólo con sesión).
type Mio struct {
	// Hizo check-in.
	Llegue bool `json:"llegue"`
	// Dijo «Voy» a «¿Seguís yendo?».
	Confirme bool `json:"confirme"`
	// La carita que le puso después (1 a 5). Sólo la ve quien la puso.
	Carita *int16 `json:"carita,omitempty"`
	// «¿Todo bien?» activo: avisamos a su contacto si no hace check-in a tiempo.
	TodoBien bool `json:"todoBien"`
	// Su lugar en la lista de espera (plan completo).
	Espera *EsperaMia `json:"espera,omitempty"`
	// Fotos del plan que puede ver (sólo si fue).
	Fotos int `json:"fotos"`
}

// EsperaMia: esperando (Posicion en la fila) o avisado (se liberó un lugar: tiene hasta VenceEn).
type EsperaMia struct {
	Estado   string     `json:"estado"`
	Posicion int        `json:"posicion"`
	VenceEn  *time.Time `json:"venceEn,omitempty"`
}

// Caritas: el promedio de las caritas que le pusieron a los planes que organizó (privadas una por una).
// Sólo se muestra con 3 o más.
type Caritas struct {
	Promedio float64 `json:"promedio"`
	Cantidad int     `json:"cantidad"`
}

// MinCaritas es cuántas hacen falta para mostrar el promedio (con menos, se adivina quién puso cuál).
const MinCaritas = 3

// Espera: «Se liberó un lugar» le da al primero de la fila 15 minutos para entrar.
const esperaPlazo = 15 * time.Minute

// EventoLugarLibre: al primero de la lista de espera, que se liberó un lugar.
const EventoLugarLibre = "espera.lugar"

// EventoCupo: quien organiza subió el cupo (o abrió el ingreso): se le ofrece el lugar a la lista de espera.
const EventoCupo = "plan.cupo"

// EventoTodoBienPregunta: llegó la hora y no hizo check-in. Primero se le pregunta a la persona
// («¿Llegaste?»): tiene TodoBienEspera para tocar «Estoy bien» antes de que le escribamos a su contacto.
const EventoTodoBienPregunta = "todo_bien.pregunta"

// EventoTodoBien: siguió sin check-in después de la pregunta: se avisa al contacto (o, si es un teléfono,
// a la persona para que le escriba).
const EventoTodoBien = "todo_bien.aviso"

// TodoBienEspera: el margen entre la pregunta a la persona y el aviso al contacto.
const TodoBienEspera = 10 * time.Minute

// completarMio suma lo de quien mira (Mio) y la reputación por caritas de quien organiza.
func completarMio(ctx context.Context, tx pgx.Tx, usuarioID string, ps []*Plan) error {
	if usuarioID == "" || usuarioID == nadie || len(ps) == 0 {
		return nil
	}
	ids := make([]string, len(ps))
	por := map[string]*Plan{}
	orgs := []string{}
	for i, p := range ps {
		ids[i] = p.ID
		por[p.ID] = p
		orgs = append(orgs, p.Organizador.ID)
	}
	rows, err := tx.Query(ctx, `
		SELECT p.id, s.checkin_en IS NOT NULL, s.confirmada_en IS NOT NULL, s.carita,
			EXISTS (SELECT 1 FROM todo_bien t WHERE t.plan_id = p.id AND t.usuario_id = $2),
			CASE WHEN `+fue+` THEN (SELECT count(*)::integer FROM fotos_plan f WHERE f.plan_id = p.id AND `+fotoVisible+`) ELSE 0 END
		FROM planes p JOIN solicitudes s ON s.plan_id = p.id AND s.usuario_id = $2 AND s.estado = 'aceptada'
		WHERE p.id = ANY ($1::uuid[])`, ids, usuarioID)
	if err != nil {
		return err
	}
	for rows.Next() {
		var id string
		m := Mio{}
		if err := rows.Scan(&id, &m.Llegue, &m.Confirme, &m.Carita, &m.TodoBien, &m.Fotos); err != nil {
			rows.Close()
			return err
		}
		por[id].Mio = &m
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return err
	}
	// La lista de espera: posición entre los que siguen esperando (o avisados).
	rows, err = tx.Query(ctx, `
		SELECT e.plan_id, e.estado, e.vence_en,
			(SELECT count(*)::integer FROM espera x WHERE x.plan_id = e.plan_id AND x.estado IN ('esperando', 'avisado')
				AND x.creada_en <= e.creada_en)
		FROM espera e WHERE e.plan_id = ANY ($1::uuid[]) AND e.usuario_id = $2 AND e.estado IN ('esperando', 'avisado')`, ids, usuarioID)
	if err != nil {
		return err
	}
	for rows.Next() {
		var id string
		var e EsperaMia
		if err := rows.Scan(&id, &e.Estado, &e.VenceEn, &e.Posicion); err != nil {
			rows.Close()
			return err
		}
		p := por[id]
		if p.Mio == nil {
			p.Mio = &Mio{}
		}
		p.Mio.Espera = &e
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return err
	}
	// Caritas de quien organiza: el promedio de lo que le pusieron los demás a sus planes.
	rows, err = tx.Query(ctx, `
		SELECT p.organizador_id, avg(s.carita)::float8, count(*)::integer
		FROM solicitudes s JOIN planes p ON p.id = s.plan_id
		WHERE p.organizador_id = ANY ($1::uuid[]) AND s.usuario_id <> p.organizador_id AND s.carita IS NOT NULL
		GROUP BY p.organizador_id HAVING count(*) >= $2`, orgs, MinCaritas)
	if err != nil {
		return err
	}
	cs := map[string]Caritas{}
	for rows.Next() {
		var org string
		var c Caritas
		if err := rows.Scan(&org, &c.Promedio, &c.Cantidad); err != nil {
			rows.Close()
			return err
		}
		c.Promedio = float64(int(c.Promedio*10+0.5)) / 10
		cs[org] = c
	}
	rows.Close()
	for _, p := range ps {
		if c, ok := cs[p.Organizador.ID]; ok {
			c := c
			p.Organizador.Caritas = &c
		}
	}
	return rows.Err()
}

// Confirmar es el «Voy» de «¿Seguís yendo?» (la víspera). «Me bajo» es salir del plan (libera el lugar).
func (s *Servicio) Confirmar(ctx context.Context, usuarioID, planID string) error {
	if !esUUID(planID) {
		return problema.NoEncontrado("No estás en ese plan.")
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `UPDATE solicitudes s SET confirmada_en = coalesce(s.confirmada_en, now())
			FROM planes p WHERE p.id = s.plan_id AND s.plan_id = $1 AND s.usuario_id = $2 AND s.estado = 'aceptada'
				AND p.cancelado_en IS NULL AND p.empieza_en + make_interval(mins => p.duracion_min) > $3`,
			planID, usuarioID, s.ahora())
		if err == nil && tag.RowsAffected() == 0 {
			return problema.NoEncontrado("No estás en ese plan.")
		}
		return err
	})
}

// ocupado es la cuenta del cupo tomado de un plan, la misma para sumarse, aceptar, la lista de espera
// y liberar: los aceptados más los lugares guardados para alguien de la lista que fue avisado y sigue a
// tiempo. plan es la expresión del id del plan, persona la de quien pide (su propio lugar guardado no le
// cuenta en contra; NULL si no hay nadie) y ahora la de la hora.
func ocupado(plan, persona, ahora string) string {
	return `((SELECT coalesce(sum(x.cantidad), 0)::integer FROM solicitudes x WHERE x.plan_id = ` + plan + ` AND x.estado = 'aceptada')
		+ (SELECT coalesce(sum(e.cantidad), 0)::integer FROM espera e WHERE e.plan_id = ` + plan + `
			AND e.estado = 'avisado' AND e.vence_en > ` + ahora + ` AND e.usuario_id IS DISTINCT FROM ` + persona + `))`
}

// Esperar anota a la persona en la lista de espera de un plan completo. Devuelve su posición.
func (s *Servicio) Esperar(ctx context.Context, usuarioID, planID string, cantidad int16, conf Confirma) (EsperaMia, error) {
	if cantidad == 0 {
		cantidad = 1
	}
	if cantidad < 1 || cantidad > 10 {
		return EsperaMia{}, problema.Invalido("Se pueden anotar de 1 a 10 personas juntas.")
	}
	if !esUUID(planID) {
		return EsperaMia{}, problema.NoEncontrado("Ese plan no existe.")
	}
	var out EsperaMia
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var (
			org, cobro, visib, generoPlan, estado string
			cancelado, grupos, activo             bool
			empieza                               time.Time
			total                                 int16
			libres                                int
			edadMin, edadMax, edad                *int16
			selfie, enGente, bloqueo              bool
		)
		err := tx.QueryRow(ctx, `
			SELECT p.organizador_id, p.cancelado_en IS NOT NULL, p.acepta_grupos, p.empieza_en, p.total, p.edad_min, p.edad_max,
				p.cobro, p.visibilidad, p.genero, `+organizaActivo+`,
				p.total - `+ocupado("p.id", "$2::uuid", "$3")+`,
				coalesce((SELECT x.estado FROM solicitudes x WHERE x.plan_id = p.id AND x.usuario_id = $2), '')
			FROM planes p WHERE p.id = $1`, planID, usuarioID, s.ahora()).
			Scan(&org, &cancelado, &grupos, &empieza, &total, &edadMin, &edadMax, &cobro, &visib, &generoPlan, &activo, &libres, &estado)
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
		case bloqueo, !activo, visib == "mi_gente" && !enGente:
			return problema.NoEncontrado("Ese plan no existe.")
		case cobro != "gratis":
			return problema.Conflicto("con_entrada", "A este plan se entra con entrada.")
		case cancelado:
			return problema.Conflicto("cancelado", "Este plan se canceló.")
		case !empieza.After(s.ahora()):
			return problema.Conflicto("paso", "Este plan ya arrancó.")
		case estado == "aceptada" || estado == "pendiente":
			return problema.Conflicto("ya_estas", "Ya estás en este plan.")
		case estado == "rechazada":
			return problema.Conflicto("rechazada", "Esta vez el organizador no te sumó.")
		case cantidad > 1 && !grupos:
			return problema.Conflicto("sin_grupos", "Este plan es de a una persona.")
		case libres >= int(cantidad):
			return problema.Conflicto("hay_lugar", "Hay lugar: sumate directo.")
		case int(cantidad) > int(total):
			return problema.Conflicto("completo", "No hay lugar para tantos.")
		}
		if err := Admitir(selfie, edad, edadMin, edadMax, generoPlan, conf); err != nil {
			return err
		}
		// Volver a anotarse (después de vencer o de entrar y bajarse) va al final de la fila.
		if _, err := tx.Exec(ctx, `INSERT INTO espera (plan_id, usuario_id, cantidad) VALUES ($1, $2, $3)
			ON CONFLICT (plan_id, usuario_id) DO UPDATE SET cantidad = excluded.cantidad,
				estado = CASE WHEN espera.estado IN ('esperando', 'avisado') THEN espera.estado ELSE 'esperando' END,
				creada_en = CASE WHEN espera.estado IN ('esperando', 'avisado') THEN espera.creada_en ELSE now() END,
				avisado_en = CASE WHEN espera.estado IN ('esperando', 'avisado') THEN espera.avisado_en END,
				vence_en = CASE WHEN espera.estado IN ('esperando', 'avisado') THEN espera.vence_en END`,
			planID, usuarioID, cantidad); err != nil {
			return err
		}
		return tx.QueryRow(ctx, `SELECT e.estado, e.vence_en, (SELECT count(*)::integer FROM espera x WHERE x.plan_id = e.plan_id
				AND x.estado IN ('esperando', 'avisado') AND x.creada_en <= e.creada_en)
			FROM espera e WHERE e.plan_id = $1 AND e.usuario_id = $2`, planID, usuarioID).Scan(&out.Estado, &out.VenceEn, &out.Posicion)
	})
	return out, err
}

// DejarDeEsperar saca a la persona de la lista. Si tenía un lugar guardado, pasa al siguiente.
func (s *Servicio) DejarDeEsperar(ctx context.Context, usuarioID, planID string) error {
	if !esUUID(planID) {
		return problema.NoEncontrado("No estabas en la lista.")
	}
	var avisado bool
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		err := tx.QueryRow(ctx, `DELETE FROM espera WHERE plan_id = $1 AND usuario_id = $2 RETURNING estado = 'avisado'`,
			planID, usuarioID).Scan(&avisado)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("No estabas en la lista.")
		}
		return err
	})
	if err != nil || !avisado {
		return err
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error { _, err := LiberarLugar(ctx, tx, planID, s.ahora()); return err })
}

// LiberarLugar (como sistema) vence los avisos de la lista que no se usaron a tiempo y, si hay lugar,
// le avisa al siguiente que entra (EventoLugarLibre), con 15 minutos para entrar. Devuelve a quiénes
// avisó. Es idempotente: un lugar ya guardado para alguien no se vuelve a ofrecer.
func LiberarLugar(ctx context.Context, tx pgx.Tx, planID string, ahora time.Time) ([]string, error) {
	var org, cobro string
	var empieza time.Time
	var cancelado, activo bool
	var libres int
	err := tx.QueryRow(ctx, `SELECT p.organizador_id, p.cobro, p.empieza_en, p.cancelado_en IS NOT NULL, `+organizaActivo+`
		FROM planes p WHERE p.id = $1 FOR UPDATE`, planID).Scan(&org, &cobro, &empieza, &cancelado, &activo)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	// Quien ya entró (por su cuenta o con el aviso) sale de la fila; los avisos sin usar vencen.
	if _, err := tx.Exec(ctx, `UPDATE espera e SET estado = 'entro' FROM solicitudes s
		WHERE e.plan_id = $1 AND s.plan_id = e.plan_id AND s.usuario_id = e.usuario_id
			AND s.estado IN ('pendiente', 'aceptada') AND e.estado IN ('esperando', 'avisado')`, planID); err != nil {
		return nil, err
	}
	if _, err := tx.Exec(ctx, `UPDATE espera SET estado = 'vencido' WHERE plan_id = $1 AND estado = 'avisado' AND vence_en <= $2`,
		planID, ahora); err != nil {
		return nil, err
	}
	if cancelado || !activo || cobro != "gratis" || !empieza.After(ahora) {
		return nil, nil
	}
	if err := tx.QueryRow(ctx, `SELECT p.total - `+ocupado("p.id", "NULL::uuid", "$2")+`
		FROM planes p WHERE p.id = $1`, planID, ahora).Scan(&libres); err != nil {
		return nil, err
	}
	var avisados []string
	for libres > 0 {
		var quien string
		var cantidad int
		err := tx.QueryRow(ctx, `SELECT e.usuario_id, e.cantidad FROM espera e
			JOIN usuarios u ON u.id = e.usuario_id AND u.baja_en IS NULL AND u.suspendido_en IS NULL
			WHERE e.plan_id = $1 AND e.estado = 'esperando' AND e.cantidad <= $3
				AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = e.usuario_id AND b.bloqueado_id = $2)
					OR (b.usuario_id = $2 AND b.bloqueado_id = e.usuario_id))
			ORDER BY e.creada_en LIMIT 1 FOR UPDATE OF e`, planID, org, libres).Scan(&quien, &cantidad)
		if errors.Is(err, pgx.ErrNoRows) {
			break
		}
		if err != nil {
			return nil, err
		}
		vence := ahora.Add(esperaPlazo)
		if vence.After(empieza) {
			vence = empieza
		}
		if _, err := tx.Exec(ctx, `UPDATE espera SET estado = 'avisado', avisado_en = $3, vence_en = $4
			WHERE plan_id = $1 AND usuario_id = $2`, planID, quien, ahora, vence); err != nil {
			return nil, err
		}
		if err := outbox.Publicar(ctx, tx, EventoLugarLibre, map[string]string{"planId": planID, "usuarioId": quien}); err != nil {
			return nil, err
		}
		avisados = append(avisados, quien)
		libres -= cantidad
	}
	return avisados, nil
}

// VencerEspera (como sistema, el worker): los planes con avisos de la lista que no se usaron a
// tiempo pasan el lugar al siguiente. De red, también los planes gratis por venir con gente esperando y
// lugar libre (si algo liberó un lugar sin pasar por LiberarLugar, la fila no se queda esperando).
func VencerEspera(ctx context.Context, tx pgx.Tx, ahora time.Time) (int, error) {
	rows, err := tx.Query(ctx, `SELECT DISTINCT plan_id::text FROM espera WHERE estado = 'avisado' AND vence_en <= $1
		UNION
		SELECT p.id::text FROM planes p
		WHERE p.cobro = 'gratis' AND p.cancelado_en IS NULL AND p.empieza_en > $1
			AND EXISTS (SELECT 1 FROM espera e WHERE e.plan_id = p.id AND e.estado = 'esperando')
			AND p.total - `+ocupado("p.id", "NULL::uuid", "$1")+` > 0`, ahora)
	if err != nil {
		return 0, err
	}
	ids, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return 0, err
	}
	n := 0
	for _, id := range ids {
		av, err := LiberarLugar(ctx, tx, id, ahora)
		if err != nil {
			return n, err
		}
		n += len(av)
	}
	return n, nil
}

// Contacto de confianza (perfil): nombre y teléfono o mail.
type Contacto struct {
	Nombre   string `json:"nombre"`
	Contacto string `json:"contacto"`
	// A quién le llega el aviso si no hay check-in: "contacto" (es un mail: le escribimos nosotros) o
	// "vos" (es un teléfono: no mandamos SMS, te avisamos a vos para que le escribas).
	Avisa string `json:"avisa"`
}

// EsMail: el contacto es un mail (le escribimos nosotros si no hay check-in).
func (c Contacto) EsMail() bool { return contacto.EsMail(c.Contacto) }

// conAvisa completa Avisa.
func (c Contacto) conAvisa() Contacto {
	c.Avisa = "vos"
	if c.EsMail() {
		c.Avisa = "contacto"
	}
	return c
}

// TodoBien es el estado de «¿Todo bien?» de quien mira en un plan.
type TodoBien struct {
	Activo   bool       `json:"activo"`
	Contacto *Contacto  `json:"contacto,omitempty"`
	AvisaEn  *time.Time `json:"avisaEn,omitempty"`
	Avisado  bool       `json:"avisado"`
	// Ya se le preguntó a la persona «¿Llegaste?» (el aviso al contacto sale TodoBienEspera después).
	Preguntado bool `json:"preguntado"`
	// Los contactos del perfil, para elegir a quién.
	Contactos []Contacto `json:"contactos"`
}

// «¿Todo bien?» se puede activar desde 3 h antes hasta el final del plan; avisa a la media hora
// del inicio (o media hora después de activarlo, si ya había empezado).
const (
	todoBienAntes  = 3 * time.Hour
	todoBienMargen = 30 * time.Minute
)

func (s *Servicio) estaEnPlan(ctx context.Context, tx pgx.Tx, usuarioID, planID string) (empieza, fin time.Time, llego bool, err error) {
	var dur int16
	err = tx.QueryRow(ctx, `SELECT p.empieza_en, p.duracion_min, s.checkin_en IS NOT NULL FROM planes p
		JOIN solicitudes s ON s.plan_id = p.id AND s.usuario_id = $2 AND s.estado = 'aceptada'
		WHERE p.id = $1 AND p.cancelado_en IS NULL`, planID, usuarioID).Scan(&empieza, &dur, &llego)
	if errors.Is(err, pgx.ErrNoRows) {
		err = problema.NoEncontrado("No estás en ese plan.")
	}
	return empieza, empieza.Add(time.Duration(dur) * time.Minute), llego, err
}

// Contactos lee los contactos de confianza del perfil (en la transacción de la persona).
func Contactos(ctx context.Context, tx pgx.Tx, usuarioID string) ([]Contacto, error) {
	var raw []byte
	err := tx.QueryRow(ctx, `SELECT contactos FROM usuarios_privado WHERE usuario_id = $1`, usuarioID).Scan(&raw)
	if errors.Is(err, pgx.ErrNoRows) {
		return []Contacto{}, nil
	}
	if err != nil {
		return nil, err
	}
	cs := []Contacto{}
	_ = json.Unmarshal(raw, &cs)
	// Los de antes de validar el largo (un «12») no sirven para avisar a nadie: no se ofrecen.
	out := cs[:0]
	for _, c := range cs {
		c.Contacto = strings.TrimSpace(c.Contacto)
		if n := len([]rune(c.Contacto)); n >= contacto.Min && n <= contacto.Max {
			out = append(out, c.conAvisa())
		}
	}
	return out, nil
}

// VerTodoBien devuelve el estado de «¿Todo bien?» y los contactos para elegir.
func (s *Servicio) VerTodoBien(ctx context.Context, usuarioID, planID string) (TodoBien, error) {
	if !esUUID(planID) {
		return TodoBien{}, problema.NoEncontrado("No estás en ese plan.")
	}
	var t TodoBien
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if _, _, _, err := s.estaEnPlan(ctx, tx, usuarioID, planID); err != nil {
			return err
		}
		var err error
		if t.Contactos, err = Contactos(ctx, tx, usuarioID); err != nil {
			return err
		}
		var c Contacto
		var avisa time.Time
		var avisado, preguntado bool
		err = tx.QueryRow(ctx, `SELECT contacto_nombre, contacto, avisa_en, avisado_en IS NOT NULL, preguntado_en IS NOT NULL
			FROM todo_bien WHERE plan_id = $1 AND usuario_id = $2`, planID, usuarioID).Scan(&c.Nombre, &c.Contacto, &avisa, &avisado, &preguntado)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		c = c.conAvisa()
		t.Activo, t.Contacto, t.AvisaEn, t.Avisado, t.Preguntado = true, &c, &avisa, avisado, preguntado
		return nil
	})
	return t, err
}

// ActivarTodoBien: «Avisale a mi contacto si no hago check-in». indice es el contacto del perfil.
func (s *Servicio) ActivarTodoBien(ctx context.Context, usuarioID, planID string, indice int) (TodoBien, error) {
	if !esUUID(planID) {
		return TodoBien{}, problema.NoEncontrado("No estás en ese plan.")
	}
	ahora := s.ahora()
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		empieza, fin, llego, err := s.estaEnPlan(ctx, tx, usuarioID, planID)
		if err != nil {
			return err
		}
		switch {
		case llego:
			return problema.Conflicto("llegaste", "Ya hiciste check-in.")
		case ahora.Before(empieza.Add(-todoBienAntes)):
			return problema.Conflicto("temprano", "Se activa el día del plan, desde 3 h antes.")
		case !ahora.Before(fin):
			return problema.Conflicto("paso", "El plan ya terminó.")
		}
		cs, err := Contactos(ctx, tx, usuarioID)
		if err != nil {
			return err
		}
		if len(cs) == 0 {
			return problema.NoProcesable("sin_contacto", "Sumá un contacto de confianza en tu perfil.")
		}
		if indice < 0 || indice >= len(cs) {
			return problema.Invalido("Elegí uno de tus contactos.")
		}
		// Un solo aviso al contacto por plan: después del aviso (o de un mail ya enviado, aunque haya tocado
		// «Estoy bien» y lo vuelva a activar) no se rearma. Si no, cada media hora salía otro mail.
		var yaAvisado bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM todo_bien WHERE plan_id = $1 AND usuario_id = $2 AND avisado_en IS NOT NULL)
			OR EXISTS (SELECT 1 FROM todo_bien_mails WHERE plan_id = $1 AND usuario_id = $2)`, planID, usuarioID).Scan(&yaAvisado); err != nil {
			return err
		}
		if yaAvisado {
			return problema.Conflicto("ya_avisamos", "Ya le avisamos a tu contacto en este plan.")
		}
		avisa := empieza.Add(todoBienMargen)
		if a := ahora.Add(todoBienMargen); a.After(avisa) {
			avisa = a
		}
		c := cs[indice]
		_, err = tx.Exec(ctx, `INSERT INTO todo_bien (plan_id, usuario_id, contacto_nombre, contacto, avisa_en) VALUES ($1, $2, $3, $4, $5)
			ON CONFLICT (plan_id, usuario_id) DO UPDATE SET contacto_nombre = excluded.contacto_nombre,
				contacto = excluded.contacto, avisa_en = excluded.avisa_en, preguntado_en = NULL`,
			planID, usuarioID, recortar(c.Nombre, 40), c.Contacto, avisa)
		return err
	})
	if err != nil {
		return TodoBien{}, err
	}
	return s.VerTodoBien(ctx, usuarioID, planID)
}

// ApagarTodoBien: «Estoy bien» (o lo desactiva). No se guarda nada más.
func (s *Servicio) ApagarTodoBien(ctx context.Context, usuarioID, planID string) error {
	if !esUUID(planID) {
		return nil
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `DELETE FROM todo_bien WHERE plan_id = $1 AND usuario_id = $2`, planID, usuarioID)
		return err
	})
}

// ProgramadosDia son las consultas del worker para el día del plan ($1 = ahora). Van con las de
// avisos.Programados: cada una anota y encola en la misma sentencia (idempotente).
var ProgramadosDia = []string{
	// «¿Todo bien?», primer paso: llegó la hora y no hizo check-in (ni lo apagó). Se le pregunta a la persona.
	`WITH n AS (
		UPDATE todo_bien t SET preguntado_en = $1 FROM solicitudes s, planes p
		WHERE s.plan_id = t.plan_id AND s.usuario_id = t.usuario_id AND p.id = t.plan_id
			AND t.preguntado_en IS NULL AND t.avisado_en IS NULL AND t.avisa_en <= $1 AND s.checkin_en IS NULL
			AND s.estado = 'aceptada' AND p.cancelado_en IS NULL AND t.avisa_en > $1::timestamptz - interval '6 hours'
		RETURNING t.plan_id, t.usuario_id)
	INSERT INTO outbox (tipo, datos) SELECT '` + EventoTodoBienPregunta + `', jsonb_build_object('planId', plan_id, 'usuarioId', usuario_id) FROM n`,
	// Segundo paso: TodoBienEspera después de la pregunta sigue sin check-in ni «Estoy bien»: se avisa.
	`WITH n AS (
		UPDATE todo_bien t SET avisado_en = $1 FROM solicitudes s, planes p
		WHERE s.plan_id = t.plan_id AND s.usuario_id = t.usuario_id AND p.id = t.plan_id
			AND t.avisado_en IS NULL AND t.preguntado_en <= $1::timestamptz - interval '10 minutes' AND s.checkin_en IS NULL
			AND s.estado = 'aceptada' AND p.cancelado_en IS NULL AND t.preguntado_en > $1::timestamptz - interval '6 hours'
		RETURNING t.plan_id, t.usuario_id)
	INSERT INTO outbox (tipo, datos) SELECT '` + EventoTodoBien + `', jsonb_build_object('planId', plan_id, 'usuarioId', usuario_id) FROM n`,
	// Privacidad: lo de «¿Todo bien?» se borra a las 24 h del final del plan, y enseguida si hizo
	// check-in o se fue del plan.
	`DELETE FROM todo_bien t USING planes p WHERE p.id = t.plan_id AND (
		p.empieza_en + make_interval(mins => p.duracion_min) < $1::timestamptz - interval '24 hours'
		OR p.cancelado_en IS NOT NULL
		OR NOT EXISTS (SELECT 1 FROM solicitudes s WHERE s.plan_id = t.plan_id AND s.usuario_id = t.usuario_id
			AND s.estado = 'aceptada' AND s.checkin_en IS NULL))`,
	// La lista de espera de un plan que ya pasó no sirve más.
	`DELETE FROM espera e USING planes p WHERE p.id = e.plan_id AND p.empieza_en < $1::timestamptz - interval '1 day'`,
}

// propositoBaja: el link de baja del mail de «¿Todo bien?» (lleva la huella del mail, no la dirección).
const propositoBaja = "baja-contacto"

// Topes del mail de «¿Todo bien?» por dirección, sumando todas las cuentas: nadie puede usar Caigo para
// llenarle la casilla a otro.
const (
	MailsPorDestino = 3
	MailsVentana    = 24 * time.Hour
)

// LinkBaja arma el link para que el contacto deje de recibir los mails de «¿Todo bien?».
func (s *Servicio) LinkBaja(mail string) string {
	if s.APIPublica == "" {
		return ""
	}
	tok := s.Firma.Firmar(propositoBaja, contacto.Huella(mail), s.ahora().AddDate(1, 0, 0))
	return strings.TrimRight(s.APIPublica, "/") + "/v1/publico/todo-bien/baja/" + tok
}

// Baja anota la dirección del link en la lista de supresión: no le llegan más mails de «¿Todo bien?».
func (s *Servicio) Baja(ctx context.Context, token string) error {
	huella, err := s.Firma.Verificar(propositoBaja, token, s.ahora())
	if err != nil || len(huella) != 64 {
		return problema.NoEncontrado("Este link no es válido.")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `INSERT INTO correo_supresion (destino) VALUES ($1) ON CONFLICT DO NOTHING`, huella)
		return err
	})
}

// ReservarMail (como sistema) decide si el mail de «¿Todo bien?» de este plan puede salir hacia mail y,
// si puede, lo anota (uno por plan y persona). false: la dirección pidió la baja, llegó al tope o ya salió.
func ReservarMail(ctx context.Context, tx pgx.Tx, planID, usuarioID, mail string, ahora time.Time) (bool, error) {
	h := contacto.Huella(mail)
	var bloqueado bool
	if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM correo_supresion WHERE destino = $1)
		OR (SELECT count(*) FROM todo_bien_mails WHERE destino = $1 AND enviado_en > $2) >= $3`,
		h, ahora.Add(-MailsVentana), MailsPorDestino).Scan(&bloqueado); err != nil || bloqueado {
		return false, err
	}
	tag, err := tx.Exec(ctx, `INSERT INTO todo_bien_mails (plan_id, usuario_id, destino, enviado_en) VALUES ($1, $2, $3, $4)
		ON CONFLICT DO NOTHING`, planID, usuarioID, h, ahora)
	return err == nil && tag.RowsAffected() == 1, err
}

// SoltarMail deshace ReservarMail si el envío falló (el reintento del outbox lo vuelve a probar).
func SoltarMail(ctx context.Context, tx pgx.Tx, planID, usuarioID string) error {
	_, err := tx.Exec(ctx, `DELETE FROM todo_bien_mails WHERE plan_id::text = $1 AND usuario_id::text = $2`, planID, usuarioID)
	return err
}
