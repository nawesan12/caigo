package lugares

import (
	"context"
	"crypto/sha256"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"
)

// RutaVuelta del OAuth: tiene que coincidir con la cargada en la aplicación de Mercado Pago.
const RutaVuelta = "/mercadopago/vuelta"

func (s *Servicio) vuelta() string { return s.Panel + RutaVuelta }

func (s *Servicio) mpActivo() bool { return s.MP.Activo() && s.Cifra != nil }

var errSinMP = problema.NoProcesable("sin_mp", "Mercado Pago todavía no está disponible en Caigo.")

func hashEstado(e string) []byte {
	h := sha256.Sum256([]byte(e))
	return h[:]
}

// ConectarMP devuelve la URL para que el lugar autorice a Caigo en su Mercado Pago.
func (s *Servicio) ConectarMP(ctx context.Context, usuarioID, lugarID string) (string, error) {
	if !s.mpActivo() {
		return "", errSinMP
	}
	estado, verificador := mercadopago.Azar(), mercadopago.Azar()+mercadopago.Azar()
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		_, _ = tx.Exec(ctx, `DELETE FROM mp_estados WHERE vence_en < now()`)
		_, err := tx.Exec(ctx, `INSERT INTO mp_estados (estado_hash, lugar_id, usuario_id, verificador, vence_en)
			VALUES ($1, $2, $3, $4, now() + interval '10 minutes')`, hashEstado(estado), lugarID, usuarioID, verificador)
		return err
	})
	if err != nil {
		return "", err
	}
	return s.MP.URLAutorizacion(s.vuelta(), estado, verificador), nil
}

// VolverMP consume el state, canjea el código y guarda los tokens cifrados. Devuelve el lugar.
func (s *Servicio) VolverMP(ctx context.Context, usuarioID, codigo, estado string) (string, error) {
	if !s.mpActivo() {
		return "", errSinMP
	}
	var lugarID, verificador string
	err := s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		err := tx.QueryRow(ctx, `UPDATE mp_estados SET usado_en = now()
			WHERE estado_hash = $1 AND usuario_id = $2 AND usado_en IS NULL AND vence_en > now()
			RETURNING lugar_id, verificador`, hashEstado(estado), usuarioID).Scan(&lugarID, &verificador)
		if errors.Is(err, pgx.ErrNoRows) {
			return problema.Conflicto("estado", "El link de conexión venció. Probá de nuevo.")
		}
		return err
	})
	if err != nil {
		return "", err
	}
	tk, err := s.MP.Canjear(ctx, codigo, s.vuelta(), verificador)
	if err != nil {
		return "", err
	}
	return lugarID, s.guardarTokens(ctx, lugarID, tk)
}

func (s *Servicio) guardarTokens(ctx context.Context, lugarID string, tk mercadopago.Tokens) error {
	acceso, err := s.Cifra.Cifrar("mp:"+lugarID, []byte(tk.AccessToken))
	if err != nil {
		return err
	}
	renov, err := s.Cifra.Cifrar("mp:"+lugarID, []byte(tk.RefreshToken))
	if err != nil {
		return err
	}
	vence := s.ahora().Add(time.Duration(tk.ExpiresIn) * time.Second)
	return s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		var otro bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM mp_conexiones WHERE mp_usuario = $1 AND lugar_id::text <> $2)`,
			tk.UserID, lugarID).Scan(&otro); err != nil {
			return err
		}
		if otro {
			return problema.Conflicto("mp_en_uso", "Esa cuenta de Mercado Pago ya está conectada a otro lugar de Caigo.")
		}
		_, err := tx.Exec(ctx, `INSERT INTO mp_conexiones (lugar_id, mp_usuario, acceso, renovacion, vence_en)
			VALUES ($1, $2, $3, $4, $5)
			ON CONFLICT (lugar_id) DO UPDATE SET mp_usuario = excluded.mp_usuario, acceso = excluded.acceso,
				renovacion = excluded.renovacion, vence_en = excluded.vence_en`, lugarID, tk.UserID, acceso, renov, vence)
		return err
	})
}

// DesconectarMP borra la conexión.
func (s *Servicio) DesconectarMP(ctx context.Context, usuarioID, lugarID string) error {
	return s.App.EnUsuario(ctx, usuarioID, func(tx pgx.Tx) error {
		if err := miembro(ctx, tx, lugarID, usuarioID, false); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `DELETE FROM mp_conexiones WHERE lugar_id::text = $1`, lugarID)
		return err
	})
}

// tokenDe devuelve el token de acceso del lugar, renovándolo si vence en menos de un día.
func (s *Servicio) tokenDe(ctx context.Context, lugarID string) (string, error) {
	if !s.mpActivo() {
		return "", errSinMP
	}
	var acceso, renov []byte
	var vence time.Time
	err := s.App.EnSistema(ctx, func(tx pgx.Tx) error {
		return tx.QueryRow(ctx, `SELECT acceso, renovacion, vence_en FROM mp_conexiones WHERE lugar_id::text = $1`, lugarID).Scan(&acceso, &renov, &vence)
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return "", problema.NoProcesable("sin_mp", "Este lugar todavía no conectó Mercado Pago.")
	}
	if err != nil {
		return "", err
	}
	tok, err := s.Cifra.Descifrar("mp:"+lugarID, acceso)
	if err != nil {
		return "", err
	}
	if vence.Sub(s.ahora()) > 24*time.Hour {
		return string(tok), nil
	}
	r, err := s.Cifra.Descifrar("mp:"+lugarID, renov)
	if err != nil {
		return "", err
	}
	tk, err := s.MP.Renovar(ctx, string(r))
	if err != nil {
		return "", err
	}
	if tk.UserID == 0 {
		var mp int64
		_ = s.App.EnSistema(ctx, func(tx pgx.Tx) error {
			return tx.QueryRow(ctx, `SELECT mp_usuario FROM mp_conexiones WHERE lugar_id::text = $1`, lugarID).Scan(&mp)
		})
		tk.UserID = mp
	}
	if err := s.guardarTokens(ctx, lugarID, tk); err != nil {
		return "", err
	}
	return tk.AccessToken, nil
}
