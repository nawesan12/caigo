package ar.com.caigo.app.tema

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/**
 * Los íconos de la barra de estado que pide cada pantalla de paleta fija (la clara de Persona, Recibidos y el
 * ingreso; la oscura de Plus y Tap). CaigoTema aplica el último pedido vivo y, sin ninguno, el tema elegido.
 * Con una pila y no con «al salir, volver»: en la transición la pantalla nueva entra antes de que salga la vieja.
 */
object BarraEstado {
    /** (dueño, fondo oscuro → íconos claros). */
    val pedidos = mutableStateListOf<Pair<Any, Boolean>>()

    /** Pide íconos claros (fondo oscuro) o oscuros (fondo claro) mientras la pantalla esté. */
    @Composable
    fun Pedir(fondoOscuro: Boolean) {
        val dueno = remember { Any() }
        DisposableEffect(fondoOscuro) {
            pedidos.add(dueno to fondoOscuro)
            onDispose { pedidos.removeAll { it.first === dueno } }
        }
    }
}

/**
 * Una pantalla que va siempre clara (o siempre oscura): la paleta y los íconos de la barra de estado juntos
 * (el `temaFijo` de iOS). Con el teléfono en oscuro, la hora no queda blanca sobre una pantalla clara.
 */
@Composable
fun PaletaFija(p: Paleta, contenido: @Composable () -> Unit) {
    BarraEstado.Pedir(p.oscuro)
    CompositionLocalProvider(LocalPaleta provides p, contenido)
}
