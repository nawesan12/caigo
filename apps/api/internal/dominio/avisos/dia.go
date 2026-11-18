package avisos

import (
	"context"
	"encoding/json"
	"html/template"
	"strings"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/correo"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
)

// todoBienPregunta: primer paso de «¿Todo bien?». Llegó la hora y no hizo check-in: se le pregunta a
// la persona, que tiene planes.TodoBienEspera para tocar «Estoy bien» antes de que avisemos.
func (e *Enviador) todoBienPregunta(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PlanID, UsuarioID string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil || d.PlanID == "" {
		return nil
	}
	var titulo, contactoNombre, contacto string
	var llego bool
	if err := e.leer(ctx, `SELECT p.titulo, t.contacto_nombre, t.contacto, s.checkin_en IS NOT NULL
		FROM todo_bien t JOIN planes p ON p.id = t.plan_id
		JOIN solicitudes s ON s.plan_id = t.plan_id AND s.usuario_id = t.usuario_id
		WHERE t.plan_id::text = $1 AND t.usuario_id::text = $2 AND t.avisado_en IS NULL`, []any{d.PlanID, d.UsuarioID},
		&titulo, &contactoNombre, &contacto, &llego); err != nil {
		return ignorarSiNoEsta(err)
	}
	if llego {
		return nil
	}
	cuerpo := "Hacé check-in o tocá «Estoy bien». Si no, en 10 min te recordamos avisarle a " + quienEs(contactoNombre, contacto) + "."
	if (planes.Contacto{Contacto: contacto}).EsMail() {
		cuerpo = "Hacé check-in o tocá «Estoy bien». Si no, en 10 min le escribimos a " + quienEs(contactoNombre, contacto) + "."
	}
	return e.Mandar(ctx, d.UsuarioID, Aviso{Titulo: "¿Llegaste a " + titulo + "?", Cuerpo: cuerpo,
		Datos: datosPlan(d.PlanID, "dia"), Colapso: "todo-bien-" + d.PlanID})
}

func quienEs(nombre, contacto string) string {
	if nombre != "" {
		return nombre
	}
	return contacto
}

// todoBien: segundo paso. Siguió sin check-in ni «Estoy bien» después de la pregunta. Si el contacto es
// un mail (y la dirección no pidió la baja ni llegó al tope), le escribimos con el link en vivo; si es un
// teléfono, no tenemos cómo mandarle un SMS: le avisamos a la persona para que le escriba con un toque.
func (e *Enviador) todoBien(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PlanID, UsuarioID string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil || d.PlanID == "" {
		return nil
	}
	var nombre, titulo, lugar, contactoNombre, contacto string
	var llego bool
	p, err := e.plan(ctx, d.PlanID)
	if err != nil {
		return ignorarSiNoEsta(err)
	}
	if err := e.leer(ctx, `SELECT u.nombre, p.titulo, coalesce(nullif(p.lugar_nombre, ''), p.zona), t.contacto_nombre, t.contacto,
			s.checkin_en IS NOT NULL
		FROM todo_bien t JOIN planes p ON p.id = t.plan_id JOIN usuarios u ON u.id = t.usuario_id
		JOIN solicitudes s ON s.plan_id = t.plan_id AND s.usuario_id = t.usuario_id
		WHERE t.plan_id::text = $1 AND t.usuario_id::text = $2`, []any{d.PlanID, d.UsuarioID},
		&nombre, &titulo, &lugar, &contactoNombre, &contacto, &llego); err != nil {
		return ignorarSiNoEsta(err) // lo apagó o ya se borró
	}
	if llego {
		return nil
	}
	quien := quienEs(contactoNombre, contacto)
	c := planes.Contacto{Nombre: contactoNombre, Contacto: contacto}
	if c.EsMail() && e.Correo != nil {
		var puede bool
		if err := e.App.EnSistema(ctx, func(tx pgx.Tx) error {
			var err error
			puede, err = planes.ReservarMail(ctx, tx, d.PlanID, d.UsuarioID, contacto, e.ahora())
			return err
		}); err != nil {
			return err
		}
		if puede {
			link, baja := "", ""
			if e.Planes != nil {
				link, _ = e.Planes.LinkEnVivo(ctx, d.UsuarioID, d.PlanID)
				baja = e.Planes.LinkBaja(contacto)
			}
			m := mailTodoBien(primerNombre(nombre), titulo, lugar, plus.EnArgentina(p.EmpiezaEn).Format("15:04"), link, baja, contacto)
			if err := e.Correo.Enviar(ctx, m); err != nil {
				_ = e.App.EnSistema(ctx, func(tx pgx.Tx) error { return planes.SoltarMail(ctx, tx, d.PlanID, d.UsuarioID) })
				return err
			}
			return e.Mandar(ctx, d.UsuarioID, Aviso{Titulo: "¿Todo bien?", Cuerpo: "No hiciste check-in en " + titulo +
				". Le escribimos a " + quien + ".", Datos: datosPlan(d.PlanID, "dia"), Colapso: "todo-bien-" + d.PlanID})
		}
	}
	return e.Mandar(ctx, d.UsuarioID, Aviso{Titulo: "¿Todo bien?", Cuerpo: "No hiciste check-in en " + titulo +
		". Avisale a " + quien + " con un toque.", Datos: datosPlan(d.PlanID, "dia"), Colapso: "todo-bien-" + d.PlanID})
}

func primerNombre(n string) string {
	p, _, _ := strings.Cut(strings.TrimSpace(n), " ")
	return p
}

var plantillaTodoBien = template.Must(template.New("todo-bien").Parse(`<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<meta name="color-scheme" content="light only"><title>¿Todo bien con {{.Nombre}}?</title></head>
<body style="margin:0;padding:0;background:#f4f1fb;font-family:Geist,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#17141f">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f1fb;padding:32px 12px"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:24px;overflow:hidden">
<tr><td style="background:#6B4EFF;padding:22px 28px;font-size:24px;font-weight:800;letter-spacing:-0.5px;color:#ffffff">Caigo<span style="color:#C8F36B">.</span></td></tr>
<tr><td style="padding:28px 28px 8px;font-size:22px;font-weight:800;line-height:1.25;letter-spacing:-0.4px">¿Todo bien con {{.Nombre}}?</td></tr>
<tr><td style="padding:0 28px 16px;font-size:16px;line-height:1.5;color:#5d5870">Hola. {{.Nombre}} te eligió como contacto de confianza en Caigo. Hoy a las {{.Hora}} tenía «{{.Plan}}» en {{.Lugar}} y todavía no marcó que llegó.</td></tr>
<tr><td style="padding:0 28px 16px;font-size:16px;line-height:1.5;color:#5d5870">Probá escribirle o llamar. Capaz se olvidó de marcarlo.</td></tr>
{{if .Link}}<tr><td style="padding:0 28px 20px"><a href="{{.Link}}" style="display:inline-block;background:#C8F36B;color:#17141f;font-weight:800;font-size:16px;text-decoration:none;padding:14px 22px;border-radius:16px">Ver dónde es el plan</a></td></tr>{{end}}
<tr><td style="padding:0 28px 28px;font-size:14px;line-height:1.5;color:#5d5870">Si no responde y te preocupa, llamá al 911.</td></tr>
</table>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px"><tr><td style="font-size:12px;color:#8f8a9e;padding:16px 8px;text-align:center">
Te llega porque {{.Nombre}} activó «¿Todo bien?» en un plan. No guardamos tu mail para otra cosa.{{if .Baja}}<br><a href="{{.Baja}}" style="color:#8f8a9e">No quiero recibir estos avisos</a>{{end}}
</td></tr></table>
</td></tr></table></body></html>`))

// mailTodoBien: el mail al contacto de confianza.
// El saludo no lleva el nombre que la persona le puso al contacto (texto libre: no se modera).
func mailTodoBien(nombre, plan, lugar, hora, link, baja, para string) correo.Mensaje {
	var b strings.Builder
	_ = plantillaTodoBien.Execute(&b, map[string]string{"Nombre": nombre, "Plan": plan,
		"Lugar": lugar, "Hora": hora, "Link": link, "Baja": baja})
	texto := []string{
		"¿Todo bien con " + nombre + "?",
		"",
		nombre + " te eligió como contacto de confianza en Caigo. Hoy a las " + hora + " tenía «" + plan + "» en " + lugar +
			" y todavía no marcó que llegó.",
		"",
		"Probá escribirle o llamar. Capaz se olvidó de marcarlo.",
	}
	if link != "" {
		texto = append(texto, "", "Dónde es el plan: "+link)
	}
	texto = append(texto, "", "Si no responde y te preocupa, llamá al 911.")
	if baja != "" {
		texto = append(texto, "", "No quiero recibir estos avisos: "+baja)
	}
	return correo.Mensaje{Para: para, Asunto: "¿Todo bien con " + nombre + "?", HTML: b.String(), Texto: strings.Join(texto, "\n"), Baja: baja}
}
