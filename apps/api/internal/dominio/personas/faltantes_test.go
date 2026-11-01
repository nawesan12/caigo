package personas

import (
	"slices"
	"testing"
)

func TestFaltantes(t *testing.T) {
	if f := Faltantes(Perfil{}); !slices.Equal(f, []string{"edad", "ciudad", "zona", "bio", "fotos", "intereses"}) {
		t.Fatalf("vacío: %v", f)
	}
	e := int16(25)
	completo := Perfil{Edad: &e, Ciudad: "Mar del Plata", Zona: "Güemes", Bio: "Me copan los planes de playa y el café.",
		Fotos: []string{"a", "b"}, Intereses: []string{"x", "y", "z"}}
	if f := Faltantes(completo); len(f) != 0 {
		t.Fatalf("completo: %v", f)
	}
	completo.Bio = "corta"
	if f := Faltantes(completo); !slices.Equal(f, []string{"bio"}) {
		t.Fatalf("bio corta: %v", f)
	}
}
