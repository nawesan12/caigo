package plus

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/tiendas"
)

// Tiendas con las que se compra Plus. Cualquiera en nil: esa vía queda apagada.
type Tiendas struct {
	Apple *tiendas.Apple
	Play  *tiendas.Google
	MP    *mercadopago.Config
	// Sitio público (vuelta de Mercado Pago).
	Sitio string
}

// Apple registra una compra de StoreKit 2 (el JWS de la transacción que manda la app). Una compra de
// prueba (sandbox, Xcode: TestFlight) sólo cuenta para alguien del equipo: si no, Plus saldría gratis.
func (s *Servicio) Apple(ctx context.Context, t *Tiendas, usuarioID string, equipo bool, jws string) (Estado, error) {
	if t.Apple == nil {
		return Estado{}, problema.NoProcesable("apagado", "Las compras en iPhone todavía no están disponibles.")
	}
	tx, err := t.Apple.Transaccion(jws)
	if err != nil {
		return Estado{}, problema.Invalido("No pudimos validar la compra con Apple.")
	}
	// La app pone appAccountToken = el id de la persona: una compra hecha por otra cuenta no se pasa.
	if tx.AppAccountToken != "" && !strings.EqualFold(tx.AppAccountToken, usuarioID) {
		return Estado{}, problema.Conflicto("otra_cuenta", "Esta compra es de otra cuenta de Caigo.")
	}
	if tx.Environment != "Production" && !equipo {
		return Estado{}, problema.Conflicto("compra_de_prueba", "Esa compra es de prueba: no activa Caigo Plus.")
	}
	plan, ok := PlanDeProducto(tx.ProductID)
	if !ok {
		return Estado{}, problema.Invalido("Ese producto no es de Caigo Plus.")
	}
	estado := "activa"
	if tx.OfferType == 1 {
		estado = "prueba"
	}
	if tx.RevocationDate != 0 || (!tx.Vence().IsZero() && tx.Vence().Before(s.ahora())) {
		estado = "vencida"
	}
	if err := s.Guardar(ctx, usuarioID, "apple", tx.OriginalTransactionID, plan, estado, tx.Vence(), true); err != nil {
		return Estado{}, err
	}
	return s.Ver(ctx, usuarioID)
}

// estadoGoogle traduce el estado de Play al nuestro.
func estadoGoogle(g tiendas.SuscripcionGoogle) string {
	switch g.Estado {
	case "SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
		if g.Prueba {
			return "prueba"
		}
		return "activa"
	case "SUBSCRIPTION_STATE_CANCELED":
		return "cancelada"
	case "SUBSCRIPTION_STATE_PENDING":
		return "pendiente"
	}
	return "vencida"
}

// Google registra una compra de Play Billing (el purchaseToken que manda la app).
func (s *Servicio) Google(ctx context.Context, t *Tiendas, usuarioID, token string) (Estado, error) {
	if t.Play == nil {
		return Estado{}, problema.NoProcesable("apagado", "Las compras en Android todavía no están disponibles.")
	}
	g, err := t.Play.Suscripcion(ctx, token)
	if err != nil {
		return Estado{}, err
	}
	if g.Cuenta != "" && g.Cuenta != usuarioID {
		return Estado{}, problema.Conflicto("otra_cuenta", "Esta compra es de otra cuenta de Caigo.")
	}
	plan, ok := PlanDeProducto(g.Producto)
	if !ok {
		return Estado{}, problema.Invalido("Ese producto no es de Caigo Plus.")
	}
	if err := s.Guardar(ctx, usuarioID, "google", token, plan, estadoGoogle(g), g.Vence, g.Renueva); err != nil {
		return Estado{}, err
	}
	if g.Reconoce {
		if err := t.Play.Reconocer(ctx, g.Producto, token); err != nil {
			return Estado{}, err
		}
	}
	return s.Ver(ctx, usuarioID)
}

// MercadoPago crea la suscripción (preapproval pendiente) para pagar Plus desde la web y devuelve
// el link de pago. Plus se activa cuando Mercado Pago avisa que quedó autorizada.
func (s *Servicio) MercadoPago(ctx context.Context, t *Tiendas, usuarioID, email, plan string) (string, error) {
	if t.MP == nil || t.MP.TokenPlataforma == "" {
		return "", problema.NoProcesable("apagado", "El pago con Mercado Pago todavía no está disponible.")
	}
	p, ok := Planes[plan]
	if !ok {
		return "", problema.Invalido("Elegí Semana, Mes o Temporada.")
	}
	if email == "" {
		return "", problema.NoProcesable("sin_email", "Para pagar con Mercado Pago necesitamos un mail en tu cuenta.")
	}
	// Los 7 días gratis, una sola vez: quien ya tuvo Plus pago (de cualquier tienda) paga desde el primer día.
	var usada bool
	if err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var err error
		usada, err = PruebaUsada(ctx, tx, usuarioID)
		return err
	}); err != nil {
		return "", err
	}
	rec := map[string]any{"transaction_amount": p.Centavos / 100, "currency_id": "ARS"}
	switch plan {
	case "semana":
		rec["frequency"], rec["frequency_type"] = 7, "days"
	case "mes":
		rec["frequency"], rec["frequency_type"] = 1, "months"
		if !usada {
			rec["free_trial"] = map[string]any{"frequency": 7, "frequency_type": "days"}
		}
	case "temporada":
		rec["frequency"], rec["frequency_type"] = 3, "months"
	}
	var r struct {
		ID        string `json:"id"`
		InitPoint string `json:"init_point"`
	}
	if err := t.MP.JSON(ctx, http.MethodPost, "/preapproval", t.MP.TokenPlataforma, "", map[string]any{
		"reason": "Caigo Plus · " + p.Nombre, "external_reference": usuarioID + ":" + plan,
		"payer_email": mercadopago.SinEtiqueta(email), "auto_recurring": rec,
		"back_url": strings.TrimRight(t.Sitio, "/") + "/plus?vuelta=1", "status": "pending",
	}, &r); err != nil {
		return "", err
	}
	if r.ID == "" || r.InitPoint == "" {
		return "", errors.New("plus: la suscripción no trajo init_point")
	}
	if err := s.Guardar(ctx, usuarioID, "mercadopago", r.ID, plan, "pendiente", time.Time{}, true); err != nil {
		return "", err
	}
	return r.InitPoint, nil
}

// CancelarMP da de baja en Mercado Pago las suscripciones vivas de la persona (el botón «Dar de baja» de
// la web y la baja de la cuenta). Lo ya pago sigue hasta su vencimiento: sólo deja de renovarse.
func (s *Servicio) CancelarMP(ctx context.Context, t *Tiendas, usuarioID string) error {
	var ids []string
	if err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT externo FROM suscripciones WHERE usuario_id = $1 AND origen = 'mercadopago'
			AND (estado = 'pendiente' OR (estado = 'activa' AND renueva))`, usuarioID)
		if err != nil {
			return err
		}
		ids, err = pgx.CollectRows(rows, pgx.RowTo[string])
		return err
	}); err != nil {
		return err
	}
	if len(ids) == 0 {
		return nil
	}
	if t == nil || t.MP == nil || t.MP.TokenPlataforma == "" {
		return problema.NoProcesable("apagado", "No pudimos dar de baja la suscripción en Mercado Pago. Probá de nuevo en un rato.")
	}
	for _, id := range ids {
		if err := t.MP.JSON(ctx, http.MethodPut, "/preapproval/"+id, t.MP.TokenPlataforma, "", map[string]any{"status": "cancelled"}, nil); err != nil {
			return err
		}
		if err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
			_, err := tx.Exec(ctx, `UPDATE suscripciones SET estado = CASE WHEN estado = 'pendiente' THEN 'cancelada' ELSE estado END,
				renueva = false, actualizada_en = now() WHERE origen = 'mercadopago' AND externo = $1`, id)
			return err
		}); err != nil {
			return err
		}
	}
	return nil
}

// ConciliarMP lee el preapproval y actualiza Plus (aviso subscription_preapproval).
func (s *Servicio) ConciliarMP(ctx context.Context, t *Tiendas, id string) error {
	if t.MP == nil || t.MP.TokenPlataforma == "" {
		return nil
	}
	var p struct {
		ID                string `json:"id"`
		Status            string `json:"status"`
		ExternalReference string `json:"external_reference"`
		NextPaymentDate   string `json:"next_payment_date"`
	}
	if err := t.MP.JSON(ctx, http.MethodGet, "/preapproval/"+id, t.MP.TokenPlataforma, "", nil, &p); err != nil {
		return err
	}
	usuario, plan, ok := strings.Cut(p.ExternalReference, ":")
	if !ok || Planes[plan].Producto == "" {
		return nil // no es de Plus
	}
	estado, renueva := "pendiente", true
	var vence time.Time
	switch p.Status {
	case "authorized":
		estado = "activa"
		// Vale hasta el próximo cobro, con dos días de margen por si el cobro se demora.
		if n, err := time.Parse(time.RFC3339Nano, p.NextPaymentDate); err == nil {
			vence = n.Add(48 * time.Hour)
		}
	case "paused", "cancelled":
		estado, renueva = "cancelada", false
	}
	if vence.IsZero() && estado == "activa" {
		return fmt.Errorf("plus: preapproval %s autorizado sin próximo cobro", id)
	}
	err := s.Guardar(ctx, usuario, "mercadopago", p.ID, plan, estado, vence, renueva)
	if errors.Is(err, ErrOtraCuenta) {
		return s.Actualizar(ctx, "mercadopago", p.ID, plan, estado, vence, renueva)
	}
	return err
}

// AvisoApple procesa una App Store Server Notification V2.
func (s *Servicio) AvisoApple(ctx context.Context, t *Tiendas, signedPayload string) error {
	if t.Apple == nil {
		return nil
	}
	n, err := t.Apple.Notificacion(signedPayload)
	if err != nil {
		return problema.Invalido("Firma inválida.")
	}
	if n.Transaccion == nil {
		return nil
	}
	tx := n.Transaccion
	plan, _ := PlanDeProducto(tx.ProductID)
	// Las de prueba no crean ni pasan suscripciones: sólo actualizan la que el equipo registró desde
	// la app (POST /v1/plus/apple, que es la que las acepta).
	if tx.Environment != "Production" {
		return s.Actualizar(ctx, "apple", tx.OriginalTransactionID, plan, estadoApple(n.Tipo, n.Renueva), tx.Vence(), n.Renueva == nil || *n.Renueva)
	}
	estado := estadoApple(n.Tipo, n.Renueva)
	renueva := n.Renueva == nil || *n.Renueva
	// Si la compra entró por notificación antes que por la app, se crea con el appAccountToken. Si ya
	// era de otra persona, no se pasa: sólo se actualiza.
	if tx.AppAccountToken != "" && plan != "" {
		err := s.Guardar(ctx, strings.ToLower(tx.AppAccountToken), "apple", tx.OriginalTransactionID, plan, estado, tx.Vence(), renueva)
		if !errors.Is(err, ErrOtraCuenta) {
			return err
		}
	}
	return s.Actualizar(ctx, "apple", tx.OriginalTransactionID, plan, estado, tx.Vence(), renueva)
}

// estadoApple traduce el tipo de una notificación de Apple a nuestro estado.
func estadoApple(tipo string, renueva *bool) string {
	switch tipo {
	case "EXPIRED", "GRACE_PERIOD_EXPIRED", "REVOKE", "REFUND":
		return "vencida"
	case "DID_CHANGE_RENEWAL_STATUS":
		if renueva != nil && !*renueva {
			return "cancelada"
		}
	}
	return "activa"
}

// AvisoGoogle procesa una Real-time Developer Notification (por Pub/Sub): vuelve a leer el estado y,
// si Google todavía espera la confirmación (acknowledge) de una compra vigente, la confirma. Así no
// depende de que la app llegue a mandar POST /v1/plus/google: si se cerró o perdió la red entre la
// compra y ese pedido, Google la reintegraba sola a los 3 días. Si la confirmación falla, devuelve el
// error y Pub/Sub reintenta el aviso (guardar el estado otra vez no cambia nada).
func (s *Servicio) AvisoGoogle(ctx context.Context, t *Tiendas, token string) error {
	if t.Play == nil || token == "" {
		return nil
	}
	g, err := t.Play.Suscripcion(ctx, token)
	if err != nil {
		return err
	}
	plan, _ := PlanDeProducto(g.Producto)
	err = ErrOtraCuenta
	if g.Cuenta != "" && plan != "" {
		err = s.Guardar(ctx, g.Cuenta, "google", token, plan, estadoGoogle(g), g.Vence, g.Renueva)
	}
	if errors.Is(err, ErrOtraCuenta) {
		err = s.Actualizar(ctx, "google", token, plan, estadoGoogle(g), g.Vence, g.Renueva)
	}
	if err != nil {
		return err
	}
	// Sólo las de Plus y con una persona de Caigo detrás (la app pone su id en la compra): una compra
	// que no sabemos de quién es no se confirma, y Google la devuelve.
	if g.Reconoce && plan != "" && g.Cuenta != "" && vigenteGoogle(g, s.ahora()) {
		return t.Play.Reconocer(ctx, g.Producto, token)
	}
	return nil
}

// vigenteGoogle: Google le da acceso (activa, en período de gracia, o dada de baja sin vencer).
func vigenteGoogle(g tiendas.SuscripcionGoogle, ahora time.Time) bool {
	switch g.Estado {
	case "SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED":
		return g.Vence.After(ahora)
	}
	return false
}
