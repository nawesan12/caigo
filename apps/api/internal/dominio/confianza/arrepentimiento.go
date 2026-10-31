package confianza

import (
	"context"
	"crypto/rand"
	"errors"
	"math/big"
	"net/mail"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Arrepentimiento: el pedido de revocar una compra hecha en caigo.com.ar (botón de
// arrepentimiento, Resolución 424/2020). No pide cuenta.
type Arrepentimiento struct {
	ID         string     `json:"id"`
	Codigo     string     `json:"codigo"`
	Nombre     string     `json:"nombre"`
	Email      string     `json:"email"`
	Compra     string     `json:"compra"`
	CreadoEn   time.Time  `json:"creadoEn"`
	AtendidoEn *time.Time `json:"atendidoEn,omitempty"`
	Nota       string     `json:"nota,omitempty"`
}

const letrasCodigo = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

func codigoArrepentimiento() string {
	b := make([]byte, 6)
	for i := range b {
		n, _ := rand.Int(rand.Reader, big.NewInt(int64(len(letrasCodigo))))
		b[i] = letrasCodigo[n.Int64()]
	}
	return "ARR-" + string(b)
}

// Arrepentirse registra el pedido y devuelve el código de trámite. Avisa al equipo.
func (s *Servicio) Arrepentirse(ctx context.Context, nombre, email, compra string) (string, error) {
	nombre, email, compra = strings.TrimSpace(nombre), strings.TrimSpace(email), strings.TrimSpace(compra)
	var campos []problema.Campo
	if nombre == "" || len([]rune(nombre)) > 80 {
		campos = append(campos, problema.Campo{Ruta: "nombre", Mensaje: "Poné tu nombre."})
	}
	if a, err := mail.ParseAddress(email); err != nil || a.Address != email || len(email) > 200 {
		campos = append(campos, problema.Campo{Ruta: "email", Mensaje: "Revisá el mail."})
	}
	if compra == "" || len([]rune(compra)) > 500 {
		campos = append(campos, problema.Campo{Ruta: "compra", Mensaje: "Contanos qué compraste y cuándo."})
	}
	if len(campos) > 0 {
		return "", problema.Invalido("Revisá los datos.", campos...)
	}
	var codigo string
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		for i := 0; ; i++ {
			codigo = codigoArrepentimiento()
			var id string
			err := tx.QueryRow(ctx, `INSERT INTO arrepentimientos (codigo, nombre, email, compra) VALUES ($1, $2, $3, $4)
				RETURNING id`, codigo, nombre, email, compra).Scan(&id)
			var pe *pgconn.PgError
			if errors.As(err, &pe) && pe.Code == "23505" && i < 5 {
				continue
			}
			if err != nil {
				return err
			}
			return outbox.Publicar(ctx, tx, "arrepentimiento.nuevo", map[string]any{"id": id, "codigo": codigo})
		}
	})
	return codigo, err
}

// Arrepentimientos: los pendientes primero y los atendidos de los últimos 30 días.
func (s *Servicio) Arrepentimientos(ctx context.Context) ([]Arrepentimiento, error) {
	out := []Arrepentimiento{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT id, codigo, nombre, email, compra, creado_en, atendido_en, nota
			FROM arrepentimientos WHERE atendido_en IS NULL OR atendido_en > now() - interval '30 days'
			ORDER BY atendido_en IS NOT NULL, creado_en LIMIT 200`)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var a Arrepentimiento
			if err := rows.Scan(&a.ID, &a.Codigo, &a.Nombre, &a.Email, &a.Compra, &a.CreadoEn, &a.AtendidoEn, &a.Nota); err != nil {
				return err
			}
			out = append(out, a)
		}
		return rows.Err()
	})
	return out, err
}

// AtenderArrepentimiento lo marca como atendido (con lo que se hizo: reembolso, rechazo y por qué).
func (s *Servicio) AtenderArrepentimiento(ctx context.Context, equipoID, id, nota string) error {
	nota = strings.TrimSpace(nota)
	if len([]rune(nota)) > 500 {
		return problema.Invalido("La nota es de hasta 500 letras.")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `UPDATE arrepentimientos SET atendido_en = now(), atendido_por = $2, nota = $3
			WHERE id::text = $1 AND atendido_en IS NULL`, id, equipoID, nota)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problema.NoEncontrado("Ese pedido no existe o ya se atendió.")
		}
		return nil
	})
}
