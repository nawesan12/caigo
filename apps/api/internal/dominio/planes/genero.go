package planes

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// ChequearGenero: un plan "Solo mujeres" pide genero = mujer; "Solo diversidades", diversidad o
// genero = no_binario. Corre como la persona (lee su fila privada). generoPlan es el del plan
// (cualquiera | mujeres | diversidades).
func ChequearGenero(ctx context.Context, tx pgx.Tx, usuarioID, generoPlan string) error {
	if generoPlan != "mujeres" && generoPlan != "diversidades" {
		return nil
	}
	var genero string
	var diversidad bool
	err := tx.QueryRow(ctx, `SELECT genero, diversidad FROM usuarios_privado WHERE usuario_id = $1`, usuarioID).Scan(&genero, &diversidad)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return err
	}
	if generoPlan == "mujeres" {
		switch {
		case genero == "":
			return problema.NoProcesable("falta_genero", "Este plan es sólo para mujeres. Contanos con qué género te identificás.")
		case genero != "mujer":
			return problema.NoProcesable("solo_mujeres", "Este plan es sólo para mujeres.")
		}
		return nil
	}
	switch {
	case diversidad || genero == "no_binario":
		return nil
	case genero == "":
		return problema.NoProcesable("falta_genero", "Este plan es sólo para diversidades. Contanos con qué género te identificás.")
	}
	return problema.NoProcesable("solo_diversidades", "Este plan es sólo para diversidades.")
}
