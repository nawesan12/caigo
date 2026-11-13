package archivos

import (
	"context"
	"encoding/json"
	"errors"
	"io/fs"
	"os"
	"regexp"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
)

// Las fotos públicas (perfil, planes y lugares) se guardan como archivos y se usan por su URL
// (…/v1/archivos/<id>). Cuando una se saca del perfil, de un plan o de un lugar, se borra de la base
// en la misma transacción (la URL responde 404 desde ese momento) y el worker la borra del disco
// (evento archivo.borrar). Si la sigue usando otro perfil, plan o lugar (un plan repetido copia la
// foto), no se toca. Las que quedaron sueltas sin pasar por acá (una subida que nunca se guardó, o la
// foto de un lugar que subió otra persona) las barre el worker con BarrerFotos.

// EventoBorrar es el tipo del evento del outbox que borra un archivo del disco.
const EventoBorrar = "archivo.borrar"

var reIDFoto = regexp.MustCompile(`/v1/archivos/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$`)

// sinUso: las fotos que no usa ningún perfil, plan ni lugar.
const sinUso = `a.tipo = 'foto' AND a.id::text NOT IN (
	SELECT x.id FROM (
		SELECT substring(f FROM '/v1/archivos/([0-9a-f-]{36})$') AS id FROM usuarios, unnest(fotos) f
		UNION SELECT substring(foto FROM '/v1/archivos/([0-9a-f-]{36})$') FROM planes WHERE foto <> ''
		UNION SELECT substring(foto FROM '/v1/archivos/([0-9a-f-]{36})$') FROM lugares WHERE foto <> ''
	) x WHERE x.id IS NOT NULL)`

// SoltarFotos recibe las URLs que se acaban de sacar (con el cambio ya hecho en tx) y borra las que
// ya no usa nadie. Las URLs que no son de /v1/archivos (la foto de Google de Clerk) se ignoran. Con
// RLS, en la transacción de una persona sólo ve y borra las suyas; el resto queda para BarrerFotos.
func SoltarFotos(ctx context.Context, tx pgx.Tx, urls ...string) error {
	var ids []string
	for _, u := range urls {
		if m := reIDFoto.FindStringSubmatch(u); m != nil {
			ids = append(ids, m[1])
		}
	}
	if len(ids) == 0 {
		return nil
	}
	rows, err := tx.Query(ctx, `DELETE FROM archivos a WHERE a.id::text = ANY ($1) AND `+sinUso+` RETURNING a.ruta`, ids)
	if err != nil {
		return err
	}
	rutas, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return err
	}
	return borrarDespues(ctx, tx, rutas)
}

// BarrerFotos borra las fotos sueltas con más de un día (el margen para que una subida llegue a
// guardarse en el perfil o el plan). tx como sistema. Devuelve cuántas.
func BarrerFotos(ctx context.Context, tx pgx.Tx) (int, error) {
	rows, err := tx.Query(ctx, `DELETE FROM archivos a WHERE a.creado_en < now() - interval '1 day' AND `+sinUso+` RETURNING a.ruta`)
	if err != nil {
		return 0, err
	}
	rutas, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return 0, err
	}
	return len(rutas), borrarDespues(ctx, tx, rutas)
}

func borrarDespues(ctx context.Context, tx pgx.Tx, rutas []string) error {
	for _, r := range rutas {
		if err := outbox.Publicar(ctx, tx, EventoBorrar, map[string]string{"ruta": r}); err != nil {
			return err
		}
	}
	return nil
}

// ManejadorBorrar borra del disco el archivo del evento (el worker). Si la carpeta no está montada
// falla, y el outbox lo reintenta: no se da por borrado algo que no se pudo ver.
func (d Disco) ManejadorBorrar(_ context.Context, datos []byte) error {
	var ev struct{ Ruta string }
	if err := json.Unmarshal(datos, &ev); err != nil || ev.Ruta == "" {
		return nil // un evento mal armado no se arregla reintentando
	}
	if st, err := os.Stat(d.Raiz); err != nil || !st.IsDir() {
		return errors.New("archivos: sin la carpeta de archivos")
	}
	if err := d.Borrar(ev.Ruta); err != nil && !errors.Is(err, fs.ErrNotExist) {
		return err
	}
	return nil
}
