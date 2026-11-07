package confianza

import (
	"strings"
	"testing"
)

func TestEnmascarar(t *testing.T) {
	casos := map[string]string{
		"nicolas@gmail.com": "n•••@gmail.com",
		"a@caigo.com.ar":    "a•••@caigo.com.ar",
		"ñandu@x.com":       "ñ•••@x.com",
		"sinarroba":         "•••",
	}
	for in, quiero := range casos {
		if got := enmascarar(in); got != quiero {
			t.Errorf("enmascarar(%q) = %q, quería %q", in, got, quiero)
		}
	}
}

func TestMensajeCodigo(t *testing.T) {
	m := mensajeCodigo("lu@caigo.test", "042137")
	if m.Para != "lu@caigo.test" || !strings.Contains(m.Asunto, "042137") {
		t.Fatalf("encabezados: %+v", m)
	}
	for _, parte := range []string{m.HTML, m.Texto} {
		for _, frase := range []string{"042137", "Vence en 10 minutos.", "Si no fuiste vos, ignorá este mail."} {
			if !strings.Contains(parte, frase) {
				t.Errorf("falta %q", frase)
			}
		}
	}
	if !strings.Contains(m.HTML, "#6B4EFF") || !strings.Contains(m.HTML, "Geist") {
		t.Error("el HTML no tiene la marca")
	}
}
