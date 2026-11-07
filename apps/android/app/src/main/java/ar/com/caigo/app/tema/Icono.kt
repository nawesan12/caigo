package ar.com.caigo.app.tema

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Círculo en sintaxis de path de SVG (para los `<circle>` de los íconos del handoff). */
private fun c(cx: Float, cy: Float, r: Float): String {
    fun n(v: Float): String = if (v == kotlin.math.round(v)) v.toInt().toString() else v.toString()
    return "M${n(cx - r)} ${n(cy)}a${n(r)} ${n(r)} 0 1 0 ${n(2 * r)} 0a${n(r)} ${n(r)} 0 1 0 ${n(-2 * r)} 0"
}

/** Los íconos lineales del handoff: los mismos paths (viewBox 24×24, trazo redondeado). */
object I {
    const val reloj = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7v5l3 2"
    const val lupa = "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14zM20 20l-4-4"
    val lupaChica = c(11f, 11f, 7f) + "M20 20l-4-4"
    const val calendario = "M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM4 10h16M8 3v4M16 3v4"
    const val calendarioSimple = "M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM4 10h16"
    const val usuario = "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM4 20c1.5-4 4.5-6 8-6s6.5 2 8 6"
    const val mas = "M12 4v16M4 12h16"
    const val check = "M5 12l5 5L20 7"
    const val checkChico = "M8 12l3 3 5-6"
    const val atras = "M15 5l-7 7 7 7"
    const val adelante = "M9 5l7 7-7 7"
    const val flecha = "M5 12h14M13 6l6 6-6 6"
    const val cruz = "M6 6l12 12M18 6L6 18"
    const val abajo = "M6 9l6 6 6-6"
    const val arriba = "M6 15l6-6 6 6"
    val pin = "M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z" + c(12f, 10f, 2.5f)
    const val escudo = "M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z"
    const val escudoCheck = "M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6zM9 12l2 2 4-4"
    const val verificado = "M12 2l2.4 1.8 3-.2.9 2.9 2.5 1.7-1 2.8 1 2.8-2.5 1.7-.9 2.9-3-.2L12 22l-2.4-1.8-3 .2-.9-2.9-2.5-1.7 1-2.8-1-2.8 2.5-1.7.9-2.9 3 .2z"
    const val candado = "M7 10V7a5 5 0 0 1 10 0v3h1v11H6V10zM9 10h6V7a3 3 0 0 0-6 0z"
    const val candadoAbierto = "M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3"
    const val regalo = "M4 11h16v9H4zM3 7h18v4H3zM12 7v13M12 7c-1.5-3-5-3-5-.5S10 7 12 7zM12 7c1.5-3 5-3 5-.5S14 7 12 7z"
    const val compartir = "M12 3v12M7 8l5-5 5 5M5 14v6h14v-6"
    const val corazon = "M12 20.5s-8-4.9-8-11A4.5 4.5 0 0 1 12 6.6a4.5 4.5 0 0 1 8 2.9c0 6.1-8 11-8 11z"
    const val corazonChico = "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"
    const val estrella = "M12 2.5l2.9 6 6.6.8-4.9 4.5 1.3 6.5L12 17l-5.9 3.3 1.3-6.5L2.5 9.3l6.6-.8z"
    const val nfc = "M8 7a7 7 0 0 1 0 10M12 4a11 11 0 0 1 0 16M4 10a3 3 0 0 1 0 4"
    val filtros = "M4 7h10M18 7h2M4 17h4M12 17h8" + c(16f, 7f, 2f) + c(10f, 17f, 2f)
    const val gente = "M9 4a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7zM2.5 20c1-3.5 3.5-5.5 6.5-5.5s5.5 2 6.5 5.5M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5c2 .6 3.2 2.5 3.5 5.5"
    const val campana = "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15zM10 20.5a2 2 0 0 0 4 0"
    val bloqueo = c(12f, 12f, 9f) + "M5.6 5.6l12.8 12.8"
    const val qr = "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2v2h-2zM18 18h2v2h-2zM14 18h2v2h-2zM18 14h2v2h-2z"
    const val billetera = "M3 7h18v12H3zM3 11h18M3 15h18"
    const val enviar = "M3 11l18-8-8 18-2-8z"
    val sol = c(12f, 12f, 3f) + "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"
    val relojCirculo = c(12f, 12f, 9f) + "M12 7v5l3 2"
    val puntos = c(5f, 12f, 2f) + c(12f, 12f, 2f) + c(19f, 12f, 2f)
    const val ubicacion = "M3 11l18-8-8 18-2-8z"

    val todos: List<String> = listOf(
        reloj, lupa, lupaChica, calendario, calendarioSimple, usuario, mas, check, checkChico, atras, adelante, flecha, cruz, abajo,
        arriba, pin, escudo, escudoCheck, verificado, candado, candadoAbierto, regalo, compartir, corazon, corazonChico, estrella, nfc,
        filtros, gente, campana, bloqueo, qr, billetera, enviar, sol, relojCirculo, puntos, ubicacion,
    )
}

/**
 * Ícono del handoff. [trazo] es el stroke-width del SVG (en unidades del viewBox de 24);
 * con [relleno] se pinta lleno (corazón, estrella, puntos).
 */
@Composable
fun Icono(
    d: String,
    tam: Dp = 24.dp,
    color: Color = LocalPaleta.current.texto,
    trazo: Float = 2f,
    relleno: Color? = null,
    modifier: Modifier = Modifier,
) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(tam)) {
        val k = size.minDimension / 24f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            if (relleno != null) drawPath(path, relleno, style = Fill)
            if (trazo > 0f && color != Color.Transparent) {
                drawPath(path, color, style = Stroke(width = trazo, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
