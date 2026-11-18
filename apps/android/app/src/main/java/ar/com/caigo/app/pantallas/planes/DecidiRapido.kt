package ar.com.caigo.app.pantallas.planes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.gel
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Encabezado
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Sistema
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.px
import kotlinx.coroutines.launch

/**
 * «Decidí rápido» (Ahora, con 8 o más planes que se suman con un toque): a pantalla completa, los planes de a uno,
 * con la misma tarjeta más grande (sin su botón: los del mazo van abajo) y los mismos gestos. «Paso» sólo pasa
 * (ocultar por hoy, con «Deshacer», es el gesto de la lista), «Ver más» abre el detalle y «Sumarme» se suma
 * directo, sin festejo en el medio: la carta muestra «Adentro» y el resumen va al final («Listo: te sumaste a N.»).
 *
 * Lo decidido se guarda con rememberSaveable (ids): «Ver más» navega a otra entrada y, al volver, el mazo sigue
 * donde estaba (como en iOS, donde Ahora es la raíz y conserva su estado).
 */
@Composable
internal fun DecidiRapido(ir: Ir, planes: List<Plan>, ocultos: PlanesOcultos, cerrar: () -> Unit) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val p = LocalPaleta.current
    val vista = LocalView.current
    val alcance = rememberCoroutineScope()
    val sinMovimiento = reducirMovimiento()
    val escala = LocalDensity.current.fontScale
    var pasados by rememberSaveable { mutableStateOf(listOf<String>()) }
    var sumados by rememberSaveable { mutableIntStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    var adentro by remember { mutableStateOf(false) }
    val actual = planes.firstOrNull { it.id !in pasados }
    val numero = (planes.count { it.id in pasados } + 1).coerceAtMost(planes.size)
    BackHandler(onBack = cerrar)

    fun siguiente(pl: Plan) { pasados = pasados + pl.id }

    // «Paso»: sólo pasa a la siguiente (el plan sigue en la lista).
    fun paso(pl: Plan) {
        if (!ocupado) siguiente(pl)
    }

    // «Sumarme»: directo (el mazo sólo trae planes así), sin festejo; «Adentro» en la carta y a la siguiente.
    fun sumarme(pl: Plan) {
        if (ocupado) return
        // Sin DNI se mira: pide verificarse y la tarjeta se queda (no pasa a la siguiente).
        if (!c.conDni(ir, "sumarte", pl.id)) return
        ocupado = true
        alcance.launch {
            val e = planesCaigo(c, ir, avisos, pl, festejar = false) { Sistema.vibrar(vista) }
            if (e == "aceptada" || e == "pendiente") {
                sumados++
                adentro = true
                delay(550)
                adentro = false
            }
            ocupado = false
            if (e != null) siguiente(pl)
        }
    }

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp, bottom = abajo)) {
            Encabezado(
                "Decidí rápido", actual?.let { "$numero de ${planes.size}" }, cerrar,
                Modifier.padding(horizontal = 16.dp), icono = I.cruz,
            )
            AnimatedContent(
                actual, Modifier.weight(1f),
                transitionSpec = { if (sinMovimiento) fadeIn(tween(0)) togetherWith fadeOut(tween(0)) else fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "mazo",
            ) { pl ->
                if (pl != null) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp)) {
                            DeslizarPlan(24.dp, alCaigo = { sumarme(pl) }, alNoMeInteresa = { paso(pl) }) { m ->
                                TarjetaPlan(pl, ir, m, altoFoto = 300.dp, conAccion = false)
                            }
                            if (adentro) {
                                Row(
                                    Modifier.align(Alignment.Center).gel(20.dp, Gels.lima).padding(horizontal = 22.dp, vertical = 14.dp)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icono(I.check, 22.dp, C.carbon, 3.4f)
                                    T("Adentro", 20f, 800, C.carbon, unRenglon = true)
                                }
                            }
                        }
                        // Con letra grande, los tres botones van uno abajo del otro (si no, quedaban cortados).
                        val botones: @Composable (Modifier) -> Unit = { m ->
                            Cta(
                                "Paso", { paso(pl) }, m, gel = Gels.blanco, alto = 56.dp, tam = 16f, color = C.carbon,
                                icono = I.cruz, iconoTrazo = 3f, iconoTam = 14.dp,
                            )
                            Cta("Ver más", { ir.plan(pl.id) }, m, gel = Gels.blanco, alto = 56.dp, tam = 16f, color = C.carbon)
                            Cta(
                                "Sumarme", { sumarme(pl) }, m, gel = Gels.lima, alto = 56.dp, tam = 16f, color = C.carbon,
                                icono = I.mas, iconoTrazo = 4f, iconoTam = 14.dp, cargando = ocupado,
                            )
                        }
                        val bordes = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp)
                        if (escala > 1.3f) {
                            Column(bordes, verticalArrangement = Arrangement.spacedBy(10.dp)) { botones(Modifier.fillMaxWidth()) }
                        } else {
                            Row(bordes, horizontalArrangement = Arrangement.spacedBy(10.dp)) { botones(Modifier.weight(1f)) }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                    ) {
                        T(
                            if (sumados > 0) "Listo: te sumaste a\u00A0$sumados." else "Listo.", 30f, 700, p.texto, alto = 1.08f,
                            espaciado = px(-.6f, 30f), alinear = TextAlign.Center,
                        )
                        Cta("Volver a la lista", cerrar, Modifier.fillMaxWidth().padding(top = 6.dp), alto = 58.dp, tam = 16f)
                    }
                }
            }
        }
    }
}
