package ar.com.caigo.app.piezas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.nucleo.Asist
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.rgba
import kotlinx.coroutines.launch

/** "96% asistencia" o "Nuevo en Caigo". */
fun asistencia(fue: Int, de: Int): String = if (de > 0) "${(fue * 100 + de / 2) / de}% asistencia" else "Nuevo en Caigo"
fun asistencia(a: Asist): String = asistencia(a.fue, a.de)

/** Sello de verificada: estrella lima con tilde carbón. */
@Composable
fun Verificado(tam: Dp = 24.dp, color: Color = C.lima, tilde: Color = C.carbon, modifier: Modifier = Modifier) {
    Box(modifier.semantics { contentDescription = "Verificada" }, contentAlignment = Alignment.Center) {
        Icono(I.verificado, tam, Color.Transparent, 0f, relleno = color)
        Icono(I.checkChico, tam * .75f, tilde, 2.6f)
    }
}

/** El color de cada categoría (chips sobre foto, pines del mapa). */
fun colorDeCategoria(cat: String): Color = when (cat) {
    "deporte" -> Color(0xFF3B6B57)
    "juego" -> C.violetaTexto
    "previa", "fiesta", "musica" -> C.oscuro2
    "salida", "cita_doble" -> Color(0xFF6B4A5E)
    else -> C.oscuro1
}

/** Fondo de las píldoras de vidrio sobre fotos. */
val vidrioSobreFoto: Color = rgba(255, 255, 255, .16f)

/** Una fila de una hoja de opciones. */
data class Opcion(val texto: String, val accion: () -> Unit)

/**
 * Hoja de opciones (armada con la tarjeta de filas de "Herramientas para salir tranqui"): se apoya
 * abajo sobre un velo; tocar afuera cierra. Las opciones que reportan, cancelan, bloquean o borran
 * van en coral.
 */
@Composable
fun HojaOpciones(cerrar: () -> Unit, filas: List<Pair<String, () -> Unit>>) {
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val entrada = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(Modifier.fillMaxSize().zIndex(30f)) {
        AnimatedVisibility(entrada, Modifier.fillMaxSize(), enter = fadeIn(tween(200))) {
            Box(Modifier.fillMaxSize().background(rgba(32, 29, 43, .45f)).tocable(cerrar))
        }
        AnimatedVisibility(entrada, Modifier.align(Alignment.BottomCenter), enter = slideInVertically(tween(280)) { it }) {
            Column(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp + abajo).tocable { }
                    .gel(24.dp, Gels.neutra),
            ) {
                filas.forEachIndexed { i, (t, f) ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.lavanda))
                    Box(Modifier.fillMaxWidth().tocable { cerrar(); f() }.padding(horizontal = 18.dp, vertical = 16.dp)) {
                        T(t, 15f, 700, if (listOf("Reportar", "Cancelar", "Bloquear", "Borrar").any { t.startsWith(it) }) C.coral2 else C.carbon)
                    }
                }
            }
        }
    }
}

@Composable
fun HojaOpciones(cerrar: () -> Unit, filas: List<Opcion>) = HojaOpciones(cerrar, filas.map { it.texto to it.accion })

/** Motivos de reporte (personas.go · motivos). */
val motivosReporte: List<Pair<String, String>> = listOf(
    "acoso" to "Acoso o mal trato", "falso" to "Perfil o plan falso", "no_vino" to "No vino",
    "inapropiado" to "Contenido inapropiado", "inseguro" to "Me sentí insegura/o", "otro" to "Otro motivo",
)

@Composable
fun HojaReporte(cerrar: () -> Unit, alElegir: (String) -> Unit) =
    HojaOpciones(cerrar, motivosReporte.map { (k, l) -> l to { alElegir(k) } })

/** tk-pop: escala .4 → 1.08 → 1 con rotación −20° → 6° → 0° (confirmaciones). */
@Composable
fun Modifier.pop(): Modifier {
    val escala = remember { Animatable(.4f) }
    val giro = remember { Animatable(-20f) }
    LaunchedEffect(Unit) {
        launch {
            escala.animateTo(1.08f, tween(270, easing = LinearOutSlowInEasing))
            escala.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        }
        launch {
            giro.animateTo(6f, tween(270, easing = LinearOutSlowInEasing))
            giro.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer { scaleX = escala.value; scaleY = escala.value; rotationZ = giro.value }
}

/** tk-up: entra subiendo 20 y apareciendo ([retraso] en segundos). */
@Composable
fun Modifier.sube(retraso: Double = 0.0): Modifier {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(250, delayMillis = (retraso * 1000).toInt(), easing = LinearOutSlowInEasing)) }
    return graphicsLayer {
        alpha = t.value
        translationY = (1f - t.value) * 20.dp.toPx()
    }
}
