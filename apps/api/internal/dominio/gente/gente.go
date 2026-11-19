// Package gente: los modos Gente y Citas de Ahora. Un mazo de personas con ganas de salir (o de
// una cita): "ME PRENDO" la suma a tu gente; en Citas, "ME INTERESA" (o "me encanta") y si es
// mutuo se abre el chat. Sin Plus, 3 sumadas por día. También las invitaciones a planes.
package gente

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
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Un "paso" esconde a la persona del mazo por este tiempo.
const olvidoPaso = 30 * 24 * time.Hour

// Servicio de gente.
type Servicio struct {
	App   *db.Pool
	Reloj func() time.Time
}

func (s *Servicio) ahora() time.Time {
	if s.Reloj != nil {
		return s.Reloj()
	}
	return time.Now()
}

// Prompt del perfil.
type Prompt struct {
	P string `json:"p"`
	R string `json:"r"`
}

// ProximoPlan de una persona (en su tarjeta).
type ProximoPlan struct {
	ID        string    `json:"id"`
	Titulo    string    `json:"titulo"`
	Categoria string    `json:"categoria"`
	EmpiezaEn time.Time `json:"empiezaEn"`
	Foto      string    `json:"foto"`
}

// Tarjeta de una persona en el mazo.
type Tarjeta struct {
	ID          string   `json:"id"`
	Nombre      string   `json:"nombre"`
	Edad        *int16   `json:"edad,omitempty"`
	Zona        string   `json:"zona"`
	Trabajo     string   `json:"trabajo"`
	Bio         string   `json:"bio"`
	Intereses   []string `json:"intereses"`
	Intenciones []string `json:"intenciones"`
	Prompts     []Prompt `json:"prompts"`
	Fotos       []string `json:"fotos"`
	Verificada  bool     `json:"verificada"`
	Asistencia  struct {
		Fue int `json:"fue"`
		De  int `json:"de"`
	} `json:"asistencia"`
	Planes int `json:"planes"`
	// Intereses en común con quien mira.
	EnComun  []string      `json:"enComun"`
	Proximos []ProximoPlan `json:"proximos"`
}

func validarModo(modo string) error {
	if modo != "gente" && modo != "citas" {
		return problema.Invalido("El modo es gente o citas.")
	}
	return nil
}

// Mazo devuelve personas para el modo, primero las que tienen más intereses en común. Primero se eligen
// los 30 (filtros, orden y LIMIT) y recién sobre esos se calcula la asistencia, el conteo de planes y los
// próximos planes, en una sola consulta (antes, la asistencia corría para toda la ciudad y los próximos
// eran 30 consultas más).
func (s *Servicio) Mazo(ctx context.Context, usuarioID, modo string) ([]Tarjeta, error) {
	if err := validarModo(modo); err != nil {
		return nil, err
	}
	out := []Tarjeta{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var ciudad string
		var intenciones []string
		if err := tx.QueryRow(ctx, `SELECT ciudad, intenciones FROM usuarios WHERE id = $1`, usuarioID).Scan(&ciudad, &intenciones); err != nil {
			return err
		}
		if modo == "citas" && !contiene(intenciones, "citas") {
			return problema.Conflicto("sin_citas", "Activá Citas en lo que buscás para ver este modo.")
		}
		rows, err := tx.Query(ctx, `
			WITH yo AS (SELECT intereses FROM usuarios WHERE id = $1),
			elegidos AS (
				SELECT u.id, ARRAY(SELECT unnest(u.intereses) INTERSECT SELECT unnest(yo.intereses)) AS en_comun,
					EXISTS (SELECT 1 FROM intereses e WHERE e.usuario_id = u.id AND e.otro_id = $1 AND e.modo = $3
						AND e.accion = 'encanta') AS le_encanto,
					u.actualizado_en
				FROM usuarios u CROSS JOIN yo
				WHERE u.id <> $1 AND u.baja_en IS NULL AND u.suspendido_en IS NULL AND u.selfie_en IS NOT NULL
					-- Sin DNI aprobado no se muestra a nadie (el DNI es obligatorio desde el 16/11).
					AND u.dni_en IS NOT NULL AND cardinality(u.fotos) > 0
					-- Sin ciudad cargada (el alta de la web no la pide) también aparece.
					AND ($2 = '' OR u.ciudad = '' OR lower(u.ciudad) = lower($2))
					AND u.intenciones && CASE WHEN $3 = 'citas' THEN ARRAY['citas'] ELSE ARRAY['gente', 'planes', 'completar'] END
					AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = $1 AND b.bloqueado_id = u.id)
						OR (b.usuario_id = u.id AND b.bloqueado_id = $1))
					AND NOT EXISTS (SELECT 1 FROM intereses i WHERE i.usuario_id = $1 AND i.otro_id = u.id AND i.modo = $3
						AND (i.accion <> 'paso' OR i.creado_en > $4))
					AND ($3 = 'citas' OR NOT EXISTS (SELECT 1 FROM mi_gente g WHERE g.usuario_id = $1 AND g.otro_id = u.id))
				ORDER BY le_encanto DESC, cardinality(ARRAY(SELECT unnest(u.intereses) INTERSECT SELECT unnest(yo.intereses))) DESC,
					u.actualizado_en DESC
				LIMIT 30)
			SELECT u.id, u.nombre, u.edad, u.zona, u.trabajo, u.bio, u.intereses, u.intenciones, u.prompts, u.fotos,
				u.dni_en IS NOT NULL, rep.fue, rep.de,
				(SELECT count(*) FROM solicitudes s WHERE s.usuario_id = u.id AND s.estado = 'aceptada'),
				e.en_comun,
				coalesce((SELECT jsonb_agg(jsonb_build_object('id', x.id, 'titulo', x.titulo, 'categoria', x.categoria,
						'empiezaEn', x.empieza_en, 'foto', x.foto) ORDER BY x.empieza_en)
					FROM (SELECT p.id, p.titulo, p.categoria, p.empieza_en, p.foto FROM planes p
						JOIN solicitudes s ON s.plan_id = p.id AND s.usuario_id = u.id AND s.estado = 'aceptada'
						WHERE p.cancelado_en IS NULL AND p.empieza_en > now() AND p.visibilidad = 'publico'
							-- Ni un plan oculto por las normas ni uno de una cuenta suspendida o borrada (como en las listas).
							AND `+planes.OrganizaActivo+`
							AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = $1 AND b.bloqueado_id = p.organizador_id)
								OR (b.usuario_id = p.organizador_id AND b.bloqueado_id = $1))
						ORDER BY p.empieza_en LIMIT 3) x), '[]'::jsonb)
			FROM elegidos e JOIN usuarios u ON u.id = e.id
			LEFT JOIN LATERAL app.asistencia(u.id) rep ON true
			ORDER BY e.le_encanto DESC, cardinality(e.en_comun) DESC, e.actualizado_en DESC`,
			usuarioID, ciudad, modo, s.ahora().Add(-olvidoPaso))
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var t Tarjeta
			var prompts, prox []byte
			if err := rows.Scan(&t.ID, &t.Nombre, &t.Edad, &t.Zona, &t.Trabajo, &t.Bio, &t.Intereses, &t.Intenciones,
				&prompts, &t.Fotos, &t.Verificada, &t.Asistencia.Fue, &t.Asistencia.De, &t.Planes, &t.EnComun, &prox); err != nil {
				return err
			}
			_ = json.Unmarshal(prompts, &t.Prompts)
			t.Proximos = []ProximoPlan{}
			_ = json.Unmarshal(prox, &t.Proximos)
			// Que alguien busca Citas lo ve sólo quien también está en Citas.
			t.Intenciones = IntencionesVisibles(t.Intenciones, intenciones)
			out = append(out, t)
		}
		return rows.Err()
	})
	return out, err
}

// IntencionesVisibles saca «citas» de las intenciones de otra persona salvo que quien mira también esté en
// Citas: es un dato íntimo y no se muestra fuera de ese modo.
func IntencionesVisibles(delOtro, mias []string) []string {
	if contiene(mias, "citas") {
		return delOtro
	}
	out := make([]string, 0, len(delOtro))
	for _, v := range delOtro {
		if v != "citas" {
			out = append(out, v)
		}
	}
	return out
}

func contiene(xs []string, x string) bool {
	for _, v := range xs {
		if v == x {
			return true
		}
	}
	return false
}

// Resultado de reaccionar.
type Resultado struct {
	// En Citas: los dos se interesaron, ya pueden hablar.
	Mutuo bool        `json:"mutuo"`
	Plus  plus.Estado `json:"plus"`
}

// Reaccionar: prendo (Gente), interesa / encanta (Citas) o paso.
//
// A quién: en Citas, sólo a quien el mazo mostraría (con DNI y selfie, fotos, sin suspender y en Citas), y
// sólo si quien reacciona también eligió Citas: el consentimiento de ese modo. En Gente alcanza con una
// cuenta con DNI y sin suspender (se suma también desde el perfil, al que se llega por la lista de un plan).
//
// Repetir la misma reacción no hace nada (ni gasta topes, ni corre la fecha, ni manda otro aviso).
func (s *Servicio) Reaccionar(ctx context.Context, usuarioID, otroID, modo, accion string) (Resultado, error) {
	if err := validarModo(modo); err != nil {
		return Resultado{}, err
	}
	switch {
	case accion == "paso":
	case modo == "gente" && accion == "prendo":
	case modo == "citas" && (accion == "interesa" || accion == "encanta"):
	default:
		return Resultado{}, problema.Invalido("Esa acción no va en ese modo.")
	}
	if otroID == usuarioID {
		return Resultado{}, problema.Invalido("No te podés sumar a vos.")
	}
	var r Resultado
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		// Los topes del día (sumadas y «me encanta») se cuentan y se gastan con la persona bloqueada:
		// dos reacciones a la vez no pasan las dos con el último lugar del día.
		if err := plus.Bloquear(ctx, tx, usuarioID); err != nil {
			return err
		}
		if modo == "citas" && accion != "paso" {
			var mias []string
			if err := tx.QueryRow(ctx, `SELECT intenciones FROM usuarios WHERE id = $1`, usuarioID).Scan(&mias); err != nil {
				return err
			}
			if !contiene(mias, "citas") {
				return problema.Conflicto("sin_citas", "Activá Citas en lo que buscás para ver este modo.")
			}
		}
		var existe bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM usuarios u WHERE u.id = $2::uuid AND u.baja_en IS NULL
			AND ($4 = 'paso' OR (u.suspendido_en IS NULL AND u.dni_en IS NOT NULL
				AND ($3 <> 'citas' OR (u.selfie_en IS NOT NULL AND cardinality(u.fotos) > 0 AND 'citas' = ANY(u.intenciones)))))
			AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = $1 AND b.bloqueado_id = u.id)
				OR (b.usuario_id = u.id AND b.bloqueado_id = $1)))`, usuarioID, otroID, modo, accion).Scan(&existe); err != nil {
			return err
		}
		if !existe {
			return problema.NoEncontrado("Esa persona no existe.")
		}
		var previa string
		// yaEncanto: ya le dio «me encanta» a esta persona (encanta_en queda aunque después cambie a
		// «me interesa» o pase): volver a darlo no gasta otro ni le avisa de nuevo. Sin esto, ir y volver
		// entre interesa y encanta liberaba el tope del día.
		var yaEncanto bool
		if err := tx.QueryRow(ctx, `SELECT accion, encanta_en IS NOT NULL FROM intereses WHERE usuario_id = $1 AND otro_id = $2::uuid AND modo = $3`,
			usuarioID, otroID, modo).Scan(&previa, &yaEncanto); err != nil && !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		var err error
		if r.Plus, err = plus.Leer(ctx, tx, usuarioID, s.ahora()); err != nil {
			return err
		}
		if previa == accion {
			if accion != "paso" {
				return mutuo(ctx, tx, usuarioID, otroID, modo, &r.Mutuo)
			}
			return nil
		}
		// «Nueva»: de nada (o de un paso) a una reacción positiva. Sólo esa gasta una sumada y avisa.
		nueva := accion != "paso" && (previa == "" || previa == "paso")
		if nueva && !r.Plus.Activo && r.Plus.SumadasHoy >= plus.LimiteDiario {
			return problema.Conflicto("limite", "Llegaste a las 3 sumadas de hoy. Con Plus no tenés límite.")
		}
		encantaNuevo := accion == "encanta" && !yaEncanto
		if encantaNuevo && r.Plus.EncantasHoy >= r.Plus.LimiteEncantas {
			if r.Plus.Activo {
				return problema.Conflicto("limite_encanta", fmt.Sprintf("Ya usaste los %d me encanta de hoy.", plus.EncantasPlus))
			}
			return problema.Conflicto("limite_encanta", fmt.Sprintf("Ya usaste tu me encanta de hoy. Con Plus tenés %d por día.", plus.EncantasPlus))
		}
		// creado_en es el día de la sumada (o del paso, para olvidarlo): pasar de interesa a encanta no lo corre.
		// El «me encanta» se cuenta por encanta_en, que se pone una vez y no se borra.
		if _, err := tx.Exec(ctx, `INSERT INTO intereses (usuario_id, otro_id, modo, accion, encanta_en)
			VALUES ($1, $2::uuid, $3, $4, CASE WHEN $4 = 'encanta' THEN now() END)
			ON CONFLICT (usuario_id, otro_id, modo) DO UPDATE SET accion = excluded.accion, descartado_en = NULL,
				creado_en = CASE WHEN intereses.accion = 'paso' OR excluded.accion = 'paso' THEN now() ELSE intereses.creado_en END,
				encanta_en = coalesce(intereses.encanta_en, excluded.encanta_en)`,
			usuarioID, otroID, modo, accion); err != nil {
			return err
		}
		if accion == "paso" {
			return nil
		}
		if nueva {
			r.Plus.SumadasHoy++
		}
		if encantaNuevo {
			r.Plus.EncantasHoy++
		}
		if modo == "gente" {
			if _, err := tx.Exec(ctx, `INSERT INTO mi_gente (usuario_id, otro_id) VALUES ($1, $2::uuid) ON CONFLICT DO NOTHING`, usuarioID, otroID); err != nil {
				return err
			}
		}
		if err := mutuo(ctx, tx, usuarioID, otroID, modo, &r.Mutuo); err != nil {
			return err
		}
		// Un aviso por reacción nueva (o por el primer «me encanta»): cambiar entre interesa y encanta, o
		// repetir, no le vuelve a llegar a la otra persona.
		if !nueva && !encantaNuevo {
			return nil
		}
		return outbox.Publicar(ctx, tx, "interes.nuevo", map[string]any{"de": usuarioID, "a": otroID, "modo": modo, "mutuo": r.Mutuo && nueva})
	})
	return r, err
}

// mutuo: en Gente, «ya pueden hablar» es la misma regla del privado (chat.puedenHablar): los dos se tienen
// en Mi gente. En Citas, que el otro también reaccionó (no un paso).
func mutuo(ctx context.Context, tx pgx.Tx, usuarioID, otroID, modo string, out *bool) error {
	if modo == "gente" {
		return tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM mi_gente WHERE usuario_id = $2::uuid AND otro_id = $1)
			AND EXISTS (SELECT 1 FROM mi_gente WHERE usuario_id = $1 AND otro_id = $2::uuid)`, usuarioID, otroID).Scan(out)
	}
	return tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM intereses WHERE usuario_id = $2::uuid AND otro_id = $1
		AND modo = $3 AND accion <> 'paso')`, usuarioID, otroID, modo).Scan(out)
}

// Recibido: alguien que te sumó o le interesás.
type Recibido struct {
	// Sin Plus no va nada que identifique (ni id, ni nombre, ni foto): sólo cuándo.
	ID     string    `json:"id,omitempty"`
	Nombre string    `json:"nombre,omitempty"`
	Edad   *int16    `json:"edad,omitempty"`
	Foto   string    `json:"foto"`
	Accion string    `json:"accion,omitempty"`
	Cuando time.Time `json:"cuando"`
}

// Recibidos: "Te sumaron" (Gente) o "Les interesás" (Citas).
func (s *Servicio) Recibidos(ctx context.Context, usuarioID, modo string) ([]Recibido, bool, error) {
	if err := validarModo(modo); err != nil {
		return nil, false, err
	}
	out := []Recibido{}
	var bloqueada bool
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		e, err := plus.Leer(ctx, tx, usuarioID, s.ahora())
		if err != nil {
			return err
		}
		bloqueada = !e.Activo
		// «Les interesás» es de Citas: vacío para quien no está en ese modo.
		if modo == "citas" {
			var mias []string
			if err := tx.QueryRow(ctx, `SELECT intenciones FROM usuarios WHERE id = $1`, usuarioID).Scan(&mias); err != nil {
				return err
			}
			if !contiene(mias, "citas") {
				return nil
			}
		}
		rows, err := tx.Query(ctx, `
			SELECT u.id, u.nombre, u.edad, coalesce(u.fotos[1], ''), i.accion, i.creado_en
			FROM intereses i JOIN usuarios u ON u.id = i.usuario_id
			WHERE i.otro_id = $1 AND i.modo = $2 AND i.accion <> 'paso' AND i.descartado_en IS NULL
				AND u.baja_en IS NULL AND u.suspendido_en IS NULL
				AND ($2 <> 'citas' OR 'citas' = ANY(u.intenciones))
				AND NOT EXISTS (SELECT 1 FROM intereses y WHERE y.usuario_id = $1 AND y.otro_id = u.id AND y.modo = $2)
				AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = $1 AND b.bloqueado_id = u.id)
					OR (b.usuario_id = u.id AND b.bloqueado_id = $1))
			ORDER BY i.accion = 'encanta' DESC, i.creado_en DESC LIMIT 100`, usuarioID, modo)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var r Recibido
			if err := rows.Scan(&r.ID, &r.Nombre, &r.Edad, &r.Foto, &r.Accion, &r.Cuando); err != nil {
				return err
			}
			// Sin Plus no va nada que identifique: ni id, ni nombre, ni el link de la foto (es el mismo del
			// mazo y del perfil, y comparándolo se sabría quién es). La app pone caras genéricas borrosas.
			if bloqueada {
				r = Recibido{Cuando: r.Cuando}
			}
			out = append(out, r)
		}
		return rows.Err()
	})
	return out, bloqueada, err
}

// Descartar saca a alguien de "Te sumaron" / "Les interesás" (necesita Plus: sin Plus no se sabe quién es).
func (s *Servicio) Descartar(ctx context.Context, usuarioID, otroID, modo string) error {
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `UPDATE intereses SET descartado_en = now() WHERE usuario_id::text = $2 AND otro_id = $1 AND modo = $3`,
			usuarioID, otroID, modo)
		return err
	})
}

// Invitacion a un plan.
type Invitacion struct {
	PlanID    string    `json:"planId"`
	Titulo    string    `json:"titulo,omitempty"`
	Categoria string    `json:"categoria,omitempty"`
	EmpiezaEn time.Time `json:"empiezaEn"`
	De        *Recibido `json:"de,omitempty"`
	Foto      string    `json:"foto"`
	Cuando    time.Time `json:"cuando"`
}

// Invitar: alguien que está en un plan invita a otra persona. Le llega como invitación.
func (s *Servicio) Invitar(ctx context.Context, usuarioID, otroID, planID string) error {
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var esta, otroEsta, puede bool
		if err := tx.QueryRow(ctx, `SELECT
				EXISTS (SELECT 1 FROM solicitudes WHERE plan_id::text = $1 AND usuario_id = $2 AND estado = 'aceptada'),
				EXISTS (SELECT 1 FROM solicitudes WHERE plan_id::text = $1 AND usuario_id::text = $3 AND estado IN ('pendiente', 'aceptada')),
				EXISTS (SELECT 1 FROM usuarios u JOIN planes p ON p.id::text = $1 WHERE u.id::text = $3 AND u.baja_en IS NULL
					AND p.cancelado_en IS NULL AND p.empieza_en > now()
					AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = $2 AND b.bloqueado_id = u.id)
						OR (b.usuario_id = u.id AND b.bloqueado_id = $2)))`,
			planID, usuarioID, otroID).Scan(&esta, &otroEsta, &puede); err != nil {
			return err
		}
		switch {
		case !esta:
			return problema.SinPermiso("Para invitar tenés que estar en el plan.")
		case otroEsta:
			return problema.Conflicto("ya_esta", "Ya está en el plan.")
		case !puede:
			return problema.NoEncontrado("No se puede invitar a esa persona a ese plan.")
		}
		tag, err := tx.Exec(ctx, `INSERT INTO invitaciones (plan_id, de_id, a_id) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING`, planID, usuarioID, otroID)
		if err != nil || tag.RowsAffected() == 0 {
			return err
		}
		return outbox.Publicar(ctx, tx, "invitacion.nueva", map[string]string{"planId": planID, "de": usuarioID, "a": otroID})
	})
}

// Invitaciones recibidas ("Te invitan"). Sin Plus: sólo el plan y la foto de quien invita.
func (s *Servicio) Invitaciones(ctx context.Context, usuarioID string) ([]Invitacion, bool, error) {
	out := []Invitacion{}
	var bloqueada bool
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		e, err := plus.Leer(ctx, tx, usuarioID, s.ahora())
		if err != nil {
			return err
		}
		bloqueada = !e.Activo
		rows, err := tx.Query(ctx, `
			SELECT p.id, p.titulo, p.categoria, p.empieza_en, p.foto, u.id, u.nombre, u.edad, coalesce(u.fotos[1], ''), i.creada_en
			FROM invitaciones i JOIN planes p ON p.id = i.plan_id JOIN usuarios u ON u.id = i.de_id
			WHERE i.a_id = $1 AND i.estado = 'pendiente' AND p.cancelado_en IS NULL AND p.empieza_en > now()
			ORDER BY i.creada_en DESC LIMIT 100`, usuarioID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var v Invitacion
			de := Recibido{}
			if err := rows.Scan(&v.PlanID, &v.Titulo, &v.Categoria, &v.EmpiezaEn, &v.Foto, &de.ID, &de.Nombre, &de.Edad, &de.Foto, &v.Cuando); err != nil {
				return err
			}
			de.Cuando = v.Cuando
			if bloqueada {
				de = Recibido{Foto: de.Foto, Cuando: de.Cuando}
			}
			v.De = &de
			out = append(out, v)
		}
		return rows.Err()
	})
	return out, bloqueada, err
}

// ErrResponder: la invitación ya no está.
var ErrResponder = problema.NoEncontrado("Esa invitación ya no está.")

// ResponderInvitacion marca la invitación. Si acepta y la invitó el organizador, entra directo
// (si hay lugar); si no, la app sigue con el "Sumarme" del plan. Devuelve si quedó adentro.
func (s *Servicio) ResponderInvitacion(ctx context.Context, usuarioID, planID string, aceptar bool, conf planes.Confirma) (bool, error) {
	adentro := false
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var de, org, genero, cobro string
		var total int16
		var cancelado, activo, selfie, bloqueo bool
		var empieza time.Time
		var edadMin, edadMax, edad *int16
		var previo *string
		err := tx.QueryRow(ctx, `SELECT i.de_id, p.organizador_id, p.total, p.genero, p.cobro, p.cancelado_en IS NOT NULL,
				p.empieza_en, p.edad_min, p.edad_max, `+planes.OrganizaActivo+`,
				u.selfie_en IS NOT NULL, u.edad,
				EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = $2 AND b.bloqueado_id IN (i.de_id, p.organizador_id))
					OR (b.bloqueado_id = $2 AND b.usuario_id IN (i.de_id, p.organizador_id))),
				(SELECT s.estado FROM solicitudes s WHERE s.plan_id = p.id AND s.usuario_id = $2)
			FROM invitaciones i JOIN planes p ON p.id = i.plan_id JOIN usuarios u ON u.id = i.a_id
			WHERE i.plan_id::text = $1 AND i.a_id = $2 AND i.estado = 'pendiente' FOR UPDATE OF p`, planID, usuarioID).
			Scan(&de, &org, &total, &genero, &cobro, &cancelado, &empieza, &edadMin, &edadMax, &activo, &selfie, &edad, &bloqueo, &previo)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrResponder
		}
		if err != nil {
			return err
		}
		estado := "descartada"
		if aceptar {
			estado = "aceptada"
			switch {
			case cancelado:
				return problema.Conflicto("cancelado", "Este plan se canceló.")
			case !empieza.After(s.ahora()):
				return problema.Conflicto("paso", "Este plan ya arrancó.")
			case bloqueo || !activo:
				return ErrResponder
			}
			// Lo mismo que al sumarse: selfie, rango de edad y el género que confirma (el DNI lo pidió
			// la sesión). Aunque invite el organizador.
			if err := planes.Admitir(selfie, edad, edadMin, edadMax, genero, conf); err != nil {
				return err
			}
		}
		if _, err := tx.Exec(ctx, `UPDATE invitaciones SET estado = $3 WHERE plan_id::text = $1 AND a_id = $2`, planID, usuarioID, estado); err != nil {
			return err
		}
		// Entra directo sólo si la invitó el organizador, a un plan gratis sin lugar (los de entrada se
		// compran) y si no la habían sacado antes.
		if !aceptar || de != org || cobro != "gratis" {
			return nil
		}
		switch {
		case previo != nil && *previo == "rechazada":
			return problema.Conflicto("rechazada", "Esta vez el organizador no te sumó.")
		case previo != nil && *previo == "aceptada":
			// Ya estaba (quizás con «somos 3»): no se toca la cantidad.
			adentro = true
			return nil
		}
		// Si había pedido sumarse en grupo y estaba pendiente, entra el grupo entero (con su cupo).
		cantidad := 1
		if previo != nil && *previo == "pendiente" {
			if err := tx.QueryRow(ctx, `SELECT cantidad FROM solicitudes WHERE plan_id::text = $1 AND usuario_id = $2`,
				planID, usuarioID).Scan(&cantidad); err != nil {
				return err
			}
		}
		var sumados int
		if err := tx.QueryRow(ctx, `SELECT coalesce(sum(cantidad), 0) FROM solicitudes WHERE plan_id::text = $1 AND estado = 'aceptada'`, planID).Scan(&sumados); err != nil {
			return err
		}
		if sumados+cantidad > int(total) {
			return problema.Conflicto("completo", "El plan se llenó antes de que respondieras.")
		}
		if _, err := tx.Exec(ctx, `INSERT INTO solicitudes (plan_id, usuario_id, cantidad, estado, respondida_en)
			VALUES ($1, $2, $3, 'aceptada', now()) ON CONFLICT (plan_id, usuario_id) DO UPDATE SET estado = 'aceptada',
				cantidad = excluded.cantidad, respondida_en = now()`,
			planID, usuarioID, cantidad); err != nil {
			return err
		}
		adentro = true
		return outbox.Publicar(ctx, tx, "solicitud.aceptada", map[string]string{"planId": planID, "usuarioId": usuarioID})
	})
	return adentro, err
}
