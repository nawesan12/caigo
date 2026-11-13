// Comando worker: consume el outbox (push por APNs y FCM) y corre las tareas de fondo.
package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/avisos"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/confianza"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/lugares"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/wallet"
	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/apns"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/cifrado"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/config"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/fcm"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/vetados"
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
	manejadores := env.Manejadores()
	// Pagos de entradas: se concilian con el token del lugar.
	if cfg.MPAppID != "" && cfg.ClaveCifrado != "" {
		cifra, err := cifrado.Nuevo(cfg.ClaveCifrado)
		if err != nil {
			return err
		}
		ls := &lugares.Servicio{App: app, Cifra: cifra, MP: &mercadopago.Config{AppID: cfg.MPAppID, Secreto: cfg.MPSecreto}}
		for k, m := range ls.Manejadores() {
			manejadores[k] = m
		}
	}
	// Avisos de pases de Apple Wallet actualizados.
	wa, err := wallet.AppleDesde(cfg.WalletPassType, cfg.APNsTeamID, cfg.WalletCert, cfg.WalletClave, "", nil)
	if err != nil {
		return err
	}
	for k, m := range (&wallet.Servicio{App: app, Apple: wa}).Manejadores() {
		manejadores[k] = m
	}
	// Las fotos que se sacan de un perfil, un plan o un lugar se borran del disco acá.
	disco := archivos.Disco{Raiz: cfg.Archivos}
	manejadores[archivos.EventoBorrar] = func(ctx context.Context, ev outbox.Evento) error { return disco.ManejadorBorrar(ctx, ev.Datos) }
	// La clave de las huellas de los vetados (de CAIGO_CLAVE_VERIFICACION): la purga de una cuenta
	// suspendida las anota si faltan. Sin ella, esas cuentas esperan.
	var veto *vetados.Clave
	if cfg.ClaveVerificacion != "" {
		if veto, err = vetados.NuevaClave(cfg.ClaveVerificacion); err != nil {
			slog.Error("CAIGO_CLAVE_VERIFICACION inválida: la purga no purga cuentas suspendidas sin huellas", "error", err)
		}
	} else {
		slog.Warn("sin CAIGO_CLAVE_VERIFICACION: la purga no purga cuentas suspendidas sin huellas")
	}
	w := &outbox.Worker{Pool: app, Manejadores: manejadores}
	go limpiar(ctx, app, cfg.Archivos, veto)
	go programados(ctx, app)
	return w.Correr(ctx)
}

// programados corre cada minuto lo que depende de la hora: recordatorios, check-in abierto,
// "¿Repetimos?", borrar ubicaciones de más de 24 h y la próxima fecha de los planes recurrentes.
// Todo es idempotente (avisos_enviados y el último plan de cada serie): otra vuelta no repite nada.
func programados(ctx context.Context, app *db.Pool) {
	t := time.NewTicker(time.Minute)
	defer t.Stop()
	av := &avisos.Programados{App: app}
	ps := &planes.Servicio{App: app}
	for {
		if _, err := av.Pasada(ctx); err != nil && ctx.Err() == nil {
			slog.Warn("avisos programados", "error", err)
		}
		if n, err := ps.Recurrencia(ctx); err != nil && ctx.Err() == nil {
			slog.Warn("recurrencia", "error", err)
		} else if n > 0 {
			slog.Info("recurrencia: planes creados", "cantidad", n)
		}
		select {
		case <-ctx.Done():
			return
		case <-t.C:
		}
	}
}

// limpiar borra cada hora lo que ya no sirve: topes viejos, sesiones vencidas, estados de OAuth
// usados o vencidos, códigos de teléfono vencidos, eventos del outbox publicados hace más de 30 días y
// los accesos del equipo a DNI y selfies de más de 2 años; los plazos de lo que queda de las cuentas
// purgadas y de los intereses (confianza.Plazos), y las fotos sueltas (archivos.BarrerFotos).
// Y purga los datos personales de las cuentas borradas hace más de 30 días (confianza.Purgar).
func limpiar(ctx context.Context, app *db.Pool, carpetaArchivos string, veto *vetados.Clave) {
	t := time.NewTicker(time.Hour)
	defer t.Stop()
	consultas := []string{
		`DELETE FROM intentos WHERE desde < now() - interval '2 days'`,
		`DELETE FROM sesiones WHERE vence_en < now()`,
		`DELETE FROM mp_estados WHERE vence_en < now() - interval '1 day'`,
		`DELETE FROM telefono_codigos WHERE vence_en < now() - interval '1 day'`,
		`DELETE FROM outbox WHERE publicado_en < now() - interval '30 days'`,
		// Política de privacidad: reportes cerrados, 2 años.
		`DELETE FROM reportes WHERE estado <> 'abierto' AND creado_en < now() - interval '2 years'`,
		// Quién del equipo miró un DNI o una selfie: 2 años, como los reportes.
		`DELETE FROM accesos_verificacion WHERE visto_en < now() - interval '2 years'`,
	}
	consultas = append(consultas, confianza.Plazos...)
	for {
		// Cada una por separado: si una falla, las demás se hacen igual.
		for _, q := range consultas {
			err := app.EnSistema(ctx, func(tx pgx.Tx) error {
				_, err := tx.Exec(ctx, q)
				return err
			})
			if err != nil && ctx.Err() == nil {
				slog.Warn("limpieza fallida", "consulta", q, "error", err)
			}
		}
		barrerFotos(ctx, app, carpetaArchivos)
		purgar(ctx, app, carpetaArchivos, veto)
		select {
		case <-ctx.Done():
			return
		case <-t.C:
		}
	}
}

// barrerFotos borra las fotos que ya no usa nadie (de la base ahora, del disco con archivo.borrar).
// Sin la carpeta montada no barre: el evento no podría borrar el archivo.
func barrerFotos(ctx context.Context, app *db.Pool, carpeta string) {
	if st, err := os.Stat(carpeta); err != nil || !st.IsDir() {
		return
	}
	err := app.EnSistema(ctx, func(tx pgx.Tx) error {
		n, err := archivos.BarrerFotos(ctx, tx)
		if n > 0 {
			slog.Info("fotos sueltas borradas", "cantidad", n)
		}
		return err
	})
	if err != nil && ctx.Err() == nil {
		slog.Warn("fotos sueltas", "error", err)
	}
}

// purgar corre la purga de las cuentas borradas. Necesita la carpeta de archivos (las fotos, la
// selfie y el DNI se borran del disco; los cifrados no necesitan la clave para borrarse): si el worker no la tiene montada, no purga nada (mejor tarde
// que dejar fotos huérfanas en el disco).
func purgar(ctx context.Context, app *db.Pool, carpeta string, veto *vetados.Clave) {
	if st, err := os.Stat(carpeta); err != nil || !st.IsDir() {
		slog.Warn("purga de cuentas: sin la carpeta de archivos, no se purga", "carpeta", carpeta)
		return
	}
	n, err := confianza.Purgar(ctx, app, archivos.Disco{Raiz: carpeta}, veto, time.Now().AddDate(0, 0, -confianza.DiasParaPurgar))
	if err != nil && ctx.Err() == nil {
		slog.Warn("purga de cuentas", "error", err)
	}
	if n > 0 {
		slog.Info("cuentas purgadas", "cantidad", n)
	}
}
