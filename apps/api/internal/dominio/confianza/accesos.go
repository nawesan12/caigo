package confianza

import (
	"context"
	"errors"
	"io/fs"
	"net"
	"net/http"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// errSinClave: falta CAIGO_CLAVE_VERIFICACION en el servidor. No es culpa de quien pide, así que va
// como 503 (y el detalle queda en el log).
var errSinClave = &problema.Problema{Estado: http.StatusServiceUnavailable, Codigo: "sin_clave_verificacion",
	Titulo: "No disponible", Detalle: "Ahora no podemos guardar ni mostrar fotos de verificación. Probá de nuevo en un rato."}

// VerPrivado lee una selfie o una foto del DNI (descifrada en memoria) para quien ya se autorizó: la
// dueña o alguien del equipo. Si es alguien del equipo mirando lo de otra persona, el acceso queda en
// accesos_verificacion (quién, de quién, qué archivo, cuándo y desde qué IP) antes de devolverlo: si
// no se puede anotar, no se muestra. La dueña viendo lo suyo no se anota.
func (s *Servicio) VerPrivado(ctx context.Context, a ArchivoGuardado, quien sesion.Persona, ip string) ([]byte, error) {
	b, err := s.Disco.Leer(a.Ruta)
	switch {
	case errors.Is(err, archivos.ErrSinClave):
		s.log().Error("verificación: no se puede mostrar la foto", "archivo", a.ID, "error", err)
		return nil, errSinClave
	case errors.Is(err, fs.ErrNotExist):
		return nil, problema.NoEncontrado("Ese archivo no existe.")
	case err != nil:
		return nil, err
	}
	if quien.ID == a.Dueno {
		return b, nil
	}
	var dir any // NULL si no es una IP (no debería pasar)
	if p := net.ParseIP(ip); p != nil {
		dir = p.String()
	}
	err = s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `INSERT INTO accesos_verificacion (equipo_id, usuario_id, archivo_id, tipo, ip)
			VALUES ($1, $2, $3, $4, $5::inet)`, quien.ID, a.Dueno, a.ID, a.Tipo, dir)
		return err
	})
	if err != nil {
		return nil, err
	}
	return b, nil
}

// InformeCifrado es lo que hizo CifrarVerificaciones.
type InformeCifrado struct {
	// Revisados: selfies y fotos del DNI en la base.
	Revisados int
	// Cifrados ahora (con probar: los que se cifrarían).
	Cifrados int
	// YaCifrados de antes (no se tocan).
	YaCifrados int
	// Restos: originales sin cifrar que quedaron al lado de su .cif (un corte a mitad de camino) y se borraron.
	Restos int
	// Faltan: en la base pero no en el disco (se informan y se siguen).
	Faltan []string
}

// CifrarVerificaciones pasa al formato cifrado las selfies y fotos del DNI de antes (caigoctl
// cifrar-verificaciones). Por cada una: escribe el .cif, confirma que descifra igual, cambia la ruta
// en la base y recién ahí borra el original. Se puede correr las veces que haga falta: lo ya cifrado
// no se toca, y si un corte dejó el original al lado del .cif, lo borra después de comparar.
// probar: no escribe nada (ni en el disco ni en la base), sólo cuenta y prueba cifrar en memoria.
// Va con el dueño del esquema: la app no puede cambiar la ruta de un archivo.
func CifrarVerificaciones(ctx context.Context, pool *db.Pool, disco archivos.Disco, probar bool) (InformeCifrado, error) {
	var inf InformeCifrado
	if disco.Clave == nil {
		return inf, archivos.ErrSinClave
	}
	type fila struct{ ID, Ruta string }
	var filas []fila
	err := pool.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT id::text, ruta FROM archivos WHERE tipo IN ('selfie', 'dni') ORDER BY creado_en`)
		if err != nil {
			return err
		}
		filas, err = pgx.CollectRows(rows, pgx.RowToStructByPos[fila])
		return err
	})
	if err != nil {
		return inf, err
	}
	for _, f := range filas {
		inf.Revisados++
		if archivos.Cifrado(f.Ruta) {
			inf.YaCifrados++
			resto, err := restoSinCifrar(disco, f.Ruta, probar)
			if err != nil {
				return inf, err
			}
			if resto {
				inf.Restos++
			}
			continue
		}
		nueva, err := disco.Cifrar(f.Ruta, probar)
		if errors.Is(err, fs.ErrNotExist) {
			inf.Faltan = append(inf.Faltan, f.ID)
			continue
		}
		if err != nil {
			return inf, err
		}
		inf.Cifrados++
		if probar {
			continue
		}
		var cambio int64
		err = pool.EnSistema(ctx, func(tx pgx.Tx) error {
			tag, err := tx.Exec(ctx, `UPDATE archivos SET ruta = $2 WHERE id::text = $1 AND ruta = $3`, f.ID, nueva, f.Ruta)
			cambio = tag.RowsAffected()
			return err
		})
		if err != nil {
			// El .cif queda suelto y el original sigue sirviendo: la próxima corrida lo vuelve a hacer.
			return inf, err
		}
		if cambio == 0 {
			// Lo borró la purga (o lo tocó otro) mientras tanto: el original no se toca, el .cif sobra.
			inf.Cifrados--
			_ = disco.BorrarCifrado(nueva)
			continue
		}
		if err := disco.BorrarOriginal(nueva); err != nil {
			return inf, err
		}
	}
	return inf, nil
}

// restoSinCifrar mira si al lado de un .cif quedó el original; si descifra igual, lo borra.
func restoSinCifrar(disco archivos.Disco, ruta string, probar bool) (bool, error) {
	ok, err := disco.MismoOriginal(ruta)
	if err != nil || !ok {
		return false, err
	}
	if probar {
		return true, nil
	}
	return true, disco.BorrarOriginal(ruta)
}
