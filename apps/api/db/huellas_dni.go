package db

import (
	"context"
	"database/sql"
	"errors"

	"github.com/pressly/goose/v3"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/vetados"
)

// Go son las migraciones que no se pueden escribir en SQL porque necesitan algo del entorno.
// claveVerificacion es CAIGO_CLAVE_VERIFICACION (la de las fotos cifradas y las huellas).
func Go(claveVerificacion string) []*goose.Migration {
	return []*goose.Migration{
		goose.NewGoMigration(23,
			&goose.GoFunc{RunTx: func(ctx context.Context, tx *sql.Tx) error { return huellasDNI(ctx, tx, claveVerificacion) }},
			&goose.GoFunc{RunTx: sinHuellasDNI}),
	}
}

// ErrSinClaveHuellas: hay verificaciones de DNI con el número en claro y no está la clave para
// calcularles la huella. La migración no avanza (no se pierde el veto de nadie).
var ErrSinClaveHuellas = errors.New("migración 00023: hay verificaciones de DNI sin huella y falta CAIGO_CLAVE_VERIFICACION")

// huellasDNI (00023, AAIP 1.2): verificaciones_dni deja de guardar el número, el apellido, los
// nombres y el trámite del DNI. Antes de borrarlos, a cada fila se le calcula la huella del número
// con la misma función de la lista de vetados (vetados.Huella: HMAC-SHA256 con la clave derivada de
// CAIGO_CLAVE_VERIFICACION), así los vetos y el «DNI ya aprobado en otra cuenta» siguen andando.
// Queda el estado, la fecha de nacimiento (de ahí sale la edad que se aprueba) y la huella. Como el
// sistema: la tabla tiene RLS forzada.
func huellasDNI(ctx context.Context, tx *sql.Tx, claveVerificacion string) error {
	if _, err := tx.ExecContext(ctx, `SELECT set_config('app.sistema', 'si', true)`); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `ALTER TABLE verificaciones_dni ADD COLUMN huella bytea
		CHECK (huella IS NULL OR length(huella) = 32)`); err != nil {
		return err
	}
	rows, err := tx.QueryContext(ctx, `SELECT id::text, numero FROM verificaciones_dni`)
	if err != nil {
		return err
	}
	type fila struct{ id, numero string }
	var filas []fila
	for rows.Next() {
		var f fila
		if err := rows.Scan(&f.id, &f.numero); err != nil {
			rows.Close()
			return err
		}
		filas = append(filas, f)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return err
	}
	if len(filas) > 0 {
		clave, err := vetados.NuevaClave(claveVerificacion)
		if err != nil {
			return errors.Join(ErrSinClaveHuellas, err)
		}
		for _, f := range filas {
			h := clave.Huella(vetados.DNI, f.numero)
			if h == nil {
				return errors.New("migración 00023: una verificación de DNI sin número: " + f.id)
			}
			if _, err := tx.ExecContext(ctx, `UPDATE verificaciones_dni SET huella = $2 WHERE id::text = $1`, f.id, h); err != nil {
				return err
			}
		}
	}
	for _, q := range []string{
		`ALTER TABLE verificaciones_dni ALTER COLUMN huella SET NOT NULL`,
		// Un DNI aprobado no puede estar en dos cuentas: ahora por la huella.
		`DROP INDEX verificaciones_dni_numero`,
		`CREATE UNIQUE INDEX verificaciones_dni_huella ON verificaciones_dni (huella) WHERE estado = 'aprobada'`,
		`ALTER TABLE verificaciones_dni DROP COLUMN numero, DROP COLUMN apellido, DROP COLUMN nombres, DROP COLUMN tramite`,
		`SELECT set_config('app.sistema', '', true)`,
	} {
		if _, err := tx.ExecContext(ctx, q); err != nil {
			return err
		}
	}
	return nil
}

// sinHuellasDNI vuelve las columnas, vacías (lo borrado no se recupera), y saca la huella.
func sinHuellasDNI(ctx context.Context, tx *sql.Tx) error {
	for _, q := range []string{
		`ALTER TABLE verificaciones_dni ADD COLUMN numero text NOT NULL DEFAULT '',
			ADD COLUMN apellido text NOT NULL DEFAULT '', ADD COLUMN nombres text NOT NULL DEFAULT '',
			ADD COLUMN tramite text NOT NULL DEFAULT ''`,
		`DROP INDEX verificaciones_dni_huella`,
		`ALTER TABLE verificaciones_dni DROP COLUMN huella`,
	} {
		if _, err := tx.ExecContext(ctx, q); err != nil {
			return err
		}
	}
	return nil
}
