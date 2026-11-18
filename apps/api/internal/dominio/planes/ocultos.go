package planes

import (
	"context"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Ocultar es «No me interesa» (deslizar la tarjeta a la izquierda): el plan no vuelve a aparecer en
// Ahora ni en Explorar hasta el final del día. El link y Mis planes no cambian.
func (s *Servicio) Ocultar(ctx context.Context, usuarioID, planID string) error {
	if !esUUID(planID) {
		return problema.NoEncontrado("Ese plan no existe.")
	}
	_, hasta := plus.Dia(s.ahora())
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var existe bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM planes WHERE id = $1)`, planID).Scan(&existe); err != nil {
			return err
		}
		if !existe {
			return problema.NoEncontrado("Ese plan no existe.")
		}
		_, err := tx.Exec(ctx, `INSERT INTO planes_ocultos (usuario_id, plan_id, hasta) VALUES ($1, $2, $3)
			ON CONFLICT (usuario_id, plan_id) DO UPDATE SET hasta = excluded.hasta`, usuarioID, planID, hasta)
		return err
	})
}

// Mostrar es «Deshacer»: el plan vuelve a la lista.
func (s *Servicio) Mostrar(ctx context.Context, usuarioID, planID string) error {
	if !esUUID(planID) {
		return problema.NoEncontrado("Ese plan no existe.")
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `DELETE FROM planes_ocultos WHERE usuario_id = $1 AND plan_id = $2`, usuarioID, planID)
		return err
	})
}
