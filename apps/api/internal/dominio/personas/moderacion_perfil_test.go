package personas

import "testing"

func TestMotivoPerfil(t *testing.T) {
	for _, c := range []struct {
		texto string
		pasa  bool
	}{
		{"Me encanta el pádel y salir a correr por la costa.", true},
		{"Vendo empanadas los findes", true},
		{"Escribime al 223 555 1234", false},
		{"Mi insta: www.instagram.com/lu", false},
	} {
		if got := MotivoPerfil(c.texto) == ""; got != c.pasa {
			t.Errorf("%q: pasa=%v, se esperaba %v (%q)", c.texto, got, c.pasa, MotivoPerfil(c.texto))
		}
	}
}
