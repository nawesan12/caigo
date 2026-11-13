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
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/cifrado"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/clerk"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/config"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/correo"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/google"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/tiendas"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/vetados"
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
		Secreto: cfg.Secreto, Sitio: cfg.Sitio, APIPublica: cfg.APIPublica, Archivos: cfg.Archivos,
		MinAndroid: cfg.MinAndroid, MinIos: cfg.MinIos}
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
	// El código del teléfono sale por mail (no hay proveedor de SMS). Sin SMTP, simulado.
	if cfg.SMTP != "" {
		deps.Correo = correo.Elegir(cfg.SMTP, cfg.SMTPUsuario, cfg.SMTPClave, cfg.CorreoDe)
	}
	if !cfg.Desarrollo() && !cfg.CorreoReal() {
		slog.Warn("sin SMTP (CAIGO_SMTP, CAIGO_SMTP_USUARIO, CAIGO_SMTP_CLAVE): el código del teléfono vuelve en la respuesta")
	}
	if cfg.ClaveCifrado != "" {
		if deps.Cifra, err = cifrado.Nuevo(cfg.ClaveCifrado); err != nil {
			return err
		}
	}
	deps.ClaveVerificacion = claveVerificacion(cfg.ClaveVerificacion)
	// La misma clave, derivada para otra cosa: las huellas de la lista de vetados (sin ella, el alta y
	// el DNI no se cruzan con la lista y suspender no anota huellas; lo deja dicho claveVerificacion).
	if cfg.ClaveVerificacion != "" {
		deps.Vetados, _ = vetados.NuevaClave(cfg.ClaveVerificacion)
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

// claveVerificacion lee CAIGO_CLAVE_VERIFICACION. Si falta o está mal, la API arranca igual (el
// resto de Caigo no depende de ella) y lo deja en el log: subir o ver una selfie o un DNI responde
// 503 sin_clave_verificacion hasta que se cargue.
func claveVerificacion(b64 string) *archivos.Clave {
	if b64 == "" {
		slog.Error("sin CAIGO_CLAVE_VERIFICACION: no se pueden subir ni ver selfies ni DNI")
		return nil
	}
	c, err := archivos.NuevaClave(b64)
	if err != nil {
		slog.Error("CAIGO_CLAVE_VERIFICACION inválida: no se pueden subir ni ver selfies ni DNI", "error", err)
		return nil
	}
	return c
}
