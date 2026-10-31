// Comando caigoctl: tareas de operación de la base.
//
//	caigoctl bootstrap   crea roles y base (superusuario: CAIGO_BASE_DE_DATOS_ADMIN)
//	caigoctl migrar      aplica las migraciones (CAIGO_BASE_DE_DATOS_MIGRADOR)
//
// Las claves de los roles salen de CAIGO_CLAVE_<ROL> (vacías en desarrollo).
package main

import (
	"context"
	"fmt"
	"net/url"
	"os"
	"strings"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/config"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "uso: caigoctl bootstrap|migrar")
		os.Exit(2)
	}
	if err := correr(os.Args[1]); err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
}

func correr(cmd string) error {
	cfg, err := config.Leer()
	if err != nil {
		return err
	}
	ctx := context.Background()
	switch cmd {
	case "bootstrap":
		if err := cfg.Exigir("BaseAdmin", "BaseMigrador"); err != nil {
			return err
		}
		u, err := url.Parse(cfg.BaseMigrador)
		if err != nil {
			return err
		}
		claves := db.Claves{}
		for _, r := range db.Roles {
			claves[r] = os.Getenv("CAIGO_CLAVE_" + strings.ToUpper(strings.TrimPrefix(r, "caigo_")))
		}
		if err := db.Bootstrap(ctx, cfg.BaseAdmin, strings.TrimPrefix(u.Path, "/"), claves); err != nil {
			return err
		}
		fmt.Println("roles y base listos")
	case "migrar":
		if err := cfg.Exigir("BaseMigrador"); err != nil {
			return err
		}
		if err := db.Migrar(ctx, cfg.BaseMigrador); err != nil {
			return err
		}
		fmt.Println("migraciones aplicadas")
	default:
		return fmt.Errorf("comando desconocido: %s", cmd)
	}
	return nil
}
