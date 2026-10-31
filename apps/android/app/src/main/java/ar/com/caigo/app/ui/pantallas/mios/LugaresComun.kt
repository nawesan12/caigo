package ar.com.caigo.app.ui.pantallas.mios

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical

/*
 * Piezas que comparten las pantallas de lugares, Plus, entradas y Caigo Tap. Todas salen del
 * prototipo y de "Caigo Pases NFC" (con Geist en vez de Bricolage).
 */

internal fun Context.lugaresActividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.lugaresActividad()
    else -> null
}

/** Íconos de la barra de estado claros (fondos oscuros: Plus, Tap) u oscuros. */
@Composable
internal fun LugaresBarraEstado(clara: Boolean) {
    val vista = LocalView.current
    val oscuroSistema = LocalPaleta.current.oscuro
    DisposableEffect(clara) {
        val ventana = vista.context.lugaresActividad()?.window
        val ctl = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        ctl?.isAppearanceLightStatusBars = !clara
        onDispose { ctl?.isAppearanceLightStatusBars = !oscuroSistema }
    }
}

/** Lima gel de los Pases NFC y el paywall (base `#7FA82E` y brillo lima). */
internal val lugaresGelLimaNfc = Gel(
    Gels.lima.fondo,
    listOf(
        interna(3f, 2f, rgba(255, 255, 255, .85f)),
        interna(-5f, 0f, rgba(96, 140, 10, .32f)),
        externa(5f, 0f, Color(0xFF7FA82E)),
        externa(18f, 30f, rgba(200, 243, 107, .45f), spread = -10f),
    ),
)

/** Pill "PLUS" inclinada 10° (paywall). */
@Composable
internal fun LugaresPillPlus(modifier: Modifier = Modifier, tam: Float = 20f) {
    Box(modifier.graphicsLayer { rotationZ = 10f }.gel(14.dp, lugaresGelLimaNfc).padding(horizontal = 14.dp, vertical = 8.dp)) {
        T("PLUS", tam, 800, C.carbon, alto = 1f)
    }
}

/** Botón de ancho completo de los Pases NFC: negro de Wallet, blanco con borde, o grafito. */
internal enum class LugaresEstiloBoton { Negro, Blanco, Grafito, Lima }

@Composable
internal fun LugaresBoton(
    texto: String,
    estilo: LugaresEstiloBoton,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icono: String? = null,
    alto: Dp = 56.dp,
    radio: Dp = 18.dp,
    tam: Float = 16f,
    peso: Int = 700,
) {
    val (gel, color) = when (estilo) {
        LugaresEstiloBoton.Negro -> Gel(vertical(C.negro, C.negro), listOf(externa(4f, 0f, Color(0xFF333333)))) to C.blanco
        LugaresEstiloBoton.Blanco -> Gel(vertical(C.blanco, C.blanco), listOf(interna(0f, 0f, C.borde3, spread = -1.5f), externa(4f, 0f, C.borde3))) to C.carbon
        LugaresEstiloBoton.Grafito -> Gel(vertical(C.carbon, C.carbon), listOf(externa(5f, 0f, C.oscuro7))) to C.blanco
        LugaresEstiloBoton.Lima -> Gel(vertical(C.lima, C.lima), listOf(externa(4f, 0f, C.limaBase))) to C.carbon
    }
    Box(modifier.height(alto).toque(onClick = onClick).gel(radio, gel), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icono != null) Icono(icono, 22.dp, color, 2f)
            T(texto, tam, peso, color, unRenglon = true)
        }
    }
}

/** `tk-pop`: escala .4 → 1.08 → 1 con rotación −20° → 6° → 0°, para las confirmaciones. */
@Composable
internal fun LugaresPop(retraso: Int = 0, contenido: @Composable BoxScope.() -> Unit) {
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        k.animateTo(1f, keyframes { durationMillis = 500 + retraso; 0f at retraso; .7f at retraso + 300; 1f at 500 + retraso })
    }
    Box(
        Modifier.graphicsLayer {
            val t = k.value
            val (s, r) = when {
                t <= 0f -> .4f to -20f
                t < .7f -> (.4f + (1.08f - .4f) * (t / .7f)) to (-20f + 26f * (t / .7f))
                else -> (1.08f - .08f * ((t - .7f) / .3f)) to (6f - 6f * ((t - .7f) / .3f))
            }
            scaleX = s; scaleY = s; rotationZ = r; alpha = (t * 3f).coerceAtMost(1f)
        },
        contentAlignment = Alignment.Center,
        content = contenido,
    )
}

/** "Hoy 19:30 · Café Molienda" */
internal fun lugaresCuandoDonde(iso: String, lugar: String): String =
    listOf(ar.com.caigo.app.ui.piezas.Fecha.cuando(iso), lugar).filter { it.isNotBlank() }.joinToString(" · ")

/** Ícono de Wallet de los botones (tarjetas apiladas). */
internal val lugaresIconoWallet = I.billetera
