// Comando api: el servidor HTTP de Caigo para el VPS.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/clerk"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/config"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
)

// version la pone el build: -ldflags "-X main.version=<commit>".
var version = "dev"

func main() {
	if err := correr(); err != nil {
		slog.Error("la api no arrancó", "error", err)
		os.Exit(1)
	}
}

func correr() error {
	cfg, err := config.Leer()
	if err != nil {
		return err
	}
	exigir := []string{"BaseApp"}
	if !cfg.Desarrollo() {
		exigir = append(exigir, "ClerkSecreto", "ClerkPublicable")
	}
	if err := cfg.Exigir(exigir...); err != nil {
		return err
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	app, err := db.Abrir(ctx, cfg.BaseApp)
	if err != nil {
		return err
	}
	defer app.Close()

	deps := servidor.Dependencias{App: app, Version: version}
	if cfg.ClerkSecreto != "" {
		c, err := clerk.Nuevo(cfg.ClerkSecreto, cfg.ClerkPublicable, cfg.ClerkOrigenes)
		if err != nil {
			return err
		}
		deps.Clerk = c
	} else {
		slog.Warn("sin Clerk: ningún pedido con sesión va a pasar")
	}
	srv := &http.Server{
		Addr:              cfg.Direccion,
		Handler:           servidor.Nuevo(deps),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       90 * time.Second,
	}
	errs := make(chan error, 1)
	go func() {
		slog.Info("api escuchando", "direccion", cfg.Direccion, "entorno", cfg.Entorno, "version", version)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errs <- err
		}
	}()
	select {
	case err := <-errs:
		return err
	case <-ctx.Done():
	}
	apagar, cancelar := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancelar()
	return srv.Shutdown(apagar)
}
