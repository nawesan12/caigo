package moderacion

import "testing"

func TestFiltro(t *testing.T) {
	casos := []struct {
		texto, decision, categoria string
	}{
		// Lo de todos los días sale tal cual (el «boludo» y el «gil» rioplatenses incluidos).
		{"Fulbito 5 en Punta Mogotes, faltan 2!", OK, ""},
		{"che boludo, llegás a las 9?", OK, ""},
		{"Qué hacés gil, nos vemos en la puerta", OK, ""},
		{"La entrada sale $15.000.000 jaja no, $5.000 cada uno", OK, ""},
		{"Nos juntamos el 2026-11-20 a las 21", OK, ""},
		{"Es acá: https://maps.app.goo.gl/abc123", OK, ""},
		{"Llevo a mi hija, Sofi", OK, ""},
		{"", OK, ""},
		// Bloqueos: menores, amenazas, odio.
		{"hola, tengo 15 años y me quiero sumar", Bloquear, "menores"},
		{"Soy menor pero aparento más", Bloquear, "menores"},
		{"te voy a matar cuando te vea", Bloquear, "violencia"},
		{"TE VOY A CAGAR A PALOS", Bloquear, "violencia"},
		{"negros de mierda no vengan", Bloquear, "odio"},
		{"bolitaaa de mierdaaa", Bloquear, "odio"},
		// Dudosos: van a la IA.
		{"pasame tu wsp", Dudoso, "contacto"},
		{"escribime al 223 555-1234", Dudoso, "contacto"},
		{"mi mail es juan.perez@gmail.com", Dudoso, "contacto"},
		{"seguime en @juanperez", Dudoso, "contacto"},
		{"pasá la seña al CBU: 0000003100", Dudoso, "estafa"},
		{"invertí en usdt y duplicás", Dudoso, "estafa"},
		{"entrá a www.ganaplata.xyz", Dudoso, "spam"},
		{"sos un pelotudo", Dudoso, "insultos"},
		{"sos un p3lotud0", Dudoso, "insultos"},
		{"alguien tiene faso?", Dudoso, "drogas"},
		{"vamos con los nenes de 8 años", Dudoso, "menores"},
		{"me mandás nudes?", Dudoso, "sexual"},
		{"te mato de risa vos jaja", Dudoso, "violencia"},
		{"compren compren compren compren compren compren compren compren compren compren compren compren", Dudoso, "spam"},
		{"aaaaaaaaaaaaaaaaaaaa", Dudoso, "spam"},
	}
	for _, c := range casos {
		v := Filtrar(c.texto)
		if v.Decision != c.decision || (c.categoria != "" && v.Categoria != c.categoria) {
			t.Errorf("Filtrar(%q) = %s/%s, quería %s/%s", c.texto, v.Decision, v.Categoria, c.decision, c.categoria)
		}
		if v.Decision != OK && v.Motivo == "" {
			t.Errorf("Filtrar(%q): sin motivo", c.texto)
		}
	}
}

func TestNormalizar(t *testing.T) {
	for in, want := range map[string]string{
		"Árbol  ÑANDÚ":          "arbol nandu",
		"putaaaaa":              "puta",
		"llamame":               "llamame",
		"p1ja":                  "pija",
		"tengo 14 años!!!":      "tengo 14 anos",
		"llamame al 2235551100": "llamame al 2235551100",
	} {
		if got := Normalizar(in); got != want {
			t.Errorf("Normalizar(%q) = %q, quería %q", in, got, want)
		}
	}
}
