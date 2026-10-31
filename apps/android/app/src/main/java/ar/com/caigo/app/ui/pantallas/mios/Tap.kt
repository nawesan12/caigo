package ar.com.caigo.app.ui.pantallas.mios

import android.net.Uri
import android.nfc.NfcAdapter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.Destino
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.datos.Lectura
import ar.com.caigo.app.datos.mensajeDe
import ar.com.caigo.app.logica.Plata
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.EscanerCodigos
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.rememberCarga
import ar.com.caigo.app.ui.piezas.rememberPermiso
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.radial
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical
import kotlinx.coroutines.delay

private val fondoTap = radial(.5f, .6f, .9f, .55f, 0f to Color(0xFF3B2F78), .75f to C.oscuro6)
private val fondoAdentro = radial(.5f, .3f, 1f, .6f, 0f to C.lima0, .55f to C.lima, 1f to Color(0xFF9FD13B))
private val textoTenue = Color(0xFFC9C4DA)
private val verdeOscuro = Color(0xFF2E4A00)

/** Onda concéntrica que late (tk-pop alternado). */
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
 * Caigo Tap (Pases NFC 1d / prototipo `tap`): "Apoyá el celu en la placa". Mientras está abierta
 * se leen placas NFC en primer plano; también se puede escanear el QR de la placa.
 */
@Composable
fun Tap(ir: Ir, planId: String?) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    LugaresBarraEstado(clara = true)
    val plan = rememberCarga(planId) { planId?.let { runCatching { c.api.plan(it) }.getOrNull() } }
    val nfc = remember { NfcAdapter.getDefaultAdapter(ctx) }
    var escaneando by remember { mutableStateOf(false) }
    var mandando by remember { mutableStateOf(false) }
    val (camara, pedirCamara) = rememberPermiso(android.Manifest.permission.CAMERA) { if (it) escaneando = true }

    DisposableEffect(Unit) {
        c.nfcActivo.value = true
        onDispose { c.nfcActivo.value = false }
    }

    suspend fun mandar(t: Destino.Tap) {
        if (mandando) return
        mandando = true
        try {
            c.ultimoTap = c.api.tap(Lectura(t.placa, t.picc, t.cmac))
            ir.adentro()
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            avisos.error(mensajeDe(e))
            escaneando = false
        } finally {
            mandando = false
        }
    }

    // Lecturas que llegan por NFC (MainActivity) o por un App Link de la placa.
    LaunchedEffect(Unit) {
        while (true) {
            val d = c.destino.value
            val pendiente = c.tapPendiente ?: (d as? Destino.Tap)?.also { c.destino.value = null }
            if (pendiente != null) {
                c.tapPendiente = null
                mandar(pendiente)
            }
            delay(250)
        }
    }
    var leido by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(leido) {
        val texto = leido ?: return@LaunchedEffect
        val d = runCatching { c.destinoDe(Uri.parse(texto.trim())) }.getOrNull()
        if (d is Destino.Tap) mandar(d) else { avisos.error("Ese QR no es de una placa de Caigo Tap."); delay(1500) }
        leido = null
    }

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
                        T(lugaresCuandoDonde(p.empiezaEn, p.lugar?.nombre?.ifBlank { null } ?: p.lugarNombre), 12f, 600, textoTenue, unRenglon = true)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (escaneando) {
                    Box(Modifier.size(280.dp).clip(RoundedCornerShape(30.dp)).background(C.oscuro8)) {
                        EscanerCodigos(Modifier.fillMaxSize()) { if (leido == null) leido = it }
                        Box(Modifier.fillMaxSize().gel(30.dp, null, listOf(interna(0f, 0f, C.lima, spread = -3f))))
                    }
                } else {
                    Onda(300.dp, 1.5f, .14f)
                    Onda(225.dp, 2f, .28f, retraso = 200)
                    Onda(150.dp, 2.5f, .55f, relleno = .1f, late = false)
                    Box(
                        Modifier.size(92.dp).gel(
                            30.dp,
                            Gel(
                                lugaresGelLimaNfc.fondo,
                                listOf(interna(3f, 2f, rgba(255, 255, 255, .85f)), interna(-5f, 0f, rgba(96, 140, 10, .32f)), externa(5f, 0f, Color(0xFF7FA82E)), externa(0f, 60f, rgba(200, 243, 107, .5f))),
                            ),
                        ),
                        contentAlignment = Alignment.Center,
                    ) { if (mandando) Puntitos(C.carbon) else Icono(I.nfc, 44.dp, C.carbon, 2.4f) }
                }
            }
            val sinNfc = nfc == null
            val apagado = nfc != null && !nfc.isEnabled
            T(
                if (escaneando) "Escaneá el QR de la placa" else "Apoyá el celu en la placa Caigo Tap",
                30f, 800, C.blancoCalido, alto = 1.05f, espaciado = px(-1f, 30f), alinear = TextAlign.Center,
            )
            T(
                when {
                    escaneando -> "Apuntá la cámara al código de la placa del lugar."
                    sinNfc -> "Este teléfono no tiene NFC. Escaneá el QR de la placa."
                    apagado -> "Prendé el NFC en los ajustes del teléfono, o escaneá el QR de la placa."
                    else -> "Desbloqueá el celu y apoyalo en la placa del lugar. Te aparece un aviso para confirmar."
                },
                14f, 600, textoTenue, alto = 1.5f, alinear = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.tocable { if (escaneando) escaneando = false else if (camara) escaneando = true else pedirCamara() }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Icono(if (escaneando) I.nfc else I.qr, 16.dp, C.lima, 2.2f)
                T(if (escaneando) "Usar la placa" else "Escanear el QR de la placa", 14f, 800, C.lima)
            }
            T("Cancelar", 14f, 700, textoTenue, Modifier.tocable { ir.atras() }.padding(horizontal = 18.dp, vertical = 12.dp))
        }
    }
}

/** "Adentro" (Pases NFC 1e / prototipo `tapdone`): check-in hecho, beneficio y visitas. */
@Composable
fun Adentro(ir: Ir) {
    val c = LocalApp.current
    val r = c.ultimoTap
    LugaresBarraEstado(clara = false)
    if (r == null) {
        LaunchedEffect(Unit) { ir.atras() }
        return
    }
    val nombre = c.yo.value?.nombre?.substringBefore(' ').orEmpty()
    val plan = r.plan
    val socio = r.socio
    Pantalla(oscuro = false, brillo = fondoAdentro) { arriba, abajo ->
        Column(
            Modifier.fillMaxSize().padding(top = arriba + 50.dp, start = 24.dp, end = 24.dp, bottom = abajo + 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LugaresPop {
                    Box(
                        Modifier.size(120.dp).gel(40.dp, Gel(vertical(C.carbon, C.carbon), listOf(externa(8f, 0f, C.oscuro7), externa(30f, 40f, rgba(36, 33, 45, .6f), spread = -16f)))),
                        contentAlignment = Alignment.Center,
                    ) { Icono(I.check, 60.dp, C.lima, 3f) }
                }
                T("Adentro", 58f, 800, C.carbon, alto = 1f, espaciado = px(-2.4f, 58f))
                val texto = when {
                    plan?.repetido == true -> "Ya habías hecho check-in en ${r.lugar}."
                    nombre.isNotBlank() -> "Check-in hecho en ${r.lugar}. Que lo disfrutes, $nombre."
                    else -> "Check-in hecho en ${r.lugar}. Que lo disfrutes."
                }
                T(texto, 16f, 700, verdeOscuro, alto = 1.45f, alinear = TextAlign.Center)
                if (!plan?.beneficio.isNullOrBlank()) TarjetaBeneficio("${plan!!.beneficio} desbloqueado", "Mostralo en la barra")
                if (socio?.gano == true && socio.beneficio.isNotBlank()) TarjetaBeneficio(socio.beneficio, "Lo ganaste con tu pase de socio · mostralo en la barra")
                if ((plan?.saldoCentavos ?: 0) > 0) {
                    Box(Modifier.fillMaxWidth().background(rgba(36, 33, 45, .08f), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        T("Seña paga · en el lugar pagás ${Plata.pesos(plan!!.saldoCentavos.toLong())}", 14f, 800, C.carbon)
                    }
                }
            }
            if (socio?.sumo == true) {
                T("+1 visita en tu pase de ${r.lugar} (${socio.visitas} de ${socio.meta})", 13f, 700, verdeOscuro, alinear = TextAlign.Center)
            }
            LugaresBoton(
                if (plan != null) "Volver al plan" else "Listo",
                LugaresEstiloBoton.Grafito,
                { if (plan != null) ir.plan(plan.id) else ir.atras() },
                Modifier.fillMaxWidth(), alto = 60.dp, radio = 22.dp, tam = 17f, peso = 800,
            )
        }
    }
}

@Composable
private fun TarjetaBeneficio(titulo: String, sub: String) {
    Row(
        Modifier.fillMaxWidth().gel(22.dp, Gel(vertical(C.blanco, C.blanco), listOf(externa(4f, 0f, C.limaBase)))).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp).background(C.carbon, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icono(I.regalo, 24.dp, C.lima, 2.2f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(titulo, 15f, 800, C.carbon)
            T(sub, 12f, 600, C.texto2)
        }
    }
}
