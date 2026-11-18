// Package logros premia ir de verdad, no anotarse: nivel, pocas insignias, racha semanal con
// comodín y «Anfitrión». Todo sale de los check-ins validados; acá está el cálculo puro (sin base),
// con las reglas contra el abuso, y en base.go lo que lo lee de Postgres.
//
// Sobrio a propósito (pedido de Nahuel): cuatro niveles, pocas insignias, nada de puntos a la vista,
// sin rankings públicos y la racha es semanal, nunca diaria.
package logros

import (
	"slices"
	"sort"
	"strconv"
	"strings"
	"time"
)

var argentina = func() *time.Location {
	l, err := time.LoadLocation("America/Argentina/Buenos_Aires")
	if err != nil {
		return time.FixedZone("ART", -3*3600)
	}
	return l
}()

// Reglas.
const (
	// Una cuenta valida el check-in de otra si tenía al menos esta antigüedad el día del plan (y el DNI).
	AntiguedadValidar = 7 * 24 * time.Hour
	// Quien organiza suma sólo si el plan se hizo: al menos tantos check-ins ajenos que validan.
	AjenosParaOrganizar = 2
	// Insignia de categoría: tantos planes que cuentan de esa categoría.
	PlanesInsignia = 5
	// «Anfitrión»: planes hechos y porcentaje mínimo de planes hechos.
	AnfitrionPlanes = 3
	AnfitrionPct    = 90
	// Cancelar con menos de esto antes cuenta como plan no hecho.
	CancelacionTarde = 6 * time.Hour
	// «Local de…»: días distintos con check-in en los últimos 30 días.
	LocalDias = 3
	// Faltazos en 30 días a partir de los cuales quien organiza lo ve.
	FaltazosAviso = 2
	// Insignias que ven los demás.
	InsigniasAjenas = 3
)

// Nivel por planes que cuentan. Nunca baja (la asistencia sí).
type nivel struct {
	nombre string
	desde  int
}

var niveles = []nivel{{"Nuevo", 0}, {"Habitué", 5}, {"De fierro", 15}, {"Leyenda", 40}}

// Habitue es el nivel desde el que se dan los beneficios por nivel de los lugares.
const Habitue = 5

// Asistido es un plan con check-in de la persona.
type Asistido struct {
	PlanID    string
	Empieza   time.Time
	Categoria string
	LugarID   string
	// El check-in lo validó el lugar (puerta, entrada o placa Caigo Tap).
	PorLugar bool
	// La persona organizaba el plan.
	Organizo bool
	// Los otros que llegaron y validan: cuentas con DNI y con AntiguedadValidar el día del plan.
	Validan []string
}

// Valido dice si el plan cuenta: hubo alguien más (que valida) o lo validó el lugar. Para quien
// organiza, el plan tiene que haberse hecho: AjenosParaOrganizar check-ins ajenos que validan.
func Valido(a Asistido) bool {
	if a.Organizo {
		return len(a.Validan) >= AjenosParaOrganizar
	}
	return a.PorLugar || len(a.Validan) >= 1
}

// semana devuelve el lunes (en Argentina) de la semana de t.
func semana(t time.Time) time.Time {
	a := t.In(argentina)
	d := time.Date(a.Year(), a.Month(), a.Day(), 0, 0, 0, 0, argentina)
	return d.AddDate(0, 0, -((int(d.Weekday()) + 6) % 7))
}

// Contar devuelve los planes que cuentan, en orden. Además de Valido: entre el mismo grupo, un plan
// por semana (si todos los que validan este plan ya validaron otro que contó esa semana, no suma).
func Contar(as []Asistido) []Asistido {
	orden := slices.Clone(as)
	sort.SliceStable(orden, func(i, j int) bool { return orden[i].Empieza.Before(orden[j].Empieza) })
	type grupo struct {
		sem   time.Time
		gente map[string]bool
	}
	var vistos []grupo
	var out []Asistido
	for _, a := range orden {
		if !Valido(a) {
			continue
		}
		s := semana(a.Empieza)
		repetido := false
		if len(a.Validan) > 0 {
			for _, g := range vistos {
				if !g.sem.Equal(s) {
					continue
				}
				todos := true
				for _, v := range a.Validan {
					if !g.gente[v] {
						todos = false
						break
					}
				}
				if todos {
					repetido = true
					break
				}
			}
		}
		if repetido {
			continue
		}
		g := grupo{sem: s, gente: map[string]bool{}}
		for _, v := range a.Validan {
			g.gente[v] = true
		}
		vistos = append(vistos, g)
		out = append(out, a)
	}
	return out
}

// Nivel según los planes que cuentan: el nombre, el siguiente y cuántos faltan ("" y 0 en el último).
func Nivel(planes int) (nombre, siguiente string, faltan int) {
	for i := len(niveles) - 1; i >= 0; i-- {
		if planes >= niveles[i].desde {
			if i+1 < len(niveles) {
				return niveles[i].nombre, niveles[i+1].nombre, niveles[i+1].desde - planes
			}
			return niveles[i].nombre, "", 0
		}
	}
	return niveles[0].nombre, niveles[1].nombre, niveles[1].desde - planes
}

// Racha: semanas seguidas (de lunes a domingo, en Argentina) con al menos un plan que cuenta. La
// semana en curso suma si ya tiene uno, y si no, todavía no corta. Una semana sin planes se salva
// con el comodín del mes (uno por mes calendario, automático). comodin: si el de este mes está libre.
func Racha(fechas []time.Time, ahora time.Time) (semanas int, comodin bool) {
	con := map[time.Time]bool{}
	for _, f := range fechas {
		if !f.After(ahora) {
			con[semana(f)] = true
		}
	}
	mes := func(t time.Time) string { return t.Format("2006-01") }
	usados := map[string]bool{}
	var pendientes []string
	actual := semana(ahora)
	if con[actual] {
		semanas++
	}
	for w, i := actual.AddDate(0, 0, -7), 0; i < 160; w, i = w.AddDate(0, 0, -7), i+1 {
		if con[w] {
			semanas++
			// Los comodines que salvaron el hueco quedan usados recién si la racha sigue más atrás.
			for _, m := range pendientes {
				usados[m] = true
			}
			pendientes = nil
			continue
		}
		m := mes(w)
		if usados[m] || slices.Contains(pendientes, m) {
			break
		}
		pendientes = append(pendientes, m)
	}
	if semanas == 0 {
		usados = map[string]bool{}
	}
	return semanas, !usados[mes(actual)]
}

// Insignia que se muestra en el perfil.
type Insignia struct {
	ID      string `json:"id"`
	Nombre  string `json:"nombre"`
	Detalle string `json:"detalle"`
}

// Pocas y con sentido: una por familia de categorías, a los PlanesInsignia planes.
var familias = []struct {
	id, nombre, detalle string
	categorias          []string
}{
	{"cancha", "En cancha", "planes de deporte", []string{"deporte"}},
	{"mesa", "De la mesa", "planes de juego", []string{"juego"}},
	{"noche", "De noche", "previas y salidas", []string{"previa", "salida", "fiesta", "musica"}},
	{"cultura", "Cultura", "planes de cultura", []string{"cultura", "taller"}},
	{"buen-diente", "Buen diente", "planes de comida", []string{"gastronomia"}},
}

// LocalDe es un lugar donde la persona es «Local» (la que más fue en 30 días).
type LocalDe struct {
	LugarID, Nombre string
}

// Insignias: las de categoría (de los planes que cuentan), «Local de…» y «Anfitrión».
func Insignias(cuentan []Asistido, locales []LocalDe, anfitrion bool) []Insignia {
	out := []Insignia{}
	if anfitrion {
		out = append(out, Insignia{ID: "anfitrion", Nombre: "Anfitrión", Detalle: "Sus planes se hacen"})
	}
	for _, l := range locales {
		out = append(out, Insignia{ID: "local-" + l.LugarID, Nombre: "Local de " + l.Nombre, Detalle: "El que más fue este mes"})
	}
	por := map[string]int{}
	for _, a := range cuentan {
		por[a.Categoria]++
	}
	for _, f := range familias {
		n := 0
		for _, c := range f.categorias {
			n += por[c]
		}
		if n >= PlanesInsignia {
			out = append(out, Insignia{ID: f.id, Nombre: f.nombre, Detalle: strconv.Itoa(n) + " " + f.detalle})
		}
	}
	return out
}

// Organizado es un plan que la persona organizó y ya pasó (o se canceló).
type Organizado struct {
	Empieza time.Time
	// Cancelado y cuándo.
	CanceladoEn *time.Time
	// Hubo algún check-in (el plan usó el check-in).
	ConCheckin bool
	// Check-ins ajenos que validan.
	Ajenos int
}

// Anfitrion: AnfitrionPlanes planes hechos o más y AnfitrionPct % o más de lo que organizó se hizo.
// Se miran los planes que usaron el check-in (los que no lo usaron no dicen nada, como la asistencia)
// y los cancelados con menos de CancelacionTarde, que cuentan como no hechos.
func Anfitrion(os []Organizado) bool {
	hechos, mirados := 0, 0
	for _, o := range os {
		if o.CanceladoEn != nil {
			if o.Empieza.Sub(*o.CanceladoEn) < CancelacionTarde {
				mirados++
			}
			continue
		}
		if !o.ConCheckin {
			continue
		}
		mirados++
		if o.Ajenos >= AjenosParaOrganizar {
			hechos++
		}
	}
	return hechos >= AnfitrionPlanes && hechos*100 >= AnfitrionPct*mirados
}

// codigoValido: los códigos de «Traé a alguien» (8 letras y números sin los que se confunden).
func codigoValido(c string) bool {
	if len(c) != 8 {
		return false
	}
	return strings.Trim(c, "abcdefghjkmnpqrstuvwxyz23456789") == ""
}
