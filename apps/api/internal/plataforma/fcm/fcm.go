// Package fcm manda push a Android por Firebase Cloud Messaging (API HTTP v1). Se autentica con la
// cuenta de servicio del proyecto de Firebase: un JWT RS256 que se cambia por un token de OAuth
// (vale una hora; acá se renueva a los 50 minutos). Sólo biblioteca estándar.
//
// Sin archivo de cuenta de servicio, Desde devuelve nil y Enviar sobre un cliente nil no hace nada.
package fcm

import (
	"bytes"
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

// ErrTokenInvalido: FCM dice que el token del dispositivo ya no sirve (UNREGISTERED). Hay que borrarlo.
var ErrTokenInvalido = errors.New("fcm: token de dispositivo inválido")

// Cliente de FCM. Seguro entre goroutines.
type Cliente struct {
	Proyecto, Email string
	Clave           *rsa.PrivateKey
	// URLs (tests). Vacías: las de Google.
	URLToken, URLEnvio string
	HTTP               *http.Client

	mu    sync.Mutex
	token string
	vence time.Time
}

// Desde arma el cliente con el JSON de la cuenta de servicio. Ruta vacía: apagado (nil, nil).
func Desde(ruta string) (*Cliente, error) {
	if ruta == "" {
		return nil, nil
	}
	b, err := os.ReadFile(ruta)
	if err != nil {
		return nil, fmt.Errorf("fcm: no se pudo leer la cuenta de servicio: %w", err)
	}
	var cs struct {
		ProjectID   string `json:"project_id"`
		ClientEmail string `json:"client_email"`
		PrivateKey  string `json:"private_key"`
	}
	if err := json.Unmarshal(b, &cs); err != nil {
		return nil, fmt.Errorf("fcm: cuenta de servicio inválida: %w", err)
	}
	p, _ := pem.Decode([]byte(cs.PrivateKey))
	if p == nil {
		return nil, errors.New("fcm: la clave privada no es PEM")
	}
	k, err := x509.ParsePKCS8PrivateKey(p.Bytes)
	if err != nil {
		return nil, fmt.Errorf("fcm: clave: %w", err)
	}
	rk, ok := k.(*rsa.PrivateKey)
	if !ok || cs.ProjectID == "" || cs.ClientEmail == "" {
		return nil, errors.New("fcm: la cuenta de servicio no trae proyecto, email o clave RSA")
	}
	return &Cliente{Proyecto: cs.ProjectID, Email: cs.ClientEmail, Clave: rk}, nil
}

func (c *Cliente) cli() *http.Client {
	if c.HTTP != nil {
		return c.HTTP
	}
	return &http.Client{Timeout: 15 * time.Second}
}

func (c *Cliente) acceso(ctx context.Context) (string, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.token != "" && time.Now().Before(c.vence) {
		return c.token, nil
	}
	aud := c.URLToken
	if aud == "" {
		aud = "https://oauth2.googleapis.com/token"
	}
	ahora := time.Now()
	enc := base64.RawURLEncoding
	cab, _ := json.Marshal(map[string]string{"alg": "RS256", "typ": "JWT"})
	cue, _ := json.Marshal(map[string]any{
		"iss": c.Email, "scope": "https://www.googleapis.com/auth/firebase.messaging",
		"aud": aud, "iat": ahora.Unix(), "exp": ahora.Add(time.Hour).Unix(),
	})
	firmable := enc.EncodeToString(cab) + "." + enc.EncodeToString(cue)
	h := sha256.Sum256([]byte(firmable))
	firma, err := rsa.SignPKCS1v15(rand.Reader, c.Clave, crypto.SHA256, h[:])
	if err != nil {
		return "", err
	}
	form := url.Values{"grant_type": {"urn:ietf:params:oauth:grant-type:jwt-bearer"}, "assertion": {firmable + "." + enc.EncodeToString(firma)}}
	req, _ := http.NewRequestWithContext(ctx, http.MethodPost, aud, strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	res, err := c.cli().Do(req)
	if err != nil {
		return "", errors.New("fcm: no se pudo pedir el token")
	}
	defer res.Body.Close()
	var r struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
	}
	if err := json.NewDecoder(io.LimitReader(res.Body, 64<<10)).Decode(&r); err != nil || res.StatusCode != http.StatusOK || r.AccessToken == "" {
		return "", fmt.Errorf("fcm: token: HTTP %d", res.StatusCode)
	}
	c.token, c.vence = r.AccessToken, ahora.Add(50*time.Minute)
	return c.token, nil
}

// Envio es un aviso para un dispositivo Android.
type Envio struct {
	Token, Titulo, Cuerpo string
	// Datos para que la app abra la pantalla correcta (p. ej. {"plan": "<id>"}).
	Datos map[string]string
	// Colapso: avisos con la misma clave se pisan (el chat de un plan).
	Colapso string
}

// Enviar manda el aviso. Sobre un cliente nil no hace nada.
func (c *Cliente) Enviar(ctx context.Context, e Envio) error {
	if c == nil {
		return nil
	}
	tok, err := c.acceso(ctx)
	if err != nil {
		return err
	}
	msg := map[string]any{
		"token":        e.Token,
		"notification": map[string]string{"title": e.Titulo, "body": e.Cuerpo},
		"data":         e.Datos,
		"android":      map[string]any{"priority": "HIGH", "collapse_key": e.Colapso},
	}
	b, _ := json.Marshal(map[string]any{"message": msg})
	dest := c.URLEnvio
	if dest == "" {
		dest = "https://fcm.googleapis.com/v1/projects/" + c.Proyecto + "/messages:send"
	}
	req, _ := http.NewRequestWithContext(ctx, http.MethodPost, dest, bytes.NewReader(b))
	req.Header.Set("Authorization", "Bearer "+tok)
	req.Header.Set("Content-Type", "application/json")
	res, err := c.cli().Do(req)
	if err != nil {
		return errors.New("fcm: no se pudo conectar")
	}
	defer res.Body.Close()
	cuerpo, _ := io.ReadAll(io.LimitReader(res.Body, 8<<10))
	if res.StatusCode == http.StatusOK {
		return nil
	}
	if res.StatusCode == http.StatusNotFound || bytes.Contains(cuerpo, []byte("UNREGISTERED")) {
		return ErrTokenInvalido
	}
	return fmt.Errorf("fcm: HTTP %d", res.StatusCode)
}
