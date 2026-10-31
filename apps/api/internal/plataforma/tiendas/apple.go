// Package tiendas valida las compras de Caigo Plus en las tiendas: App Store (StoreKit 2, JWS
// firmados por Apple) y Google Play (Play Developer API). Nunca se cree lo que diga la app: la
// firma de Apple se verifica contra su raíz, y a Google se le pregunta el estado.
package tiendas

import (
	"crypto/ecdsa"
	"crypto/sha256"
	"crypto/x509"
	_ "embed"
	"encoding/asn1"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"math/big"
	"strings"
	"time"
)

// Apple Root CA - G3 (https://www.apple.com/certificateauthority/), SHA-256
// 63:34:3A:BF:B8:9A:6A:03:EB:B5:7E:9B:3F:5F:A7:BE:7C:4F:5C:75:6F:30:17:B3:A8:C4:88:C3:65:3E:91:79.
//
//go:embed apple-root-ca-g3.pem
var raizApplePEM []byte

// ErrFirma: el JWS no lo firmó Apple (o está mal formado).
var ErrFirma = errors.New("tiendas: firma de Apple inválida")

// OIDs que Apple pone en los certificados de firma de StoreKit.
var (
	oidHojaStoreKit       = asn1.ObjectIdentifier{1, 2, 840, 113635, 100, 6, 11, 1}
	oidIntermediaAppleWWD = asn1.ObjectIdentifier{1, 2, 840, 113635, 100, 6, 2, 1}
)

// Apple verifica JWS de StoreKit.
type Apple struct {
	// Bundle de la app (ar.com.caigo.app). Un JWS de otra app no sirve.
	Bundle string
	// Raices (tests). nil: Apple Root CA - G3.
	Raices *x509.CertPool
	Ahora  func() time.Time
}

func (a *Apple) raices() *x509.CertPool {
	if a.Raices != nil {
		return a.Raices
	}
	p := x509.NewCertPool()
	b, _ := pem.Decode(raizApplePEM)
	c, _ := x509.ParseCertificate(b.Bytes)
	p.AddCert(c)
	return p
}

// VerificarJWS valida la cadena x5c contra la raíz de Apple, la firma ES256 y devuelve el payload.
func (a *Apple) VerificarJWS(jws string) ([]byte, error) {
	partes := strings.Split(jws, ".")
	if len(partes) != 3 || len(jws) > 64<<10 {
		return nil, ErrFirma
	}
	enc := base64.RawURLEncoding
	cabB, err := enc.DecodeString(partes[0])
	if err != nil {
		return nil, ErrFirma
	}
	var cab struct {
		Alg string   `json:"alg"`
		X5c []string `json:"x5c"`
	}
	if json.Unmarshal(cabB, &cab) != nil || cab.Alg != "ES256" || len(cab.X5c) < 2 {
		return nil, ErrFirma
	}
	var certs []*x509.Certificate
	for _, c := range cab.X5c {
		der, err := base64.StdEncoding.DecodeString(c)
		if err != nil {
			return nil, ErrFirma
		}
		x, err := x509.ParseCertificate(der)
		if err != nil {
			return nil, ErrFirma
		}
		certs = append(certs, x)
	}
	hoja := certs[0]
	inter := x509.NewCertPool()
	for _, c := range certs[1:] {
		inter.AddCert(c)
	}
	ahora := time.Now()
	if a.Ahora != nil {
		ahora = a.Ahora()
	}
	if _, err := hoja.Verify(x509.VerifyOptions{Roots: a.raices(), Intermediates: inter, CurrentTime: ahora,
		KeyUsages: []x509.ExtKeyUsage{x509.ExtKeyUsageAny}}); err != nil {
		return nil, ErrFirma
	}
	if !tieneExtension(hoja, oidHojaStoreKit) || !tieneExtension(certs[1], oidIntermediaAppleWWD) {
		return nil, ErrFirma
	}
	clave, ok := hoja.PublicKey.(*ecdsa.PublicKey)
	firma, err := enc.DecodeString(partes[2])
	if !ok || err != nil || len(firma) != 64 {
		return nil, ErrFirma
	}
	h := sha256.Sum256([]byte(partes[0] + "." + partes[1]))
	if !ecdsa.Verify(clave, h[:], new(big.Int).SetBytes(firma[:32]), new(big.Int).SetBytes(firma[32:])) {
		return nil, ErrFirma
	}
	return enc.DecodeString(partes[1])
}

func tieneExtension(c *x509.Certificate, oid asn1.ObjectIdentifier) bool {
	for _, e := range c.Extensions {
		if e.Id.Equal(oid) {
			return true
		}
	}
	return false
}

// TransaccionApple es lo que importa de un JWSTransactionDecodedPayload.
type TransaccionApple struct {
	OriginalTransactionID string `json:"originalTransactionId"`
	TransactionID         string `json:"transactionId"`
	BundleID              string `json:"bundleId"`
	ProductID             string `json:"productId"`
	// appAccountToken: la app lo pone con el id de la persona en Caigo al comprar.
	AppAccountToken string `json:"appAccountToken"`
	ExpiresDate     int64  `json:"expiresDate"`
	RevocationDate  int64  `json:"revocationDate"`
	OfferType       int    `json:"offerType"`
	Environment     string `json:"environment"`
}

// Vence cuándo vence la transacción (cero si no tiene vencimiento).
func (t TransaccionApple) Vence() time.Time {
	if t.ExpiresDate == 0 {
		return time.Time{}
	}
	return time.UnixMilli(t.ExpiresDate)
}

// Transaccion valida un JWS de transacción de StoreKit 2 (Transaction.jwsRepresentation).
func (a *Apple) Transaccion(jws string) (TransaccionApple, error) {
	b, err := a.VerificarJWS(jws)
	if err != nil {
		return TransaccionApple{}, err
	}
	var t TransaccionApple
	if err := json.Unmarshal(b, &t); err != nil || t.OriginalTransactionID == "" {
		return t, ErrFirma
	}
	if a.Bundle != "" && t.BundleID != a.Bundle {
		return t, ErrFirma
	}
	return t, nil
}

// NotificacionApple es una App Store Server Notification V2 ya verificada.
type NotificacionApple struct {
	Tipo, Subtipo string
	Transaccion   *TransaccionApple
	// Renueva: autoRenewStatus de signedRenewalInfo (nil si no vino).
	Renueva *bool
}

// Notificacion valida el signedPayload de una notificación del servidor de Apple.
func (a *Apple) Notificacion(signedPayload string) (NotificacionApple, error) {
	b, err := a.VerificarJWS(signedPayload)
	if err != nil {
		return NotificacionApple{}, err
	}
	var n struct {
		NotificationType string `json:"notificationType"`
		Subtype          string `json:"subtype"`
		Data             struct {
			BundleID              string `json:"bundleId"`
			SignedTransactionInfo string `json:"signedTransactionInfo"`
			SignedRenewalInfo     string `json:"signedRenewalInfo"`
		} `json:"data"`
	}
	if err := json.Unmarshal(b, &n); err != nil {
		return NotificacionApple{}, ErrFirma
	}
	out := NotificacionApple{Tipo: n.NotificationType, Subtipo: n.Subtype}
	if a.Bundle != "" && n.Data.BundleID != "" && n.Data.BundleID != a.Bundle {
		return out, ErrFirma
	}
	if n.Data.SignedTransactionInfo != "" {
		t, err := a.Transaccion(n.Data.SignedTransactionInfo)
		if err != nil {
			return out, err
		}
		out.Transaccion = &t
	}
	if n.Data.SignedRenewalInfo != "" {
		rb, err := a.VerificarJWS(n.Data.SignedRenewalInfo)
		if err != nil {
			return out, err
		}
		var r struct {
			AutoRenewStatus *int `json:"autoRenewStatus"`
		}
		if json.Unmarshal(rb, &r) == nil && r.AutoRenewStatus != nil {
			v := *r.AutoRenewStatus == 1
			out.Renueva = &v
		}
	}
	return out, nil
}
