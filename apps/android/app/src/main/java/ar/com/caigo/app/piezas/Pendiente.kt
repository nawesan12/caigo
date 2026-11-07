package ar.com.caigo.app.piezas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels

/** Marcador de una pantalla que todavía no está armada. */
@Composable
fun Pendiente(ir: Ir, titulo: String) {
    Pantalla { _, _ ->
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            MensajeCentro(titulo, "Esta pantalla está en camino.")
            Cta("Volver", { ir.atras() }, Modifier.width(200.dp), gel = Gels.violeta, alto = 52.dp, radio = 18.dp, tam = 16f, color = C.blanco)
        }
    }
}
