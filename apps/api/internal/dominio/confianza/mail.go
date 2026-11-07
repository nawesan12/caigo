package confianza

import (
	"bytes"
	"html/template"
	"strings"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/correo"
)

// mailCodigo es el mail con el código de verificación del teléfono: la marca arriba, el código
// grande en lima, cuánto dura y qué hacer si no lo pidió. Tablas y estilos en línea (lo que
// entienden todos los clientes de mail); Geist si el cliente la carga, si no, la del sistema.
var mailCodigo = template.Must(template.New("codigo").Parse(`<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<meta name="color-scheme" content="light only"><title>Tu código de Caigo</title>
<link href="https://fonts.googleapis.com/css2?family=Geist:wght@400;600;800&display=swap" rel="stylesheet"></head>
<body style="margin:0;padding:0;background:#f4f1fb;font-family:Geist,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#17141f">
<div style="display:none;max-height:0;overflow:hidden">{{.Codigo}} es tu código de Caigo. Vence en 10 minutos.</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f1fb;padding:32px 12px"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:24px;overflow:hidden">
<tr><td style="background:#6B4EFF;padding:22px 28px;font-size:24px;font-weight:800;letter-spacing:-0.5px;color:#ffffff">Caigo<span style="color:#C8F36B">.</span></td></tr>
<tr><td style="padding:28px 28px 8px;font-size:22px;font-weight:800;line-height:1.25;letter-spacing:-0.4px">Tu código para confirmar el teléfono</td></tr>
<tr><td style="padding:0 28px 20px;font-size:16px;line-height:1.5;color:#5d5870">Escribilo en la app para seguir con el alta.</td></tr>
<tr><td style="padding:0 28px 20px"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#C8F36B;border-radius:18px">
<tr><td align="center" style="padding:22px 12px;font-size:40px;font-weight:800;letter-spacing:10px;color:#17141f;font-family:Geist,ui-monospace,Menlo,Consolas,monospace">{{.Codigo}}</td></tr>
</table></td></tr>
<tr><td style="padding:0 28px 8px;font-size:15px;font-weight:600;line-height:1.5">Vence en 10 minutos.</td></tr>
<tr><td style="padding:0 28px 28px;font-size:14px;line-height:1.5;color:#5d5870">Si no fuiste vos, ignorá este mail. No se lo pases a nadie: nadie de Caigo te lo va a pedir.</td></tr>
</table>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px"><tr><td style="font-size:12px;color:#8f8a9e;padding:16px 8px;text-align:center">
Caigo · Hacé planes. Conocé gente.
</td></tr></table>
</td></tr></table></body></html>`))

// mensajeCodigo arma el mail del código, en HTML y en texto.
func mensajeCodigo(para, codigo string) correo.Mensaje {
	var b bytes.Buffer
	_ = mailCodigo.Execute(&b, struct{ Codigo string }{codigo})
	texto := strings.Join([]string{
		"Caigo",
		"",
		"Tu código para confirmar el teléfono:",
		"",
		codigo,
		"",
		"Vence en 10 minutos.",
		"",
		"Si no fuiste vos, ignorá este mail. No se lo pases a nadie: nadie de Caigo te lo va a pedir.",
	}, "\n")
	return correo.Mensaje{Para: para, Asunto: "Tu código de Caigo es " + codigo, HTML: b.String(), Texto: texto}
}

// enmascarar deja ver la primera letra y el dominio: nicolas@gmail.com → n•••@gmail.com.
func enmascarar(email string) string {
	i := strings.LastIndex(email, "@")
	if i <= 0 {
		return "•••"
	}
	primera := []rune(email[:i])[0]
	return string(primera) + "•••" + email[i:]
}
