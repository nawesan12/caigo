// Package plus: Caigo Plus. Sin Plus hay 3 "sumadas" por día en Gente y Citas (se reinician a la
// medianoche de Argentina) y las listas de "Te sumaron" y "Te invitan" se ven borrosas. Se compra
// con la compra integrada de cada tienda (StoreKit en iPhone, Play Billing en Android) o con
// Mercado Pago desde la web; la API unifica el estado en la tabla suscripciones.
package plus

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
)

// LimiteDiario de sumadas sin Plus.
const LimiteDiario = 3

// "Me encanta" por día en Citas: 1 sin Plus, 5 con Plus (también cuentan como sumadas).
const (
	EncantasGratis = 1
	EncantasPlus   = 5
)

// SemanaDestacado: con Plus se destaca un plan por semana.
const SemanaDestacado = 7 * 24 * time.Hour

// Planes de Plus (precios en centavos). Los ids de producto son los mismos en App Store y Play.
var Planes = map[string]struct {
	Producto string
	Centavos int64
	Nombre   string
}{
	"semana":    {"plus_semana", 249900, "Semana"},
	"mes":       {"plus_mes", 599900, "Mes"},
	"temporada": {"plus_temporada", 1399900, "Temporada"},
}

// PlanDeProducto traduce el id de producto de la tienda al plan.
func PlanDeProducto(producto string) (string, bool) {
	for k, v := range Planes {
		if v.Producto == producto {
			return k, true
		}
	}
	return "", false
}

var argentina = func() *time.Location {
	l, err := time.LoadLocation("America/Argentina/Buenos_Aires")
	if err != nil {
		return time.FixedZone("ART", -3*3600)
	}
	return l
}()

// EnArgentina pasa una hora a la de Argentina.
func EnArgentina(t time.Time) time.Time { return t.In(argentina) }

// Medianoche de hoy y de mañana en Argentina.
func Dia(t time.Time) (desde, hasta time.Time) {
	a := t.In(argentina)
	desde = time.Date(a.Year(), a.Month(), a.Day(), 0, 0, 0, 0, argentina)
	return desde, desde.AddDate(0, 0, 1)
}

// Estado de Plus de una persona.
type Estado struct {
	Activo  bool       `json:"activo"`
	Plan    string     `json:"plan,omitempty"`
	Origen  string     `json:"origen,omitempty"`
	Vence   *time.Time `json:"vence,omitempty"`
	Renueva bool       `json:"renueva"`
	// Sumadas de hoy y el límite (sin Plus), y cuándo se reinicia.
	SumadasHoy int       `json:"sumadasHoy"`
	Limite     int       `json:"limite"`
	ReiniciaEn time.Time `json:"reiniciaEn"`
	// "Me encanta" de hoy y cuántos hay por día.
	EncantasHoy    int `json:"encantasHoy"`
	LimiteEncantas int `json:"limiteEncantas"`
	// Cuándo puede destacar otro plan (vacío: ya puede, si tiene Plus).
	DestacarDesde *time.Time `json:"destacarDesde,omitempty"`
}

// Leer devuelve el estado dentro de una transacción ya abierta (con la persona fijada).
func Leer(ctx context.Context, tx pgx.Tx, usuarioID string, ahora time.Time) (Estado, error) {
	desde, hasta := Dia(ahora)
	e := Estado{Limite: LimiteDiario, ReiniciaEn: hasta}
	var vence time.Time
	err := tx.QueryRow(ctx, `SELECT plan, origen, vence_en, renueva FROM suscripciones
		WHERE usuario_id = $1 AND estado IN ('activa', 'prueba', 'cancelada') AND vence_en > $2
		ORDER BY vence_en DESC LIMIT 1`, usuarioID, ahora).Scan(&e.Plan, &e.Origen, &vence, &e.Renueva)
	if err == nil {
		e.Activo, e.Vence = true, &vence
	} else if !errors.Is(err, pgx.ErrNoRows) {
		return e, err
	}
	e.LimiteEncantas = EncantasGratis
	if e.Activo {
		e.LimiteEncantas = EncantasPlus
	}
	var ultimo *time.Time
	err = tx.QueryRow(ctx, `SELECT
			(SELECT count(*) FROM intereses WHERE usuario_id = $1 AND accion <> 'paso' AND creado_en >= $2),
			(SELECT count(*) FROM intereses WHERE usuario_id = $1 AND accion = 'encanta' AND creado_en >= $2),
			(SELECT max(destacado_en) FROM planes WHERE organizador_id = $1 AND destacado_en IS NOT NULL)`,
		usuarioID, desde).Scan(&e.SumadasHoy, &e.EncantasHoy, &ultimo)
	if ultimo != nil && ultimo.Add(SemanaDestacado).After(ahora) {
		d := ultimo.Add(SemanaDestacado)
		e.DestacarDesde = &d
	}
	return e, err
}

// Servicio de Plus.
type Servicio struct {
	App   *db.Pool
	Reloj func() time.Time
}

func (s *Servicio) ahora() time.Time {
	if s.Reloj != nil {
		return s.Reloj()
	}
	return time.Now()
}

// Ver el estado de Plus.
func (s *Servicio) Ver(ctx context.Context, usuarioID string) (Estado, error) {
	var e Estado
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var err error
		e, err = Leer(ctx, tx, usuarioID, s.ahora())
		return err
	})
	return e, err
}

// Guardar registra (o actualiza) una suscripción de una tienda. Corre como sistema: el
// externo puede haber quedado de otra cuenta (la misma compra restaurada en otro usuario).
func (s *Servicio) Guardar(ctx context.Context, usuarioID, origen, externo, plan, estado string, vence time.Time, renueva bool) error {
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `
			INSERT INTO suscripciones (usuario_id, origen, externo, plan, estado, vence_en, renueva)
			VALUES ($1, $2, $3, $4, $5, $6, $7)
			ON CONFLICT (origen, externo) DO UPDATE SET usuario_id = coalesce(excluded.usuario_id, suscripciones.usuario_id),
				plan = excluded.plan, estado = excluded.estado, vence_en = excluded.vence_en,
				renueva = excluded.renueva, actualizada_en = now()`,
			usuarioID, origen, externo, plan, estado, nulo(vence), renueva)
		return err
	})
}

// Actualizar cambia una suscripción que ya existe (avisos de las tiendas: no saben de qué persona es).
func (s *Servicio) Actualizar(ctx context.Context, origen, externo, plan, estado string, vence time.Time, renueva bool) error {
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `UPDATE suscripciones SET plan = coalesce(nullif($3, ''), plan), estado = $4,
			vence_en = coalesce($5, vence_en), renueva = $6, actualizada_en = now() WHERE origen = $1 AND externo = $2`,
			origen, externo, plan, estado, nulo(vence), renueva)
		return err
	})
}

func nulo(t time.Time) *time.Time {
	if t.IsZero() {
		return nil
	}
	return &t
}
