package moderacion

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Item de la cola del equipo (GET /v1/equipo/moderacion).
type Item struct {
	ID         string  `json:"id"`
	Tipo       string  `json:"tipo"`
	ObjetoID   *string `json:"objetoId,omitempty"`
	PlanID     *string `json:"planId,omitempty"`
	PlanTitulo string  `json:"planTitulo,omitempty"`
	Autor      struct {
		ID     string `json:"id"`
		Nombre string `json:"nombre"`
	} `json:"autor"`
	// Reportes que tiene el autor (abiertos o resueltos) y otras veces que la moderación lo marcó.
	AutorReportes     int       `json:"autorReportes"`
	AutorModeraciones int       `json:"autorModeraciones"`
	Texto             string    `json:"texto"`
	Origen            string    `json:"origen"`
	Categoria         string    `json:"categoria"`
	Motivo            string    `json:"motivo"`
	Estado            string    `json:"estado"`
	CreadaEn          time.Time `json:"creadaEn"`
}

// Cola: lo abierto, lo más viejo primero (lo que dice ser menor va arriba de todo).
func (s *Servicio) Cola(ctx context.Context) ([]Item, error) {
	out := []Item{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `
			SELECT m.id, m.tipo, m.objeto_id, m.plan_id::text, coalesce(p.titulo, ''), u.id, u.nombre,
				(SELECT count(*) FROM reportes r WHERE r.sobre_usuario_id = u.id)::integer,
				(SELECT count(*) FROM moderaciones x WHERE x.usuario_id = u.id AND x.id <> m.id)::integer,
				m.texto, m.origen, m.categoria, m.motivo, m.estado, m.creada_en
			FROM moderaciones m JOIN usuarios u ON u.id = m.usuario_id LEFT JOIN planes p ON p.id = m.plan_id
			WHERE m.resuelta_en IS NULL
			ORDER BY m.categoria = 'menores' DESC, m.creada_en LIMIT 200`)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var i Item
			if err := rows.Scan(&i.ID, &i.Tipo, &i.ObjetoID, &i.PlanID, &i.PlanTitulo, &i.Autor.ID, &i.Autor.Nombre,
				&i.AutorReportes, &i.AutorModeraciones, &i.Texto, &i.Origen, &i.Categoria, &i.Motivo, &i.Estado,
				&i.CreadaEn); err != nil {
				return err
			}
			out = append(out, i)
		}
		return rows.Err()
	})
	return out, err
}

// Acciones del equipo sobre una entrada de la cola.
const (
	AccionAprobar   = "aprobar"
	AccionOcultar   = "ocultar"
	AccionSuspender = "suspender"
)

// Suspensor suspende y veta a una persona en tx (equipo.SuspenderTx; se pasa así para no importar
// equipo, que importa planes, que importa moderacion).
type Suspensor func(ctx context.Context, tx pgx.Tx, usuarioID, motivo string) error

// Resolver cierra una entrada. aprobar: el objeto vuelve a la normalidad. ocultar: se oculta y se avisa
// al autor. suspender: además se suspende la cuenta (y se veta su mail y su DNI).
func (s *Servicio) Resolver(ctx context.Context, equipoID, id, accion, motivo string, suspender Suspensor) error {
	motivo = strings.TrimSpace(motivo)
	resolucion := map[string]string{AccionAprobar: "aprobado", AccionOcultar: "ocultado", AccionSuspender: "suspendido"}[accion]
	switch {
	case resolucion == "":
		return problema.Invalido("La acción es aprobar, ocultar o suspender.")
	case accion == AccionSuspender && motivo == "":
		return problema.Invalido("Decí por qué se suspende (lo ve la persona).")
	case len([]rune(motivo)) > 300:
		return problema.Invalido("El motivo puede tener hasta 300 letras.")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var tipo, usuario string
		var objeto *string
		err := tx.QueryRow(ctx, `UPDATE moderaciones SET resolucion = $2, resuelta_por = $3, resuelta_en = now()
			WHERE id::text = $1 AND resuelta_en IS NULL RETURNING tipo, objeto_id, usuario_id::text`, id, resolucion, equipoID).
			Scan(&tipo, &objeto, &usuario)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Eso ya se revisó.")
		}
		if err != nil {
			return err
		}
		if accion == AccionSuspender {
			if err := suspender(ctx, tx, usuario, motivo); err != nil {
				return err
			}
		}
		if objeto == nil {
			return nil // no se publicó: no hay nada que mostrar ni ocultar
		}
		estado := EstadoOculto
		if accion == AccionAprobar {
			estado = EstadoOK
		}
		return CambiarTx(ctx, tx, tipo, *objeto, estado)
	})
}
