package logros

import (
	"testing"
	"time"
)

// dia arma una fecha a las 21 h de Argentina.
func dia(s string) time.Time {
	t, err := time.ParseInLocation("2006-01-02 15:04", s+" 21:00", argentina)
	if err != nil {
		panic(err)
	}
	return t
}

func TestNivel(t *testing.T) {
	casos := []struct {
		planes            int
		nombre, siguiente string
		faltan            int
	}{
		{0, "Nuevo", "Habitué", 5},
		{4, "Nuevo", "Habitué", 1},
		{5, "Habitué", "De fierro", 10},
		{9, "Habitué", "De fierro", 6},
		{15, "De fierro", "Leyenda", 25},
		{40, "Leyenda", "", 0},
		{120, "Leyenda", "", 0},
	}
	for _, c := range casos {
		n, s, f := Nivel(c.planes)
		if n != c.nombre || s != c.siguiente || f != c.faltan {
			t.Errorf("Nivel(%d) = %q %q %d; quería %q %q %d", c.planes, n, s, f, c.nombre, c.siguiente, c.faltan)
		}
	}
}

func TestValido(t *testing.T) {
	casos := []struct {
		nombre string
		a      Asistido
		quiero bool
	}{
		{"solo, nadie más llegó", Asistido{}, false},
		{"con alguien que valida", Asistido{Validan: []string{"b"}}, true},
		{"lo validó el lugar", Asistido{PorLugar: true}, true},
		{"organiza con un solo ajeno", Asistido{Organizo: true, Validan: []string{"b"}}, false},
		{"organiza y vinieron dos", Asistido{Organizo: true, Validan: []string{"b", "c"}}, true},
		{"organiza en un lugar sin ajenos", Asistido{Organizo: true, PorLugar: true}, false},
	}
	for _, c := range casos {
		if got := Valido(c.a); got != c.quiero {
			t.Errorf("%s: Valido = %v", c.nombre, got)
		}
	}
}

// Las mismas cuentas haciéndose check-in entre sí: un plan por semana entre el mismo grupo.
func TestContarMismoGrupoUnaVezPorSemana(t *testing.T) {
	as := []Asistido{
		{PlanID: "1", Empieza: dia("2026-11-09"), Validan: []string{"b", "c"}}, // lunes
		{PlanID: "2", Empieza: dia("2026-11-10"), Validan: []string{"c", "b"}}, // mismo grupo, misma semana
		{PlanID: "3", Empieza: dia("2026-11-11"), Validan: []string{"b"}},      // parte del mismo grupo
		{PlanID: "4", Empieza: dia("2026-11-12"), Validan: []string{"b", "d"}}, // alguien nuevo: cuenta
		{PlanID: "5", Empieza: dia("2026-11-16"), Validan: []string{"b", "c"}}, // otra semana: cuenta
		{PlanID: "6", Empieza: dia("2026-11-17")},                              // nadie valida
		{PlanID: "7", Empieza: dia("2026-11-17"), PorLugar: true},              // el lugar sí
		{PlanID: "8", Empieza: dia("2026-11-18"), PorLugar: true},              // otro día en el lugar
	}
	got := Contar(as)
	var ids string
	for _, a := range got {
		ids += a.PlanID
	}
	if ids != "14578" {
		t.Fatalf("cuentan %q; quería 14578", ids)
	}
}

func TestRacha(t *testing.T) {
	ahora := dia("2026-11-18") // miércoles
	casos := []struct {
		nombre  string
		fechas  []string
		semanas int
		comodin bool
	}{
		{"sin planes", nil, 0, true},
		{"esta semana", []string{"2026-11-17"}, 1, true},
		{"la semana en curso todavía no corta", []string{"2026-11-10", "2026-11-03"}, 2, true},
		{"tres seguidas", []string{"2026-11-16", "2026-11-10", "2026-11-04"}, 3, true},
		{"dos en la misma semana suman una", []string{"2026-11-16", "2026-11-17"}, 1, true},
		// Falta la del 9/11: la salva el comodín de noviembre (y ya no queda).
		{"un hueco con comodín", []string{"2026-11-17", "2026-11-03", "2026-10-27"}, 3, false},
		// Dos huecos en noviembre: el segundo corta.
		{"dos huecos el mismo mes", []string{"2026-11-17", "2026-10-27"}, 1, true},
		// Un hueco en noviembre y otro en octubre: un comodín por mes.
		{"un hueco por mes", []string{"2026-11-17", "2026-11-03", "2026-10-20"}, 3, false},
		// Dos huecos en octubre: el segundo corta.
		{"dos huecos en octubre", []string{"2026-11-17", "2026-11-03", "2026-10-20", "2026-10-06"}, 3, false},
		{"hace mucho", []string{"2026-09-01"}, 0, true},
		{"lo que viene no cuenta", []string{"2026-11-25"}, 0, true},
	}
	for _, c := range casos {
		var fs []time.Time
		for _, f := range c.fechas {
			fs = append(fs, dia(f))
		}
		s, com := Racha(fs, ahora)
		if s != c.semanas || com != c.comodin {
			t.Errorf("%s: Racha = %d, comodín %v; quería %d, %v", c.nombre, s, com, c.semanas, c.comodin)
		}
	}
}

func TestInsignias(t *testing.T) {
	var cuentan []Asistido
	for range 5 {
		cuentan = append(cuentan, Asistido{Categoria: "juego"})
	}
	for range 3 {
		cuentan = append(cuentan, Asistido{Categoria: "previa"})
	}
	for range 2 {
		cuentan = append(cuentan, Asistido{Categoria: "salida"})
	}
	cuentan = append(cuentan, Asistido{Categoria: "deporte"})
	got := Insignias(cuentan, []LocalDe{{"l1", "La Fonda"}}, true)
	var nombres []string
	for _, i := range got {
		nombres = append(nombres, i.Nombre)
	}
	quiero := []string{"Anfitrión", "De la mesa", "De noche", "Local de La Fonda"}
	if len(nombres) != len(quiero) {
		t.Fatalf("insignias %v; quería %v", nombres, quiero)
	}
	for i := range quiero {
		if nombres[i] != quiero[i] {
			t.Fatalf("insignias %v; quería %v", nombres, quiero)
		}
	}
	if got[1].Detalle != "5 planes de juego" {
		t.Errorf("detalle %q", got[1].Detalle)
	}
	if len(Insignias(nil, nil, false)) != 0 {
		t.Error("sin planes no hay insignias")
	}
}

func TestAnfitrion(t *testing.T) {
	hecho := Organizado{ConCheckin: true, Ajenos: 2}
	flojo := Organizado{ConCheckin: true, Ajenos: 1}
	sinCheckin := Organizado{}
	emp := dia("2026-11-10")
	tarde, temprano := emp.Add(-2*time.Hour), emp.Add(-48*time.Hour)
	cancTarde := Organizado{Empieza: emp, CanceladoEn: &tarde}
	cancTemprano := Organizado{Empieza: emp, CanceladoEn: &temprano}
	casos := []struct {
		nombre string
		os     []Organizado
		quiero bool
	}{
		{"nada", nil, false},
		{"dos hechos", []Organizado{hecho, hecho}, false},
		{"tres hechos", []Organizado{hecho, hecho, hecho}, true},
		{"sin check-in no dice nada", []Organizado{hecho, hecho, hecho, sinCheckin, sinCheckin}, true},
		{"uno flojo baja del 90 %", []Organizado{hecho, hecho, hecho, flojo}, false},
		{"nueve de diez", []Organizado{hecho, hecho, hecho, hecho, hecho, hecho, hecho, hecho, hecho, flojo}, true},
		{"cancelar tarde resta", []Organizado{hecho, hecho, hecho, cancTarde}, false},
		{"cancelar con tiempo no", []Organizado{hecho, hecho, hecho, cancTemprano}, true},
	}
	for _, c := range casos {
		if got := Anfitrion(c.os); got != c.quiero {
			t.Errorf("%s: Anfitrion = %v", c.nombre, got)
		}
	}
}

func TestCodigoValido(t *testing.T) {
	for c, ok := range map[string]bool{"abcd2345": true, "abcd234": false, "abcd2341": false, "ABCD2345": false, "abcdefgh": true} {
		if codigoValido(c) != ok {
			t.Errorf("codigoValido(%q) != %v", c, ok)
		}
	}
	for range 50 {
		if c := nuevoCodigo(); !codigoValido(c) {
			t.Fatalf("nuevoCodigo dio %q", c)
		}
	}
}
