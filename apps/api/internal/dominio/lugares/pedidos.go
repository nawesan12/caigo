package lugares

import (
	"context"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// PedidoPlacas: placas Caigo Tap que un lugar le pide al equipo (antes era un mail).
type PedidoPlacas struct {
	ID         string     `json:"id"`
	Cantidad   int        `json:"cantidad"`
	Tipo       string     `json:"tipo"` // comun | ntag424
	Nota       string     `json:"nota"`
	CreadoEn   time.Time  `json:"creadoEn"`
	AtendidoEn *time.Time `json:"atendidoEn,omitempty"`
	Respuesta  string     `json:"respuesta,omitempty"`
	// Sólo para el equipo: de qué lugar es y quién lo pidió.
	LugarID        string `json:"lugarId,omitempty"`
	LugarNombre    string `json:"lugarNombre,omitempty"`
	LugarCiudad    string `json:"lugarCiudad,omitempty"`
	LugarDireccion string `json:"lugarDireccion,omitempty"`
	LugarTelefono  string `json:"lugarTelefono,omitempty"`
	LugarInstagram string `json:"lugarInstagram,omitempty"`
	PidioNombre    string `json:"pidioNombre,omitempty"`
}

// Pedidos sin atender que puede tener un lugar a la vez.
const maxPedidosPendientes = 3

// PedirPlacas registra el pedido y avisa al equipo. El lugar tiene que estar aprobado.
func (s *Servicio) PedirPlacas(ctx context.Context, usuarioID, lugarID string, cantidad int, tipo, nota string) (PedidoPlacas, error) {
	nota = strings.TrimSpace(nota)
	var campos []problema.Campo
	if cantidad < 1 || cantidad > 20 {
		campos = append(campos, problema.Campo{Ruta: "cantidad", Mensaje: "Pedí de 1 a 20 placas."})
	}
	if tipo != "comun" && tipo != "ntag424" {
		campos = append(campos, problema.Campo{Ruta: "tipo", Mensaje: "Elegí placa común o NTAG 424."})
	}
	if len([]rune(nota)) > 500 {
		campos = append(campos, problema.Campo{Ruta: "nota", Mensaje: "La nota es de hasta 500 letras."})
	}
	if len(campos) > 0 {
		return PedidoPlacas{}, problema.Invalido("Revisá el pedido.", campos...)
	}
	p := PedidoPlacas{Cantidad: cantidad, Tipo: tipo, Nota: nota}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, true); err != nil {
			return err
		}
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM pedidos_placas WHERE lugar_id::text = $1 AND atendido_en IS NULL`,
			lugarID).Scan(&n); err != nil {
			return err
		}
		if n >= maxPedidosPendientes {
			return problema.Conflicto("demasiados", "Ya tenés pedidos en camino. Te escribimos apenas los despachemos.")
		}
		if err := tx.QueryRow(ctx, `INSERT INTO pedidos_placas (lugar_id, usuario_id, cantidad, tipo, nota)
			VALUES ($1, $2, $3, $4, $5) RETURNING id, creado_en`, lugarID, usuarioID, cantidad, tipo, nota).Scan(&p.ID, &p.CreadoEn); err != nil {
			return err
		}
		return outbox.Publicar(ctx, tx, "placas.pedido", map[string]any{"id": p.ID, "lugarId": lugarID})
	})
	return p, err
}

// PedidosDelLugar: los pedidos de placas del lugar, los más nuevos primero.
func (s *Servicio) PedidosDelLugar(ctx context.Context, usuarioID, lugarID string) ([]PedidoPlacas, error) {
	out := []PedidoPlacas{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		rows, err := tx.Query(ctx, `SELECT id, cantidad, tipo, nota, creado_en, atendido_en, respuesta FROM pedidos_placas
			WHERE lugar_id::text = $1 ORDER BY creado_en DESC LIMIT 20`, lugarID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var p PedidoPlacas
			if err := rows.Scan(&p.ID, &p.Cantidad, &p.Tipo, &p.Nota, &p.CreadoEn, &p.AtendidoEn, &p.Respuesta); err != nil {
				return err
			}
			out = append(out, p)
		}
		return rows.Err()
	})
	return out, err
}

// PedidosPlacas (equipo): los pendientes primero (el más viejo arriba) y los atendidos de los
// últimos 30 días.
func (s *Servicio) PedidosPlacas(ctx context.Context) ([]PedidoPlacas, error) {
	out := []PedidoPlacas{}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT p.id, p.cantidad, p.tipo, p.nota, p.creado_en, p.atendido_en, p.respuesta,
				l.id, l.nombre, l.ciudad, l.direccion, l.telefono, l.instagram, u.nombre
			FROM pedidos_placas p JOIN lugares l ON l.id = p.lugar_id JOIN usuarios u ON u.id = p.usuario_id
			WHERE p.atendido_en IS NULL OR p.atendido_en > now() - interval '30 days'
			ORDER BY p.atendido_en IS NOT NULL, p.creado_en LIMIT 200`)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var p PedidoPlacas
			if err := rows.Scan(&p.ID, &p.Cantidad, &p.Tipo, &p.Nota, &p.CreadoEn, &p.AtendidoEn, &p.Respuesta,
				&p.LugarID, &p.LugarNombre, &p.LugarCiudad, &p.LugarDireccion, &p.LugarTelefono, &p.LugarInstagram, &p.PidioNombre); err != nil {
				return err
			}
			out = append(out, p)
		}
		return rows.Err()
	})
	return out, err
}

// AtenderPedidoPlacas lo marca como atendido, con lo que se hizo (lo ve el lugar en su panel).
func (s *Servicio) AtenderPedidoPlacas(ctx context.Context, equipoID, id, nota string) error {
	nota = strings.TrimSpace(nota)
	if len([]rune(nota)) > 500 {
		return problema.Invalido("La nota es de hasta 500 letras.")
	}
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `UPDATE pedidos_placas SET atendido_en = now(), atendido_por = $2, respuesta = $3
			WHERE id::text = $1 AND atendido_en IS NULL`, id, equipoID, nota)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problema.NoEncontrado("Ese pedido no existe o ya se atendió.")
		}
		return nil
	})
}
