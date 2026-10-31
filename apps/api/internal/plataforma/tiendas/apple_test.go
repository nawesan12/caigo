package tiendas

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"math/big"
	"testing"
	"time"
)

// cadena arma raíz → intermedia → hoja con los OIDs de Apple, para firmar JWS de prueba.
func cadena(t *testing.T, conOIDs bool) (*x509.CertPool, []*x509.Certificate, *ecdsa.PrivateKey) {
	t.Helper()
	nuevo := func(nombre string, padre *x509.Certificate, clavePadre *ecdsa.PrivateKey, ca bool, oid []int) (*x509.Certificate, *ecdsa.PrivateKey) {
		k, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
		tpl := &x509.Certificate{SerialNumber: big.NewInt(time.Now().UnixNano()), Subject: pkix.Name{CommonName: nombre},
			NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour), IsCA: ca, BasicConstraintsValid: true,
			KeyUsage: x509.KeyUsageDigitalSignature | x509.KeyUsageCertSign}
		if oid != nil && conOIDs {
			tpl.ExtraExtensions = []pkix.Extension{{Id: oid, Value: []byte{5, 0}}}
		}
		if padre == nil {
			padre, clavePadre = tpl, k
		}
		der, err := x509.CreateCertificate(rand.Reader, tpl, padre, &k.PublicKey, clavePadre)
		if err != nil {
			t.Fatal(err)
		}
		c, _ := x509.ParseCertificate(der)
		return c, k
	}
	raiz, kr := nuevo("raiz", nil, nil, true, nil)
	inter, ki := nuevo("inter", raiz, kr, true, oidIntermediaAppleWWD)
	hoja, kh := nuevo("hoja", inter, ki, false, oidHojaStoreKit)
	p := x509.NewCertPool()
	p.AddCert(raiz)
	return p, []*x509.Certificate{hoja, inter, raiz}, kh
}

func firmarJWS(t *testing.T, certs []*x509.Certificate, k *ecdsa.PrivateKey, payload any) string {
	enc := base64.RawURLEncoding
	var x5c []string
	for _, c := range certs {
		x5c = append(x5c, base64.StdEncoding.EncodeToString(c.Raw))
	}
	cab, _ := json.Marshal(map[string]any{"alg": "ES256", "x5c": x5c})
	cue, _ := json.Marshal(payload)
	firmable := enc.EncodeToString(cab) + "." + enc.EncodeToString(cue)
	h := sha256.Sum256([]byte(firmable))
	r, s, _ := ecdsa.Sign(rand.Reader, k, h[:])
	f := make([]byte, 64)
	r.FillBytes(f[:32])
	s.FillBytes(f[32:])
	return firmable + "." + enc.EncodeToString(f)
}

func TestTransaccionApple(t *testing.T) {
	raices, certs, k := cadena(t, true)
	a := &Apple{Bundle: "ar.com.caigo.app", Raices: raices}
	tx := map[string]any{"originalTransactionId": "2000", "bundleId": "ar.com.caigo.app", "productId": "plus_mes",
		"expiresDate": time.Now().Add(30 * 24 * time.Hour).UnixMilli(), "appAccountToken": "u-1"}
	got, err := a.Transaccion(firmarJWS(t, certs, k, tx))
	if err != nil || got.ProductID != "plus_mes" || got.Vence().Before(time.Now()) {
		t.Fatalf("%+v %v", got, err)
	}
	// Otra app: no.
	tx["bundleId"] = "ar.com.otra.app"
	if _, err := a.Transaccion(firmarJWS(t, certs, k, tx)); err == nil {
		t.Fatal("aceptó un JWS de otra app")
	}
	// Firma rota: no.
	tx["bundleId"] = "ar.com.caigo.app"
	j := firmarJWS(t, certs, k, tx)
	if _, err := a.VerificarJWS(j[:len(j)-4] + "AAAA"); err == nil {
		t.Fatal("aceptó una firma rota")
	}
	// Cadena sin los OIDs de Apple: no.
	raices2, certs2, k2 := cadena(t, false)
	if _, err := (&Apple{Raices: raices2}).VerificarJWS(firmarJWS(t, certs2, k2, tx)); err == nil {
		t.Fatal("aceptó una cadena sin OIDs de Apple")
	}
	// Con la raíz real de Apple, nuestra cadena de prueba no vale.
	if _, err := (&Apple{}).VerificarJWS(j); err == nil {
		t.Fatal("aceptó una cadena que no es de Apple")
	}
}

func TestRaizDeAppleEmbebida(t *testing.T) {
	b, _ := pem.Decode(raizApplePEM)
	c, err := x509.ParseCertificate(b.Bytes)
	if err != nil || c.Subject.CommonName != "Apple Root CA - G3" {
		t.Fatalf("%v %v", c, err)
	}
}
