package servidor_test

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/dominio/lugares"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/plus"
	"github.com/nawesan12/caigo/apps/api/internal/dominio/wallet"
	"github.com/nawesan12/caigo/apps/api/internal/integraciones/mercadopago"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/cifrado"
	"github.com/nawesan12/caigo/apps/api/internal/plataforma/servidor"
)

// tokenDelLugar es lo que devuelve el OAuth falso (no es una credencial).
const tokenDelLugar = "token-del-lugar-de-prueba"

// mpFalso: OAuth, preferencias y pagos de Mercado Pago, en memoria.
type mpFalso struct {
	*httptest.Server
	ref     string
	total   float64
	fee     float64
	estado  string
	usuario int64
	// Reembolsos pedidos: el cuerpo tal cual ("" = el pago entero).
	reembolsos []string
}

func nuevoMP(t *testing.T) *mpFalso {
	m := &mpFalso{estado: "approved", usuario: time.Now().UnixNano() % 1e12}
	m.Server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.URL.Path == "/oauth/token":
			fmt.Fprintf(w, `{"access_token":%q,"refresh_token":"renovar","expires_in":15552000,"user_id":%d}`, tokenDelLugar, m.usuario)
		case r.URL.Path == "/checkout/preferences":
			if strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ") != tokenDelLugar {
				w.WriteHeader(401)
				return
			}
			var in struct {
				ExternalReference string  `json:"external_reference"`
				MarketplaceFee    float64 `json:"marketplace_fee"`
				Items             []struct {
					UnitPrice float64 `json:"unit_price"`
				} `json:"items"`
			}
			_ = json.NewDecoder(r.Body).Decode(&in)
			m.ref, m.fee, m.total = in.ExternalReference, in.MarketplaceFee, in.Items[0].UnitPrice
			_, _ = w.Write([]byte(`{"id":"pref-1","init_point":"https://mp.test/checkout"}`))
		case r.URL.Path == "/v1/payments/99/refunds" && r.Method == http.MethodPost:
			if strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ") != tokenDelLugar || r.Header.Get("X-Idempotency-Key") == "" {
				w.WriteHeader(401)
				return
			}
			b, _ := io.ReadAll(r.Body)
			m.reembolsos = append(m.reembolsos, string(b))
			w.WriteHeader(201)
			_, _ = w.Write([]byte(`{"id":1,"status":"approved"}`))
		case r.URL.Path == "/v1/payments/99":
			fmt.Fprintf(w, `{"id":99,"status":%q,"external_reference":%q,"transaction_amount":%v}`, m.estado, m.ref, m.total)
		default:
			w.WriteHeader(404)
		}
	}))
	t.Cleanup(m.Close)
	return m
}

func TestLugarDePuntaAPunta(t *testing.T) {
	mp := nuevoMP(t)
	cfg := &mercadopago.Config{AppID: "1", Secreto: "s", SecretoWebhook: "wh", Base: mp.URL}
	cifra, _ := cifrado.Nuevo(base64.StdEncoding.EncodeToString(make([]byte, 32)))
	e := nuevoEntornoAjustado(t, func(d *servidor.Dependencias) {
		d.Tiendas = &plus.Tiendas{MP: cfg}
		d.Cifra = cifra
	})
	duena, equipo, martina := e.persona("Dueña"), e.persona("Equipo"), e.persona("Martina")
	e.alEquipo(equipo)
	e.verificar(martina, true)

	// Alta del lugar: queda pendiente y no puede publicar.
	var l struct{ ID, Estado string }
	e.pedir(duena, "POST", "/v1/lugares", map[string]any{"nombre": "Café Molienda", "rubro": "Café", "ciudad": "Mar del Plata",
		"zona": "Güemes", "direccion": "Güemes 3000", "lat": -38.01, "lng": -57.54}, http.StatusCreated, &l)
	if l.Estado != "pendiente" {
		t.Fatalf("estado: %q", l.Estado)
	}
	plan := map[string]any{"titulo": "Taller de cerámica y café", "categoria": "taller", "empiezaEn": time.Now().Add(20 * time.Minute),
		"duracionMin": 120, "cupo": 10, "aceptaGrupos": true, "cobro": "sena", "precioCentavos": 800000, "senaPct": 30,
		"cancelacion": "mod", "beneficio": "Café y medialuna", "quien": "cualquiera"}
	e.pedir(duena, "POST", "/v1/lugares/"+l.ID+"/planes", plan, http.StatusConflict, nil)
	// Otra persona no ve el lugar.
	e.pedir(martina, "GET", "/v1/lugares/"+l.ID, nil, http.StatusNotFound, nil)

	// El equipo lo aprueba.
	e.pedir(duena, "GET", "/v1/equipo/lugares", nil, http.StatusForbidden, nil)
	e.pedir(equipo, "POST", "/v1/equipo/lugares/"+l.ID, map[string]any{"aprobar": true}, http.StatusNoContent, nil)

	// Sin Mercado Pago no se cobran entradas; se conecta por OAuth.
	e.pedir(duena, "POST", "/v1/lugares/"+l.ID+"/planes", plan, http.StatusUnprocessableEntity, nil)
	var con struct{ Link string }
	e.pedir(duena, "POST", "/v1/lugares/"+l.ID+"/mercadopago", nil, http.StatusOK, &con)
	u, _ := url.Parse(con.Link)
	if u.Query().Get("code_challenge_method") != "S256" || u.Query().Get("redirect_uri") != "https://lugares.caigo.test/mercadopago/vuelta" {
		t.Fatalf("link: %s", con.Link)
	}
	// El state es de la dueña: otra persona no lo puede usar.
	e.pedir(martina, "POST", "/v1/mercadopago/vuelta", map[string]string{"codigo": "c", "estado": u.Query().Get("state")}, http.StatusConflict, nil)
	e.pedir(duena, "POST", "/v1/mercadopago/vuelta", map[string]string{"codigo": "c", "estado": u.Query().Get("state")}, http.StatusOK, nil)

	var creado struct{ ID string }
	e.pedir(duena, "POST", "/v1/lugares/"+l.ID+"/planes", plan, http.StatusCreated, &creado)
	var uno struct {
		SenaPct   int
		Beneficio string
	}
	e.pedir(duena, "GET", "/v1/lugares/"+l.ID+"/planes/"+creado.ID, nil, http.StatusOK, &uno)
	if uno.SenaPct != 30 || uno.Beneficio != "Café y medialuna" {
		t.Fatalf("plan del lugar: %+v", uno)
	}

	// La gente lo ve con entrada: no se "suma", compra.
	var visto struct {
		Cobro         string
		CargoCentavos int64
		Lugar         *struct{ Nombre string }
		Somos, Faltan int
	}
	e.pedir(martina, "GET", "/v1/planes/"+creado.ID, nil, http.StatusOK, &visto)
	if visto.Cobro != "sena" || visto.CargoCentavos != 60000 || visto.Lugar == nil || visto.Somos != 0 || visto.Faltan != 10 {
		t.Fatalf("plan visto: %+v", visto)
	}
	e.pedir(martina, "POST", "/v1/planes/"+creado.ID+"/sumarme", map[string]any{}, http.StatusConflict, nil)

	// Compra 2 con seña: 30 % de $8.000 = $2.400 + $600 de cargo, por 2; el resto ($11.200) en el lugar.
	var c struct {
		Link                                        string
		TotalCentavos, CargoCentavos, SaldoCentavos int64
	}
	e.pedir(martina, "POST", "/v1/planes/"+creado.ID+"/entradas", map[string]int{"cantidad": 2}, http.StatusOK, &c)
	if c.TotalCentavos != 600000 || c.CargoCentavos != 120000 || c.SaldoCentavos != 1120000 || mp.fee != 1200 || mp.total != 6000 {
		t.Fatalf("compra: %+v fee %v total %v", c, mp.fee, mp.total)
	}

	// Llega el pago (lo concilia el worker): Martina queda adentro con su código.
	ls := &lugares.Servicio{App: e.app, MP: cfg, Cifra: cifra}
	for range 2 { // idempotente
		if err := ls.ConciliarPago(context.Background(), mp.usuario, 99); err != nil {
			t.Fatal(err)
		}
	}
	var pases struct {
		Entradas []struct{ ID, Codigo, Estado string }
	}
	e.pedir(martina, "GET", "/v1/pases", nil, http.StatusOK, &pases)
	if len(pases.Entradas) != 1 || pases.Entradas[0].Estado != "pagada" || !strings.HasPrefix(pases.Entradas[0].Codigo, "TK-") {
		t.Fatalf("pases: %+v", pases)
	}
	e.pedir(martina, "GET", "/v1/planes/"+creado.ID, nil, http.StatusOK, &visto)
	if visto.Somos != 2 {
		t.Fatalf("somos: %d", visto.Somos)
	}

	// El lugar ve quiénes vienen y valida el código en la puerta.
	var as struct {
		Asistentes []struct{ Nombre, Pago string }
	}
	e.pedir(duena, "GET", "/v1/lugares/"+l.ID+"/planes/"+creado.ID+"/asistentes", nil, http.StatusOK, &as)
	if len(as.Asistentes) != 1 || as.Asistentes[0].Pago != "sena" {
		t.Fatalf("asistentes: %+v", as)
	}
	e.pedir(duena, "POST", "/v1/lugares/"+l.ID+"/validar", map[string]string{"codigo": "TK-0000"}, http.StatusNotFound, nil)
	var v struct {
		Cantidad      int
		SaldoCentavos int
		Beneficio     string
		Repetida      bool
	}
	e.pedir(duena, "POST", "/v1/lugares/"+l.ID+"/validar", map[string]string{"codigo": pases.Entradas[0].Codigo}, http.StatusOK, &v)
	if v.Cantidad != 2 || v.SaldoCentavos != 1120000 || v.Beneficio != "Café y medialuna" || v.Repetida {
		t.Fatalf("validar: %+v", v)
	}
	e.pedir(duena, "POST", "/v1/lugares/"+l.ID+"/validar", map[string]string{"codigo": pases.Entradas[0].Codigo}, http.StatusOK, &v)
	if !v.Repetida {
		t.Fatal("la segunda validación no avisó")
	}

	// Cobros: lo que recibe el lugar (las señas), sin el cargo.
	var cs struct {
		Cobros []struct {
			Entradas        int
			CobradoCentavos int64
		}
	}
	e.pedir(duena, "GET", "/v1/lugares/"+l.ID+"/cobros", nil, http.StatusOK, &cs)
	if len(cs.Cobros) != 1 || cs.Cobros[0].Entradas != 2 || cs.Cobros[0].CobradoCentavos != 480000 {
		t.Fatalf("cobros: %+v", cs)
	}

	// Caigo Tap con pase de socio: la placa común lleva un token firmado.
	e.pedir(duena, "PUT", "/v1/lugares/"+l.ID, map[string]any{"nombre": "Café Molienda", "ciudad": "Mar del Plata", "zona": "Güemes",
		"direccion": "Güemes 3000", "lat": -38.01, "lng": -57.54, "socioActivo": true, "socioVisitas": 2, "socioBeneficio": "Taller gratis"}, http.StatusOK, nil)
	var placa struct{ ID, URL string }
	e.pedir(duena, "POST", "/v1/lugares/"+l.ID+"/placas", map[string]any{"nombre": "Puerta principal", "uso": "puerta"}, http.StatusCreated, &placa)
	tok := placa.URL[strings.LastIndex(placa.URL, "/")+1:]
	// El id pelado (sin firma) no sirve.
	e.pedir(martina, "POST", "/v1/tap", map[string]string{"placa": placa.ID}, http.StatusNotFound, nil)
	var tap struct {
		Lugar string
		Plan  *struct {
			Titulo   string
			Repetido bool
		}
		Socio *struct {
			Visitas, Meta int
			Sumo          bool
		}
	}
	e.pedir(martina, "POST", "/v1/tap", map[string]string{"placa": tok}, http.StatusOK, &tap)
	if tap.Lugar != "Café Molienda" || tap.Plan == nil || !tap.Plan.Repetido || tap.Socio == nil || tap.Socio.Visitas != 1 || !tap.Socio.Sumo {
		t.Fatalf("tap: %+v plan %+v socio %+v", tap, tap.Plan, tap.Socio)
	}
	// Otro tap el mismo día no suma visita.
	e.pedir(martina, "POST", "/v1/tap", map[string]string{"placa": tok}, http.StatusOK, &tap)
	if tap.Socio.Sumo || tap.Socio.Visitas != 1 {
		t.Fatalf("segundo tap: %+v", tap.Socio)
	}
	// Sin Wallet configurada, el pase avisa que no está disponible.
	e.pedir(martina, "GET", "/v1/pases/entradas/"+pases.Entradas[0].ID+"/apple", nil, http.StatusUnprocessableEntity, nil)

	// En un plan con seña también se puede pagar todo: $8.000 + $600, sin saldo en el lugar.
	fede := e.persona("Fede")
	e.verificar(fede, false)
	e.pedir(fede, "POST", "/v1/planes/"+creado.ID+"/entradas", map[string]any{"cantidad": 1, "modo": "cualquiera"}, http.StatusBadRequest, nil)
	e.pedir(fede, "POST", "/v1/planes/"+creado.ID+"/entradas", map[string]any{"cantidad": 1, "modo": "entrada"}, http.StatusOK, &c)
	if c.TotalCentavos != 860000 || c.CargoCentavos != 60000 || c.SaldoCentavos != 0 || mp.total != 8600 {
		t.Fatalf("pagar todo: %+v total %v", c, mp.total)
	}
}

func TestPKPassFirmado(t *testing.T) {
	// Un intermedio de prueba en lugar del WWDR de Apple, y el certificado del pase firmado por él.
	ki, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	ti := &x509.Certificate{SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: "WWDR de prueba"}, IsCA: true,
		BasicConstraintsValid: true, KeyUsage: x509.KeyUsageCertSign, NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour)}
	deri, _ := x509.CreateCertificate(rand.Reader, ti, ti, &ki.PublicKey, ki)
	inter, _ := x509.ParseCertificate(deri)
	k, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	tpl := &x509.Certificate{SerialNumber: big.NewInt(2), Subject: pkix.Name{CommonName: "Pass Type ID: pass.ar.com.caigo"},
		NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour)}
	der, _ := x509.CreateCertificate(rand.Reader, tpl, inter, &k.PublicKey, ki)
	cert, _ := x509.ParseCertificate(der)
	a := &wallet.Apple{PassTypeID: "pass.ar.com.caigo", TeamID: "M6935MSNQB", Cert: cert, Clave: k,
		WebService: "https://api.caigo.test/v1/wallet", Secreto: []byte("x"), Intermedio: inter}
	b, err := a.PKPass(wallet.Pase{Serie: "e-1", Tipo: "entrada", Titulo: "Taller", Codigo: "TK-4821"})
	if err != nil || len(b) < 1000 || string(b[:2]) != "PK" {
		t.Fatalf("pkpass: %d bytes, %v", len(b), err)
	}
}
