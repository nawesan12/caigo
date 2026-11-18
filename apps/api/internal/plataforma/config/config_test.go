package config

import "testing"

func TestEntornoInvalidoNoArranca(t *testing.T) {
	t.Setenv("CAIGO_ENTORNO", "prod")
	if _, err := Leer(); err == nil {
		t.Fatal("un entorno desconocido tiene que fallar")
	}
}

func TestDesarrolloTieneBaseLocal(t *testing.T) {
	t.Setenv("CAIGO_ENTORNO", "desarrollo")
	c, err := Leer()
	if err != nil {
		t.Fatal(err)
	}
	if err := c.Exigir("BaseApp", "BaseMigrador", "BaseAdmin"); err != nil {
		t.Fatal(err)
	}
}

func TestProduccionExigeLaBase(t *testing.T) {
	t.Setenv("CAIGO_ENTORNO", "produccion")
	t.Setenv("CAIGO_BASE_DE_DATOS", "")
	c, err := Leer()
	if err != nil {
		t.Fatal(err)
	}
	if err := c.Exigir("BaseApp"); err == nil {
		t.Fatal("sin base en producción tiene que fallar")
	}
}

// La vuelta del OAuth de Mercado Pago se arma con el panel: en producción tiene que ser absoluta.
func TestProduccionLeeElPanel(t *testing.T) {
	t.Setenv("CAIGO_ENTORNO", "produccion")
	t.Setenv("CAIGO_PANEL", "")
	c, err := Leer()
	if err != nil {
		t.Fatal(err)
	}
	if c.Panel != "https://lugares.caigo.com.ar" {
		t.Fatalf("panel por defecto: %q", c.Panel)
	}
	t.Setenv("CAIGO_PANEL", "https://panel.test")
	if c, _ = Leer(); c.Panel != "https://panel.test" {
		t.Fatalf("panel de CAIGO_PANEL: %q", c.Panel)
	}
}

func TestTopesDeIA(t *testing.T) {
	t.Setenv("CAIGO_ENTORNO", "desarrollo")
	t.Setenv("CAIGO_IA_TOPE_MES_USD", "")
	t.Setenv("CAIGO_IA_TOPE_DIA_USD", "0.5")
	c, err := Leer()
	if err != nil || c.IATopeMes != 15 || c.IATopeDia != 0.5 {
		t.Fatalf("topes: %v %v %v", c.IATopeMes, c.IATopeDia, err)
	}
	t.Setenv("CAIGO_IA_TOPE_MES_USD", "quince")
	if _, err := Leer(); err == nil {
		t.Fatal("un tope que no es número tiene que fallar")
	}
}
