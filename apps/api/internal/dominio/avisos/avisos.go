// Package avisos: los dispositivos de cada persona y los push que salen de los eventos del outbox
// (alguien quiere sumarse, te aceptaron, mensaje nuevo en el chat, privado nuevo). Los manda el
// worker, por APNs al iPhone y por FCM a Android.
package avisos

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"strings"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/apns"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/fcm"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Rutas registra los dispositivos de la persona.
func Rutas(mux *http.ServeMux, app *db.Pool, con func(http.Handler) http.Handler, log *slog.Logger) {
	mux.Handle("PUT /v1/dispositivos", con(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		p, _ := sesion.De(r.Context())
		var d struct {
			Plataforma string `json:"plataforma"`
			Token      string `json:"token"`
			Entorno    string `json:"entorno"`
		}
		if !respuesta.Leer(w, r, &d) {
			return
		}
		if d.Entorno == "" {
			d.Entorno = "produccion"
		}
		if (d.Plataforma != "ios" && d.Plataforma != "android") || len(d.Token) < 8 || len(d.Token) > 4096 ||
			(d.Entorno != "sandbox" && d.Entorno != "produccion") {
			problema.Escribir(w, problema.Invalido("Dispositivo inválido."))
			return
		}
		// El token puede venir de otra persona que usó el mismo teléfono: pasa a esta (como sistema,
		// porque la fila vieja no es de quien pide).
		err := app.EnSistema(r.Context(), func(tx pgx.Tx) error {
			_, err := tx.Exec(r.Context(), `
				INSERT INTO dispositivos (usuario_id, plataforma, token, entorno) VALUES ($1, $2, $3, $4)
				ON CONFLICT (token) DO UPDATE SET usuario_id = excluded.usuario_id, entorno = excluded.entorno, visto_en = now()`,
				p.ID, d.Plataforma, d.Token, d.Entorno)
			return err
		})
		if err != nil {
			respuesta.Error(w, log, err)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})))
	mux.Handle("DELETE /v1/dispositivos/{token}", con(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		p, _ := sesion.De(r.Context())
		err := app.EnUsuario(r.Context(), p.ID, func(tx pgx.Tx) error {
			_, err := tx.Exec(r.Context(), `DELETE FROM dispositivos WHERE token = $1`, r.PathValue("token"))
			return err
		})
		if err != nil {
			respuesta.Error(w, log, err)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})))
}

// Enviador manda los push. APNs o FCM en nil: esa plataforma queda apagada.
type Enviador struct {
	App  *db.Pool
	APNs *apns.Cliente
	FCM  *fcm.Cliente
	Log  *slog.Logger
}

// Aviso es un push para una persona.
type Aviso struct {
	Titulo, Cuerpo string
	// Datos para abrir la pantalla: {"plan": id} o {"privado": id}.
	Datos   map[string]string
	Colapso string
}

// Mandar manda el aviso a todos los dispositivos de la persona. Borra los tokens que ya no sirven.
func (e *Enviador) Mandar(ctx context.Context, usuarioID string, a Aviso) error {
	type disp struct{ Plataforma, Token, Entorno string }
	var ds []disp
	err := e.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT plataforma, token, entorno FROM dispositivos WHERE usuario_id = $1`, usuarioID)
		if err != nil {
			return err
		}
		ds, err = pgx.CollectRows(rows, pgx.RowToStructByPos[disp])
		return err
	})
	if err != nil {
		return err
	}
	var errs []error
	for _, d := range ds {
		var err error
		switch d.Plataforma {
		case "ios":
			aps := map[string]any{"aps": map[string]any{"alert": map[string]string{"title": a.Titulo, "body": a.Cuerpo}, "sound": "default"}}
			for k, v := range a.Datos {
				aps[k] = v
			}
			b, _ := json.Marshal(aps)
			err = e.APNs.Enviar(ctx, apns.Envio{Token: d.Token, Entorno: d.Entorno, Payload: b, Colapso: a.Colapso})
			if errors.Is(err, apns.ErrTokenInvalido) {
				err = e.olvidar(ctx, d.Token)
			}
		case "android":
			err = e.FCM.Enviar(ctx, fcm.Envio{Token: d.Token, Titulo: a.Titulo, Cuerpo: a.Cuerpo, Datos: a.Datos, Colapso: a.Colapso})
			if errors.Is(err, fcm.ErrTokenInvalido) {
				err = e.olvidar(ctx, d.Token)
			}
		}
		if err != nil {
			errs = append(errs, err)
		}
	}
	return errors.Join(errs...)
}

func (e *Enviador) olvidar(ctx context.Context, token string) error {
	return e.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, err := tx.Exec(ctx, `DELETE FROM dispositivos WHERE token = $1`, token)
		return err
	})
}

// Manejadores del outbox que terminan en push.
func (e *Enviador) Manejadores() map[string]outbox.Manejador {
	return map[string]outbox.Manejador{
		"solicitud.nueva":    e.solicitudNueva,
		"solicitud.aceptada": e.solicitudAceptada,
		"mensaje.plan":       e.mensajePlan,
		"privado.nuevo":      e.privadoNuevo,
	}
}

func (e *Enviador) leer(ctx context.Context, q string, args []any, dst ...any) error {
	return e.App.EnSistema(ctx, func(tx pgx.Tx) error { return tx.QueryRow(ctx, q, args...).Scan(dst...) })
}

func (e *Enviador) solicitudNueva(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PlanID, UsuarioID string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var org, titulo, nombre string
	var cantidad int
	if err := e.leer(ctx, `SELECT p.organizador_id, p.titulo, u.nombre, s.cantidad FROM planes p
		JOIN solicitudes s ON s.plan_id = p.id JOIN usuarios u ON u.id = s.usuario_id
		WHERE p.id = $1 AND s.usuario_id = $2`, []any{d.PlanID, d.UsuarioID}, &org, &titulo, &nombre, &cantidad); err != nil {
		return ignorarSiNoEsta(err)
	}
	t := nombre + " quiere sumarse"
	if cantidad > 1 {
		t = fmt.Sprintf("%s y %d más quieren sumarse", nombre, cantidad-1)
	}
	return e.Mandar(ctx, org, Aviso{Titulo: t, Cuerpo: titulo + " · Tocá para responder.",
		Datos: map[string]string{"plan": d.PlanID, "pantalla": "solicitudes"}, Colapso: "sol-" + d.PlanID})
}

func (e *Enviador) solicitudAceptada(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PlanID, UsuarioID string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var titulo, org string
	if err := e.leer(ctx, `SELECT p.titulo, u.nombre FROM planes p JOIN usuarios u ON u.id = p.organizador_id WHERE p.id = $1`,
		[]any{d.PlanID}, &titulo, &org); err != nil {
		return ignorarSiNoEsta(err)
	}
	return e.Mandar(ctx, d.UsuarioID, Aviso{Titulo: "¡Estás adentro!", Cuerpo: org + " te aceptó en " + titulo + ".",
		Datos: map[string]string{"plan": d.PlanID}})
}

func (e *Enviador) mensajePlan(ctx context.Context, ev outbox.Evento) error {
	var d struct {
		MensajeID int64
		PlanID    string
	}
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var autor, nombre, texto, titulo string
	if err := e.leer(ctx, `SELECT m.usuario_id, u.nombre, m.texto, p.titulo FROM mensajes m
		JOIN usuarios u ON u.id = m.usuario_id JOIN planes p ON p.id = m.plan_id WHERE m.id = $1`,
		[]any{d.MensajeID}, &autor, &nombre, &texto, &titulo); err != nil {
		return ignorarSiNoEsta(err)
	}
	var ids []string
	err := e.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT usuario_id FROM solicitudes WHERE plan_id = $1 AND estado = 'aceptada' AND usuario_id <> $2`, d.PlanID, autor)
		if err != nil {
			return err
		}
		ids, err = pgx.CollectRows(rows, pgx.RowTo[string])
		return err
	})
	if err != nil {
		return err
	}
	var errs []error
	for _, u := range ids {
		errs = append(errs, e.Mandar(ctx, u, Aviso{Titulo: titulo, Cuerpo: nombre + ": " + recortar(texto, 140),
			Datos: map[string]string{"plan": d.PlanID, "pantalla": "chat"}, Colapso: "chat-" + d.PlanID}))
	}
	return errors.Join(errs...)
}

func (e *Enviador) privadoNuevo(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PrivadoID int64 }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var de, a, nombre, texto string
	if err := e.leer(ctx, `SELECT p.de_id, p.a_id, u.nombre, p.texto FROM privados p JOIN usuarios u ON u.id = p.de_id WHERE p.id = $1`,
		[]any{d.PrivadoID}, &de, &a, &nombre, &texto); err != nil {
		return ignorarSiNoEsta(err)
	}
	return e.Mandar(ctx, a, Aviso{Titulo: nombre, Cuerpo: recortar(texto, 140),
		Datos: map[string]string{"privado": de}, Colapso: "priv-" + de})
}

func ignorarSiNoEsta(err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return nil // se borró en el medio: no hay a quién avisar
	}
	return err
}

func recortar(s string, n int) string {
	r := []rune(strings.TrimSpace(s))
	if len(r) <= n {
		return string(r)
	}
	return string(r[:n-1]) + "…"
}
