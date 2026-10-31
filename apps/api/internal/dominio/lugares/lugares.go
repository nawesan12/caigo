// Package lugares: el panel para lugares aliados (lugares.caigo.com.ar). Un lugar se registra solo
// y el equipo de Caigo lo aprueba; después publica planes gratis o con entrada/seña (cobra con su
// Mercado Pago, sin comisión: el cargo por servicio lo paga quien compra), valida entradas en la
// puerta, maneja sus placas Caigo Tap y su pase de socio.
package lugares

import (
	"context"
	"errors"
	"regexp"
	"strings"
	"time"
	"unicode"

	"github.com/jackc/pgx/v5"
	"golang.org/x/text/unicode/norm"

	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/cifrado"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/firma"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Servicio de lugares.
type Servicio struct {
	App *db.Pool
	// Mercado Pago (app de Caigo). nil o sin AppID: los planes con cobro no se pueden publicar.
	MP *mercadopago.Config
	// Cifrado de los tokens de Mercado Pago de cada lugar.
	Cifra *cifrado.Cifrador
	// Firma de las placas comunes (sin NTAG 424).
	Firma firma.Firmador
	// Sitio público (caigo.com.ar), panel (lugares.caigo.com.ar) y API pública.
	Sitio, Panel, APIPublica string
	Reloj                    func() time.Time
}

func (s *Servicio) ahora() time.Time {
	if s.Reloj != nil {
		return s.Reloj()
	}
	return time.Now()
}

// Lugar como lo ve el panel.
type Lugar struct {
	ID             string     `json:"id"`
	Slug           string     `json:"slug"`
	Nombre         string     `json:"nombre"`
	Rubro          string     `json:"rubro"`
	Ciudad         string     `json:"ciudad"`
	Zona           string     `json:"zona"`
	Direccion      string     `json:"direccion"`
	Lat            float64    `json:"lat"`
	Lng            float64    `json:"lng"`
	Foto           string     `json:"foto"`
	Instagram      string     `json:"instagram"`
	Telefono       string     `json:"telefono"`
	Estado         string     `json:"estado"`
	Motivo         string     `json:"motivo,omitempty"`
	SocioActivo    bool       `json:"socioActivo"`
	SocioVisitas   int16      `json:"socioVisitas"`
	SocioBeneficio string     `json:"socioBeneficio"`
	Rol            string     `json:"rol,omitempty"`
	MPConectado    bool       `json:"mpConectado"`
	MPDesde        *time.Time `json:"mpDesde,omitempty"`
	CreadoEn       time.Time  `json:"creadoEn"`
}

const columnas = `l.id, l.slug, l.nombre, l.rubro, l.ciudad, l.zona, l.direccion, l.lat, l.lng, l.foto,
	l.instagram, l.telefono, l.estado, l.motivo, l.socio_activo, l.socio_visitas, l.socio_beneficio,
	coalesce(m.rol, ''), c.lugar_id IS NOT NULL, c.conectada_en, l.creado_en`

const desde = ` FROM lugares l LEFT JOIN lugar_miembros m ON m.lugar_id = l.id AND m.usuario_id = $1
	LEFT JOIN mp_conexiones c ON c.lugar_id = l.id`

func escanear(row pgx.Row) (Lugar, error) {
	var l Lugar
	err := row.Scan(&l.ID, &l.Slug, &l.Nombre, &l.Rubro, &l.Ciudad, &l.Zona, &l.Direccion, &l.Lat, &l.Lng,
		&l.Foto, &l.Instagram, &l.Telefono, &l.Estado, &l.Motivo, &l.SocioActivo, &l.SocioVisitas,
		&l.SocioBeneficio, &l.Rol, &l.MPConectado, &l.MPDesde, &l.CreadoEn)
	return l, err
}

// Datos del lugar (alta y edición).
type Datos struct {
	Nombre    string  `json:"nombre"`
	Rubro     string  `json:"rubro"`
	Ciudad    string  `json:"ciudad"`
	Zona      string  `json:"zona"`
	Direccion string  `json:"direccion"`
	Lat       float64 `json:"lat"`
	Lng       float64 `json:"lng"`
	Foto      string  `json:"foto"`
	Instagram string  `json:"instagram"`
	Telefono  string  `json:"telefono"`
}

func (d *Datos) validar() error {
	var campos []problema.Campo
	mal := func(r, m string) { campos = append(campos, problema.Campo{Ruta: r, Mensaje: m}) }
	for _, v := range []*string{&d.Nombre, &d.Rubro, &d.Ciudad, &d.Zona, &d.Direccion, &d.Foto, &d.Instagram, &d.Telefono} {
		*v = strings.TrimSpace(*v)
	}
	d.Instagram = strings.TrimPrefix(d.Instagram, "@")
	if d.Nombre == "" || len([]rune(d.Nombre)) > 80 {
		mal("nombre", "Poné el nombre del lugar.")
	}
	if d.Ciudad == "" {
		mal("ciudad", "Decí la ciudad.")
	}
	if d.Direccion == "" || len([]rune(d.Direccion)) > 160 {
		mal("direccion", "Poné la dirección.")
	}
	if d.Lat == 0 && d.Lng == 0 {
		mal("lat", "Marcá el lugar en el mapa.")
	}
	if len([]rune(d.Rubro)) > 40 || len([]rune(d.Zona)) > 60 || len(d.Instagram) > 60 || len(d.Telefono) > 30 || len(d.Foto) > 500 {
		mal("nombre", "Algún dato es muy largo.")
	}
	if len(campos) > 0 {
		return problema.Invalido("Revisá los datos del lugar.", campos...)
	}
	return nil
}

var noSlug = regexp.MustCompile(`[^a-z0-9]+`)

func slugDe(nombre string) string {
	var b strings.Builder
	for _, r := range norm.NFD.String(strings.ToLower(nombre)) {
		if !unicode.Is(unicode.Mn, r) {
			b.WriteRune(r)
		}
	}
	s := strings.Trim(noSlug.ReplaceAllString(b.String(), "-"), "-")
	if len(s) > 40 {
		s = strings.Trim(s[:40], "-")
	}
	if s == "" {
		s = "lugar"
	}
	return s
}

// Registrar da de alta un lugar (pendiente de aprobación) con la persona como dueña.
func (s *Servicio) Registrar(ctx context.Context, usuarioID string, d Datos) (Lugar, error) {
	if err := d.validar(); err != nil {
		return Lugar{}, err
	}
	var id string
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM lugar_miembros WHERE usuario_id = $1`, usuarioID).Scan(&n); err != nil {
			return err
		}
		if n >= 5 {
			return problema.Conflicto("demasiados", "Ya manejás 5 lugares. Escribinos para sumar más.")
		}
		base := slugDe(d.Nombre)
		slug := base
		for i := 2; ; i++ {
			var existe bool
			if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM lugares WHERE slug = $1)`, slug).Scan(&existe); err != nil {
				return err
			}
			if !existe {
				break
			}
			slug = base + "-" + mercadopago.Azar()[:4]
			slug = strings.ToLower(noSlug.ReplaceAllString(slug, ""))
			if i > 20 {
				return problema.Conflicto("slug", "Probá con otro nombre.")
			}
		}
		if err := tx.QueryRow(ctx, `INSERT INTO lugares (slug, nombre, rubro, ciudad, zona, direccion, lat, lng, foto, instagram, telefono)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING id`,
			slug, d.Nombre, d.Rubro, d.Ciudad, d.Zona, d.Direccion, d.Lat, d.Lng, d.Foto, d.Instagram, d.Telefono).Scan(&id); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `INSERT INTO lugar_miembros (lugar_id, usuario_id, rol) VALUES ($1, $2, 'dueno')`, id, usuarioID)
		return err
	})
	if err != nil {
		return Lugar{}, err
	}
	return s.Ver(ctx, usuarioID, id)
}

// Mios lista los lugares que maneja la persona.
func (s *Servicio) Mios(ctx context.Context, usuarioID string) ([]Lugar, error) {
	out := []Lugar{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT `+columnas+desde+` WHERE m.usuario_id IS NOT NULL ORDER BY l.creado_en`, usuarioID)
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

// miembro verifica que la persona maneje el lugar (y, si aprobado, que esté aprobado).
func miembro(ctx context.Context, tx pgx.Tx, lugarID, usuarioID string, aprobado bool) error {
	var estado string
	err := tx.QueryRow(ctx, `SELECT l.estado FROM lugares l JOIN lugar_miembros m ON m.lugar_id = l.id
		WHERE l.id::text = $1 AND m.usuario_id = $2`, lugarID, usuarioID).Scan(&estado)
	if errors.Is(err, pgx.ErrNoRows) {
		return problema.NoEncontrado("Ese lugar no es tuyo.")
	}
	if err != nil {
		return err
	}
	if aprobado && estado != "aprobado" {
		return problema.Conflicto("sin_aprobar", "Tu lugar todavía no está aprobado. Te avisamos apenas lo esté.")
	}
	return nil
}

// Ver un lugar propio.
func (s *Servicio) Ver(ctx context.Context, usuarioID, lugarID string) (Lugar, error) {
	var l Lugar
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		var err error
		l, err = escanear(tx.QueryRow(ctx, `SELECT `+columnas+desde+` WHERE l.id::text = $2`, usuarioID, lugarID))
		return err
	})
	return l, err
}

// Editar cambia los datos y el pase de socio.
type Edicion struct {
	Datos
	SocioActivo    *bool   `json:"socioActivo"`
	SocioVisitas   *int16  `json:"socioVisitas"`
	SocioBeneficio *string `json:"socioBeneficio"`
}

// Editar el lugar.
func (s *Servicio) Editar(ctx context.Context, usuarioID, lugarID string, e Edicion) (Lugar, error) {
	if err := e.Datos.validar(); err != nil {
		return Lugar{}, err
	}
	if e.SocioVisitas != nil && (*e.SocioVisitas < 2 || *e.SocioVisitas > 30) {
		return Lugar{}, problema.Invalido("Las visitas para el beneficio van de 2 a 30.")
	}
	if e.SocioBeneficio != nil && len([]rune(*e.SocioBeneficio)) > 80 {
		return Lugar{}, problema.Invalido("El beneficio es muy largo.")
	}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `UPDATE lugares SET nombre = $2, rubro = $3, ciudad = $4, zona = $5, direccion = $6,
			lat = $7, lng = $8, foto = $9, instagram = $10, telefono = $11,
			socio_activo = coalesce($12, socio_activo), socio_visitas = coalesce($13, socio_visitas),
			socio_beneficio = coalesce($14, socio_beneficio) WHERE id::text = $1`,
			lugarID, e.Nombre, e.Rubro, e.Ciudad, e.Zona, e.Direccion, e.Lat, e.Lng, e.Foto, e.Instagram, e.Telefono,
			e.SocioActivo, e.SocioVisitas, e.SocioBeneficio)
		return err
	})
	if err != nil {
		return Lugar{}, err
	}
	return s.Ver(ctx, usuarioID, lugarID)
}

// Pendientes de aprobación (equipo).
func (s *Servicio) Pendientes(ctx context.Context) ([]Lugar, error) {
	out := []Lugar{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT `+columnas+desde+` WHERE l.estado = 'pendiente' ORDER BY l.creado_en`, "00000000-0000-0000-0000-000000000000")
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

// Revisar aprueba o rechaza un lugar (equipo).
func (s *Servicio) Revisar(ctx context.Context, lugarID string, aprobar bool, motivo string) error {
	motivo = strings.TrimSpace(motivo)
	if !aprobar && motivo == "" {
		return problema.Invalido("Decí por qué se rechaza (lo ve el lugar).")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		estado := "rechazado"
		if aprobar {
			estado = "aprobado"
		}
		tag, err := tx.Exec(ctx, `UPDATE lugares SET estado = $2, motivo = $3, aprobado_en = CASE WHEN $2 = 'aprobado' THEN now() END
			WHERE id::text = $1 AND estado = 'pendiente'`, lugarID, estado, motivo)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problema.NoEncontrado("Ese lugar no está pendiente.")
		}
		return nil
	})
}
