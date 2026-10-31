// Comando api: el servidor HTTP de Caigo para el VPS.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/chat"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/wallet"
	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/cifrado"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/clerk"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/config"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/google"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/tiendas"
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
		exigir = append(exigir, "ClerkSecreto", "ClerkPublicable", "Secreto")
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

	hub := &chat.Hub{Servicio: &chat.Servicio{App: app}}
	go hub.Escuchar(ctx)
	deps := servidor.Dependencias{App: app, Version: version, Hub: hub, Origenes: cfg.Hosts(),
		Secreto: cfg.Secreto, Sitio: cfg.Sitio, APIPublica: cfg.APIPublica, Archivos: cfg.Archivos}
	cuentaPlay, err := google.Leer(cfg.GooglePlayCuenta)
	if err != nil {
		return err
	}
	deps.Tiendas = &plus.Tiendas{Apple: &tiendas.Apple{Bundle: cfg.AppleBundle}, Sitio: cfg.Sitio}
	if cuentaPlay != nil {
		deps.Tiendas.Play = &tiendas.Google{Cuenta: cuentaPlay, Paquete: cfg.GooglePaquete}
	}
	if cfg.MPAppID != "" || cfg.MPToken != "" {
		deps.Tiendas.MP = &mercadopago.Config{AppID: cfg.MPAppID, Secreto: cfg.MPSecreto, TokenPlataforma: cfg.MPToken,
			SecretoWebhook: cfg.MPSecretoWebhook}
	}
	deps.ClaveGoogle = cfg.GoogleClaveWebhook
	ap, err := wallet.AppleDesde(cfg.WalletPassType, cfg.APNsTeamID, cfg.WalletCert, cfg.WalletClave,
		strings.TrimRight(cfg.APIPublica, "/")+"/v1/wallet", []byte(cfg.Secreto))
	if err != nil {
		return err
	}
	deps.Wallet = &wallet.Servicio{Apple: ap}
	if cuentaPlay != nil && cfg.GoogleWalletEmisor != "" {
		deps.Wallet.Google = &wallet.Google{Cuenta: cuentaPlay, Emisor: cfg.GoogleWalletEmisor, Origenes: []string{cfg.Sitio}}
	}
	deps.Panel = cfg.Panel
	if cfg.ClaveCifrado != "" {
		if deps.Cifra, err = cifrado.Nuevo(cfg.ClaveCifrado); err != nil {
			return err
		}
	}
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
		// Sin WriteTimeout: cortaría los WebSocket del tiempo real. Cada escritura tiene su propio
		// tope en el hub y los handlers comunes responden rápido.
		IdleTimeout: 90 * time.Second,
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
