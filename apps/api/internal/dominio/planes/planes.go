// Package planes es el corazón de Caigo: publicar un plan, encontrarlo (Ahora y Explorar), sumarse
// solo o en bloque y que el organizador acepte. El cupo es "somos N de M": la suma de las
// solicitudes aceptadas, empezando por la del organizador (su grupo).
package planes

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"math"
	"regexp"
	"sort"
	"strings"
	"time"
	"unicode"

	"github.com/jackc/pgx/v5"
	"golang.org/x/text/unicode/norm"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/logros"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/moderacion"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/firma"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Categorías de plan (las del handoff).
var Categorias = map[string]string{
	"deporte": "Deporte", "juego": "Juego", "previa": "Previa", "salida": "Salida",
	"cita_doble": "Cita doble", "cultura": "Cultura", "gastronomia": "Gastronomía", "otro": "Otro",
	// Las de los lugares (panel).
	"taller": "Taller", "musica": "Música en vivo", "fiesta": "Fiesta",
}

// Ventana de "Ahora": los planes de las próximas 3 horas, y los que arrancaron hace poco.
const (
	ahoraAdelante = 3 * time.Hour
	ahoraAtras    = 30 * time.Minute
)

// Servicio de planes.
type Servicio struct {
	App *db.Pool
	// Firma del QR de check-in y del link en vivo.
	Firma firma.Firmador
	// Sitio público (para el link en vivo).
	Sitio string
	// URL pública de la API: la foto de un plan tiene que ser de ahí (…/v1/archivos/<id>).
	APIPublica string
	// Ahora (tests). nil: time.Now.
	Reloj func() time.Time
	// Moderación del título y la descripción. nil: sólo el filtro propio (lo dudoso va al equipo).
	Moderacion *moderacion.Servicio
	// Disco de las fotos del plan (las que ven sólo quienes fueron).
	Disco archivos.Disco
}

func (s *Servicio) ahora() time.Time {
	if s.Reloj != nil {
		return s.Reloj()
	}
	return time.Now()
}

// Persona es la vista corta de alguien en un plan (avatares, organizador, solicitudes).
type Persona struct {
	// Vacío en las rutas públicas (sin sesión): ahí van sólo el primer nombre y la foto.
	ID          string `json:"id,omitempty"`
	Nombre      string `json:"nombre"`
	Foto        string `json:"foto,omitempty"`
	Edad        *int16 `json:"edad,omitempty"`
	Verificada  bool   `json:"verificada"`
	Asistencia  Asist  `json:"asistencia"`
	EnMiGente   bool   `json:"enMiGente,omitempty"`
	Organizador bool   `json:"organizador,omitempty"`
	// Ya hizo check-in en el plan ("Lu y Nico ya llegaron"; sólo en la lista de los que van).
	Llego bool `json:"llego,omitempty"`
	// Promedio de las caritas de sus planes (sólo de quien organiza, con 3 o más).
	Caritas *Caritas `json:"caritas,omitempty"`
	// «Anfitrión»: sus planes se hacen (sólo en quien organiza; ver logros.Anfitrion).
	Anfitrion bool `json:"anfitrion,omitempty"`
	// Faltazos en los últimos 30 días, si llegó a logros.FaltazosAviso (sólo en las solicitudes).
	Faltazos int `json:"faltazos,omitempty"`
}

// Asist es la asistencia real: fue a Fue de De planes con check-in.
type Asist struct {
	Fue int `json:"fue"`
	De  int `json:"de"`
}

// Plan como lo ve una persona.
type Plan struct {
	ID               string    `json:"id"`
	Slug             string    `json:"slug"`
	Titulo           string    `json:"titulo"`
	Categoria        string    `json:"categoria"`
	Descripcion      string    `json:"descripcion"`
	EmpiezaEn        time.Time `json:"empiezaEn"`
	DuracionMin      int16     `json:"duracionMin"`
	Recurrente       bool      `json:"recurrente"`
	Ciudad           string    `json:"ciudad"`
	Zona             string    `json:"zona"`
	LugarNombre      string    `json:"lugarNombre"`
	Lat              float64   `json:"lat"`
	Lng              float64   `json:"lng"`
	DistanciaM       *int      `json:"distanciaM,omitempty"`
	Somos            int       `json:"somos"`
	Total            int16     `json:"total"`
	Faltan           int       `json:"faltan"`
	Ingreso          string    `json:"ingreso"`
	Visibilidad      string    `json:"visibilidad"`
	SoloVerificados  bool      `json:"soloVerificados"`
	OcultarDireccion bool      `json:"ocultarDireccion"`
	EdadMin          *int16    `json:"edadMin,omitempty"`
	EdadMax          *int16    `json:"edadMax,omitempty"`
	Genero           string    `json:"genero"`
	Nivel            string    `json:"nivel"`
	CostoCentavos    *int32    `json:"costoCentavos,omitempty"`
	Foto             string    `json:"foto"`
	Cancelado        bool      `json:"cancelado"`
	// Destacado con Caigo Plus: va primero en Ahora y Explorar.
	Destacado   bool      `json:"destacado"`
	Organizador Persona   `json:"organizador"`
	Van         []Persona `json:"van"`
	// Estado de quien mira: ninguno | pendiente | aceptada | rechazada | cancelada | organizador.
	MiEstado   string `json:"miEstado"`
	MiCantidad int16  `json:"miCantidad,omitempty"`
	// Dirección exacta: sólo si el plan no la oculta, lo organiza o lo aceptaron.
	Direccion *Direccion `json:"direccion,omitempty"`
	// Solicitudes pendientes (sólo para el organizador).
	Pendientes int `json:"pendientes,omitempty"`
	// Moderación, sólo para quien organiza: "" | revision (la mira el equipo) | oculto (nadie más lo ve).
	Moderacion string `json:"moderacion,omitempty"`
	// Plan de un lugar aliado: se entra con entrada o seña en vez de "Sumarme".
	Lugar *LugarCorto `json:"lugar,omitempty"`
	Cobro string      `json:"cobro"` // gratis | entrada | sena
	// Precio por persona, seña y cargo por servicio (por persona), en centavos.
	PrecioCentavos *int32 `json:"precioCentavos,omitempty"`
	SenaPct        *int16 `json:"senaPct,omitempty"`
	CargoCentavos  int64  `json:"cargoCentavos,omitempty"`
	Incluye        string `json:"incluye,omitempty"`
	Cancelacion    string `json:"cancelacion,omitempty"`
	Beneficio      string `json:"beneficio,omitempty"`
	AceptaGrupos   bool   `json:"aceptaGrupos"`
	// La línea de confianza de la tarjeta («Todos verificados · Organiza Martín (9/10) · Van 2 chicas»).
	Confianza Confianza `json:"confianza"`
	// Lo de quien mira, si está en el plan (llegó, confirmó, carita, «¿Todo bien?», lista de espera, fotos).
	Mio *Mio `json:"mio,omitempty"`
}

// Confianza es lo que la tarjeta del plan muestra para que se vea que es seguro, con datos reales y
// sin exponer de más. La asistencia de quien organiza ya viene en organizador.asistencia (app.asistencia,
// 00027: sólo planes con check-in).
type Confianza struct {
	// Todos los que van (quien organiza incluido) tienen el DNI verificado.
	TodosVerificados bool `json:"todosVerificados"`
	// Un plan «Solo mujeres». Caigo no guarda el género de nadie (AAIP 1.2, 00022): lo único que se sabe
	// de quiénes van es que, en un plan así, cada una lo confirmó al sumarse.
	SoloChicas bool `json:"soloChicas"`
	// Cuántas van, sólo en un plan «Solo mujeres» y desde 2 (para no señalar a una sola). 0: no se muestra.
	Chicas int `json:"chicas,omitempty"`
}

// confianza arma la línea de confianza de un plan con los que van ya cargados (completar).
func confianza(p *Plan) {
	c := Confianza{TodosVerificados: len(p.Van) > 0 && p.Organizador.Verificada, SoloChicas: p.Genero == "mujeres"}
	for _, v := range p.Van {
		if !v.Verificada {
			c.TodosVerificados = false
		}
	}
	if c.SoloChicas && p.Somos >= 2 {
		c.Chicas = p.Somos
	}
	p.Confianza = c
}

// SinDNI recorta un plan para quien lo mira sin el DNI verificado (puede mirar, no sumarse): sin la
// dirección exacta, que se ve recién al verificarse (y, si el plan la oculta, al entrar).
func SinDNI(p *Plan) {
	if p.MiEstado != "organizador" && p.MiEstado != "aceptada" {
		p.Direccion = nil
	}
}

// LugarCorto es el lugar aliado de un plan.
type LugarCorto struct {
	ID     string `json:"id"`
	Slug   string `json:"slug"`
	Nombre string `json:"nombre"`
	Zona   string `json:"zona"`
	Foto   string `json:"foto,omitempty"`
	// Beneficio por nivel del lugar (para Habitué o más).
	BeneficioNivel string `json:"beneficioNivel,omitempty"`
}

// Cargo por servicio por persona: 7,5 % del precio, redondeado a $100 (lo paga quien compra).
func Cargo(precioCentavos int64) int64 {
	return int64(math.Round(float64(precioCentavos)*0.075/10000)) * 10000
}

// Direccion exacta de un plan.
type Direccion struct {
	Texto string  `json:"texto"`
	Lat   float64 `json:"lat"`
	Lng   float64 `json:"lng"`
}

// Entrada para crear un plan. Los punteros nil toman el valor por defecto del handoff.
type Entrada struct {
	Titulo           string    `json:"titulo"`
	Categoria        string    `json:"categoria"`
	Descripcion      string    `json:"descripcion"`
	EmpiezaEn        time.Time `json:"empiezaEn"`
	DuracionMin      int16     `json:"duracionMin"`
	Recurrente       bool      `json:"recurrente"`
	Ciudad           string    `json:"ciudad"`
	Zona             string    `json:"zona"`
	LugarNombre      string    `json:"lugarNombre"`
	Direccion        string    `json:"direccion"`
	Lat              float64   `json:"lat"`
	Lng              float64   `json:"lng"`
	Somos            int16     `json:"somos"`
	Faltan           int16     `json:"faltan"`
	Ingreso          string    `json:"ingreso"`
	Visibilidad      string    `json:"visibilidad"`
	SoloVerificados  *bool     `json:"soloVerificados"`
	OcultarDireccion *bool     `json:"ocultarDireccion"`
	EdadMin          *int16    `json:"edadMin"`
	EdadMax          *int16    `json:"edadMax"`
	Genero           string    `json:"genero"`
	Nivel            string    `json:"nivel"`
	CostoCentavos    *int32    `json:"costoCentavos"`
	Foto             string    `json:"foto"`
}

func (e *Entrada) validar(ahora time.Time) error {
	var campos []problema.Campo
	mal := func(ruta, msg string) { campos = append(campos, problema.Campo{Ruta: ruta, Mensaje: msg}) }
	e.Titulo = strings.TrimSpace(e.Titulo)
	e.Ciudad = strings.TrimSpace(e.Ciudad)
	e.Zona = strings.TrimSpace(e.Zona)
	if _, ok := Categorias[e.Categoria]; !ok {
		mal("categoria", "Elegí qué hacemos.")
	}
	if e.Titulo == "" && e.Categoria != "" {
		// Título vacío → "{categoría} en {zona}", como en el handoff.
		e.Titulo = Categorias[e.Categoria]
		if e.Zona != "" {
			e.Titulo += " en " + e.Zona
		}
	}
	if len([]rune(e.Titulo)) > 80 {
		mal("titulo", "El título puede tener hasta 80 letras.")
	}
	if e.EmpiezaEn.IsZero() {
		mal("empiezaEn", "Decí cuándo.")
	} else if e.EmpiezaEn.Before(ahora.Add(-ahoraAtras)) {
		mal("empiezaEn", "Ese horario ya pasó.")
	} else if e.EmpiezaEn.After(ahora.AddDate(0, 6, 0)) {
		mal("empiezaEn", "Se pueden armar planes hasta 6 meses adelante.")
	}
	if e.DuracionMin == 0 {
		e.DuracionMin = 120
	}
	if e.DuracionMin < 15 || e.DuracionMin > 1440 {
		mal("duracionMin", "La duración va de 15 minutos a 24 horas.")
	}
	if e.Ciudad == "" {
		mal("ciudad", "Decí dónde.")
	}
	if e.Lat < -90 || e.Lat > 90 || e.Lng < -180 || e.Lng > 180 || (e.Lat == 0 && e.Lng == 0) {
		mal("lat", "Marcá el lugar en el mapa.")
	}
	if e.Somos < 1 {
		mal("somos", "Al menos sos vos.")
	}
	if e.Faltan < 1 {
		mal("faltan", "Tiene que faltar al menos uno.")
	}
	if int(e.Somos)+int(e.Faltan) > 60 {
		mal("faltan", "Un plan puede ser de hasta 60 personas.")
	}
	e.Ingreso = conDefecto(e.Ingreso, "aprobacion")
	if e.Ingreso != "libre" && e.Ingreso != "aprobacion" && e.Ingreso != "mi_gente" {
		mal("ingreso", "Elegí cómo se suman.")
	}
	e.Visibilidad = conDefecto(e.Visibilidad, "publico")
	if e.Visibilidad != "publico" && e.Visibilidad != "mi_gente" && e.Visibilidad != "link" {
		mal("visibilidad", "Elegí quién lo ve.")
	}
	e.Genero = conDefecto(e.Genero, "cualquiera")
	if e.Genero != "cualquiera" && e.Genero != "mujeres" && e.Genero != "diversidades" {
		mal("genero", "Elegí a quién buscás.")
	}
	if e.EdadMin != nil && (*e.EdadMin < 18 || *e.EdadMin > 120) {
		mal("edadMin", "Caigo es sólo para mayores de 18.")
	}
	if e.EdadMax != nil && (*e.EdadMax < 18 || *e.EdadMax > 120 || (e.EdadMin != nil && *e.EdadMax < *e.EdadMin)) {
		mal("edadMax", "Revisá el rango de edad.")
	}
	if e.CostoCentavos != nil && *e.CostoCentavos < 0 {
		mal("costoCentavos", "El costo no puede ser negativo.")
	}
	if len([]rune(e.Descripcion)) > 600 {
		mal("descripcion", "La descripción puede tener hasta 600 letras.")
	}
	if e.Direccion = strings.TrimSpace(e.Direccion); len([]rune(e.Direccion)) > 160 {
		mal("direccion", "La dirección es muy larga.")
	}
	// Los topes de la tabla (si no, el CHECK de Postgres sale como un 500).
	e.LugarNombre, e.Nivel, e.Foto = strings.TrimSpace(e.LugarNombre), strings.TrimSpace(e.Nivel), strings.TrimSpace(e.Foto)
	for _, c := range []struct {
		ruta, valor string
		max         int
	}{{"ciudad", e.Ciudad, 60}, {"zona", e.Zona, 60}, {"lugarNombre", e.LugarNombre, 80}, {"nivel", e.Nivel, 30}, {"foto", e.Foto, 500}} {
		if len([]rune(c.valor)) > c.max {
			mal(c.ruta, fmt.Sprintf("Puede tener hasta %d letras.", c.max))
		}
	}
	if len(campos) > 0 {
		return problema.Invalido("Revisá el plan.", campos...)
	}
	return nil
}

func conDefecto(v, def string) string {
	if v == "" {
		return def
	}
	return v
}

func verdad(b *bool, def bool) bool {
	if b == nil {
		return def
	}
	return *b
}

// aproximar corre el punto a una grilla de ~300 m: lo que ve cualquiera cuando la dirección se
// oculta, así el pin no delata la casa.
func aproximar(x float64) float64 { return math.Round(x/0.003) * 0.003 }

var noSlug = regexp.MustCompile(`[^a-z0-9]+`)

// slug arma "truco-guemes-k3f9": legible y con un sufijo al azar para que no choque.
func slug(titulo, zona string) string {
	base := strings.ToLower(titulo)
	if zona != "" && !strings.Contains(base, strings.ToLower(zona)) {
		base += " " + strings.ToLower(zona)
	}
	var b strings.Builder
	for _, r := range norm.NFD.String(base) {
		if !unicode.Is(unicode.Mn, r) {
			b.WriteRune(r)
		}
	}
	s := strings.Trim(noSlug.ReplaceAllString(b.String(), "-"), "-")
	if len(s) > 50 {
		s = strings.Trim(s[:50], "-")
	}
	if s == "" {
		s = "plan"
	}
	return s + "-" + sufijo(4)
}

func sufijo(n int) string {
	const letras = "abcdefghjkmnpqrstuvwxyz23456789"
	b := make([]byte, n)
	_, _ = rand.Read(b)
	for i := range b {
		b[i] = letras[int(b[i])%len(letras)]
	}
	return string(b)
}

// Crear publica el plan y deja al organizador (con su grupo) adentro.
func (s *Servicio) Crear(ctx context.Context, usuarioID string, e Entrada) (Plan, error) {
	if err := e.validar(s.ahora()); err != nil {
		return Plan{}, err
	}
	// La moderación va antes y fuera de la transacción: puede esperar a la IA hasta 5 s.
	texto := moderacion.TextoPlan(e.Titulo, e.Descripcion)
	mod := s.Moderacion.RevisarPlan(ctx, texto)
	if mod.Estado == moderacion.Bloquear {
		s.Moderacion.AnotarBloqueo(ctx, usuarioID, moderacion.TipoPlan, "", texto,
			moderacion.Veredicto{Categoria: mod.Categoria, Motivo: mod.Motivo})
		return Plan{}, moderacion.Bloqueado(moderacion.TipoPlan, mod.Motivo)
	}
	ocultar := verdad(e.OcultarDireccion, true)
	lat, lng := e.Lat, e.Lng
	if ocultar {
		lat, lng = aproximar(lat), aproximar(lng)
	}
	var id string
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := s.topeAbiertos(ctx, tx, usuarioID); err != nil {
			return err
		}
		if err := archivos.ExigirPropias(ctx, tx, s.APIPublica, "foto", []string{usuarioID}, nil, e.Foto); err != nil {
			return err
		}
		var serie *string
		if e.Recurrente {
			var sid string
			if err := tx.QueryRow(ctx, `SELECT gen_random_uuid()`).Scan(&sid); err != nil {
				return err
			}
			serie = &sid
		}
		err := tx.QueryRow(ctx, `
			INSERT INTO planes (slug, organizador_id, titulo, categoria, descripcion, empieza_en,
				duracion_min, recurrente, serie_id, ciudad, zona, lugar_nombre, lat, lng, somos, total,
				ingreso, visibilidad, solo_verificados, ocultar_direccion, edad_min, edad_max, genero,
				nivel, costo_centavos, foto, moderacion)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18,
				$19, $20, $21, $22, $23, $24, $25, $26, $27)
			RETURNING id`,
			slug(e.Titulo, e.Zona), usuarioID, e.Titulo, e.Categoria, strings.TrimSpace(e.Descripcion),
			e.EmpiezaEn, e.DuracionMin, e.Recurrente, serie, e.Ciudad, e.Zona, strings.TrimSpace(e.LugarNombre),
			lat, lng, e.Somos, e.Somos+e.Faltan, e.Ingreso, e.Visibilidad, verdad(e.SoloVerificados, true),
			ocultar, e.EdadMin, e.EdadMax, e.Genero, strings.TrimSpace(e.Nivel), e.CostoCentavos,
			strings.TrimSpace(e.Foto), mod.Estado).Scan(&id)
		if err != nil {
			return err
		}
		if mod.Estado == moderacion.EstadoRevisar {
			if err := moderacion.AnotarTx(ctx, tx, entradaPlan(id, usuarioID, texto, mod)); err != nil {
				return err
			}
		}
		if _, err := tx.Exec(ctx, `
			INSERT INTO solicitudes (plan_id, usuario_id, cantidad, estado, respondida_en)
			VALUES ($1, $2, $3, 'aceptada', now())`, id, usuarioID, e.Somos); err != nil {
			return err
		}
		texto := e.Direccion
		if texto == "" {
			texto = conDefecto(strings.TrimSpace(e.LugarNombre), e.Zona+", "+e.Ciudad)
		}
		if _, err := tx.Exec(ctx, `INSERT INTO planes_direccion (plan_id, direccion, lat, lng) VALUES ($1, $2, $3, $4)`,
			id, texto, e.Lat, e.Lng); err != nil {
			return err
		}
		return Publicado(ctx, tx, id)
	})
	if err != nil {
		return Plan{}, err
	}
	return s.Ver(ctx, usuarioID, id, nil)
}

// Planes abiertos a la vez (que todavía no terminaron) por persona: contra el spam de planes.
const (
	AbiertosGratis = 3
	AbiertosPlus   = 5
)

// topeAbiertos frena un plan nuevo si ya tiene AbiertosGratis abiertos (AbiertosPlus con Plus).
func (s *Servicio) topeAbiertos(ctx context.Context, tx pgx.Tx, usuarioID string) error {
	// Un candado por persona: dos «Publicar» a la vez no pasan los dos con el último lugar.
	if err := plus.Bloquear(ctx, tx, usuarioID); err != nil {
		return err
	}
	var abiertos int
	if err := tx.QueryRow(ctx, `SELECT count(*) FROM planes WHERE organizador_id = $1 AND cancelado_en IS NULL
		AND empieza_en + make_interval(mins => duracion_min) > $2`, usuarioID, s.ahora()).Scan(&abiertos); err != nil {
		return err
	}
	if abiertos < AbiertosGratis {
		return nil
	}
	e, err := plus.Leer(ctx, tx, usuarioID, s.ahora())
	if err != nil {
		return err
	}
	if e.Activo && abiertos < AbiertosPlus {
		return nil
	}
	tope := AbiertosGratis
	if e.Activo {
		tope = AbiertosPlus
	}
	return problema.Conflicto("tope_planes", fmt.Sprintf("Ya tenés %d planes abiertos. Cuando pase uno, armás otro.", tope))
}

// columnas del plan con todo lo que hace falta para mostrarlo. $1 es quien mira.
const columnas = `
	p.id, p.slug, p.titulo, p.categoria, p.descripcion, p.empieza_en, p.duracion_min, p.recurrente,
	p.ciudad, p.zona, p.lugar_nombre, p.lat, p.lng, coalesce(c.sumados, 0), p.total, p.ingreso,
	p.visibilidad, p.solo_verificados, p.ocultar_direccion, p.edad_min, p.edad_max, p.genero, p.nivel,
	p.costo_centavos, p.foto, p.cancelado_en IS NOT NULL,
	o.id, o.nombre, coalesce(o.fotos[1], ''), o.edad, o.dni_en IS NOT NULL, rep.fue, rep.de,
	coalesce(m.estado, 'ninguno'), coalesce(m.cantidad, 0),
	CASE WHEN p.organizador_id = $1 THEN
		(SELECT count(*) FROM solicitudes x WHERE x.plan_id = p.id AND x.estado = 'pendiente') ELSE 0 END,
	l.id, coalesce(l.slug, ''), coalesce(l.nombre, ''), coalesce(l.zona, ''), coalesce(l.foto, ''), coalesce(l.beneficio_nivel, ''),
	p.cobro, p.precio_centavos, p.sena_pct, p.incluye, p.cancelacion, p.beneficio, p.acepta_grupos,
	p.destacado_en IS NOT NULL,
	CASE WHEN p.organizador_id = $1 THEN CASE p.moderacion WHEN 'revisar' THEN 'revision' WHEN 'oculto' THEN 'oculto' ELSE '' END
		ELSE '' END`

const desde = `
	FROM planes p
	JOIN usuarios o ON o.id = p.organizador_id
	LEFT JOIN LATERAL (SELECT sum(cantidad)::integer AS sumados FROM solicitudes
		WHERE plan_id = p.id AND estado = 'aceptada') c ON true
	LEFT JOIN LATERAL app.asistencia(o.id) rep ON true
	LEFT JOIN solicitudes m ON m.plan_id = p.id AND m.usuario_id = $1
	LEFT JOIN lugares l ON l.id = p.lugar_id`

// sinBloqueo: ni quien mira ($1) bloqueó al organizador ni al revés.
const sinBloqueo = `
	NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = $1 AND b.bloqueado_id = p.organizador_id)
		OR (b.usuario_id = p.organizador_id AND b.bloqueado_id = $1))`

// noOculto: un plan oculto por la moderación sólo lo ve quien lo organiza ($1).
const noOculto = `(p.moderacion <> 'oculto' OR p.organizador_id = $1)`

// visible: lo que puede encontrar quien mira ($1) en las listas. Los planes "por link" no se listan.
const visible = sinBloqueo + `
	AND (p.organizador_id = $1 OR m.estado IS NOT NULL OR p.visibilidad = 'publico'
		OR (p.visibilidad = 'mi_gente' AND EXISTS (SELECT 1 FROM mi_gente g
			WHERE g.usuario_id = p.organizador_id AND g.otro_id = $1)))`

func escanear(row pgx.Row) (Plan, error) {
	var p Plan
	var cancelado bool
	var lugarID *string
	var lugar LugarCorto
	err := row.Scan(&p.ID, &p.Slug, &p.Titulo, &p.Categoria, &p.Descripcion, &p.EmpiezaEn,
		&p.DuracionMin, &p.Recurrente, &p.Ciudad, &p.Zona, &p.LugarNombre, &p.Lat, &p.Lng, &p.Somos,
		&p.Total, &p.Ingreso, &p.Visibilidad, &p.SoloVerificados, &p.OcultarDireccion, &p.EdadMin,
		&p.EdadMax, &p.Genero, &p.Nivel, &p.CostoCentavos, &p.Foto, &cancelado,
		&p.Organizador.ID, &p.Organizador.Nombre, &p.Organizador.Foto, &p.Organizador.Edad,
		&p.Organizador.Verificada, &p.Organizador.Asistencia.Fue, &p.Organizador.Asistencia.De,
		&p.MiEstado, &p.MiCantidad, &p.Pendientes,
		&lugarID, &lugar.Slug, &lugar.Nombre, &lugar.Zona, &lugar.Foto, &lugar.BeneficioNivel,
		&p.Cobro, &p.PrecioCentavos, &p.SenaPct, &p.Incluye, &p.Cancelacion, &p.Beneficio, &p.AceptaGrupos, &p.Destacado, &p.Moderacion)
	if lugarID != nil {
		lugar.ID = *lugarID
		p.Lugar = &lugar
	}
	if p.PrecioCentavos != nil {
		p.CargoCentavos = Cargo(int64(*p.PrecioCentavos))
	}
	if p.Cobro == "gratis" {
		p.Cancelacion = ""
	}
	p.Cancelado = cancelado
	p.Organizador.Organizador = true
	p.Faltan = max(int(p.Total)-p.Somos, 0)
	return p, err
}

// Ver devuelve un plan por id o slug. Un plan "por link" se ve con el link; uno de "mi gente",
// sólo si quien mira está en la gente del organizador (o ya está en el plan). desdeLat/Lng, si
// vienen, calculan la distancia.
func (s *Servicio) Ver(ctx context.Context, usuarioID, idOSlug string, desdePunto *[2]float64) (Plan, error) {
	var p Plan
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		campo := "p.slug"
		if esUUID(idOSlug) {
			campo = "p.id::text"
		}
		var err error
		p, err = escanear(tx.QueryRow(ctx, `SELECT `+columnas+desde+`
			WHERE `+campo+` = $2 AND ((p.visibilidad = 'link' AND `+sinBloqueo+`) OR (`+visible+`))
				-- Como en las listas: el plan de una cuenta suspendida o dada de baja no se ve, ni por link.
				-- Quien ya estaba en el plan sí, y uno cancelado también (la baja cancela los suyos: el link
				-- dice «se canceló» en vez de «no existe»).
				AND (`+organizaActivo+` OR m.estado IS NOT NULL OR p.cancelado_en IS NOT NULL) AND `+noOculto, usuarioID, idOSlug))
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Ese plan no existe o no lo podés ver.")
		}
		if err != nil {
			return err
		}
		if p.Organizador.ID == usuarioID {
			p.MiEstado = "organizador"
		}
		var d Direccion
		err = tx.QueryRow(ctx, `SELECT direccion, lat, lng FROM planes_direccion WHERE plan_id = $1`, p.ID).Scan(&d.Texto, &d.Lat, &d.Lng)
		if err == nil {
			p.Direccion = &d
		} else if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		return s.completar(ctx, tx, usuarioID, []*Plan{&p})
	})
	if err == nil && desdePunto != nil {
		ponerDistancia(&p, desdePunto[0], desdePunto[1])
	}
	return p, err
}

// nadie es quien mira sin sesión: no está en ningún plan ni en la gente de nadie.
const nadie = "00000000-0000-0000-0000-000000000000"

// Publico es el plan para el link público (sin sesión), por slug o id: sin dirección exacta ni
// datos de nadie más que el nombre y la foto del organizador y de los que van.
func (s *Servicio) Publico(ctx context.Context, slugPlan string) (Plan, error) {
	var p Plan
	campo := "p.slug"
	if esUUID(slugPlan) {
		campo = "p.id::text"
	}
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var err error
		p, err = escanear(tx.QueryRow(ctx, `SELECT `+columnas+desde+`
			WHERE `+campo+` = $2 AND p.visibilidad IN ('publico', 'link') AND `+organizaActivo, nadie, slugPlan))
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Ese plan no existe o es privado.")
		}
		if err != nil {
			return err
		}
		return s.completar(ctx, tx, "", []*Plan{&p})
	})
	anonimo(&p)
	return p, err
}

// anonimo deja un plan para las rutas públicas (sin sesión): de quien organiza y de los que van,
// sólo el primer nombre y la foto (ni id, ni edad, ni asistencia, ni si llegaron). El id y el slug del
// plan quedan (la web arma los links con ellos).
func anonimo(p *Plan) {
	corto := func(x Persona) Persona {
		nombre, _, _ := strings.Cut(strings.TrimSpace(x.Nombre), " ")
		// El badge de verificada y la asistencia son la reputación que se ve en el plan: quedan.
		return Persona{Nombre: nombre, Foto: x.Foto, Verificada: x.Verificada, Asistencia: x.Asistencia,
			Organizador: x.Organizador, Anfitrion: x.Anfitrion}
	}
	p.Organizador = corto(p.Organizador)
	for i := range p.Van {
		p.Van[i] = corto(p.Van[i])
	}
	p.MiEstado, p.MiCantidad, p.Direccion, p.Pendientes = "ninguno", 0, nil, 0
}

// completar suma los que van (avatares) de cada plan.
func (s *Servicio) completar(ctx context.Context, tx pgx.Tx, usuarioID string, ps []*Plan) error {
	if len(ps) == 0 {
		return nil
	}
	ids := make([]string, len(ps))
	por := map[string]*Plan{}
	for i, p := range ps {
		ids[i] = p.ID
		por[p.ID] = p
		p.Van = []Persona{}
	}
	rows, err := tx.Query(ctx, `
		SELECT s.plan_id, u.id, u.nombre, coalesce(u.fotos[1], ''), u.dni_en IS NOT NULL, s.checkin_en IS NOT NULL
		FROM solicitudes s JOIN usuarios u ON u.id = s.usuario_id
		WHERE s.plan_id = ANY($1::uuid[]) AND s.estado = 'aceptada'
		ORDER BY s.respondida_en NULLS FIRST, s.creada_en`, ids)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var planID string
		var u Persona
		if err := rows.Scan(&planID, &u.ID, &u.Nombre, &u.Foto, &u.Verificada, &u.Llego); err != nil {
			return err
		}
		u.Organizador = u.ID == por[planID].Organizador.ID
		por[planID].Van = append(por[planID].Van, u)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	rows.Close()
	// «Anfitrión» de quienes organizan (una consulta para toda la lista).
	var orgs []string
	vistos := map[string]bool{}
	for _, p := range ps {
		if id := p.Organizador.ID; id != "" && !vistos[id] {
			vistos[id] = true
			orgs = append(orgs, id)
		}
	}
	anf, err := logros.Anfitriones(ctx, tx, orgs, s.ahora())
	if err != nil {
		return err
	}
	for _, p := range ps {
		p.Organizador.Anfitrion = anf[p.Organizador.ID]
		confianza(p)
	}
	return completarMio(ctx, tx, usuarioID, ps)
}

// Filtro de las listas.
type Filtro struct {
	// Punto desde donde se busca y radio. Sin punto: no se filtra por distancia.
	Lat, Lng *float64
	RadioKm  float64
	// Rango de fechas (Explorar). Vacío en Ahora.
	Desde, Hasta time.Time
	Categoria    string
	Ciudad       string
	// Texto libre (título, zona o lugar; 2 letras o más) y zona exacta.
	Q, Zona string
	Limite  int
	// Seguridad, gratis siempre (Plus vende comodidad, nunca seguridad): sólo planes «sólo
	// verificados» y sólo planes «Solo mujeres» («Sólo chicas»).
	SoloVerificados bool
	SoloChicas      bool
	// Filtros finos (Caigo Plus): que falten al menos N, edad de quien organiza y sólo gratis.
	FaltanMin  int
	EdadDesde  int
	EdadHasta  int
	SoloGratis bool
}

// finos: si pide algún filtro de Plus. Los de seguridad (verificados, sólo chicas) no son de Plus.
func (f Filtro) finos() bool {
	return f.FaltanMin > 0 || f.EdadDesde > 0 || f.EdadHasta > 0 || f.SoloGratis
}

// Ahora: los planes de las próximas 3 horas cerca, del más cercano al más lejano.
func (s *Servicio) Ahora(ctx context.Context, usuarioID string, f Filtro) ([]Plan, error) {
	t := s.ahora()
	f.Desde, f.Hasta = t.Add(-ahoraAtras), t.Add(ahoraAdelante)
	if f.RadioKm == 0 {
		f.RadioKm = 15
	}
	ps, err := s.listar(ctx, usuarioID, f)
	if err != nil {
		return nil, err
	}
	if f.Lat != nil && f.Lng != nil {
		sort.SliceStable(ps, func(i, j int) bool {
			if ps[i].Destacado != ps[j].Destacado {
				return ps[i].Destacado
			}
			return *ps[i].DistanciaM < *ps[j].DistanciaM
		})
	}
	return ps, nil
}

// AhoraPublico y ExplorarPublico: lo mismo sin sesión (para mirar antes de entrar). Sólo planes
// públicos; nunca la dirección ni el estado de nadie.
func (s *Servicio) AhoraPublico(ctx context.Context, f Filtro) ([]Plan, error) {
	ps, err := s.Ahora(ctx, nadie, f)
	for i := range ps {
		anonimo(&ps[i])
	}
	return ps, err
}

// ExplorarPublico es Explorar sin sesión.
func (s *Servicio) ExplorarPublico(ctx context.Context, f Filtro) ([]Plan, error) {
	ps, err := s.Explorar(ctx, nadie, f)
	for i := range ps {
		anonimo(&ps[i])
	}
	return ps, err
}

// Explorar: la agenda, por fecha.
func (s *Servicio) Explorar(ctx context.Context, usuarioID string, f Filtro) ([]Plan, error) {
	t := s.ahora()
	if f.Desde.IsZero() || f.Desde.Before(t.Add(-ahoraAtras)) {
		f.Desde = t.Add(-ahoraAtras)
	}
	if f.Hasta.IsZero() || f.Hasta.After(f.Desde.AddDate(0, 2, 0)) {
		f.Hasta = f.Desde.AddDate(0, 0, 14)
	}
	if f.RadioKm == 0 {
		f.RadioKm = 40
	}
	return s.listar(ctx, usuarioID, f)
}

func (s *Servicio) listar(ctx context.Context, usuarioID string, f Filtro) ([]Plan, error) {
	if f.Limite <= 0 || f.Limite > 100 {
		f.Limite = 60
	}
	args := []any{usuarioID, f.Desde, f.Hasta}
	// Los de una cuenta suspendida (o dada de baja) no salen.
	where := []string{"p.cancelado_en IS NULL", "p.empieza_en BETWEEN $2 AND $3", "p.visibilidad <> 'link'", visible, noOculto,
		"o.suspendido_en IS NULL", "o.baja_en IS NULL",
		// «No me interesa»: lo que la persona ocultó por hoy no vuelve a salir.
		"NOT EXISTS (SELECT 1 FROM planes_ocultos h WHERE h.usuario_id = $1 AND h.plan_id = p.id AND h.hasta > now())"}
	arg := func(v any) string { args = append(args, v); return fmt.Sprintf("$%d", len(args)) }
	if f.Lat != nil && f.Lng != nil {
		dLat := f.RadioKm / 111.0
		dLng := f.RadioKm / (111.0 * math.Cos(*f.Lat*math.Pi/180))
		where = append(where, fmt.Sprintf("p.lat BETWEEN %s AND %s AND p.lng BETWEEN %s AND %s",
			arg(*f.Lat-dLat), arg(*f.Lat+dLat), arg(*f.Lng-dLng), arg(*f.Lng+dLng)))
	}
	if f.Categoria != "" {
		where = append(where, "p.categoria = "+arg(f.Categoria))
	}
	if f.Ciudad != "" {
		where = append(where, "lower(p.ciudad) = lower("+arg(f.Ciudad)+")")
	}
	if f.Zona != "" {
		where = append(where, sinTildes("p.zona")+" = "+sinTildes(arg(f.Zona)))
	}
	if q := strings.TrimSpace(f.Q); len([]rune(q)) >= 2 {
		patron := arg("%" + strings.NewReplacer(`\`, `\\`, "%", `\%`, "_", `\_`).Replace(q) + "%")
		where = append(where, "("+sinTildes("p.titulo")+" LIKE "+sinTildes(patron)+" OR "+sinTildes("p.zona")+" LIKE "+sinTildes(patron)+
			" OR "+sinTildes("p.lugar_nombre")+" LIKE "+sinTildes(patron)+" OR "+sinTildes("coalesce(l.nombre, '')")+" LIKE "+sinTildes(patron)+")")
	}
	if f.SoloVerificados {
		where = append(where, "p.solo_verificados")
	}
	if f.SoloChicas {
		where = append(where, "p.genero = 'mujeres'")
	}
	if f.FaltanMin > 0 {
		where = append(where, "p.total - coalesce(c.sumados, 0) >= "+arg(f.FaltanMin))
	}
	if f.EdadDesde > 0 {
		where = append(where, "o.edad >= "+arg(f.EdadDesde))
	}
	if f.EdadHasta > 0 {
		where = append(where, "o.edad <= "+arg(f.EdadHasta))
	}
	if f.SoloGratis {
		where = append(where, "p.cobro = 'gratis' AND coalesce(p.costo_centavos, 0) = 0")
	}
	q := `SELECT ` + columnas + desde + ` WHERE ` + strings.Join(where, " AND ") +
		` ORDER BY p.destacado_en IS NOT NULL DESC, p.empieza_en LIMIT ` + arg(f.Limite)
	var ps []Plan
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if f.finos() {
			e, err := plus.Leer(ctx, tx, usuarioID, s.ahora())
			if err != nil {
				return err
			}
			if !e.Activo {
				return problema.SoloPlus("Los filtros finos son de Caigo Plus.")
			}
		}
		rows, err := tx.Query(ctx, q, args...)
		if err != nil {
			return err
		}
		for rows.Next() {
			p, err := escanear(rows)
			if err != nil {
				rows.Close()
				return err
			}
			if p.Organizador.ID == usuarioID {
				p.MiEstado = "organizador"
			}
			ps = append(ps, p)
		}
		rows.Close()
		if err := rows.Err(); err != nil {
			return err
		}
		punteros := make([]*Plan, len(ps))
		for i := range ps {
			punteros[i] = &ps[i]
		}
		return s.completar(ctx, tx, usuarioID, punteros)
	})
	if err != nil {
		return nil, err
	}
	if f.Lat != nil && f.Lng != nil {
		filtrados := ps[:0]
		for i := range ps {
			ponerDistancia(&ps[i], *f.Lat, *f.Lng)
			if float64(*ps[i].DistanciaM) <= f.RadioKm*1000 {
				filtrados = append(filtrados, ps[i])
			}
		}
		ps = filtrados
	}
	if ps == nil {
		ps = []Plan{}
	}
	return ps, nil
}

// Mios: "Mis planes". cual: organizo | me_sume | historial.
func (s *Servicio) Mios(ctx context.Context, usuarioID, cual string) ([]Plan, error) {
	var where, orden string
	switch cual {
	case "organizo":
		where = "p.organizador_id = $1 AND p.cancelado_en IS NULL AND p.empieza_en + make_interval(mins => p.duracion_min) >= now()"
		orden = "p.empieza_en"
	case "me_sume":
		where = "p.organizador_id <> $1 AND m.estado IN ('pendiente', 'aceptada') AND p.cancelado_en IS NULL AND p.empieza_en + make_interval(mins => p.duracion_min) >= now()"
		orden = "p.empieza_en"
	case "historial":
		where = "m.estado = 'aceptada' AND (p.cancelado_en IS NOT NULL OR p.empieza_en + make_interval(mins => p.duracion_min) < now())"
		orden = "p.empieza_en DESC"
	default:
		return nil, problema.Invalido("Elegí organizo, me_sume o historial.")
	}
	ps := []Plan{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT `+columnas+desde+` WHERE `+where+` AND `+noOculto+` ORDER BY `+orden+` LIMIT 100`, usuarioID)
		if err != nil {
			return err
		}
		for rows.Next() {
			p, err := escanear(rows)
			if err != nil {
				rows.Close()
				return err
			}
			if p.Organizador.ID == usuarioID {
				p.MiEstado = "organizador"
			}
			ps = append(ps, p)
		}
		rows.Close()
		if err := rows.Err(); err != nil {
			return err
		}
		punteros := make([]*Plan, len(ps))
		for i := range ps {
			punteros[i] = &ps[i]
		}
		return s.completar(ctx, tx, usuarioID, punteros)
	})
	return ps, err
}

// Destacar: con Plus, un plan propio por semana va primero en Ahora y Explorar hasta que pasa.
func (s *Servicio) Destacar(ctx context.Context, usuarioID, planID string) (Plan, error) {
	if !esUUID(planID) {
		return Plan{}, problema.NoEncontrado("Ese plan no existe.")
	}
	t := s.ahora()
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := plus.Bloquear(ctx, tx, usuarioID); err != nil {
			return err
		}
		e, err := plus.Leer(ctx, tx, usuarioID, t)
		if err != nil {
			return err
		}
		if !e.Activo {
			return problema.SoloPlus("Destacar un plan es de Caigo Plus.")
		}
		var empieza time.Time
		var cancelado, destacado bool
		err = tx.QueryRow(ctx, `SELECT empieza_en, cancelado_en IS NOT NULL, destacado_en IS NOT NULL FROM planes
			WHERE id = $1 AND organizador_id = $2`, planID, usuarioID).Scan(&empieza, &cancelado, &destacado)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Ese plan no es tuyo.")
		} else if err != nil {
			return err
		}
		switch {
		case destacado:
			return nil
		case cancelado || empieza.Before(t):
			return problema.Conflicto("plan_pasado", "Ese plan ya pasó o está cancelado.")
		case e.DestacarDesde != nil:
			return problema.Conflicto("destacado_usado", "Ya destacaste un plan esta semana. Podés destacar otro el "+
				plus.EnArgentina(*e.DestacarDesde).Format("02/01")+".")
		}
		_, err = tx.Exec(ctx, `UPDATE planes SET destacado_en = $3 WHERE id = $1 AND organizador_id = $2`, planID, usuarioID, t)
		return err
	})
	if err != nil {
		return Plan{}, err
	}
	return s.Ver(ctx, usuarioID, planID, nil)
}

// sinTildes compara sin mayúsculas ni tildes ("guemes" encuentra "Güemes").
func sinTildes(expr string) string {
	return "translate(lower(" + expr + "), 'áéíóúüñ', 'aeiouun')"
}

var reUUID = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)

func esUUID(s string) bool { return reUUID.MatchString(s) }

// distancia en metros entre dos puntos (haversine).
func distancia(lat1, lng1, lat2, lng2 float64) float64 {
	const r = 6371000.0
	rad := math.Pi / 180
	dLat, dLng := (lat2-lat1)*rad, (lng2-lng1)*rad
	a := math.Sin(dLat/2)*math.Sin(dLat/2) + math.Cos(lat1*rad)*math.Cos(lat2*rad)*math.Sin(dLng/2)*math.Sin(dLng/2)
	return 2 * r * math.Asin(math.Sqrt(a))
}

func ponerDistancia(p *Plan, lat, lng float64) {
	d := int(math.Round(distancia(lat, lng, p.Lat, p.Lng)))
	p.DistanciaM = &d
}

// entradaPlan es la entrada de la cola de un plan para revisar.
func entradaPlan(planID, usuarioID, texto string, r moderacion.Resultado) moderacion.Entrada {
	return moderacion.Entrada{Tipo: moderacion.TipoPlan, ObjetoID: planID, PlanID: planID, UsuarioID: usuarioID,
		Texto: texto, Origen: r.Origen, Categoria: r.Categoria, Motivo: r.Motivo, Estado: "revision"}
}
