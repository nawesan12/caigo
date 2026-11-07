package ar.com.caigo.app.pantallas.pagos

import ar.com.caigo.app.piezas.Sistema
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.R
import ar.com.caigo.app.app.Destino
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Lectura
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.PaletaOscura
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val fondoTap = radial(.5f, .6f, .9f, .55f, 0f to Color(0xFF3B2F78), .75f to C.oscuro6)
private val textoTenue = Color(0xFFC9C4DA)

/*
 * Lector de placas Caigo Tap: en el iPhone es Core NFC (PagosLectorNfc, una sesión por toque). En
 * Android, mientras esta pantalla está abierta (`c.nfcActivo`), MainActivity lee las placas en
 * primer plano (reader mode) y deja la lectura en `c.destino`; con la app cerrada llegan por el
 * intent NDEF o el App Link `/t/…`, y Navegacion la deja en `c.tapPendiente`.
 */

/** Onda concéntrica que late. */
@Composable
private fun Onda(tam: Dp, borde: Float, alfaBorde: Float, relleno: Float = 0f, retraso: Int = 0, late: Boolean = true) {
    val t = rememberInfiniteTransition(label = "onda")
    val k by t.animateFloat(
        .94f, 1.04f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(retraso)),
        label = "k",
    )
    Box(
        Modifier.size(tam).graphicsLayer { if (late) { scaleX = k; scaleY = k } }
            .gel(tam / 2, vertical(rgba(200, 243, 107, relleno), rgba(200, 243, 107, relleno)), listOf(interna(0f, 0f, rgba(200, 243, 107, alfaBorde), spread = -borde))),
    )
}

/**
 * Caigo Tap (Pases NFC 1d / prototipo `tap`): "Apoyá el celu en la placa". Lee la placa por NFC o
 * su QR; también toma una placa ya leída por un link (`/t/…`).
 */
@Composable
fun Tap(ir: Ir, planId: String?) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val vista = LocalView.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    PagosBarraEstado(clara = true)
    val plan = rememberCarga(planId) { planId?.let { runCatching { c.api.plan(it) }.getOrNull() } }
    val nfc = remember { NfcAdapter.getDefaultAdapter(ctx) }
    val disponible = nfc != null
    var escaneando by remember { mutableStateOf(false) }
    var mandando by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        c.nfcActivo.value = true
        onDispose { c.nfcActivo.value = false }
    }

    suspend fun mandar(l: Lectura) {
        if (mandando) return
        mandando = true
        try {
            c.ultimoTap = c.api.tap(l)
            Sistema.vibrar(vista)
            ir.adentro()
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            avisos.error(mensajeDe(e))
            escaneando = false
        } finally {
            mandando = false
        }
    }

    fun leido(texto: String) = alcance.launch {
        val d = runCatching { c.destinoDe(Uri.parse(texto.trim())) }.getOrNull()
        if (d !is Destino.Tap) {
            avisos.error("Ese código no es de una placa de Caigo Tap.")
            return@launch
        }
        mandar(Lectura(d.placa, d.picc, d.cmac))
    }

    LaunchedEffect(Unit) {
        // Placa ya leída (link /t/… o NFC con la app cerrada).
        val pendiente = c.tapPendiente
        if (pendiente != null) {
            c.tapPendiente = null
            mandar(Lectura(pendiente.placa, pendiente.picc, pendiente.cmac))
        } else if (!disponible) {
            escaneando = true
        }
        // Una placa (NFC o link) que llega con la pantalla abierta.
        while (true) {
            val d = c.destino.value as? Destino.Tap ?: c.tapPendiente
            if (d != null) {
                if (c.destino.value === d) c.destino.value = null
                if (c.tapPendiente === d) c.tapPendiente = null
                mandar(Lectura(d.placa, d.picc, d.cmac))
            }
            delay(250)
        }
    }

    CompositionLocalProvider(LocalPaleta provides PaletaOscura) {
        Pantalla(oscuro = true, brillo = fondoTap) { arriba, abajo ->
            Column(
                Modifier.fillMaxSize().padding(top = arriba + 14.dp, start = 24.dp, end = 24.dp, bottom = abajo + 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val p = plan.dato
                if (p != null) {
                    Row(
                        Modifier.fillMaxWidth().gel(18.dp, Gel(vertical(C.carbon, C.carbon), listOf(interna(0f, 0f, rgba(255, 255, 255, .08f), spread = -1f)))).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(40.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            T(p.titulo, 14f, 800, C.blancoCalido, unRenglon = true)
                            T(pagosCuandoDonde(p.empiezaEn, pagosLugarDe(p)), 12f, 600, textoTenue, unRenglon = true)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (escaneando) {
                        PagosEscanerQr(
                            Modifier.size(280.dp).clip(RoundedCornerShape(30.dp)).background(C.oscuro8)
                                .border(3.dp, C.lima, RoundedCornerShape(30.dp)),
                        ) { leido(it) }
                    } else {
                        Onda(300.dp, 1.5f, .14f)
                        Onda(225.dp, 2f, .28f, retraso = 200)
                        Onda(150.dp, 2.5f, .55f, relleno = .1f, late = false)
                        Box(
                            Modifier.size(92.dp)
                                .semantics { contentDescription = "Leer la placa" }
                                // La lectura ya está prendida: el botón lleva a prender el NFC si está apagado.
                                .toque {
                                    if (nfc != null && !nfc.isEnabled) runCatching { ctx.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                                }
                                .gel(
                                    30.dp,
                                    Gel(
                                        pagosGelLimaNfc.fondo,
                                        listOf(
                                            interna(3f, 2f, rgba(255, 255, 255, .85f)), interna(-5f, 0f, rgba(96, 140, 10, .32f)),
                                            externa(5f, 0f, Color(0xFF7FA82E)), externa(0f, 60f, rgba(200, 243, 107, .5f)),
                                        ),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) { if (mandando) Puntitos(C.carbon) else Icono(I.nfc, 44.dp, C.carbon, 2.4f) }
                    }
                }
                T(
                    if (escaneando) "Escaneá el QR de la placa" else "Apoyá el celu en la placa Caigo Tap",
                    30f, 800, C.blancoCalido, alto = 1.05f, espaciado = px(-1f, 30f), alinear = TextAlign.Center,
                )
                T(
                    when {
                        escaneando -> "Apuntá la cámara al código de la placa del lugar."
                        nfc == null -> "Este teléfono no lee placas. Escaneá el QR de la placa."
                        !nfc.isEnabled -> "Prendé el NFC en los ajustes del teléfono, o escaneá el QR de la placa."
                        else -> "Acercá la parte de atrás del celu a la placa del lugar."
                    },
                    14f, 600, textoTenue, alto = 1.5f, alinear = TextAlign.Center,
                )
                Row(
                    Modifier.tocable { escaneando = !escaneando }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icono(if (escaneando) I.nfc else I.qr, 16.dp, C.lima, 2.2f)
                    T(if (escaneando) "Usar la placa" else "Escanear el QR de la placa", 14f, 800, C.lima)
                }
                T("Cancelar", 14f, 700, textoTenue, Modifier.tocable { ir.atras() }.padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }
    }
}
