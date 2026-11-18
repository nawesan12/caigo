package planes

import (
	"context"
	"errors"
	"io"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/firma"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Fotos del plan: las suben y las ven sólo quienes fueron. «Fueron» es estar aceptado y haber hecho
// check-in; si en el plan nadie usó el check-in, alcanza con estar aceptado (como la asistencia, 00027).
// Con un bloqueo de por medio, no se ven las del otro. Los links a las fotos van firmados y vencen a la
// hora: no son públicos como las fotos del perfil.

// fue (con s = la solicitud de quien mira en el plan p): fue al plan.
const fue = `(s.estado = 'aceptada' AND (s.checkin_en IS NOT NULL OR s.usuario_id = p.organizador_id
	OR NOT EXISTS (SELECT 1 FROM solicitudes c WHERE c.plan_id = p.id AND c.checkin_en IS NOT NULL)))`

// fotoVisible (con f = fotos_plan, $2 = quien mira): la foto se ve si no está oculta (salvo para quien la
// subió) y no hay un bloqueo entre quien mira y quien la subió.
const fotoVisible = `(f.moderacion = 'ok' OR f.usuario_id = $2) AND NOT EXISTS (SELECT 1 FROM bloqueos b
	WHERE (b.usuario_id = $2 AND b.bloqueado_id = f.usuario_id) OR (b.usuario_id = f.usuario_id AND b.bloqueado_id = $2))`

// Topes de fotos y plazos.
const (
	fotosPorPersona = 5
	fotosPorPlan    = 40
	// Se pueden subir desde que empieza hasta 7 días después del final.
	fotosHasta = 7 * 24 * time.Hour
	// El link firmado de una foto vale una hora.
	fotoLink = time.Hour
	// PropositoFoto firma los links de las fotos del plan.
	PropositoFoto = "foto_plan"
)

// FotoPlan es una foto del plan como la ve alguien que fue.
type FotoPlan struct {
	ID       string    `json:"id"`
	URL      string    `json:"url"`
	Autor    Persona   `json:"autor"`
	Mia      bool      `json:"mia"`
	CreadaEn time.Time `json:"creadaEn"`
}

// urlFoto: el link firmado de una foto para quien la mira (archivo~persona, vence a la hora).
func (s *Servicio) urlFoto(archivoID, usuarioID string) string {
	tok := s.Firma.Firmar(PropositoFoto, archivoID+"~"+usuarioID, s.ahora().Add(fotoLink))
	return strings.TrimRight(s.APIPublica, "/") + "/v1/archivos/" + archivoID + "?t=" + tok
}

// FotoFirmada devuelve para quién se firmó el link de una foto del plan ("" si no vale para ese archivo).
func FotoFirmada(f firma.Firmador, archivoID, token string, ahora time.Time) string {
	dato, err := f.Verificar(PropositoFoto, token, ahora)
	id, quien, ok := strings.Cut(dato, "~")
	if err != nil || !ok || id != archivoID {
		return ""
	}
	return quien
}

// exigirQueFue: quien pide fue al plan (y el plan no se canceló). Devuelve inicio y fin.
func exigirQueFue(ctx context.Context, tx pgx.Tx, usuarioID, planID string) (time.Time, time.Time, error) {
	var empieza time.Time
	var dur int16
	var ok bool
	err := tx.QueryRow(ctx, `SELECT p.empieza_en, p.duracion_min, `+fue+` FROM planes p
		JOIN solicitudes s ON s.plan_id = p.id AND s.usuario_id = $2 WHERE p.id = $1 AND p.cancelado_en IS NULL`,
		planID, usuarioID).Scan(&empieza, &dur, &ok)
	if errors.Is(err, pgx.ErrNoRows) || (err == nil && !ok) {
		return empieza, empieza, problema.NoEncontrado("Las fotos las ven sólo quienes fueron.")
	}
	return empieza, empieza.Add(time.Duration(dur) * time.Minute), err
}

// Fotos lista las fotos del plan para alguien que fue.
func (s *Servicio) Fotos(ctx context.Context, usuarioID, planID string) ([]FotoPlan, error) {
	if !esUUID(planID) {
		return nil, problema.NoEncontrado("Ese plan no existe.")
	}
	out := []FotoPlan{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if _, _, err := exigirQueFue(ctx, tx, usuarioID, planID); err != nil {
			return err
		}
		rows, err := tx.Query(ctx, `SELECT f.id, f.archivo_id, f.creada_en, u.id, u.nombre, coalesce(u.fotos[1], '')
			FROM fotos_plan f JOIN usuarios u ON u.id = f.usuario_id
			WHERE f.plan_id = $1 AND `+fotoVisible+` ORDER BY f.creada_en`, planID, usuarioID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var f FotoPlan
			var archivo string
			if err := rows.Scan(&f.ID, &archivo, &f.CreadaEn, &f.Autor.ID, &f.Autor.Nombre, &f.Autor.Foto); err != nil {
				return err
			}
			f.URL, f.Mia = s.urlFoto(archivo, usuarioID), f.Autor.ID == usuarioID
			out = append(out, f)
		}
		return rows.Err()
	})
	return out, err
}

// SubirFoto guarda una foto del plan (cuerpo crudo, JPEG/PNG/WebP hasta 8 MB).
func (s *Servicio) SubirFoto(ctx context.Context, usuarioID, planID string, r io.Reader) (FotoPlan, error) {
	if !esUUID(planID) {
		return FotoPlan{}, problema.NoEncontrado("Ese plan no existe.")
	}
	// Antes de tocar el disco: que haya ido y esté a tiempo.
	ahora := s.ahora()
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		empieza, fin, err := exigirQueFue(ctx, tx, usuarioID, planID)
		if err != nil {
			return err
		}
		if ahora.Before(empieza) {
			return problema.Conflicto("temprano", "Las fotos se suben cuando arranca el plan.")
		}
		if ahora.After(fin.Add(fotosHasta)) {
			return problema.Conflicto("tarde", "Ya pasó una semana del plan: no se suben más fotos.")
		}
		var mias, todas int
		if err := tx.QueryRow(ctx, `SELECT count(*) FILTER (WHERE usuario_id = $2), count(*) FROM fotos_plan WHERE plan_id = $1`,
			planID, usuarioID).Scan(&mias, &todas); err != nil {
			return err
		}
		if mias >= fotosPorPersona || todas >= fotosPorPlan {
			return problema.Conflicto("tope_fotos", "Ya hay muchas fotos de este plan.")
		}
		return nil
	})
	if err != nil {
		return FotoPlan{}, err
	}
	ruta, contenido, n, err := s.Disco.Guardar("plan", r)
	if errors.Is(err, archivos.ErrTipo) || errors.Is(err, archivos.ErrGrande) {
		return FotoPlan{}, problema.Invalido(strings.TrimPrefix(err.Error(), "archivos: "))
	}
	if err != nil {
		return FotoPlan{}, err
	}
	var f FotoPlan
	var archivo string
	err = s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := tx.QueryRow(ctx, `INSERT INTO archivos (usuario_id, tipo, ruta, contenido, bytes) VALUES ($1, 'plan', $2, $3, $4)
			RETURNING id`, usuarioID, ruta, contenido, n).Scan(&archivo); err != nil {
			return err
		}
		return tx.QueryRow(ctx, `INSERT INTO fotos_plan (plan_id, usuario_id, archivo_id) VALUES ($1, $2, $3) RETURNING id, creada_en`,
			planID, usuarioID, archivo).Scan(&f.ID, &f.CreadaEn)
	})
	if err != nil {
		_ = s.Disco.Borrar(ruta)
		return FotoPlan{}, err
	}
	f.URL, f.Mia, f.Autor.ID = s.urlFoto(archivo, usuarioID), true, usuarioID
	return f, nil
}

// BorrarFoto: quien la subió la saca (se borra el archivo).
func (s *Servicio) BorrarFoto(ctx context.Context, usuarioID, planID, fotoID string) error {
	if !esUUID(planID) || !esUUID(fotoID) {
		return problema.NoEncontrado("Esa foto no existe.")
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var ruta string
		err := tx.QueryRow(ctx, `WITH f AS (DELETE FROM fotos_plan WHERE id = $1 AND plan_id = $2 AND usuario_id = $3 RETURNING archivo_id)
			DELETE FROM archivos a USING f WHERE a.id = f.archivo_id RETURNING a.ruta`, fotoID, planID, usuarioID).Scan(&ruta)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Esa foto no existe.")
		}
		if err != nil {
			return err
		}
		return outbox.Publicar(ctx, tx, archivos.EventoBorrar, map[string]string{"ruta": ruta})
	})
}

// ReportarFoto: alguien que fue la reporta. Deja de verse enseguida (para todos menos quien la subió)
// y va a la cola de reportes del equipo.
func (s *Servicio) ReportarFoto(ctx context.Context, usuarioID, planID, fotoID string) error {
	if !esUUID(planID) || !esUUID(fotoID) {
		return problema.NoEncontrado("Esa foto no existe.")
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if _, _, err := exigirQueFue(ctx, tx, usuarioID, planID); err != nil {
			return err
		}
		var autor string
		err := tx.QueryRow(ctx, `UPDATE fotos_plan SET moderacion = 'oculto' WHERE id = $1 AND plan_id = $2 AND usuario_id <> $3
			RETURNING usuario_id`, fotoID, planID, usuarioID).Scan(&autor)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Esa foto no existe.")
		}
		if err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `INSERT INTO reportes (usuario_id, sobre_usuario_id, sobre_plan_id, motivo, detalle)
			VALUES ($1, $2, $3, 'inapropiado', $4)`, usuarioID, autor, planID, "Foto del plan "+fotoID)
		return err
	})
}

// PuedeVerFoto (como sistema): quien mira puede ver ese archivo de una foto del plan.
func PuedeVerFoto(ctx context.Context, tx pgx.Tx, usuarioID, archivoID string) (bool, error) {
	var ok bool
	err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM fotos_plan f JOIN planes p ON p.id = f.plan_id
		JOIN solicitudes s ON s.plan_id = p.id AND s.usuario_id = $2
		WHERE f.archivo_id::text = $1 AND `+fue+` AND `+fotoVisible+`)`, archivoID, usuarioID).Scan(&ok)
	return ok, err
}

// PurgarFotos (como sistema, el worker): las fotos de planes que terminaron hace más de 6 meses, y
// los archivos de fotos del plan que quedaron sin foto (una subida cortada). Devuelve cuántas.
func PurgarFotos(ctx context.Context, tx pgx.Tx) (int, error) {
	rows, err := tx.Query(ctx, `DELETE FROM archivos a WHERE a.tipo = 'plan' AND (
		EXISTS (SELECT 1 FROM fotos_plan f JOIN planes p ON p.id = f.plan_id WHERE f.archivo_id = a.id
			AND p.empieza_en + make_interval(mins => p.duracion_min) < now() - interval '6 months')
		OR (a.creado_en < now() - interval '1 day' AND NOT EXISTS (SELECT 1 FROM fotos_plan f WHERE f.archivo_id = a.id)))
		RETURNING a.ruta`)
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
