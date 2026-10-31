package tiendas

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/google"
)

const scopePlay = "https://www.googleapis.com/auth/androidpublisher"

// Google pregunta a la Play Developer API por una suscripción.
type Google struct {
	Cuenta  *google.Cuenta
	Paquete string // ar.com.caigo.app
	// Base (tests). Vacía: la de Google.
	Base string
}

// SuscripcionGoogle es lo que importa de SubscriptionPurchaseV2.
type SuscripcionGoogle struct {
	Estado string // SUBSCRIPTION_STATE_ACTIVE, _IN_GRACE_PERIOD, _CANCELED, _EXPIRED, _ON_HOLD, _PAUSED, _PENDING
	// Producto: el id de la suscripción en Play Console (plus_semana, plus_mes, plus_temporada).
	Producto string
	Vence    time.Time
	Renueva  bool
	// Cuenta: obfuscatedExternalAccountId, la app lo pone con el id de la persona en Caigo.
	Cuenta   string
	Reconoce bool // falta acknowledge
	Prueba   bool
}

// ErrApagado: falta configurar la tienda.
var ErrApagado = errors.New("tiendas: sin configurar")

func (g *Google) base() string {
	if g.Base != "" {
		return g.Base
	}
	return "https://androidpublisher.googleapis.com"
}

func (g *Google) pedir(ctx context.Context, metodo, ruta string, salida any) error {
	if g == nil || g.Cuenta == nil {
		return ErrApagado
	}
	tok, err := g.Cuenta.Token(ctx, scopePlay)
	if err != nil {
		return err
	}
	req, _ := http.NewRequestWithContext(ctx, metodo, g.base()+ruta, nil)
	req.Header.Set("Authorization", "Bearer "+tok)
	res, err := g.Cuenta.Cliente().Do(req)
	if err != nil {
		return errors.New("tiendas: no se pudo hablar con Google Play")
	}
	defer res.Body.Close()
	b, _ := io.ReadAll(io.LimitReader(res.Body, 256<<10))
	if res.StatusCode >= 300 {
		return fmt.Errorf("tiendas: Google Play respondió %d", res.StatusCode)
	}
	if salida == nil || len(b) == 0 {
		return nil
	}
	return json.Unmarshal(b, salida)
}

// Suscripcion lee el estado de una compra por su purchaseToken.
func (g *Google) Suscripcion(ctx context.Context, token string) (SuscripcionGoogle, error) {
	var r struct {
		SubscriptionState    string `json:"subscriptionState"`
		AcknowledgementState string `json:"acknowledgementState"`
		LineItems            []struct {
			ProductID        string `json:"productId"`
			ExpiryTime       string `json:"expiryTime"`
			AutoRenewingPlan *struct {
				AutoRenewEnabled bool `json:"autoRenewEnabled"`
			} `json:"autoRenewingPlan"`
			OfferDetails *struct {
				OfferID string `json:"offerId"`
			} `json:"offerDetails"`
		} `json:"lineItems"`
		ExternalAccountIdentifiers *struct {
			ObfuscatedExternalAccountID string `json:"obfuscatedExternalAccountId"`
		} `json:"externalAccountIdentifiers"`
	}
	ruta := "/androidpublisher/v3/applications/" + url.PathEscape(g.Paquete) + "/purchases/subscriptionsv2/tokens/" + url.PathEscape(token)
	if err := g.pedir(ctx, http.MethodGet, ruta, &r); err != nil {
		return SuscripcionGoogle{}, err
	}
	s := SuscripcionGoogle{Estado: r.SubscriptionState, Reconoce: r.AcknowledgementState == "ACKNOWLEDGEMENT_STATE_PENDING"}
	if r.ExternalAccountIdentifiers != nil {
		s.Cuenta = r.ExternalAccountIdentifiers.ObfuscatedExternalAccountID
	}
	for _, li := range r.LineItems {
		t, err := time.Parse(time.RFC3339Nano, li.ExpiryTime)
		if err == nil && t.After(s.Vence) {
			s.Vence, s.Producto = t, li.ProductID
			s.Renueva = li.AutoRenewingPlan != nil && li.AutoRenewingPlan.AutoRenewEnabled
			s.Prueba = li.OfferDetails != nil && li.OfferDetails.OfferID != ""
		}
	}
	return s, nil
}

// Reconocer hace el acknowledge (sin él, Google devuelve la plata a los 3 días).
func (g *Google) Reconocer(ctx context.Context, producto, token string) error {
	ruta := "/androidpublisher/v3/applications/" + url.PathEscape(g.Paquete) + "/purchases/subscriptions/" +
		url.PathEscape(producto) + "/tokens/" + url.PathEscape(token) + ":acknowledge"
	return g.pedir(ctx, http.MethodPost, ruta, nil)
}
