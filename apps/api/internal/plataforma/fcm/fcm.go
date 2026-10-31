// Package fcm manda push a Android por Firebase Cloud Messaging (API HTTP v1), autenticado con la
// cuenta de servicio del proyecto de Firebase. Sobre un cliente nil, Enviar no hace nada.
package fcm

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/google"
)

// ErrTokenInvalido: FCM dice que el token del dispositivo ya no sirve (UNREGISTERED). Hay que borrarlo.
var ErrTokenInvalido = errors.New("fcm: token de dispositivo inválido")

const scope = "https://www.googleapis.com/auth/firebase.messaging"

// Cliente de FCM.
type Cliente struct {
	Cuenta *google.Cuenta
	// URLEnvio (tests). Vacía: la de Google.
	URLEnvio string
}

// Desde arma el cliente con el JSON de la cuenta de servicio. Ruta vacía: apagado (nil, nil).
func Desde(ruta string) (*Cliente, error) {
	c, err := google.Leer(ruta)
	if err != nil || c == nil {
		return nil, err
	}
	return &Cliente{Cuenta: c}, nil
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
	tok, err := c.Cuenta.Token(ctx, scope)
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
		dest = "https://fcm.googleapis.com/v1/projects/" + c.Cuenta.Proyecto + "/messages:send"
	}
	req, _ := http.NewRequestWithContext(ctx, http.MethodPost, dest, bytes.NewReader(b))
	req.Header.Set("Authorization", "Bearer "+tok)
	req.Header.Set("Content-Type", "application/json")
	res, err := c.Cuenta.Cliente().Do(req)
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
