package confianza

import (
	"context"
	"errors"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
)

// Las fotos de la verificación del DNI (frente, dorso y la selfie que se sacó para el DNI) sólo
// sirven mientras el equipo la revisa. Apenas queda resuelta (aprobada o rechazada) se borran: de la
// base en la misma transacción que la resuelve y del disco con el evento archivo.borrar del worker.
// Queda sólo el resultado, la fecha de nacimiento (ya en el perfil) y la huella del número para los
// vetados. Lo dice la política de privacidad.

// soltarFotosDNI suelta las fotos de las verificaciones resueltas: saca las referencias, borra las
// filas de archivos (las que no use una verificación pendiente) y encola su borrado del disco. Con id,
// sólo esa verificación; vacío, todas (el worker). tx como sistema. Idempotente.
func soltarFotosDNI(ctx context.Context, tx pgx.Tx, id string) (int, error) {
	rows, err := tx.Query(ctx, `SELECT frente_id, dorso_id, selfie_id FROM verificaciones_dni
		WHERE estado <> 'pendiente' AND ($1 = '' OR id::text = $1)
			AND (frente_id IS NOT NULL OR dorso_id IS NOT NULL OR selfie_id IS NOT NULL)
		FOR UPDATE`, id)
	if err != nil {
		return 0, err
	}
	var ids []string
	for rows.Next() {
		var f, d, s *string
		if err := rows.Scan(&f, &d, &s); err != nil {
			rows.Close()
			return 0, err
		}
		for _, x := range []*string{f, d, s} {
			if x != nil {
				ids = append(ids, *x)
			}
		}
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return 0, err
	}
	if len(ids) == 0 {
		return 0, nil
	}
	if _, err := tx.Exec(ctx, `UPDATE verificaciones_dni SET frente_id = NULL, dorso_id = NULL, selfie_id = NULL
		WHERE estado <> 'pendiente' AND ($1 = '' OR id::text = $1)
			AND (frente_id IS NOT NULL OR dorso_id IS NOT NULL OR selfie_id IS NOT NULL)`, id); err != nil {
		return 0, err
	}
	return borrarArchivos(ctx, tx, `DELETE FROM archivos a WHERE a.id::text = ANY ($1) AND NOT EXISTS (
		SELECT 1 FROM verificaciones_dni v WHERE a.id IN (v.frente_id, v.dorso_id, v.selfie_id)) RETURNING a.ruta`, ids)
}

// borrarArchivos corre un DELETE ... RETURNING ruta y encola el borrado de cada una del disco.
func borrarArchivos(ctx context.Context, tx pgx.Tx, q string, args ...any) (int, error) {
	rows, err := tx.Query(ctx, q, args...)
	if err != nil {
		return 0, err
	}
	rutas, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return 0, err
	}
	for _, r := range rutas {
		if err := outbox.Publicar(ctx, tx, archivos.EventoBorrar, map[string]string{"ruta": r}); err != nil {
			return 0, err
		}
	}
	return len(rutas), nil
}

// margenSubida: lo que puede tardar una foto del DNI entre que se sube y se pide la verificación.
const margenSubida = 24 * time.Hour

// carpetasVerificacion son las carpetas del disco donde quedan la selfie y las fotos del DNI.
var carpetasVerificacion = []string{"dni", "selfie"}

// InformeFotosDNI es lo que hizo PurgarFotosDNI.
type InformeFotosDNI struct {
	// Resueltas: fotos de verificaciones ya resueltas que todavía estaban (borradas de la base).
	Resueltas int
	// Sueltas: fotos del DNI subidas hace más de un día que no son de ninguna verificación pendiente.
	Sueltas int
	// Disco: archivos de dni/ y selfie/ que ya no tienen fila en la base (borrados del disco).
	Disco int
}

// PurgarFotosDNI (el worker, cada hora) borra lo que quedó de las verificaciones resueltas: las fotos
// todavía referenciadas (las de antes de este cambio o un corte a mitad de camino), las fotos del
// DNI sueltas (una subida que nunca llegó a verificación) y los archivos de dni/ y selfie/ que no
// tienen fila en la base. Idempotente: otra vuelta no encuentra nada. Sin la carpeta montada no
// toca el disco (lo de la base sí: el evento espera a que el worker la vea).
func PurgarFotosDNI(ctx context.Context, app *db.Pool, disco archivos.Disco) (InformeFotosDNI, error) {
	var inf InformeFotosDNI
	err := app.EnSistema(ctx, func(tx pgx.Tx) error {
		var err error
		if inf.Resueltas, err = soltarFotosDNI(ctx, tx, ""); err != nil {
			return err
		}
		inf.Sueltas, err = borrarArchivos(ctx, tx, `DELETE FROM archivos a WHERE a.tipo = 'dni' AND a.creado_en < $1
			AND NOT EXISTS (SELECT 1 FROM verificaciones_dni v WHERE a.id IN (v.frente_id, v.dorso_id, v.selfie_id))
			RETURNING a.ruta`, time.Now().Add(-margenSubida))
		return err
	})
	if err != nil {
		return inf, err
	}
	if st, err := os.Stat(disco.Raiz); err != nil || !st.IsDir() {
		return inf, nil
	}
	inf.Disco, err = purgarDisco(ctx, app, disco)
	return inf, err
}

// purgarDisco borra de dni/ y selfie/ los archivos (con más de un día, para no pisar una subida en
// curso) que no tienen fila en archivos: ya nadie los puede ver ni borrar por otro camino.
func purgarDisco(ctx context.Context, app *db.Pool, disco archivos.Disco) (int, error) {
	conocidas := map[string]bool{}
	err := app.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT ruta FROM archivos WHERE tipo IN ('dni', 'selfie')`)
		if err != nil {
			return err
		}
		rutas, err := pgx.CollectRows(rows, pgx.RowTo[string])
		for _, r := range rutas {
			conocidas[r] = true
			// Borrar también saca la otra versión (.cif u original): las dos son de la misma fila.
			if archivos.Cifrado(r) {
				conocidas[strings.TrimSuffix(r, archivos.ExtCifrado)] = true
			} else {
				conocidas[r+archivos.ExtCifrado] = true
			}
		}
		return err
	})
	if err != nil {
		return 0, err
	}
	limite := time.Now().Add(-margenSubida)
	n := 0
	for _, carpeta := range carpetasVerificacion {
		raiz := filepath.Join(disco.Raiz, carpeta)
		err := filepath.WalkDir(raiz, func(abs string, d fs.DirEntry, err error) error {
			if err != nil {
				if errors.Is(err, fs.ErrNotExist) {
					return nil
				}
				return err
			}
			if ctx.Err() != nil {
				return ctx.Err()
			}
			if d.IsDir() {
				return nil
			}
			rel, err := filepath.Rel(disco.Raiz, abs)
			if err != nil || conocidas[filepath.ToSlash(rel)] {
				return err
			}
			info, err := d.Info()
			if err != nil || info.ModTime().After(limite) {
				return nil
			}
			if err := os.Remove(abs); err != nil && !errors.Is(err, fs.ErrNotExist) {
				return err
			}
			n++
			return nil
		})
		if err != nil {
			return n, err
		}
	}
	return n, nil
}
