package planes

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/firma"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Check-in: la asistencia real que alimenta la reputación. Se puede hacer desde 30 minutos antes
// hasta una hora después del final, estando a menos de 150 m del lugar o escaneando el QR que
// muestra el organizador.
const (
	checkinAntes   = 30 * time.Minute
	checkinDespues = time.Hour
	checkinRadioM  = 150
)

// Propósitos de los tokens firmados.
const (
	propositoQR   = "checkin"
	propositoVivo = "vivo"
	// PropositoAsiste: el QR de una persona aceptada ("plan~usuario"), para la puerta de un lugar.
	PropositoAsiste = "asiste"
)

// QR devuelve el código de check-in del plan (vale hasta el fin de la ventana). Al organizador, el
// que escanean los que llegan; a alguien aceptado, el suyo, que muestra en la puerta de un lugar
// aliado (el lugar lo valida en POST /v1/lugares/{id}/validar).
func (s *Servicio) QR(ctx context.Context, usuarioID, planID string) (string, error) {
	var fin time.Time
	var org bool
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		err := tx.QueryRow(ctx, `SELECT p.empieza_en + make_interval(mins => p.duracion_min), p.organizador_id = $2
			FROM planes p WHERE p.id::text = $1 AND p.cancelado_en IS NULL AND (p.organizador_id = $2
				OR EXISTS (SELECT 1 FROM solicitudes s WHERE s.plan_id = p.id AND s.usuario_id = $2 AND s.estado = 'aceptada'))`,
			planID, usuarioID).Scan(&fin, &org)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("No estás en ese plan.")
		}
		return err
	})
	if err != nil {
		return "", err
	}
	if !org {
		return s.Firma.Firmar(PropositoAsiste, planID+"~"+usuarioID, fin.Add(checkinDespues)), nil
	}
	return s.Firma.Firmar(propositoQR, planID, fin.Add(checkinDespues)), nil
}

// Llegada es cómo la persona dice que llegó.
type Llegada struct {
	Metodo string   `json:"metodo"` // geo | qr | nfc
	Lat    *float64 `json:"lat"`
	Lng    *float64 `json:"lng"`
	Codigo string   `json:"codigo"`
}

// Checkin marca la llegada al plan.
func (s *Servicio) Checkin(ctx context.Context, usuarioID, planID string, l Llegada) error {
	ahora := s.ahora()
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var empieza time.Time
		var dur int16
		var estado string
		var ya bool
		err := tx.QueryRow(ctx, `SELECT p.empieza_en, p.duracion_min, s.estado, s.checkin_en IS NOT NULL
			FROM planes p JOIN solicitudes s ON s.plan_id = p.id AND s.usuario_id = $2
			WHERE p.id::text = $1 AND p.cancelado_en IS NULL`, planID, usuarioID).Scan(&empieza, &dur, &estado, &ya)
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && estado != "aceptada") {
			return problema.NoEncontrado("No estás en ese plan.")
		}
		if err != nil {
			return err
		}
		if ya {
			return nil
		}
		fin := empieza.Add(time.Duration(dur) * time.Minute)
		if ahora.Before(empieza.Add(-checkinAntes)) {
			return problema.Conflicto("temprano", "Todavía es temprano: el check-in abre media hora antes.")
		}
		if ahora.After(fin.Add(checkinDespues)) {
			return problema.Conflicto("tarde", "El check-in de este plan ya cerró.")
		}
		switch l.Metodo {
		case "geo":
			if l.Lat == nil || l.Lng == nil {
				return problema.Invalido("Falta tu ubicación.")
			}
			var lat, lng float64
			// La dirección exacta: la persona está aceptada, así que RLS la deja ver.
			if err := tx.QueryRow(ctx, `SELECT lat, lng FROM planes_direccion WHERE plan_id::text = $1`, planID).Scan(&lat, &lng); err != nil {
				return err
			}
			if distancia(*l.Lat, *l.Lng, lat, lng) > checkinRadioM {
				return problema.Conflicto("lejos", "Parece que todavía no llegaste. Acercate al lugar o escaneá el QR.")
			}
		case "qr", "nfc":
			id, err := s.Firma.Verificar(propositoQR, strings.TrimSpace(l.Codigo), ahora)
			if errors.Is(err, firma.ErrToken) || id != planID {
				return problema.Conflicto("codigo", "Ese código no es de este plan.")
			}
		default:
			return problema.Invalido("Elegí cómo hacés el check-in.")
		}
		_, err = tx.Exec(ctx, `UPDATE solicitudes SET checkin_en = now(), checkin_metodo = $3
			WHERE plan_id::text = $1 AND usuario_id = $2`, planID, usuarioID, l.Metodo)
		return err
	})
}

// Opinion de "¿Volverías a salir con…?".
type Opinion struct {
	UsuarioID string `json:"usuarioId"`
	Si        bool   `json:"si"`
}

// Repetimos guarda el "¿Repetimos?" después del plan: a quién volvería a ver (privado; los "sí"
// pasan a mi gente). Si el organizador dice que repite, se publica el mismo plan una semana después.
func (s *Servicio) Repetimos(ctx context.Context, usuarioID, planID string, repetir bool, ops []Opinion) (*Plan, error) {
	if len(ops) > 60 {
		return nil, problema.Invalido("Demasiadas personas.")
	}
	var nuevo, existente string
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var org, titulo, zona string
		var empieza time.Time
		err := tx.QueryRow(ctx, `SELECT p.organizador_id, p.empieza_en, p.titulo, p.zona FROM planes p JOIN solicitudes s ON s.plan_id = p.id
			WHERE p.id::text = $1 AND s.usuario_id = $2 AND s.estado = 'aceptada'`, planID, usuarioID).Scan(&org, &empieza, &titulo, &zona)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("No estuviste en ese plan.")
		}
		if err != nil {
			return err
		}
		if empieza.After(s.ahora()) {
			return problema.Conflicto("no_paso", "El plan todavía no pasó.")
		}
		for _, o := range ops {
			if o.UsuarioID == usuarioID {
				continue
			}
			var estaba bool
			if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM solicitudes WHERE plan_id::text = $1 AND usuario_id::text = $2 AND estado = 'aceptada')`,
				planID, o.UsuarioID).Scan(&estaba); err != nil {
				return err
			}
			if !estaba {
				continue
			}
			if _, err := tx.Exec(ctx, `INSERT INTO volveria (plan_id, usuario_id, otro_id, si) VALUES ($1, $2, $3, $4)
				ON CONFLICT (plan_id, usuario_id, otro_id) DO UPDATE SET si = excluded.si`, planID, usuarioID, o.UsuarioID, o.Si); err != nil {
				return err
			}
			if o.Si {
				if _, err := tx.Exec(ctx, `INSERT INTO mi_gente (usuario_id, otro_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`, usuarioID, o.UsuarioID); err != nil {
					return err
				}
			}
		}
		if org != usuarioID {
			return nil
		}
		if !repetir {
			// "Esta vez no": corta la serie (los planes recurrentes dejan de repetirse).
			_, err := tx.Exec(ctx, `UPDATE planes SET recurrente = false WHERE organizador_id = $2 AND recurrente
				AND coalesce(serie_id, id) = (SELECT coalesce(serie_id, id) FROM planes WHERE id::text = $1)
				AND empieza_en >= (SELECT empieza_en FROM planes WHERE id::text = $1)`, planID, usuarioID)
			return err
		}
		// Ya está la semana que viene (la creó el worker o un "Sí" anterior): se devuelve esa.
		err = tx.QueryRow(ctx, `SELECT n.id FROM planes v JOIN planes n ON coalesce(n.serie_id, n.id) = coalesce(v.serie_id, v.id)
			AND n.empieza_en = v.empieza_en + interval '7 days' WHERE v.id::text = $1 LIMIT 1`, planID).Scan(&existente)
		if err == nil {
			return nil
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		// Mismo plan, una semana después, en la misma serie. Arranca con el grupo del organizador.
		return tx.QueryRow(ctx, `
			WITH viejo AS (SELECT * FROM planes WHERE id::text = $1),
			n AS (
				INSERT INTO planes (slug, organizador_id, titulo, categoria, descripcion, empieza_en, duracion_min,
					recurrente, serie_id, ciudad, zona, lugar_nombre, lat, lng, somos, total, ingreso, visibilidad,
					solo_verificados, ocultar_direccion, edad_min, edad_max, genero, nivel, costo_centavos, foto)
				SELECT $2, organizador_id, titulo, categoria, descripcion, empieza_en + interval '7 days', duracion_min,
					true, coalesce(serie_id, id), ciudad, zona, lugar_nombre, lat, lng, somos, total, ingreso, visibilidad,
					solo_verificados, ocultar_direccion, edad_min, edad_max, genero, nivel, costo_centavos, foto
				FROM viejo RETURNING id, somos)
			SELECT id FROM n`, planID, slug(titulo, zona)).Scan(&nuevo)
	})
	if err == nil && existente != "" {
		p, err := s.Ver(ctx, usuarioID, existente, nil)
		return &p, err
	}
	if err != nil || nuevo == "" {
		return nil, err
	}
	err = s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `INSERT INTO solicitudes (plan_id, usuario_id, cantidad, estado, respondida_en)
			SELECT $1, organizador_id, somos, 'aceptada', now() FROM planes WHERE id = $1`, nuevo); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO planes_direccion (plan_id, direccion, lat, lng)
			SELECT $1, direccion, lat, lng FROM planes_direccion WHERE plan_id::text = $2`, nuevo, planID); err != nil {
			return err
		}
		return Publicado(ctx, tx, nuevo)
	})
	if err != nil {
		return nil, err
	}
	p, err := s.Ver(ctx, usuarioID, nuevo, nil)
	return &p, err
}

// LinkEnVivo arma el link para "Compartir mi plan en vivo" con un contacto de confianza: muestra
// el plan, dónde y con quién, hasta que termina.
func (s *Servicio) LinkEnVivo(ctx context.Context, usuarioID, planID string) (string, error) {
	var fin time.Time
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		err := tx.QueryRow(ctx, `SELECT p.empieza_en + make_interval(mins => p.duracion_min) FROM planes p
			JOIN solicitudes s ON s.plan_id = p.id WHERE p.id::text = $1 AND s.usuario_id = $2 AND s.estado = 'aceptada'`,
			planID, usuarioID).Scan(&fin)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("No estás en ese plan.")
		}
		return err
	})
	if err != nil {
		return "", err
	}
	tok := s.Firma.Firmar(propositoVivo, planID+"~"+usuarioID, fin.Add(2*time.Hour))
	return strings.TrimRight(s.Sitio, "/") + "/vivo/" + tok, nil
}

// EnVivo es lo que ve el contacto de confianza.
type EnVivo struct {
	Quien     string    `json:"quien"`
	Plan      string    `json:"plan"`
	EmpiezaEn time.Time `json:"empiezaEn"`
	TerminaEn time.Time `json:"terminaEn"`
	Lugar     string    `json:"lugar"`
	Direccion string    `json:"direccion"`
	Lat       float64   `json:"lat"`
	Lng       float64   `json:"lng"`
	Con       []string  `json:"con"`
	Llego     bool      `json:"llego"`
}

// VerEnVivo lee el link (sin sesión).
func (s *Servicio) VerEnVivo(ctx context.Context, token string) (EnVivo, error) {
	dato, err := s.Firma.Verificar(propositoVivo, token, s.ahora())
	partes := strings.Split(dato, "~")
	if err != nil || len(partes) != 2 {
		return EnVivo{}, problema.NoEncontrado("Este link ya no está activo.")
	}
	var v EnVivo
	err = s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var dur int16
		if err := tx.QueryRow(ctx, `SELECT u.nombre, p.titulo, p.empieza_en, p.duracion_min, p.lugar_nombre, d.direccion, d.lat, d.lng,
				s.checkin_en IS NOT NULL
			FROM planes p JOIN planes_direccion d ON d.plan_id = p.id
			JOIN solicitudes s ON s.plan_id = p.id AND s.usuario_id::text = $2 JOIN usuarios u ON u.id = s.usuario_id
			WHERE p.id::text = $1`, partes[0], partes[1]).Scan(&v.Quien, &v.Plan, &v.EmpiezaEn, &dur, &v.Lugar,
			&v.Direccion, &v.Lat, &v.Lng, &v.Llego); err != nil {
			return err
		}
		v.TerminaEn = v.EmpiezaEn.Add(time.Duration(dur) * time.Minute)
		rows, err := tx.Query(ctx, `SELECT u.nombre FROM solicitudes s JOIN usuarios u ON u.id = s.usuario_id
			WHERE s.plan_id::text = $1 AND s.estado = 'aceptada' AND s.usuario_id::text <> $2 ORDER BY s.creada_en`, partes[0], partes[1])
		if err != nil {
			return err
		}
		v.Con, err = pgx.CollectRows(rows, pgx.RowTo[string])
		return err
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return v, problema.NoEncontrado("Este link ya no está activo.")
	}
	return v, err
}
