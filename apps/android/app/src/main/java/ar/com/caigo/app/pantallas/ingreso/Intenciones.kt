package ar.com.caigo.app.pantallas.ingreso

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.CambiosPerfil
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.Pestana
import ar.com.caigo.app.piezas.Chip
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.launch

/** Una intención del onboarding del prototipo, con la clave que entiende la API. */
private data class Intencion(val api: String, val titulo: String, val bajada: String, val icono: String, val fondo: Brush, val trazo: Color)

private val intenciones = listOf(
    Intencion("planes", "Sumarme a planes", "Partidos, previas y mesas a las que les falta uno", I.mas,
        vertical(0f to C.lima0, .6f to C.lima, 1f to C.lima3), C.carbon),
    Intencion("gente", "Conocer gente nueva", "Personas de a una con ganas de salir", I.gente,
        vertical(0f to C.violeta1, .6f to C.violeta, 1f to C.violeta4), C.blanco),
    Intencion("completar", "Completar mis planes", "Ya tengo el plan, me falta gente", I.calendario,
        vertical(C.oscuro1, C.carbon), C.lima),
    Intencion("citas", "Algo más romántico", "Citas, citas dobles y ver qué onda", I.corazonChico,
        vertical(0f to C.coral0, .6f to C.coral, 1f to C.coral2), C.carbon),
)

/** Los intereses del prototipo (LIKES). */
private val intereses = listOf(
    "Fútbol 5", "Pádel", "Truco", "Previas", "After beach", "Surf", "Cine", "Asado", "Running",
    "Juegos de mesa", "Música en vivo", "Mate en la playa",
)

private val gelIntencionOn = Gel(
    vertical(C.lavanda, C.lavanda),
    listOf(interna(0f, 0f, C.violeta, spread = -2.5f), externa(4f, 0f, C.borde5), externa(14f, 24f, rgba(116, 86, 241, .5f), spread = -12f)),
)
private val gelIntencionOff = Gel(
    vertical(C.blanco, C.blanco),
    listOf(interna(1f, 0f, C.blanco), externa(3f, 0f, C.borde1), externa(12f, 22f, rgba(80, 60, 160, .3f), spread = -14f)),
)

/**
 * Onboarding de intenciones del prototipo: "¿Qué buscás en Caigo?" (al menos una) y "¿Qué te copa?".
 * Al terminar arranca en Planes, Gente o Citas según lo elegido.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Intenciones(ir: Ir, soloEditar: Boolean) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val yo = c.yo.value
    var paso by remember { mutableIntStateOf(0) }
    var elegidas by remember { mutableStateOf(yo?.intenciones.orEmpty().toSet()) }
    var copan by remember { mutableStateOf(yo?.intereses.orEmpty().toSet()) }
    var cargando by remember { mutableStateOf(false) }

    BackHandler(paso == 1) { paso = 0 }

    val terminar: () -> Unit = {
        alcance.launch {
            cargando = true
            try {
                c.yo.value = c.api.actualizarYo(CambiosPerfil(intenciones = intenciones.map { it.api }.filter { it in elegidas }, intereses = intereses.filter { it in copan }))
                if (soloEditar) {
                    ir.atras()
                } else {
                    // La regla del prototipo: si sólo busca gente o citas, arranca mostrando personas.
                    val soloGente = ("gente" in elegidas || "citas" in elegidas) && "planes" !in elegidas && "completar" !in elegidas
                    val modo = if (!soloGente) "planes" else if ("citas" in elegidas && "gente" !in elegidas) "citas" else "gente"
                    avisos.ok(if (soloGente) "Listo. Arrancamos mostrándote gente cerca." else "Listo. Arrancamos con los planes de las próximas horas.")
                    ir.principal(Pestana.Ahora, modo)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(ingresoMensaje(e))
            } finally {
                cargando = false
            }
        }
    }

    Pantalla { arriba, abajo ->
        AnimatedContent(paso, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "paso") { p ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = arriba + 62.dp, bottom = 140.dp + abajo),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (p == 0) {
                    T("¿Qué buscás en Caigo?", 34f, 800, C.carbon, alto = 1.02f, espaciado = px(-1f, 34f))
                    T("Elegí todas las que quieras. Armamos la app según esto y lo cambiás cuando quieras.", 15f, 400, C.texto2, alto = 1.5f,
                        modifier = Modifier.padding(bottom = 6.dp))
                    intenciones.forEach { it ->
                        val on = it.api in elegidas
                        Row(
                            Modifier.fillMaxWidth().toque(alBajar = 2.dp) { elegidas = if (on) elegidas - it.api else elegidas + it.api }
                                .gel(24.dp, if (on) gelIntencionOn else gelIntencionOff).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier.size(52.dp).gel(18.dp, it.fondo, listOf(interna(2f, 1f, rgba(255, 255, 255, .45f)), interna(-3f, 0f, rgba(0, 0, 0, .15f)))),
                                contentAlignment = Alignment.Center,
                            ) { Icono(it.icono, 24.dp, it.trazo, 2.6f) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                T(it.titulo, 16f, 800, C.carbon)
                                T(it.bajada, 13f, 500, C.texto2, alto = 1.35f)
                            }
                            if (on) {
                                Box(Modifier.size(28.dp).gel(10.dp, vertical(C.violeta, C.violeta)), contentAlignment = Alignment.Center) {
                                    Icono(I.check, 14.dp, C.lima, 3.6f)
                                }
                            } else {
                                Box(Modifier.size(28.dp).border(2.dp, Color(0xFFD9D3EE), RoundedCornerShape(10.dp)))
                            }
                        }
                    }
                } else {
                    T("¿Qué te copa?", 34f, 800, C.carbon, alto = 1.02f, espaciado = px(-1f, 34f))
                    T("Te mostramos primero planes y gente con lo mismo.", 15f, 400, C.texto2, alto = 1.5f, modifier = Modifier.padding(bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        intereses.forEach { l ->
                            val on = l in copan
                            Chip(l, on, { copan = if (on) copan - l else copan + l }, tam = 15f, pesoOff = 700, padH = 16.dp, padV = 12.dp, radio = 17.dp)
                        }
                    }
                }
            }
        }
        if (paso == 1 || soloEditar) {
            Box(Modifier.padding(start = 20.dp, top = arriba + 8.dp)) {
                ar.com.caigo.app.piezas.BotonIcono(I.atras, { if (paso == 1) paso = 0 else ir.atras() }, gel = ingresoGelVolver, trazo = 2.4f)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 34.dp + abajo),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ingresoPuntos(paso, 2, C.violeta, Color(0xFFD9D3EE))
            ingresoCtaGrande(
                when {
                    paso == 0 -> "Seguir"
                    soloEditar -> "Listo"
                    else -> "Listo, mostrame Caigo"
                },
                {
                    if (paso == 0) {
                        if (elegidas.isEmpty()) avisos.error("Elegí al menos una, después la cambiás.") else paso = 1
                    } else terminar()
                },
                cargando = cargando,
            )
        }
    }
}
