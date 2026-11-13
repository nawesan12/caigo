package ar.com.caigo.app.pantallas.ingreso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Contenedor
import ar.com.caigo.app.nucleo.Confirma
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.Generos
import ar.com.caigo.app.pantallas.mios.MiosHoja
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Interruptor
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T

/**
 * «Antes de sumarte»: aparece cuando la API responde 422 `confirmar_mujer` (plan «Solo mujeres») o
 * `confirmar_diversidad` (plan «Solo diversidades»). La persona lo confirma (el interruptor arranca
 * apagado) y se reintenta el pedido con esa confirmación. Caigo no guarda género, orientación ni
 * identidad de género: la confirmación vale sólo para ese pedido (AAIP 1.2).
 * Responde a [Contenedor.confirmar] (la hoja global; en iOS cada pantalla tiene la suya).
 * No está en el handoff: se arma con la hoja de Mis planes y el interruptor del alta.
 */
@Composable
fun BoxScope.HojaAntesDeSumarte(c: Contenedor) {
    val p = LocalPaleta.current
    val pregunta by c.pregunta.collectAsState()
    var soy by remember { mutableStateOf(false) }
    // El último pedido: el texto no cambia mientras la hoja se va.
    var ultimo by remember { mutableStateOf("") }
    LaunchedEffect(pregunta) {
        soy = false
        pregunta?.let { ultimo = it.pide }
    }
    val mujer = (pregunta?.pide ?: ultimo) == "mujer"
    MiosHoja(pregunta != null, { pregunta?.respuesta?.complete(false) }, "Antes de sumarte") {
        T(if (mujer) "Este plan es sólo para mujeres." else "Este plan es sólo para la diversidad LGBTIQ+.", 15f, 400, p.texto, alto = 1.45f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            T(if (mujer) "Soy mujer" else "Soy parte de la diversidad LGBTIQ+", 14f, 600, p.texto, Modifier.weight(1f))
            Interruptor(soy, { soy = it })
        }
        T("No lo guardamos: sólo vale para sumarte a este plan.", 12f, 400, p.texto2, alto = 1.4f)
        Cta("Sumarme", { pregunta?.respuesta?.complete(true) }, Modifier.fillMaxWidth(), habilitado = soy)
    }
}

/**
 * Corre [f] sin confirmación y, si la API responde `confirmar_mujer` o `confirmar_diversidad`, abre
 * «Antes de sumarte» y, si la persona lo confirma, prueba una vez más con esa confirmación (vale sólo
 * para ese pedido). Los demás errores pasan tal cual.
 */
suspend fun <R> conConfirmacion(c: Contenedor, f: suspend (Confirma) -> R): R = try {
    f(Confirma())
} catch (e: ErrorApi) {
    val pide = Generos.pide(e.codigo)
    if (pide != null && c.confirmar(pide)) f(Generos.confirma(pide)) else throw e
}
