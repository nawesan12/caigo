package lugares

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"math"
	"math/big"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/wallet"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Categorías del panel de lugares → categorías de plan.
var categoriasLugar = map[string]string{
	"taller": "taller", "musica": "musica", "juego": "juego", "deporte": "deporte",
	"gastronomia": "gastronomia", "fiesta": "fiesta",
}

// PlanLugar es el formulario "Nuevo plan" del panel.
type PlanLugar struct {
	Titulo       string    `json:"titulo"`
	Categoria    string    `json:"categoria"`
	Descripcion  string    `json:"descripcion"`
	Foto         string    `json:"foto"`
	EmpiezaEn    time.Time `json:"empiezaEn"`
	DuracionMin  int16     `json:"duracionMin"`
	Cupo         int16     `json:"cupo"`
	AceptaGrupos bool      `json:"aceptaGrupos"`
	Recurrente   bool      `json:"recurrente"`
	// gratis | entrada | sena
	Cobro          string `json:"cobro"`
	PrecioCentavos int32  `json:"precioCentavos"`
	SenaPct        int16  `json:"senaPct"`
	Incluye        string `json:"incluye"`
	// flex | mod | strict
	Cancelacion string `json:"cancelacion"`
	Beneficio   string `json:"beneficio"`
	// cualquiera | verificados | mujeres
	Quien string `json:"quien"`
}

func (p *PlanLugar) validar(ahora time.Time) error {
	var campos []problema.Campo
	mal := func(r, m string) { campos = append(campos, problema.Campo{Ruta: r, Mensaje: m}) }
	p.Titulo, p.Descripcion, p.Incluye, p.Beneficio = strings.TrimSpace(p.Titulo), strings.TrimSpace(p.Descripcion),
		strings.TrimSpace(p.Incluye), strings.TrimSpace(p.Beneficio)
	if p.Titulo == "" || len([]rune(p.Titulo)) > 80 {
		mal("titulo", "Poné el nombre del plan.")
	}
	if _, ok := categoriasLugar[p.Categoria]; !ok {
		mal("categoria", "Elegí una categoría.")
	}
	if p.EmpiezaEn.Before(ahora) || p.EmpiezaEn.After(ahora.AddDate(0, 6, 0)) {
		mal("empiezaEn", "Elegí un día y una hora que no hayan pasado (hasta 6 meses).")
	}
	if p.DuracionMin == 0 {
		p.DuracionMin = 120
	}
	if p.DuracionMin < 15 || p.DuracionMin > 1440 {
		mal("duracionMin", "La duración va de 15 minutos a 24 horas.")
	}
	if p.Cupo < 2 || p.Cupo > 60 {
		mal("cupo", "El cupo va de 2 a 60 personas.")
	}
	switch p.Cobro {
	case "gratis":
		p.PrecioCentavos, p.SenaPct = 0, 0
	case "entrada", "sena":
		if p.PrecioCentavos < 50000 || p.PrecioCentavos > 50000000 {
			mal("precioCentavos", "El precio va de $500 a $500.000.")
		}
		if p.Cobro == "sena" && (p.SenaPct < 10 || p.SenaPct > 90) {
			mal("senaPct", "La seña va del 10 % al 90 %.")
		}
	default:
		mal("cobro", "Elegí cómo se cobra.")
	}
	if p.Cancelacion == "" {
		p.Cancelacion = "flex"
	}
	if p.Cancelacion != "flex" && p.Cancelacion != "mod" && p.Cancelacion != "strict" {
		mal("cancelacion", "Elegí qué pasa si alguien cancela.")
	}
	if p.Quien == "" {
		p.Quien = "cualquiera"
	}
	if p.Quien != "cualquiera" && p.Quien != "verificados" && p.Quien != "mujeres" {
		mal("quien", "Elegí quién puede sumarse.")
	}
	if len([]rune(p.Descripcion)) > 600 || len([]rune(p.Incluye)) > 200 || len([]rune(p.Beneficio)) > 80 || len(p.Foto) > 500 {
		mal("descripcion", "Algún texto es muy largo.")
	}
	if len(campos) > 0 {
		return problema.Invalido("Revisá el plan.", campos...)
	}
	return nil
}

func nulo[T comparable](v T) *T {
	var cero T
	if v == cero {
		return nil
	}
	return &v
}

// CrearPlan publica un plan del lugar. Con cobro, el lugar tiene que tener Mercado Pago conectado.
func (s *Servicio) CrearPlan(ctx context.Context, usuarioID, lugarID string, p PlanLugar) (string, error) {
	if err := p.validar(s.ahora()); err != nil {
		return "", err
	}
	var id, slug string
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, true); err != nil {
			return err
		}
		var l struct {
			nombre, ciudad, zona, direccion string
			lat, lng                        float64
			mp                              bool
		}
		if err := tx.QueryRow(ctx, `SELECT l.nombre, l.ciudad, l.zona, l.direccion, l.lat, l.lng,
				EXISTS (SELECT 1 FROM mp_conexiones c WHERE c.lugar_id = l.id)
			FROM lugares l WHERE l.id::text = $1`, lugarID).Scan(&l.nombre, &l.ciudad, &l.zona, &l.direccion, &l.lat, &l.lng, &l.mp); err != nil {
			return err
		}
		if p.Cobro != "gratis" && !l.mp {
			return problema.NoProcesable("sin_mp", "Para cobrar entradas, conectá Mercado Pago en Cobros.")
		}
		genero := "cualquiera"
		if p.Quien == "mujeres" {
			genero = "mujeres"
		}
		slug = slugDe(p.Titulo+" "+l.nombre) + "-" + strings.ToLower(noSlug.ReplaceAllString(azar4(), ""))
		var serie *string
		if p.Recurrente {
			var sid string
			if err := tx.QueryRow(ctx, `SELECT gen_random_uuid()`).Scan(&sid); err != nil {
				return err
			}
			serie = &sid
		}
		if err := tx.QueryRow(ctx, `
			INSERT INTO planes (slug, organizador_id, lugar_id, titulo, categoria, descripcion, empieza_en, duracion_min,
				recurrente, serie_id, ciudad, zona, lugar_nombre, lat, lng, somos, total, ingreso, visibilidad,
				solo_verificados, ocultar_direccion, genero, foto, cobro, precio_centavos, sena_pct, incluye,
				cancelacion, beneficio, acepta_grupos)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, 0, $16, 'libre', 'publico',
				$17, false, $18, $19, $20, $21, $22, $23, $24, $25, $26) RETURNING id`,
			slug, usuarioID, lugarID, p.Titulo, categoriasLugar[p.Categoria], p.Descripcion, p.EmpiezaEn, p.DuracionMin,
			p.Recurrente, serie, l.ciudad, l.zona, l.nombre, l.lat, l.lng, p.Cupo, p.Quien == "verificados", genero, p.Foto,
			p.Cobro, nulo(p.PrecioCentavos), nulo(p.SenaPct), p.Incluye, p.Cancelacion, p.Beneficio, p.AceptaGrupos).Scan(&id); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `INSERT INTO planes_direccion (plan_id, direccion, lat, lng) VALUES ($1, $2, $3, $4)`, id, l.direccion, l.lat, l.lng)
		return err
	})
	return id, err
}

func azar4() string {
	const letras = "abcdefghjkmnpqrstuvwxyz23456789"
	b := make([]byte, 4)
	for i := range b {
		n, _ := rand.Int(rand.Reader, big.NewInt(int64(len(letras))))
		b[i] = letras[n.Int64()]
	}
	return string(b)
}

// PlanDelLugar en el panel.
type PlanDelLugar struct {
	ID          string    `json:"id"`
	Slug        string    `json:"slug"`
	Titulo      string    `json:"titulo"`
	Categoria   string    `json:"categoria"`
	EmpiezaEn   time.Time `json:"empiezaEn"`
	Foto        string    `json:"foto"`
	Cupo        int16     `json:"cupo"`
	Confirmados int       `json:"confirmados"`
	Cobro       string    `json:"cobro"`
	// Precio por persona y seña (sólo con cobro), beneficio por check-in y duración.
	PrecioCentavos *int32 `json:"precioCentavos,omitempty"`
	SenaPct        *int16 `json:"senaPct,omitempty"`
	Beneficio      string `json:"beneficio,omitempty"`
	DuracionMin    int16  `json:"duracionMin"`
	Entradas       int    `json:"entradas"`
	// Lo que recibe el lugar (sin el cargo de Caigo), en centavos.
	CobradoCentavos int64  `json:"cobradoCentavos"`
	Cancelado       bool   `json:"cancelado"`
	Link            string `json:"link"`
}

// Planes del lugar: proximos | pasados, o uno solo si planID no está vacío.
func (s *Servicio) Planes(ctx context.Context, usuarioID, lugarID, cual, planID string) ([]PlanDelLugar, error) {
	args := []any{lugarID}
	cond := "p.empieza_en + make_interval(mins => p.duracion_min) >= now() ORDER BY p.empieza_en"
	switch {
	case planID != "":
		cond = "p.id::text = $2"
		args = append(args, planID)
	case cual == "pasados":
		cond = "p.empieza_en + make_interval(mins => p.duracion_min) < now() ORDER BY p.empieza_en DESC"
	}
	out := []PlanDelLugar{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		rows, err := tx.Query(ctx, `
			SELECT p.id, p.slug, p.titulo, p.categoria, p.empieza_en, p.foto, p.total,
				coalesce((SELECT sum(cantidad) FROM solicitudes x WHERE x.plan_id = p.id AND x.estado = 'aceptada'), 0)::integer,
				p.cobro, p.precio_centavos, p.sena_pct, p.beneficio, p.duracion_min,
				coalesce((SELECT sum(cantidad) FROM entradas e WHERE e.plan_id = p.id AND e.estado = 'pagada'), 0)::integer,
				coalesce((SELECT sum(lugar_centavos) FROM entradas e WHERE e.plan_id = p.id AND e.estado = 'pagada'), 0)::bigint,
				p.cancelado_en IS NOT NULL
			FROM planes p WHERE p.lugar_id::text = $1 AND `+cond+` LIMIT 100`, args...)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var x PlanDelLugar
			if err := rows.Scan(&x.ID, &x.Slug, &x.Titulo, &x.Categoria, &x.EmpiezaEn, &x.Foto, &x.Cupo, &x.Confirmados,
				&x.Cobro, &x.PrecioCentavos, &x.SenaPct, &x.Beneficio, &x.DuracionMin, &x.Entradas, &x.CobradoCentavos, &x.Cancelado); err != nil {
				return err
			}
			x.Link = strings.TrimRight(s.Sitio, "/") + "/p/" + x.Slug
			out = append(out, x)
		}
		return rows.Err()
	})
	return out, err
}

// Asistente de un plan del lugar ("Quiénes vienen").
type Asistente struct {
	Nombre   string     `json:"nombre"`
	Foto     string     `json:"foto"`
	Cantidad int16      `json:"cantidad"`
	Pago     string     `json:"pago"` // gratis | pago | sena
	Codigo   string     `json:"codigo,omitempty"`
	Saldo    int32      `json:"saldoCentavos,omitempty"`
	Checkin  *time.Time `json:"checkin,omitempty"`
}

// Asistentes de un plan del lugar.
func (s *Servicio) Asistentes(ctx context.Context, usuarioID, lugarID, planID string) ([]Asistente, error) {
	out := []Asistente{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		rows, err := tx.Query(ctx, `
			SELECT u.nombre, coalesce(u.fotos[1], ''), s.cantidad,
				CASE WHEN e.id IS NULL THEN 'gratis' WHEN e.modo = 'sena' THEN 'sena' ELSE 'pago' END,
				coalesce(e.codigo, ''), coalesce(e.saldo_centavos, 0), s.checkin_en
			FROM solicitudes s JOIN usuarios u ON u.id = s.usuario_id JOIN planes p ON p.id = s.plan_id
			LEFT JOIN entradas e ON e.plan_id = s.plan_id AND e.usuario_id = s.usuario_id AND e.estado = 'pagada'
			WHERE s.plan_id::text = $1 AND p.lugar_id::text = $2 AND s.estado = 'aceptada'
			ORDER BY s.respondida_en`, planID, lugarID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var a Asistente
			if err := rows.Scan(&a.Nombre, &a.Foto, &a.Cantidad, &a.Pago, &a.Codigo, &a.Saldo, &a.Checkin); err != nil {
				return err
			}
			out = append(out, a)
		}
		return rows.Err()
	})
	return out, err
}

// Compra: el resultado de pedir entradas.
type Compra struct {
	EntradaID string `json:"entradaId"`
	// Link de Checkout Pro (vacío si el plan es gratis: ya quedó adentro).
	Link          string `json:"link,omitempty"`
	TotalCentavos int64  `json:"totalCentavos"`
	CargoCentavos int64  `json:"cargoCentavos"`
	SaldoCentavos int64  `json:"saldoCentavos"`
}

func nuevoCodigo() string {
	n, _ := rand.Int(rand.Reader, big.NewInt(9000))
	return fmt.Sprintf("TK-%04d", 1000+n.Int64())
}

// Comprar reserva entradas de un plan de lugar y devuelve el link de pago. modo: entrada (pagar
// todo) | sena (sólo si el plan tiene seña; vacío = lo que diga el plan). El monto sale de la
// base, nunca del cliente.
func (s *Servicio) Comprar(ctx context.Context, usuarioID, planID string, cantidad int16, modo string) (Compra, error) {
	if cantidad < 1 || cantidad > 10 {
		return Compra{}, problema.Invalido("Podés comprar de 1 a 10 entradas.")
	}
	if modo != "" && modo != "entrada" && modo != "sena" {
		return Compra{}, problema.Invalido("El modo es entrada o sena.")
	}
	var c Compra
	var lugarID, titulo, ref, lugarNombre, slug string
	var precio int64
	var senaPct *int16
	var cobro string
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var total int16
		var sumados int
		var selfie, dni, cancelado, grupos bool
		var empieza time.Time
		var soloVerif bool
		var lid *string
		var prec *int32
		err := tx.QueryRow(ctx, `SELECT p.lugar_id, p.titulo, p.slug, p.cobro, p.precio_centavos, p.sena_pct, p.total,
				p.cancelado_en IS NOT NULL, p.empieza_en, p.solo_verificados, p.acepta_grupos, coalesce(l.nombre, ''),
				coalesce((SELECT sum(cantidad) FROM solicitudes x WHERE x.plan_id = p.id AND x.estado = 'aceptada'), 0)::integer
					-- Las compras sin pagar reservan su lugar mientras vale la preferencia (30 min).
					+ coalesce((SELECT sum(cantidad) FROM entradas y WHERE y.plan_id = p.id AND y.estado = 'pendiente'
						AND y.usuario_id <> $2 AND y.creada_en > now() - interval '30 minutes'), 0)::integer
			FROM planes p LEFT JOIN lugares l ON l.id = p.lugar_id WHERE p.id::text = $1 FOR UPDATE OF p`, planID, usuarioID).
			Scan(&lid, &titulo, &slug, &cobro, &prec, &senaPct, &total, &cancelado, &empieza, &soloVerif, &grupos, &lugarNombre, &sumados)
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && lid == nil) {
			return problema.NoEncontrado("Ese plan no tiene entradas.")
		}
		if err != nil {
			return err
		}
		lugarID = *lid
		if prec != nil {
			precio = int64(*prec)
		}
		if err := tx.QueryRow(ctx, `SELECT selfie_en IS NOT NULL, dni_en IS NOT NULL FROM usuarios WHERE id = $1`, usuarioID).Scan(&selfie, &dni); err != nil {
			return err
		}
		switch {
		case cancelado:
			return problema.Conflicto("cancelado", "Este plan se canceló.")
		case empieza.Before(s.ahora()):
			return problema.Conflicto("paso", "Este plan ya arrancó.")
		case !selfie:
			return problema.NoProcesable("falta_selfie", "Antes de sumarte, verificá tu identidad con una selfie.")
		case soloVerif && !dni:
			return problema.NoProcesable("falta_dni", "Este plan es sólo para personas con DNI verificado.")
		case cantidad > 1 && !grupos:
			return problema.Conflicto("sin_grupos", "Este plan es de a una persona.")
		case sumados+int(cantidad) > int(total):
			return problema.Conflicto("completo", "No quedan tantos lugares (puede que se liberen si alguien no termina de pagar).")
		}
		var ya bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM solicitudes WHERE plan_id::text = $1 AND usuario_id = $2 AND estado = 'aceptada')`,
			planID, usuarioID).Scan(&ya); err != nil {
			return err
		}
		if ya {
			return problema.Conflicto("ya_esta", "Ya tenés tu lugar en este plan.")
		}
		switch {
		case modo == "sena" && cobro != "sena":
			return problema.Conflicto("sin_sena", "Este plan no se puede señar.")
		case modo == "":
			modo = "entrada"
			if cobro == "sena" {
				modo = "sena"
			}
		}
		if cobro == "gratis" {
			// Gratis: queda adentro al toque.
			_, err := tx.Exec(ctx, `INSERT INTO solicitudes (plan_id, usuario_id, cantidad, estado, respondida_en)
				VALUES ($1, $2, $3, 'aceptada', now()) ON CONFLICT (plan_id, usuario_id) DO UPDATE SET
				cantidad = excluded.cantidad, estado = 'aceptada', respondida_en = now()`, planID, usuarioID, cantidad)
			return err
		}
		q := int64(cantidad)
		cargo := planes.Cargo(precio)
		lugarPP := precio
		if modo == "sena" {
			// Seña: el % del precio, redondeado a $100 (como el panel).
			lugarPP = int64(math.Round(float64(precio)*float64(*senaPct)/100/10000)) * 10000
		}
		c.CargoCentavos = cargo * q
		c.TotalCentavos = (lugarPP + cargo) * q
		c.SaldoCentavos = (precio - lugarPP) * q
		ref = "CAI" + strings.ToUpper(azar4()+azar4()+azar4())
		// El código puede chocar (4 dígitos): se reintenta.
		for i := 0; ; i++ {
			err := tx.QueryRow(ctx, `INSERT INTO entradas (codigo, plan_id, usuario_id, cantidad, modo, lugar_centavos, cargo_centavos,
				saldo_centavos, referencia) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) ON CONFLICT (codigo) DO NOTHING RETURNING id`,
				nuevoCodigo(), planID, usuarioID, cantidad, modo, lugarPP*q, c.CargoCentavos, c.SaldoCentavos, ref).Scan(&c.EntradaID)
			if err == nil {
				return nil
			}
			if !errors.Is(err, pgx.ErrNoRows) || i > 10 {
				return err
			}
		}
	})
	if err != nil || cobro == "gratis" {
		return c, err
	}
	token, err := s.tokenDe(ctx, lugarID)
	if err != nil {
		return Compra{}, err
	}
	concepto := "Entrada"
	if modo == "sena" {
		concepto = "Seña"
	}
	vuelta := strings.TrimRight(s.Sitio, "/") + "/p/" + slug
	pref := map[string]any{
		"items": []map[string]any{{
			"title": recortar(concepto+" · "+titulo, 120), "quantity": 1, "currency_id": "ARS",
			"unit_price": float64(c.TotalCentavos) / 100,
		}},
		"external_reference": ref,
		// El cargo por servicio es la comisión de Caigo: Mercado Pago se lo deja a la plataforma.
		"marketplace_fee":      float64(c.CargoCentavos) / 100,
		"back_urls":            map[string]string{"success": vuelta + "?pago=ok", "pending": vuelta + "?pago=pendiente", "failure": vuelta + "?pago=error"},
		"auto_return":          "approved",
		"statement_descriptor": recortar("CAIGO "+lugarNombre, 22),
		"notification_url":     strings.TrimRight(s.APIPublica, "/") + "/v1/webhooks/mercadopago",
		"expires":              true,
		"expiration_date_to":   s.ahora().Add(30 * time.Minute).Format("2006-01-02T15:04:05.000-07:00"),
	}
	var r struct {
		ID        string `json:"id"`
		InitPoint string `json:"init_point"`
	}
	if err := s.MP.JSON(ctx, http.MethodPost, "/checkout/preferences", token, ref, pref, &r); err != nil {
		return Compra{}, err
	}
	if r.InitPoint == "" {
		return Compra{}, errors.New("lugares: la preferencia no trajo init_point")
	}
	c.Link = r.InitPoint
	err = s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `UPDATE entradas SET mp_preferencia = $2 WHERE id = $1`, c.EntradaID, r.ID)
		return err
	})
	return c, err
}

func recortar(s string, n int) string {
	r := []rune(s)
	if len(r) <= n {
		return s
	}
	return string(r[:n])
}

// ConciliarPago lee el pago (con el token del lugar dueño de la cuenta) y aplica su estado a la
// entrada. Idempotente: lo llama el worker por cada aviso de Mercado Pago.
func (s *Servicio) ConciliarPago(ctx context.Context, mpUsuario int64, pagoID int64) error {
	var lugarID string
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `SELECT lugar_id FROM mp_conexiones WHERE mp_usuario = $1`, mpUsuario).Scan(&lugarID)
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return nil // de otra cuenta (Plus) o de un lugar que se desconectó
	}
	if err != nil {
		return err
	}
	token, err := s.tokenDe(ctx, lugarID)
	if err != nil {
		return err
	}
	var p struct {
		ID                int64   `json:"id"`
		Status            string  `json:"status"`
		ExternalReference string  `json:"external_reference"`
		TransactionAmount float64 `json:"transaction_amount"`
	}
	if err := s.MP.JSON(ctx, http.MethodGet, "/v1/payments/"+strconv.FormatInt(pagoID, 10), token, "", nil, &p); err != nil {
		return err
	}
	return s.aplicar(ctx, p.ExternalReference, p.ID, p.Status, int64(p.TransactionAmount*100+0.5))
}

func (s *Servicio) aplicar(ctx context.Context, ref string, pagoID int64, estado string, pagado int64) error {
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var id, planID, usuarioID, actual string
		var cantidad int16
		var total int64
		err := tx.QueryRow(ctx, `SELECT id, plan_id, usuario_id, estado, cantidad, lugar_centavos + cargo_centavos
			FROM entradas WHERE referencia = $1 FOR UPDATE`, ref).Scan(&id, &planID, &usuarioID, &actual, &cantidad, &total)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		switch estado {
		case "approved":
			if actual == "pagada" {
				return nil
			}
			if pagado < total {
				return fmt.Errorf("lugares: pago %d de %d por menos de lo debido", pagoID, total)
			}
			if _, err := tx.Exec(ctx, `UPDATE entradas SET estado = 'pagada', mp_pago = $2, pagada_en = now(), actualizada_en = now() WHERE id = $1`, id, pagoID); err != nil {
				return err
			}
			if _, err := tx.Exec(ctx, `INSERT INTO solicitudes (plan_id, usuario_id, cantidad, estado, respondida_en)
				VALUES ($1, $2, $3, 'aceptada', now()) ON CONFLICT (plan_id, usuario_id) DO UPDATE SET
				cantidad = excluded.cantidad, estado = 'aceptada', respondida_en = now()`, planID, usuarioID, cantidad); err != nil {
				return err
			}
			return outbox.Publicar(ctx, tx, "entrada.pagada", map[string]string{"entradaId": id})
		case "refunded", "charged_back", "cancelled":
			if actual != "pagada" && actual != "pendiente" {
				return nil
			}
			nuevo := "cancelada"
			if actual == "pagada" {
				nuevo = "reembolsada"
			}
			if _, err := tx.Exec(ctx, `UPDATE entradas SET estado = $2, mp_pago = $3, actualizada_en = now() WHERE id = $1`, id, nuevo, pagoID); err != nil {
				return err
			}
			if err := wallet.Actualizado(ctx, tx, "e-"+id); err != nil {
				return err
			}
			_, err := tx.Exec(ctx, `UPDATE solicitudes SET estado = 'cancelada', respondida_en = now()
				WHERE plan_id = $1 AND usuario_id = $2 AND estado = 'aceptada'`, planID, usuarioID)
			return err
		}
		return nil
	})
}

// Validacion es lo que ve el lugar al validar un código en la puerta.
type Validacion struct {
	Nombre    string `json:"nombre"`
	Cantidad  int16  `json:"cantidad"`
	Plan      string `json:"plan"`
	Saldo     int32  `json:"saldoCentavos"`
	Beneficio string `json:"beneficio,omitempty"`
	// Ya había entrado (se validó antes).
	Repetida bool `json:"repetida"`
}

// Validar el código de una entrada (TK-4821) en la puerta: hace el check-in y desbloquea el beneficio.
func (s *Servicio) Validar(ctx context.Context, usuarioID, lugarID, codigo string) (Validacion, error) {
	codigo = strings.ToUpper(strings.TrimSpace(codigo))
	if !strings.HasPrefix(codigo, "TK-") {
		codigo = "TK-" + codigo
	}
	var v Validacion
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		var id, planID, persona string
		var validada *time.Time
		var empieza time.Time
		err := tx.QueryRow(ctx, `SELECT e.id, e.plan_id, e.usuario_id, u.nombre, e.cantidad, p.titulo, e.saldo_centavos, p.beneficio,
				e.validada_en, p.empieza_en
			FROM entradas e JOIN planes p ON p.id = e.plan_id JOIN usuarios u ON u.id = e.usuario_id
			WHERE e.codigo = $1 AND p.lugar_id::text = $2 AND e.estado = 'pagada' FOR UPDATE OF e`, codigo, lugarID).
			Scan(&id, &planID, &persona, &v.Nombre, &v.Cantidad, &v.Plan, &v.Saldo, &v.Beneficio, &validada, &empieza)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.NoEncontrado("Ese código no es de una entrada de tu lugar.")
		}
		if err != nil {
			return err
		}
		if d := empieza.Sub(s.ahora()); d > 12*time.Hour || d < -24*time.Hour {
			return problema.Conflicto("otro_dia", "Esa entrada es para otro día.")
		}
		if validada != nil {
			v.Repetida = true
			return nil
		}
		if _, err := tx.Exec(ctx, `UPDATE entradas SET validada_en = now(), actualizada_en = now() WHERE id = $1`, id); err != nil {
			return err
		}
		if err := wallet.Actualizado(ctx, tx, "e-"+id); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `UPDATE solicitudes SET checkin_en = coalesce(checkin_en, now()), checkin_metodo = coalesce(checkin_metodo, 'qr')
			WHERE plan_id = $1 AND usuario_id = $2`, planID, persona)
		return err
	})
	return v, err
}

// CobroPlan es una fila de "Cobros".
type CobroPlan struct {
	PlanID          string    `json:"planId"`
	Titulo          string    `json:"titulo"`
	EmpiezaEn       time.Time `json:"empiezaEn"`
	Entradas        int       `json:"entradas"`
	CobradoCentavos int64     `json:"cobradoCentavos"`
	Estado          string    `json:"estado"` // en_venta | cobrado
}

// Cobros del lugar, por plan.
func (s *Servicio) Cobros(ctx context.Context, usuarioID, lugarID string) ([]CobroPlan, error) {
	out := []CobroPlan{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		rows, err := tx.Query(ctx, `
			SELECT p.id, p.titulo, p.empieza_en, coalesce(sum(e.cantidad), 0)::integer, coalesce(sum(e.lugar_centavos), 0)::bigint,
				CASE WHEN p.empieza_en + make_interval(mins => p.duracion_min) < now() THEN 'cobrado' ELSE 'en_venta' END
			FROM planes p LEFT JOIN entradas e ON e.plan_id = p.id AND e.estado = 'pagada'
			WHERE p.lugar_id::text = $1 AND p.cobro <> 'gratis'
			GROUP BY p.id ORDER BY p.empieza_en DESC LIMIT 100`, lugarID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var c CobroPlan
			if err := rows.Scan(&c.PlanID, &c.Titulo, &c.EmpiezaEn, &c.Entradas, &c.CobradoCentavos, &c.Estado); err != nil {
				return err
			}
			out = append(out, c)
		}
		return rows.Err()
	})
	return out, err
}

// EntradaMia es una entrada en "Mis pases".
type EntradaMia struct {
	ID        string    `json:"id"`
	Codigo    string    `json:"codigo"`
	PlanID    string    `json:"planId"`
	Titulo    string    `json:"titulo"`
	Lugar     string    `json:"lugar"`
	EmpiezaEn time.Time `json:"empiezaEn"`
	Cantidad  int16     `json:"cantidad"`
	Modo      string    `json:"modo"`
	Estado    string    `json:"estado"`
	Saldo     int32     `json:"saldoCentavos"`
	Beneficio string    `json:"beneficio,omitempty"`
	Validada  bool      `json:"validada"`
}

// MisEntradas: las entradas pagadas de la persona (las que vienen primero).
func (s *Servicio) MisEntradas(ctx context.Context, usuarioID string) ([]EntradaMia, error) {
	out := []EntradaMia{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `
			SELECT e.id, e.codigo, p.id, p.titulo, coalesce(l.nombre, ''), p.empieza_en, e.cantidad, e.modo, e.estado,
				e.saldo_centavos, p.beneficio, e.validada_en IS NOT NULL
			FROM entradas e JOIN planes p ON p.id = e.plan_id LEFT JOIN lugares l ON l.id = p.lugar_id
			WHERE e.usuario_id = $1 AND e.estado IN ('pagada', 'reembolsada')
				AND p.empieza_en > now() - interval '1 day'
			ORDER BY p.empieza_en`, usuarioID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var e EntradaMia
			if err := rows.Scan(&e.ID, &e.Codigo, &e.PlanID, &e.Titulo, &e.Lugar, &e.EmpiezaEn, &e.Cantidad, &e.Modo,
				&e.Estado, &e.Saldo, &e.Beneficio, &e.Validada); err != nil {
				return err
			}
			out = append(out, e)
		}
		return rows.Err()
	})
	return out, err
}
