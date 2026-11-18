package ar.com.caigo.app.pantallas.planes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Gestos
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
 * «Decidí rápido» (Ahora, con 8 o más planes para decidir): a pantalla completa, los planes de la lista de a uno,
 * con la misma tarjeta más grande y los mismos gestos. «Paso» lo oculta (como «No me interesa»), «Ver más» abre
 * el detalle y «Caigo» se suma directo o abre el detalle ([planesCaigo]). Al final: «Listo: te sumaste a N.».
 */
@Composable
fun DecidiRapido(ir: Ir, planes: List<Plan>, ocultos: PlanesOcultos, cerrar: () -> Unit) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val p = LocalPaleta.current
    val vista = LocalView.current
    val alcance = rememberCoroutineScope()
    val sinMovimiento = reducirMovimiento()
    var indice by remember { mutableIntStateOf(0) }
    var sumados by remember { mutableIntStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    val actual = planes.getOrNull(indice)
    BackHandler(onBack = cerrar)

    fun siguiente() { indice++ }

    // «Paso»: POST /v1/planes/{id}/ocultar (sin el aviso de «Deshacer»: el mazo sigue).
    fun paso(pl: Plan) {
        ocultos.ocultar(c, avisos, pl, avisar = false)
        siguiente()
    }

    // «Caigo»: directo (con el festejo si queda adentro) o el detalle; en los dos casos el mazo sigue.
    fun caigo(pl: Plan) {
        if (ocupado) return
        if (!Gestos.directo(pl) || cerroParaSumarse(pl)) {
            ir.plan(pl.id)
            siguiente()
            return
        }
        // Sin DNI se mira: «Caigo» pide verificarse y la tarjeta se queda (no pasa a la siguiente).
        if (!c.conDni(ir, "sumarte", pl.id)) return
        ocupado = true
        alcance.launch {
            val e = planesCaigo(c, ir, avisos, pl) { Sistema.vibrar(vista) }
            if (e == "aceptada") sumados++
            ocupado = false
            siguiente()
        }
    }

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp, bottom = abajo)) {
            Encabezado(
                "Decidí rápido", actual?.let { "${indice + 1} de ${planes.size}" }, cerrar,
                Modifier.padding(horizontal = 16.dp), icono = I.cruz,
            )
            AnimatedContent(
                actual, Modifier.weight(1f),
                transitionSpec = { if (sinMovimiento) fadeIn(tween(0)) togetherWith fadeOut(tween(0)) else fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "mazo",
            ) { pl ->
                if (pl != null) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp)) {
                            DeslizarPlan(24.dp, alCaigo = { caigo(pl) }, alNoMeInteresa = { paso(pl) }) { m -> TarjetaPlan(pl, ir, m, altoFoto = 300.dp) }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Cta(
                                "Paso", { paso(pl) }, Modifier.weight(1f), gel = Gels.blanco, alto = 56.dp, tam = 16f, color = C.carbon,
                                icono = I.cruz, iconoTrazo = 3f, iconoTam = 14.dp,
                            )
                            Cta("Ver más", { ir.plan(pl.id) }, Modifier.weight(1f), gel = Gels.blanco, alto = 56.dp, tam = 16f, color = C.carbon)
                            Cta(
                                "Caigo", { caigo(pl) }, Modifier.weight(1f), gel = Gels.lima, alto = 56.dp, tam = 16f, color = C.carbon,
                                icono = I.mas, iconoTrazo = 4f, iconoTam = 14.dp, cargando = ocupado,
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                    ) {
                        T(
                            if (sumados > 0) "Listo: te sumaste a $sumados." else "Listo.", 30f, 700, p.texto, alto = 1.08f,
                            espaciado = px(-.6f, 30f), alinear = TextAlign.Center,
                        )
                        Cta("Volver a la lista", cerrar, Modifier.fillMaxWidth().padding(top = 6.dp), alto = 58.dp, tam = 16f)
                    }
                }
            }
        }
    }
}
