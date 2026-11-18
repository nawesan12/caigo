package servidor_test

import (
	"net/http"
	"strings"
	"testing"
)

// Revisión de código del 18/11: lo que se cerró, con su prueba.

// El link «en vivo» deja de mostrar dirección y gente si a quien lo compartió la sacaron del plan o
// el plan se canceló.
func TestEnVivoSoloMientrasSigaEnElPlan(t *testing.T) {
	e := nuevoEntorno(t)
	_, lu, _, planID := planConLu(t, e)
	var link struct{ Link string }
	e.pedir(lu, "POST", "/v1/planes/"+planID+"/en-vivo", nil, http.StatusOK, &link)
	ruta := "/v1/publico/vivo/" + link.Link[strings.LastIndex(link.Link, "/")+1:]
	e.pedir(persona{}, "GET", ruta, nil, http.StatusOK, nil)

	e.sistema(`UPDATE solicitudes SET estado = 'rechazada' WHERE plan_id = $1 AND usuario_id = $2`, planID, lu.id)
	e.pedir(persona{}, "GET", ruta, nil, http.StatusNotFound, nil)

	e.sistema(`UPDATE solicitudes SET estado = 'aceptada' WHERE plan_id = $1 AND usuario_id = $2`, planID, lu.id)
	e.sistema(`UPDATE planes SET cancelado_en = now() WHERE id = $1`, planID)
	e.pedir(persona{}, "GET", ruta, nil, http.StatusNotFound, nil)
}
