package archivos

import (
	"bytes"
	"encoding/binary"
	"errors"
	"hash/crc32"
	"io"
	"path/filepath"
)

// ErrSinLimpiar: un formato del que no sabemos sacar los metadatos (HEIC). Las apps y la web suben JPEG.
var ErrSinLimpiar = errors.New("archivos: subí la foto en JPG, PNG o WebP")

// GuardarLimpia es Guardar sin los metadatos de la foto (ubicación GPS, fecha y hora, modelo del
// teléfono): las fotos que ven otras personas no tienen que decir dónde se sacaron. De la orientación
// del EXIF queda sólo eso, para que la foto no se vea de costado.
func (d Disco) GuardarLimpia(carpeta string, r io.Reader) (ruta, contenido string, n int, err error) {
	b, contenido, ext, err := leerFoto(r)
	if err != nil {
		return "", "", 0, err
	}
	if b, err = SinMetadatos(b, contenido); err != nil {
		return "", "", 0, err
	}
	ruta = nuevaRuta(carpeta, ext)
	if err := escribir(filepath.Join(d.Raiz, ruta), b); err != nil {
		return "", "", 0, err
	}
	return ruta, contenido, len(b), nil
}

// SinMetadatos saca los metadatos de una foto sin volver a comprimirla.
func SinMetadatos(b []byte, contenido string) ([]byte, error) {
	switch contenido {
	case "image/jpeg":
		return jpegLimpio(b)
	case "image/png":
		return pngLimpio(b)
	case "image/webp":
		return webpLimpio(b)
	}
	return nil, ErrSinLimpiar
}

// ErrMalFormada: la foto no se pudo leer.
var ErrMalFormada = errors.New("archivos: la foto está dañada")

// jpegLimpio saca los segmentos APP1 (Exif, XMP), APP12/APP13 (IPTC, Photoshop) y los comentarios. Si el
// Exif tenía una orientación distinta de la normal, deja un Exif mínimo con sólo esa etiqueta.
func jpegLimpio(b []byte) ([]byte, error) {
	if len(b) < 4 || b[0] != 0xFF || b[1] != 0xD8 {
		return nil, ErrMalFormada
	}
	out := bytes.NewBuffer(make([]byte, 0, len(b)))
	out.Write(b[:2])
	orientacion := uint16(1)
	i := 2
	for i+4 <= len(b) {
		if b[i] != 0xFF {
			return nil, ErrMalFormada
		}
		marca := b[i+1]
		if marca == 0xFF { // relleno
			i++
			continue
		}
		if marca == 0xDA || marca == 0xD9 { // empieza la imagen: lo que sigue va tal cual
			if orientacion != 1 {
				out.Write(exifOrientacion(orientacion))
			}
			out.Write(b[i:])
			return out.Bytes(), nil
		}
		largo := int(binary.BigEndian.Uint16(b[i+2 : i+4]))
		if largo < 2 || i+2+largo > len(b) {
			return nil, ErrMalFormada
		}
		seg := b[i : i+2+largo]
		switch {
		case marca == 0xE1:
			if o, ok := leerOrientacion(seg[4:]); ok {
				orientacion = o
			}
		case marca == 0xEC || marca == 0xED || marca == 0xFE:
		default:
			out.Write(seg)
		}
		i += 2 + largo
	}
	return nil, ErrMalFormada
}

// leerOrientacion lee la etiqueta 0x0112 del IFD0 de un segmento «Exif\0\0…».
func leerOrientacion(p []byte) (uint16, bool) {
	if len(p) < 14 || string(p[:6]) != "Exif\x00\x00" {
		return 0, false
	}
	t := p[6:]
	var bo binary.ByteOrder
	switch string(t[:2]) {
	case "II":
		bo = binary.LittleEndian
	case "MM":
		bo = binary.BigEndian
	default:
		return 0, false
	}
	ifd := int(bo.Uint32(t[4:8]))
	if ifd+2 > len(t) {
		return 0, false
	}
	n := int(bo.Uint16(t[ifd : ifd+2]))
	for k := 0; k < n; k++ {
		e := ifd + 2 + 12*k
		if e+12 > len(t) {
			return 0, false
		}
		if bo.Uint16(t[e:e+2]) == 0x0112 {
			o := bo.Uint16(t[e+8 : e+10])
			if o >= 1 && o <= 8 {
				return o, true
			}
			return 0, false
		}
	}
	return 0, false
}

// exifOrientacion arma un APP1 con un Exif que tiene sólo la orientación.
func exifOrientacion(o uint16) []byte {
	var t bytes.Buffer
	t.WriteString("II*\x00")
	_ = binary.Write(&t, binary.LittleEndian, uint32(8)) // IFD0
	_ = binary.Write(&t, binary.LittleEndian, uint16(1)) // una entrada
	_ = binary.Write(&t, binary.LittleEndian, []uint16{0x0112, 3})
	_ = binary.Write(&t, binary.LittleEndian, uint32(1))
	_ = binary.Write(&t, binary.LittleEndian, []uint16{o, 0})
	_ = binary.Write(&t, binary.LittleEndian, uint32(0)) // no hay otro IFD
	carga := append([]byte("Exif\x00\x00"), t.Bytes()...)
	seg := []byte{0xFF, 0xE1, 0, 0}
	binary.BigEndian.PutUint16(seg[2:], uint16(len(carga)+2))
	return append(seg, carga...)
}

// pngLimpio saca los chunks de texto, Exif y fecha (tEXt, zTXt, iTXt, eXIf, tIME).
func pngLimpio(b []byte) ([]byte, error) {
	const firma = 8
	if len(b) < firma {
		return nil, ErrMalFormada
	}
	out := bytes.NewBuffer(make([]byte, 0, len(b)))
	out.Write(b[:firma])
	i := firma
	for i+12 <= len(b) {
		largo := int(binary.BigEndian.Uint32(b[i : i+4]))
		if largo < 0 || i+12+largo > len(b) {
			return nil, ErrMalFormada
		}
		tipo := string(b[i+4 : i+8])
		chunk := b[i : i+12+largo]
		switch tipo {
		case "tEXt", "zTXt", "iTXt", "eXIf", "tIME":
		default:
			out.Write(chunk)
		}
		i += 12 + largo
		if tipo == "IEND" {
			return out.Bytes(), nil
		}
	}
	return nil, ErrMalFormada
}

// webpLimpio saca los chunks EXIF y XMP y apaga sus marcas en VP8X.
func webpLimpio(b []byte) ([]byte, error) {
	if len(b) < 12 {
		return nil, ErrMalFormada
	}
	var cuerpo bytes.Buffer
	i := 12
	for i+8 <= len(b) {
		tipo := string(b[i : i+4])
		largo := int(binary.LittleEndian.Uint32(b[i+4 : i+8]))
		fin := i + 8 + largo + largo%2
		if largo < 0 || i+8+largo > len(b) {
			return nil, ErrMalFormada
		}
		if fin > len(b) {
			fin = len(b)
		}
		chunk := append([]byte(nil), b[i:fin]...)
		switch tipo {
		case "EXIF", "XMP ":
		case "VP8X":
			if len(chunk) > 8 {
				chunk[8] &^= 0x08 | 0x04
			}
			cuerpo.Write(chunk)
		default:
			cuerpo.Write(chunk)
		}
		i = fin
	}
	out := make([]byte, 12, 12+cuerpo.Len())
	copy(out, b[:12])
	binary.LittleEndian.PutUint32(out[4:8], uint32(4+cuerpo.Len()))
	return append(out, cuerpo.Bytes()...), nil
}

// crcPNG (pruebas): el CRC de un chunk.
func crcPNG(tipo string, datos []byte) uint32 {
	return crc32.ChecksumIEEE(append([]byte(tipo), datos...))
}
