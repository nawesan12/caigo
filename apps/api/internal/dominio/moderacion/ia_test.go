package moderacion

import (
	"context"
	"errors"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/anthropics/anthropic-sdk-go"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/ia"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/ia/iafalso"
)

func TestClasificadorArmaElPedidoYCachea(t *testing.T) {
	f := &iafalso.Falso{Fijo: Clasificacion{Decision: IARevisar, Categoria: "contacto", Motivo: "Publica un teléfono."}}
	c := &Clasificador{IA: &ia.Cliente{Llamador: f}}
	ctx := context.Background()
	got, err := c.Clasificar(ctx, TipoPlan, "Fulbito\n\nllamame al 2235551100")
	if err != nil || got.Decision != IARevisar || got.Categoria != "contacto" {
		t.Fatalf("clasificación: %+v %v", got, err)
	}
	p := f.Pedidos[0]
	if p.Model != ia.Modelo || p.MaxTokens != 200 || p.OutputConfig.Effort != anthropic.OutputConfigEffortLow {
		t.Fatalf("modelo/tokens/esfuerzo: %s %d %s", p.Model, p.MaxTokens, p.OutputConfig.Effort)
	}
	if len(p.System) != 1 || p.System[0].Text != system || p.System[0].CacheControl.Type == "" {
		t.Fatal("el system tiene que ser el fijo, con breakpoint de caché")
	}
	if p.OutputConfig.Format.Schema == nil {
		t.Fatal("sin salida estructurada")
	}
	if len(p.Messages) != 1 || !strings.Contains(p.Messages[0].Content[0].OfText.Text, "2235551100") {
		t.Fatal("el único mensaje es el texto a revisar")
	}
	// El mismo texto (con otras mayúsculas) no vuelve a la IA; otro tipo, sí.
	if _, err := c.Clasificar(ctx, TipoPlan, "FULBITO\n\nllamame al 2235551100"); err != nil || f.Cantidad() != 1 {
		t.Fatalf("caché de respuestas: %d pedidos, %v", f.Cantidad(), err)
	}
	if _, err := c.Clasificar(ctx, TipoMensaje, "Fulbito llamame al 2235551100"); err != nil || f.Cantidad() != 2 {
		t.Fatalf("otro tipo: %d pedidos", f.Cantidad())
	}
	// A los 10 minutos vence.
	c.ahora = func() time.Time { return time.Now().Add(11 * time.Minute) }
	if _, _ = c.Clasificar(ctx, TipoPlan, "Fulbito\n\nllamame al 2235551100"); f.Cantidad() != 3 {
		t.Fatalf("caché vencido: %d pedidos", f.Cantidad())
	}
}

func TestClasificadorErrores(t *testing.T) {
	ctx := context.Background()
	var nada *Clasificador
	if _, err := nada.Clasificar(ctx, TipoMensaje, "x"); !errors.Is(err, ia.ErrApagada) {
		t.Fatalf("sin IA: %v", err)
	}
	f := &iafalso.Falso{Fijo: map[string]string{"decision": "ok"}, Stop: anthropic.StopReasonRefusal}
	c := &Clasificador{IA: &ia.Cliente{Llamador: f}}
	if _, err := c.Clasificar(ctx, TipoMensaje, "x"); !errors.Is(err, ia.ErrRechazo) {
		t.Fatalf("refusal: %v", err)
	}
	f.Stop = anthropic.StopReasonMaxTokens
	if _, err := c.Clasificar(ctx, TipoMensaje, "y"); !errors.Is(err, ia.ErrRespuesta) {
		t.Fatalf("cortada: %v", err)
	}
	f.Stop = ""
	f.Fijo = map[string]string{"decision": "quizas"}
	if _, err := c.Clasificar(ctx, TipoMensaje, "z"); !errors.Is(err, ia.ErrRespuesta) {
		t.Fatalf("decisión inválida: %v", err)
	}
	f.Responder = func(string) (any, error) { return nil, errors.New("conexión cortada") }
	_, err := c.Clasificar(ctx, TipoMensaje, "w")
	if err == nil || !ia.Transitorio(err) {
		t.Fatalf("corte: %v", err)
	}
}

// Un plan dudoso espera a la IA; si no contesta a tiempo se publica para revisar. Uno sano no la llama.
func TestRevisarPlan(t *testing.T) {
	ctx := context.Background()
	f := &iafalso.Falso{Fijo: Clasificacion{Decision: IABloquear, Categoria: "estafa", Motivo: "Pide plata por fuera."}}
	s := &Servicio{IA: &Clasificador{IA: &ia.Cliente{Llamador: f}}}
	if r := s.RevisarPlan(ctx, "Fulbito en Mogotes"); r.Estado != EstadoOK || f.Cantidad() != 0 {
		t.Fatalf("sano: %+v, %d pedidos", r, f.Cantidad())
	}
	if r := s.RevisarPlan(ctx, "Pasá la seña al CBU 123"); r.Estado != Bloquear || r.Origen != OrigenIA {
		t.Fatalf("bloqueado por la IA: %+v", r)
	}
	if r := s.RevisarPlan(ctx, "tengo 15 años"); r.Estado != Bloquear || r.Origen != OrigenFiltro || f.Cantidad() != 1 {
		t.Fatalf("bloqueado por el filtro sin IA: %+v", r)
	}
	lento := &iafalso.Falso{Responder: func(string) (any, error) { time.Sleep(time.Second); return nil, context.DeadlineExceeded }}
	s = &Servicio{IA: &Clasificador{IA: &ia.Cliente{Llamador: lento}}, EsperaPlan: 50 * time.Millisecond}
	if r := s.RevisarPlan(ctx, "pasame tu wsp"); r.Estado != EstadoRevisar || r.Origen != OrigenSinIA || r.Categoria != "contacto" {
		t.Fatalf("sin respuesta: %+v", r)
	}
	var nada *Servicio
	if r := nada.RevisarPlan(ctx, "pasame tu wsp"); r.Estado != EstadoRevisar {
		t.Fatalf("sin servicio: %+v", r)
	}
}

// TestCacheReal manda dos pedidos parecidos a Anthropic y comprueba que el segundo lee el system del
// caché. Se corre a mano (cuesta centavos), nunca en el CI:
// CAIGO_IA_PRUEBA_REAL=1 ANTHROPIC_API_KEY=… go test ./internal/dominio/moderacion -run CacheReal -v
func TestCacheReal(t *testing.T) {
	if os.Getenv("CAIGO_IA_PRUEBA_REAL") != "1" || os.Getenv("ANTHROPIC_API_KEY") == "" {
		t.Skip("sólo a mano, con la clave")
	}
	cli := ia.Nuevo(os.Getenv("ANTHROPIC_API_KEY"), nil, 0, 0, nil)
	ctx := context.Background()
	for i, texto := range []string{"pasame tu wsp así arreglamos", "pasame tu insta así arreglamos"} {
		var out Clasificacion
		pedido := ia.Pedido{Ruta: "prueba", System: system, Texto: texto, Esquema: esquema, MaxTokens: 200}
		u, err := cli.Clasificar(ctx, pedido, &out)
		if err != nil {
			t.Fatal(err)
		}
		t.Logf("pedido %d: %+v → %+v", i+1, u, out)
		if i == 1 && u.CacheLeido == 0 {
			t.Fatal("el segundo pedido no leyó el caché (¿el system pasa los 512 tokens?)")
		}
	}
}
