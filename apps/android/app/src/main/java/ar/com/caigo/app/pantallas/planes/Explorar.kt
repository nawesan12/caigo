package ar.com.caigo.app.pantallas.planes

import ar.com.caigo.app.piezas.colorDeCategoria
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.R
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.app.altoTabBar
import ar.com.caigo.app.nucleo.Api
import ar.com.caigo.app.nucleo.Categorias
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.nucleo.FiltrosFinos
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.ZonaAR
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.BadgeFalta
import ar.com.caigo.app.piezas.Chip
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.Interruptor
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.tocable
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
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

/**
 * Las categorías de Explorar: «Todos» y las 8 de un plan (06a), cada una con su foto local CC0
 * (res/drawable-nodpi/explorar_*.jpg; fuentes en apps/android/FUENTES.md).
 */
private val tilesExplorar: List<Triple<String?, String, Int?>> =
    listOf(Triple<String?, String, Int?>(null, "Todos", R.drawable.explorar_todos)) + Categorias.paraCrear.map { (k, l) -> Triple(k, l, fotoTile(k)) }

private fun fotoTile(clave: String): Int? = when (clave) {
    "deporte" -> R.drawable.explorar_deporte
    "juego" -> R.drawable.explorar_juego
    "previa" -> R.drawable.explorar_previa
    "salida" -> R.drawable.explorar_salida
    "gastronomia" -> R.drawable.explorar_gastronomia
    "cita_doble" -> R.drawable.explorar_cita_doble
    "cultura" -> R.drawable.explorar_cultura
    "otro" -> R.drawable.explorar_otro
    else -> null
}

/** Edad de quien organiza (filtro fino): etiqueta y rango para la API. */
private val rangosEdad: List<Triple<String, Int?, Int?>> = listOf(
    Triple("Todas", null, null), Triple("18–30", 18, 30), Triple("31–45", 31, 45), Triple("46+", 46, null),
)

private val gelTarjetaFila = Gel(vertical(C.blanco, C.blanco), listOf(interna(1f, 0f, C.blanco), externa(2f, 0f, C.borde2), externa(12f, 24f, rgba(80, 60, 160, .22f), spread = -12f)))

/** Lo que dispara una carga (el `Clave` de iOS). */
private data class ClaveExplorar(
    val categoria: String?, val ciudad: String?, val zona: String?, val lat: Double?, val finos: FiltrosFinos, val vuelta: Int, val q: String?,
)

/** 04 · Explorar: "¿Qué tenés ganas de hacer?", categorías con foto, días, recurrentes y agenda. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Explorar(ir: Ir) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val yo by c.yo.collectAsState()
    val plus by c.plus.collectAsState()
    var categoria by rememberSaveable { mutableStateOf<String?>(null) }
    var dia by rememberSaveable { mutableIntStateOf(0) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var verificados by rememberSaveable { mutableStateOf(false) }
    var faltan by rememberSaveable { mutableStateOf<Int?>(null) }
    var edad by rememberSaveable { mutableIntStateOf(0) }
    var gratis by rememberSaveable { mutableStateOf(false) }
    var ciudad by rememberSaveable { mutableStateOf<String?>(null) }
    var zona by rememberSaveable { mutableStateOf<String?>(null) }
    var zonas by remember { mutableStateOf<List<String>>(emptyList()) }
    var punto by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var filtros by rememberSaveable { mutableStateOf(false) }
    var todos by rememberSaveable { mutableStateOf(false) }
    var semana by remember { mutableStateOf<List<Plan>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var vuelta by remember { mutableIntStateOf(0) }

    val conPlus = plus?.activo == true
    // Los filtros finos son de Caigo Plus: sin Plus no se mandan (la API respondería 403 `plus`).
    val finos = if (!conPlus) FiltrosFinos() else rangosEdad[edad].let { r -> FiltrosFinos(verificados, faltan, r.second, r.third, gratis) }

    LaunchedEffect(Unit) {
        if (c.plus.value == null) c.recargarPlus()
        // La ubicación sólo sirve para la distancia: si no hay permiso, no se pide de nuevo acá.
        if (c.tienePermisoUbicacion()) punto = c.ubicacion()
    }
    val ciudadZonas = ciudad ?: yo?.ciudad ?: ""
    LaunchedEffect(ciudadZonas) {
        zonas = runCatching { c.api.zonas(ciudadZonas.ifEmpty { "Mar del Plata" }).zonas }.getOrDefault(emptyList())
        val z = zona
        if (z != null && z !in zonas) zona = null
    }
    val q = Api.busqueda(busqueda)
    LaunchedEffect(ClaveExplorar(categoria, ciudad, zona, punto?.first, finos, vuelta, q)) {
        // Mientras se escribe, se espera un toque antes de buscar (si cambia, esta tarea se cancela).
        if (q != null) delay(350)
        val ahora = ZonedDateTime.now(ZonaAR)
        val hasta = ahora.toLocalDate().atStartOfDay(ZonaAR).plusDays(7)
        try {
            semana = c.api.explorar(
                desde = Fecha.iso(ahora), hasta = Fecha.iso(hasta), categoria = categoria, ciudad = ciudad,
                lat = punto?.first, lng = punto?.second, finos = finos, q = q, zona = zona,
            ).planes
            error = null
        } catch (e: ErrorApi) {
            if (e.codigo == "plus") ir.plus("filtros") else { error = mensajeDe(e) }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = mensajeDe(e)
        }
    }

    // La búsqueda la hace la API con `q` (título, zona y lugar).
    val planes = semana.orEmpty()
    val ahora = ZonedDateTime.now(ZonaAR)
    val delDia = planes.filter { pl -> Fecha.parse(pl.empiezaEn)?.let { Fecha.diasEntre(ahora, it) == dia } ?: false }
    val recurrente = planes.firstOrNull { it.recurrente }
    fun fechaDia(i: Int) = ahora.toLocalDate().plusDays(i.toLong()).atStartOfDay(ZonaAR)

    PullToRefreshBox(false, { vuelta++ }, Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = arriba + 8.dp, bottom = altoTabBar()),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    T("Explorar", 40f, 800, p.texto, alto = 1f, espaciado = px(-1.4f, 40f))
                    Buscador(busqueda, { busqueda = it }) { filtros = !filtros }
                    AnimatedVisibility(
                        filtros,
                        enter = fadeIn(tween(200, easing = EaseOut)) + slideInVertically(tween(200, easing = EaseOut)) { -it },
                        exit = fadeOut(tween(200, easing = EaseOut)) + slideOutVertically(tween(200, easing = EaseOut)) { -it },
                    ) {
                        PanelFiltros(
                            ir, conPlus, ciudad, { ciudad = it }, zonas, zona, { zona = it },
                            verificados, { verificados = it }, faltan, { faltan = it }, edad, { edad = it }, gratis, { gratis = it },
                        )
                    }
                }
            }
            item { Tiles(categoria) { categoria = it } }
            item {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 6).forEach { i ->
                        val d = fechaDia(i)
                        val on = i == dia
                        val hay = planes.any { pl -> Fecha.parse(pl.empiezaEn)?.let { Fecha.diasEntre(ahora, it) == i } ?: false }
                        val fg = if (on) C.blanco else p.texto
                        Column(
                            Modifier.weight(1f).semantics { role = Role.Button; selected = on }.tocable { dia = i; todos = false }
                                .gel(
                                    18.dp,
                                    if (on) Gel(
                                        vertical(0f to Color(0xFF9A84FF), .55f to C.violeta, 1f to Color(0xFF6243E6)),
                                        listOf(interna(2f, 1f, rgba(255, 255, 255, .4f)), interna(-3f, 0f, rgba(40, 20, 140, .3f)), externa(10f, 18f, rgba(116, 86, 241, .6f), spread = -6f)),
                                    )
                                    else if (p.oscuro) Gels.chipOscuro else Gel(vertical(C.blanco, C.tinte5), listOf(interna(1f, 0f, C.blanco), externa(2f, 0f, C.borde2))),
                                )
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            T(if (i == 0) "Hoy" else Fecha.diaCorto(d), 12f, 700, fg)
                            T("${d.dayOfMonth}", 22f, 700, fg, alto = 1.1f)
                            Box(Modifier.size(5.dp).background(if (on) C.lima else if (hay) C.coral else Color.Transparent, CircleShape))
                        }
                    }
                }
            }
            if (recurrente != null) item { Box(Modifier.padding(horizontal = 20.dp)) { TarjetaRecurrente(recurrente, ir) } }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    val d = fechaDia(dia)
                    T(if (dia == 0) "Hoy" else "${Fecha.diaLargo(d)} ${d.dayOfMonth}", 22f, 700, p.texto, Modifier.weight(1f))
                    if (delDia.size > 4 && !todos) T("Ver los ${delDia.size}", 13f, 700, if (p.oscuro) C.lima else C.violeta, modifier = Modifier.tocable { todos = true })
                }
            }
            when {
                semana == null -> item {
                    Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 30.dp), contentAlignment = Alignment.Center) {
                        val e = error
                        if (e != null) T(e, 15f, 500, p.texto2, alinear = TextAlign.Center) else Puntitos(p.acento)
                    }
                }
                delDia.isEmpty() -> item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        T("Todavía no hay planes para este día. ¿Armás uno?", 15f, 500, p.texto2, alinear = TextAlign.Center)
                        Chip("Armá un plan", true, { ir.crear() })
                    }
                }
                else -> items(if (todos) delDia else delDia.take(4), key = { it.id }) { Box(Modifier.padding(horizontal = 20.dp)) { FilaAgenda(it, ir) } }
            }
        }
    }
}

@Composable
private fun Buscador(busqueda: String, alCambiar: (String) -> Unit, alFiltros: () -> Unit) {
    val p = LocalPaleta.current
    Row(
        Modifier.fillMaxWidth().height(60.dp)
            .gel(
                22.dp,
                if (p.oscuro) Gels.tarjetaOscura
                else Gel(vertical(C.blanco, C.blanco), listOf(interna(1.5f, 0f, C.blanco), externa(2f, 0f, C.borde2), externa(16f, 30f, rgba(80, 60, 160, .3f), spread = -12f))),
            )
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icono(I.lupaChica, 22.dp, if (p.oscuro) C.lima else C.violeta, 2.4f)
        PlanesCampo(busqueda, alCambiar, "¿Qué tenés ganas de hacer?", Modifier.weight(1f), tam = 16f, colorPista = C.inactivo)
        Box(
            Modifier.size(46.dp).semantics { contentDescription = "Filtros" }.toque(onClick = alFiltros).gel(16.dp, planesVioletaChico),
            contentAlignment = Alignment.Center,
        ) { Icono(I.filtros, 20.dp, C.blanco, 2.4f) }
    }
}

@Composable
private fun Tiles(categoria: String?, alElegir: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 24.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
    ) {
        items(tilesExplorar, key = { it.second }) { (k, l, foto) ->
            val on = k == categoria
            val radio = if (on) 20.dp else 24.dp
            Box(
                Modifier.size(96.dp, 112.dp).semantics { role = Role.Button; selected = on }.tocable { alElegir(k) }
                    .then(if (on) Modifier.border(3.dp, C.violeta, RoundedCornerShape(27.dp)).padding(5.dp) else Modifier)
                    .gel(radio, null, listOf(externa(12f, 22f, rgba(36, 33, 45, .45f), spread = -10f)))
                    .clip(RoundedCornerShape(radio))
                    .background(colorDeCategoria(k ?: "")),
            ) {
                if (foto != null) Image(painterResource(foto), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.4f to rgba(32, 29, 43, 0f), 1f to rgba(32, 29, 43, .85f))))
                T(l, 15f, 700, C.blanco, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
            }
        }
    }
}

/** Panel de filtros: ciudad para todos; los finos con el chip «Plus». Sin Plus, tocarlos abre el paywall. */
@Composable
private fun PanelFiltros(
    ir: Ir, conPlus: Boolean,
    ciudad: String?, alCiudad: (String?) -> Unit,
    zonas: List<String>, zona: String?, alZona: (String?) -> Unit,
    verificados: Boolean, alVerificados: (Boolean) -> Unit,
    faltan: Int?, alFaltan: (Int?) -> Unit,
    edad: Int, alEdad: (Int) -> Unit,
    gratis: Boolean, alGratis: (Boolean) -> Unit,
) {
    @Composable
    fun Separador() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.lavanda))

    @Composable
    fun Titulo(t: String, modifier: Modifier = Modifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        T(t, 14f, 700, C.carbon, unRenglon = true)
        if (!conPlus) PlanesChipPlus()
    }

    @Composable
    fun FilaInterruptor(t: String, on: Boolean, alCambiar: (Boolean) -> Unit) =
        Row(
            Modifier.fillMaxWidth().tocable { if (conPlus) alCambiar(!on) else ir.plus("filtros") }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Titulo(t, Modifier.weight(1f))
            Interruptor(conPlus && on, { if (conPlus) alCambiar(it) else ir.plus("filtros") })
        }

    @Composable
    fun FilaChips(t: String, opciones: List<String>, elegida: Int, elegir: (Int) -> Unit) =
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Titulo(t)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                opciones.forEachIndexed { i, l ->
                    Chip(l, conPlus && i == elegida, { if (conPlus) elegir(i) else ir.plus("filtros") }, tam = 13f, padH = 12.dp, padV = 8.dp, radio = 13.dp)
                }
            }
        }

    Column(Modifier.fillMaxWidth().gel(22.dp, Gels.neutra)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "Todas", "Mar del Plata" to "Mar del Plata", "Villa Gesell" to "Villa Gesell").forEach { (k, l) ->
                Chip(l, ciudad == k, { alCiudad(k) }, tam = 13f, padH = 12.dp, padV = 8.dp, radio = 13.dp)
            }
        }
        if (zonas.isNotEmpty()) {
            Separador()
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Zona", 14f, 700, C.carbon, Modifier.padding(horizontal = 16.dp), unRenglon = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
                    item { Chip("Todas", zona == null, { alZona(null) }, tam = 13f, padH = 12.dp, padV = 8.dp, radio = 13.dp) }
                    items(zonas) { z -> Chip(z, zona == z, { alZona(if (zona == z) null else z) }, tam = 13f, padH = 12.dp, padV = 8.dp, radio = 13.dp) }
                }
            }
        }
        Separador()
        FilaInterruptor("Solo verificados", verificados, alVerificados)
        Separador()
        FilaChips("Que falten al menos", listOf("Cualquiera", "1", "2", "3"), faltan ?: 0) { alFaltan(if (it > 0) it else null) }
        Separador()
        FilaChips("Edad de quien organiza", rangosEdad.map { it.first }, edad, alEdad)
        Separador()
        FilaInterruptor("Solo gratis", gratis, alGratis)
    }
}

/** Recurrente: "Todos los sábados · 10:00 · Pádel mixto en Gesell". */
@Composable
private fun TarjetaRecurrente(p: Plan, ir: Ir) {
    // java.time: 1 = lunes … 7 = domingo (en iOS el weekday arranca en domingo).
    val dias = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábados", "domingos")
    val t = Fecha.parse(p.empiezaEn)
    val etiqueta = if (t != null) "Todos los ${dias[t.dayOfWeek.value - 1]} · ${Fecha.hora(t)}" else "Todas las semanas"
    Box(
        Modifier.fillMaxWidth().height(168.dp).semantics { role = Role.Button }.toque(alBajar = 0.dp) { ir.plan(p.id) }
            .gel(28.dp, null, listOf(externa(18f, 32f, rgba(36, 33, 45, .5f), spread = -14f))).clip(RoundedCornerShape(28.dp)),
    ) {
        Foto(fotoDe(p), Modifier.fillMaxSize(), colorDeCategoria(p.categoria))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to rgba(32, 29, 43, .88f), .75f to rgba(32, 29, 43, .2f))))
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.gel(11.dp, vertical(rgba(255, 255, 255, .18f), rgba(255, 255, 255, .18f))).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    T(etiqueta, 12f, 800, C.blanco, unRenglon = true)
                }
                if (p.destacado) ChipDestacado()
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(p.titulo, 24f, 700, C.blanco, alto = 1.05f, maxRenglones = 2)
                    T(
                        listOf(p.lugar?.nombre ?: p.lugarNombre, if (p.soloVerificados) "verificados" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                        13f, 400, Color(0xFFDAD6E6), unRenglon = true,
                    )
                }
                Spacer(Modifier.width(8.dp))
                BadgeFalta(p.faltan, tam = 17f)
            }
        }
    }
}

/** Fila de la agenda: foto 64, "18:00 · PÁDEL", título, lugar y badge. */
@Composable
private fun FilaAgenda(p: Plan, ir: Ir) {
    val pal = LocalPaleta.current
    val f = p.faltan
    val acento = if (pal.oscuro) C.lima else C.violeta
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { role = Role.Button }.toque(alBajar = 0.dp) { ir.plan(p.id) }
            .gel(24.dp, if (pal.oscuro) Gels.tarjetaOscura else gelTarjetaFila).padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Foto(fotoDe(p), Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)), colorDeCategoria(p.categoria))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (p.destacado) Icono(I.estrella, 12.dp, Color.Transparent, 0f, relleno = acento)
                T("${Fecha.hora(p.empiezaEn)} · ${Categorias.nombre(p.categoria).uppercase()}", 12f, 800, acento, unRenglon = true)
            }
            T(p.titulo, 15f, 700, pal.texto, alto = 1.25f, maxRenglones = 2)
            T(lugarYDistancia(p) + if (p.soloVerificados) " · Verificados" else "", 12f, 400, pal.texto2, unRenglon = true)
        }
        Box(
            Modifier.gel(
                12.dp,
                when {
                    Cupo.completo(f) -> Gel(vertical(C.cupoLleno1, C.cupoLleno2), emptyList())
                    f == 1 -> Gel(vertical(0f to Color(0xFFFFA597), .5f to C.coral, 1f to Color(0xFFF2604F)), emptyList())
                    else -> Gel(vertical(C.tinte2, Color(0xFFEAE4FF)), emptyList())
                },
            ).padding(horizontal = 10.dp, vertical = 7.dp),
        ) { T(Cupo.badge(f), 15f, 700, C.carbon, alto = 1f, unRenglon = true) }
    }
}
