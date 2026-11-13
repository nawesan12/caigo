package planes

import "github.com/nawesan12/caigo/apps/api/internal/plataforma/problema"

// Confirma es lo que la persona confirma, sólo para un pedido (sumarse, comprar entrada, aceptar
// una invitación), cuando el plan es "Solo mujeres" o "Solo diversidades". Va dentro del cuerpo del
// pedido y no se guarda en ningún lado ni se deduce de otro dato: Caigo no guarda género,
// orientación ni identidad de género (AAIP 1.2; art. 2 y 7 de la Ley 25.326).
type Confirma struct {
	Mujer      bool `json:"mujer"`
	Diversidad bool `json:"diversidad"`
}

// ChequearGenero: generoPlan es el del plan (cualquiera | mujeres | diversidades).
func ChequearGenero(generoPlan string, c Confirma) error {
	switch {
	case generoPlan == "mujeres" && !c.Mujer:
		return problema.NoProcesable("confirmar_mujer", "Este plan es sólo para mujeres. Confirmalo para sumarte.")
	case generoPlan == "diversidades" && !c.Diversidad:
		return problema.NoProcesable("confirmar_diversidad", "Este plan es sólo para la diversidad LGBTIQ+. Confirmalo para sumarte.")
	}
	return nil
}
