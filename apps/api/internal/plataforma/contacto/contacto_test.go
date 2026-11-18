package contacto

import "testing"

func TestValido(t *testing.T) {
	casos := map[string]bool{
		"mama@gmail.com":         true,
		"Mamá <mama@gmail.com>":  false,
		"mama@":                  false,
		"12":                     false,
		"0223 15 592-6772":       true,
		"+54 9 223 592-6772":     true,
		"1234567":                false,
		"llamame al 2235926772":  false,
		"a@b.co":                 true,
	}
	for c, quiere := range casos {
		if Valido(c) != quiere {
			t.Errorf("Valido(%q) = %v, quería %v", c, !quiere, quiere)
		}
	}
	if !EsMail("x@y.com") || EsMail("+54 223 5926772") {
		t.Error("EsMail")
	}
	if Huella("A@b.com ") != Huella("a@b.com") || len(Huella("a@b.com")) != 64 {
		t.Error("Huella")
	}
}
