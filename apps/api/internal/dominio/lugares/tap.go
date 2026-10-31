package lugares

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/wallet"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

const propositoPlaca = "placa"

// Placa Caigo Tap como la ve el panel.
type Placa struct {
	ID     string `json:"id"`
	Nombre string `json:"nombre"`
	Uso    string `json:"uso"` // puerta | barra
	SUN    bool   `json:"sun"`
	Activa bool   `json:"activa"`
	// URL que se graba en el chip (o en el QR de la placa).
	URL string `json:"url"`
	// Sólo al activarla con NTAG 424: la clave para programar el chip (no se vuelve a mostrar).
	ClaveSUN string `json:"claveSun,omitempty"`
}

func (s *Servicio) urlPlaca(id string, sun bool) string {
	base := strings.TrimRight(s.Sitio, "/") + "/t/"
	if sun {
		// El chip agrega ?picc_data=…&cmac=… en cada lectura.
		return base + id
	}
	return base + s.Firma.Firmar(propositoPlaca, id, time.Time{})
}

// Placas del lugar.
func (s *Servicio) Placas(ctx context.Context, usuarioID, lugarID string) ([]Placa, error) {
	out := []Placa{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		rows, err := tx.Query(ctx, `SELECT id, nombre, uso, clave_sun IS NOT NULL, activa FROM placas
			WHERE lugar_id::text = $1 ORDER BY creada_en`, lugarID)
		if err != nil {
			return err
		}
		defer rows.Close()
		for rows.Next() {
			var p Placa
			if err := rows.Scan(&p.ID, &p.Nombre, &p.Uso, &p.SUN, &p.Activa); err != nil {
				return err
			}
			p.URL = s.urlPlaca(p.ID, p.SUN)
			out = append(out, p)
		}
		return rows.Err()
	})
	return out, err
}

// ActivarPlaca suma una placa. sun: para un chip NTAG 424 DNA (genera la clave del chip).
func (s *Servicio) ActivarPlaca(ctx context.Context, usuarioID, lugarID, nombre, uso string, sun bool) (Placa, error) {
	nombre = strings.TrimSpace(nombre)
	if nombre == "" || len([]rune(nombre)) > 40 {
		return Placa{}, problema.Invalido("Poné un nombre para la placa (por ejemplo, Puerta principal).")
	}
	if uso != "puerta" && uso != "barra" {
		return Placa{}, problema.Invalido("La placa es de puerta o de barra.")
	}
	p := Placa{Nombre: nombre, Uso: uso, SUN: sun, Activa: true}
	var cifrada []byte
	if sun {
		if s.Cifra == nil {
			return Placa{}, problema.NoProcesable("sin_cifrado", "Las placas NTAG 424 todavía no están disponibles.")
		}
		clave := make([]byte, 16)
		_, _ = rand.Read(clave)
		p.ClaveSUN = hex.EncodeToString(clave)
		var err error
		if cifrada, err = s.Cifra.Cifrar("placa:"+lugarID, clave); err != nil {
			return Placa{}, err
		}
	}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, true); err != nil {
			return err
		}
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM placas WHERE lugar_id::text = $1 AND activa`, lugarID).Scan(&n); err != nil {
			return err
		}
		if n >= 20 {
			return problema.Conflicto("demasiadas", "Ya tenés 20 placas activas.")
		}
		return tx.QueryRow(ctx, `INSERT INTO placas (lugar_id, nombre, uso, clave_sun) VALUES ($1, $2, $3, $4) RETURNING id`,
			lugarID, nombre, uso, cifrada).Scan(&p.ID)
	})
	p.URL = s.urlPlaca(p.ID, sun)
	return p, err
}

// DesactivarPlaca da de baja una placa (una perdida o robada deja de servir).
func (s *Servicio) DesactivarPlaca(ctx context.Context, usuarioID, lugarID, placaID string) error {
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `UPDATE placas SET activa = false WHERE id::text = $1 AND lugar_id::text = $2`, placaID, lugarID)
		return err
	})
}

// Lectura de una placa: lo que manda la app al leer el chip o el QR.
type Lectura struct {
	// Lo que va después de /t/ en la URL: el id (con SUN) o el token firmado (placa común).
	Placa string `json:"placa"`
	Picc  string `json:"picc"`
	CMAC  string `json:"cmac"`
}

// ResultadoTap es lo que ve la persona ("Adentro").
type ResultadoTap struct {
	Lugar string `json:"lugar"`
	Uso   string `json:"uso"`
	// Plan de hoy en el lugar al que se hizo check-in.
	Plan *struct {
		ID        string `json:"id"`
		Titulo    string `json:"titulo"`
		Beneficio string `json:"beneficio,omitempty"`
		Saldo     int32  `json:"saldoCentavos"`
		Repetido  bool   `json:"repetido"`
	} `json:"plan,omitempty"`
	// Pase de socio.
	Socio *struct {
		Visitas   int16  `json:"visitas"`
		Meta      int16  `json:"meta"`
		Beneficio string `json:"beneficio"`
		Gano      bool   `json:"gano"`
		Canjeo    bool   `json:"canjeo"`
		Sumo      bool   `json:"sumo"`
	} `json:"socio,omitempty"`
}

// placaDe resuelve y verifica la lectura. Devuelve el id de la placa.
func (s *Servicio) placaDe(ctx context.Context, tx pgx.Tx, l Lectura) (id, lugarID, uso, lugar string, err error) {
	if id, err = s.Firma.Verificar(propositoPlaca, l.Placa, s.ahora()); err != nil {
		id = l.Placa
	}
	var clave []byte
	var contador int32
	err = tx.QueryRow(ctx, `SELECT p.id, p.lugar_id, p.uso, l.nombre, p.clave_sun, p.contador FROM placas p
		JOIN lugares l ON l.id = p.lugar_id WHERE p.id::text = $1 AND p.activa AND l.estado = 'aprobado' FOR UPDATE OF p`, id).
		Scan(&id, &lugarID, &uso, &lugar, &clave, &contador)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", "", "", "", problema.NoEncontrado("Esta placa no está activa.")
	}
	if err != nil {
		return
	}
	// Placa común: tiene que venir con el token firmado, no con el id pelado.
	if clave == nil {
		if l.Placa == id {
			return "", "", "", "", problema.NoEncontrado("Esta placa no está activa.")
		}
		return
	}
	k, err := s.Cifra.Descifrar("placa:"+lugarID, clave)
	if err != nil {
		return
	}
	lec, err := VerificarSUN(k, l.Picc, l.CMAC)
	if err != nil || int64(lec.Contador) <= int64(contador) {
		return "", "", "", "", problema.Conflicto("tap_invalido", "No pudimos leer bien la placa. Apoyá el celu de nuevo.")
	}
	_, err = tx.Exec(ctx, `UPDATE placas SET contador = $2 WHERE id = $1`, id, int32(lec.Contador))
	return
}

// Tap: la persona apoyó el celu en una placa (o escaneó su QR). Hace el check-in del plan de hoy
// en el lugar, suma la visita del pase de socio (una por día) y, en la barra, canjea el beneficio.
func (s *Servicio) Tap(ctx context.Context, usuarioID string, l Lectura) (ResultadoTap, error) {
	var r ResultadoTap
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		_, lugarID, uso, lugar, err := s.placaDe(ctx, tx, l)
		if err != nil {
			return err
		}
		r.Lugar, r.Uso = lugar, uso
		ahora := s.ahora()
		// Plan de hoy: desde media hora antes hasta una hora después del final.
		var plan struct {
			ID        string `json:"id"`
			Titulo    string `json:"titulo"`
			Beneficio string `json:"beneficio,omitempty"`
			Saldo     int32  `json:"saldoCentavos"`
			Repetido  bool   `json:"repetido"`
		}
		var antes *time.Time
		err = tx.QueryRow(ctx, `SELECT p.id, p.titulo, p.beneficio, coalesce(e.saldo_centavos, 0), s.checkin_en
			FROM solicitudes s JOIN planes p ON p.id = s.plan_id
			LEFT JOIN entradas e ON e.plan_id = p.id AND e.usuario_id = s.usuario_id AND e.estado = 'pagada'
			WHERE s.usuario_id = $1 AND s.estado = 'aceptada' AND p.lugar_id = $2 AND p.cancelado_en IS NULL
				AND $3 BETWEEN p.empieza_en - interval '30 minutes' AND p.empieza_en + make_interval(mins => p.duracion_min) + interval '1 hour'
			ORDER BY p.empieza_en LIMIT 1`, usuarioID, lugarID, ahora).Scan(&plan.ID, &plan.Titulo, &plan.Beneficio, &plan.Saldo, &antes)
		if err == nil {
			plan.Repetido = antes != nil
			if !plan.Repetido {
				if _, err := tx.Exec(ctx, `UPDATE solicitudes SET checkin_en = now(), checkin_metodo = 'nfc' WHERE plan_id = $1 AND usuario_id = $2`, plan.ID, usuarioID); err != nil {
					return err
				}
				var entrada string
				err := tx.QueryRow(ctx, `UPDATE entradas SET validada_en = coalesce(validada_en, now()), actualizada_en = now()
					WHERE plan_id = $1 AND usuario_id = $2 AND estado = 'pagada' RETURNING id`, plan.ID, usuarioID).Scan(&entrada)
				if err == nil {
					err = wallet.Actualizado(ctx, tx, "e-"+entrada)
				}
				if err != nil && !errors.Is(err, pgx.ErrNoRows) {
					return err
				}
			}
			r.Plan = &plan
		} else if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		// Pase de socio.
		var activo bool
		var meta int16
		var beneficio string
		if err := tx.QueryRow(ctx, `SELECT socio_activo, socio_visitas, socio_beneficio FROM lugares WHERE id = $1`, lugarID).Scan(&activo, &meta, &beneficio); err != nil {
			return err
		}
		if activo {
			hoy := ahora.In(argentina()).Format(time.DateOnly)
			var so struct {
				Visitas   int16  `json:"visitas"`
				Meta      int16  `json:"meta"`
				Beneficio string `json:"beneficio"`
				Gano      bool   `json:"gano"`
				Canjeo    bool   `json:"canjeo"`
				Sumo      bool   `json:"sumo"`
			}
			so.Meta, so.Beneficio = meta, beneficio
			var ultima *time.Time
			var ganados, canjeados int16
			err := tx.QueryRow(ctx, `INSERT INTO socios (lugar_id, usuario_id) VALUES ($1, $2)
				ON CONFLICT (lugar_id, usuario_id) DO UPDATE SET actualizado_en = socios.actualizado_en
				RETURNING visitas, ultima_visita, ganados, canjeados`, lugarID, usuarioID).Scan(&so.Visitas, &ultima, &ganados, &canjeados)
			if err != nil {
				return err
			}
			if ultima == nil || ultima.Format(time.DateOnly) != hoy {
				so.Visitas++
				so.Sumo = true
				if so.Visitas >= meta {
					so.Visitas, so.Gano = 0, true
					ganados++
				}
			}
			if uso == "barra" && ganados > canjeados {
				canjeados++
				so.Canjeo = true
			}
			if _, err := tx.Exec(ctx, `UPDATE socios SET visitas = $3, ultima_visita = $4, ganados = $5, canjeados = $6, actualizado_en = now()
				WHERE lugar_id = $1 AND usuario_id = $2`, lugarID, usuarioID, so.Visitas, hoy, ganados, canjeados); err != nil {
				return err
			}
			if err := wallet.Actualizado(ctx, tx, "s-"+lugarID+"_"+usuarioID); err != nil {
				return err
			}
			r.Socio = &so
		}
		if r.Plan == nil && r.Socio == nil {
			return problema.NoEncontrado("No tenés un plan en " + lugar + " ahora.")
		}
		return nil
	})
	return r, err
}

// PaseSocio de una persona en un lugar ("Mis pases").
type PaseSocio struct {
	LugarID   string `json:"lugarId"`
	Lugar     string `json:"lugar"`
	Visitas   int16  `json:"visitas"`
	Meta      int16  `json:"meta"`
	Beneficio string `json:"beneficio"`
	// Beneficios ganados sin canjear.
	Pendientes int16 `json:"pendientes"`
}

// MisPases de socio.
func (s *Servicio) MisPases(ctx context.Context, usuarioID string) ([]PaseSocio, error) {
	out := []PaseSocio{}
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT l.id, l.nombre, s.visitas, l.socio_visitas, l.socio_beneficio, s.ganados - s.canjeados
			FROM socios s JOIN lugares l ON l.id = s.lugar_id WHERE s.usuario_id = $1 AND l.socio_activo
			ORDER BY s.actualizado_en DESC`, usuarioID)
		if err != nil {
			return err
		}
		ps, err := pgx.CollectRows(rows, pgx.RowToStructByPos[PaseSocio])
		if ps != nil {
			out = ps
		}
		return err
	})
	return out, err
}

func argentina() *time.Location {
	l, err := time.LoadLocation("America/Argentina/Buenos_Aires")
	if err != nil {
		return time.FixedZone("ART", -3*3600)
	}
	return l
}
