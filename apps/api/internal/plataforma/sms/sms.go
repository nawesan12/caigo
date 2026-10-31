// Package sms manda los códigos de verificación del teléfono. Por ahora no hay proveedor (Nahuel
// decidió no gastar en SMS todavía): el Simulado registra el código en el log y la API lo
// devuelve en la respuesta para poder probar. Cuando haya proveedor, se implementa Enviador.
package sms

import (
	"context"
	"log/slog"
)

// Enviador manda un SMS.
type Enviador interface {
	Enviar(ctx context.Context, telefono, texto string) error
	// Simulado dice si el código se puede devolver al cliente (no hay SMS de verdad).
	Simulado() bool
}

// Simulado no manda nada: deja el SMS en el log.
type Simulado struct{ Log *slog.Logger }

func (s Simulado) Enviar(_ context.Context, telefono, texto string) error {
	l := s.Log
	if l == nil {
		l = slog.Default()
	}
	// Sin el número completo en el log: alcanza con los últimos dígitos para encontrarlo.
	l.Info("sms simulado", "telefono", "…"+telefono[max(len(telefono)-4, 0):], "texto", texto)
	return nil
}

func (Simulado) Simulado() bool { return true }
