// Package config lee la configuración de la API del entorno. Fuera de desarrollo, lo que falte
// hace que el proceso no arranque, en vez de arrancar a medias.
package config

import (
	"fmt"
	"os"
	"strings"
)

// Config de la API.
type Config struct {
	Entorno   string // desarrollo | staging | produccion
	Direccion string
	// URLs de Postgres por rol.
	BaseApp, BaseMigrador, BaseAdmin string
	// Sitio público (links de los mails, CORS).
	Sitio string
	// Clerk (ingreso de Grupo Vimo): clave secreta y publicable, y los orígenes web que pueden
	// pedir tokens (`azp`). Sin clave secreta, apagado.
	ClerkSecreto, ClerkPublicable string
	ClerkOrigenes                 []string
	// Push: APNs (clave .p8 compartida de Vimo, topic de la app) y FCM (cuenta de servicio de
	// Firebase). Sin configurar, esa plataforma queda apagada.
	APNsKeyID, APNsTeamID, APNsClave, APNsTopic string
	FCMCuenta                                   string
	// Clave para firmar QR de check-in, placas y links en vivo (32+ bytes al azar en producción).
	Secreto string
	// URL pública de la API (para armar las URLs de las fotos).
	APIPublica string
	// Carpeta de archivos subidos (fotos de perfil y de planes, DNI). En el VPS: /opt/caigo/archivos.
	Archivos string
}

// Desarrollo dice si se está corriendo en local.
func (c Config) Desarrollo() bool { return c.Entorno == "desarrollo" }

// Leer arma la configuración desde las variables CAIGO_*.
func Leer() (Config, error) {
	c := Config{
		Entorno:         valor("CAIGO_ENTORNO", "desarrollo"),
		Direccion:       valor("CAIGO_DIRECCION", ":4300"),
		BaseApp:         os.Getenv("CAIGO_BASE_DE_DATOS"),
		BaseMigrador:    os.Getenv("CAIGO_BASE_DE_DATOS_MIGRADOR"),
		BaseAdmin:       os.Getenv("CAIGO_BASE_DE_DATOS_ADMIN"),
		Sitio:           valor("CAIGO_SITIO", "https://caigo.com.ar"),
		ClerkSecreto:    os.Getenv("CAIGO_CLERK_SECRETO"),
		ClerkPublicable: os.Getenv("CAIGO_CLERK_PUBLICABLE"),
		ClerkOrigenes:   lista(valor("CAIGO_CLERK_ORIGENES", "https://caigo.com.ar,https://lugares.caigo.com.ar")),
		Archivos:        valor("CAIGO_ARCHIVOS", "/app/archivos"),
		APNsKeyID:       os.Getenv("CAIGO_APNS_KEY_ID"),
		APNsTeamID:      os.Getenv("CAIGO_APNS_TEAM_ID"),
		APNsClave:       os.Getenv("CAIGO_APNS_CLAVE"),
		APNsTopic:       valor("CAIGO_APNS_TOPIC", "ar.com.caigo.app"),
		FCMCuenta:       os.Getenv("CAIGO_FCM_CUENTA"),
		Secreto:         os.Getenv("CAIGO_SECRETO"),
		APIPublica:      valor("CAIGO_API_PUBLICA", "https://api.caigo.com.ar"),
	}
	switch c.Entorno {
	case "desarrollo", "staging", "produccion":
	default:
		return c, fmt.Errorf("CAIGO_ENTORNO=%q: tiene que ser desarrollo, staging o produccion", c.Entorno)
	}
	if c.Desarrollo() {
		// Postgres de infra/compose.dev.yml. La base de la app es caigo_dev (la crea el bootstrap
		// con el migrador como dueño); el admin entra a la base por defecto del contenedor.
		local := "postgres://%s@127.0.0.1:5448/%s?sslmode=disable"
		c.BaseApp = conDefecto(c.BaseApp, fmt.Sprintf(local, "caigo_app", "caigo_dev"))
		c.BaseMigrador = conDefecto(c.BaseMigrador, fmt.Sprintf(local, "caigo_migrador", "caigo_dev"))
		c.BaseAdmin = conDefecto(c.BaseAdmin, fmt.Sprintf(local, "caigo", "caigo"))
		c.Sitio = conDefecto(os.Getenv("CAIGO_SITIO"), "http://localhost:5173")
		c.Archivos = conDefecto(os.Getenv("CAIGO_ARCHIVOS"), "archivos")
		c.Secreto = conDefecto(c.Secreto, "secreto-de-desarrollo-no-usar-en-produccion")
		c.APIPublica = conDefecto(os.Getenv("CAIGO_API_PUBLICA"), "http://localhost:4300")
		if os.Getenv("CAIGO_CLERK_ORIGENES") == "" {
			c.ClerkOrigenes = []string{"http://localhost:5173", "http://lugares.localhost:5173"}
		}
	}
	return c, nil
}

// Exigir falla si falta alguna de las variables pedidas (por nombre de campo).
func (c Config) Exigir(campos ...string) error {
	vals := map[string]string{"BaseApp": c.BaseApp, "BaseMigrador": c.BaseMigrador, "BaseAdmin": c.BaseAdmin,
		"ClerkSecreto": c.ClerkSecreto, "ClerkPublicable": c.ClerkPublicable, "Secreto": c.Secreto}
	var faltan []string
	for _, f := range campos {
		if vals[f] == "" {
			faltan = append(faltan, f)
		}
	}
	if len(faltan) > 0 {
		return fmt.Errorf("falta configurar: %s", strings.Join(faltan, ", "))
	}
	return nil
}

// Hosts devuelve los hosts de los orígenes (para los patrones del WebSocket).
func (c Config) Hosts() []string {
	var out []string
	for _, o := range c.ClerkOrigenes {
		out = append(out, strings.TrimPrefix(strings.TrimPrefix(o, "https://"), "http://"))
	}
	return out
}

func lista(v string) []string {
	var out []string
	for _, p := range strings.Split(v, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}

func valor(k, def string) string { return conDefecto(os.Getenv(k), def) }

func conDefecto(v, def string) string {
	if v == "" {
		return def
	}
	return v
}
