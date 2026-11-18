// Package chat: el chat grupal de cada plan (organizador y aceptados) y los privados entre
// personas que compartieron un plan o son "mi gente". El tiempo real va por WebSocket (tiempo_real.go).
package chat

import (
	"context"
	"errors"
	"regexp"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/moderacion"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Archivo: el chat del plan queda en sólo lectura estos días después del encuentro.
const Archivo = 3 * 24 * time.Hour

// Servicio de chat.
type Servicio struct {
	App   *db.Pool
	Reloj func() time.Time
	// Moderación (para anotar en la cola lo que el filtro frena y el equipo tiene que ver). nil: igual
	// se filtra; sólo no se anota.
	Moderacion *moderacion.Servicio
}

func (s *Servicio) ahora() time.Time {
	if s.Reloj != nil {
		return s.Reloj()
	}
	return time.Now()
}

// Autor de un mensaje.
type Autor struct {
	ID     string `json:"id"`
	Nombre string `json:"nombre"`
	Foto   string `json:"foto,omitempty"`
}

// Mensaje del chat de un plan. Sin autor: es del sistema.
type Mensaje struct {
	ID       int64     `json:"id"`
	PlanID   string    `json:"planId"`
	Autor    *Autor    `json:"autor,omitempty"`
	Texto    string    `json:"texto"`
	CreadoEn time.Time `json:"creadoEn"`
	// Sólo en los del autor: "" | revision | oculto (los ocultos ajenos no se mandan).
	Moderacion string `json:"moderacion,omitempty"`
}

// Privado entre dos personas.
type Privado struct {
	ID       int64      `json:"id"`
	DeID     string     `json:"deId"`
	AID      string     `json:"aId"`
	Texto    string     `json:"texto"`
	CreadoEn time.Time  `json:"creadoEn"`
	LeidoEn  *time.Time `json:"leidoEn,omitempty"`
	// Como en Mensaje: sólo en los del autor.
	Moderacion string `json:"moderacion,omitempty"`
}

// estadoPara es la moderación como la ve quien mira: el autor ve «revision» u «oculto»; el resto, nada
// (los ocultos ajenos ni siquiera le llegan). Lo pendiente no se muestra: son segundos.
func estadoPara(autor, quien, estado string) string {
	if autor == "" || autor != quien {
		return ""
	}
	switch estado {
	case moderacion.EstadoRevisar:
		return "revision"
	case moderacion.EstadoOculto:
		return "oculto"
	}
	return ""
}

// columnaEstado: la moderación de m como la ve $quien (en SQL, para las listas).
func columnaEstado(autor, estado, quien string) string {
	return `CASE WHEN ` + autor + ` = ` + quien + ` THEN CASE ` + estado + ` WHEN 'revisar' THEN 'revision'
		WHEN 'oculto' THEN 'oculto' ELSE '' END ELSE '' END`
}

// filtrar pasa el texto por el filtro propio. Lo bloqueado no sale (422); lo dudoso sale y lo revisa la IA.
func (s *Servicio) filtrar(ctx context.Context, usuarioID, tipo, planID, texto string) (moderacion.Veredicto, error) {
	v := moderacion.Filtrar(texto)
	if v.Decision == moderacion.Bloquear {
		s.Moderacion.AnotarBloqueo(ctx, usuarioID, tipo, planID, texto, v)
		return v, moderacion.Bloqueado(tipo, v.Motivo)
	}
	return v, nil
}

func limpiar(texto string) (string, error) {
	texto = strings.TrimSpace(texto)
	if texto == "" {
		return "", problema.Invalido("Escribí algo.")
	}
	if len([]rune(texto)) > 2000 {
		return "", problema.Invalido("El mensaje puede tener hasta 2000 letras.")
	}
	return texto, nil
}

// participa dice si la persona está en el plan (organizador o aceptada) y si el chat sigue abierto.
// Si hay un bloqueo entre la persona y quien organiza, no está (bloquear a un aceptado lo saca del
// plan, y esto cubre el rato de antes o un plan de otro).
func participa(ctx context.Context, tx pgx.Tx, planID, usuarioID string, ahora time.Time) (esta, abierto bool, err error) {
	var fin time.Time
	err = tx.QueryRow(ctx, `
		SELECT p.empieza_en + make_interval(mins => p.duracion_min),
			EXISTS (SELECT 1 FROM solicitudes s WHERE s.plan_id = p.id AND s.usuario_id = $2 AND s.estado = 'aceptada')
			AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = $2 AND b.bloqueado_id = p.organizador_id)
				OR (b.usuario_id = p.organizador_id AND b.bloqueado_id = $2))
		FROM planes p WHERE p.id::text = $1`, planID, usuarioID).Scan(&fin, &esta)
	if errors.Is(err, pgx.ErrNoRows) {
		return false, false, nil
	}
	return esta, ahora.Before(fin.Add(Archivo)), err
}

// Mensajes del plan, del más viejo al más nuevo. antes > 0 pagina hacia atrás.
func (s *Servicio) Mensajes(ctx context.Context, usuarioID, planID string, antes int64) ([]Mensaje, error) {
	out := []Mensaje{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		esta, _, err := participa(ctx, tx, planID, usuarioID, s.ahora())
		if err != nil {
			return err
		}
		if !esta {
			return problema.SinPermiso("El chat es para quienes están en el plan.")
		}
		if antes <= 0 {
			antes = 1<<62 - 1
		}
		rows, err := tx.Query(ctx, `
			SELECT * FROM (
				SELECT m.id, m.plan_id, m.usuario_id, coalesce(u.nombre, ''), coalesce(u.fotos[1], ''), m.texto, m.creado_en,
					`+columnaEstado("m.usuario_id", "m.moderacion", "$3")+`
				FROM mensajes m LEFT JOIN usuarios u ON u.id = m.usuario_id
				WHERE m.plan_id = $1 AND m.id < $2 AND NOT `+bloqueados("m.usuario_id", "$3")+`
					AND (m.moderacion <> 'oculto' OR m.usuario_id = $3)
				ORDER BY m.id DESC LIMIT 100
			) x ORDER BY id`, planID, antes, usuarioID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			m, err := escanearMensaje(rows)
			if err != nil {
				return err
			}
			out = append(out, m)
		}
		return rows.Err()
	})
	return out, err
}

func escanearMensaje(row pgx.Row) (Mensaje, error) {
	var m Mensaje
	var autorID *string
	var nombre, foto string
	err := row.Scan(&m.ID, &m.PlanID, &autorID, &nombre, &foto, &m.Texto, &m.CreadoEn, &m.Moderacion)
	if autorID != nil {
		m.Autor = &Autor{ID: *autorID, Nombre: nombre, Foto: foto}
	}
	return m, err
}

// Enviar manda un mensaje al chat del plan.
func (s *Servicio) Enviar(ctx context.Context, usuarioID, planID, texto string) (Mensaje, error) {
	texto, err := limpiar(texto)
	if err != nil {
		return Mensaje{}, err
	}
	var plan *string
	if esUUID(planID) {
		plan = &planID
	}
	v, err := s.filtrar(ctx, usuarioID, moderacion.TipoMensaje, deref(plan), texto)
	if err != nil {
		return Mensaje{}, err
	}
	var m Mensaje
	err = s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		esta, abierto, err := participa(ctx, tx, planID, usuarioID, s.ahora())
		if err != nil {
			return err
		}
		if !esta {
			return problema.SinPermiso("El chat es para quienes están en el plan.")
		}
		if !abierto {
			return problema.Conflicto("archivado", "Este chat ya se archivó.")
		}
		m, err = escanearMensaje(tx.QueryRow(ctx, `
			WITH n AS (INSERT INTO mensajes (plan_id, usuario_id, texto, moderacion) VALUES ($1, $2, $3, $4) RETURNING *)
			SELECT n.id, n.plan_id, n.usuario_id, u.nombre, coalesce(u.fotos[1], ''), n.texto, n.creado_en, ''
			FROM n JOIN usuarios u ON u.id = n.usuario_id`, planID, usuarioID, texto, moderacion.EstadoMensaje(v)))
		if err != nil {
			return err
		}
		if v.Decision == moderacion.Dudoso {
			if err := moderacion.PublicarRevision(ctx, tx, moderacion.TipoMensaje, m.ID, v); err != nil {
				return err
			}
		}
		return outbox.Publicar(ctx, tx, "mensaje.plan", map[string]any{"mensajeId": m.ID, "planId": planID})
	})
	return m, err
}

// bloqueados es la condición «hay un bloqueo entre a y b» (en cualquier sentido). En el chat grupal
// dos personas bloqueadas pueden seguir en el mismo plan (el bloqueo sólo saca a quien bloquea el
// organizador): el grupo sigue igual para el resto, pero entre ellas no se ven los mensajes ni el
// «escribiendo». Con a NULL (mensaje del sistema) da falso.
func bloqueados(a, b string) string {
	return `EXISTS (SELECT 1 FROM bloqueos x WHERE (x.usuario_id = ` + a + ` AND x.bloqueado_id = ` + b + `)
		OR (x.usuario_id = ` + b + ` AND x.bloqueado_id = ` + a + `))`
}

// Sistema deja un mensaje del sistema en el chat del plan ("Plan completo. A disfrutar.").
func Sistema(ctx context.Context, tx pgx.Tx, planID, texto string) error {
	_, err := tx.Exec(ctx, `INSERT INTO mensajes (plan_id, texto) VALUES ($1, $2)`, planID, texto)
	return err
}

// puedenHablar: comparten un plan que viene o que terminó hace menos de un día (para coordinar), se
// tienen los dos en su gente (con «Me prendo» o con «¿Volverías?» alcanza con sumar a alguien: tiene que
// ser de ida y vuelta, si no cualquiera le escribe a cualquiera) o en Citas el interés es mutuo; y no
// hay bloqueo. Después del plan, el privado sigue sólo si es mutuo (como Timeleft, informe del 18/11).
func puedenHablar(ctx context.Context, tx pgx.Tx, a, b string) (bool, error) {
	var ok bool
	err := tx.QueryRow(ctx, `
		SELECT NOT EXISTS (SELECT 1 FROM bloqueos x WHERE (x.usuario_id = $1 AND x.bloqueado_id::text = $2)
				OR (x.usuario_id::text = $2 AND x.bloqueado_id = $1))
			AND ((EXISTS (SELECT 1 FROM mi_gente g WHERE g.usuario_id = $1 AND g.otro_id::text = $2)
				AND EXISTS (SELECT 1 FROM mi_gente g WHERE g.usuario_id::text = $2 AND g.otro_id = $1))
			OR EXISTS (SELECT 1 FROM solicitudes x JOIN solicitudes y ON x.plan_id = y.plan_id JOIN planes p ON p.id = x.plan_id
				WHERE x.usuario_id = $1 AND y.usuario_id::text = $2 AND x.estado = 'aceptada' AND y.estado = 'aceptada'
					AND p.cancelado_en IS NULL AND p.empieza_en + make_interval(mins => p.duracion_min) > now() - interval '1 day')
			OR EXISTS (SELECT 1 FROM intereses x JOIN intereses y ON y.usuario_id = x.otro_id AND y.otro_id = x.usuario_id AND y.modo = x.modo
				WHERE x.usuario_id = $1 AND x.otro_id::text = $2 AND x.modo = 'citas' AND x.accion <> 'paso' AND y.accion <> 'paso'))`,
		a, b).Scan(&ok)
	return ok, err
}

// Conversacion es el último privado con cada persona.
type Conversacion struct {
	Con     Autor   `json:"con"`
	Ultimo  Privado `json:"ultimo"`
	SinLeer int     `json:"sinLeer"`
}

// Conversaciones lista los privados agrupados por persona, la más reciente primero.
func (s *Servicio) Conversaciones(ctx context.Context, usuarioID string) ([]Conversacion, error) {
	out := []Conversacion{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `
			SELECT DISTINCT ON (otro) otro, u.nombre, coalesce(u.fotos[1], ''),
				p.id, p.de_id, p.a_id, p.texto, p.creado_en, p.leido_en, `+columnaEstado("p.de_id", "p.moderacion", "$1")+`,
				(SELECT count(*) FROM privados z WHERE z.de_id = otro AND z.a_id = $1 AND z.leido_en IS NULL)
			FROM (SELECT *, CASE WHEN de_id = $1 THEN a_id ELSE de_id END AS otro FROM privados
				WHERE moderacion <> 'oculto' OR de_id = $1) p
			JOIN usuarios u ON u.id = p.otro
			-- Sin bloqueos en ningún sentido ni cuentas dadas de baja.
			WHERE u.baja_en IS NULL AND NOT EXISTS (SELECT 1 FROM bloqueos b
				WHERE (b.usuario_id = $1 AND b.bloqueado_id = p.otro) OR (b.usuario_id = p.otro AND b.bloqueado_id = $1))
			ORDER BY otro, p.id DESC`, usuarioID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var c Conversacion
			if err := rows.Scan(&c.Con.ID, &c.Con.Nombre, &c.Con.Foto, &c.Ultimo.ID, &c.Ultimo.DeID,
				&c.Ultimo.AID, &c.Ultimo.Texto, &c.Ultimo.CreadoEn, &c.Ultimo.LeidoEn, &c.Ultimo.Moderacion, &c.SinLeer); err != nil {
				return err
			}
			out = append(out, c)
		}
		return rows.Err()
	})
	// Más reciente primero.
	for i := 1; i < len(out); i++ {
		for j := i; j > 0 && out[j].Ultimo.ID > out[j-1].Ultimo.ID; j-- {
			out[j], out[j-1] = out[j-1], out[j]
		}
	}
	return out, err
}

// Hilo devuelve los privados con otra persona y marca como leídos los que recibí.
func (s *Servicio) Hilo(ctx context.Context, usuarioID, otroID string, antes int64) ([]Privado, error) {
	out := []Privado{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if antes <= 0 {
			antes = 1<<62 - 1
		}
		// Con un bloqueo en cualquier sentido, la conversación no se ve (como en Conversaciones).
		rows, err := tx.Query(ctx, `
			SELECT * FROM (SELECT id, de_id, a_id, texto, creado_en, leido_en, `+columnaEstado("de_id", "moderacion", "$1")+` FROM privados
				WHERE ((de_id = $1 AND a_id::text = $2) OR (de_id::text = $2 AND a_id = $1)) AND id < $3
					AND (moderacion <> 'oculto' OR de_id = $1)
					AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = $1 AND b.bloqueado_id::text = $2)
						OR (b.usuario_id::text = $2 AND b.bloqueado_id = $1))
				ORDER BY id DESC LIMIT 100) x ORDER BY id`, usuarioID, otroID, antes)
		if err != nil {
			return err
		}
		for rows.Next() {
			var p Privado
			if err := rows.Scan(&p.ID, &p.DeID, &p.AID, &p.Texto, &p.CreadoEn, &p.LeidoEn, &p.Moderacion); err != nil {
				rows.Close()
				return err
			}
			out = append(out, p)
		}
		rows.Close()
		if err := rows.Err(); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `UPDATE privados SET leido_en = now() WHERE a_id = $1 AND de_id::text = $2 AND leido_en IS NULL`, usuarioID, otroID)
		return err
	})
	return out, err
}

// EnviarPrivado manda un privado (si pueden hablar).
func (s *Servicio) EnviarPrivado(ctx context.Context, usuarioID, otroID, texto string) (Privado, error) {
	texto, err := limpiar(texto)
	if err != nil {
		return Privado{}, err
	}
	if otroID == usuarioID {
		return Privado{}, problema.Invalido("No te podés escribir a vos.")
	}
	v, err := s.filtrar(ctx, usuarioID, moderacion.TipoPrivado, "", texto)
	if err != nil {
		return Privado{}, err
	}
	var p Privado
	err = s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		ok, err := puedenHablar(ctx, tx, usuarioID, otroID)
		if err != nil {
			return err
		}
		if !ok {
			return problema.SinPermiso("Los privados son entre quienes compartieron un plan o son de tu gente.")
		}
		if err := tx.QueryRow(ctx, `INSERT INTO privados (de_id, a_id, texto, moderacion) VALUES ($1, $2, $3, $4)
			RETURNING id, de_id, a_id, texto, creado_en`, usuarioID, otroID, texto, moderacion.EstadoMensaje(v)).
			Scan(&p.ID, &p.DeID, &p.AID, &p.Texto, &p.CreadoEn); err != nil {
			return err
		}
		if v.Decision == moderacion.Dudoso {
			if err := moderacion.PublicarRevision(ctx, tx, moderacion.TipoPrivado, p.ID, v); err != nil {
				return err
			}
		}
		return outbox.Publicar(ctx, tx, "privado.nuevo", map[string]any{"privadoId": p.ID})
	})
	return p, err
}

var reUUID = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

func esUUID(s string) bool { return reUUID.MatchString(s) }

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}
