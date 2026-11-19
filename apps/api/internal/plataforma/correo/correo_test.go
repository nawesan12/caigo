package correo_test

import (
	"bytes"
	"context"
	"io"
	"mime"
	"mime/multipart"
	"net"
	"net/mail"
	"strings"
	"testing"
	"time"

	"github.com/nawesan12/caigo/apps/api/internal/plataforma/correo"
)

// El mail con adjunto se arma como mixed { alternative { texto, html }, adjunto } y se lee de vuelta.
func TestArmarConAdjunto(t *testing.T) {
	m := correo.Mensaje{Para: "lucia@ejemplo.com", Asunto: "Plan confirmado — Pádel en Playa Grande", Texto: "Hola Lucía",
		HTML: "<p>Hola Lucía</p>" + strings.Repeat("x", 2000), Baja: "https://caigo.com.ar/baja",
		Adjuntos: []correo.Adjunto{{Nombre: "plan.ics", Tipo: "text/calendar; method=PUBLISH", Datos: []byte("BEGIN:VCALENDAR")}}}
	crudo, err := correo.Armar("Caigo <hola@caigo.com.ar>", m, time.Now())
	if err != nil {
		t.Fatal(err)
	}
	msj, err := mail.ReadMessage(bytes.NewReader(crudo))
	if err != nil {
		t.Fatal(err)
	}
	dec := new(mime.WordDecoder)
	asunto, _ := dec.DecodeHeader(msj.Header.Get("Subject"))
	if asunto != m.Asunto || msj.Header.Get("List-Unsubscribe") != "<https://caigo.com.ar/baja>" {
		t.Fatalf("encabezados: %v", msj.Header)
	}
	for _, linea := range strings.Split(string(crudo), "\r\n") {
		if len(linea) > 998 {
			t.Fatal("una línea pasa el límite de SMTP")
		}
	}
	_, params, _ := mime.ParseMediaType(msj.Header.Get("Content-Type"))
	r := multipart.NewReader(msj.Body, params["boundary"])
	var tipos []string
	for {
		p, err := r.NextPart()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		tipos = append(tipos, p.Header.Get("Content-Type"))
	}
	if len(tipos) != 2 || !strings.HasPrefix(tipos[0], "multipart/alternative") || !strings.HasPrefix(tipos[1], "text/calendar") {
		t.Fatalf("partes: %v", tipos)
	}
}

func TestSinInyeccionDeEncabezados(t *testing.T) {
	if _, err := correo.Armar("a@b.c", correo.Mensaje{Para: "x@y.z\r\nBcc: otro@z.z"}, time.Now()); err == nil {
		t.Fatal("aceptó un salto de línea en Para")
	}
}

func TestMemoria(t *testing.T) {
	m := &correo.Memoria{}
	_ = m.Enviar(context.Background(), correo.Mensaje{Para: "a@b.c"})
	if len(m.Para("a@b.c")) != 1 || m.Cantidad() != 1 {
		t.Fatal("memoria")
	}
}

// Un servidor que acepta la conexión y no contesta no traba el envío: vence el plazo.
func TestSMTPMudoVencePlazo(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			defer c.Close()
		}
	}()
	antes := correo.PlazoEnvio
	correo.PlazoEnvio = 300 * time.Millisecond
	defer func() { correo.PlazoEnvio = antes }()
	inicio := time.Now()
	err = (&correo.SMTP{Direccion: ln.Addr().String(), De: "Caigo <hola@caigo.com.ar>"}).Enviar(context.Background(),
		correo.Mensaje{Para: "a@b.com", Asunto: "x", Texto: "x", HTML: "x"})
	if err == nil {
		t.Fatal("un servidor mudo no puede dar el envío por bueno")
	}
	if d := time.Since(inicio); d > 3*time.Second {
		t.Fatalf("tardó %s: el plazo no se aplicó", d)
	}
}
