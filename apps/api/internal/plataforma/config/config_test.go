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
