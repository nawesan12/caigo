package planes

import (
	"testing"
	"time"
)

func TestFinDeSemana(t *testing.T) {
	art := time.FixedZone("ART", -3*3600)
	casos := map[string]string{
		"2026-11-16T18:30:00-03:00": "2026-11-23", // lunes: toda la semana
		"2026-11-18T10:00:00-03:00": "2026-11-23", // miércoles
		"2026-11-22T23:59:00-03:00": "2026-11-23", // domingo a la noche
		"2026-11-23T00:00:00-03:00": "2026-11-30", // lunes a las 0
	}
	for desde, quiero := range casos {
		t0, _ := time.Parse(time.RFC3339, desde)
		if got := FinDeSemana(t0).In(art).Format(time.DateOnly); got != quiero {
			t.Errorf("%s: %s, quería %s", desde, got, quiero)
		}
	}
}

func TestMejoresDosPorDia(t *testing.T) {
	hoy := time.Date(2026, 11, 18, 20, 0, 0, 0, time.FixedZone("ART", -3*3600))
	var ps []Plan
	for i := range 5 {
		ps = append(ps, Plan{ID: "hoy" + string(rune('a'+i)), EmpiezaEn: hoy.Add(time.Duration(i) * time.Minute)})
	}
	ps = append(ps, Plan{ID: "jueves", EmpiezaEn: hoy.AddDate(0, 0, 1)})
	got := mejores(ps, func(Plan) int { return 1 }, 8, 2)
	if len(got) != 3 || got[0].ID != "hoya" || got[1].ID != "hoyb" || got[2].ID != "jueves" {
		t.Fatalf("mejores: %+v", got)
	}
}
