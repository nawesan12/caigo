package ar.com.caigo.app.piezas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.rgba
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Estado de una carga: lo que trae la API, el error para mostrar y si está cargando. */
@Stable
class Carga<T>(private val cargar: suspend () -> T) {
    var dato by mutableStateOf<T?>(null)
    var error by mutableStateOf<String?>(null)
    var cargando by mutableStateOf(true)
    internal var vuelta by mutableIntStateOf(0)

    fun recargar() { vuelta++ }

    internal suspend fun correr() {
        cargando = true
        try {
            dato = cargar()
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = mensajeDe(e)
        } finally {
            cargando = false
        }
    }
}

/** Carga datos de la API al entrar (y otra vez con [Carga.recargar] o si cambia [clave]). */
@Composable
fun <T> rememberCarga(vararg clave: Any?, cargar: suspend () -> T): Carga<T> {
    val c = remember(*clave) { Carga(cargar) }
    LaunchedEffect(c, c.vuelta) { c.correr() }
    return c
}

/** Lo que se muestra mientras una carga no trae nada: los puntitos o el error con "Reintentar". */
@Composable
fun <V> EstadoCarga(carga: Carga<V>, modifier: Modifier = Modifier) {
    if (carga.dato != null) return
    val p = LocalPaleta.current
    val e = carga.error
    if (e != null) {
        Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            MensajeCentro("Uy, algo falló", e)
            Cta("Reintentar", { carga.recargar() }, Modifier.width(200.dp), gel = Gels.violeta, alto = 52.dp, radio = 18.dp, tam = 16f, color = C.blanco)
        }
    } else {
        Box(modifier.padding(40.dp)) { Puntitos(p.acento) }
    }
}

/**
 * `.refreshable` del iPhone: tirar hacia abajo vuelve a cargar. [contenido] tiene que poder
 * desplazarse (una lista o una columna con verticalScroll).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Refrescable(alRefrescar: suspend () -> Unit, modifier: Modifier = Modifier, contenido: @Composable BoxScope.() -> Unit) {
    var refrescando by remember { mutableStateOf(false) }
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refrescando,
        onRefresh = {
            alcance.launch {
                refrescando = true
                try { alRefrescar() } finally { refrescando = false }
            }
        },
        modifier = modifier,
        content = contenido,
    )
}

// ——— Toasts (2,6 s, arriba, como el prototipo) ———

data class Aviso(val texto: String, val error: Boolean = false, val id: Long = System.nanoTime())

class Avisos {
    var actual by mutableStateOf<Aviso?>(null)
        private set

    fun ok(t: String) { actual = Aviso(t) }
    fun error(t: String) { actual = Aviso(t, error = true) }
    fun error(e: Throwable) { actual = Aviso(mensajeDe(e), error = true) }
    fun limpiar() { actual = null }
}

val LocalAvisos = staticCompositionLocalOf { Avisos() }

@Composable
fun BoxScope.CapaAvisos(avisos: Avisos, arriba: Dp) {
    val a = avisos.actual
    LaunchedEffect(a?.id) { if (a != null) { delay(2600); if (avisos.actual?.id == a.id) avisos.limpiar() } }
    // El último aviso visto, para que se siga viendo mientras sale.
    val ultimo = remember { arrayOfNulls<Aviso>(1) }
    if (a != null) ultimo[0] = a
    AnimatedVisibility(
        a != null,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = arriba + 10.dp, start = 20.dp, end = 20.dp).zIndex(40f),
        enter = fadeIn(tween(250, easing = FastOutSlowInEasing)) + slideInVertically(tween(250, easing = FastOutSlowInEasing)) { -it },
        exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) + slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it },
    ) {
        val v = a ?: ultimo[0] ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth()
                .tocable { avisos.limpiar() }
                .gel(20.dp, Brush.verticalGradient(listOf(C.carbon, C.carbon)), listOf(externa(18f, 30f, rgba(0, 0, 0, .5f), spread = -12f)))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(28.dp).background(if (v.error) C.coral else C.lima, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icono(if (v.error) I.cruz else I.check, 14.dp, C.carbon, 3.4f)
            }
            T(v.texto, 14f, 700, C.blancoCalido, alto = 1.4f, modifier = Modifier.weight(1f))
        }
    }
}

// ——— Compartir y abrir ———

/** Abre una URL en una Custom Tab con los colores de Caigo (Mercado Pago, Wallet, Clerk). */
fun abrirPestana(ctx: Context, url: String) {
    val colores = CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF201D2B.toInt()).build()
    runCatching {
        CustomTabsIntent.Builder().setDefaultColorSchemeParams(colores).setShowTitle(true).build().launchUrl(ctx, Uri.parse(url))
    }.onFailure {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun compartir(ctx: Context, texto: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
    ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Comparte por WhatsApp si está instalado; si no, el selector común. */
fun compartirWhatsApp(ctx: Context, texto: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto).setPackage("com.whatsapp")
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { compartir(ctx, texto) }
}

fun copiar(ctx: Context, texto: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Caigo", texto))
}

fun tienePermiso(ctx: Context, permiso: String) = ContextCompat.checkSelfPermission(ctx, permiso) == PackageManager.PERMISSION_GRANTED

/**
 * Pide un permiso cuando hace falta. Devuelve (concedido, pedir()).
 */
@Composable
fun rememberPermiso(permiso: String, alResponder: (Boolean) -> Unit = {}): Pair<Boolean, () -> Unit> {
    val ctx = LocalContext.current
    var concedido by remember { mutableStateOf(tienePermiso(ctx, permiso)) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        concedido = it
        alResponder(it)
    }
    return concedido to { if (concedido) alResponder(true) else lanzador.launch(permiso) }
}

/**
 * Pide la ubicación: FINE y COARSE juntas (Android 12+ ignora el pedido de FINE sola) y toma como concedida
 * cualquiera de las dos, así la «aproximada» también sirve. Devuelve (concedido, pedir()).
 */
@Composable
fun rememberPermisoUbicacion(alResponder: (Boolean) -> Unit = {}): Pair<Boolean, () -> Unit> {
    val ctx = LocalContext.current
    val permisos = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    var concedido by remember { mutableStateOf(permisos.any { tienePermiso(ctx, it) }) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        concedido = r.values.any { it } || permisos.any { tienePermiso(ctx, it) }
        alResponder(concedido)
    }
    return concedido to { if (concedido) alResponder(true) else lanzador.launch(permisos) }
}

/** Lo del sistema que usan las pantallas (abrir, copiar, compartir, vibrar), con los nombres del iPhone. */
object Sistema {
    /** Abre una URL fuera de la app (el navegador o la app que la atienda). */
    fun abrir(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun copiar(ctx: Context, texto: String) = ar.com.caigo.app.piezas.copiar(ctx, texto)

    /** Comparte por WhatsApp si está instalado; si no, la hoja común de compartir. */
    fun compartirWhatsApp(ctx: Context, texto: String) = ar.com.caigo.app.piezas.compartirWhatsApp(ctx, texto)

    fun compartir(ctx: Context, texto: String) = ar.com.caigo.app.piezas.compartir(ctx, texto)

    /** Vibración corta de confirmación (o de error). */
    fun vibrar(vista: View, exito: Boolean = true) {
        val tipo = if (Build.VERSION.SDK_INT >= 30) {
            if (exito) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        vista.performHapticFeedback(tipo)
    }
}
