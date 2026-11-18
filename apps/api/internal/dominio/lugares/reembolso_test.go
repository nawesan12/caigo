package lugares

import "testing"

// La devolución parcial y la total de una misma entrada no comparten la clave de idempotencia.
func TestClaveReembolso(t *testing.T) {
	parcial, otra := int64(800000), int64(500000)
	total := claveReembolso("e1", nil)
	if total == claveReembolso("e1", &parcial) || claveReembolso("e1", &parcial) == claveReembolso("e1", &otra) {
		t.Fatal("claves repetidas")
	}
	if claveReembolso("e1", &parcial) != claveReembolso("e1", &parcial) || total != claveReembolso("e1", nil) {
		t.Fatal("la misma devolución tiene que dar la misma clave")
	}
}
