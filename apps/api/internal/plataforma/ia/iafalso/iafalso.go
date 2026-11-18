// Package iafalso: un Llamador de Anthropic para las pruebas, sin red. Devuelve respuestas fijas
// (o lo que diga Responder) y guarda lo que se le pidió.
package iafalso

import (
	"context"
	"encoding/json"
	"sync"

	"github.com/anthropics/anthropic-sdk-go"
	"github.com/anthropics/anthropic-sdk-go/option"
)

// Falso responde con Responder (o con JSON fijo) y anota los pedidos.
type Falso struct {
	// Responder decide la respuesta según el texto del usuario. nil: Fijo.
	Responder func(texto string) (any, error)
	// Fijo: lo que responde si no hay Responder.
	Fijo any
	// Uso que informa cada respuesta (por defecto, 600 de entrada y 40 de salida).
	Uso anthropic.Usage
	// StopReason (por defecto end_turn).
	Stop anthropic.StopReason

	mu      sync.Mutex
	Pedidos []anthropic.MessageNewParams
}

// New implementa ia.Llamador.
func (f *Falso) New(ctx context.Context, p anthropic.MessageNewParams, _ ...option.RequestOption) (*anthropic.Message, error) {
	f.mu.Lock()
	f.Pedidos = append(f.Pedidos, p)
	f.mu.Unlock()
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	texto := ""
	if len(p.Messages) > 0 && len(p.Messages[0].Content) > 0 && p.Messages[0].Content[0].OfText != nil {
		texto = p.Messages[0].Content[0].OfText.Text
	}
	var v any = f.Fijo
	if f.Responder != nil {
		var err error
		if v, err = f.Responder(texto); err != nil {
			return nil, err
		}
	}
	b, _ := json.Marshal(v)
	uso := f.Uso
	if uso.InputTokens == 0 && uso.OutputTokens == 0 {
		uso = anthropic.Usage{InputTokens: 600, OutputTokens: 40}
	}
	stop := f.Stop
	if stop == "" {
		stop = anthropic.StopReasonEndTurn
	}
	// El Message del SDK se arma desde JSON (los bloques son una unión que sólo se llena al decodificar).
	crudo, _ := json.Marshal(map[string]any{
		"id": "msg_falso", "type": "message", "role": "assistant", "model": p.Model,
		"content":     []map[string]any{{"type": "text", "text": string(b)}},
		"stop_reason": stop,
		"usage": map[string]any{"input_tokens": uso.InputTokens, "output_tokens": uso.OutputTokens,
			"cache_read_input_tokens": uso.CacheReadInputTokens, "cache_creation_input_tokens": uso.CacheCreationInputTokens},
	})
	var m anthropic.Message
	if err := json.Unmarshal(crudo, &m); err != nil {
		return nil, err
	}
	return &m, nil
}

// Cantidad de pedidos recibidos.
func (f *Falso) Cantidad() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return len(f.Pedidos)
}
