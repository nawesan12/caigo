package archivos

import (
	"bytes"
	"encoding/binary"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"testing"
)

// exifConGPS arma un APP1 con orientación 6 y un puntero a un IFD de GPS (con «-38.0» de mentira).
func exifConGPS() []byte {
	var t bytes.Buffer
	t.WriteString("MM\x00*")
	_ = binary.Write(&t, binary.BigEndian, uint32(8))
	_ = binary.Write(&t, binary.BigEndian, uint16(2))
	_ = binary.Write(&t, binary.BigEndian, []uint16{0x0112, 3})
	_ = binary.Write(&t, binary.BigEndian, uint32(1))
	_ = binary.Write(&t, binary.BigEndian, []uint16{6, 0})
	_ = binary.Write(&t, binary.BigEndian, []uint16{0x8825, 4}) // GPSInfo
	_ = binary.Write(&t, binary.BigEndian, uint32(1))
	_ = binary.Write(&t, binary.BigEndian, uint32(38))
	_ = binary.Write(&t, binary.BigEndian, uint32(0))
	t.WriteString("GPS-38.0055-57.5426")
	carga := append([]byte("Exif\x00\x00"), t.Bytes()...)
	seg := []byte{0xFF, 0xE1, 0, 0}
	binary.BigEndian.PutUint16(seg[2:], uint16(len(carga)+2))
	return append(seg, carga...)
}

func fotoJPEG(t *testing.T) []byte {
	img := image.NewRGBA(image.Rect(0, 0, 8, 8))
	img.Set(1, 1, color.RGBA{200, 10, 10, 255})
	var b bytes.Buffer
	if err := jpeg.Encode(&b, img, nil); err != nil {
		t.Fatal(err)
	}
	return b.Bytes()
}

func TestJPEGSinGPS(t *testing.T) {
	orig := fotoJPEG(t)
	con := append(append(append([]byte{}, orig[:2]...), exifConGPS()...), orig[2:]...)
	if !bytes.Contains(con, []byte("GPS-38")) {
		t.Fatal("la foto de prueba no tiene GPS")
	}
	limpia, err := SinMetadatos(con, "image/jpeg")
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(limpia, []byte("GPS-38")) {
		t.Fatal("quedó el GPS")
	}
	if o, ok := leerOrientacion(limpia[bytes.Index(limpia, []byte("Exif")):]); !ok || o != 6 {
		t.Fatalf("la orientación tenía que quedar en 6: %d %v", o, ok)
	}
	if _, err := jpeg.Decode(bytes.NewReader(limpia)); err != nil {
		t.Fatalf("la foto limpia no se lee: %v", err)
	}
	// Sin Exif, queda igual.
	igual, err := SinMetadatos(orig, "image/jpeg")
	if err != nil || !bytes.Equal(igual, orig) {
		t.Fatal("una foto sin metadatos tenía que quedar igual")
	}
}

func TestPNGSinTexto(t *testing.T) {
	var b bytes.Buffer
	_ = png.Encode(&b, image.NewGray(image.Rect(0, 0, 2, 2)))
	orig := b.Bytes()
	datos := []byte("Location\x00Mar del Plata")
	chunk := make([]byte, 4)
	binary.BigEndian.PutUint32(chunk, uint32(len(datos)))
	chunk = append(append(chunk, "tEXt"...), datos...)
	chunk = binary.BigEndian.AppendUint32(chunk, crcPNG("tEXt", datos))
	con := append(append(append([]byte{}, orig[:33]...), chunk...), orig[33:]...)
	limpia, err := SinMetadatos(con, "image/png")
	if err != nil || bytes.Contains(limpia, []byte("Mar del Plata")) {
		t.Fatalf("quedó el texto: %v", err)
	}
	if _, err := png.Decode(bytes.NewReader(limpia)); err != nil {
		t.Fatal(err)
	}
}

func TestHEICNoSeLimpia(t *testing.T) {
	if _, err := SinMetadatos([]byte("....ftypheic"), "image/heic"); err != ErrSinLimpiar {
		t.Fatal("HEIC tenía que rechazarse")
	}
}
