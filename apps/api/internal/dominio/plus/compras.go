package plus

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

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

// Apple registra una compra de StoreKit 2 (el JWS de la transacción que manda la app).
func (s *Servicio) Apple(ctx context.Context, t *Tiendas, usuarioID, jws string) (Estado, error) {
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
	rec := map[string]any{"transaction_amount": p.Centavos / 100, "currency_id": "ARS"}
	switch plan {
	case "semana":
		rec["frequency"], rec["frequency_type"] = 7, "days"
	case "mes":
		rec["frequency"], rec["frequency_type"] = 1, "months"
		rec["free_trial"] = map[string]any{"frequency": 7, "frequency_type": "days"}
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
	return s.Guardar(ctx, usuario, "mercadopago", p.ID, plan, estado, vence, renueva)
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
	estado := "activa"
	switch n.Tipo {
	case "EXPIRED", "GRACE_PERIOD_EXPIRED", "REVOKE", "REFUND":
		estado = "vencida"
	case "DID_CHANGE_RENEWAL_STATUS":
		if n.Renueva != nil && !*n.Renueva {
			estado = "cancelada"
		}
	}
	renueva := n.Renueva == nil || *n.Renueva
	// Si la compra entró por notificación antes que por la app, se crea con el appAccountToken.
	if tx.AppAccountToken != "" && plan != "" {
		return s.Guardar(ctx, strings.ToLower(tx.AppAccountToken), "apple", tx.OriginalTransactionID, plan, estado, tx.Vence(), renueva)
	}
	return s.Actualizar(ctx, "apple", tx.OriginalTransactionID, plan, estado, tx.Vence(), renueva)
}

// AvisoGoogle procesa una Real-time Developer Notification (por Pub/Sub): vuelve a leer el estado.
func (s *Servicio) AvisoGoogle(ctx context.Context, t *Tiendas, token string) error {
	if t.Play == nil || token == "" {
		return nil
	}
	g, err := t.Play.Suscripcion(ctx, token)
	if err != nil {
		return err
	}
	plan, _ := PlanDeProducto(g.Producto)
	if g.Cuenta != "" && plan != "" {
		return s.Guardar(ctx, g.Cuenta, "google", token, plan, estadoGoogle(g), g.Vence, g.Renueva)
	}
	return s.Actualizar(ctx, "google", token, plan, estadoGoogle(g), g.Vence, g.Renueva)
}
