package confianza

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/equipo"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Las tareas del equipo de Caigo corren como sistema: ven todo. Quién es del equipo lo decide la
// sesión (sesion.ExigirEquipo); acá queda registrado quién revisó. El DNI se aprueba solo al subirlo
// (PedirDNI): el equipo lo revisa después y, si no cierra, suspende la cuenta.

// DNIPendiente es un DNI aprobado (al subirlo) que el equipo todavía no revisó.
type DNIPendiente struct {
	ID           string    `json:"id"`
	UsuarioID    string    `json:"usuarioId"`
	NombrePerfil string    `json:"nombrePerfil"`
	EdadPerfil   *int16    `json:"edadPerfil,omitempty"`
	Nacimiento   time.Time `json:"nacimiento"`
	// Las fotos (vacías si pasaron los 30 días sin revisión y se purgaron).
	FrenteID string `json:"frenteId,omitempty"`
	DorsoID  string `json:"dorsoId,omitempty"`
	SelfieID string `json:"selfieId,omitempty"`
	// Selfie de la prueba de vida al entrar (para comparar con la del DNI).
	SelfieIngresoID string `json:"selfieIngresoId,omitempty"`
	// El número también está aprobado en otra cuenta (no debería: se frena al subirlo).
	Repetido bool      `json:"repetido"`
	CreadaEn time.Time `json:"creadaEn"`
}

// DNIPendientes lista los DNI aprobados sin revisar, el más viejo primero.
func (s *Servicio) DNIPendientes(ctx context.Context) ([]DNIPendiente, error) {
	out := []DNIPendiente{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `
			SELECT v.id, v.usuario_id, u.nombre, u.edad, v.nacimiento,
				coalesce(v.frente_id::text, ''), coalesce(v.dorso_id::text, ''), coalesce(v.selfie_id::text, ''),
				coalesce((SELECT a.id::text FROM archivos a WHERE a.usuario_id = u.id AND a.tipo = 'selfie'
					AND a.id IS DISTINCT FROM v.selfie_id ORDER BY a.creado_en LIMIT 1), ''),
				EXISTS (SELECT 1 FROM verificaciones_dni o WHERE o.huella = v.huella AND o.estado = 'aprobada' AND o.id <> v.id),
				v.creada_en
			FROM verificaciones_dni v JOIN usuarios u ON u.id = v.usuario_id
			WHERE v.estado = 'aprobada' AND v.revisada_en IS NULL ORDER BY v.creada_en LIMIT 100`)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var d DNIPendiente
			if err := rows.Scan(&d.ID, &d.UsuarioID, &d.NombrePerfil, &d.EdadPerfil,
				&d.Nacimiento, &d.FrenteID, &d.DorsoID, &d.SelfieID, &d.SelfieIngresoID,
				&d.Repetido, &d.CreadaEn); err != nil {
				return err
			}
			out = append(out, d)
		}
		return rows.Err()
	})
	return out, err
}

// Lo que puede hacer el equipo al revisar un DNI ya aprobado.
const (
	// RevisionOK: está bien, queda revisada.
	RevisionOK = "ok"
	// RevisionSuspender: no cierra (no es la persona, la foto no es un DNI…): la verificación pasa a
	// rechazada con el motivo y la cuenta se suspende como desde el backoffice.
	RevisionSuspender = "suspender"
)

// RevisarDNI cierra la revisión de un DNI aprobado sin revisar. En los dos casos queda revisada y las
// fotos de la verificación (frente, dorso y la selfie del DNI) se borran. suspender: además la
// verificación pasa a rechazada (el motivo lo ve la persona), el perfil deja de estar Verificado y
// la cuenta se suspende (equipo.SuspenderTx: el mail y el DNI quedan vetados).
func (s *Servicio) RevisarDNI(ctx context.Context, equipoID, id, accion, motivo string) error {
	motivo = strings.TrimSpace(motivo)
	switch {
	case accion != RevisionOK && accion != RevisionSuspender:
		return problema.Invalido("La acción es ok o suspender.")
	case accion == RevisionSuspender && motivo == "":
		return problema.Invalido("Decí por qué se suspende (lo ve la persona).")
	case len([]rune(motivo)) > 300:
		return problema.Invalido("El motivo puede tener hasta 300 letras.")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var usuario string
		err := tx.QueryRow(ctx, `SELECT usuario_id FROM verificaciones_dni WHERE id::text = $1 AND estado = 'aprobada'
			AND revisada_en IS NULL FOR UPDATE`, id).Scan(&usuario)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Esa verificación ya se revisó.")
		}
		if err != nil {
			return err
		}
		if accion == RevisionSuspender {
			// Primero la suspensión: con el DNI todavía aprobado, su huella entra en los vetados.
			if err := equipo.SuspenderTx(ctx, tx, s.Vetados, usuario, motivo); err != nil {
				return err
			}
			if _, err := tx.Exec(ctx, `UPDATE verificaciones_dni SET estado = 'rechazada', motivo = $2 WHERE id::text = $1`,
				id, motivo); err != nil {
				return err
			}
			if _, err := tx.Exec(ctx, `UPDATE usuarios SET dni_en = NULL WHERE id = $1`, usuario); err != nil {
				return err
			}
		}
		if _, err := tx.Exec(ctx, `UPDATE verificaciones_dni SET revisada_por = $2, revisada_en = now() WHERE id::text = $1`,
			id, equipoID); err != nil {
			return err
		}
		// Revisada: las fotos (frente, dorso y selfie) se borran ya (ver fotos_dni.go).
		_, err = soltarFotosDNI(ctx, tx, id)
		return err
	})
}

// ReporteAbierto para moderar.
type ReporteAbierto struct {
	ID          string  `json:"id"`
	De          string  `json:"de"`
	DeNombre    string  `json:"deNombre"`
	SobreID     *string `json:"sobreId,omitempty"`
	SobreNombre string  `json:"sobreNombre,omitempty"`
	// Reportes que ya tiene esa persona (abiertos o resueltos).
	SobreReportes int       `json:"sobreReportes"`
	PlanID        *string   `json:"planId,omitempty"`
	PlanTitulo    string    `json:"planTitulo,omitempty"`
	Motivo        string    `json:"motivo"`
	Detalle       string    `json:"detalle"`
	CreadoEn      time.Time `json:"creadoEn"`
}

// Reportes abiertos, el más viejo primero.
func (s *Servicio) Reportes(ctx context.Context) ([]ReporteAbierto, error) {
	out := []ReporteAbierto{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `
			SELECT r.id, r.usuario_id, d.nombre, r.sobre_usuario_id, coalesce(o.nombre, ''),
				(SELECT count(*) FROM reportes x WHERE x.sobre_usuario_id = r.sobre_usuario_id),
				r.sobre_plan_id, coalesce(p.titulo, ''), r.motivo, r.detalle, r.creado_en
			FROM reportes r JOIN usuarios d ON d.id = r.usuario_id
			LEFT JOIN usuarios o ON o.id = r.sobre_usuario_id LEFT JOIN planes p ON p.id = r.sobre_plan_id
			WHERE r.estado = 'abierto' ORDER BY r.creado_en LIMIT 200`)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var r ReporteAbierto
			if err := rows.Scan(&r.ID, &r.De, &r.DeNombre, &r.SobreID, &r.SobreNombre, &r.SobreReportes,
				&r.PlanID, &r.PlanTitulo, &r.Motivo, &r.Detalle, &r.CreadoEn); err != nil {
				return err
			}
			out = append(out, r)
		}
		return rows.Err()
	})
	return out, err
}

// Resolver cierra un reporte. suspender: además suspende a la persona reportada (y la veta).
func (s *Servicio) Resolver(ctx context.Context, equipoID, id string, descartar, suspender bool, resolucion string) error {
	estado := "resuelto"
	if descartar {
		estado = "descartado"
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var sobre *string
		err := tx.QueryRow(ctx, `UPDATE reportes SET estado = $2, resolucion = $3, resuelto_por = $4, resuelto_en = now()
			WHERE id::text = $1 AND estado = 'abierto' RETURNING sobre_usuario_id`, id, estado, strings.TrimSpace(resolucion), equipoID).Scan(&sobre)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Ese reporte no está abierto.")
		}
		if err != nil || !suspender || sobre == nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE usuarios SET suspendido_en = now(), suspension_motivo = $2 WHERE id = $1`, *sobre, strings.TrimSpace(resolucion)); err != nil {
			return err
		}
		// Y su mail y su DNI quedan vetados (sólo las huellas): no vuelve con otra cuenta.
		_, err = s.Vetados.Anotar(ctx, tx, *sobre)
		return err
	})
}
