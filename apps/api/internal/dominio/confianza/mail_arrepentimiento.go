package confianza

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"html/template"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/correo"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
)

// Eventos del outbox con los mails del botón de arrepentimiento.
const (
	// EventoAcuseArrepentimiento: el acuse a quien lo pidió (código, qué dijo que compró y que le
	// respondemos a ese mail).
	EventoAcuseArrepentimiento = "arrepentimiento.acuse"
	// EventoAvisoArrepentimiento: los datos del pedido a la casilla del equipo.
	EventoAvisoArrepentimiento = "arrepentimiento.aviso"
)

// CasillaEquipo es donde le llegan al equipo los pedidos de arrepentimiento (y adonde responde la
// persona).
const CasillaEquipo = "hola@caigo.com.ar"

var mailAcuse = template.Must(template.New("acuse").Parse(`<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<meta name="color-scheme" content="light only"><title>Recibimos tu pedido</title>
<link href="https://fonts.googleapis.com/css2?family=Geist:wght@400;600;800&display=swap" rel="stylesheet"></head>
<body style="margin:0;padding:0;background:#f4f1fb;font-family:Geist,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#17141f">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f1fb;padding:32px 12px"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:24px;overflow:hidden">
<tr><td style="background:#6B4EFF;padding:22px 28px;font-size:24px;font-weight:800;letter-spacing:-0.5px;color:#ffffff">Caigo<span style="color:#C8F36B">.</span></td></tr>
<tr><td style="padding:28px 28px 8px;font-size:22px;font-weight:800;line-height:1.25;letter-spacing:-0.4px">Recibimos tu pedido de arrepentimiento</td></tr>
<tr><td style="padding:0 28px 16px;font-size:16px;line-height:1.5;color:#5d5870">Hola, {{.Nombre}}. Tu número de trámite es:</td></tr>
<tr><td style="padding:0 28px 20px"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#C8F36B;border-radius:18px">
<tr><td align="center" style="padding:18px 12px;font-size:28px;font-weight:800;letter-spacing:4px;color:#17141f">{{.Codigo}}</td></tr>
</table></td></tr>
<tr><td style="padding:0 28px 8px;font-size:15px;font-weight:600;line-height:1.5">Lo que nos contaste que compraste:</td></tr>
<tr><td style="padding:0 28px 20px;font-size:15px;line-height:1.5;color:#5d5870;white-space:pre-wrap">{{.Compra}}</td></tr>
<tr><td style="padding:0 28px 28px;font-size:14px;line-height:1.5;color:#5d5870">Te respondemos a este mail para confirmar la devolución. Si querés sumar algo, respondé este mismo mail.</td></tr>
</table>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px"><tr><td style="font-size:12px;color:#8f8a9e;padding:16px 8px;text-align:center">
Caigo · Hacé planes. Conocé gente.
</td></tr></table>
</td></tr></table></body></html>`))

type datosArrepentimiento struct {
	Codigo, Nombre, Email, Compra string
	Creado                        time.Time
}

// mensajeAcuse: el acuse a quien pidió el arrepentimiento.
func mensajeAcuse(a datosArrepentimiento) correo.Mensaje {
	var b bytes.Buffer
	_ = mailAcuse.Execute(&b, a)
	texto := strings.Join([]string{
		"Caigo",
		"",
		"Recibimos tu pedido de arrepentimiento, " + a.Nombre + ".",
		"",
		"Número de trámite: " + a.Codigo,
		"",
		"Lo que nos contaste que compraste:",
		a.Compra,
		"",
		"Te respondemos a este mail para confirmar la devolución. Si querés sumar algo, respondé este mismo mail.",
	}, "\n")
	return correo.Mensaje{Para: a.Email, Asunto: "Recibimos tu pedido de arrepentimiento (" + a.Codigo + ")",
		HTML: b.String(), Texto: texto, ResponderA: CasillaEquipo}
}

// mensajeAvisoEquipo: el pedido para el equipo (responder a la persona: Responder-A es su mail).
func mensajeAvisoEquipo(a datosArrepentimiento) correo.Mensaje {
	texto := strings.Join([]string{
		"Pedido de arrepentimiento " + a.Codigo,
		"",
		"Nombre: " + a.Nombre,
		"Mail: " + a.Email,
		"Cuándo: " + a.Creado.In(argentina).Format("02/01/2006 15:04") + " (hora de Argentina)",
		"",
		"Qué compró:",
		a.Compra,
		"",
		"Hay que confirmarle la devolución. Respondé este mail (le llega a la persona) y marcalo atendido en caigo.com.ar/equipo.",
	}, "\n")
	return correo.Mensaje{Para: CasillaEquipo, Asunto: "Arrepentimiento " + a.Codigo + " · " + a.Nombre,
		HTML:  "<pre style=\"font-family:Geist,-apple-system,sans-serif;white-space:pre-wrap\">" + template.HTMLEscapeString(texto) + "</pre>",
		Texto: texto, ResponderA: a.Email}
}

var argentina = func() *time.Location {
	if l, err := time.LoadLocation("America/Argentina/Buenos_Aires"); err == nil {
		return l
	}
	return time.FixedZone("ART", -3*60*60)
}()

// ManejadoresCorreo son los manejadores del outbox que mandan los mails del arrepentimiento (el
// worker, con el mismo SMTP que el código del teléfono). Cada mail se marca en la fila antes de salir
// (acuse_en / aviso_en) y se desmarca si falla: un reintento o un evento repetido no lo manda dos
// veces.
func ManejadoresCorreo(app *db.Pool, c correo.Correo) map[string]outbox.Manejador {
	mandar := func(columna string, armar func(datosArrepentimiento) correo.Mensaje) outbox.Manejador {
		return func(ctx context.Context, ev outbox.Evento) error {
			var d struct{ ID string }
			if err := json.Unmarshal(ev.Datos, &d); err != nil || d.ID == "" {
				return nil
			}
			var a datosArrepentimiento
			err := app.EnSistema(ctx, func(tx pgx.Tx) error {
				return tx.QueryRow(ctx, `UPDATE arrepentimientos SET `+columna+` = now() WHERE id::text = $1 AND `+columna+` IS NULL
					RETURNING codigo, nombre, email, compra, creado_en`, d.ID).Scan(&a.Codigo, &a.Nombre, &a.Email, &a.Compra, &a.Creado)
			})
			if errors.Is(err, pgx.ErrNoRows) {
				return nil // ya salió (o no existe)
			}
			if err != nil {
				return err
			}
			if err := c.Enviar(ctx, armar(a)); err != nil {
				_ = app.EnSistema(ctx, func(tx pgx.Tx) error {
					_, err := tx.Exec(ctx, `UPDATE arrepentimientos SET `+columna+` = NULL WHERE id::text = $1`, d.ID)
					return err
				})
				return fmt.Errorf("arrepentimiento %s: %w", a.Codigo, err)
			}
			return nil
		}
	}
	return map[string]outbox.Manejador{
		EventoAcuseArrepentimiento: mandar("acuse_en", mensajeAcuse),
		EventoAvisoArrepentimiento: mandar("aviso_en", mensajeAvisoEquipo),
	}
}
