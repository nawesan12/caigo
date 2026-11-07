package ar.com.caigo.app.pantallas.planes

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.R
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.app.Pestana
import ar.com.caigo.app.nucleo.CambiosPlan
import ar.com.caigo.app.nucleo.Categorias
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.PlanNuevo
import ar.com.caigo.app.nucleo.Plata
import ar.com.caigo.app.nucleo.Problema
import ar.com.caigo.app.nucleo.ZonaAR
import ar.com.caigo.app.nucleo.latMDP
import ar.com.caigo.app.nucleo.linkPlan
import ar.com.caigo.app.nucleo.lngMDP
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.BarraPasos
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.piezas.Chip
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.Interruptor
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.Segmentado
import ar.com.caigo.app.piezas.Sistema
import ar.com.caigo.app.piezas.fondoCaigo
import ar.com.caigo.app.piezas.jpegDe
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.estilo
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.rgba
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

private val horasCrear = listOf("20:30", "21:30", "22:30")
/** Cuánto dura (minutos): las mismas piezas que las horas de 06a. */
private val duracionesCrear = listOf(60 to "1 h", 120 to "2 h", 180 to "3 h", 300 to "5 h")
private val nivelesCrear = listOf("Recién aprendo", "Me defiendo", "Canto falta envido")
private const val EDAD_MIN = 18.0
private const val EDAD_MAX = 60.0
private val bordeSuave = Color(0xFFECE8F2)
private val bordeSuaveOscuro = Color(0xFF3A3550)

/**
 * Borrador del plan con los valores por defecto del README: Hoy 21:30, Somos 3 / Falta 1, con
 * aprobación, verificados y dirección oculta.
 */
private class Borrador {
    var titulo by mutableStateOf("")
    var categoria by mutableStateOf("juego")
    var cuando by mutableIntStateOf(1) // 0 Ahora · 1 Hoy · 2 Otro día
    var hora by mutableStateOf("21:30")
    var horaOtra by mutableStateOf(false)
    var h by mutableIntStateOf(21)
    var m by mutableIntStateOf(30)
    var dia by mutableIntStateOf(1)
    var repite by mutableStateOf(false)
    var lugar by mutableStateOf("")
    var direccion by mutableStateOf("")
    var zona by mutableStateOf("")
    var lat by mutableDoubleStateOf(latMDP)
    var lng by mutableDoubleStateOf(lngMDP)
    var somos by mutableIntStateOf(3)
    var faltan by mutableIntStateOf(1)
    var edadDesde by mutableDoubleStateOf(EDAD_MIN)
    var edadHasta by mutableDoubleStateOf(EDAD_MAX)
    var genero by mutableStateOf("cualquiera")
    var nivel by mutableStateOf("")
    var ingreso by mutableStateOf("aprobacion")
    var visibilidad by mutableIntStateOf(0)
    var verificados by mutableStateOf(true)
    var ocultar by mutableStateOf(true)
    var costo by mutableStateOf<Int?>(null)
    var descripcion by mutableStateOf("")
    var duracion by mutableIntStateOf(120)
    var foto by mutableStateOf("")
    var subiendoFoto by mutableStateOf(false)
    /** Ciudad que salió de la geocodificación (o del perfil); si no hay, se deduce de la latitud. */
    var ciudadElegida by mutableStateOf("")
    /** Editando: los que ya van no se pueden sacar del contador (el cupo no baja de ahí). */
    var somosMin by mutableIntStateOf(1)
    var editando by mutableStateOf(false)

    fun empieza(): ZonedDateTime {
        val ahora = ZonedDateTime.now(ZonaAR)
        if (cuando == 0) return ahora.plusMinutes(15)
        val (hh, mm) = if (horaOtra) h to m else hora.split(":").mapNotNull { it.toIntOrNull() }.let { (it.firstOrNull() ?: 21) to (it.lastOrNull() ?: 30) }
        val base = ahora.toLocalDate().plusDays(if (cuando == 1) 0L else dia.toLong())
        var t = base.atTime(hh, mm).atZone(ZonaAR)
        if (cuando == 1 && t.isBefore(ahora)) t = t.plusDays(1)
        return t
    }

    val ciudad: String get() = ciudadElegida.ifEmpty { if (lat > -37.6) "Villa Gesell" else "Mar del Plata" }
    /** Lo que va después de «en» en el título por defecto: la zona, o la ciudad. */
    val dondeCorto: String get() = zona.ifEmpty { ciudad }
    val nombreCategoria: String get() = Categorias.nombre(categoria)
}

/** Centro de Villa Gesell (si el perfil dice esa ciudad y no hay ubicación). */
private const val latGesell = -37.2639
private const val lngGesell = -56.9730

/**
 * 06a–06c · Crear plan en tres pasos (menos de 30 s). Con [editar], los mismos pasos cargados con
 * el plan y «Guardar cambios» (PATCH /v1/planes/{id}, contrato §2).
 */
@Composable
fun Crear(ir: Ir, editar: String? = null) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val vista = LocalView.current
    val avisos = LocalAvisos.current
    val pal = LocalPaleta.current
    val alcance = rememberCoroutineScope()
    val b = remember { Borrador() }
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var eligiendoLugar by remember { mutableStateOf(false) }
    var editandoCosto by remember { mutableStateOf(false) }
    var publicando by remember { mutableStateOf(false) }
    // El plan como estaba (editando) y la fecha tal como quedó al cargarlo.
    var original by remember { mutableStateOf<Plan?>(null) }
    var empiezaCargada by remember { mutableStateOf<ZonedDateTime?>(null) }
    var errorCarga by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (editar != null) {
            // Editar: carga el plan en el borrador.
            try {
                val p = c.api.plan(editar)
                b.titulo = p.titulo
                b.categoria = p.categoria
                b.descripcion = p.descripcion
                b.duracion = p.duracionMin
                b.foto = p.foto
                b.repite = p.recurrente
                b.lugar = p.lugarNombre
                b.direccion = p.direccion?.texto.orEmpty()
                b.zona = p.zona
                b.ciudadElegida = p.ciudad
                b.lat = p.direccion?.let { if (it.lat != 0.0) it.lat else p.lat } ?: p.lat
                b.lng = p.direccion?.let { if (it.lng != 0.0) it.lng else p.lng } ?: p.lng
                b.somos = maxOf(1, p.somos)
                b.somosMin = maxOf(1, p.somos)
                b.editando = true
                b.faltan = maxOf(0, p.total - p.somos)
                b.edadDesde = (p.edadMin ?: EDAD_MIN.toInt()).toDouble()
                b.edadHasta = minOf(p.edadMax ?: EDAD_MAX.toInt(), EDAD_MAX.toInt()).toDouble()
                b.genero = p.genero
                b.nivel = p.nivel
                b.ingreso = p.ingreso
                b.visibilidad = listOf("publico", "mi_gente", "link").indexOf(p.visibilidad).let { if (it < 0) 0 else it }
                b.verificados = p.soloVerificados
                b.ocultar = p.ocultarDireccion
                b.costo = p.costoCentavos
                val t = Fecha.parse(p.empiezaEn)
                if (t != null) {
                    b.h = t.hour
                    b.m = t.minute
                    b.horaOtra = true
                    val d = Fecha.diasEntre(ZonedDateTime.now(ZonaAR), t)
                    if (d <= 0) {
                        b.cuando = 1
                    } else {
                        b.cuando = 2
                        b.dia = minOf(d, 6)
                    }
                }
                empiezaCargada = b.empieza()
                original = p
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorCarga = mensajeDe(e)
            }
            return@LaunchedEffect
        }
        if (c.yo.value == null) c.recargarYo()
        // Sin ubicación: la zona y la ciudad del perfil (y el centro de esa ciudad).
        val y = c.yo.value
        if (b.zona.isEmpty()) b.zona = y?.zona.orEmpty()
        if (b.ciudadElegida.isEmpty()) b.ciudadElegida = y?.ciudad.orEmpty()
        if (b.ciudadElegida == "Villa Gesell") { b.lat = latGesell; b.lng = lngGesell }
        val u = c.ubicacion()
        if (u != null) {
            val (la, ln) = u
            b.lat = la; b.lng = ln
            // Con ubicación: la zona y la ciudad de donde está (geocodificación inversa).
            val zc = crearZonaDe(ctx, la, ln)
            if (zc != null) {
                val (zona, ciudad) = zc
                if (zona.isNotEmpty() && b.lugar.isEmpty() && b.direccion.isEmpty()) b.zona = zona
                if (ciudad.isNotEmpty()) b.ciudadElegida = ciudad
            }
        }
    }

    /** Manda sólo lo que cambió. La fecha viaja si se tocó el día o la hora. */
    fun guardar() {
        val o = original ?: return
        val id = editar ?: return
        if (b.lugar.isEmpty() && b.direccion.isEmpty()) {
            avisos.error("Marcá dónde es el plan.")
            eligiendoLugar = true
            return
        }
        fun <T> cambio(nuevo: T, viejo: T): T? = if (nuevo == viejo) null else nuevo
        val titulo = b.titulo.trim()
        val cargada = empiezaCargada
        val empiezaEn = if (cargada != null && abs(Duration.between(cargada, b.empieza()).seconds) > 59) Fecha.iso(b.empieza()) else null
        // Sin límite de edad: el rango entero (la API no borra con null).
        val dMin = b.edadDesde.roundToInt()
        val dMax = b.edadHasta.roundToInt()
        val nuevoMin: Int? = if (dMin > EDAD_MIN.toInt()) dMin else null
        val nuevoMax: Int? = if (dMax < EDAD_MAX.toInt()) dMax else null
        val lugar = b.lugar.trim()
        val dir = b.direccion.trim()
        val zona = b.zona.trim()
        val lat0 = o.direccion?.let { if (it.lat != 0.0) it.lat else o.lat } ?: o.lat
        val lng0 = o.direccion?.let { if (it.lng != 0.0) it.lng else o.lng } ?: o.lng
        val movio = lugar != o.lugarNombre || dir != o.direccion?.texto.orEmpty() || zona != o.zona ||
            abs(b.lat - lat0) > 0.00001 || abs(b.lng - lng0) > 0.00001
        val k = CambiosPlan(
            titulo = if (titulo.isNotEmpty()) cambio(titulo, o.titulo) else null,
            descripcion = cambio(b.descripcion.trim(), o.descripcion),
            duracionMin = cambio(b.duracion, o.duracionMin),
            empiezaEn = empiezaEn,
            total = cambio(b.somos + b.faltan, o.total),
            ingreso = cambio(b.ingreso, o.ingreso),
            visibilidad = cambio(listOf("publico", "mi_gente", "link")[b.visibilidad], o.visibilidad),
            soloVerificados = cambio(b.verificados, o.soloVerificados),
            ocultarDireccion = cambio(b.ocultar, o.ocultarDireccion),
            edadMin = if (nuevoMin != o.edadMin) nuevoMin ?: EDAD_MIN.toInt() else null,
            edadMax = if (nuevoMax != o.edadMax) nuevoMax ?: 120 else null,
            genero = cambio(b.genero, o.genero),
            nivel = cambio(b.nivel, o.nivel),
            costoCentavos = if (b.costo != o.costoCentavos) b.costo ?: 0 else null,
            foto = cambio(b.foto, o.foto),
            lugarNombre = if (movio) lugar else null,
            direccion = if (movio) dir else null,
            zona = if (movio) zona else null,
            lat = if (movio) b.lat else null,
            lng = if (movio) b.lng else null,
        )
        alcance.launch {
            publicando = true
            try {
                c.api.editarPlan(id, k)
                Sistema.vibrar(vista)
                avisos.ok(if (k.empiezaEn != null || k.lat != null) "Listo. Les avisamos del cambio a los que van." else "Listo, guardamos los cambios.")
                ir.atras()
            } catch (e: ErrorApi) {
                // 409 cupo_menor: el detalle dice cuántos van.
                avisos.error(e.problema.issues.firstOrNull()?.message ?: e.detalle)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            } finally {
                publicando = false
            }
        }
    }

    fun publicar() {
        if (b.lugar.isEmpty() && b.direccion.isEmpty()) {
            avisos.error("Marcá dónde es el plan.")
            eligiendoLugar = true
            return
        }
        alcance.launch {
            publicando = true
            val titulo = b.titulo.trim()
            val n = PlanNuevo(
                titulo = titulo.ifEmpty { "${b.nombreCategoria} en ${b.dondeCorto}" },
                categoria = b.categoria, empiezaEn = Fecha.iso(b.empieza()), ciudad = b.ciudad, lat = b.lat, lng = b.lng,
                somos = b.somos, faltan = b.faltan,
                recurrente = b.repite,
                zona = b.zona.trim(),
                lugarNombre = b.lugar.trim(),
                direccion = b.direccion.trim(),
                ingreso = b.ingreso,
                visibilidad = listOf("publico", "mi_gente", "link")[b.visibilidad],
                soloVerificados = b.verificados,
                ocultarDireccion = b.ocultar,
                edadMin = b.edadDesde.roundToInt().takeIf { it > EDAD_MIN.toInt() },
                edadMax = b.edadHasta.roundToInt().takeIf { it < EDAD_MAX.toInt() },
                genero = b.genero,
                nivel = b.nivel,
                costoCentavos = b.costo,
                descripcion = b.descripcion.trim(),
                duracionMin = b.duracion,
                foto = b.foto,
            )
            try {
                val p = c.api.crearPlan(n)
                Sistema.vibrar(vista)
                ir.publicado(p.id)
            } catch (e: ErrorApi) {
                avisos.error(e.problema.issues.firstOrNull()?.message ?: e.detalle)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            } finally {
                publicando = false
            }
        }
    }

    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        b.subiendoFoto = true
        alcance.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    val bm = runCatching { ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
                        ?: throw ErrorApi(0, Problema(detail = "No pudimos leer esa foto. Probá con otra."))
                    jpegDe(bm, 0, 86, 1600)
                }
                b.foto = c.api.subir("foto", jpeg).url
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            }
            b.subiendoFoto = false
        }
    }

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = arriba + 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BotonIcono(
                        if (paso == 0) I.cruz else I.atras, { if (paso == 0) ir.atras() else paso -= 1 },
                        Modifier.semantics { contentDescription = if (paso == 0) "Cerrar" else "Atrás" },
                    )
                    Spacer(Modifier.weight(1f))
                    T("${paso + 1} de 3", 14f, 700, pal.texto2)
                }
                BarraPasos(paso + 1, 3)
            }
            Crossfade(paso, Modifier.weight(1f), label = "paso") { pasoActual ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 130.dp + abajo),
                    verticalArrangement = Arrangement.spacedBy(if (pasoActual == 2) 16.dp else 20.dp),
                ) {
                    if (editar != null && original == null) {
                        val e = errorCarga
                        if (e != null) T(e, 16f, 600, pal.texto, Modifier.fillMaxWidth().padding(top = 60.dp), alinear = TextAlign.Center)
                        else Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { Puntitos(pal.acento) }
                    } else when (pasoActual) {
                        0 -> Paso1(b, editar != null) { eligiendoLugar = true }
                        1 -> Paso2(b)
                        else -> Paso3(b, { editandoCosto = true }) { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    }
                }
            }
        }
        // Velo del color del fondo detrás del botón, como la barra de abajo del prototipo.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp + abajo)
                .background(Brush.verticalGradient(0f to pal.fondo.copy(alpha = 0f), .35f to pal.fondo)),
        )
        Cta(
            if (paso < 2) "Siguiente" else if (editar != null) "Guardar cambios" else "Publicar plan",
            { if (paso < 2) paso += 1 else if (editar != null) guardar() else publicar() },
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp + abajo),
            gel = if (paso < 2) Gels.violeta else Gels.limaChico, alto = 58.dp,
            color = if (paso < 2) C.blanco else C.carbon, icono = if (paso == 2 && editar == null) I.mas else null, iconoTrazo = 4f, iconoTam = 18.dp,
            habilitado = !(editar != null && original == null) && !b.subiendoFoto, cargando = publicando,
        )
        if (eligiendoLugar) ElegirLugar(b, alcance) { eligiendoLugar = false }
        if (editandoCosto) EditarCosto(b) { editandoCosto = false }
    }
}

/** Zona (barrio) y ciudad de un punto, con la geocodificación inversa (Geocoder en vez de CLGeocoder). */
internal suspend fun crearZonaDe(ctx: android.content.Context, lat: Double, lng: Double): Pair<String, String>? {
    val a = sitioEn(ctx, lat, lng) ?: return null
    return a.subLocality.orEmpty() to a.locality.orEmpty()
}

/** Campo del alta (58, radio 18, borde violeta). */
@Composable
private fun CampoCrear(texto: String, alCambiar: (String) -> Unit, pista: String, tam: Float = 18f, alto: Dp = 58.dp, teclado: KeyboardType = KeyboardType.Text) {
    val pal = LocalPaleta.current
    PlanesCampo(
        texto, alCambiar, pista,
        Modifier.fillMaxWidth().height(alto).background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(18.dp))
            .border(2.dp, C.violeta, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp),
        tam = tam, peso = 600, colorPista = C.apagado, teclado = teclado,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Paso1(b: Borrador, editando: Boolean, alLugar: () -> Unit) {
    val pal = LocalPaleta.current
    T(if (editando) "Editá tu plan" else "¿Qué hacemos?", 32f, 700, pal.texto, alto = 1.05f, espaciado = px(-.8f, 32f))
    CampoCrear(b.titulo, { b.titulo = it.take(80) }, "${b.nombreCategoria} en ${b.dondeCorto}")
    // La categoría no se cambia después de publicar (no está entre lo que se edita).
    if (!editando) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Categorias.paraCrear.forEach { (k, l) ->
            // En oscuro, el chip apagado es el oscuro (el claro quedaba como una pastilla blanca suelta).
            Chip(l, b.categoria == k, { b.categoria = k }, gelOn = planesVioletaChico, gelOff = if (pal.oscuro) Gels.chipOscuro else Gels.chipClaro,
                colorOff = pal.texto, padH = 13.dp, radio = 13.dp)
        }
    }
    CampoLargo(b.descripcion, { b.descripcion = it.take(500) }, "Contá un poco más (opcional)")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("¿Cuándo?", 15f, 700, pal.texto)
        Segmentado(listOf("Ahora", "Hoy", "Otro día"), b.cuando, { b.cuando = it }, Modifier.fillMaxWidth(), alto = 46.dp)
        if (b.cuando == 0) {
            T("Arranca en los próximos 30 minutos", 13f, 500, pal.texto2)
        } else {
            if (b.cuando == 2) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..6).forEach { i ->
                    val d = ZonedDateTime.now(ZonaAR).plusDays(i.toLong())
                    Chip(
                        "${Fecha.diaCorto(d)} ${d.dayOfMonth}", b.dia == i, { b.dia = i }, Modifier.weight(1f),
                        gelOff = if (pal.oscuro) Gels.chipOscuro else Gels.off, colorOff = pal.texto, tam = 12f, padH = 4.dp, radio = 12.dp,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                horasCrear.forEach { h -> ChipHora(h, !b.horaOtra && b.hora == h) { b.hora = h; b.horaOtra = false } }
                ChipHora(if (b.horaOtra) String.format(java.util.Locale.ROOT, "%02d:%02d", b.h, b.m) else "Otra", b.horaOtra) { b.horaOtra = true }
            }
            if (b.horaOtra) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stepper(String.format(java.util.Locale.ROOT, "%02d", b.h), { b.h = (b.h + 23) % 24 }, { b.h = (b.h + 1) % 24 })
                T(":", 28f, 800, pal.texto)
                Stepper(String.format(java.util.Locale.ROOT, "%02d", b.m), { b.m = (b.m + 55) % 60 }, { b.m = (b.m + 5) % 60 })
            }
        }
        // Cuánto dura: con las mismas piezas que las horas.
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T("Dura", 14f, 700, pal.texto2, Modifier.padding(end = 2.dp))
            duracionesCrear.forEach { (min, l) -> ChipHora(l, b.duracion == min) { b.duracion = min } }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("¿Dónde?", 15f, 700, pal.texto)
        Row(
            Modifier.fillMaxWidth().tocable(alLugar).background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(18.dp))
                .border(1.5.dp, if (pal.oscuro) bordeSuaveOscuro else bordeSuave, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icono(I.pin, 20.dp, C.violeta, 2f)
            Column(Modifier.weight(1f)) {
                T(b.lugar.ifEmpty { b.direccion.ifEmpty { "Elegí el lugar" } }, 15f, 700, pal.texto, unRenglon = true)
                val sub = listOf(if (b.lugar.isEmpty()) "" else b.direccion, b.zona).filter { it.isNotEmpty() }.joinToString(", ")
                if (sub.isNotEmpty()) T(sub, 12f, 400, pal.texto2, unRenglon = true)
            }
            T("Cambiar", 13f, 700, C.violeta)
        }
    }
    if (!editando) Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T("Se repite cada semana", 15f, 700, pal.texto)
            T("Ideal para el truco de los jueves", 12f, 400, pal.texto2)
        }
        Interruptor(b.repite, { b.repite = it })
    }
}

/** Descripción: el campo del alta en varios renglones. */
@Composable
private fun CampoLargo(texto: String, alCambiar: (String) -> Unit, pista: String) {
    val pal = LocalPaleta.current
    Box(
        Modifier.fillMaxWidth().background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(18.dp))
            .border(1.5.dp, if (pal.oscuro) bordeSuaveOscuro else bordeSuave, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (texto.isEmpty()) T(pista, 15f, 400, C.apagado)
        BasicTextField(
            texto, alCambiar, textStyle = estilo(15f, 500, pal.texto), cursorBrush = SolidColor(C.violeta),
            minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChipHora(texto: String, on: Boolean, accion: () -> Unit) {
    val pal = LocalPaleta.current
    Box(
        Modifier.toque(onClick = accion)
            .then(
                // En oscuro, el carbón del elegido no se distinguía del fondo: va lima, como los filtros de Ahora.
                if (on) Modifier.gel(13.dp, if (pal.oscuro) Gels.limaChico else Gels.oscuroSel)
                else Modifier.background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(13.dp))
                    .border(1.5.dp, if (pal.oscuro) bordeSuaveOscuro else bordeSuave, RoundedCornerShape(13.dp)),
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) { T(texto, 14f, if (on) 700 else 600, if (on) (if (pal.oscuro) C.carbon else C.blancoCalido) else pal.texto, unRenglon = true) }
}

/** Stepper − N + de 06b. */
@Composable
private fun Stepper(valor: String, menos: () -> Unit, mas: () -> Unit, oscuro: Boolean = false, color: Color? = null, tam: Float = 28f) {
    val pal = LocalPaleta.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BotonStep("−", menos, oscuro, "Menos")
        // Dígitos de ancho fijo (monospacedDigit de iOS).
        BasicText(valor, style = estilo(tam, 800, color ?: if (oscuro) C.blancoCalido else pal.texto, 1f).copy(fontFeatureSettings = "tnum"))
        BotonStep("+", mas, oscuro, "Más")
    }
}

@Composable
private fun BotonStep(t: String, onClick: () -> Unit, oscuro: Boolean, etiqueta: String) {
    Box(
        Modifier.size(36.dp).semantics { contentDescription = etiqueta }.toque(onClick = onClick)
            .then(if (oscuro) Modifier.background(Color(0xFF3A3550), RoundedCornerShape(12.dp)) else Modifier.gel(12.dp, Gels.chipClaro)),
        contentAlignment = Alignment.Center,
    ) { T(t, 20f, 700, if (oscuro) C.blancoCalido else C.carbon, alto = 1f) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Paso2(b: Borrador) {
    val pal = LocalPaleta.current
    T("¿Cuántos son?", 32f, 700, pal.texto, alto = 1.05f, espaciado = px(-.8f, 32f))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(1f).background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(22.dp))
                .border(1.5.dp, if (pal.oscuro) bordeSuaveOscuro else bordeSuave, RoundedCornerShape(22.dp)).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T("Somos", 13f, 700, pal.texto2)
            Stepper(
                "${b.somos}", { b.somos = maxOf(b.somosMin, b.somos - 1) },
                { if (!b.editando) b.somos = minOf(60 - b.faltan, b.somos + 1) }, tam = 48f,
            )
        }
        Column(
            Modifier.weight(1f).gel(22.dp, Gels.oscuroSel).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T("Faltan", 13f, 700, C.coral)
            Stepper(
                "${b.faltan}", { b.faltan = maxOf(if (b.editando) 0 else 1, b.faltan - 1) }, { b.faltan = minOf(60 - b.somos, b.faltan + 1) },
                oscuro = true, color = C.coral, tam = 48f,
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().gel(16.dp, Gels.chipClaro).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("Se va a ver así:", 14f, 600, C.texto2)
        Box(Modifier.gel(10.dp, Gels.coralBadge).padding(horizontal = 10.dp, vertical = 5.dp)) { T(Cupo.badge(b.faltan), 15f, 700, C.carbon, alto = 1f) }
        T("· ${if (b.categoria == "juego") "mesa" else "grupo"} de ${b.somos + b.faltan}", 14f, 600, C.carbon)
    }
    Row(Modifier.fillMaxWidth()) {
        T("¿A quién buscás?", 20f, 700, pal.texto, Modifier.alignByBaseline())
        Spacer(Modifier.weight(1f))
        T("Todo opcional", 13f, 400, pal.texto2, Modifier.alignByBaseline())
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            T("Edad", 14f, 700, pal.texto, Modifier.weight(1f))
            val d = b.edadDesde.roundToInt()
            val h = b.edadHasta.roundToInt()
            T(if (d <= EDAD_MIN.toInt() && h >= EDAD_MAX.toInt()) "Cualquier edad" else "$d a $h${if (h >= EDAD_MAX.toInt()) "+" else ""}", 14f, 700, C.violeta)
        }
        RangoEdad(b)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("Quiénes", 14f, 700, pal.texto)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("cualquiera" to "Cualquiera", "mujeres" to "Solo mujeres", "diversidades" to "Solo diversidades").forEach { (k, l) ->
                Chip(l, b.genero == k, { b.genero = k }, gelOff = Gels.chipClaro, padH = 13.dp, radio = 13.dp)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("Nivel", 14f, 700, pal.texto)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            nivelesCrear.forEach { l -> Chip(l, b.nivel == l, { b.nivel = if (b.nivel == l) "" else l }, gelOff = Gels.chipClaro, padH = 13.dp, radio = 13.dp) }
        }
    }
}

/** Rango de edad con dos perillas (06b). */
@Composable
private fun RangoEdad(b: Borrador) {
    val densidad = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp)) {
        val ancho = with(densidad) { this@BoxWithConstraints.maxWidth.toPx() }.coerceAtLeast(1f)
        fun x(v: Double) = ((v - EDAD_MIN) / (EDAD_MAX - EDAD_MIN) * ancho).toFloat()
        fun v(px: Float) = (EDAD_MIN + px / ancho * (EDAD_MAX - EDAD_MIN)).coerceIn(EDAD_MIN, EDAD_MAX)
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(4.dp).background(C.pista, CircleShape))
        Box(
            Modifier.offset(x = with(densidad) { x(b.edadDesde).toDp() }, y = 12.dp)
                .width(with(densidad) { (x(b.edadHasta) - x(b.edadDesde)).coerceAtLeast(0f).toDp() }).height(4.dp).background(C.violeta),
        )
        listOf(true, false).forEach { esDesde ->
            val valor = if (esDesde) b.edadDesde else b.edadHasta
            Box(
                Modifier.offset(x = with(densidad) { (x(valor) - 12.dp.toPx()).toDp() }, y = 2.dp).size(24.dp)
                    .semantics {
                        contentDescription = if (esDesde) "Edad desde" else "Edad hasta"
                        stateDescription = "${valor.toInt()}"
                    }
                    .background(C.blanco, CircleShape).border(3.dp, C.violeta, CircleShape)
                    .pointerInput(esDesde) {
                        detectDragGestures { ch, d ->
                            ch.consume()
                            if (esDesde) b.edadDesde = minOf(v(x(b.edadDesde) + d.x), b.edadHasta - 1)
                            else b.edadHasta = maxOf(v(x(b.edadHasta) + d.x), b.edadDesde + 1)
                        }
                    },
            )
        }
    }
}

@Composable
private fun Paso3(b: Borrador, alCosto: () -> Unit, alFoto: () -> Unit) {
    val pal = LocalPaleta.current
    T("¿Cómo se suman?", 32f, 700, pal.texto, alto = 1.05f, espaciado = px(-.8f, 32f))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("libre", "Libre", "El que llega, entra"),
            Triple("aprobacion", "Con aprobación", "Vos elegís quién entra · recomendado"),
            Triple("mi_gente", "Solo mi gente", "Gente con la que ya compartiste un plan"),
        ).forEach { (k, l, d) ->
            val on = b.ingreso == k
            Row(
                Modifier.fillMaxWidth().semantics { role = Role.Button; selected = on }.tocable { b.ingreso = k }
                    .then(
                        if (on) Modifier.gel(18.dp, Gels.chipClaro).border(2.dp, C.violeta, RoundedCornerShape(18.dp))
                        else Modifier.background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(18.dp))
                            .border(1.5.dp, if (pal.oscuro) bordeSuaveOscuro else bordeSuave, RoundedCornerShape(18.dp)),
                    ).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(22.dp).background(C.blanco, CircleShape).border(if (on) 7.dp else 2.dp, if (on) C.violeta else C.borde6, CircleShape))
                Column(Modifier.weight(1f)) {
                    T(l, 15f, 700, if (on) C.carbon else pal.texto)
                    T(d, 12f, 400, if (on) C.texto2 else pal.texto2)
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("Quién lo ve", 14f, 700, pal.texto)
        Segmentado(listOf("Público", "Mi gente", "Por link"), b.visibilidad, { b.visibilidad = it }, Modifier.fillMaxWidth(), alto = 46.dp)
    }
    Column(
        Modifier.fillMaxWidth().background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(20.dp))
            .border(1.5.dp, if (pal.oscuro) bordeSuaveOscuro else bordeSuave, RoundedCornerShape(20.dp)),
    ) {
        FilaInterruptor("Solo verificados", b.verificados) { b.verificados = it }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.separador))
        FilaInterruptor("Ocultar dirección hasta aceptar", b.ocultar) { b.ocultar = it }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.separador))
        Row(Modifier.fillMaxWidth().tocable(alCosto).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Costo por persona", 14f, 600, pal.texto, Modifier.weight(1f))
            T(b.costo?.let { Plata.pesos(it.toLong()) } ?: "Cada uno lo suyo", 14f, 700, C.violeta)
        }
    }
    FotoPlan(b, alFoto)
}

/** Foto del plan (opcional): la celda punteada de las fotos del perfil, a lo ancho. */
@Composable
private fun FotoPlan(b: Borrador, alFoto: () -> Unit) {
    val pal = LocalPaleta.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            T("Foto del plan", 14f, 700, pal.texto, Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            T("Opcional", 13f, 400, pal.texto2, Modifier.alignByBaseline())
        }
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            if (b.foto.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().semantics { contentDescription = "Sumar foto del plan"; role = Role.Button }
                        .tocable { if (!b.subiendoFoto) alFoto() }
                        .background(pal.superficie, RoundedCornerShape(20.dp))
                        .drawBehind {
                            val g = 2.dp.toPx()
                            drawRoundRect(
                                Color(0xFFB9ACF5), topLeft = Offset(g / 2, g / 2), size = Size(size.width - g, size.height - g),
                                cornerRadius = CornerRadius(20.dp.toPx() - g / 2),
                                style = Stroke(g, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (b.subiendoFoto) Puntitos(C.violeta)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icono(I.mas, 26.dp, C.violeta, 3f)
                        T("Sin foto, usamos una de la categoría", 12f, 600, pal.texto2, alinear = TextAlign.Center)
                    }
                }
            } else {
                Foto(b.foto, Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)))
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).semantics { contentDescription = "Sacar foto" }
                        .background(C.carbon, CircleShape).tocable { b.foto = "" },
                    contentAlignment = Alignment.Center,
                ) { Icono(I.cruz, 14.dp, C.blanco, 3f) }
            }
        }
    }
}

@Composable
private fun FilaInterruptor(t: String, on: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        T(t, 14f, 600, LocalPaleta.current.texto, Modifier.weight(1f))
        Interruptor(on, alCambiar)
    }
}

/** Hoja con un panel (para elegir lugar o costo). */
@Composable
private fun BoxScope.HojaCrear(cerrar: () -> Unit, contenido: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPaleta.current
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val entrada = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(Modifier.matchParentSize().zIndex(30f)) {
        AnimatedVisibility(entrada, Modifier.fillMaxSize(), enter = fadeIn(tween(200))) {
            Box(Modifier.fillMaxSize().background(rgba(32, 29, 43, .45f)).tocable(cerrar))
        }
        AnimatedVisibility(entrada, Modifier.align(Alignment.BottomCenter), enter = slideInVertically(tween(280)) { it }) {
            Column(
                Modifier.fillMaxWidth().tocable { }
                    .background(if (pal.oscuro) C.grafito else C.blancoCalido, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp + abajo),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = contenido,
            )
        }
    }
}

@Composable
private fun BoxScope.EditarCosto(b: Borrador, cerrar: () -> Unit) {
    var texto by remember { mutableStateOf(b.costo?.let { (it / 100).toString() }.orEmpty()) }
    HojaCrear(cerrar) {
        T("Costo por persona", 22f, 700, LocalPaleta.current.texto)
        CampoCrear(texto, { texto = it.filter(Char::isDigit).take(7) }, "Cada uno lo suyo", teclado = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cta("Cada uno lo suyo", { b.costo = null; cerrar() }, Modifier.weight(1f), gel = Gels.blanco, alto = 52.dp, tam = 15f)
            Cta("Listo", {
                val n = texto.toIntOrNull()
                b.costo = if (n != null && n > 0) n * 100 else null
                cerrar()
            }, Modifier.weight(1f), alto = 52.dp, tam = 15f)
        }
    }
}

/** Un resultado del Geocoder (el MKMapItem de iOS): nombre, dirección y lo que completa el borrador. */
private data class Sitio(val nombre: String, val direccion: String, val zona: String, val ciudad: String, val lat: Double, val lng: Double)

private fun sitioDe(a: android.location.Address): Sitio {
    // "Güemes 2950" (planesDireccion de iOS).
    val calle = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ")
    val nombre = a.featureName?.takeIf { it != a.subThoroughfare && it != a.thoroughfare && it != calle } ?: calle.ifEmpty { a.getAddressLine(0).orEmpty() }
    return Sitio(nombre, calle, a.subLocality ?: a.locality.orEmpty(), a.locality.orEmpty(), a.latitude, a.longitude)
}

/** Busca alrededor del centro (la región de 30 km de MKLocalSearch). */
@Suppress("DEPRECATION")
private suspend fun buscarSitios(ctx: android.content.Context, q: String, la: Double, ln: Double): List<Sitio> = withContext(Dispatchers.IO) {
    if (!android.location.Geocoder.isPresent()) return@withContext emptyList()
    val g = android.location.Geocoder(ctx, java.util.Locale.forLanguageTag("es-AR"))
    runCatching { g.getFromLocationName(q, 6, la - .135, ln - .17, la + .135, ln + .17).orEmpty() }.getOrDefault(emptyList()).map(::sitioDe)
}

@Suppress("DEPRECATION")
internal suspend fun sitioEn(ctx: android.content.Context, lat: Double, lng: Double): android.location.Address? = withContext(Dispatchers.IO) {
    if (!android.location.Geocoder.isPresent()) return@withContext null
    runCatching { android.location.Geocoder(ctx, java.util.Locale.forLanguageTag("es-AR")).getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
}

/** Elegir el lugar: buscador (Geocoder), mapa (MapLibre) con el pin al centro, nombre, dirección y zona. */
@Composable
private fun BoxScope.ElegirLugar(b: Borrador, alcance: CoroutineScope, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPaleta.current
    val anchoMapa = LocalConfiguration.current.screenWidthDp.toFloat() - 40f
    var lugar by remember { mutableStateOf(b.lugar) }
    var direccion by remember { mutableStateOf(b.direccion) }
    var zona by remember { mutableStateOf(b.zona) }
    var ciudad by remember { mutableStateOf("") }
    var buscar by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<Sitio>>(emptyList()) }
    var centro by remember { mutableStateOf(b.lat to b.lng) }
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    val oscuro = pal.oscuro
    val vista = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply {
            onCreate(Bundle())
            getMapAsync { m ->
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isRotateGesturesEnabled = false
                m.setStyle(if (oscuro) BuildConfig.MAPA_ESTILO_OSCURO else BuildConfig.MAPA_ESTILO) {
                    m.cameraPosition = CameraPosition.Builder().target(LatLng(b.lat, b.lng)).zoom(zoomPara(b.lat, 900.0, anchoMapa)).build()
                    mapa = m
                }
                m.addOnCameraIdleListener { m.cameraPosition.target?.let { centro = it.latitude to it.longitude } }
            }
        }
    }
    DisposableEffect(Unit) {
        vista.onStart(); vista.onResume()
        onDispose { vista.onPause(); vista.onStop(); vista.onDestroy() }
    }
    LaunchedEffect(buscar) {
        val q = buscar.trim()
        if (q.length < 3) { resultados = emptyList(); return@LaunchedEffect }
        delay(350)
        resultados = buscarSitios(ctx, q, centro.first, centro.second)
    }

    fun elegir(r: Sitio) {
        lugar = r.nombre.take(80)
        direccion = r.direccion.take(160)
        zona = r.zona.take(60)
        ciudad = r.ciudad
        centro = r.lat to r.lng
        mapa?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(r.lat, r.lng), zoomPara(r.lat, 600.0, anchoMapa)))
        buscar = ""
        resultados = emptyList()
    }

    HojaCrear(cerrar) {
        T("¿Dónde?", 22f, 700, pal.texto)
        Row(
            Modifier.fillMaxWidth().height(48.dp).background(if (pal.oscuro) Color(0xFF2E2A3D) else Color(0xFFF2F0EC), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icono(I.lupaChica, 18.dp, C.violeta, 2.2f)
            PlanesCampo(buscar, { buscar = it }, "Buscá un bar, una cancha o una dirección", Modifier.weight(1f))
        }
        if (resultados.isNotEmpty()) Column(Modifier.fillMaxWidth().gel(18.dp, Gels.neutra)) {
            resultados.take(4).forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.lavanda))
                Column(Modifier.fillMaxWidth().tocable { elegir(r) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    T(r.nombre, 14f, 700, C.carbon, unRenglon = true)
                    T(r.direccion, 12f, 400, C.texto2, unRenglon = true)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFF2EFE9))) {
            AndroidView({ vista }, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).offset(y = (-14).dp)) { Icono(I.pin, 34.dp, C.violeta, 2.4f, relleno = C.blanco) }
        }
        CampoCrear(lugar, { lugar = it.take(80) }, "Nombre del lugar", tam = 16f, alto = 52.dp)
        CampoCrear(direccion, { direccion = it.take(160) }, "Dirección", tam = 16f, alto = 52.dp)
        CampoCrear(zona, { zona = it.take(60) }, "Barrio o zona", tam = 16f, alto = 52.dp)
        Cta("Listo", {
            val t = mapa?.cameraPosition?.target
            val (la, ln) = if (t != null) t.latitude to t.longitude else centro
            b.lat = la
            b.lng = ln
            b.lugar = lugar.trim()
            b.direccion = direccion.trim()
            b.zona = zona.trim()
            if (ciudad.isNotEmpty()) b.ciudadElegida = ciudad
            val sinZona = b.zona.isEmpty()
            cerrar()
            // Moviendo el mapa a mano no hay zona: la saca la geocodificación inversa.
            alcance.launch {
                val (z, ci) = crearZonaDe(ctx, la, ln) ?: return@launch
                if (sinZona && b.zona.isEmpty()) b.zona = z.take(60)
                if (ci.isNotEmpty()) b.ciudadElegida = ci
            }
        }, Modifier.fillMaxWidth(), alto = 56.dp)
    }
}

/** Publicado (prototipo): fondo violeta, isotipo con tk-pop, "Plan publicado" y compartir el link. */
@Composable
fun Publicado(ir: Ir, id: String) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    var plan by remember { mutableStateOf<Plan?>(null) }
    LaunchedEffect(id) { plan = runCatching { c.api.plan(id) }.getOrNull() }
    // tk-pop: escala .4 → 1.08 → 1 con rotación −20° → 6° → 0°.
    val escala = remember { Animatable(.4f) }
    val giro = remember { Animatable(-20f) }
    LaunchedEffect(Unit) {
        launch { escala.animateTo(1.08f, tween(270, easing = EaseOut)); escala.animateTo(1f, tween(180, easing = EaseInOut)) }
        giro.animateTo(6f, tween(270, easing = EaseOut)); giro.animateTo(0f, tween(180, easing = EaseInOut))
    }
    val fondo = radial(.5f, .3f, 1f, .7f, 0f to Color(0xFF8A70FF), 1f to C.violeta4)
    Pantalla(fondo = Modifier.fondoCaigo(false, fondo)) { _, abajo ->
        Column(
            Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, bottom = abajo),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(200.dp).scale(escala.value).rotate(giro.value))
            T("Plan publicado", 36f, 800, C.blanco, alto = 1f, espaciado = px(-1f, 36f), alinear = TextAlign.Center)
            val p = plan
            T("«${p?.titulo.orEmpty()}» ya aparece en Ahora. Te avisamos cuando alguien se prenda.", 15f, 500, rgba(255, 255, 255, .9f), alto = 1.5f, alinear = TextAlign.Center)
            Box(Modifier.height(4.dp))
            if (p != null) {
                val link = "https://caigo.com.ar/p/${p.slug}"
                Cta("Compartir link por WhatsApp", { Sistema.compartirWhatsApp(ctx, "${p.titulo} · ${Cupo.badge(p.faltan)}. Sumate en Caigo: $link") }, Modifier.fillMaxWidth(), alto = 58.dp, tam = 16f)
                T("Copiar link", 14f, 800, C.blanco, modifier = Modifier.tocable { Sistema.copiar(ctx, link); avisos.ok("Link copiado: ${linkPlan(p.slug)}") })
            } else {
                Puntitos(C.blanco)
            }
            T("Ver mis planes", 14f, 800, C.blanco, modifier = Modifier.tocable { ir.principal(Pestana.Mis) })
        }
    }
}
