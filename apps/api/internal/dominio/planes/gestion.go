package planes

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/archivos"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/outbox"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// Edicion es el cuerpo de PATCH /v1/planes/{id}: los campos de la creación (los que no vienen no
// cambian) y el cupo total.
type Edicion struct {
	Entrada
	Total *int16 `json:"total"`
}

// Editar cambia un plan propio que no empezó. El cuerpo se aplica sobre el plan actual (PATCH):
// lo que no viene queda igual. Si cambian la fecha o el lugar, avisa a los que van.
func (s *Servicio) Editar(ctx context.Context, usuarioID, planID string, cuerpo []byte) (Plan, error) {
	if !esUUID(planID) {
		return Plan{}, problema.NoEncontrado("Ese plan no existe.")
	}
	ahora := s.ahora()
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var org string
		var deLugar, cancelado bool
		var total int16
		var e Entrada
		var solo, ocultar bool
		err := tx.QueryRow(ctx, `SELECT organizador_id, lugar_id IS NOT NULL, cancelado_en IS NOT NULL, titulo, categoria,
				descripcion, empieza_en, duracion_min, recurrente, ciudad, zona, lugar_nombre, somos, total, ingreso,
				visibilidad, solo_verificados, ocultar_direccion, edad_min, edad_max, genero, nivel, costo_centavos, foto
			FROM planes WHERE id = $1 FOR UPDATE`, planID).Scan(&org, &deLugar, &cancelado, &e.Titulo, &e.Categoria,
			&e.Descripcion, &e.EmpiezaEn, &e.DuracionMin, &e.Recurrente, &e.Ciudad, &e.Zona, &e.LugarNombre, &e.Somos, &total,
			&e.Ingreso, &e.Visibilidad, &solo, &ocultar, &e.EdadMin, &e.EdadMax, &e.Genero, &e.Nivel, &e.CostoCentavos, &e.Foto)
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && org != usuarioID) {
			return problema.NoEncontrado("Ese plan no es tuyo.")
		}
		if err != nil {
			return err
		}
		fotoAntes := e.Foto
		switch {
		case deLugar:
			return problema.Conflicto("de_un_lugar", "Este plan es de un lugar: se edita desde el panel.")
		case cancelado:
			return problema.Conflicto("cancelado", "Este plan está cancelado.")
		case !e.EmpiezaEn.After(ahora):
			return problema.Conflicto("empezo", "Este plan ya empezó: no se puede editar.")
		}
		e.SoloVerificados, e.OcultarDireccion = &solo, &ocultar
		e.Faltan = total - e.Somos
		if err := tx.QueryRow(ctx, `SELECT direccion, lat, lng FROM planes_direccion WHERE plan_id = $1`, planID).
			Scan(&e.Direccion, &e.Lat, &e.Lng); err != nil {
			return err
		}
		orig := e
		ed := Edicion{Entrada: e}
		if err := Superponer(cuerpo, &ed); err != nil {
			return err
		}
		e = ed.Entrada
		// Lo que no se edita: qué es, la ciudad, la serie y el grupo del organizador.
		e.Categoria, e.Ciudad, e.Recurrente, e.Somos, e.Faltan = orig.Categoria, orig.Ciudad, orig.Recurrente, orig.Somos, orig.Faltan
		var sumados int
		if err := tx.QueryRow(ctx, `SELECT coalesce(sum(cantidad), 0) FROM solicitudes WHERE plan_id = $1 AND estado = 'aceptada'`, planID).Scan(&sumados); err != nil {
			return err
		}
		if ed.Total != nil {
			if int(*ed.Total) < sumados || *ed.Total <= e.Somos {
				return problema.Conflicto("cupo_menor", fmt.Sprintf("Ya van %d: el cupo no puede ser menor.", max(sumados, int(e.Somos)+1)))
			}
			e.Faltan = *ed.Total - e.Somos
		}
		if err := e.validar(ahora); err != nil {
			return err
		}
		if !e.EmpiezaEn.After(ahora) {
			return problema.Invalido("Ese horario ya pasó.", problema.Campo{Ruta: "empiezaEn", Mensaje: "Ese horario ya pasó."})
		}
		ocultar = verdad(e.OcultarDireccion, true)
		lat, lng := e.Lat, e.Lng
		if ocultar {
			lat, lng = aproximar(lat), aproximar(lng)
		}
		e.LugarNombre = strings.TrimSpace(e.LugarNombre)
		if e.Direccion == "" {
			e.Direccion = conDefecto(e.LugarNombre, e.Zona+", "+e.Ciudad)
		}
		if _, err := tx.Exec(ctx, `UPDATE planes SET titulo = $2, descripcion = $3, empieza_en = $4, duracion_min = $5,
				zona = $6, lugar_nombre = $7, lat = $8, lng = $9, total = $10, ingreso = $11, visibilidad = $12,
				solo_verificados = $13, ocultar_direccion = $14, edad_min = $15, edad_max = $16, genero = $17, nivel = $18,
				costo_centavos = $19, foto = $20
			WHERE id = $1`, planID, e.Titulo, strings.TrimSpace(e.Descripcion), e.EmpiezaEn, e.DuracionMin, e.Zona,
			e.LugarNombre, lat, lng, e.Somos+e.Faltan, e.Ingreso, e.Visibilidad, verdad(e.SoloVerificados, true), ocultar,
			e.EdadMin, e.EdadMax, e.Genero, strings.TrimSpace(e.Nivel), e.CostoCentavos, strings.TrimSpace(e.Foto)); err != nil {
			return err
		}
		// La foto que se sacó del plan se borra ahora (si no la usa otro plan, como uno repetido).
		if fotoAntes != strings.TrimSpace(e.Foto) {
			if err := archivos.SoltarFotos(ctx, tx, fotoAntes); err != nil {
				return err
			}
		}
		if _, err := tx.Exec(ctx, `UPDATE planes_direccion SET direccion = $2, lat = $3, lng = $4 WHERE plan_id = $1`,
			planID, e.Direccion, e.Lat, e.Lng); err != nil {
			return err
		}
		cambio := !e.EmpiezaEn.Equal(orig.EmpiezaEn) || e.Lat != orig.Lat || e.Lng != orig.Lng ||
			e.Direccion != orig.Direccion || e.LugarNombre != orig.LugarNombre || e.Zona != orig.Zona
		if !cambio {
			return nil
		}
		return outbox.Publicar(ctx, tx, "plan.cambiado", map[string]string{"planId": planID})
	})
	if err != nil {
		return Plan{}, err
	}
	return s.Ver(ctx, usuarioID, planID, nil)
}

// Superponer aplica un cuerpo JSON (PATCH) sobre v, que ya tiene los valores actuales. Campos
// desconocidos son error, como en el resto de la API.
func Superponer(cuerpo []byte, v any) error {
	if len(bytes.TrimSpace(cuerpo)) == 0 {
		return nil
	}
	dec := json.NewDecoder(bytes.NewReader(cuerpo))
	dec.DisallowUnknownFields()
	if err := dec.Decode(v); err != nil {
		return problema.Invalido("El cuerpo del pedido no es JSON válido: " + err.Error())
	}
	return nil
}

// CancelarTx cancela el plan en la transacción: avisa a los aceptados y pendientes (plan.cancelado)
// y encola el reembolso del 100 % de cada entrada pagada (entrada.reembolsar, lo hace el worker con
// el token del lugar). Quien llama ya verificó el permiso. Devuelve false si ya estaba cancelado.
func CancelarTx(ctx context.Context, tx pgx.Tx, planID, motivo string) (bool, error) {
	var org string
	motivo = recortar(strings.TrimSpace(motivo), 300)
	err := tx.QueryRow(ctx, `UPDATE planes SET cancelado_en = now(), cancelado_motivo = $2
		WHERE id::text = $1 AND cancelado_en IS NULL RETURNING organizador_id`, planID, motivo).Scan(&org)
	if errors.Is(err, pgx.ErrNoRows) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	rows, err := tx.Query(ctx, `SELECT usuario_id::text FROM solicitudes WHERE plan_id::text = $1 AND usuario_id <> $2
		AND estado IN ('aceptada', 'pendiente')`, planID, org)
	if err != nil {
		return false, err
	}
	usuarios, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return false, err
	}
	if err := outbox.Publicar(ctx, tx, "plan.cancelado", map[string]any{"planId": planID, "usuarios": usuarios, "motivo": motivo}); err != nil {
		return false, err
	}
	// Las compras sin pagar quedan anuladas; las pagadas se devuelven enteras (cargo incluido).
	if _, err := tx.Exec(ctx, `UPDATE entradas SET estado = 'cancelada', actualizada_en = now()
		WHERE plan_id::text = $1 AND estado = 'pendiente'`, planID); err != nil {
		return false, err
	}
	rows, err = tx.Query(ctx, `SELECT id::text FROM entradas WHERE plan_id::text = $1 AND estado = 'pagada'`, planID)
	if err != nil {
		return false, err
	}
	pagadas, err := pgx.CollectRows(rows, pgx.RowTo[string])
	if err != nil {
		return false, err
	}
	for _, id := range pagadas {
		if err := outbox.Publicar(ctx, tx, "entrada.reembolsar", map[string]any{"entradaId": id, "total": true}); err != nil {
			return false, err
		}
	}
	return true, nil
}

func recortar(s string, n int) string {
	r := []rune(s)
	if len(r) <= n {
		return s
	}
	return string(r[:n])
}

// Cancelar baja el plan (sólo el organizador): avisa y devuelve las entradas pagadas.
func (s *Servicio) Cancelar(ctx context.Context, usuarioID, planID string) error {
	if !esUUID(planID) {
		return problema.NoEncontrado("Ese plan no existe.")
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var org string
		err := tx.QueryRow(ctx, `SELECT organizador_id FROM planes WHERE id = $1 FOR UPDATE`, planID).Scan(&org)
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && org != usuarioID) {
			return problema.NoEncontrado("Ese plan no es tuyo o ya está cancelado.")
		}
		if err != nil {
			return err
		}
		ok, err := CancelarTx(ctx, tx, planID, "")
		if err == nil && !ok {
			return problema.NoEncontrado("Ese plan no es tuyo o ya está cancelado.")
		}
		return err
	})
}

// SacarPersona: el organizador saca a alguien aceptado (no a quien pagó una entrada). Queda como
// rechazada: no puede volver a pedir.
func (s *Servicio) SacarPersona(ctx context.Context, usuarioID, planID, otroID string) error {
	if !esUUID(planID) || !esUUID(otroID) {
		return problema.NoEncontrado("Esa persona no está en el plan.")
	}
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		var org string
		err := tx.QueryRow(ctx, `SELECT organizador_id FROM planes WHERE id = $1 FOR UPDATE`, planID).Scan(&org)
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && org != usuarioID) {
			return problema.NoEncontrado("Ese plan no es tuyo.")
		}
		if err != nil {
			return err
		}
		if otroID == usuarioID {
			return problema.Conflicto("es_tuyo", "Es tu plan: si no vas, cancelalo.")
		}
		var pago bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM entradas WHERE plan_id = $1 AND usuario_id = $2 AND estado = 'pagada')`,
			planID, otroID).Scan(&pago); err != nil {
			return err
		}
		if pago {
			return problema.Conflicto("con_entrada", "Esa persona pagó su entrada: no se la puede sacar.")
		}
		tag, err := tx.Exec(ctx, `UPDATE solicitudes SET estado = 'rechazada', respondida_en = now()
			WHERE plan_id = $1 AND usuario_id = $2 AND estado = 'aceptada'`, planID, otroID)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return problema.NoEncontrado("Esa persona no está en el plan.")
		}
		return outbox.Publicar(ctx, tx, "solicitud.sacada", map[string]string{"planId": planID, "usuarioId": otroID})
	})
}

// Publicado encola el aviso de plan nuevo (tu gente y cerca): lo reparte el worker.
func Publicado(ctx context.Context, tx pgx.Tx, planID string) error {
	return outbox.Publicar(ctx, tx, "plan.publicado", map[string]string{"planId": planID})
}

// Recurrencia: el worker crea la próxima fecha de cada serie recurrente 6 días antes (mismo plan,
// mismo organizador, sin sumados). Una serie se corta con "¿Repetimos? No" (recurrente = false) o
// cancelando su último plan. Idempotente: sólo mira el último plan de cada serie. Devuelve cuántos
// creó.
func (s *Servicio) Recurrencia(ctx context.Context) (int, error) {
	ahora := s.ahora()
	var ids []string
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT p.id::text FROM planes p
			WHERE p.recurrente AND p.cancelado_en IS NULL
				AND p.empieza_en <= $1::timestamptz - interval '1 day' AND p.empieza_en > $1::timestamptz - interval '60 days'
				AND NOT EXISTS (SELECT 1 FROM planes q WHERE coalesce(q.serie_id, q.id) = coalesce(p.serie_id, p.id)
					AND q.empieza_en > p.empieza_en)
			ORDER BY p.empieza_en LIMIT 1000`, ahora)
		if err != nil {
			return err
		}
		ids, err = pgx.CollectRows(rows, pgx.RowTo[string])
		return err
	})
	if err != nil {
		return 0, err
	}
	// Una serie que falla no frena a las demás.
	creados := 0
	var errs []error
	for _, id := range ids {
		ok, err := s.repetir(ctx, id, ahora)
		if err != nil {
			errs = append(errs, fmt.Errorf("plan %s: %w", id, err))
		}
		if ok {
			creados++
		}
	}
	return creados, errors.Join(errs...)
}

func (s *Servicio) repetir(ctx context.Context, planID string, ahora time.Time) (bool, error) {
	creado := false
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var titulo, zona, serie string
		var empieza time.Time
		var somos int16
		var org string
		var deLugar bool
		// Lock por serie: dos workers no la repiten dos veces.
		err := tx.QueryRow(ctx, `SELECT titulo, zona, coalesce(serie_id, id)::text, empieza_en, somos, organizador_id,
				lugar_id IS NOT NULL
			FROM planes WHERE id::text = $1 AND recurrente AND cancelado_en IS NULL FOR UPDATE`, planID).
			Scan(&titulo, &zona, &serie, &empieza, &somos, &org, &deLugar)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock(hashtext($1))`, "serie:"+serie); err != nil {
			return err
		}
		var otro bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM planes WHERE coalesce(serie_id, id)::text = $1 AND empieza_en > $2)`,
			serie, empieza).Scan(&otro); err != nil || otro {
			return err
		}
		const semana = 7 * 24 * time.Hour
		proximo := empieza.Add(semana)
		for !proximo.After(ahora) {
			proximo = proximo.Add(semana)
		}
		if proximo.After(ahora.Add(6 * 24 * time.Hour)) {
			return nil
		}
		var nuevo string
		if err := tx.QueryRow(ctx, `
			INSERT INTO planes (slug, organizador_id, titulo, categoria, descripcion, empieza_en, duracion_min, recurrente,
				serie_id, ciudad, zona, lugar_nombre, lat, lng, somos, total, ingreso, visibilidad, solo_verificados,
				ocultar_direccion, edad_min, edad_max, genero, nivel, costo_centavos, foto, lugar_id, cobro, precio_centavos,
				sena_pct, incluye, cancelacion, beneficio, acepta_grupos)
			SELECT $2, organizador_id, titulo, categoria, descripcion, $3, duracion_min, true, $4::uuid, ciudad, zona,
				lugar_nombre, lat, lng, somos, total, ingreso, visibilidad, solo_verificados, ocultar_direccion, edad_min,
				edad_max, genero, nivel, costo_centavos, foto, lugar_id, cobro, precio_centavos, sena_pct, incluye,
				cancelacion, beneficio, acepta_grupos
			FROM planes WHERE id::text = $1 RETURNING id`, planID, slug(titulo, zona), proximo, serie).Scan(&nuevo); err != nil {
			return err
		}
		if !deLugar && somos > 0 {
			if _, err := tx.Exec(ctx, `INSERT INTO solicitudes (plan_id, usuario_id, cantidad, estado, respondida_en)
				VALUES ($1, $2, $3, 'aceptada', now())`, nuevo, org, somos); err != nil {
				return err
			}
		}
		if _, err := tx.Exec(ctx, `INSERT INTO planes_direccion (plan_id, direccion, lat, lng)
			SELECT $1, direccion, lat, lng FROM planes_direccion WHERE plan_id::text = $2`, nuevo, planID); err != nil {
			return err
		}
		creado = true
		return Publicado(ctx, tx, nuevo)
	})
	return creado, err
}

// Zonas fijas de cada ciudad (se suman a las de los planes que vienen).
var zonasFijas = map[string][]string{
	"mar del plata": {"Centro", "Güemes", "La Perla", "Playa Grande", "Los Troncos", "Plaza Mitre", "Chauvín",
		"Puerto", "Punta Mogotes", "Constitución", "Sierra de los Padres"},
	"villa gesell": {"Centro", "Norte", "Sur", "Mar de las Pampas", "Las Gaviotas", "Mar Azul"},
}

// Zonas de una ciudad: las fijas y las de los planes públicos de los próximos 30 días.
func (s *Servicio) Zonas(ctx context.Context, ciudad string) ([]string, error) {
	ciudad = strings.TrimSpace(ciudad)
	ahora := s.ahora()
	var dePlanes []string
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT DISTINCT zona FROM planes WHERE zona <> '' AND cancelado_en IS NULL
				AND visibilidad = 'publico' AND empieza_en BETWEEN $2 AND $2::timestamptz + interval '30 days'
				AND ($1 = '' OR lower(ciudad) = lower($1))
			LIMIT 200`, ciudad, ahora)
		if err != nil {
			return err
		}
		dePlanes, err = pgx.CollectRows(rows, pgx.RowTo[string])
		return err
	})
	if err != nil {
		return nil, err
	}
	var fijas []string
	if ciudad == "" {
		for _, zs := range zonasFijas {
			fijas = append(fijas, zs...)
		}
	} else {
		fijas = zonasFijas[strings.ToLower(ciudad)]
	}
	vistas := map[string]bool{}
	out := []string{}
	for _, z := range append(fijas, dePlanes...) {
		k := clave(z)
		if !vistas[k] {
			vistas[k] = true
			out = append(out, z)
		}
	}
	sort.Slice(out, func(i, j int) bool { return clave(out[i]) < clave(out[j]) })
	return out, nil
}

// clave: minúsculas sin tildes, para comparar y ordenar zonas.
func clave(s string) string {
	return strings.NewReplacer("á", "a", "é", "e", "í", "i", "ó", "o", "ú", "u", "ü", "u", "ñ", "n").
		Replace(strings.ToLower(strings.TrimSpace(s)))
}
