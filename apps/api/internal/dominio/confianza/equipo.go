package confianza

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Las tareas del equipo de Caigo corren como sistema: ven todo. Quién es del equipo lo decide la
// sesión (sesion.ExigirEquipo); acá queda registrado quién revisó.

// DNIPendiente para revisar.
type DNIPendiente struct {
	ID           string    `json:"id"`
	UsuarioID    string    `json:"usuarioId"`
	NombrePerfil string    `json:"nombrePerfil"`
	EdadPerfil   *int16    `json:"edadPerfil,omitempty"`
	Numero       string    `json:"numero"`
	Apellido     string    `json:"apellido"`
	Nombres      string    `json:"nombres"`
	Nacimiento   time.Time `json:"nacimiento"`
	FrenteID     string    `json:"frenteId"`
	DorsoID      string    `json:"dorsoId"`
	SelfieID     string    `json:"selfieId"`
	// Selfie de la prueba de vida al entrar (para comparar con la del DNI).
	SelfieIngresoID string `json:"selfieIngresoId,omitempty"`
	// El número ya está aprobado en otra cuenta.
	Repetido bool      `json:"repetido"`
	CreadaEn time.Time `json:"creadaEn"`
}

// DNIPendientes lista las verificaciones por revisar, la más vieja primero.
func (s *Servicio) DNIPendientes(ctx context.Context) ([]DNIPendiente, error) {
	out := []DNIPendiente{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `
			SELECT v.id, v.usuario_id, u.nombre, u.edad, v.numero, v.apellido, v.nombres, v.nacimiento,
				v.frente_id, v.dorso_id, v.selfie_id,
				coalesce((SELECT a.id::text FROM archivos a WHERE a.usuario_id = u.id AND a.tipo = 'selfie'
					AND a.id <> v.selfie_id ORDER BY a.creado_en LIMIT 1), ''),
				EXISTS (SELECT 1 FROM verificaciones_dni o WHERE o.numero = v.numero AND o.estado = 'aprobada'),
				v.creada_en
			FROM verificaciones_dni v JOIN usuarios u ON u.id = v.usuario_id
			WHERE v.estado = 'pendiente' ORDER BY v.creada_en LIMIT 100`)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var d DNIPendiente
			if err := rows.Scan(&d.ID, &d.UsuarioID, &d.NombrePerfil, &d.EdadPerfil, &d.Numero, &d.Apellido,
				&d.Nombres, &d.Nacimiento, &d.FrenteID, &d.DorsoID, &d.SelfieID, &d.SelfieIngresoID,
				&d.Repetido, &d.CreadaEn); err != nil {
				return err
			}
			out = append(out, d)
		}
		return rows.Err()
	})
	return out, err
}

// RevisarDNI aprueba o rechaza. Al aprobar, el perfil queda Verificado y toma la edad del DNI. En
// los dos casos, las fotos de la verificación se borran.
func (s *Servicio) RevisarDNI(ctx context.Context, equipoID, id string, aprobar bool, motivo string) error {
	motivo = strings.TrimSpace(motivo)
	if !aprobar && motivo == "" {
		return problema.Invalido("Decí por qué se rechaza (lo ve la persona).")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var usuario string
		var nac time.Time
		err := tx.QueryRow(ctx, `SELECT usuario_id, nacimiento FROM verificaciones_dni WHERE id::text = $1 AND estado = 'pendiente' FOR UPDATE`, id).Scan(&usuario, &nac)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Esa verificación no está pendiente.")
		}
		if err != nil {
			return err
		}
		estado := "rechazada"
		if aprobar {
			estado = "aprobada"
		}
		if _, err := tx.Exec(ctx, `UPDATE verificaciones_dni SET estado = $2, motivo = $3, revisada_por = $4, revisada_en = now() WHERE id::text = $1`,
			id, estado, motivo, equipoID); err != nil {
			return problema.Conflicto("repetido", "Ese DNI ya está verificado en otra cuenta.")
		}
		// Resuelta: las fotos (frente, dorso y selfie) se borran ya (ver fotos_dni.go).
		if _, err := soltarFotosDNI(ctx, tx, id); err != nil {
			return err
		}
		if !aprobar {
			return nil
		}
		hoy := s.ahora()
		edad := hoy.Year() - nac.Year()
		if hoy.YearDay() < nac.YearDay() {
			edad--
		}
		if _, err := tx.Exec(ctx, `UPDATE usuarios SET dni_en = now(), edad = $2 WHERE id = $1`, usuario, edad); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `INSERT INTO usuarios_privado (usuario_id, nacimiento) VALUES ($1, $2)
			ON CONFLICT (usuario_id) DO UPDATE SET nacimiento = excluded.nacimiento`, usuario, nac)
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
