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
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/planes"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/apns"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/correo"
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
	// Olvidar un dispositivo sin sesión: la app lo llama cuando la sesión venció o se cerró en otro lado,
	// para que el teléfono deje de recibir los avisos de esa cuenta. El token push sólo lo conoce el
	// teléfono, así que alcanza como prueba; borrarlo como mucho apaga los avisos hasta el próximo ingreso.
	mux.HandleFunc("POST /v1/dispositivos/olvidar", func(w http.ResponseWriter, r *http.Request) {
		var d struct {
			Token string `json:"token"`
		}
		if !respuesta.Leer(w, r, &d) {
			return
		}
		if len(d.Token) < 8 || len(d.Token) > 4096 {
			problema.Escribir(w, problema.Invalido("Dispositivo inválido."))
			return
		}
		err := app.EnSistema(r.Context(), func(tx pgx.Tx) error {
			_, err := tx.Exec(r.Context(), `DELETE FROM dispositivos WHERE token = $1`, d.Token)
			return err
		})
		if err != nil {
			respuesta.Error(w, log, err)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})
}

// Enviador manda los push. APNs o FCM en nil: esa plataforma queda apagada.
type Enviador struct {
	App  *db.Pool
	APNs *apns.Cliente
	FCM  *fcm.Cliente
	Log  *slog.Logger
	// Reloj (tests). nil: time.Now.
	Reloj func() time.Time
	// Correo para «¿Todo bien?» (el mail al contacto de confianza). nil: sólo el push a la persona.
	Correo correo.Correo
	// Planes arma el link «en vivo» que va en el mail al contacto (Firma y Sitio). nil: el mail va sin link.
	Planes *planes.Servicio
}

func (e *Enviador) log() *slog.Logger {
	if e.Log != nil {
		return e.Log
	}
	return slog.Default()
}

// Aviso es un push para una persona.
type Aviso struct {
	Titulo, Cuerpo string
	// Datos para abrir la pantalla: {"plan": id} o {"privado": id}.
	Datos   map[string]string
	Colapso string
}

// Mandar manda el aviso a todos los dispositivos de la persona. Borra los tokens que ya no sirven. Si
// un dispositivo falla, no se reintenta todo el evento (los demás ya lo recibieron): su envío queda en
// el outbox como aviso.dispositivo, que reintenta sólo ese.
func (e *Enviador) Mandar(ctx context.Context, usuarioID string, a Aviso) error {
	var ds []dispositivo
	err := e.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT plataforma, token, entorno FROM dispositivos WHERE usuario_id = $1`, usuarioID)
		if err != nil {
			return err
		}
		ds, err = pgx.CollectRows(rows, pgx.RowToStructByPos[dispositivo])
		return err
	})
	if err != nil {
		return err
	}
	var errs []error
	for _, d := range ds {
		if err := e.mandarA(ctx, d, a); err != nil {
			e.log().Warn("avisos: un dispositivo falló, se reintenta aparte", "plataforma", d.Plataforma, "error", err)
			errs = append(errs, e.App.EnSistema(ctx, func(tx pgx.Tx) error {
				return outbox.Publicar(ctx, tx, eventoDispositivo, envioPendiente{Dispositivo: d, Aviso: a})
			}))
		}
	}
	return errors.Join(errs...)
}

// eventoDispositivo: el aviso a un solo dispositivo que falló en el primer envío.
const eventoDispositivo = "aviso.dispositivo"

type dispositivo struct{ Plataforma, Token, Entorno string }

type envioPendiente struct {
	Dispositivo dispositivo
	Aviso       Aviso
}

// mandarA manda el aviso a un dispositivo. Un token que ya no sirve se borra (y no es error).
func (e *Enviador) mandarA(ctx context.Context, d dispositivo, a Aviso) error {
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
	return err
}

// reintentarDispositivo: el manejador de aviso.dispositivo (si el token ya no está, no se manda).
func (e *Enviador) reintentarDispositivo(ctx context.Context, ev outbox.Evento) error {
	var p envioPendiente
	if err := json.Unmarshal(ev.Datos, &p); err != nil || p.Dispositivo.Token == "" {
		return nil
	}
	var sigue bool
	if err := e.leer(ctx, `SELECT EXISTS (SELECT 1 FROM dispositivos WHERE token = $1)`, []any{p.Dispositivo.Token}, &sigue); err != nil {
		return err
	}
	if !sigue {
		return nil
	}
	return e.mandarA(ctx, p.Dispositivo, p.Aviso)
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
		"solicitud.sumada":   e.solicitudSumada,
		"solicitud.salio":    e.solicitudSalio,
		"solicitud.aceptada": e.solicitudAceptada,
		"mensaje.plan":       e.mensajePlan,
		"privado.nuevo":      e.privadoNuevo,
		"interes.nuevo":      e.interesNuevo,
		"invitacion.nueva":   e.invitacionNueva,
		"entrada.pagada":     e.entradaPagada,
		// Planes (contrato del 31/10).
		"plan.cancelado":    e.planCancelado,
		"plan.cambiado":     e.planCambiado,
		"plan.recordatorio": e.planRecordatorio,
		"plan.repetimos":    e.planRepetimos,
		"plan.publicado":    e.planPublicado,
		// El día del plan y después (informe del 18/11).
		"plan.como_estuvo":      e.planComoEstuvo,
		planes.EventoLugarLibre: e.lugarLibre,
		planes.EventoTodoBien:   e.todoBien,
		"semana.aviso":          e.semanaAviso,
		"solicitud.rechazada":   e.solicitudFuera(false),
		"solicitud.sacada":      e.solicitudFuera(true),
		// Al equipo: un pedido de arrepentimiento (hay que responder en el día).
		"arrepentimiento.nuevo": e.arrepentimientoNuevo,
		// Al equipo: un lugar pidió placas Caigo Tap.
		"placas.pedido": e.placasPedido,
		// Moderación: al autor, que se ocultó algo suyo; al equipo, lo urgente (posible menor de edad).
		"moderacion.oculto":  e.moderacionOculto,
		"moderacion.urgente": e.moderacionUrgente,
		// El reintento de un aviso a un solo dispositivo.
		eventoDispositivo: e.reintentarDispositivo,
	}
}

// textoModerado: lo que dice el push de un mensaje según su moderación. Uno que la IA todavía está
// revisando avisa sin el texto (si después se oculta, el texto no quedó en ninguna pantalla bloqueada);
// uno ya oculto no avisa.
func textoModerado(texto, estado string) (string, bool) {
	switch estado {
	case "oculto":
		return "", false
	case "pendiente":
		return "Te mandó un mensaje.", true
	}
	return recortar(texto, 140), true
}

// moderacionOculto le avisa al autor que se ocultó un mensaje o un plan suyo.
func (e *Enviador) moderacionOculto(ctx context.Context, ev outbox.Evento) error {
	var d struct {
		UsuarioID string `json:"usuarioId"`
		Tipo      string `json:"tipo"`
		PlanID    string `json:"planId"`
		OtroID    string `json:"otroId"`
		Titulo    string `json:"titulo"`
	}
	if err := json.Unmarshal(ev.Datos, &d); err != nil || d.UsuarioID == "" {
		return nil
	}
	a := Aviso{Titulo: "Ocultamos un mensaje tuyo", Cuerpo: "Iba contra las normas de Caigo. Si creés que es un error, escribinos."}
	switch d.Tipo {
	case "plan":
		a.Titulo = "Ocultamos tu plan"
		a.Cuerpo = "«" + recortar(d.Titulo, 60) + "» iba contra las normas de Caigo: nadie más lo ve."
		a.Datos = map[string]string{"plan": d.PlanID}
	case "mensaje":
		a.Datos = map[string]string{"plan": d.PlanID, "pantalla": "chat"}
	case "privado":
		a.Datos = map[string]string{"privado": d.OtroID}
	}
	return e.Mandar(ctx, d.UsuarioID, a)
}

// moderacionUrgente: el filtro frenó a alguien que dice ser menor de edad (Caigo es +18).
func (e *Enviador) moderacionUrgente(ctx context.Context, _ outbox.Evento) error {
	return e.alEquipo(ctx, Aviso{Titulo: "Moderación: posible menor de edad",
		Cuerpo: "Revisalo en caigo.com.ar/equipo › Moderación.", Colapso: "moderacion-urgente"})
}

func (e *Enviador) arrepentimientoNuevo(ctx context.Context, ev outbox.Evento) error {
	var d struct{ Codigo string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	return e.alEquipo(ctx, Aviso{Titulo: "Pedido de arrepentimiento " + d.Codigo,
		Cuerpo: "Revisalo en caigo.com.ar/equipo.", Colapso: "arr-" + d.Codigo})
}

// placasPedido: un lugar pidió placas Caigo Tap (hay que prepararlas y coordinar el envío).
func (e *Enviador) placasPedido(ctx context.Context, ev outbox.Evento) error {
	var d struct{ ID string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var lugar, tipo string
	var cantidad int
	if err := e.leer(ctx, `SELECT l.nombre, p.cantidad, p.tipo FROM pedidos_placas p JOIN lugares l ON l.id = p.lugar_id
		WHERE p.id::text = $1`, []any{d.ID}, &lugar, &cantidad, &tipo); err != nil {
		return ignorarSiNoEsta(err)
	}
	cuantas := "1 placa"
	if cantidad != 1 {
		cuantas = fmt.Sprintf("%d placas", cantidad)
	}
	if tipo == "ntag424" {
		cuantas += " NTAG 424"
	}
	return e.alEquipo(ctx, Aviso{Titulo: lugar + " pidió " + cuantas,
		Cuerpo: "Revisalo en caigo.com.ar/equipo.", Colapso: "placas-" + d.ID})
}

// alEquipo manda el aviso a todas las personas del equipo.
func (e *Enviador) alEquipo(ctx context.Context, a Aviso) error {
	var equipo []string
	err := e.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT id FROM usuarios WHERE equipo AND baja_en IS NULL`)
		if err != nil {
			return err
		}
		equipo, err = pgx.CollectRows(rows, pgx.RowTo[string])
		return err
	})
	if err != nil {
		return err
	}
	for _, id := range equipo {
		if err := e.Mandar(ctx, id, a); err != nil {
			return err
		}
	}
	return nil
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

// solicitudSumada: alguien entró directo a un plan con ingreso libre (no hubo nada que responder).
func (e *Enviador) solicitudSumada(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PlanID, UsuarioID string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var org, titulo, nombre, estado string
	var cantidad, sumados, total int
	if err := e.leer(ctx, `SELECT p.organizador_id, p.titulo, u.nombre, s.cantidad, s.estado,
			(SELECT coalesce(sum(x.cantidad), 0)::integer FROM solicitudes x WHERE x.plan_id = p.id AND x.estado = 'aceptada'), p.total
		FROM planes p JOIN solicitudes s ON s.plan_id = p.id JOIN usuarios u ON u.id = s.usuario_id
		WHERE p.id = $1 AND s.usuario_id = $2 AND p.cancelado_en IS NULL`, []any{d.PlanID, d.UsuarioID},
		&org, &titulo, &nombre, &cantidad, &estado, &sumados, &total); err != nil {
		return ignorarSiNoEsta(err)
	}
	// Si ya se bajó (o lo sacaron) antes de que saliera el aviso, no hay nada que contar.
	if estado != "aceptada" {
		return nil
	}
	t := nombre + " se sumó"
	if cantidad > 1 {
		t = fmt.Sprintf("%s y %d más se sumaron", nombre, cantidad-1)
	}
	cuerpo := fmt.Sprintf("%s · Somos %d de %d.", titulo, sumados, total)
	if sumados >= total {
		cuerpo = titulo + " · ¡Plan completo!"
	}
	return e.Mandar(ctx, org, Aviso{Titulo: t, Cuerpo: cuerpo, Datos: map[string]string{"plan": d.PlanID}, Colapso: "sum-" + d.PlanID})
}

// solicitudSalio: alguien que estaba adentro se bajó; quien organiza se entera de que hay lugar.
func (e *Enviador) solicitudSalio(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PlanID, UsuarioID string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var org, titulo, nombre string
	if err := e.leer(ctx, `SELECT p.organizador_id, p.titulo, u.nombre FROM planes p JOIN usuarios u ON u.id = $2
		WHERE p.id = $1 AND p.cancelado_en IS NULL`, []any{d.PlanID, d.UsuarioID}, &org, &titulo, &nombre); err != nil {
		return ignorarSiNoEsta(err)
	}
	// Primero la lista de espera (el lugar queda guardado para el siguiente); un reintento no avisa dos veces.
	if err := e.liberar(ctx, d.PlanID); err != nil {
		return err
	}
	return e.Mandar(ctx, org, Aviso{Titulo: nombre + " se bajó", Cuerpo: titulo + " · Se liberó un lugar.",
		Datos: map[string]string{"plan": d.PlanID}, Colapso: "sum-" + d.PlanID})
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
	var autor, nombre, texto, titulo, estado string
	if err := e.leer(ctx, `SELECT m.usuario_id, u.nombre, m.texto, p.titulo, m.moderacion FROM mensajes m
		JOIN usuarios u ON u.id = m.usuario_id JOIN planes p ON p.id = m.plan_id WHERE m.id = $1`,
		[]any{d.MensajeID}, &autor, &nombre, &texto, &titulo, &estado); err != nil {
		return ignorarSiNoEsta(err)
	}
	texto, sale := textoModerado(texto, estado)
	if !sale {
		return nil
	}
	var ids []string
	err := e.App.EnSistema(ctx, func(tx pgx.Tx) error {
		// A quien tiene un bloqueo con el autor no le llega (tampoco lo ve en el chat).
		rows, err := tx.Query(ctx, `SELECT s.usuario_id FROM solicitudes s WHERE s.plan_id = $1 AND s.estado = 'aceptada'
			AND s.usuario_id <> $2 AND NOT EXISTS (SELECT 1 FROM bloqueos b WHERE (b.usuario_id = s.usuario_id AND b.bloqueado_id = $2)
				OR (b.usuario_id = $2 AND b.bloqueado_id = s.usuario_id))`, d.PlanID, autor)
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
		cuerpo := nombre + ": " + texto
		if estado == "pendiente" {
			cuerpo = nombre + " escribió en el chat."
		}
		errs = append(errs, e.Mandar(ctx, u, Aviso{Titulo: titulo, Cuerpo: cuerpo,
			Datos: map[string]string{"plan": d.PlanID, "pantalla": "chat"}, Colapso: "chat-" + d.PlanID}))
	}
	return errors.Join(errs...)
}

func (e *Enviador) privadoNuevo(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PrivadoID int64 }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var de, a, nombre, texto, estado string
	if err := e.leer(ctx, `SELECT p.de_id, p.a_id, u.nombre, p.texto, p.moderacion FROM privados p JOIN usuarios u ON u.id = p.de_id WHERE p.id = $1`,
		[]any{d.PrivadoID}, &de, &a, &nombre, &texto, &estado); err != nil {
		return ignorarSiNoEsta(err)
	}
	texto, sale := textoModerado(texto, estado)
	if !sale {
		return nil
	}
	return e.Mandar(ctx, a, Aviso{Titulo: nombre, Cuerpo: texto,
		Datos: map[string]string{"privado": de}, Colapso: "priv-" + de})
}

// interesNuevo: en Citas, si es mutuo se avisa a los dos; si no, un aviso sin nombre (se ve con Plus).
func (e *Enviador) interesNuevo(ctx context.Context, ev outbox.Evento) error {
	var d struct {
		De, A, Modo string
		Mutuo       bool
	}
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var de, a string
	if err := e.leer(ctx, `SELECT (SELECT nombre FROM usuarios WHERE id = $1), (SELECT nombre FROM usuarios WHERE id = $2)`,
		[]any{d.De, d.A}, &de, &a); err != nil {
		return ignorarSiNoEsta(err)
	}
	// Mutuo (en Citas o en Gente, donde los dos se sumaron): a los dos, con el nombre y al privado. El
	// aviso genérico de «Recibidos» llevaba a una lista donde esa persona ya no está.
	if d.Mutuo {
		cuerpo := "Ya pueden hablar."
		if d.Modo == "gente" {
			cuerpo = "Se sumaron a su gente. Ya pueden hablar."
		}
		return errors.Join(
			e.Mandar(ctx, d.A, Aviso{Titulo: "¡Es mutuo con " + de + "!", Cuerpo: cuerpo, Datos: map[string]string{"privado": d.De}}),
			e.Mandar(ctx, d.De, Aviso{Titulo: "¡Es mutuo con " + a + "!", Cuerpo: cuerpo, Datos: map[string]string{"privado": d.A}}),
		)
	}
	t := "Alguien te sumó a su gente"
	if d.Modo == "citas" {
		t = "Le interesás a alguien"
	}
	return e.Mandar(ctx, d.A, Aviso{Titulo: t, Cuerpo: "Entrá a ver quién es.", Datos: map[string]string{"pantalla": "recibidos", "modo": d.Modo},
		Colapso: "recibidos-" + d.Modo})
}

func (e *Enviador) invitacionNueva(ctx context.Context, ev outbox.Evento) error {
	var d struct{ PlanID, De, A string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var de, titulo string
	if err := e.leer(ctx, `SELECT u.nombre, p.titulo FROM usuarios u, planes p WHERE u.id = $1 AND p.id = $2`,
		[]any{d.De, d.PlanID}, &de, &titulo); err != nil {
		return ignorarSiNoEsta(err)
	}
	return e.Mandar(ctx, d.A, Aviso{Titulo: de + " te invita", Cuerpo: titulo + ". ¿Te prendés?",
		Datos: map[string]string{"plan": d.PlanID, "pantalla": "invitaciones"}})
}

func (e *Enviador) entradaPagada(ctx context.Context, ev outbox.Evento) error {
	var d struct{ EntradaID string }
	if err := json.Unmarshal(ev.Datos, &d); err != nil {
		return nil
	}
	var usuario, codigo, titulo string
	if err := e.leer(ctx, `SELECT e.usuario_id, e.codigo, p.titulo FROM entradas e JOIN planes p ON p.id = e.plan_id WHERE e.id = $1`,
		[]any{d.EntradaID}, &usuario, &codigo, &titulo); err != nil {
		return ignorarSiNoEsta(err)
	}
	return e.Mandar(ctx, usuario, Aviso{Titulo: "Tenés tu entrada", Cuerpo: titulo + " · " + codigo + ". Sumala a tu Wallet.",
		Datos: map[string]string{"pantalla": "pases"}})
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
