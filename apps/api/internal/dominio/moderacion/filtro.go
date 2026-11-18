// Package moderacion revisa lo que se publica en Caigo (planes y mensajes). Primero un filtro propio,
// al instante y sin costo; sólo lo dudoso va a la IA (Claude Haiku) y lo que queda en duda, al equipo.
package moderacion

import (
	"regexp"
	"strings"
	"unicode"

	"golang.org/x/text/unicode/norm"
)

// Decisiones del filtro propio.
const (
	OK       = "ok"
	Dudoso   = "dudoso"
	Bloquear = "bloquear"
)

// Veredicto del filtro: qué hacer, de qué se trata y por qué (el motivo lo puede ver el autor).
type Veredicto struct {
	Decision  string
	Categoria string
	Motivo    string
}

// regla: frases (palabras enteras, ya normalizadas) o una expresión sobre el texto normalizado.
type regla struct {
	decision, categoria, motivo string
	frases                      []string
	expr                        *regexp.Regexp
	// fn: para lo que no entra en una expresión (un teléfono, no una fecha ni un monto).
	fn func(n string) bool
}

// Orden de las reglas: gana la primera que bloquea; si ninguna bloquea, la primera dudosa. Por eso
// las graves van arriba (el motivo que se guarda es el más importante).
var reglas = []regla{
	// Menores: quien dice serlo, o algo sexual con menores. Caigo es +18.
	{decision: Bloquear, categoria: "menores", motivo: "Caigo es sólo para mayores de 18",
		frases: []string{"soy menor", "soy menor de edad", "todavia no tengo 18", "no tengo 18", "voy al secundario",
			"loli", "lolis", "jailbait", "pack de menores", "cp de menores"},
		expr: regexp.MustCompile(`\btengo (1[0-7]|[5-9]) (anos|anitos)\b`)},
	{decision: Dudoso, categoria: "menores", motivo: "menciona a menores de edad",
		frases: []string{"menor de edad", "menores de edad", "colegiala", "colegialas", "nena", "nenas", "pendeja", "pendejas",
			"secundaria", "secundario", "nene", "nenes"},
		expr: regexp.MustCompile(`\b(de|con|tiene|tienen) (1[0-7]|[5-9]) (anos|anitos)\b`)},
	// Amenazas y violencia.
	{decision: Bloquear, categoria: "violencia", motivo: "amenazas",
		frases: []string{"te voy a matar", "los voy a matar", "te voy a cagar a palos", "te cago a palos",
			"te rompo la cara", "te voy a romper la cara", "te voy a violar", "te violo", "te voy a buscar a tu casa",
			"se donde vivis", "te voy a prender fuego", "te pego un tiro", "te voy a pegar un tiro"}},
	{decision: Dudoso, categoria: "violencia", motivo: "puede ser violento",
		frases: []string{"te mato", "matar", "matarte", "violar", "violacion", "pegarte", "cagarte a trompadas", "fierro", "chumbo",
			"cuchillo", "apunalar", "suicidarme", "matarme"}},
	// Odio: insulto + grupo, o insultos que en sí son discriminatorios.
	{decision: Bloquear, categoria: "odio", motivo: "discurso de odio",
		frases: []string{"negro de mierda", "negros de mierda", "judio de mierda", "judios de mierda", "bolita de mierda",
			"bolitas de mierda", "paragua de mierda", "paraguas de mierda", "villero de mierda", "villeros de mierda",
			"puto de mierda", "trava de mierda", "sudaca de mierda", "mogolico de mierda", "hay que matarlos a todos"}},
	{decision: Dudoso, categoria: "odio", motivo: "puede ser discriminatorio",
		frases: []string{"trolo", "trolos", "maricon", "maricones", "puto", "putos", "trava", "travas", "sudaca", "sudacas",
			"bolita", "bolitas", "paragua", "paraguas", "villero", "villeros", "mogolico", "mogolica", "negrada", "judio",
			"retrasado", "retrasada", "sidoso"}},
	// Sexual explícito, comercio sexual.
	{decision: Dudoso, categoria: "sexual", motivo: "contenido sexual",
		frases: []string{"pija", "pijas", "concha", "garchar", "garchamos", "coger", "cogemos", "cogerte", "pete", "petera",
			"chupamela", "chupame", "tetas", "orto", "nudes", "nude", "pack", "packs", "sexo", "trio", "onlyfans", "escort",
			"putita", "trola", "servicio completo", "final feliz", "fotos hot", "videollamada hot", "hot", "culo", "verga"}},
	// Plata por fuera de la app (estafas): CBU, alias, cripto, «transferime».
	{decision: Dudoso, categoria: "estafa", motivo: "pide plata por fuera de Caigo",
		frases: []string{"cbu", "cvu", "alias", "transferime", "transferi", "transferis", "transferencia", "mandame plata",
			"mandame la plata", "deposita", "depositame", "usdt", "binance", "cripto", "criptomonedas", "bitcoin",
			"inverti", "invertir", "inversion", "ganancias", "ganar plata", "ganar dinero", "plata facil", "prestamo"}},
	// Sacar la charla de la app: teléfonos, mails, redes.
	{decision: Dudoso, categoria: "contacto", motivo: "datos de contacto",
		frases: []string{"whatsapp", "wsp", "wpp", "whats", "telegram", "instagram", "insta", "ig", "snapchat", "snap",
			"tiktok", "facebook", "mi numero", "mi celu", "mi cel", "escribime al", "agregame", "seguime", "mandame al"},
		expr: regexp.MustCompile(`[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,}|(^|\s)@[a-z0-9._]{3,}`), fn: telefono},
	// Drogas.
	{decision: Dudoso, categoria: "drogas", motivo: "drogas",
		frases: []string{"faso", "porro", "porros", "merca", "cocaina", "falopa", "pepa", "pepas", "tusi", "lsd", "keta",
			"ketamina", "extasis", "mdma", "vendo flores", "porrito", "marihuana", "blunt", "cristal"}},
	// Insultos (los cariñosos como «boludo» o «gil» quedan afuera: son el saludo de medio país).
	{decision: Dudoso, categoria: "insultos", motivo: "insultos",
		frases: []string{"pelotudo", "pelotuda", "forro", "forra", "hdp", "hijo de puta", "hija de puta", "la concha de tu madre",
			"lcdtm", "conchudo", "conchuda", "puta", "sorete", "garca", "imbecil", "idiota", "tarado", "tarada", "cagon",
			"cornudo", "lacra", "andate a la mierda", "chupame la pija", "mierda", "pajero", "pajera"}},
	// Spam: promociones y enlaces (los mapas y Caigo pasan).
	{decision: Dudoso, categoria: "spam", motivo: "parece spam",
		frases: []string{"sorteo", "promo", "promocion", "seguidores gratis", "trabajo desde casa", "link en bio", "clickea",
			"hace click", "entra al link", "descuento", "oferta", "gratis gratis", "vendo"},
		expr: regexp.MustCompile(`(https?://|www\.)\S+|\b[a-z0-9\-]+\.(com|net|org|ar|io|ly|me|xyz|club|link|site|info|biz)(\.[a-z]{2})?\b`)},
}

// Enlaces que no son spam: mapas y la propia Caigo.
var enlacePermitido = regexp.MustCompile(`(https?://)?(www\.)?(maps\.app\.goo\.gl|goo\.gl/maps|google\.com/maps|maps\.google\.[a-z.]+|caigo\.com\.ar)\S*`)

// Filtrar decide sin IA. ok: sale tal cual. dudoso: lo revisa la IA. bloquear: no sale.
func Filtrar(texto string) Veredicto {
	n := Normalizar(texto)
	if n == "" {
		return Veredicto{Decision: OK}
	}
	sinMapas := strings.TrimSpace(enlacePermitido.ReplaceAllString(n, " "))
	// Las frases se buscan por palabras enteras, sin signos («cbu:» es «cbu»).
	conBordes := " " + strings.Join(strings.Fields(signos.ReplaceAllString(sinMapas, " ")), " ") + " "
	var dudoso *Veredicto
	for _, r := range reglas {
		if !r.coincide(sinMapas, conBordes) {
			continue
		}
		v := Veredicto{Decision: r.decision, Categoria: r.categoria, Motivo: r.motivo}
		if r.decision == Bloquear {
			return v
		}
		if dudoso == nil {
			dudoso = &v
		}
	}
	if dudoso != nil {
		return *dudoso
	}
	if repetido(texto) {
		return Veredicto{Decision: Dudoso, Categoria: "spam", Motivo: "parece spam"}
	}
	return Veredicto{Decision: OK}
}

func (r regla) coincide(n, conBordes string) bool {
	for _, f := range r.frases {
		if strings.Contains(conBordes, " "+f+" ") {
			return true
		}
	}
	return (r.expr != nil && r.expr.MatchString(n)) || (r.fn != nil && r.fn(n))
}

var (
	signos       = regexp.MustCompile(`[^a-z0-9]+`)
	candidatoTel = regexp.MustCompile(`\+?\d[\d \-.]{6,}\d`)
	pareceFecha  = regexp.MustCompile(`^\d{4}-\d{2}-\d{2}$|^\d{1,2}[-.]\d{1,2}[-.]\d{2,4}$`)
	pareceMonto  = regexp.MustCompile(`^\d{1,3}(\.\d{3})+$`)
)

// telefono: 8 a 15 dígitos seguidos (con espacios, guiones o puntos), que no sean una fecha ni un
// monto con puntos de miles («$ 15.000.000»).
func telefono(n string) bool {
	for _, c := range candidatoTel.FindAllString(n, -1) {
		c = strings.TrimSpace(c)
		if pareceFecha.MatchString(c) || pareceMonto.MatchString(c) {
			continue
		}
		digitos := 0
		for _, r := range c {
			if unicode.IsDigit(r) {
				digitos++
			}
		}
		if digitos >= 8 && digitos <= 15 {
			return true
		}
	}
	return false
}

// Normalizar: minúsculas, sin tildes, el «leet» más común (p1ja, c0ger), letras estiradas a una
// («putaaaa») y todo lo que no es letra, número o lo que usan los mails y enlaces, como espacio.
func Normalizar(s string) string {
	s = strings.ToLower(norm.NFD.String(s))
	var b strings.Builder
	var ultima rune
	for _, r := range s {
		if unicode.Is(unicode.Mn, r) {
			continue // la tilde suelta que dejó NFD
		}
		switch r {
		case '0':
			r = siEntreLetras(r, 'o', ultima)
		case '1':
			r = siEntreLetras(r, 'i', ultima)
		case '3':
			r = siEntreLetras(r, 'e', ultima)
		case '4':
			r = siEntreLetras(r, 'a', ultima)
		case '$':
			r = siEntreLetras(r, 's', ultima)
		}
		if !(unicode.IsLetter(r) || unicode.IsDigit(r) || strings.ContainsRune("@.:/_-+%", r)) {
			r = ' '
		}
		b.WriteRune(r)
		ultima = r
	}
	return strings.TrimSpace(estirar(b.String()))
}

// estirar junta las letras estiradas («putaaaa» → «puta») y los espacios. Las de a dos quedan: son
// castellano («ll», «rr»).
func estirar(s string) string {
	rs := []rune(s)
	var b strings.Builder
	for i := 0; i < len(rs); {
		j := i
		for j < len(rs) && rs[j] == rs[i] {
			j++
		}
		n := j - i
		if (n >= 3 && unicode.IsLetter(rs[i])) || rs[i] == ' ' {
			n = 1
		}
		for k := 0; k < n; k++ {
			b.WriteRune(rs[i])
		}
		i = j
	}
	return b.String()
}

// siEntreLetras cambia el número por la letra sólo dentro de una palabra (p1ja), no en «tengo 14».
func siEntreLetras(r, letra, anterior rune) rune {
	if unicode.IsLetter(anterior) {
		return letra
	}
	return r
}

// repetido: el mismo carácter 10+ veces seguidas o una palabra que se repite mucho (copiar y pegar).
func repetido(texto string) bool {
	var ultima rune
	n := 0
	for _, r := range texto {
		if r == ultima && !unicode.IsSpace(r) {
			n++
			if n >= 9 {
				return true
			}
		} else {
			n, ultima = 0, r
		}
	}
	palabras := strings.Fields(strings.ToLower(texto))
	if len(palabras) < 12 {
		return false
	}
	cuenta := map[string]int{}
	for _, p := range palabras {
		cuenta[p]++
	}
	return len(cuenta)*4 < len(palabras)
}
