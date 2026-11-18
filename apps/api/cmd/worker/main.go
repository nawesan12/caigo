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
	"github.com/nawesan12/caigo/apps/api/internal/dominio/logros"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/lugares"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/moderacion"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/wallet"
	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/apns"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/cifrado"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/config"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/correo"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/fcm"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/firma"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/ia"
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
	// «¿Todo bien?»: el mail al contacto de confianza sale por el mismo SMTP; el link en vivo, con la firma de la API.
	env := &avisos.Enviador{App: app, APNs: ap, FCM: fc, Correo: correo.Elegir(cfg.SMTP, cfg.SMTPUsuario, cfg.SMTPClave, cfg.CorreoDe),
		Planes: &planes.Servicio{App: app, Firma: firma.Firmador{Clave: []byte(cfg.Secreto)}, Sitio: cfg.Sitio}}
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
	// Los mails del botón de arrepentimiento (acuse a quien lo pidió y aviso a hola@), por el mismo
	// SMTP que el código del teléfono. Sin SMTP quedan en el log.
	if cfg.SMTP == "" {
		slog.Warn("sin SMTP (CAIGO_SMTP): los mails del arrepentimiento quedan en el log")
	}
	for k, m := range confianza.ManejadoresCorreo(app, correo.Elegir(cfg.SMTP, cfg.SMTPUsuario, cfg.SMTPClave, cfg.CorreoDe)) {
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
	// Moderación: los mensajes dudosos se revisan con la IA acá, fuera del pedido (el chat no espera).
	cli := ia.Nuevo(cfg.AnthropicClave, app, cfg.IATopeMes, cfg.IATopeDia, nil)
	if cli == nil {
		slog.Warn("sin ANTHROPIC_API_KEY: los mensajes dudosos van directo a la cola del equipo")
	}
	manejadores[moderacion.EventoRevisar] = moderacion.Nuevo(app, cli, nil).Manejador
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
		// «Traé a alguien»: la semana de Plus cuando el referido ya fue a su primer plan.
		if n, err := logros.PremiarReferidos(ctx, app, time.Now()); err != nil && ctx.Err() == nil {
			slog.Warn("referidos", "error", err)
		} else if n > 0 {
			slog.Info("referidos premiados", "cantidad", n)
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
// purgadas y de los intereses (confianza.Plazos), las fotos sueltas (archivos.BarrerFotos) y las fotos
// de las verificaciones de DNI resueltas (confianza.PurgarFotosDNI).
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
		// La cola de la moderación: lo resuelto se borra al año (guarda una copia del texto).
		`DELETE FROM moderaciones WHERE resuelta_en < now() - interval '1 year'`,
		// Quién del equipo miró un DNI o una selfie: 2 años, como los reportes.
		`DELETE FROM accesos_verificacion WHERE visto_en < now() - interval '2 years'`,
	}
	consultas = append(consultas, confianza.Plazos...)
	// El promedio de caritas de quien organiza, una vez por día (planes.CalcularCaritas).
	consultas = append(consultas, planes.CalcularCaritas)
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
		fotosDePlanes(ctx, app, carpetaArchivos)
		fotosDNI(ctx, app, carpetaArchivos)
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

// fotosDNI borra lo que quedó de las fotos de verificaciones de DNI ya resueltas (las que se resuelven
// ahora se borran en el momento), las fotos del DNI sueltas y los archivos de dni/ y selfie/ sin fila
// en la base (confianza.PurgarFotosDNI). Idempotente.
func fotosDNI(ctx context.Context, app *db.Pool, carpeta string) {
	inf, err := confianza.PurgarFotosDNI(ctx, app, archivos.Disco{Raiz: carpeta})
	if err != nil && ctx.Err() == nil {
		slog.Warn("fotos del DNI", "error", err)
	}
	if inf.Resueltas+inf.Sueltas+inf.Disco > 0 {
		slog.Info("fotos del DNI borradas", "resueltas", inf.Resueltas, "sueltas", inf.Sueltas, "disco", inf.Disco)
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

// fotosDePlanes borra las fotos de los planes que terminaron hace más de 6 meses (y las subidas
// cortadas). Sin la carpeta montada no borra: el evento no podría sacar el archivo del disco.
func fotosDePlanes(ctx context.Context, app *db.Pool, carpeta string) {
	if st, err := os.Stat(carpeta); err != nil || !st.IsDir() {
		return
	}
	err := app.EnSistema(ctx, func(tx pgx.Tx) error {
		n, err := planes.PurgarFotos(ctx, tx)
		if n > 0 {
			slog.Info("fotos de planes borradas", "cantidad", n)
		}
		return err
	})
	if err != nil && ctx.Err() == nil {
		slog.Warn("fotos de planes", "error", err)
	}
}
