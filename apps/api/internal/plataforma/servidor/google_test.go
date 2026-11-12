package servidor_test

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/google"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/tiendas"
)

// playFalso imita la Play Developer API: el token de la cuenta de servicio, la suscripción y el
// acknowledge (anota cuáles se confirmaron).
type playFalso struct {
	mu          sync.Mutex
	cuenta      string // obfuscatedExternalAccountId
	reconocida  bool
	reconocidas []string
}

func (f *playFalso) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	switch {
	case r.URL.Path == "/token":
		_ = json.NewEncoder(w).Encode(map[string]string{"access_token": "ya29.prueba"})
	case r.Method == http.MethodGet && strings.Contains(r.URL.Path, "/purchases/subscriptionsv2/tokens/"):
		ack := "ACKNOWLEDGEMENT_STATE_PENDING"
		if f.reconocida {
			ack = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
		}
		_ = json.NewEncoder(w).Encode(map[string]any{
			"subscriptionState": "SUBSCRIPTION_STATE_ACTIVE", "acknowledgementState": ack,
			"lineItems": []map[string]any{{"productId": "plus_mes", "expiryTime": time.Now().Add(30 * 24 * time.Hour).Format(time.RFC3339Nano),
				"autoRenewingPlan": map[string]bool{"autoRenewEnabled": true}}},
			"externalAccountIdentifiers": map[string]string{"obfuscatedExternalAccountId": f.cuenta},
		})
	case r.Method == http.MethodPost && strings.HasSuffix(r.URL.Path, ":acknowledge"):
		f.reconocidas = append(f.reconocidas, r.URL.Path)
		f.reconocida = true
		w.WriteHeader(http.StatusOK)
	default:
		http.NotFound(w, r)
	}
}

// avisoGoogle manda una RTDN como la manda Pub/Sub.
func avisoGoogle(t *testing.T, e *entorno, clave, token string) int {
	t.Helper()
	datos, _ := json.Marshal(map[string]any{"subscriptionNotification": map[string]any{"notificationType": 4, "purchaseToken": token}})
	cuerpo := fmt.Sprintf(`{"message":{"data":%q}}`, base64.StdEncoding.EncodeToString(datos))
	w := httptest.NewRecorder()
	e.h.ServeHTTP(w, httptest.NewRequest("POST", "/v1/webhooks/google?clave="+clave, strings.NewReader(cuerpo)))
	return w.Code
}

// Si la app no llega a mandar la compra, el aviso de Google la registra y la confirma (si no, Google
// la reintegra a los 3 días). Confirmada, otro aviso no la vuelve a confirmar.
func TestAvisoGoogleConfirmaLaCompra(t *testing.T) {
	f := &playFalso{}
	srv := httptest.NewServer(f)
	defer srv.Close()
	clave, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	cuenta := &google.Cuenta{Proyecto: "p", Email: "play@p.iam.gserviceaccount.com", Clave: clave, URLToken: srv.URL + "/token", HTTP: srv.Client()}
	play := &tiendas.Google{Cuenta: cuenta, Paquete: "ar.com.caigo.app", Base: srv.URL}
	e := nuevoEntornoAjustado(t, func(d *servidor.Dependencias) {
		d.Tiendas = &plus.Tiendas{Play: play}
		d.ClaveGoogle = "clave-pubsub"
	})
	yo := e.persona("Coti")
	f.cuenta = yo.id
	token := "tok-" + yo.id

	if c := avisoGoogle(t, e, "otra", token); c != http.StatusNotFound {
		t.Fatalf("sin la clave: %d", c)
	}
	if c := avisoGoogle(t, e, "clave-pubsub", token); c != http.StatusOK {
		t.Fatalf("aviso: %d", c)
	}
	if len(f.reconocidas) != 1 || !strings.Contains(f.reconocidas[0], "/subscriptions/plus_mes/tokens/"+token+":acknowledge") {
		t.Fatalf("acknowledge: %v", f.reconocidas)
	}
	var est struct {
		Estado struct {
			Activo bool
			Plan   string
		}
	}
	e.pedir(yo, "GET", "/v1/plus", nil, http.StatusOK, &est)
	if !est.Estado.Activo || est.Estado.Plan != "mes" {
		t.Fatalf("plus: %+v", est)
	}
	if c := avisoGoogle(t, e, "clave-pubsub", token); c != http.StatusOK || len(f.reconocidas) != 1 {
		t.Fatalf("segundo aviso: %d %v", c, f.reconocidas)
	}

	// Una compra sin persona de Caigo detrás no se confirma.
	f.cuenta, f.reconocida = "", false
	if c := avisoGoogle(t, e, "clave-pubsub", "tok-sin-cuenta-"+yo.id); c != http.StatusOK || len(f.reconocidas) != 1 {
		t.Fatalf("compra sin cuenta: %d %v", c, f.reconocidas)
	}
}
