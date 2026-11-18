// Package ia habla con Claude (Anthropic) para lo que el motor propio no resuelve. En Caigo es sólo
// la moderación: clasificar un texto dudoso con salida estructurada. Sigue el diseño común de las 4
// apps (docs-ia/asistente-ia.md): un modelo (Haiku 5.5, esfuerzo low), system fijo con breakpoint de
// caché, medición y topes en la base (ia_uso), timeouts cortos y nunca dentro de una transacción.
package ia

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"math"
	"net/http"
	"time"

	"github.com/anthropics/anthropic-sdk-go"
	"github.com/anthropics/anthropic-sdk-go/option"
	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
)

// Modelo único de las 4 apps.
const Modelo = "claude-haiku-5-5"

// Precios de Haiku 5.5 (prompts de menos de 100K tokens) en micro-dólares por token: 0,10 / 0,50 USD
// por millón de entrada / salida; leer caché 0,1× y escribirlo (5 min) 1,25× la entrada.
const (
	precioEntrada      = 0.10
	precioSalida       = 0.50
	precioCacheLeido   = 0.01
	precioCacheEscrito = 0.125
)

// Timeout de cada llamada (el diseño común): pasado esto, la IA «no respondió».
const Timeout = 25 * time.Second

var (
	// ErrApagada: sin clave, o se pasó el tope del día o del mes. Lo dudoso va directo al equipo.
	ErrApagada = errors.New("ia apagada")
	// ErrRechazo: el modelo se negó (stop_reason refusal).
	ErrRechazo = errors.New("la ia no quiso responder")
	// ErrRespuesta: la respuesta no vino entera o no es el JSON pedido.
	ErrRespuesta = errors.New("respuesta de la ia inválida")
)

// Llamador es lo único que se usa del SDK: así las pruebas usan uno falso, sin red.
type Llamador interface {
	New(ctx context.Context, params anthropic.MessageNewParams, opts ...option.RequestOption) (*anthropic.Message, error)
}

// Cliente con medición y topes. Los topes son globales de Caigo (su parte del presupuesto de las 4 apps).
type Cliente struct {
	Llamador Llamador
	App      *db.Pool
	// Topes en micro-dólares (0: sin tope).
	TopeMes, TopeDia int64
	Log              *slog.Logger
	// Reloj (tests). nil: time.Now.
	Reloj func() time.Time
}

// Nuevo arma el cliente real con la clave de Anthropic. Sin clave devuelve nil (sin IA).
func Nuevo(clave string, app *db.Pool, topeMesUSD, topeDiaUSD float64, log *slog.Logger) *Cliente {
	if clave == "" {
		return nil
	}
	// El SDK ya reintenta: uno solo, sólo ante 429/5xx/conexión (lo decide él).
	c := anthropic.NewClient(option.WithAPIKey(clave), option.WithMaxRetries(1))
	return &Cliente{Llamador: &c.Messages, App: app, TopeMes: usdAMicro(topeMesUSD), TopeDia: usdAMicro(topeDiaUSD), Log: log}
}

func usdAMicro(usd float64) int64 { return int64(math.Round(usd * 1e6)) }

func (c *Cliente) ahora() time.Time {
	if c.Reloj != nil {
		return c.Reloj()
	}
	return time.Now()
}

func (c *Cliente) log() *slog.Logger {
	if c.Log != nil {
		return c.Log
	}
	return slog.Default()
}

// Pedido de clasificación con salida estructurada.
type Pedido struct {
	// Ruta para medir (p. ej. "moderacion.plan"): una fila de ia_uso por día y ruta.
	Ruta string
	// System fijo de la ruta: sin fechas ni datos, para que el prefijo se lea siempre del caché.
	System string
	// Texto del usuario (lo único variable).
	Texto string
	// Esquema JSON de la respuesta (output_config.format).
	Esquema map[string]any
	// Tope de tokens de salida (clasificación: 200).
	MaxTokens int64
}

// Uso de una llamada.
type Uso struct {
	Entrada, CacheLeido, CacheEscrito, Salida int64
	CostoMicro                                int64
}

// Costo en micro-dólares (redondeado para arriba: mejor sobrestimar contra el tope).
func Costo(u anthropic.Usage) int64 {
	c := float64(u.InputTokens)*precioEntrada + float64(u.OutputTokens)*precioSalida +
		float64(u.CacheReadInputTokens)*precioCacheLeido + float64(u.CacheCreationInputTokens)*precioCacheEscrito
	return int64(math.Ceil(c))
}

// Clasificar manda el texto y decodifica el JSON en out. No llamar dentro de una transacción abierta:
// tarda hasta Timeout y la medición abre la suya. Un cliente nil es «sin IA» (ErrApagada).
func (c *Cliente) Clasificar(ctx context.Context, p Pedido, out any) (Uso, error) {
	if c == nil || c.Llamador == nil {
		return Uso{}, ErrApagada
	}
	if err := c.dentroDelTope(ctx); err != nil {
		_ = c.medir(ctx, p.Ruta, Uso{}, true)
		return Uso{}, err
	}
	ctx, cancel := context.WithTimeout(ctx, Timeout)
	defer cancel()
	params := anthropic.MessageNewParams{
		Model:     Modelo,
		MaxTokens: p.MaxTokens,
		// Breakpoint en el system fijo: todas las clasificaciones de la ruta leen el mismo prefijo.
		System: []anthropic.TextBlockParam{{Text: p.System, CacheControl: anthropic.NewCacheControlEphemeralParam()}},
		Messages: []anthropic.MessageParam{
			anthropic.NewUserMessage(anthropic.NewTextBlock(p.Texto)),
		},
		// Sin pensamiento y esfuerzo low: es una clasificación corta y así entra en MaxTokens. Fijo
		// por ruta (cambiarlo invalida el caché).
		Thinking: anthropic.ThinkingConfigParamUnion{OfDisabled: &anthropic.ThinkingConfigDisabledParam{}},
		OutputConfig: anthropic.OutputConfigParam{
			Effort: anthropic.OutputConfigEffortLow,
			Format: anthropic.JSONOutputFormatParam{Schema: p.Esquema},
		},
	}
	res, err := c.Llamador.New(ctx, params)
	if err != nil {
		_ = c.medir(context.WithoutCancel(ctx), p.Ruta, Uso{}, true)
		return Uso{}, fmt.Errorf("ia: %w", err)
	}
	u := Uso{Entrada: res.Usage.InputTokens, CacheLeido: res.Usage.CacheReadInputTokens,
		CacheEscrito: res.Usage.CacheCreationInputTokens, Salida: res.Usage.OutputTokens, CostoMicro: Costo(res.Usage)}
	falla := error(nil)
	switch res.StopReason {
	case anthropic.StopReasonRefusal:
		falla = ErrRechazo
	case anthropic.StopReasonEndTurn:
		falla = decodificar(res, out)
	default:
		// max_tokens u otra cosa: el JSON vino cortado.
		falla = fmt.Errorf("%w: stop_reason %s", ErrRespuesta, res.StopReason)
	}
	if err := c.medir(context.WithoutCancel(ctx), p.Ruta, u, falla != nil); err != nil {
		c.log().Warn("ia: no se pudo medir el uso", "ruta", p.Ruta, "error", err)
	}
	return u, falla
}

func decodificar(res *anthropic.Message, out any) error {
	for _, b := range res.Content {
		if t, ok := b.AsAny().(anthropic.TextBlock); ok {
			if err := json.Unmarshal([]byte(t.Text), out); err != nil {
				return fmt.Errorf("%w: %v", ErrRespuesta, err)
			}
			return nil
		}
	}
	return fmt.Errorf("%w: sin texto", ErrRespuesta)
}

// Transitorio dice si vale la pena reintentar más tarde (corte, 429, 5xx); el resto no cambia solo.
func Transitorio(err error) bool {
	if err == nil || errors.Is(err, ErrApagada) || errors.Is(err, ErrRechazo) || errors.Is(err, ErrRespuesta) {
		return false
	}
	var ae *anthropic.Error
	if errors.As(err, &ae) {
		return ae.StatusCode == http.StatusTooManyRequests || ae.StatusCode >= 500
	}
	return true // timeout o conexión
}

// zona: el día y el mes de los topes son los de Argentina.
var zona = func() *time.Location {
	z, err := time.LoadLocation("America/Argentina/Buenos_Aires")
	if err != nil {
		return time.FixedZone("ART", -3*3600)
	}
	return z
}()

func (c *Cliente) dia() time.Time {
	t := c.ahora().In(zona)
	return time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, time.UTC)
}

// Gastado devuelve lo gastado hoy y en el mes (micro-dólares).
func (c *Cliente) Gastado(ctx context.Context) (dia, mes int64, err error) {
	hoy := c.dia()
	inicio := time.Date(hoy.Year(), hoy.Month(), 1, 0, 0, 0, 0, time.UTC)
	err = c.App.EnSistema(ctx, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `SELECT coalesce(sum(costo_micro) FILTER (WHERE dia = $1), 0)::bigint,
				coalesce(sum(costo_micro), 0)::bigint
			FROM ia_uso WHERE dia >= $2 AND dia <= $1`, hoy, inicio).Scan(&dia, &mes)
	})
	return dia, mes, err
}

// dentroDelTope: si se pasó el día o el mes, la IA se apaga hasta que cambie el día o el mes. Es una
// medida aproximada (dos pedidos a la vez pueden pasarse por uno): alcanza para cuidar el presupuesto.
func (c *Cliente) dentroDelTope(ctx context.Context) error {
	if c.TopeDia == 0 && c.TopeMes == 0 {
		return nil
	}
	dia, mes, err := c.Gastado(ctx)
	if err != nil {
		return err
	}
	if (c.TopeDia > 0 && dia >= c.TopeDia) || (c.TopeMes > 0 && mes >= c.TopeMes) {
		c.log().Warn("ia: tope alcanzado, queda sólo el filtro propio", "dia_micro", dia, "mes_micro", mes)
		return ErrApagada
	}
	return nil
}

// medir suma la llamada a ia_uso (en su propia transacción corta).
func (c *Cliente) medir(ctx context.Context, ruta string, u Uso, fallo bool) error {
	if c.App == nil {
		return nil
	}
	pedidos, fallidos := 1, 0
	if fallo {
		fallidos = 1
		if u == (Uso{}) {
			pedidos = 0 // no llegó a hacerse (tope o error de red): no cuenta como pedido
		}
	}
	return c.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `INSERT INTO ia_uso AS x (dia, ruta, pedidos, entrada, cache_leido, cache_escrito, salida, costo_micro, fallidos)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
			ON CONFLICT (dia, ruta) DO UPDATE SET pedidos = x.pedidos + excluded.pedidos, entrada = x.entrada + excluded.entrada,
				cache_leido = x.cache_leido + excluded.cache_leido, cache_escrito = x.cache_escrito + excluded.cache_escrito,
				salida = x.salida + excluded.salida, costo_micro = x.costo_micro + excluded.costo_micro,
				fallidos = x.fallidos + excluded.fallidos`,
			c.dia(), ruta, pedidos, u.Entrada, u.CacheLeido, u.CacheEscrito, u.Salida, u.CostoMicro, fallidos)
		return err
	})
}

// Fila de ia_uso para el resumen del mes (caigoctl ia-uso).
type Fila struct {
	Ruta                                      string
	Pedidos, Fallidos                         int
	Entrada, CacheLeido, CacheEscrito, Salida int64
	CostoMicro                                int64
}

// DelMes resume el mes en curso por ruta.
func (c *Cliente) DelMes(ctx context.Context) ([]Fila, error) {
	hoy := c.dia()
	var out []Fila
	err := c.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT ruta, sum(pedidos)::integer, sum(fallidos)::integer, sum(entrada)::bigint,
				sum(cache_leido)::bigint, sum(cache_escrito)::bigint, sum(salida)::bigint, sum(costo_micro)::bigint
			FROM ia_uso WHERE dia >= $1 AND dia <= $2 GROUP BY ruta ORDER BY ruta`, time.Date(hoy.Year(), hoy.Month(), 1, 0, 0, 0, 0, time.UTC), hoy)
		if err != nil {
			return err
		}
		out, err = pgx.CollectRows(rows, pgx.RowToStructByPos[Fila])
		return err
	})
	return out, err
}
