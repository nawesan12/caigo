package vetados

import (
	"bytes"
	"encoding/base64"
	"testing"
)

func clave(t *testing.T, b byte) *Clave {
	t.Helper()
	c, err := NuevaClave(base64.StdEncoding.EncodeToString(bytes.Repeat([]byte{b}, 32)))
	if err != nil {
		t.Fatal(err)
	}
	return c
}

func TestNormalizar(t *testing.T) {
	casos := []struct{ tipo, a, b string }{
		{DNI, "30.123.456", "30123456"},
		{DNI, "030123456", "30123456"},
		{Email, " Nahuel@Caigo.com.ar ", "nahuel@caigo.com.ar"},
		{Email, "nahuel+caigo@caigo.com.ar", "nahuel@caigo.com.ar"},
		{Email, "n.a.huel+x@googlemail.com", "nahuel@gmail.com"},
	}
	for _, c := range casos {
		if got := normalizar(c.tipo, c.a); got != c.b {
			t.Errorf("%s %q: %q, quería %q", c.tipo, c.a, got, c.b)
		}
	}
	// Fuera de Gmail los puntos cuentan.
	if NormalizarEmail("n.ahuel@caigo.com.ar") == NormalizarEmail("nahuel@caigo.com.ar") {
		t.Error("los puntos fuera de Gmail son otra casilla")
	}
}

func TestHuella(t *testing.T) {
	a, b := clave(t, 1), clave(t, 2)
	h := a.Huella(Email, "Nahuel@gmail.com")
	if len(h) != 32 || !bytes.Equal(h, a.Huella(Email, "na.huel+otra@gmail.com")) {
		t.Fatalf("la misma casilla da otra huella: %x", h)
	}
	if bytes.Equal(h, b.Huella(Email, "nahuel@gmail.com")) {
		t.Fatal("con otra clave da la misma huella")
	}
	if bytes.Contains(h, []byte("nahuel")) {
		t.Fatal("la huella tiene el mail en claro")
	}
	// El tipo va adentro: el mismo texto como DNI y como mail no coincide.
	if bytes.Equal(a.Huella(DNI, "30123456"), a.Huella(Email, "30123456")) {
		t.Fatal("DNI y mail con la misma huella")
	}
	if a.Huella(DNI, "  ") != nil || a.Huella(Email, "") != nil {
		t.Fatal("un valor vacío no tiene huella")
	}
	var nada *Clave
	if nada.Huella(DNI, "30123456") != nil {
		t.Fatal("sin clave no hay huella")
	}
	if _, err := NuevaClave("corta"); err == nil {
		t.Fatal("aceptó una clave inválida")
	}
}
