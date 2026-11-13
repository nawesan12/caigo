// Comando caigoctl: tareas de operación de la base.
//
//	caigoctl bootstrap   crea roles y base (superusuario: CAIGO_BASE_DE_DATOS_ADMIN)
//	caigoctl migrar      aplica las migraciones (CAIGO_BASE_DE_DATOS_MIGRADOR)
//	caigoctl equipo <user_…> [no]   suma (o saca) a una persona del equipo de Caigo
//	caigoctl recuperar-cuenta <mail|user_…>  reactiva una cuenta borrada hace menos de 30 días
//	caigoctl semilla <planes.json>  publica planes semilla del equipo (ver docs/semilla.md)
//	caigoctl cifrar-verificaciones [--probar]  cifra las selfies y DNI viejos (CAIGO_CLAVE_VERIFICACION)
//
// Las claves de los roles salen de CAIGO_CLAVE_<ROL> (vacías en desarrollo).
package main

import (
	"context"
	"fmt"
	"net/url"
	"os"
	"strings"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/confianza"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/config"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "uso: caigoctl bootstrap|migrar|equipo <user_…> [no]|recuperar-cuenta <mail|user_…>|semilla <planes.json>|cifrar-verificaciones [--probar]")
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
	case "equipo":
		// La app no puede tocar la columna equipo: va con el dueño del esquema.
		if err := cfg.Exigir("BaseMigrador"); err != nil {
			return err
		}
		if len(os.Args) < 3 {
			return fmt.Errorf("falta el id de Clerk (user_…): la persona tiene que haber entrado una vez a Caigo")
		}
		conn, err := db.Abrir(ctx, cfg.BaseMigrador)
		if err != nil {
			return err
		}
		defer conn.Close()
		si := len(os.Args) < 4 || os.Args[3] != "no"
		tag, err := conn.Exec(ctx, `UPDATE usuarios SET equipo = $2 WHERE clerk_id = $1`, os.Args[2], si)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return fmt.Errorf("no hay nadie con ese id: que entre una vez a Caigo y probá de nuevo")
		}
		fmt.Println("listo")
	case "recuperar-cuenta":
		// Alguien borró su cuenta y escribió a hola@ dentro de los 30 días: se reactiva (sus planes
		// cancelados siguen cancelados). Por mail o por id de Clerk.
		if err := cfg.Exigir("BaseMigrador"); err != nil {
			return err
		}
		if len(os.Args) < 3 {
			return fmt.Errorf("falta el mail o el id de Clerk (user_…) de la cuenta")
		}
		conn, err := db.Abrir(ctx, cfg.BaseMigrador)
		if err != nil {
			return err
		}
		defer conn.Close()
		id, err := confianza.Recuperar(ctx, conn, os.Args[2])
		if err != nil {
			return err
		}
		if id == "" {
			return fmt.Errorf("no hay una cuenta borrada sin purgar con ese mail o id")
		}
		fmt.Println("recuperada:", id)
	case "semilla":
		if err := cfg.Exigir("BaseApp"); err != nil {
			return err
		}
		if len(os.Args) < 3 {
			return fmt.Errorf("falta el archivo de planes")
		}
		return semilla(ctx, cfg.BaseApp, os.Args[2])
	case "cifrar-verificaciones":
		// Pasa las selfies y fotos del DNI guardadas sin cifrar al formato .cif. Se puede repetir.
		// --probar: no escribe nada, sólo cuenta y prueba la clave.
		if err := cfg.Exigir("BaseMigrador"); err != nil {
			return err
		}
		probar := len(os.Args) > 2 && os.Args[2] == "--probar"
		if len(os.Args) > 2 && !probar {
			return fmt.Errorf("opción desconocida: %s (la única es --probar)", os.Args[2])
		}
		if cfg.ClaveVerificacion == "" {
			return fmt.Errorf("falta CAIGO_CLAVE_VERIFICACION (generala con: openssl rand -base64 32)")
		}
		clave, err := archivos.NuevaClave(cfg.ClaveVerificacion)
		if err != nil {
			return err
		}
		if st, err := os.Stat(cfg.Archivos); err != nil || !st.IsDir() {
			return fmt.Errorf("no está la carpeta de archivos %s (CAIGO_ARCHIVOS)", cfg.Archivos)
		}
		conn, err := db.Abrir(ctx, cfg.BaseMigrador)
		if err != nil {
			return err
		}
		defer conn.Close()
		inf, err := confianza.CifrarVerificaciones(ctx, conn, archivos.Disco{Raiz: cfg.Archivos, Clave: clave}, probar)
		verbo := "cifrados"
		if probar {
			verbo = "se cifrarían (--probar: no se escribió nada)"
		}
		fmt.Printf("revisados: %d, %s: %d, ya cifrados: %d, originales sueltos al lado de su .cif: %d, faltan en el disco: %d\n",
			inf.Revisados, verbo, inf.Cifrados, inf.YaCifrados, inf.Restos, len(inf.Faltan))
		for _, id := range inf.Faltan {
			fmt.Println("  falta en el disco:", id)
		}
		return err
	default:
		return fmt.Errorf("comando desconocido: %s", cmd)
	}
	return nil
}
