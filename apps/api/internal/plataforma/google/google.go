// Package google se autentica con una cuenta de servicio de Google (el JSON que se baja de la
// consola) y entrega tokens de OAuth para un scope: FCM (push a Android) y la Play Developer API
// (validar Caigo Plus). El token vale una hora; acá se renueva a los 50 minutos. Sólo biblioteca
// estándar.
package google

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"
)

// Cuenta de servicio.
type Cuenta struct {
	Proyecto, Email string
	Clave           *rsa.PrivateKey
	// URLToken (tests). Vacía: la de Google.
	URLToken string
	HTTP     *http.Client

	mu     sync.Mutex
	tokens map[string]token
}

type token struct {
	valor string
	vence time.Time
}

// Leer abre el JSON de la cuenta de servicio. Ruta vacía: nil, nil (apagado).
func Leer(ruta string) (*Cuenta, error) {
	if ruta == "" {
		return nil, nil
	}
	b, err := os.ReadFile(ruta)
	if err != nil {
		return nil, fmt.Errorf("google: no se pudo leer la cuenta de servicio: %w", err)
	}
	var cs struct {
		ProjectID   string `json:"project_id"`
		ClientEmail string `json:"client_email"`
		PrivateKey  string `json:"private_key"`
	}
	if err := json.Unmarshal(b, &cs); err != nil {
		return nil, fmt.Errorf("google: cuenta de servicio inválida: %w", err)
	}
	p, _ := pem.Decode([]byte(cs.PrivateKey))
	if p == nil {
		return nil, errors.New("google: la clave privada no es PEM")
	}
	k, err := x509.ParsePKCS8PrivateKey(p.Bytes)
	if err != nil {
		return nil, fmt.Errorf("google: clave: %w", err)
	}
	rk, ok := k.(*rsa.PrivateKey)
	if !ok || cs.ProjectID == "" || cs.ClientEmail == "" {
		return nil, errors.New("google: la cuenta de servicio no trae proyecto, email o clave RSA")
	}
	return &Cuenta{Proyecto: cs.ProjectID, Email: cs.ClientEmail, Clave: rk}, nil
}

// Cliente HTTP de la cuenta.
func (c *Cuenta) Cliente() *http.Client {
	if c.HTTP != nil {
		return c.HTTP
	}
	return &http.Client{Timeout: 15 * time.Second}
}

// Token devuelve un token de acceso para el scope.
func (c *Cuenta) Token(ctx context.Context, scope string) (string, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if t, ok := c.tokens[scope]; ok && time.Now().Before(t.vence) {
		return t.valor, nil
	}
	aud := c.URLToken
	if aud == "" {
		aud = "https://oauth2.googleapis.com/token"
	}
	ahora := time.Now()
	enc := base64.RawURLEncoding
	cab, _ := json.Marshal(map[string]string{"alg": "RS256", "typ": "JWT"})
	cue, _ := json.Marshal(map[string]any{"iss": c.Email, "scope": scope, "aud": aud,
		"iat": ahora.Unix(), "exp": ahora.Add(time.Hour).Unix()})
	firmable := enc.EncodeToString(cab) + "." + enc.EncodeToString(cue)
	h := sha256.Sum256([]byte(firmable))
	firma, err := rsa.SignPKCS1v15(rand.Reader, c.Clave, crypto.SHA256, h[:])
	if err != nil {
		return "", err
	}
	form := url.Values{"grant_type": {"urn:ietf:params:oauth:grant-type:jwt-bearer"},
		"assertion": {firmable + "." + enc.EncodeToString(firma)}}
	req, _ := http.NewRequestWithContext(ctx, http.MethodPost, aud, strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	res, err := c.Cliente().Do(req)
	if err != nil {
		return "", errors.New("google: no se pudo pedir el token")
	}
	defer res.Body.Close()
	var r struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.NewDecoder(io.LimitReader(res.Body, 64<<10)).Decode(&r); err != nil || res.StatusCode != http.StatusOK || r.AccessToken == "" {
		return "", fmt.Errorf("google: token: HTTP %d", res.StatusCode)
	}
	if c.tokens == nil {
		c.tokens = map[string]token{}
	}
	c.tokens[scope] = token{r.AccessToken, ahora.Add(50 * time.Minute)}
	return r.AccessToken, nil
}
