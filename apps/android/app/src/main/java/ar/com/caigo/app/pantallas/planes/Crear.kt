package ar.com.caigo.app.pantallas.planes

import android.os.Bundle
import org.maplibre.android.camera.CameraUpdateFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import ar.com.caigo.app.nucleo.CambiosPlan
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.pantallas.mios.MiosCampo
import ar.com.caigo.app.piezas.jpegDe
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.nombreCategoria
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.app.LAT_MDP
import ar.com.caigo.app.app.LNG_MDP
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.PlanNuevo
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.nucleo.Plata
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.Pestana
import ar.com.caigo.app.piezas.BarraPasos
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.piezas.Chip
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Fecha
import ar.com.caigo.app.piezas.Interruptor
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.Segmentado
import ar.com.caigo.app.piezas.ZonaAR
import ar.com.caigo.app.piezas.compartirWhatsApp
import ar.com.caigo.app.piezas.copiar
import ar.com.caigo.app.piezas.fondoCaigo
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.estilo
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** Categorías para crear un plan (06a). */
private val categoriasCrear = listOf(
    "deporte" to "Deporte", "juego" to "Juego", "previa" to "Previa", "salida" to "Salida",
    "cita_doble" to "Cita doble", "cultura" to "Cultura", "gastronomia" to "Gastronomía", "otro" to "Otro",
)
private val horasCrear = listOf("20:30", "21:30", "22:30")
private val nivelesCrear = listOf("Recién aprendo", "Me defiendo", "Canto falta envido")
private const val EDAD_MIN = 18f
private const val EDAD_MAX = 60f

/** Campo de texto del alta (58 px, radio 18, borde violeta). */
@Composable
internal fun CampoCrear(valor: String, alCambiar: (String) -> Unit, pista: String, modifier: Modifier = Modifier, tam: Float = 18f, alto: Dp = 58.dp, teclado: KeyboardType = KeyboardType.Text) {
    val pal = LocalPaleta.current
    Box(
        modifier.fillMaxWidth().height(alto).background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(18.dp))
            .border(2.dp, C.violeta, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (valor.isEmpty()) T(pista, tam, 600, C.apagado, unRenglon = true)
        BasicTextField(
            valor, alCambiar, singleLine = true, textStyle = estilo(tam, 600, pal.texto), cursorBrush = SolidColor(C.violeta),
            keyboardOptions = KeyboardOptions(keyboardType = teclado), modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Borrador del plan con los valores por defecto del README: Hoy 21:30, Somos 3 / Falta 1, con aprobación, verificados y dirección oculta. */
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
    var lat by mutableStateOf(LAT_MDP)
    var lng by mutableStateOf(LNG_MDP)
    var somos by mutableIntStateOf(3)
    var faltan by mutableIntStateOf(1)
    var edadMin by mutableFloatStateOf(EDAD_MIN)
    var edadMax by mutableFloatStateOf(EDAD_MAX)
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
    /** La ciudad del lugar elegido (Geocoder); vacía, sale de la del perfil. */
    var ciudad by mutableStateOf("")

    /** Carga un plan para editarlo (contrato §2). */
    fun desde(p: Plan) {
        titulo = p.titulo; categoria = p.categoria; descripcion = p.descripcion; duracion = p.duracionMin; foto = p.foto
        repite = p.recurrente; lugar = p.lugarNombre; direccion = p.direccion?.texto.orEmpty(); zona = p.zona; ciudad = p.ciudad
        lat = p.direccion?.lat?.takeIf { it != 0.0 } ?: p.lat; lng = p.direccion?.lng?.takeIf { it != 0.0 } ?: p.lng
        somos = p.somos.coerceAtLeast(1); faltan = p.faltan.coerceAtLeast(1)
        edadMin = (p.edadMin ?: 18).toFloat().coerceIn(EDAD_MIN, EDAD_MAX); edadMax = (p.edadMax ?: 99).toFloat().coerceIn(EDAD_MIN, EDAD_MAX)
        genero = p.genero; nivel = p.nivel; ingreso = p.ingreso
        visibilidad = listOf("publico", "mi_gente", "link").indexOf(p.visibilidad).coerceAtLeast(0)
        verificados = p.soloVerificados; ocultar = p.ocultarDireccion; costo = p.costoCentavos
        Fecha.parse(p.empiezaEn)?.let { t ->
            val dias = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(ZonaAR), t.toLocalDate()).toInt()
            cuando = if (dias <= 0) 1 else 2
            dia = dias.coerceIn(1, 6)
            horaOtra = true; h = t.hour; m = t.minute - t.minute % 5
        }
    }

    fun empieza(): ZonedDateTime {
        val ahora = ZonedDateTime.now(ZonaAR)
        if (cuando == 0) return ahora.plusMinutes(15)
        val (hh, mm) = if (horaOtra) h to m else hora.split(":").let { it[0].toInt() to it[1].toInt() }
        val d = if (cuando == 1) LocalDate.now(ZonaAR) else LocalDate.now(ZonaAR).plusDays(dia.toLong())
        var t = d.atTime(hh, mm).atZone(ZonaAR)
        if (cuando == 1 && t.isBefore(ahora)) t = t.plusDays(1)
        return t
    }

    fun ciudad(porDefecto: String) = ciudad.ifBlank { porDefecto.ifBlank { "Mar del Plata" } }
}

/** Duraciones del plan (minutos). */
private val duracionesCrear = listOf(60 to "1 h", 120 to "2 h", 180 to "3 h", 240 to "4 h")

/** 06a–06c · Crear plan en tres pasos. Con [editar], los mismos pasos cambian un plan propio (PATCH). */
@Composable
fun Crear(ir: Ir, editar: String? = null) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val pal = LocalPaleta.current
    val alcance = rememberCoroutineScope()
    val yo by c.yo.collectAsState()
    val b = remember { Borrador() }
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var eligiendoLugar by remember { mutableStateOf(false) }
    var editandoCosto by remember { mutableStateOf(false) }
    var publicando by remember { mutableStateOf(false) }
    var cargado by remember { mutableStateOf(editar == null) }
    LaunchedEffect(Unit) {
        if (editar != null) {
            try {
                b.desde(c.api.plan(editar))
                cargado = true
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e)); ir.atras()
            }
            return@LaunchedEffect
        }
        if (b.zona.isBlank()) b.zona = yo?.zona.orEmpty()
        c.ubicacion()?.let { (la, ln) -> b.lat = la; b.lng = ln }
    }

    fun publicar() = alcance.launch {
        if (b.lugar.isBlank() && b.direccion.isBlank()) { avisos.error("Marcá dónde es el plan."); eligiendoLugar = true; return@launch }
        publicando = true
        try {
            if (editar != null) {
                c.api.editarPlan(
                    editar,
                    CambiosPlan(
                        titulo = b.titulo.trim(), descripcion = b.descripcion.trim(), empiezaEn = Fecha.iso(b.empieza()), duracionMin = b.duracion,
                        total = b.somos + b.faltan, ingreso = b.ingreso, visibilidad = listOf("publico", "mi_gente", "link")[b.visibilidad],
                        soloVerificados = b.verificados, ocultarDireccion = b.ocultar,
                        // «Cualquier edad» se manda como el rango entero (null sería «no cambia»).
                        edadMin = b.edadMin.roundToInt(), edadMax = b.edadMax.roundToInt().let { if (it >= EDAD_MAX) 120 else it },
                        genero = b.genero, nivel = b.nivel, costoCentavos = b.costo ?: 0, foto = b.foto,
                        zona = b.zona.trim(), lugarNombre = b.lugar.trim(), direccion = b.direccion.trim(), lat = b.lat, lng = b.lng,
                    ),
                )
                avisos.ok("Listo, guardamos los cambios. Si cambió el día o el lugar, le avisamos a la gente.")
                ir.atras()
                return@launch
            }
            val p = c.api.crearPlan(
                PlanNuevo(
                    titulo = b.titulo.trim(), categoria = b.categoria, descripcion = b.descripcion.trim(),
                    empiezaEn = Fecha.iso(b.empieza()), duracionMin = b.duracion, recurrente = b.repite,
                    ciudad = b.ciudad(yo?.ciudad.orEmpty()), zona = b.zona.trim(), lugarNombre = b.lugar.trim(), direccion = b.direccion.trim(),
                    lat = b.lat, lng = b.lng, somos = b.somos, faltan = b.faltan, ingreso = b.ingreso,
                    visibilidad = listOf("publico", "mi_gente", "link")[b.visibilidad],
                    soloVerificados = b.verificados, ocultarDireccion = b.ocultar,
                    edadMin = b.edadMin.roundToInt().takeIf { it > EDAD_MIN }, edadMax = b.edadMax.roundToInt().takeIf { it < EDAD_MAX },
                    genero = b.genero, nivel = b.nivel, costoCentavos = b.costo, foto = b.foto,
                ),
            )
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

    Pantalla { arriba, abajo ->
        Column(Modifier.padding(top = arriba + 8.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BotonIcono(if (paso == 0) I.cruz else I.atras, { if (paso == 0) ir.atras() else paso-- })
                Box(Modifier.weight(1f))
                T("${paso + 1} de 3", 14f, 700, pal.texto2)
            }
            BarraPasos(paso + 1, 3)
        }
        Column(
            Modifier.fillMaxSize().padding(top = arriba + 100.dp).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 130.dp + abajo),
            verticalArrangement = Arrangement.spacedBy(if (paso == 2) 16.dp else 20.dp),
        ) {
            if (!cargado) Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { Puntitos() }
            else when (paso) {
                0 -> Paso1(b, editar != null, { eligiendoLugar = true })
                1 -> Paso2(b)
                else -> Paso3(b) { editandoCosto = true }
            }
        }
        // Velo del color del fondo detrás del botón, como la barra de abajo del prototipo.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp + abajo)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to pal.fondo.copy(alpha = 0f), .35f to pal.fondo)),
        )
        Cta(
            if (paso < 2) "Siguiente" else if (editar != null) "Guardar cambios" else "Publicar plan",
            { if (paso < 2) paso++ else publicar() },
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 34.dp + abajo),
            alto = 58.dp, icono = if (paso == 2 && editar == null) I.mas else null, iconoTrazo = 4f, iconoTam = 18.dp, cargando = publicando,
        )
        if (eligiendoLugar) ElegirLugar(b) { eligiendoLugar = false }
        if (editandoCosto) EditarCosto(b) { editandoCosto = false }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Paso1(b: Borrador, editando: Boolean, onLugar: () -> Unit) {
    val pal = LocalPaleta.current
    T(if (editando) "Editar el plan" else "¿Qué hacemos?", 32f, 700, pal.texto, alto = 1.05f, espaciado = px(-.8f, 32f))
    CampoCrear(b.titulo, { b.titulo = it.take(80) }, "${categoriasCrear.firstOrNull { it.first == b.categoria }?.second ?: nombreCategoria(b.categoria)} en ${b.zona.ifBlank { "Güemes" }}")
    if (!editando) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categoriasCrear.forEach { (k, l) ->
            Chip(l, b.categoria == k, { b.categoria = k }, gelOn = Gels.violeta.copy(sombras = Gels.violeta.sombras.map { if (!it.inset && it.blur == 0f) it.copy(y = 4f) else if (!it.inset) it.copy(y = 16f, blur = 26f) else it }),
                gelOff = Gels.chipClaro, padH = 13.dp, radio = 13.dp)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("¿Cuándo?", 15f, 700, pal.texto)
        Segmentado(listOf("Ahora", "Hoy", "Otro día"), b.cuando, { b.cuando = it }, alto = 46.dp)
        when (b.cuando) {
            0 -> T("Arranca en los próximos 30 minutos", 13f, 500, pal.texto2)
            else -> {
                if (b.cuando == 2) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..6).forEach { i ->
                        val d = LocalDate.now(ZonaAR).plusDays(i.toLong())
                        Chip("${Fecha.diaCorto(d.atStartOfDay(ZonaAR))} ${d.dayOfMonth}", b.dia == i, { b.dia = i }, Modifier.weight(1f), tam = 12f, padH = 4.dp, radio = 12.dp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    horasCrear.forEach { h -> ChipHora(h, !b.horaOtra && b.hora == h) { b.hora = h; b.horaOtra = false } }
                    ChipHora(if (b.horaOtra) "%02d:%02d".format(b.h, b.m) else "Otra", b.horaOtra) { b.horaOtra = true }
                }
                if (b.horaOtra) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stepper("%02d".format(b.h), { b.h = (b.h + 23) % 24 }, { b.h = (b.h + 1) % 24 })
                    T(":", 28f, 800, pal.texto)
                    Stepper("%02d".format(b.m), { b.m = (b.m + 55) % 60 }, { b.m = (b.m + 5) % 60 })
                }
            }
        }
        // Duración: los chips de hora de 06a.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T("Dura", 14f, 600, pal.texto2, modifier = Modifier.padding(end = 2.dp))
            duracionesCrear.forEach { (min, l) -> ChipHora(l, b.duracion == min) { b.duracion = min } }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("¿Dónde?", 15f, 700, pal.texto)
        Row(
            Modifier.fillMaxWidth().tocable(onLugar).background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(18.dp))
                .border(1.5.dp, if (pal.oscuro) Color(0xFF3A3550) else Color(0xFFECE8F2), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icono(I.pin, 20.dp, C.violeta, 2f)
            Column(Modifier.weight(1f)) {
                T(b.lugar.ifBlank { if (b.direccion.isBlank()) "Elegí el lugar" else b.direccion }, 15f, 700, pal.texto, unRenglon = true)
                val sub = listOf(b.direccion.takeIf { b.lugar.isNotBlank() }.orEmpty(), b.zona).filter { it.isNotBlank() }.joinToString(", ")
                if (sub.isNotBlank()) T(sub, 12f, 400, pal.texto2, unRenglon = true)
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

@Composable
private fun ChipHora(t: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPaleta.current
    Box(
        Modifier.toque(onClick = onClick)
            .then(if (on) Modifier.gel(13.dp, Gels.oscuroSel) else Modifier.background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(13.dp)).border(1.5.dp, if (pal.oscuro) Color(0xFF3A3550) else Color(0xFFECE8F2), RoundedCornerShape(13.dp)))
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) { T(t, 14f, if (on) 700 else 600, if (on) C.blancoCalido else pal.texto, unRenglon = true) }
}

/** Stepper − N + de 06b. */
@Composable
private fun Stepper(valor: String, menos: () -> Unit, mas: () -> Unit, oscuro: Boolean = false, color: Color? = null, tam: Float = 28f) {
    val pal = LocalPaleta.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BotonStep("−", menos, oscuro)
        T(valor, tam, 800, color ?: if (oscuro) C.blancoCalido else pal.texto, alto = 1f)
        BotonStep("+", mas, oscuro)
    }
}

@Composable
private fun BotonStep(t: String, onClick: () -> Unit, oscuro: Boolean) {
    Box(
        Modifier.size(36.dp).toque(onClick = onClick)
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
                .border(1.5.dp, if (pal.oscuro) Color(0xFF3A3550) else Color(0xFFECE8F2), RoundedCornerShape(22.dp)).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T("Somos", 13f, 700, pal.texto2)
            Stepper("${b.somos}", { b.somos = (b.somos - 1).coerceAtLeast(1) }, { b.somos = (b.somos + 1).coerceAtMost(59 - b.faltan + 1) }, tam = 48f)
        }
        Column(
            Modifier.weight(1f).gel(22.dp, Gels.oscuroSel).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T("Faltan", 13f, 700, C.coral)
            Stepper("${b.faltan}", { b.faltan = (b.faltan - 1).coerceAtLeast(1) }, { b.faltan = (b.faltan + 1).coerceAtMost(60 - b.somos) }, oscuro = true, color = C.coral, tam = 48f)
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        T("¿A quién buscás?", 20f, 700, pal.texto, modifier = Modifier.weight(1f))
        T("Todo opcional", 13f, 400, pal.texto2)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            T("Edad", 14f, 700, pal.texto, modifier = Modifier.weight(1f))
            val todo = b.edadMin.roundToInt() <= EDAD_MIN && b.edadMax.roundToInt() >= EDAD_MAX
            T(if (todo) "Cualquier edad" else "${b.edadMin.roundToInt()} a ${b.edadMax.roundToInt()}${if (b.edadMax.roundToInt() >= EDAD_MAX) "+" else ""}", 14f, 700, C.violeta)
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
        val ancho = with(densidad) { maxWidth.toPx() }
        fun x(v: Float) = (v - EDAD_MIN) / (EDAD_MAX - EDAD_MIN) * ancho
        fun v(px: Float) = (EDAD_MIN + px / ancho * (EDAD_MAX - EDAD_MIN)).coerceIn(EDAD_MIN, EDAD_MAX)
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(4.dp).background(C.pista, RoundedCornerShape(2.dp)))
        Box(
            Modifier.offset(x = with(densidad) { x(b.edadMin).toDp() }, y = 12.dp)
                .width(with(densidad) { (x(b.edadMax) - x(b.edadMin)).coerceAtLeast(0f).toDp() }).height(4.dp).background(C.violeta),
        )
        listOf(true, false).forEach { esMin ->
            val valor = if (esMin) b.edadMin else b.edadMax
            Box(
                Modifier.offset(x = with(densidad) { (x(valor) - 12.dp.toPx()).toDp() }, y = 2.dp).size(24.dp)
                    .background(C.blanco, CircleShape).border(3.dp, C.violeta, CircleShape)
                    .pointerInput(esMin) {
                        detectDragGestures { ch, d ->
                            ch.consume()
                            if (esMin) b.edadMin = v(x(b.edadMin) + d.x).coerceAtMost(b.edadMax - 1)
                            else b.edadMax = v(x(b.edadMax) + d.x).coerceAtLeast(b.edadMin + 1)
                        }
                    },
            )
        }
    }
}

@Composable
private fun Paso3(b: Borrador, onCosto: () -> Unit) {
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
                Modifier.fillMaxWidth().tocable { b.ingreso = k }
                    .then(
                        if (on) Modifier.gel(18.dp, Gels.chipClaro).border(2.dp, C.violeta, RoundedCornerShape(18.dp))
                        else Modifier.background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(18.dp)).border(1.5.dp, if (pal.oscuro) Color(0xFF3A3550) else Color(0xFFECE8F2), RoundedCornerShape(18.dp)),
                    ).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(22.dp).background(C.blanco, CircleShape).border(if (on) 7.dp else 2.dp, if (on) C.violeta else C.borde6, CircleShape))
                Column {
                    T(l, 15f, 700, if (on) C.carbon else pal.texto)
                    T(d, 12f, 400, if (on) C.texto2 else pal.texto2)
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("Quién lo ve", 14f, 700, pal.texto)
        Segmentado(listOf("Público", "Mi gente", "Por link"), b.visibilidad, { b.visibilidad = it }, alto = 46.dp)
    }
    Column(
        Modifier.fillMaxWidth().background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(20.dp))
            .border(1.5.dp, if (pal.oscuro) Color(0xFF3A3550) else Color(0xFFECE8F2), RoundedCornerShape(20.dp)),
    ) {
        FilaInterruptor("Solo verificados", b.verificados) { b.verificados = it }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.separador))
        FilaInterruptor("Ocultar dirección hasta aceptar", b.ocultar) { b.ocultar = it }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.separador))
        Row(Modifier.fillMaxWidth().tocable(onCosto).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Costo por persona", 14f, 600, pal.texto, modifier = Modifier.weight(1f))
            T(b.costo?.takeIf { it > 0 }?.let { Plata.pesos(it.toLong()) } ?: "Cada uno lo suyo", 14f, 700, C.violeta)
        }
    }
    FotoYDescripcion(b)
}

/**
 * Foto y descripción del plan (opcionales). No están en 06a–06c: van con el campo de 06a y la
 * tarjeta de filas de 06c, como «Foto de portada» y «Descripción» del panel de lugares.
 */
@Composable
private fun FotoYDescripcion(b: Borrador) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val pal = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var subiendo by remember { mutableStateOf(false) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        subiendo = true
        alcance.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    val bm = ctx.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
                    jpegDe(bm, 0, 86, 1600)
                }
                b.foto = c.api.subir("foto", jpeg).url
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            } finally {
                subiendo = false
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            T("Contalo un poco", 14f, 700, pal.texto, modifier = Modifier.weight(1f))
            T("Opcional", 13f, 400, pal.texto2)
        }
        MiosCampo(b.descripcion, { b.descripcion = it }, placeholder = "Qué llevar, cómo nos encontramos, el nivel…", renglones = 3, maximo = 600)
        Row(
            Modifier.fillMaxWidth().tocable { if (!subiendo) elegir.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                .background(if (pal.oscuro) C.oscuro3 else C.blanco, RoundedCornerShape(20.dp))
                .border(1.5.dp, if (pal.oscuro) Color(0xFF3A3550) else Color(0xFFECE8F2), RoundedCornerShape(20.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(colorDeCategoria(b.categoria)), contentAlignment = Alignment.Center) {
                if (b.foto.isNotBlank()) Foto(b.foto, Modifier.fillMaxSize(), colorDeCategoria(b.categoria))
                else if (!subiendo) Icono(I.mas, 22.dp, C.blanco, 3f)
                if (subiendo) Puntitos(C.blanco, 6.dp)
            }
            Column(Modifier.weight(1f)) {
                T("Foto del plan", 14f, 600, pal.texto)
                T(if (b.foto.isBlank()) "Sale arriba de todo en la tarjeta" else "Tocá para cambiarla", 12f, 400, pal.texto2)
            }
            T(if (b.foto.isBlank()) "Sumar" else "Cambiar", 13f, 700, C.violeta, modifier = Modifier.padding(end = 6.dp))
        }
    }
}

@Composable
private fun FilaInterruptor(t: String, on: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        T(t, 14f, 600, LocalPaleta.current.texto, modifier = Modifier.weight(1f))
        Interruptor(on, alCambiar)
    }
}

/** Hoja con un panel (para elegir lugar o costo). */
@Composable
private fun Hoja(cerrar: () -> Unit, contenido: @Composable () -> Unit) {
    val pal = LocalPaleta.current
    Box(Modifier.fillMaxSize().zIndex(30f).background(rgba(32, 29, 43, .45f)).tocable(cerrar)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().tocable { }
                .background(if (pal.oscuro) C.grafito else C.blancoCalido, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { contenido() }
    }
}

@Composable
private fun EditarCosto(b: Borrador, cerrar: () -> Unit) {
    var texto by remember { mutableStateOf(b.costo?.let { (it / 100).toString() }.orEmpty()) }
    Hoja(cerrar) {
        T("Costo por persona", 22f, 700, LocalPaleta.current.texto)
        CampoCrear(texto, { texto = it.filter(Char::isDigit).take(7) }, "Cada uno lo suyo", teclado = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cta("Cada uno lo suyo", { b.costo = null; cerrar() }, Modifier.weight(1f), gel = Gels.blanco, alto = 52.dp, tam = 15f)
            Cta("Listo", { b.costo = texto.toIntOrNull()?.takeIf { it > 0 }?.times(100); cerrar() }, Modifier.weight(1f), alto = 52.dp, tam = 15f)
        }
    }
}

/** Un resultado del Geocoder: lo que se muestra y lo que completa el borrador. */
private data class Sitio(val titulo: String, val sub: String, val direccion: String, val zona: String, val ciudad: String, val lat: Double, val lng: Double)

private fun sitioDe(a: android.location.Address): Sitio {
    val calle = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ")
    val nombre = a.featureName?.takeIf { it != a.subThoroughfare && it != a.thoroughfare && it != calle }
    val zona = a.subLocality.orEmpty()
    val ciudad = a.locality ?: a.subAdminArea.orEmpty()
    return Sitio(
        nombre ?: calle.ifBlank { a.getAddressLine(0).orEmpty() },
        listOf(if (nombre != null) calle else "", zona, ciudad).filter { it.isNotBlank() }.joinToString(", "),
        calle, zona, ciudad, a.latitude, a.longitude,
    )
}

/** Las dos ciudades de Caigo, para buscar cerca y no en cualquier lado (sur de la costa atlántica). */
private const val SUR = -38.3
private const val OESTE = -57.8
private const val NORTE = -37.1
private const val ESTE = -56.8

@Suppress("DEPRECATION")
private suspend fun buscarSitios(ctx: android.content.Context, q: String): List<Sitio> = withContext(Dispatchers.IO) {
    if (!android.location.Geocoder.isPresent()) return@withContext emptyList()
    val g = android.location.Geocoder(ctx, java.util.Locale.forLanguageTag("es-AR"))
    runCatching { g.getFromLocationName(q, 6, SUR, OESTE, NORTE, ESTE).orEmpty() }.getOrDefault(emptyList())
        .ifEmpty { runCatching { g.getFromLocationName("$q, Buenos Aires, Argentina", 6).orEmpty() }.getOrDefault(emptyList()) }
        .map(::sitioDe)
}

@Suppress("DEPRECATION")
internal suspend fun sitioEn(ctx: android.content.Context, lat: Double, lng: Double): android.location.Address? = withContext(Dispatchers.IO) {
    if (!android.location.Geocoder.isPresent()) return@withContext null
    runCatching { android.location.Geocoder(ctx, java.util.Locale.forLanguageTag("es-AR")).getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
}

/**
 * Elegir el lugar (06a «Cambiar»): buscador con el Geocoder de Android, mapa con el pin al centro,
 * nombre y dirección. Mover el mapa completa dirección, zona y ciudad con geocodificación inversa.
 */
@Composable
private fun ElegirLugar(b: Borrador, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var lugar by remember { mutableStateOf(b.lugar) }
    var direccion by remember { mutableStateOf(b.direccion) }
    var zona by remember { mutableStateOf(b.zona) }
    var ciudad by remember { mutableStateOf(b.ciudad) }
    var busqueda by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<Sitio>?>(null) }
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    // Al elegir un resultado el mapa se mueve solo: esa vuelta no pisa lo elegido.
    var moviendoYo by remember { mutableStateOf(false) }
    val oscuro = LocalPaleta.current.oscuro
    val vista = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply {
            onCreate(Bundle())
            getMapAsync { m ->
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isRotateGesturesEnabled = false
                m.setStyle(if (oscuro) BuildConfig.MAPA_ESTILO_OSCURO else BuildConfig.MAPA_ESTILO) {
                    m.cameraPosition = CameraPosition.Builder().target(LatLng(b.lat, b.lng)).zoom(15.0).build()
                    mapa = m
                }
                m.addOnCameraIdleListener {
                    if (moviendoYo) { moviendoYo = false; return@addOnCameraIdleListener }
                    val t = m.cameraPosition.target ?: return@addOnCameraIdleListener
                    alcance.launch {
                        val a = sitioEn(ctx, t.latitude, t.longitude) ?: return@launch
                        val st = sitioDe(a)
                        if (st.direccion.isNotBlank()) direccion = st.direccion
                        if (st.zona.isNotBlank()) zona = st.zona
                        if (st.ciudad.isNotBlank()) ciudad = st.ciudad
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        vista.onStart(); vista.onResume()
        onDispose { vista.onPause(); vista.onStop(); vista.onDestroy() }
    }
    // Busca al dejar de tipear (400 ms).
    LaunchedEffect(busqueda) {
        val q = busqueda.trim()
        if (q.length < 3) { resultados = null; return@LaunchedEffect }
        delay(400)
        resultados = buscarSitios(ctx, q)
    }
    Hoja(cerrar) {
        T("¿Dónde?", 22f, 700, LocalPaleta.current.texto)
        CampoCrear(busqueda, { busqueda = it.take(80) }, "Buscá una dirección o un lugar", tam = 16f, alto = 52.dp)
        val r = resultados
        if (r != null) {
            // Los resultados, en la tarjeta de filas de las hojas de opciones.
            Column(Modifier.fillMaxWidth().height(220.dp).gel(22.dp, if (LocalPaleta.current.oscuro) Gels.tarjetaOscura else Gels.neutra)) {
                if (r.isEmpty()) Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    T("No encontramos ese lugar. Probá con la calle y la altura.", 14f, 500, LocalPaleta.current.texto2, alinear = TextAlign.Center)
                } else Column(Modifier.verticalScroll(rememberScrollState())) {
                    r.forEachIndexed { i, st ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPaleta.current.separador))
                        Row(
                            Modifier.fillMaxWidth().tocable {
                                if (st.titulo != st.direccion) lugar = st.titulo
                                direccion = st.direccion; zona = st.zona; ciudad = st.ciudad
                                moviendoYo = true
                                mapa?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(st.lat, st.lng), 16.0))
                                busqueda = ""; resultados = null
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icono(I.pin, 20.dp, C.violeta, 2f)
                            Column(Modifier.weight(1f)) {
                                T(st.titulo, 14f, 800, LocalPaleta.current.texto, unRenglon = true)
                                if (st.sub.isNotBlank()) T(st.sub, 12f, 400, LocalPaleta.current.texto2, unRenglon = true)
                            }
                        }
                    }
                }
            }
        }
        // El mapa sigue vivo debajo de los resultados (si se saca de la composición, MapLibre se rompe).
        Box(Modifier.fillMaxWidth().height(if (r != null) 1.dp else 220.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFF2EFE9))) {
            AndroidView({ vista }, Modifier.fillMaxSize().padding(0.dp))
            Box(Modifier.align(Alignment.Center).offset(y = (-14).dp)) { Icono(I.pin, 34.dp, C.violeta, 2.4f, relleno = C.blanco) }
        }
        CampoCrear(lugar, { lugar = it.take(80) }, "Nombre del lugar", tam = 16f, alto = 52.dp)
        CampoCrear(direccion, { direccion = it.take(160) }, "Dirección", tam = 16f, alto = 52.dp)
        CampoCrear(zona, { zona = it.take(60) }, "Barrio o zona", tam = 16f, alto = 52.dp)
        Cta("Listo", {
            mapa?.cameraPosition?.target?.let { b.lat = it.latitude; b.lng = it.longitude }
            b.lugar = lugar.trim(); b.direccion = direccion.trim(); b.zona = zona.trim(); b.ciudad = ciudad.trim()
            cerrar()
        }, Modifier.fillMaxWidth(), alto = 56.dp)
    }
}

/** Publicado (prototipo): fondo violeta, isotipo con tk-pop, "Plan publicado" y compartir el link. */
@Composable
fun Publicado(ir: Ir, id: String) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val plan = rememberCarga(id) { c.api.plan(id) }
    val escala = remember { Animatable(.4f) }
    val giro = remember { Animatable(-20f) }
    LaunchedEffect(Unit) {
        launch { escala.animateTo(1f, keyframes { durationMillis = 500; 1.08f at 300; 1f at 500 }) }
        giro.animateTo(0f, keyframes { durationMillis = 500; 6f at 300; 0f at 500 })
    }
    Pantalla(fondo = Modifier.background(radial(.5f, .3f, 1f, .7f, 0f to Color(0xFF8A70FF), 1f to C.violeta4)).fondoCaigo(false, radial(.5f, .3f, 1f, .7f, 0f to Color(0xFF8A70FF), 1f to C.violeta4))) { arriba, abajo ->
        Column(
            Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, bottom = abajo),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(200.dp).scale(escala.value).rotate(giro.value))
            T("Plan publicado", 36f, 800, C.blanco, alto = 1f, espaciado = px(-1f, 36f), alinear = TextAlign.Center)
            val p = plan.dato
            T("«${p?.titulo.orEmpty()}» ya aparece en Ahora. Te avisamos cuando alguien se prenda.", 15f, 500, rgba(255, 255, 255, .9f), alto = 1.5f, alinear = TextAlign.Center)
            Box(Modifier.height(4.dp))
            if (p == null) Puntitos(C.blanco) else {
                val link = "https://caigo.com.ar/p/${p.slug}"
                Cta("Compartir link por WhatsApp", { compartirWhatsApp(ctx, "${p.titulo} · ${Cupo.badge(p.faltan)}. Sumate en Caigo: $link") }, Modifier.fillMaxWidth(), alto = 58.dp, tam = 16f)
                T("Copiar link", 14f, 800, C.blanco, modifier = Modifier.tocable { copiar(ctx, link); avisos.ok("Link copiado: caigo.com.ar/p/${p.slug}") })
            }
            T("Ver mis planes", 14f, 800, C.blanco, modifier = Modifier.tocable { ir.principal(Pestana.Mis) })
        }
    }
}
