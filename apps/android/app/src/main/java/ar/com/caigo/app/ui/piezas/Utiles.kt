package ar.com.caigo.app.ui.piezas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import ar.com.caigo.app.datos.mensajeDe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Hora de Argentina: todo lo que se muestra va en esta zona. */
val ZonaAR: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
private val esAR: Locale = Locale.forLanguageTag("es-AR")

/** Categorías de plan (planes.go · Categorias), en el orden del handoff. */
val Categorias = linkedMapOf(
    "deporte" to "Deporte", "juego" to "Juego", "previa" to "Previa", "salida" to "Salida",
    "cita_doble" to "Cita doble", "cultura" to "Cultura", "gastronomia" to "Gastronomía", "otro" to "Otro",
    "taller" to "Taller", "musica" to "Música en vivo", "fiesta" to "Fiesta",
)

fun nombreCategoria(clave: String): String = Categorias[clave] ?: clave.replaceFirstChar { it.uppercase() }

object Fecha {
    fun parse(iso: String): ZonedDateTime? = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZonaAR) }.getOrNull()

    private val dias = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private val diasLargos = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    fun hora(t: ZonedDateTime): String = "%02d:%02d".format(t.hour, t.minute)
    fun hora(iso: String): String = parse(iso)?.let(::hora).orEmpty()

    fun diaCorto(t: ZonedDateTime): String = dias[t.dayOfWeek.value - 1]
    fun diaLargo(t: ZonedDateTime): String = diasLargos[t.dayOfWeek.value - 1]

    /** "Hoy 20:30", "Mañana 21:00", "Sáb 10:00" (como las etiquetas de las tarjetas). */
    fun cuando(iso: String, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String {
        val t = parse(iso) ?: return ""
        val dias = ChronoUnit.DAYS.between(ahora.toLocalDate(), t.toLocalDate())
        return when (dias) {
            0L -> "Hoy ${hora(t)}"
            1L -> "Mañana ${hora(t)}"
            else -> "${diaCorto(t)} ${hora(t)}"
        }
    }

    /** "Ahora · en 2 h", "Ahora · en 40 min" (chip coral del detalle cuando arranca pronto) o null. */
    fun enCuanto(iso: String, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String? {
        val t = parse(iso) ?: return null
        val min = ChronoUnit.MINUTES.between(ahora, t)
        return when {
            min <= 0 -> "Ahora"
            min < 60 -> "Ahora · en $min min"
            min <= 180 -> "Ahora · en ${(min + 30) / 60} h"
            else -> null
        }
    }

    /** "Hoy, 20:30 a 21:30" */
    fun rango(iso: String, duracionMin: Int, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String {
        val t = parse(iso) ?: return ""
        val fin = t.plusMinutes(duracionMin.toLong())
        val dia = when (ChronoUnit.DAYS.between(ahora.toLocalDate(), t.toLocalDate())) {
            0L -> "Hoy"
            1L -> "Mañana"
            else -> "${diaLargo(t)} ${t.dayOfMonth}"
        }
        return "$dia, ${hora(t)} a ${hora(fin)}"
    }

    fun iso(t: ZonedDateTime): String = t.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun hoy(): LocalDate = LocalDate.now(ZonaAR)

    /** "hace 20 min", "hace 2 h", "ayer", "hace 3 días". */
    fun hace(iso: String, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String {
        val t = parse(iso) ?: return ""
        val min = ChronoUnit.MINUTES.between(t, ahora)
        return when {
            min < 1 -> "recién"
            min < 60 -> "hace $min min"
            min < 24 * 60 && t.toLocalDate() == ahora.toLocalDate() -> "hace ${min / 60} h"
            t.toLocalDate() == ahora.toLocalDate().minusDays(1) -> "ayer"
            else -> "hace ${ChronoUnit.DAYS.between(t.toLocalDate(), ahora.toLocalDate())} días"
        }
    }
}

/** "800 m", "1,2 km". */
fun distancia(m: Int?): String = when {
    m == null -> ""
    m < 100 -> "$m m"
    m < 1000 -> "${((m + 50) / 100) * 100} m"
    m < 10_000 -> String.format(esAR, "%.1f km", m / 1000.0).replace('.', ',')
    else -> "${m / 1000} km"
}

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

/** Link público de un plan. */
fun linkPlan(slug: String) = "caigo.com.ar/p/$slug"

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
