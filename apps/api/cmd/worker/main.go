// Comando worker: consume el outbox (push por APNs y FCM) y corre las tareas de fondo.
package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/avisos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/apns"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/config"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/fcm"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
)

func main() {
	if err := correr(); err != nil {
		slog.Error("el worker no arrancó", "error", err)
		os.Exit(1)
	}
}

func correr() error {
	cfg, err := config.Leer()
	if err != nil {
		return err
	}
	if err := cfg.Exigir("BaseApp"); err != nil {
		return err
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	app, err := db.Abrir(ctx, cfg.BaseApp)
	if err != nil {
		return err
	}
	defer app.Close()

	ap, err := apns.Desde(cfg.APNsKeyID, cfg.APNsTeamID, cfg.APNsClave, cfg.APNsTopic)
	if err != nil {
		return err
	}
	fc, err := fcm.Desde(cfg.FCMCuenta)
	if err != nil {
		return err
	}
	slog.Info("worker andando", "apns", ap.Activo(), "fcm", fc != nil)
	env := &avisos.Enviador{App: app, APNs: ap, FCM: fc}
	w := &outbox.Worker{Pool: app, Manejadores: env.Manejadores()}
	return w.Correr(ctx)
}
