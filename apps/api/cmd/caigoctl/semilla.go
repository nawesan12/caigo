package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
)

// planSemilla es un plan del archivo: quién lo organiza (su id de Clerk; tiene que haber entrado
// una vez a Caigo) y los mismos campos que POST /v1/planes.
type planSemilla struct {
	Organizador string `json:"organizador"`
	planes.Entrada
}

// semilla publica los planes del archivo como si los creara cada organizador desde la app.
func semilla(ctx context.Context, url, archivo string) error {
	b, err := os.ReadFile(archivo)
	if err != nil {
		return err
	}
	var ps []planSemilla
	if err := json.Unmarshal(b, &ps); err != nil {
		return fmt.Errorf("el archivo no es una lista de planes: %w", err)
	}
	app, err := db.Abrir(ctx, url)
	if err != nil {
		return err
	}
	defer app.Close()
	s := &planes.Servicio{App: app}
	for i, p := range ps {
		var id string
		if err := app.EnTx(ctx, func(tx pgx.Tx) error {
			return tx.QueryRow(ctx, `SELECT id FROM usuarios WHERE clerk_id = $1 AND baja_en IS NULL`, p.Organizador).Scan(&id)
		}); err != nil {
			return fmt.Errorf("plan %d: no encontré a %s (¿entró alguna vez a Caigo?)", i+1, p.Organizador)
		}
		plan, err := s.Crear(ctx, id, p.Entrada)
		if err != nil {
			return fmt.Errorf("plan %d (%s): %w", i+1, p.Titulo, err)
		}
		fmt.Printf("publicado: %s → https://caigo.com.ar/p/%s\n", plan.Titulo, plan.Slug)
	}
	return nil
}
