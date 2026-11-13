package planes

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// ChequearGenero: un plan "Solo mujeres" pide genero = mujer (autodeclarado, privado, en el
// perfil). Un plan "Solo diversidades" pide que la persona lo confirme en ese mismo pedido
// (confirmaDiversidad): no se guarda en ningún lado ni se deduce del género ni del DNI, porque
// revelaría orientación sexual o identidad de género (dato sensible, art. 2 y 7 de la Ley 25.326).
// Corre como la persona (lee su fila privada). generoPlan es el del plan (cualquiera | mujeres |
// diversidades).
func ChequearGenero(ctx context.Context, tx pgx.Tx, usuarioID, generoPlan string, confirmaDiversidad bool) error {
	switch generoPlan {
	case "diversidades":
		if confirmaDiversidad {
			return nil
		}
		return problema.NoProcesable("confirmar_diversidad", "Este plan es sólo para la diversidad LGBTIQ+. Confirmalo para sumarte.")
	case "mujeres":
	default:
		return nil
	}
	var genero string
	err := tx.QueryRow(ctx, `SELECT genero FROM usuarios_privado WHERE usuario_id = $1`, usuarioID).Scan(&genero)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return err
	}
	switch {
	case genero == "":
		return problema.NoProcesable("falta_genero", "Este plan es sólo para mujeres. Contanos con qué género te identificás.")
	case genero != "mujer":
		return problema.NoProcesable("solo_mujeres", "Este plan es sólo para mujeres.")
	}
	return nil
}
