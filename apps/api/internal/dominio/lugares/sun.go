package lugares

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/subtle"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"strings"
)

// Verificación de los mensajes SUN (Secure Unique NFC) de NTAG 424 DNA, según NXP AN12196, para
// la configuración más simple: el chip refleja picc_data (UID y contador cifrados con la clave)
// y cmac (MAC de la sesión sobre una entrada vacía), con la misma clave AES-128 para las dos cosas.

// ErrSUN: el mensaje no es de esta placa o se repitió.
var ErrSUN = errors.New("lugares: lectura SUN inválida")

// LecturaSUN descifrada.
type LecturaSUN struct {
	UID      [7]byte
	Contador uint32
}

// VerificarSUN descifra picc_data y verifica el cmac con la clave de la placa.
func VerificarSUN(clave []byte, piccHex, cmacHex string) (LecturaSUN, error) {
	var l LecturaSUN
	picc, err1 := hex.DecodeString(strings.TrimSpace(piccHex))
	mac, err2 := hex.DecodeString(strings.TrimSpace(cmacHex))
	if err1 != nil || err2 != nil || len(picc) != 16 || len(mac) != 8 || len(clave) != 16 {
		return l, ErrSUN
	}
	b, err := aes.NewCipher(clave)
	if err != nil {
		return l, ErrSUN
	}
	plano := make([]byte, 16)
	cipher.NewCBCDecrypter(b, make([]byte, 16)).CryptBlocks(plano, picc)
	// Tag: bit 7 = UID reflejado, bit 6 = contador reflejado, nibble bajo = largo del UID (7).
	if plano[0]&0xC0 != 0xC0 || plano[0]&0x0F != 7 {
		return l, ErrSUN
	}
	copy(l.UID[:], plano[1:8])
	l.Contador = uint32(plano[8]) | uint32(plano[9])<<8 | uint32(plano[10])<<16
	// Clave de sesión: CMAC(K, SV2), SV2 = 3CC3 0001 0080 || UID || contador (LSB primero).
	sv2 := append([]byte{0x3C, 0xC3, 0x00, 0x01, 0x00, 0x80}, plano[1:11]...)
	sesion := cmac(clave, sv2)
	completo := cmac(sesion, nil)
	// MAC truncado: los bytes impares.
	corto := make([]byte, 8)
	for i := range corto {
		corto[i] = completo[2*i+1]
	}
	if subtle.ConstantTimeCompare(corto, mac) != 1 {
		return l, ErrSUN
	}
	return l, nil
}

// cmac es AES-CMAC (RFC 4493).
func cmac(clave, msg []byte) []byte {
	b, _ := aes.NewCipher(clave)
	l := make([]byte, 16)
	b.Encrypt(l, l)
	k1 := doblar(l)
	k2 := doblar(k1)
	n := (len(msg) + 15) / 16
	completo := n > 0 && len(msg)%16 == 0
	if n == 0 {
		n = 1
	}
	ultimo := make([]byte, 16)
	if completo {
		copy(ultimo, msg[16*(n-1):])
		xor(ultimo, k1)
	} else {
		resto := msg[16*(n-1):]
		copy(ultimo, resto)
		ultimo[len(resto)] = 0x80
		xor(ultimo, k2)
	}
	x := make([]byte, 16)
	for i := 0; i < n-1; i++ {
		xor(x, msg[16*i:16*i+16])
		b.Encrypt(x, x)
	}
	xor(x, ultimo)
	b.Encrypt(x, x)
	return x
}

func doblar(b []byte) []byte {
	v := binary.BigEndian.Uint64(b[:8])
	w := binary.BigEndian.Uint64(b[8:])
	out := make([]byte, 16)
	binary.BigEndian.PutUint64(out[:8], v<<1|w>>63)
	binary.BigEndian.PutUint64(out[8:], w<<1)
	if b[0]&0x80 != 0 {
		out[15] ^= 0x87
	}
	return out
}

func xor(dst, src []byte) {
	for i := range dst {
		dst[i] ^= src[i]
	}
}
