package servidor_test

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// Las entradas que se cobraron con una preferencia (antes de la API de Orders) siguen andando: el
// aviso payment las concilia y se devuelven por /v1/payments/{id}/refunds.
func TestEntradasConPreferenciaSiguen(t *testing.T) {
	e, mp, ls, duena, lugarID := lugarConMP(t)
	martina := e.persona("Martina")
	e.verificar(martina, false)
	var p struct{ ID string }
	e.pedir(duena, "POST", "/v1/lugares/"+lugarID+"/planes", map[string]any{"titulo": "Noche de preferencias", "categoria": "musica",
		"empiezaEn": time.Now().Add(48 * time.Hour), "cupo": 20, "cobro": "entrada", "precioCentavos": 800000,
		"cancelacion": "flex"}, http.StatusCreated, &p)
	var c struct{ EntradaID string }
	e.pedir(martina, "POST", "/v1/planes/"+p.ID+"/entradas", map[string]int{"cantidad": 1}, http.StatusOK, &c)
	// Como si se hubiera creado antes del cambio.
	e.sistema(`UPDATE entradas SET mp_orden = NULL, mp_preferencia = 'pref-1' WHERE id = '`+c.EntradaID+`'`)

	aviso := fmt.Sprintf(`{"type":"payment","action":"payment.created","user_id":%d,"data":{"id":"99"}}`, mp.usuario)
	r := httptest.NewRequest("POST", "/v1/webhooks/mercadopago?data.id=99", strings.NewReader(aviso))
	r.Header.Set("x-signature", firmaMP("wh", "req-p", "99", time.Now().UnixMilli()))
	r.Header.Set("x-request-id", "req-p")
	w := httptest.NewRecorder()
	e.h.ServeHTTP(w, r)
	if w.Code != http.StatusOK {
		t.Fatalf("aviso del pago: %d %s", w.Code, w.Body)
	}
	evs := e.eventos("mp.pago", fmt.Sprint(mp.usuario))
	if len(evs) != 1 {
		t.Fatalf("mp.pago: %+v", evs)
	}
	if err := ls.Manejadores()["mp.pago"](context.Background(), evs[0]); err != nil {
		t.Fatal(err)
	}
	var estado string
	e.valor(`SELECT estado FROM entradas WHERE id = $1`, &estado, c.EntradaID)
	if estado != "pagada" {
		t.Fatalf("estado: %q", estado)
	}
	// flex a 48 h: se devuelve lo del lugar, por la API de pagos.
	e.pedir(martina, "DELETE", "/v1/entradas/"+c.EntradaID, nil, http.StatusOK, nil)
	if len(mp.reembolsos) != 1 || mp.reembolsos[0] != `pago:{"amount":8000}` {
		t.Fatalf("reembolso: %q", mp.reembolsos)
	}
}

// Un aviso de una orden que no es la de la entrada (o una sin pagar) no la toca.
func TestAvisoDeOtraOrden(t *testing.T) {
	e, mp, ls, duena, lugarID := lugarConMP(t)
	ana := e.persona("Ana")
	e.verificar(ana, false)
	var p struct{ ID string }
	e.pedir(duena, "POST", "/v1/lugares/"+lugarID+"/planes", map[string]any{"titulo": "Noche de órdenes", "categoria": "musica",
		"empiezaEn": time.Now().Add(48 * time.Hour), "cupo": 20, "cobro": "entrada", "precioCentavos": 800000,
		"cancelacion": "flex"}, http.StatusCreated, &p)
	var c struct{ EntradaID string }
	e.pedir(ana, "POST", "/v1/planes/"+p.ID+"/entradas", map[string]int{"cantidad": 1}, http.StatusOK, &c)
	// La orden falla: sigue pendiente.
	mp.estadoOrden = "failed"
	if err := ls.ConciliarOrden(context.Background(), mp.usuario, mp.ultimaOrden()); err != nil {
		t.Fatal(err)
	}
	var estado string
	e.valor(`SELECT estado FROM entradas WHERE id = $1`, &estado, c.EntradaID)
	if estado != "pendiente" {
		t.Fatalf("tras el rechazo: %q", estado)
	}
	// Otra orden con la misma referencia (no es la de la entrada): no se aplica.
	mp.estadoOrden = "processed"
	e.sistema(`UPDATE entradas SET mp_orden = 'ord-otra' WHERE id = '` + c.EntradaID + `'`)
	if err := ls.ConciliarOrden(context.Background(), mp.usuario, mp.ultimaOrden()); err != nil {
		t.Fatal(err)
	}
	e.valor(`SELECT estado FROM entradas WHERE id = $1`, &estado, c.EntradaID)
	if estado != "pendiente" {
		t.Fatalf("con otra orden: %q", estado)
	}
	// Una orden que Mercado Pago no conoce no se reintenta.
	if err := ls.ConciliarOrden(context.Background(), mp.usuario, "ord-999"); err != nil {
		t.Fatal(err)
	}
}
