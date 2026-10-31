// Package wallet arma los pases de Caigo para Apple Wallet (.pkpass firmado con el certificado
// del Pass Type ID, con web service para actualizarlos) y Google Wallet (link "Guardar en Google
// Wallet" con un JWT firmado por la cuenta de servicio). Hay dos pases: la entrada a un plan de un
// lugar (formato evento, con el QR TK-xxxx) y el pase de socio de un lugar (visitas y beneficio).
//
// Sin certificado o sin emisor de Google, esa Wallet queda apagada y la app no muestra el botón.
package wallet

import (
	"archive/zip"
	"bytes"
	"context"
	"crypto"
	"crypto/hmac"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/smallstep/pkcs7"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/google"
)

//go:embed recursos/apple-wwdr-g4.pem
var wwdrPEM []byte

//go:embed recursos/icon.png
var icono []byte

//go:embed recursos/icon@2x.png
var icono2 []byte

//go:embed recursos/icon@3x.png
var icono3 []byte

//go:embed recursos/logo.png
var logo []byte

//go:embed recursos/logo@2x.png
var logo2 []byte

// Apple firma pases.
type Apple struct {
	PassTypeID, TeamID string
	Cert               *x509.Certificate
	Clave              crypto.PrivateKey
	// URL del web service (https://api.caigo.com.ar/v1/wallet) y clave para los tokens de cada pase.
	WebService string
	Secreto    []byte
	// Intermedio (tests). nil: Apple WWDR G4.
	Intermedio *x509.Certificate
}

// AppleDesde lee el certificado y la clave (PEM) del Pass Type ID. Rutas vacías: apagado (nil, nil).
func AppleDesde(passTypeID, teamID, rutaCert, rutaClave, webService string, secreto []byte) (*Apple, error) {
	if passTypeID == "" || rutaCert == "" || rutaClave == "" {
		return nil, nil
	}
	cb, err := os.ReadFile(rutaCert)
	if err != nil {
		return nil, fmt.Errorf("wallet: certificado: %w", err)
	}
	kb, err := os.ReadFile(rutaClave)
	if err != nil {
		return nil, fmt.Errorf("wallet: clave: %w", err)
	}
	par, err := tls.X509KeyPair(cb, kb)
	if err != nil {
		return nil, fmt.Errorf("wallet: el certificado y la clave no coinciden: %w", err)
	}
	cert, err := x509.ParseCertificate(par.Certificate[0])
	if err != nil {
		return nil, err
	}
	return &Apple{PassTypeID: passTypeID, TeamID: teamID, Cert: cert, Clave: par.PrivateKey, WebService: webService, Secreto: secreto}, nil
}

// TokenDe es el authenticationToken de un pase (HMAC del número de serie).
func (a *Apple) TokenDe(serie string) string {
	m := hmac.New(sha256.New, a.Secreto)
	m.Write([]byte("wallet:" + serie))
	return hex.EncodeToString(m.Sum(nil))
}

// Campo de un pase.
type Campo struct {
	Clave, Etiqueta, Valor string
}

// Pase con lo que comparten las dos Wallets.
type Pase struct {
	Serie       string
	Tipo        string // entrada | socio
	Titulo      string
	Lugar       string
	Primarios   []Campo
	Secundarios []Campo
	Auxiliares  []Campo
	Dorso       []Campo
	// QR (entrada: TK-4821) y su texto debajo.
	Codigo, TextoCodigo string
	// Cuándo y dónde se sugiere en la pantalla bloqueada.
	Cuando   time.Time
	Lat, Lng float64
	Cerca    string
	Anulado  bool
	Cambiado time.Time
}

func campos(cs []Campo) []map[string]string {
	out := []map[string]string{}
	for _, c := range cs {
		out = append(out, map[string]string{"key": c.Clave, "label": c.Etiqueta, "value": c.Valor})
	}
	return out
}

// PKPass arma el .pkpass firmado.
func (a *Apple) PKPass(p Pase) ([]byte, error) {
	if a == nil {
		return nil, errors.New("wallet: Apple Wallet sin configurar")
	}
	estilo := map[string]any{
		"primaryFields": campos(p.Primarios), "secondaryFields": campos(p.Secundarios),
		"auxiliaryFields": campos(p.Auxiliares), "backFields": campos(p.Dorso),
	}
	pj := map[string]any{
		"formatVersion": 1, "passTypeIdentifier": a.PassTypeID, "teamIdentifier": a.TeamID,
		"serialNumber": p.Serie, "organizationName": "Caigo", "description": p.Titulo,
		"logoText": "Caigo", "voided": p.Anulado,
		"webServiceURL": a.WebService, "authenticationToken": a.TokenDe(p.Serie),
	}
	if p.Tipo == "entrada" {
		pj["eventTicket"] = estilo
		pj["backgroundColor"], pj["foregroundColor"], pj["labelColor"] = "rgb(36,33,45)", "rgb(250,249,246)", "rgb(200,243,107)"
	} else {
		pj["storeCard"] = estilo
		pj["backgroundColor"], pj["foregroundColor"], pj["labelColor"] = "rgb(90,61,216)", "rgb(255,255,255)", "rgb(216,207,255)"
	}
	if p.Codigo != "" {
		pj["barcodes"] = []map[string]string{{"format": "PKBarcodeFormatQR", "message": p.Codigo,
			"messageEncoding": "iso-8859-1", "altText": p.TextoCodigo}}
	}
	if !p.Cuando.IsZero() {
		pj["relevantDate"] = p.Cuando.Format(time.RFC3339)
	}
	if p.Lat != 0 || p.Lng != 0 {
		pj["locations"] = []map[string]any{{"latitude": p.Lat, "longitude": p.Lng, "relevantText": p.Cerca}}
	}
	pjb, err := json.Marshal(pj)
	if err != nil {
		return nil, err
	}
	archivos := map[string][]byte{"pass.json": pjb, "icon.png": icono, "icon@2x.png": icono2, "icon@3x.png": icono3,
		"logo.png": logo, "logo@2x.png": logo2}
	manifiesto := map[string]string{}
	for n, b := range archivos {
		h := sha1.Sum(b)
		manifiesto[n] = hex.EncodeToString(h[:])
	}
	mb, _ := json.Marshal(manifiesto)
	firma, err := a.firmar(mb)
	if err != nil {
		return nil, err
	}
	archivos["manifest.json"], archivos["signature"] = mb, firma
	var buf bytes.Buffer
	z := zip.NewWriter(&buf)
	for n, b := range archivos {
		w, err := z.Create(n)
		if err != nil {
			return nil, err
		}
		if _, err := w.Write(b); err != nil {
			return nil, err
		}
	}
	if err := z.Close(); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

// firmar: PKCS#7 desacoplado del manifiesto, con el certificado del pase y el intermedio WWDR G4.
func (a *Apple) firmar(manifiesto []byte) ([]byte, error) {
	sd, err := pkcs7.NewSignedData(manifiesto)
	if err != nil {
		return nil, err
	}
	wwdr := a.Intermedio
	if wwdr == nil {
		b, _ := pem.Decode(wwdrPEM)
		if wwdr, err = x509.ParseCertificate(b.Bytes); err != nil {
			return nil, err
		}
	}
	if err := sd.AddSignerChain(a.Cert, a.Clave, []*x509.Certificate{wwdr}, pkcs7.SignerInfoConfig{}); err != nil {
		return nil, err
	}
	sd.Detach()
	return sd.Finish()
}

// Avisar le dice a un dispositivo que un pase cambió (APNs con el certificado del pase, payload vacío).
func (a *Apple) Avisar(ctx context.Context, pushToken string) error {
	if a == nil {
		return nil
	}
	cli := &http.Client{Timeout: 15 * time.Second, Transport: &http.Transport{ForceAttemptHTTP2: true,
		TLSClientConfig: &tls.Config{Certificates: []tls.Certificate{{Certificate: [][]byte{a.Cert.Raw}, PrivateKey: a.Clave}}}}}
	req, _ := http.NewRequestWithContext(ctx, http.MethodPost, "https://api.push.apple.com/3/device/"+pushToken, strings.NewReader("{}"))
	req.Header.Set("apns-topic", a.PassTypeID)
	res, err := cli.Do(req)
	if err != nil {
		return errors.New("wallet: no se pudo avisar a APNs")
	}
	res.Body.Close()
	if res.StatusCode != http.StatusOK && res.StatusCode != http.StatusGone {
		return fmt.Errorf("wallet: APNs respondió %d", res.StatusCode)
	}
	return nil
}

// Google arma links de Google Wallet.
type Google struct {
	Cuenta *google.Cuenta
	// Emisor de Google Wallet (Issuer ID de la consola).
	Emisor string
	// Orígenes desde donde se guarda (la web).
	Origenes []string
}

// Link devuelve la URL "Guardar en Google Wallet" del pase.
func (g *Google) Link(p Pase) (string, error) {
	if g == nil || g.Cuenta == nil || g.Emisor == "" {
		return "", errors.New("wallet: Google Wallet sin configurar")
	}
	id := g.Emisor + "." + strings.ReplaceAll(p.Serie, "-", "_")
	clase := g.Emisor + ".caigo_" + p.Tipo
	texto := func(cs []Campo) []map[string]any {
		out := []map[string]any{}
		for _, c := range cs {
			out = append(out, map[string]any{"id": c.Clave, "header": c.Etiqueta, "body": c.Valor})
		}
		return out
	}
	todos := append(append(append([]Campo{}, p.Secundarios...), p.Auxiliares...), p.Dorso...)
	obj := map[string]any{
		"id": id, "classId": clase, "state": "ACTIVE",
		"cardTitle":          map[string]any{"defaultValue": map[string]string{"language": "es-AR", "value": "Caigo"}},
		"header":             map[string]any{"defaultValue": map[string]string{"language": "es-AR", "value": p.Titulo}},
		"subheader":          map[string]any{"defaultValue": map[string]string{"language": "es-AR", "value": p.Lugar}},
		"textModulesData":    texto(todos),
		"hexBackgroundColor": map[bool]string{true: "#24212D", false: "#5A3DD8"}[p.Tipo == "entrada"],
		"logo":               map[string]any{"sourceUri": map[string]string{"uri": "https://caigo.com.ar/caigo-icon.png"}},
	}
	if p.Anulado {
		obj["state"] = "INACTIVE"
	}
	if p.Codigo != "" {
		obj["barcode"] = map[string]string{"type": "QR_CODE", "value": p.Codigo, "alternateText": p.TextoCodigo}
	}
	if p.Lat != 0 || p.Lng != 0 {
		obj["locations"] = []map[string]float64{{"latitude": p.Lat, "longitude": p.Lng}}
	}
	reclamos := map[string]any{
		"iss": g.Cuenta.Email, "aud": "google", "typ": "savetowallet", "origins": g.Origenes,
		"payload": map[string]any{
			"genericClasses": []map[string]any{{"id": clase}},
			"genericObjects": []map[string]any{obj},
		},
	}
	jwt, err := g.Cuenta.Firmar(reclamos)
	if err != nil {
		return "", err
	}
	return "https://pay.google.com/gp/v/save/" + jwt, nil
}
