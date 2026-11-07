package mercadopago

import (
	"testing"
	"time"
)

func TestImportesYDuracion(t *testing.T) {
	if Importe(860000) != "8600.00" || Importe(123456) != "1234.56" || Importe(5) != "0.05" {
		t.Fatal(Importe(860000), Importe(123456), Importe(5))
	}
	if LeerCentavos("1234.56") != 123456 || LeerCentavos("") != 0 || LeerCentavos("8600") != 860000 {
		t.Fatal("LeerCentavos")
	}
	if Duracion(30*time.Minute) != "PT30M" || Duracion(10*time.Second) != "PT1M" {
		t.Fatal(Duracion(30*time.Minute), Duracion(10*time.Second))
	}
}

func TestRelevante(t *testing.T) {
	var o OrdenLeida
	if _, ok := o.Relevante(); ok {
		t.Fatal("sin intentos")
	}
	o.Transactions.Payments = []TransaccionPago{{ID: "a", AttemptNumber: 1, Status: "processed"}, {ID: "b", AttemptNumber: 2, Status: "failed"}}
	if r, _ := o.Relevante(); r.ID != "a" {
		t.Fatalf("la acreditada: %q", r.ID)
	}
	o.Transactions.Payments = []TransaccionPago{{ID: "a", AttemptNumber: 1, Status: "failed"}, {ID: "b", AttemptNumber: 2, Status: "action_required"}}
	if r, _ := o.Relevante(); r.ID != "b" {
		t.Fatalf("el último intento: %q", r.ID)
	}
}
