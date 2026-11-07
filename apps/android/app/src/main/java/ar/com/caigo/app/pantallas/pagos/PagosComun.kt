package ar.com.caigo.app.pantallas.pagos

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.piezas.CodigoQr
import ar.com.caigo.app.piezas.EscanerCodigos
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.abrirPestana
import ar.com.caigo.app.piezas.rememberPermiso
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.launch

/*
 * Piezas que comparten Plus, entradas, pases y Caigo Tap (PagosComun.swift del iPhone). Salen del
 * prototipo y de "Caigo Pases NFC" (con Geist en vez de Bricolage).
 */

/** La Activity de un Context (para la ventana, Play Billing y el brillo). */
internal fun Context.pagosActividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.pagosActividad()
    else -> null
}

/**
 * Íconos de la barra de estado claros (fondos oscuros: Plus, Tap) u oscuros (en iOS lo da
 * `.environment(\.paleta, …)` de cada pantalla).
 */
@Composable
internal fun PagosBarraEstado(clara: Boolean) {
    val vista = LocalView.current
    val oscuroSistema = LocalPaleta.current.oscuro
    DisposableEffect(clara) {
        val ventana = vista.context.pagosActividad()?.window
        val ctl = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        ctl?.isAppearanceLightStatusBars = !clara
        onDispose { ctl?.isAppearanceLightStatusBars = !oscuroSistema }
    }
}

/** Lima gel de los Pases NFC y el paywall (base `#7FA82E` y brillo lima). */
internal val pagosGelLimaNfc = Gel(
    // Gels.fondoLima del iPhone.
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
internal fun PagosPillPlus(modifier: Modifier = Modifier, tam: Float = 20f) {
    Box(modifier.graphicsLayer { rotationZ = 10f }.gel(14.dp, pagosGelLimaNfc).padding(horizontal = 14.dp, vertical = 8.dp)) {
        T("PLUS", tam, 800, C.carbon, alto = 1f, unRenglon = true)
    }
}

/** Botón de ancho completo de los Pases NFC: negro de Wallet, blanco con borde, grafito o lima. */
internal enum class PagosEstiloBoton { Negro, Blanco, Grafito, Lima }

@Composable
internal fun PagosBoton(
    texto: String,
    estilo: PagosEstiloBoton,
    modifier: Modifier = Modifier,
    icono: String? = null,
    alto: Dp = 56.dp,
    radio: Dp = 18.dp,
    tam: Float = 16f,
    peso: Int = 700,
    cargando: Boolean = false,
    accion: () -> Unit,
) {
    val (gel, color) = when (estilo) {
        PagosEstiloBoton.Negro -> Gel(vertical(C.negro, C.negro), listOf(externa(4f, 0f, Color(0xFF333333)))) to C.blanco
        PagosEstiloBoton.Blanco -> Gel(vertical(C.blanco, C.blanco), listOf(interna(0f, 0f, C.borde3, spread = -1.5f), externa(4f, 0f, C.borde3))) to C.carbon
        PagosEstiloBoton.Grafito -> Gel(vertical(C.carbon, C.carbon), listOf(externa(5f, 0f, C.oscuro7))) to C.blanco
        PagosEstiloBoton.Lima -> Gel(vertical(C.lima, C.lima), listOf(externa(4f, 0f, C.limaBase))) to C.carbon
    }
    Box(
        modifier.fillMaxWidth().height(alto).toque(!cargando, onClick = accion).gel(radio, gel),
        contentAlignment = Alignment.Center,
    ) {
        if (cargando) {
            Puntitos(color)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icono != null) Icono(icono, 22.dp, color, 2f)
                T(texto, tam, peso, color, unRenglon = true)
            }
        }
    }
}

/** "Hoy 19:30 · Café Molienda" */
internal fun pagosCuandoDonde(iso: String, lugar: String): String =
    listOf(Fecha.cuando(iso), lugar).filter { it.isNotEmpty() }.joinToString(" · ")

/** El lugar de un plan: el del lugar aliado o el que escribió quien organiza. */
internal fun pagosLugarDe(p: Plan): String {
    val n = p.lugar?.nombre
    if (!n.isNullOrEmpty()) return n
    return p.lugarNombre
}

// PARA EL NÚCLEO: `Gels.volver` del iPhone (Tema/Gel.swift) no está en tema/Gel.kt.
private val gelVolver = Gel(vertical(C.blanco, Color(0xFFF3F0FA)), listOf(interna(2f, 0f, C.blanco), externa(3f, 0f, C.borde3)))

/** Botón volver blanco con base lavanda + título (Tu entrada, Mis pases). */
@Composable
internal fun PagosEncabezado(titulo: String, modifier: Modifier = Modifier, icono: String = I.atras, tam: Float = 28f, volver: () -> Unit) {
    val p = LocalPaleta.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BotonIcono(
            icono, volver,
            iconoTam = if (icono == I.cruz) 20.dp else 22.dp,
            trazo = if (icono == I.cruz) 2.6f else 2.4f,
            gel = gelVolver,
        )
        T(titulo, tam, 800, p.texto, Modifier.weight(1f), alto = 1f, espaciado = px(-.8f * tam / 28f, tam), unRenglon = true)
    }
}

/** Código QR con módulos nítidos (carbón sobre transparente). */
@Composable
internal fun PagosCodigoQr(texto: String, modifier: Modifier = Modifier) {
    CodigoQr(texto, modifier.semantics { contentDescription = "Código QR $texto" })
}

// MARK: - Google Wallet (Apple Wallet en el iPhone)

/**
 * En Android el pase se guarda en Google Wallet: la API da el link «Guardar en Google Wallet»
 * (`GET /v1/pases/…/google`) y se abre. Si la API responde `sin_wallet`, el botón se esconde.
 */
internal object PagosWallet {
    /** Abre el link de Google Wallet. Devuelve si se abrió. */
    suspend fun agregar(ctx: Context, bajar: suspend () -> String): Boolean {
        val link = bajar()
        if (link.isBlank()) return false
        abrirPestana(ctx, link)
        return true
    }
}

/** Botón negro "Guardar en Google Wallet" (el de los Pases NFC, 1c). */
@Composable
internal fun PagosBotonWallet(modifier: Modifier = Modifier, bajar: suspend () -> String) {
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(false) }
    var agregado by remember { mutableStateOf(false) }
    var sinWallet by remember { mutableStateOf(false) }
    if (sinWallet) return
    PagosBoton(
        if (agregado) "En tu Wallet" else "Guardar en Google Wallet", PagosEstiloBoton.Negro, modifier,
        icono = I.billetera, radio = 20.dp, peso = 800, cargando = cargando,
    ) {
        alcance.launch {
            cargando = true
            try {
                agregado = PagosWallet.agregar(ctx, bajar)
            } catch (e: ErrorApi) {
                if (e.codigo == "sin_wallet") sinWallet = true
                avisos.error(e.detalle)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            } finally {
                cargando = false
            }
        }
    }
}

// MARK: - Escáner de QR (ML Kit; VisionKit en el iPhone)

/** Escáner de códigos QR con la cámara. Llama a [leido] con el texto (una vez por código). */
@Composable
internal fun PagosEscanerQr(modifier: Modifier = Modifier, leido: (String) -> Unit) {
    val (camara, pedir) = rememberPermiso(android.Manifest.permission.CAMERA)
    var preguntado by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!camara) { preguntado = true; pedir() } }
    var ultimo by remember { mutableStateOf("") }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (camara) {
            EscanerCodigos(Modifier.matchParentSize()) { v -> if (v != ultimo) { ultimo = v; leido(v) } }
        } else if (preguntado) {
            T("La cámara no está disponible para escanear.", 14f, 600, C.apagado, Modifier.padding(24.dp), alinear = TextAlign.Center)
        }
    }
}

// MARK: - Animaciones y háptico (Piezas/Comunes.swift y Utiles.swift del iPhone)

/**
 * `tk-pop` (`.pop()` del iPhone): escala .4 → 1.08 → 1 con rotación −20° → 6° → 0°, 270 ms con
 * ease-out y 180 ms con ease-in-out.
 * PARA EL NÚCLEO: `Modifier.pop()` compartido en piezas/.
 */
@Composable
internal fun PagosPop(modifier: Modifier = Modifier, contenido: @Composable BoxScope.() -> Unit) {
    val s = remember { Animatable(.4f) }
    val r = remember { Animatable(-20f) }
    LaunchedEffect(Unit) {
        launch { s.animateTo(1.08f, tween(270, easing = LinearOutSlowInEasing)); s.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
        launch { r.animateTo(6f, tween(270, easing = LinearOutSlowInEasing)); r.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) }
    }
    Box(
        modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; rotationZ = r.value },
        contentAlignment = Alignment.Center,
        content = contenido,
    )
}

/**
 * `tk-up` (`.sube()` del iPhone): entra subiendo 20 y apareciendo, 250 ms con ease-out.
 * PARA EL NÚCLEO: `Modifier.sube()` compartido en piezas/.
 */
@Composable
internal fun PagosSube(modifier: Modifier = Modifier, retraso: Int = 0, contenido: @Composable BoxScope.() -> Unit) {
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, tween(250, retraso, LinearOutSlowInEasing)) }
    Box(
        modifier.graphicsLayer { alpha = k.value; translationY = (1f - k.value) * 20.dp.toPx() },
        contentAlignment = Alignment.Center,
        content = contenido,
    )
}

/**
 * `Sistema.vibrar()` del iPhone (notificación de éxito o de error).
 * PARA EL NÚCLEO: `Sistema.vibrar(exito)` compartido en piezas/.
 */
internal fun pagosVibrar(vista: View, exito: Boolean = true) {
    val tipo = if (android.os.Build.VERSION.SDK_INT >= 30) {
        if (exito) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    vista.performHapticFeedback(tipo)
}
