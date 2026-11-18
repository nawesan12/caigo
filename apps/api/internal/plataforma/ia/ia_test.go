package ia_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/anthropics/anthropic-sdk-go"
	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/config"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/ia"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/ia/iafalso"
)

func TestCosto(t *testing.T) {
	// 1M de entrada (0,10) + 1M de salida (0,50) + 1M leído del caché (0,01) + 1M escrito (0,125).
	u := anthropic.Usage{InputTokens: 1e6, OutputTokens: 1e6, CacheReadInputTokens: 1e6, CacheCreationInputTokens: 1e6}
	if got := ia.Costo(u); got != 735000 {
		t.Fatalf("costo: %d micro-dólares", got)
	}
	if got := ia.Costo(anthropic.Usage{InputTokens: 1}); got != 1 {
		t.Fatalf("redondea para arriba: %d", got)
	}
}

// base abre la base de desarrollo (sin ella, se saltea) y usa un día propio para no pisar otras corridas.
func base(t *testing.T) (app, migrador *db.Pool) {
	t.Helper()
	t.Setenv("CAIGO_ENTORNO", "desarrollo")
	cfg, err := config.Leer()
	if err != nil {
		t.Fatal(err)
	}
	ctx := context.Background()
	if err := db.Migrar(ctx, cfg.BaseMigrador, cfg.ClaveVerificacion); err != nil {
		t.Skipf("sin base de desarrollo: %v", err)
	}
	app, err = db.Abrir(ctx, cfg.BaseApp)
	if err != nil {
		t.Skipf("sin base de desarrollo: %v", err)
	}
	t.Cleanup(app.Close)
	// La app no puede borrar de ia_uso (sólo suma): la limpieza va con el dueño del esquema.
	mig, err := db.Abrir(ctx, cfg.BaseMigrador)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(mig.Close)
	return app, mig
}

func TestMedicionYTopes(t *testing.T) {
	app, mig := base(t)
	ctx := context.Background()
	// Un día lejano y al azar por corrida: la tabla es de toda la base.
	dia := time.Date(2090, time.Month(1+time.Now().Nanosecond()%12), 1+time.Now().Nanosecond()%27, 15, 0, 0, 0, time.UTC)
	limpiar := func() {
		if err := mig.EnSistema(ctx, func(tx pgx.Tx) error {
			_, err := tx.Exec(ctx, `DELETE FROM ia_uso WHERE dia >= '2090-01-01'`)
			return err
		}); err != nil {
			t.Fatal(err)
		}
	}
	limpiar()
	t.Cleanup(limpiar)
	f := &iafalso.Falso{Fijo: map[string]string{"decision": "ok"},
		Uso: anthropic.Usage{InputTokens: 100, CacheReadInputTokens: 900, OutputTokens: 30}}
	c := &ia.Cliente{Llamador: f, App: app, TopeDia: 60, TopeMes: 1000, Reloj: func() time.Time { return dia }}
	p := ia.Pedido{Ruta: "prueba.ruta", System: "s", Texto: "t", Esquema: map[string]any{"type": "object"}, MaxTokens: 200}
	var out map[string]string
	// Cada pedido: 100×0,1 + 900×0,01 + 30×0,5 = 34 micro-dólares.
	if _, err := c.Clasificar(ctx, p, &out); err != nil || out["decision"] != "ok" {
		t.Fatalf("primero: %v %v", out, err)
	}
	if _, err := c.Clasificar(ctx, p, &out); err != nil {
		t.Fatalf("segundo: %v", err)
	}
	// 68 ≥ 60: el tercero del día no sale (y no llama a la IA).
	if _, err := c.Clasificar(ctx, p, &out); !errors.Is(err, ia.ErrApagada) || f.Cantidad() != 2 {
		t.Fatalf("tope diario: %v, %d pedidos", err, f.Cantidad())
	}
	filas, err := c.DelMes(ctx)
	if err != nil || len(filas) != 1 {
		t.Fatalf("del mes: %+v %v", filas, err)
	}
	if r := filas[0]; r.Ruta != "prueba.ruta" || r.Pedidos != 2 || r.Fallidos != 1 || r.Entrada != 200 ||
		r.CacheLeido != 1800 || r.Salida != 60 || r.CostoMicro != 68 {
		t.Fatalf("medición: %+v", r)
	}
	// Otro día del mismo mes: el diario se reinicia, pero el mensual (1000) se mantiene.
	otro := dia.AddDate(0, 0, 1)
	c.Reloj = func() time.Time { return otro }
	if otro.Month() == dia.Month() {
		if _, err := c.Clasificar(ctx, p, &out); err != nil {
			t.Fatalf("otro día: %v", err)
		}
		c.TopeMes = 100
		if _, err := c.Clasificar(ctx, p, &out); !errors.Is(err, ia.ErrApagada) {
			t.Fatalf("tope mensual: %v", err)
		}
	}
}

