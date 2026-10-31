// Package respuesta tiene lo que comparten los handlers: escribir JSON, leer el cuerpo con tope y
// traducir los errores del dominio a problem+json.
package respuesta

import (
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// JSON escribe v con el estado dado.
func JSON(w http.ResponseWriter, estado int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(estado)
	_ = json.NewEncoder(w).Encode(v)
}

// Leer decodifica el cuerpo (hasta 64 KB) en v. Campos desconocidos son error: un typo del cliente
// no tiene que pasar en silencio.
func Leer(w http.ResponseWriter, r *http.Request, v any) bool {
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10))
	dec.DisallowUnknownFields()
	if err := dec.Decode(v); err != nil && !errors.Is(err, io.EOF) {
		problema.Escribir(w, problema.Invalido("El cuerpo del pedido no es JSON válido: "+err.Error()))
		return false
	}
	return true
}

// Error escribe err: si es un *problema.Problema va tal cual; si no, se registra y sale un 500.
func Error(w http.ResponseWriter, log *slog.Logger, err error) {
	var p *problema.Problema
	if errors.As(err, &p) {
		problema.Escribir(w, p)
		return
	}
	if log == nil {
		log = slog.Default()
	}
	log.Error("error interno", "error", err)
	problema.Escribir(w, problema.Interno())
}
