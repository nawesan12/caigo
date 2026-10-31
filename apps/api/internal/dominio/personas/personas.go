// Package personas: el perfil propio y el de los demás, mi gente, bloqueos y reportes. Los perfiles
// son livianos (foto, nombre, edad, intereses, reputación y planes compartidos): sin feed ni
// seguidores.
package personas

import (
	"context"
	"encoding/json"
	"errors"
	"math"
	"regexp"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Servicio de personas.
type Servicio struct{ App *db.Pool }

// Prompt del perfil ("Un domingo ideal…").
type Prompt struct {
	P string `json:"p"`
	R string `json:"r"`
}

// Perfil de una persona.
type Perfil struct {
	ID          string   `json:"id"`
	Nombre      string   `json:"nombre"`
	Edad        *int16   `json:"edad,omitempty"`
	Zona        string   `json:"zona"`
	Ciudad      string   `json:"ciudad"`
	Bio         string   `json:"bio"`
	Trabajo     string   `json:"trabajo"`
	Intereses   []string `json:"intereses"`
	Intenciones []string `json:"intenciones"`
	Prompts     []Prompt `json:"prompts"`
	Fotos       []string `json:"fotos"`
	Selfie      bool     `json:"selfie"`
	Verificada  bool     `json:"verificada"`
	Asistencia  struct {
		Fue int `json:"fue"`
		De  int `json:"de"`
	} `json:"asistencia"`
	Planes      int  `json:"planes"`
	Organizados int  `json:"organizados"`
	MiGente     int  `json:"miGente"`
	EnMiGente   bool `json:"enMiGente"`
	// Planes que compartimos (perfil ajeno).
	Compartidos int `json:"compartidos,omitempty"`
	// Sólo en el propio.
	Equipo   bool   `json:"equipo,omitempty"`
	Telefono string `json:"telefono,omitempty"`
	// Autodeclarados y privados (sólo en el propio): "" | mujer | varon | no_binario.
	Genero     *string `json:"genero,omitempty"`
	Diversidad *bool   `json:"diversidad,omitempty"`
	Avisos     *Avisos `json:"avisos,omitempty"`
	// Próximos planes de la persona que quien mira puede ver (perfil ajeno).
	Proximos []Proximo `json:"proximos,omitempty"`
}

// Avisos que la persona puede apagar.
type Avisos struct {
	PlanesCerca bool `json:"planesCerca"`
	TuGente     bool `json:"tuGente"`
}

// Proximo es un plan que viene (mismo formato que en el mazo de Gente).
type Proximo struct {
	ID        string    `json:"id"`
	Titulo    string    `json:"titulo"`
	Categoria string    `json:"categoria"`
	EmpiezaEn time.Time `json:"empiezaEn"`
	Foto      string    `json:"foto"`
}

const columnas = `
	u.id, u.nombre, u.edad, u.zona, u.ciudad, u.bio, u.trabajo, u.intereses, u.intenciones, u.prompts,
	u.fotos, u.selfie_en IS NOT NULL, u.dni_en IS NOT NULL, rep.fue, rep.de, u.equipo,
	(SELECT count(*) FROM solicitudes s WHERE s.usuario_id = u.id AND s.estado = 'aceptada'),
	(SELECT count(*) FROM planes p WHERE p.organizador_id = u.id AND p.cancelado_en IS NULL),
	(SELECT count(*) FROM mi_gente g WHERE g.usuario_id = u.id),
	EXISTS (SELECT 1 FROM mi_gente g WHERE g.usuario_id = $1 AND g.otro_id = u.id),
	(SELECT count(*) FROM solicitudes a JOIN solicitudes b ON a.plan_id = b.plan_id
		WHERE a.usuario_id = $1 AND b.usuario_id = u.id AND a.estado = 'aceptada' AND b.estado = 'aceptada')`

func escanear(row pgx.Row) (Perfil, error) {
	var p Perfil
	var prompts []byte
	err := row.Scan(&p.ID, &p.Nombre, &p.Edad, &p.Zona, &p.Ciudad, &p.Bio, &p.Trabajo, &p.Intereses,
		&p.Intenciones, &prompts, &p.Fotos, &p.Selfie, &p.Verificada, &p.Asistencia.Fue, &p.Asistencia.De,
		&p.Equipo, &p.Planes, &p.Organizados, &p.MiGente, &p.EnMiGente, &p.Compartidos)
	if err == nil {
		_ = json.Unmarshal(prompts, &p.Prompts)
	}
	return p, err
}

// Yo devuelve el perfil propio, con el teléfono.
func (s *Servicio) Yo(ctx context.Context, usuarioID string) (Perfil, error) {
	var p Perfil
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var err error
		p, err = escanear(tx.QueryRow(ctx, `SELECT `+columnas+` FROM usuarios u
			LEFT JOIN LATERAL app.asistencia(u.id) rep ON true WHERE u.id = $1`, usuarioID))
		if err != nil {
			return err
		}
		p.EnMiGente, p.Compartidos = false, 0
		genero, diversidad, av := "", false, Avisos{PlanesCerca: true, TuGente: true}
		err = tx.QueryRow(ctx, `SELECT coalesce(telefono, ''), genero, diversidad, avisos_cerca, avisos_gente
			FROM usuarios_privado WHERE usuario_id = $1`, usuarioID).Scan(&p.Telefono, &genero, &diversidad, &av.PlanesCerca, &av.TuGente)
		p.Genero, p.Diversidad, p.Avisos = &genero, &diversidad, &av
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		return err
	})
	return p, err
}

// Ver devuelve el perfil de otra persona (sin el flag de equipo). Si hay un bloqueo, no existe.
func (s *Servicio) Ver(ctx context.Context, usuarioID, otroID string) (Perfil, error) {
	var p Perfil
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var err error
		p, err = escanear(tx.QueryRow(ctx, `SELECT `+columnas+` FROM usuarios u
			LEFT JOIN LATERAL app.asistencia(u.id) rep ON true
			WHERE u.id::text = $2 AND u.baja_en IS NULL AND NOT EXISTS (SELECT 1 FROM bloqueos b
				WHERE (b.usuario_id = $1 AND b.bloqueado_id = u.id) OR (b.usuario_id = u.id AND b.bloqueado_id = $1))`,
			usuarioID, otroID))
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Esa persona no existe.")
		}
		if err != nil {
			return err
		}
		// Sus próximos planes (organiza o va) que quien mira puede ver.
		rows, err := tx.Query(ctx, `
			SELECT p.id, p.titulo, p.categoria, p.empieza_en, p.foto FROM planes p
			JOIN solicitudes s ON s.plan_id = p.id AND s.usuario_id = $1 AND s.estado = 'aceptada'
			WHERE p.cancelado_en IS NULL AND p.empieza_en > now()
				AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = $2 AND b.bloqueado_id = p.organizador_id)
					OR (b.usuario_id = p.organizador_id AND b.bloqueado_id = $2))
				AND (p.visibilidad = 'publico' OR p.organizador_id = $2
					OR EXISTS (SELECT 1 FROM solicitudes m WHERE m.plan_id = p.id AND m.usuario_id = $2 AND m.estado IN ('pendiente', 'aceptada'))
					OR (p.visibilidad = 'mi_gente' AND EXISTS (SELECT 1 FROM mi_gente g WHERE g.usuario_id = p.organizador_id AND g.otro_id = $2)))
			ORDER BY p.empieza_en LIMIT 5`, p.ID, usuarioID)
		if err != nil {
			return err
		}
		p.Proximos, err = pgx.CollectRows(rows, pgx.RowToStructByPos[Proximo])
		if p.Proximos == nil {
			p.Proximos = []Proximo{}
		}
		return err
	})
	p.Equipo = false
	return p, err
}

// Cambios al perfil propio. nil: no se toca.
type Cambios struct {
	Nombre      *string   `json:"nombre"`
	Nacimiento  *string   `json:"nacimiento"` // YYYY-MM-DD; de ahí sale la edad
	Zona        *string   `json:"zona"`
	Ciudad      *string   `json:"ciudad"`
	Bio         *string   `json:"bio"`
	Trabajo     *string   `json:"trabajo"`
	Intereses   *[]string `json:"intereses"`
	Intenciones *[]string `json:"intenciones"`
	Prompts     *[]Prompt `json:"prompts"`
	Fotos       *[]string `json:"fotos"`
	// Contacto de confianza para compartir el plan en vivo.
	ContactoConfianza *string `json:"contactoConfianza"`
	// Género autodeclarado ("" | mujer | varon | no_binario) y "me identifico como parte de la
	// diversidad". Privados: sólo cuentan para los planes "Solo mujeres" / "Solo diversidades".
	Genero     *string `json:"genero"`
	Diversidad *bool   `json:"diversidad"`
	Avisos     *struct {
		PlanesCerca *bool `json:"planesCerca"`
		TuGente     *bool `json:"tuGente"`
	} `json:"avisos"`
	// De dónde llegó (p:<slug>, utm:instagram). Se guarda sólo la primera vez.
	Origen *string `json:"origen"`
}

// Actualizar aplica los cambios al perfil propio.
func (s *Servicio) Actualizar(ctx context.Context, usuarioID string, c Cambios, hoy time.Time) (Perfil, error) {
	var campos []problema.Campo
	mal := func(r, m string) { campos = append(campos, problema.Campo{Ruta: r, Mensaje: m}) }
	largo := func(r string, v *string, n int) {
		if v != nil {
			*v = strings.TrimSpace(*v)
			if len([]rune(*v)) > n {
				mal(r, "Es muy largo.")
			}
		}
	}
	largo("nombre", c.Nombre, 40)
	largo("zona", c.Zona, 60)
	largo("ciudad", c.Ciudad, 60)
	largo("bio", c.Bio, 300)
	largo("trabajo", c.Trabajo, 60)
	largo("contactoConfianza", c.ContactoConfianza, 80)
	if c.Nombre != nil && *c.Nombre == "" {
		mal("nombre", "Poné tu nombre.")
	}
	var edad *int16
	var nacimiento *time.Time
	if c.Nacimiento != nil {
		t, err := time.Parse(time.DateOnly, *c.Nacimiento)
		if err != nil {
			mal("nacimiento", "La fecha tiene que ser AAAA-MM-DD.")
		} else {
			e := int16(hoy.Year() - t.Year())
			if hoy.YearDay() < t.YearDay() {
				e--
			}
			if e < 18 {
				mal("nacimiento", "Caigo es sólo para mayores de 18.")
			}
			edad, nacimiento = &e, &t
		}
	}
	if c.Intereses != nil && len(*c.Intereses) > 12 {
		mal("intereses", "Hasta 12 intereses.")
	}
	if c.Intenciones != nil {
		for _, i := range *c.Intenciones {
			if i != "planes" && i != "gente" && i != "completar" && i != "citas" {
				mal("intenciones", "Intención desconocida: "+i)
			}
		}
		if len(*c.Intenciones) == 0 {
			mal("intenciones", "Elegí al menos una.")
		}
	}
	if c.Prompts != nil && len(*c.Prompts) > 3 {
		mal("prompts", "Hasta 3 preguntas.")
	}
	if c.Fotos != nil && len(*c.Fotos) > 6 {
		mal("fotos", "Hasta 6 fotos.")
	}
	if c.Genero != nil && *c.Genero != "" && *c.Genero != "mujer" && *c.Genero != "varon" && *c.Genero != "no_binario" {
		mal("genero", "El género es mujer, varon o no_binario.")
	}
	if c.Origen != nil {
		*c.Origen = strings.TrimSpace(*c.Origen)
		if len(*c.Origen) > 80 || !reOrigen.MatchString(*c.Origen) {
			mal("origen", "Origen inválido.")
		}
	}
	var cerca, gente *bool
	if c.Avisos != nil {
		cerca, gente = c.Avisos.PlanesCerca, c.Avisos.TuGente
	}
	if len(campos) > 0 {
		return Perfil{}, problema.Invalido("Revisá tu perfil.", campos...)
	}
	var prompts []byte
	if c.Prompts != nil {
		prompts, _ = json.Marshal(*c.Prompts)
	}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `
			UPDATE usuarios SET nombre = coalesce($2, nombre), edad = coalesce($3, edad),
				zona = coalesce($4, zona), ciudad = coalesce($5, ciudad), bio = coalesce($6, bio),
				trabajo = coalesce($7, trabajo), intereses = coalesce($8, intereses),
				intenciones = coalesce($9, intenciones), prompts = coalesce($10, prompts),
				fotos = coalesce($11, fotos), actualizado_en = now(),
				origen = CASE WHEN origen = '' THEN coalesce($12, '') ELSE origen END
			WHERE id = $1`, usuarioID, c.Nombre, edad, c.Zona, c.Ciudad, c.Bio, c.Trabajo,
			c.Intereses, c.Intenciones, prompts, c.Fotos, c.Origen); err != nil {
			return err
		}
		if nacimiento == nil && c.ContactoConfianza == nil && c.Genero == nil && c.Diversidad == nil && cerca == nil && gente == nil {
			return nil
		}
		_, err := tx.Exec(ctx, `
			INSERT INTO usuarios_privado (usuario_id, nacimiento, contacto_confianza, genero, diversidad, avisos_cerca, avisos_gente)
			VALUES ($1, $2, coalesce($3, ''), coalesce($4, ''), coalesce($5, false), coalesce($6, true), coalesce($7, true))
			ON CONFLICT (usuario_id) DO UPDATE SET nacimiento = coalesce($2, usuarios_privado.nacimiento),
				contacto_confianza = coalesce($3, usuarios_privado.contacto_confianza),
				genero = coalesce($4, usuarios_privado.genero), diversidad = coalesce($5, usuarios_privado.diversidad),
				avisos_cerca = coalesce($6, usuarios_privado.avisos_cerca), avisos_gente = coalesce($7, usuarios_privado.avisos_gente)`,
			usuarioID, nacimiento, c.ContactoConfianza, c.Genero, c.Diversidad, cerca, gente)
		return err
	})
	if err != nil {
		return Perfil{}, err
	}
	return s.Yo(ctx, usuarioID)
}

// SumarAGente suma a alguien a mi gente. Sólo se puede con quien ya compartí un plan.
func (s *Servicio) SumarAGente(ctx context.Context, usuarioID, otroID string) error {
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var compartieron bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM solicitudes a JOIN solicitudes b ON a.plan_id = b.plan_id
			WHERE a.usuario_id = $1 AND b.usuario_id::text = $2 AND a.estado = 'aceptada' AND b.estado = 'aceptada')`,
			usuarioID, otroID).Scan(&compartieron); err != nil {
			return err
		}
		if !compartieron {
			return problema.Conflicto("sin_plan", "Podés sumar a tu gente a quienes compartieron un plan con vos.")
		}
		_, err := tx.Exec(ctx, `INSERT INTO mi_gente (usuario_id, otro_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`, usuarioID, otroID)
		return err
	})
}

// SacarDeGente saca a alguien de mi gente.
func (s *Servicio) SacarDeGente(ctx context.Context, usuarioID, otroID string) error {
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `DELETE FROM mi_gente WHERE usuario_id = $1 AND otro_id::text = $2`, usuarioID, otroID)
		return err
	})
}

// MiGente lista a mi gente.
func (s *Servicio) MiGente(ctx context.Context, usuarioID string) ([]Perfil, error) {
	out := []Perfil{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT `+columnas+` FROM mi_gente g JOIN usuarios u ON u.id = g.otro_id
			LEFT JOIN LATERAL app.asistencia(u.id) rep ON true
			WHERE g.usuario_id = $1 AND u.baja_en IS NULL ORDER BY g.creado_en DESC`, usuarioID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			p, err := escanear(rows)
			if err != nil {
				return err
			}
			p.Equipo = false
			out = append(out, p)
		}
		return rows.Err()
	})
	return out, err
}

// Bloquear: deja de ver y de ser visto. También lo saca de mi gente (en las dos direcciones).
func (s *Servicio) Bloquear(ctx context.Context, usuarioID, otroID string) error {
	if usuarioID == otroID {
		return problema.Invalido("No te podés bloquear.")
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `INSERT INTO bloqueos (usuario_id, bloqueado_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`, usuarioID, otroID); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `DELETE FROM mi_gente WHERE (usuario_id = $1 AND otro_id = $2) OR (usuario_id = $2 AND otro_id = $1)`, usuarioID, otroID)
		return err
	})
}

// Reporte a moderación.
type Reporte struct {
	UsuarioID string `json:"usuarioId"`
	PlanID    string `json:"planId"`
	Motivo    string `json:"motivo"`
	Detalle   string `json:"detalle"`
}

var motivos = map[string]bool{"acoso": true, "falso": true, "no_vino": true, "inapropiado": true, "inseguro": true, "otro": true}

// Reportar manda el reporte a moderación.
func (s *Servicio) Reportar(ctx context.Context, usuarioID string, r Reporte) error {
	if !motivos[r.Motivo] {
		return problema.Invalido("Elegí un motivo.")
	}
	if r.UsuarioID == "" && r.PlanID == "" {
		return problema.Invalido("Decí a quién o qué plan reportás.")
	}
	if len([]rune(r.Detalle)) > 1000 {
		return problema.Invalido("El detalle puede tener hasta 1000 letras.")
	}
	nulo := func(v string) *string {
		if v == "" {
			return nil
		}
		return &v
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `INSERT INTO reportes (usuario_id, sobre_usuario_id, sobre_plan_id, motivo, detalle)
			VALUES ($1, $2, $3, $4, $5)`, usuarioID, nulo(r.UsuarioID), nulo(r.PlanID), r.Motivo, strings.TrimSpace(r.Detalle))
		return err
	})
}

var reOrigen = regexp.MustCompile(`^[A-Za-z0-9:_./-]*$`)

// Bloqueado es alguien que bloqueé.
type Bloqueado struct {
	ID     string `json:"id"`
	Nombre string `json:"nombre"`
	Foto   string `json:"foto"`
}

// Bloqueos lista a quienes bloqueé (el último primero).
func (s *Servicio) Bloqueos(ctx context.Context, usuarioID string) ([]Bloqueado, error) {
	out := []Bloqueado{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT u.id, u.nombre, coalesce(u.fotos[1], '') FROM bloqueos b
			JOIN usuarios u ON u.id = b.bloqueado_id WHERE b.usuario_id = $1 ORDER BY b.creado_en DESC`, usuarioID)
		if err != nil {
			return err
		}
		out, err = pgx.CollectRows(rows, pgx.RowToStructByPos[Bloqueado])
		if out == nil {
			out = []Bloqueado{}
		}
		return err
	})
	return out, err
}

// Desbloquear saca un bloqueo propio (no devuelve a nadie a mi gente).
func (s *Servicio) Desbloquear(ctx context.Context, usuarioID, otroID string) error {
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `DELETE FROM bloqueos WHERE usuario_id = $1 AND bloqueado_id::text = $2`, usuarioID, otroID)
		return err
	})
}

// Redondeo de la ubicación: 0,01° (~1 km). Es todo lo que se guarda.
func redondear(x float64) float64 { return math.Round(x*100) / 100 }

// GuardarUbicacion guarda la ubicación aproximada de la persona (la app la manda al abrir). El
// worker la borra a las 24 h; sólo sirve para el aviso de planes cerca.
func (s *Servicio) GuardarUbicacion(ctx context.Context, usuarioID string, lat, lng *float64) error {
	if lat == nil || lng == nil || *lat < -90 || *lat > 90 || *lng < -180 || *lng > 180 || (*lat == 0 && *lng == 0) {
		return problema.Invalido("Ubicación inválida.")
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `INSERT INTO ubicaciones (usuario_id, lat, lng, actualizada_en) VALUES ($1, $2, $3, now())
			ON CONFLICT (usuario_id) DO UPDATE SET lat = excluded.lat, lng = excluded.lng, actualizada_en = now()`,
			usuarioID, redondear(*lat), redondear(*lng))
		return err
	})
}
