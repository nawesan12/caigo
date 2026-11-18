package logros

import (
	"context"
	"crypto/rand"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// «Traé a alguien»: el premio (una semana de Plus para los dos) llega recién cuando quien entró con
// el código tiene el DNI aprobado y su primer plan que cuenta. Tope: PremiosPorMes a quien invita.
const (
	PremiosPorMes = 10
	// Hasta cuántos días después del alta se puede poner el código de quien te invitó.
	DiasParaUsarCodigo = 14
)

// Servicio de logros y referidos.
type Servicio struct {
	App *db.Pool
	// Sitio público: el link de invitación es <sitio>/i/<código>.
	Sitio string
	Reloj func() time.Time
}

func (s *Servicio) ahora() time.Time {
	if s.Reloj != nil {
		return s.Reloj()
	}
	return time.Now()
}

// Invitacion es lo que ve quien invita.
type Invitacion struct {
	Codigo string `json:"codigo"`
	Link   string `json:"link"`
	// Entraron con su código y cuántos ya fueron a su primer plan (y les dio el premio).
	Invitados int `json:"invitados"`
	Premiados int `json:"premiados"`
	// Puede poner el código de quien la invitó (cuenta nueva sin código puesto).
	PuedeUsarCodigo bool `json:"puedeUsarCodigo"`
}

const letrasCodigo = "abcdefghjkmnpqrstuvwxyz23456789"

func nuevoCodigo() string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	for i := range b {
		b[i] = letrasCodigo[int(b[i])%len(letrasCodigo)]
	}
	return string(b)
}

// Invitar devuelve el código (lo crea la primera vez) y cómo va.
func (s *Servicio) Invitar(ctx context.Context, usuarioID string) (Invitacion, error) {
	var inv Invitacion
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var codigo *string
		var creado time.Time
		if err := tx.QueryRow(ctx, `SELECT codigo, creado_en FROM usuarios WHERE id = $1`, usuarioID).Scan(&codigo, &creado); err != nil {
			return err
		}
		for intento := 0; codigo == nil && intento < 5; intento++ {
			c := nuevoCodigo()
			_, err := tx.Exec(ctx, `SAVEPOINT codigo`)
			if err != nil {
				return err
			}
			if _, err = tx.Exec(ctx, `UPDATE usuarios SET codigo = $2 WHERE id = $1 AND codigo IS NULL`, usuarioID, c); err != nil {
				var pg *pgconn.PgError
				if errors.As(err, &pg) && pg.Code == "23505" {
					if _, err := tx.Exec(ctx, `ROLLBACK TO SAVEPOINT codigo`); err != nil {
						return err
					}
					continue
				}
				return err
			}
			codigo = &c
		}
		if codigo == nil {
			return errors.New("no se pudo armar el código")
		}
		inv.Codigo = *codigo
		inv.Link = strings.TrimRight(s.Sitio, "/") + "/i/" + inv.Codigo
		var usado bool
		if err := tx.QueryRow(ctx, `SELECT
				(SELECT count(*) FROM referidos WHERE referente_id = $1)::integer,
				(SELECT count(*) FROM referidos WHERE referente_id = $1 AND premiado_en IS NOT NULL)::integer,
				EXISTS (SELECT 1 FROM referidos WHERE referido_id = $1)`, usuarioID).Scan(&inv.Invitados, &inv.Premiados, &usado); err != nil {
			return err
		}
		inv.PuedeUsarCodigo = !usado && s.ahora().Sub(creado) < DiasParaUsarCodigo*24*time.Hour
		return nil
	})
	return inv, err
}

// UsarCodigo anota que a la persona la invitó el dueño del código.
func (s *Servicio) UsarCodigo(ctx context.Context, usuarioID, codigo string) error {
	codigo = strings.ToLower(strings.TrimSpace(codigo))
	if !codigoValido(codigo) {
		return problema.Invalido("Ese código no existe.")
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var creado time.Time
		if err := tx.QueryRow(ctx, `SELECT creado_en FROM usuarios WHERE id = $1`, usuarioID).Scan(&creado); err != nil {
			return err
		}
		if s.ahora().Sub(creado) >= DiasParaUsarCodigo*24*time.Hour {
			return problema.Conflicto("tarde", "El código de quien te invitó se pone en tus primeros 14 días.")
		}
		var referente string
		err := tx.QueryRow(ctx, `SELECT id::text FROM usuarios WHERE codigo = $1 AND suspendido_en IS NULL AND baja_en IS NULL`, codigo).Scan(&referente)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Ese código no existe.")
		}
		if err != nil {
			return err
		}
		if referente == usuarioID {
			return problema.Conflicto("propio", "Ese es tu código: pasáselo a quien quieras invitar.")
		}
		tag, err := tx.Exec(ctx, `INSERT INTO referidos (referido_id, referente_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`, usuarioID, referente)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problema.Conflicto("ya_invitado", "Ya pusiste el código de quien te invitó.")
		}
		return nil
	})
}

// PremiarReferidos da la semana de Plus a los dos cuando el referido cumplió (DNI aprobado y un plan
// que cuenta). Lo corre el worker; es idempotente. Devuelve cuántos premió.
func PremiarReferidos(ctx context.Context, app *db.Pool, ahora time.Time) (int, error) {
	n := 0
	err := app.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT r.referido_id::text, r.referente_id::text FROM referidos r
			JOIN usuarios u ON u.id = r.referido_id
			WHERE r.premiado_en IS NULL AND u.dni_en IS NOT NULL AND u.suspendido_en IS NULL AND u.baja_en IS NULL
				AND EXISTS (SELECT 1 FROM solicitudes s WHERE s.usuario_id = r.referido_id AND s.checkin_en IS NOT NULL)
			ORDER BY r.creado_en LIMIT 200 FOR UPDATE OF r SKIP LOCKED`)
		if err != nil {
			return err
		}
		type par struct{ referido, referente string }
		pares, err := pgx.CollectRows(rows, func(r pgx.CollectableRow) (par, error) {
			var p par
			err := r.Scan(&p.referido, &p.referente)
			return p, err
		})
		if err != nil {
			return err
		}
		inicioMes := time.Date(ahora.In(argentina).Year(), ahora.In(argentina).Month(), 1, 0, 0, 0, 0, argentina)
		for _, p := range pares {
			cuentan, err := PlanesQueCuentan(ctx, tx, p.referido)
			if err != nil {
				return err
			}
			if cuentan == 0 {
				continue
			}
			var delMes int
			if err := tx.QueryRow(ctx, `SELECT count(*)::integer FROM referidos WHERE referente_id = $1
				AND premiado_en >= $2 AND NOT sin_premio_referente`, p.referente, inicioMes).Scan(&delMes); err != nil {
				return err
			}
			sinPremio := delMes >= PremiosPorMes
			if err := darSemana(ctx, tx, p.referido, "a-"+p.referido, ahora); err != nil {
				return err
			}
			if !sinPremio {
				if err := darSemana(ctx, tx, p.referente, "de-"+p.referido, ahora); err != nil {
					return err
				}
			}
			if _, err := tx.Exec(ctx, `UPDATE referidos SET premiado_en = $2, sin_premio_referente = $3 WHERE referido_id = $1`,
				p.referido, ahora, sinPremio); err != nil {
				return err
			}
			n++
		}
		return nil
	})
	return n, err
}

// darSemana suma una semana de Plus: arranca cuando vence lo que ya tiene (o ahora).
func darSemana(ctx context.Context, tx pgx.Tx, usuarioID, externo string, ahora time.Time) error {
	_, err := tx.Exec(ctx, `INSERT INTO suscripciones (usuario_id, origen, externo, plan, estado, vence_en, renueva)
		SELECT $1, 'referido', $2, 'semana', 'activa',
			greatest($3::timestamptz, coalesce((SELECT max(vence_en) FROM suscripciones
				WHERE usuario_id = $1 AND estado IN ('activa', 'prueba', 'cancelada')), $3::timestamptz)) + interval '7 days',
			false
		ON CONFLICT (origen, externo) DO NOTHING`, usuarioID, externo, ahora)
	return err
}
