// Package personas: el perfil propio y el de los demás, mi gente, bloqueos y reportes. Los perfiles
// son livianos (foto, nombre, edad, intereses, reputación y planes compartidos): sin feed ni
// seguidores.
package personas

import (
	"context"
	"encoding/json"
	"errors"
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
		err = tx.QueryRow(ctx, `SELECT coalesce(telefono, '') FROM usuarios_privado WHERE usuario_id = $1`, usuarioID).Scan(&p.Telefono)
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
				fotos = coalesce($11, fotos), actualizado_en = now()
			WHERE id = $1`, usuarioID, c.Nombre, edad, c.Zona, c.Ciudad, c.Bio, c.Trabajo,
			c.Intereses, c.Intenciones, prompts, c.Fotos); err != nil {
			return err
		}
		if nacimiento == nil && c.ContactoConfianza == nil {
			return nil
		}
		_, err := tx.Exec(ctx, `
			INSERT INTO usuarios_privado (usuario_id, nacimiento, contacto_confianza)
			VALUES ($1, $2, coalesce($3, ''))
			ON CONFLICT (usuario_id) DO UPDATE SET nacimiento = coalesce($2, usuarios_privado.nacimiento),
				contacto_confianza = coalesce($3, usuarios_privado.contacto_confianza)`,
			usuarioID, nacimiento, c.ContactoConfianza)
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
