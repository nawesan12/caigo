package wallet

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/db"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/respuesta"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/sesion"
)

// Servicio arma los pases desde la base.
type Servicio struct {
	App    *db.Pool
	Apple  *Apple
	Google *Google
}

var argentina = func() *time.Location {
	l, err := time.LoadLocation("America/Argentina/Buenos_Aires")
	if err != nil {
		return time.FixedZone("ART", -3*3600)
	}
	return l
}()

// pesos: 560000 → "$5.600".
func pesos(centavos int64) string {
	s := strconv.FormatInt(centavos/100, 10)
	var b strings.Builder
	for i, r := range s {
		if i > 0 && (len(s)-i)%3 == 0 {
			b.WriteByte('.')
		}
		b.WriteRune(r)
	}
	return "$" + b.String()
}

// abreviar "Martina Gómez" → "Martina G."
func abreviar(n string) string {
	partes := strings.Fields(n)
	if len(partes) < 2 {
		return n
	}
	return partes[0] + " " + string([]rune(partes[len(partes)-1])[:1]) + "."
}

// entrada arma el pase de una entrada. dueno vacío: como sistema (web service de Apple).
func (s *Servicio) entrada(ctx context.Context, dueno, entradaID string) (Pase, error) {
	var p Pase
	var titulo, lugar, codigo, modo, estado, nombre, beneficio, usuario string
	var cantidad int16
	var saldo int32
	var cuando, cambiado time.Time
	var lat, lng float64
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `SELECT p.titulo, coalesce(l.nombre, p.lugar_nombre), e.codigo, e.modo, e.estado, u.nombre,
				p.beneficio, e.usuario_id, e.cantidad, e.saldo_centavos, p.empieza_en, e.actualizada_en, p.lat, p.lng
			FROM entradas e JOIN planes p ON p.id = e.plan_id JOIN usuarios u ON u.id = e.usuario_id
			LEFT JOIN lugares l ON l.id = p.lugar_id WHERE e.id::text = $1 AND e.estado IN ('pagada', 'reembolsada')`, entradaID).
			Scan(&titulo, &lugar, &codigo, &modo, &estado, &nombre, &beneficio, &usuario, &cantidad, &saldo, &cuando, &cambiado, &lat, &lng)
	})
	if errors.Is(err, pgx.ErrNoRows) || (err == nil && dueno != "" && dueno != usuario) {
		return p, problema.NoEncontrado("Esa entrada no existe.")
	}
	if err != nil {
		return p, err
	}
	local := cuando.In(argentina)
	tipo := "Pagada"
	if modo == "sena" {
		tipo = "Señada"
	}
	p = Pase{Serie: "e-" + entradaID, Tipo: "entrada", Titulo: titulo, Lugar: lugar,
		Primarios:   []Campo{{"evento", strings.ToUpper(dia(local)) + " · " + local.Format("15:04"), titulo}},
		Secundarios: []Campo{{"lugar", "LUGAR", lugar}, {"entrada", "ENTRADA", fmt.Sprintf("%d · %s", cantidad, tipo)}},
		Auxiliares:  []Campo{{"titular", "TITULAR", abreviar(nombre)}},
		Codigo:      codigo, TextoCodigo: codigo + " · Mostralo o apoyá en la placa",
		Cuando: cuando, Lat: lat, Lng: lng, Cerca: "Ya estás cerca: apoyá el celu en la puerta",
		Anulado: estado == "reembolsada", Cambiado: cambiado}
	if saldo > 0 {
		p.Auxiliares = append(p.Auxiliares, Campo{"saldo", "EN EL LUGAR", pesos(int64(saldo))})
	}
	if beneficio != "" {
		p.Dorso = append(p.Dorso, Campo{"beneficio", "CON CHECK-IN", beneficio})
	}
	return p, nil
}

func dia(t time.Time) string {
	hoy := time.Now().In(argentina)
	switch {
	case t.Format(time.DateOnly) == hoy.Format(time.DateOnly):
		return "Hoy"
	case t.Format(time.DateOnly) == hoy.AddDate(0, 0, 1).Format(time.DateOnly):
		return "Mañana"
	}
	dias := []string{"Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"}
	return fmt.Sprintf("%s %d", dias[t.Weekday()], t.Day())
}

// socio arma el pase de socio (serie s-<lugar>_<usuario>).
func (s *Servicio) socio(ctx context.Context, dueno, lugarID string) (Pase, error) {
	var p Pase
	var lugar, beneficio, nombre string
	var visitas, meta, pendientes int16
	var lat, lng float64
	var cambiado time.Time
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `SELECT l.nombre, l.socio_beneficio, u.nombre, s.visitas, l.socio_visitas, s.ganados - s.canjeados,
				l.lat, l.lng, s.actualizado_en
			FROM socios s JOIN lugares l ON l.id = s.lugar_id JOIN usuarios u ON u.id = s.usuario_id
			WHERE s.lugar_id::text = $1 AND s.usuario_id::text = $2 AND l.socio_activo`, lugarID, dueno).
			Scan(&lugar, &beneficio, &nombre, &visitas, &meta, &pendientes, &lat, &lng, &cambiado)
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return p, problema.NoEncontrado("Todavía no tenés pase de socio en ese lugar.")
	}
	if err != nil {
		return p, err
	}
	p = Pase{Serie: "s-" + lugarID + "_" + dueno, Tipo: "socio", Titulo: lugar + " · Socio", Lugar: lugar,
		Primarios:   []Campo{{"visitas", "VISITAS", fmt.Sprintf("%d de %d", visitas, meta)}},
		Secundarios: []Campo{{"beneficio", "PRÓXIMO BENEFICIO", beneficio}},
		Auxiliares:  []Campo{{"socio", "SOCIO", abreviar(nombre)}},
		Dorso:       []Campo{{"como", "CÓMO SUMAR", "Apoyá el celu en la placa Caigo Tap del lugar para sumar una visita."}},
		Lat:         lat, Lng: lng, Cerca: "Apoyá en la placa para sumar visita", Cambiado: cambiado}
	if pendientes > 0 {
		p.Secundarios = append(p.Secundarios, Campo{"canjear", "PARA CANJEAR", fmt.Sprintf("%d en la barra", pendientes)})
	}
	return p, nil
}

func (s *Servicio) porSerie(ctx context.Context, serie string) (Pase, error) {
	switch {
	case strings.HasPrefix(serie, "e-"):
		return s.entrada(ctx, "", strings.TrimPrefix(serie, "e-"))
	case strings.HasPrefix(serie, "s-"):
		lugar, usuario, ok := strings.Cut(strings.TrimPrefix(serie, "s-"), "_")
		if ok {
			return s.socio(ctx, usuario, lugar)
		}
	}
	return Pase{}, problema.NoEncontrado("Ese pase no existe.")
}

// Rutas de los pases y del web service de Apple Wallet.
func Rutas(mux *http.ServeMux, s *Servicio, con func(http.Handler) http.Handler, log *slog.Logger) {
	mal := func(w http.ResponseWriter, err error) { respuesta.Error(w, log, err) }
	entregar := func(w http.ResponseWriter, r *http.Request, p Pase, err error) {
		if err != nil {
			mal(w, err)
			return
		}
		if r.PathValue("wallet") == "google" {
			link, err := s.Google.Link(p)
			if err != nil {
				problema.Escribir(w, problema.NoProcesable("sin_wallet", "Google Wallet todavía no está disponible."))
				return
			}
			respuesta.JSON(w, http.StatusOK, map[string]string{"link": link})
			return
		}
		b, err := s.Apple.PKPass(p)
		if err != nil {
			problema.Escribir(w, problema.NoProcesable("sin_wallet", "Apple Wallet todavía no está disponible."))
			return
		}
		w.Header().Set("Content-Type", "application/vnd.apple.pkpass")
		w.Header().Set("Content-Disposition", `attachment; filename="caigo.pkpass"`)
		w.Header().Set("Last-Modified", p.Cambiado.UTC().Format(http.TimeFormat))
		_, _ = w.Write(b)
	}
	mux.Handle("GET /v1/pases/entradas/{id}/{wallet}", con(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		yo, _ := sesion.De(r.Context())
		p, err := s.entrada(r.Context(), yo.ID, r.PathValue("id"))
		entregar(w, r, p, err)
	})))
	mux.Handle("GET /v1/pases/socios/{lugar}/{wallet}", con(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		yo, _ := sesion.De(r.Context())
		p, err := s.socio(r.Context(), yo.ID, r.PathValue("lugar"))
		entregar(w, r, p, err)
	})))

	// Web service de PassKit (webServiceURL = <api>/v1/wallet). Autoriza con "ApplePass <token>".
	autorizado := func(r *http.Request) bool {
		if s.Apple == nil || r.PathValue("tipo") != s.Apple.PassTypeID {
			return false
		}
		tok := strings.TrimPrefix(r.Header.Get("Authorization"), "ApplePass ")
		return subtle.ConstantTimeCompare([]byte(tok), []byte(s.Apple.TokenDe(r.PathValue("serie")))) == 1
	}
	mux.HandleFunc("POST /v1/wallet/v1/devices/{disp}/registrations/{tipo}/{serie}", func(w http.ResponseWriter, r *http.Request) {
		if !autorizado(r) {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		var in struct {
			PushToken string `json:"pushToken"`
		}
		if json.NewDecoder(io.LimitReader(r.Body, 4<<10)).Decode(&in) != nil || in.PushToken == "" {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		nuevo := false
		err := s.App.EnSistema(r.Context(), func(tx pgx.Tx) error {
			tag, err := tx.Exec(r.Context(), `INSERT INTO wallet_registros (dispositivo, serie, push_token) VALUES ($1, $2, $3)
				ON CONFLICT (dispositivo, serie) DO UPDATE SET push_token = excluded.push_token`, r.PathValue("disp"), r.PathValue("serie"), in.PushToken)
			nuevo = tag.RowsAffected() > 0
			return err
		})
		if err != nil {
			mal(w, err)
			return
		}
		if nuevo {
			w.WriteHeader(http.StatusCreated)
			return
		}
		w.WriteHeader(http.StatusOK)
	})
	mux.HandleFunc("DELETE /v1/wallet/v1/devices/{disp}/registrations/{tipo}/{serie}", func(w http.ResponseWriter, r *http.Request) {
		if !autorizado(r) {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		_ = s.App.EnSistema(r.Context(), func(tx pgx.Tx) error {
			_, err := tx.Exec(r.Context(), `DELETE FROM wallet_registros WHERE dispositivo = $1 AND serie = $2`, r.PathValue("disp"), r.PathValue("serie"))
			return err
		})
		w.WriteHeader(http.StatusOK)
	})
	mux.HandleFunc("GET /v1/wallet/v1/devices/{disp}/registrations/{tipo}", func(w http.ResponseWriter, r *http.Request) {
		var series []string
		err := s.App.EnSistema(r.Context(), func(tx pgx.Tx) error {
			rows, err := tx.Query(r.Context(), `SELECT serie FROM wallet_registros WHERE dispositivo = $1`, r.PathValue("disp"))
			if err != nil {
				return err
			}
			series, err = pgx.CollectRows(rows, pgx.RowTo[string])
			return err
		})
		if err != nil {
			mal(w, err)
			return
		}
		desde, _ := strconv.ParseInt(r.URL.Query().Get("passesUpdatedSince"), 10, 64)
		var cambiadas []string
		ultimo := desde
		for _, serie := range series {
			p, err := s.porSerie(r.Context(), serie)
			if err != nil {
				continue
			}
			if p.Cambiado.Unix() > desde {
				cambiadas = append(cambiadas, serie)
			}
			ultimo = max(ultimo, p.Cambiado.Unix())
		}
		if len(cambiadas) == 0 {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		respuesta.JSON(w, http.StatusOK, map[string]any{"serialNumbers": cambiadas, "lastUpdated": strconv.FormatInt(ultimo, 10)})
	})
	mux.HandleFunc("GET /v1/wallet/v1/passes/{tipo}/{serie}", func(w http.ResponseWriter, r *http.Request) {
		if !autorizado(r) {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		p, err := s.porSerie(r.Context(), r.PathValue("serie"))
		if err != nil {
			mal(w, err)
			return
		}
		if t, err := http.ParseTime(r.Header.Get("If-Modified-Since")); err == nil && !p.Cambiado.Truncate(time.Second).After(t) {
			w.WriteHeader(http.StatusNotModified)
			return
		}
		entregar(w, r, p, nil)
	})
	mux.HandleFunc("POST /v1/wallet/v1/log", func(w http.ResponseWriter, r *http.Request) {
		var in struct {
			Logs []string `json:"logs"`
		}
		_ = json.NewDecoder(io.LimitReader(r.Body, 16<<10)).Decode(&in)
		for _, l := range in.Logs {
			log.Info("apple wallet", "log", l)
		}
		w.WriteHeader(http.StatusOK)
	})
}

// Actualizado encola el aviso de que un pase cambió (lo manda el worker a los dispositivos).
func Actualizado(ctx context.Context, tx pgx.Tx, serie string) error {
	return outbox.Publicar(ctx, tx, "wallet.actualizar", map[string]string{"serie": serie})
}

// Manejadores del worker.
func (s *Servicio) Manejadores() map[string]outbox.Manejador {
	return map[string]outbox.Manejador{
		"wallet.actualizar": func(ctx context.Context, ev outbox.Evento) error {
			var d struct{ Serie string }
			if json.Unmarshal(ev.Datos, &d) != nil || s.Apple == nil {
				return nil
			}
			var tokens []string
			err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
				rows, err := tx.Query(ctx, `SELECT push_token FROM wallet_registros WHERE serie = $1`, d.Serie)
				if err != nil {
					return err
				}
				tokens, err = pgx.CollectRows(rows, pgx.RowTo[string])
				return err
			})
			if err != nil {
				return err
			}
			var errs []error
			for _, t := range tokens {
				errs = append(errs, s.Apple.Avisar(ctx, t))
			}
			return errors.Join(errs...)
		},
	}
}
