package lugares

import (
	"crypto/aes"
	"crypto/cipher"
	"encoding/hex"
	"testing"
)

// Vectores de RFC 4493 (AES-CMAC).
func TestCMAC(t *testing.T) {
	k, _ := hex.DecodeString("2b7e151628aed2a6abf7158809cf4f3c")
	casos := map[string]string{
		"":                                 "bb1d6929e95937287fa37d129b756746",
		"6bc1bee22e409f96e93d7e117393172a": "070a16b46b4d4144f79bdd9dd04a287c",
		"6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411": "dfa66747de9ae63030ca32611497c827",
	}
	for m, want := range casos {
		msg, _ := hex.DecodeString(m)
		if got := hex.EncodeToString(cmac(k, msg)); got != want {
			t.Errorf("cmac(%s) = %s, quería %s", m, got, want)
		}
	}
}

// Una lectura armada como la arma el chip se verifica; cambiada, no.
func TestSUN(t *testing.T) {
	clave, _ := hex.DecodeString("00112233445566778899aabbccddeeff")
	uid := []byte{0x04, 0xDE, 0x5F, 0x1E, 0xAC, 0xC0, 0x40}
	plano := append([]byte{0xC7}, uid...)
	plano = append(plano, 0x3D, 0x00, 0x00, 1, 2, 3, 4, 5)
	b, _ := aes.NewCipher(clave)
	picc := make([]byte, 16)
	cipher.NewCBCEncrypter(b, make([]byte, 16)).CryptBlocks(picc, plano)
	sesion := cmac(clave, append([]byte{0x3C, 0xC3, 0x00, 0x01, 0x00, 0x80}, plano[1:11]...))
	full := cmac(sesion, nil)
	mac := make([]byte, 8)
	for i := range mac {
		mac[i] = full[2*i+1]
	}
	l, err := VerificarSUN(clave, hex.EncodeToString(picc), hex.EncodeToString(mac))
	if err != nil || l.Contador != 0x3D || l.UID[0] != 0x04 {
		t.Fatalf("%+v %v", l, err)
	}
	mac[0] ^= 1
	if _, err := VerificarSUN(clave, hex.EncodeToString(picc), hex.EncodeToString(mac)); err == nil {
		t.Fatal("aceptó un MAC cambiado")
	}
}
