package ar.com.caigo.app.ui.pantallas.planes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.Contenedor
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.datos.ErrorApi
import ar.com.caigo.app.datos.Plan
import ar.com.caigo.app.datos.mensajeDe
import ar.com.caigo.app.logica.Cupo
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Avatares
import ar.com.caigo.app.ui.piezas.Avisos
import ar.com.caigo.app.ui.piezas.BadgeFalta
import ar.com.caigo.app.ui.piezas.Fecha
import ar.com.caigo.app.ui.piezas.Foto
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.distancia
import ar.com.caigo.app.ui.piezas.nombreCategoria
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical
import kotlinx.coroutines.launch

/** "Lugar · 1,2 km" (o la zona si no hay lugar). */
internal fun lugarYDistancia(p: Plan): String =
    listOf(p.lugar?.nombre ?: p.lugarNombre.ifBlank { p.zona }, distancia(p.distanciaM)).filter { it.isNotBlank() }.joinToString(" · ")

internal fun esDeLugar(p: Plan) = p.lugar != null && p.cobro != "gratis"

internal fun adentro(p: Plan) = p.miEstado == "aceptada" || p.miEstado == "organizador"

/** Chip chico sobre la foto (categoría clara, hora oscura con grano). */
@Composable
internal fun ChipFoto(texto: String, oscuro: Boolean, modifier: Modifier = Modifier, fondo: Gel? = null, color: Color? = null) {
    val p = LocalPaleta.current
    val g = fondo ?: when {
        oscuro == !p.oscuro -> Gels.oscuroSel
        else -> Gel(vertical(C.blancoCalido, C.blancoCalido), emptyList())
    }
    val c = color ?: if (oscuro == !p.oscuro) C.blancoCalido else C.carbon
    Box(modifier.gel(11.dp, g).padding(horizontal = 10.dp, vertical = 6.dp)) { T(texto, 12f, 700, c, unRenglon = true) }
}

/** Plan destacado con Caigo Plus: el chip lima de «Lugar aliado» con la estrella del me encanta. */
@Composable
internal fun ChipDestacado(modifier: Modifier = Modifier, soloEstrella: Boolean = false) {
    Row(
        modifier.gel(11.dp, Gel(vertical(C.lima, C.lima), emptyList())).padding(horizontal = if (soloEstrella) 8.dp else 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icono(I.estrella, if (soloEstrella) 14.dp else 12.dp, Color.Transparent, 0f, relleno = C.carbon)
        if (!soloEstrella) T("Destacado", 12f, 700, C.carbon, unRenglon = true)
    }
}

/**
 * Sumarse a un plan desde la tarjeta o el detalle. Los errores de verificación llevan a la selfie o
 * al DNI (y vuelven); el resto se muestra tal cual en el toast.
 */
internal suspend fun sumarse(c: Contenedor, ir: Ir, avisos: Avisos, plan: Plan, cantidad: Int, mensaje: String = ""): String? =
    try {
        val e = c.api.sumarme(plan.id, cantidad, mensaje).estado
        avisos.ok(
            if (e == "aceptada") "¡Estás adentro!"
            else "Le pediste a ${plan.organizador.nombre.ifBlank { "quien organiza" }} sumarte.",
        )
        e
    } catch (e: ErrorApi) {
        when (e.codigo) {
            "falta_selfie" -> { avisos.error(e.detalle); ir.selfie(volver = true) }
            "falta_dni" -> { avisos.error(e.detalle); ir.dni(plan.id) }
            else -> avisos.error(e.detalle)
        }
        null
    } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        avisos.error(mensajeDe(e))
        null
    }

/** Botón de acción de la tarjeta: Sumarme / Entrada · Esperando… · Adentro. */
@Composable
internal fun AccionTarjeta(plan: Plan, ir: Ir, alCambiar: (String) -> Unit) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var enviando by remember { mutableStateOf(false) }
    when {
        plan.miEstado == "pendiente" -> Box(
            Modifier.gel(16.dp, vertical(C.tinte1, C.tinte1), listOf(interna(2f, 4f, rgba(116, 86, 241, .18f)))).padding(horizontal = 16.dp, vertical = 11.dp),
        ) { T("Esperando…", 14f, 800, C.violetaTexto, unRenglon = true) }
        adentro(plan) -> Row(
            Modifier.background(C.carbon, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icono(I.check, 14.dp, C.lima, 3.4f)
            T(if (plan.miEstado == "organizador") "Tu plan" else "Adentro", 14f, 800, C.blanco, unRenglon = true)
        }
        Cupo.completo(plan.faltan) -> Unit
        else -> Row(
            Modifier
                .toque(!enviando) {
                    if (esDeLugar(plan)) ir.plan(plan.id)
                    else alcance.launch {
                        enviando = true
                        sumarse(c, ir, avisos, plan, 1)?.let(alCambiar)
                        enviando = false
                    }
                }
                .gel(14.dp, Gels.limaChico)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icono(I.mas, 14.dp, C.carbon, 4f)
            T(if (esDeLugar(plan)) "Entrada" else "Sumarme", 14f, 800, C.carbon, unRenglon = true)
        }
    }
}

/**
 * Tarjeta de plan (02 · Ahora): foto de 150 con categoría y hora arriba, badge "Faltan N" abajo a la
 * derecha; título, lugar · distancia, avatares apilados, "Somos N de M" y el botón lima.
 */
@Composable
internal fun TarjetaPlan(plan: Plan, ir: Ir, modifier: Modifier = Modifier, altoFoto: Dp = 150.dp, alCambiar: (String) -> Unit = {}) {
    val p = LocalPaleta.current
    Column(
        modifier
            .fillMaxWidth()
            .toque(alBajar = 0.dp) { ir.plan(plan.id) }
            .gel(24.dp, if (p.oscuro) Gels.tarjetaOscura else Gels.tarjeta)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(altoFoto)) {
            Foto(plan.foto.ifBlank { plan.lugar?.foto }, Modifier.fillMaxSize(), tinte = colorDeCategoria(plan.categoria))
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipFoto(nombreCategoria(plan.categoria), oscuro = false)
                ChipFoto(Fecha.cuando(plan.empiezaEn), oscuro = true)
                if (plan.lugar != null) ChipFoto("Lugar aliado", false, fondo = Gel(vertical(C.lima, C.lima), emptyList()), color = C.carbon)
            }
            if (plan.destacado) ChipDestacado(Modifier.align(Alignment.BottomStart).padding(12.dp))
            BadgeFalta(plan.faltan, Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(plan.titulo, 20f, 700, p.texto, alto = 1.15f, espaciado = px(-.3f, 20f))
                T(lugarYDistancia(plan), 13f, 400, p.texto2, unRenglon = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Con "Adentro" / "Esperando…" el botón es más ancho: van sólo las cuentas, como en el prototipo.
                val conAvatares = plan.van.isNotEmpty() && plan.miEstado == "ninguno"
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (conAvatares) {
                        Avatares(plan.van.map { it.nombre to it.foto }, total = plan.somos)
                    }
                    T(Cupo.somos(plan.somos, plan.total), 13f, 600, if (p.oscuro) C.apagado2 else C.texto2, unRenglon = true,
                        modifier = Modifier.padding(start = if (conAvatares) 6.dp else 0.dp))
                }
                AccionTarjeta(plan, ir, alCambiar)
            }
        }
    }
}

/** Tinte de la foto mientras carga (los `tint` del mockup por categoría). */
internal fun colorDeCategoria(cat: String): Color = when (cat) {
    "deporte" -> Color(0xFF3B6B57)
    "juego" -> C.violetaTexto
    "previa", "fiesta", "musica" -> C.oscuro2
    "salida", "cita_doble" -> Color(0xFF6B4A5E)
    else -> C.oscuro1
}

/** Fondo de las píldoras de vidrio sobre fotos. */
internal val vidrio: Brush = vertical(rgba(255, 255, 255, .16f), rgba(255, 255, 255, .16f))
