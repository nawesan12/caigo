// Package mercadopago: el cliente HTTP de Mercado Pago y lo que comparten Caigo Plus (suscripción
// por preapproval, con el token de la plataforma) y los lugares (marketplace: cada lugar conecta
// su cuenta por OAuth con PKCE y cobra entradas y señas con su token; Caigo se queda el cargo por
// servicio como marketplace_fee). Sigue las guías oficiales del plugin de Mercado Pago.
package mercadopago

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// ErrApagado: falta configurar Mercado Pago.
var ErrApagado = errors.New("mercadopago: sin configurar")

// ErrorMP es una respuesta no exitosa de la API. El cuerpo nunca se le muestra a nadie.
type ErrorMP struct {
	Estado int
	Cuerpo string
}

func (e *ErrorMP) Error() string { return fmt.Sprintf("mercadopago: HTTP %d", e.Estado) }

// Config de la aplicación de Mercado Pago de Caigo.
type Config struct {
	AppID, Secreto string
	// Token de la cuenta de Caigo (Plus por preapproval).
	TokenPlataforma string
	// Clave secreta de los webhooks (x-signature).
	SecretoWebhook string
	// Base (tests). Vacía: https://api.mercadopago.com.
	Base string
	HTTP *http.Client
}

// Activo dice si hay aplicación configurada.
func (c *Config) Activo() bool { return c != nil && c.AppID != "" && c.Secreto != "" }

func (c *Config) base() string {
	if c.Base != "" {
		return c.Base
	}
	return "https://api.mercadopago.com"
}

func (c *Config) http() *http.Client {
	if c.HTTP != nil {
		return c.HTTP
	}
	return &http.Client{Timeout: 15 * time.Second}
}

// JSON hace un pedido con el token dado. idem, si no está vacío, va como X-Idempotency-Key.
func (c *Config) JSON(ctx context.Context, metodo, ruta, token, idem string, cuerpo, salida any) error {
	var r io.Reader
	if cuerpo != nil {
		b, err := json.Marshal(cuerpo)
		if err != nil {
			return err
		}
		r = bytes.NewReader(b)
	}
	req, err := http.NewRequestWithContext(ctx, metodo, c.base()+ruta, r)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	if cuerpo != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if idem != "" {
		req.Header.Set("X-Idempotency-Key", idem)
	}
	return c.hacer(req, salida)
}

// Formulario: /oauth/token va con application/x-www-form-urlencoded.
func (c *Config) Formulario(ctx context.Context, ruta string, v url.Values, salida any) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.base()+ruta, strings.NewReader(v.Encode()))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Accept", "application/json")
	return c.hacer(req, salida)
}

func (c *Config) hacer(req *http.Request, salida any) error {
	res, err := c.http().Do(req)
	if err != nil {
		return errors.New("mercadopago: no se pudo conectar")
	}
	defer res.Body.Close()
	b, _ := io.ReadAll(io.LimitReader(res.Body, 1<<20))
	if res.StatusCode >= 300 {
		return &ErrorMP{Estado: res.StatusCode, Cuerpo: string(b)}
	}
	if salida == nil || len(b) == 0 {
		return nil
	}
	return json.Unmarshal(b, salida)
}

// Tokens del OAuth.
type Tokens struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int64  `json:"expires_in"`
	UserID       int64  `json:"user_id"`
}

// Azar para state y verificador de PKCE.
func Azar() string {
	b := make([]byte, 24)
	_, _ = rand.Read(b)
	return base64.RawURLEncoding.EncodeToString(b)
}

// Desafio de PKCE S256.
func Desafio(verificador string) string {
	h := sha256.Sum256([]byte(verificador))
	return base64.RawURLEncoding.EncodeToString(h[:])
}

// URLAutorizacion arma la URL para que el lugar conecte su cuenta.
func (c *Config) URLAutorizacion(vuelta, estado, verificador string) string {
	q := url.Values{
		"client_id": {c.AppID}, "response_type": {"code"}, "platform_id": {"mp"},
		"redirect_uri": {vuelta}, "state": {estado},
		"code_challenge": {Desafio(verificador)}, "code_challenge_method": {"S256"},
	}
	return "https://auth.mercadopago.com/authorization?" + q.Encode()
}

// Canjear cambia el código del OAuth por tokens.
func (c *Config) Canjear(ctx context.Context, codigo, vuelta, verificador string) (Tokens, error) {
	var tk Tokens
	err := c.Formulario(ctx, "/oauth/token", url.Values{
		"grant_type": {"authorization_code"}, "client_id": {c.AppID}, "client_secret": {c.Secreto},
		"code": {codigo}, "redirect_uri": {vuelta}, "code_verifier": {verificador},
	}, &tk)
	if err == nil && (tk.AccessToken == "" || tk.RefreshToken == "" || tk.UserID == 0) {
		err = errors.New("mercadopago: el canje no trajo tokens")
	}
	return tk, err
}

// Renovar pide tokens nuevos con el refresh token.
func (c *Config) Renovar(ctx context.Context, refresh string) (Tokens, error) {
	var tk Tokens
	err := c.Formulario(ctx, "/oauth/token", url.Values{
		"grant_type": {"refresh_token"}, "client_id": {c.AppID}, "client_secret": {c.Secreto}, "refresh_token": {refresh},
	}, &tk)
	return tk, err
}

// Aviso es el cuerpo de una notificación de Webhooks.
type Aviso struct {
	Type   string          `json:"type"`
	Action string          `json:"action"`
	UserID json.RawMessage `json:"user_id"`
	Data   struct {
		ID json.RawMessage `json:"id"`
	} `json:"data"`
}

// ID del recurso del aviso.
func (a Aviso) ID() string { return strings.Trim(strings.TrimSpace(string(a.Data.ID)), `"`) }

// Usuario de Mercado Pago dueño del recurso.
func (a Aviso) Usuario() int64 {
	n, _ := strconv.ParseInt(strings.Trim(strings.TrimSpace(string(a.UserID)), `"`), 10, 64)
	return n
}

var alfanumerico = regexp.MustCompile(`^[A-Za-z0-9]+$`)

// FirmaValida verifica x-signature (HMAC-SHA256 de "id:{data.id};request-id:{x-request-id};ts:{ts};"
// con la clave secreta de los webhooks), en tiempo constante, y rechaza avisos de más de 10 minutos.
func FirmaValida(secreto, firma, requestID, dataID string, ahora time.Time) bool {
	if secreto == "" || firma == "" {
		return false
	}
	var ts, v1 string
	for _, parte := range strings.Split(firma, ",") {
		k, v, _ := strings.Cut(strings.TrimSpace(parte), "=")
		switch strings.TrimSpace(k) {
		case "ts":
			ts = strings.TrimSpace(v)
		case "v1":
			v1 = strings.TrimSpace(v)
		}
	}
	if ts == "" || v1 == "" {
		return false
	}
	if alfanumerico.MatchString(dataID) {
		dataID = strings.ToLower(dataID)
	}
	var b strings.Builder
	if dataID != "" {
		b.WriteString("id:" + dataID + ";")
	}
	if requestID != "" {
		b.WriteString("request-id:" + requestID + ";")
	}
	b.WriteString("ts:" + ts + ";")
	m := hmac.New(sha256.New, []byte(secreto))
	m.Write([]byte(b.String()))
	if !hmac.Equal([]byte(hex.EncodeToString(m.Sum(nil))), []byte(v1)) {
		return false
	}
	if n, err := strconv.ParseInt(ts, 10, 64); err == nil {
		cuando := time.UnixMilli(n)
		if n < 1e12 {
			cuando = time.Unix(n, 0)
		}
		if ahora.Sub(cuando).Abs() > 10*time.Minute {
			return false
		}
	}
	return true
}

// SinEtiqueta saca la parte "+algo" del mail: Mercado Pago rechaza esos mails.
func SinEtiqueta(email string) string {
	local, dominio, ok := strings.Cut(email, "@")
	if !ok {
		return email
	}
	local, _, _ = strings.Cut(local, "+")
	return local + "@" + dominio
}
