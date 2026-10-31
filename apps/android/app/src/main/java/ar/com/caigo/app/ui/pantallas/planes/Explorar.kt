package ar.com.caigo.app.ui.pantallas.planes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.datos.FiltrosFinos
import ar.com.caigo.app.datos.Plan
import ar.com.caigo.app.logica.Cupo
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.altoTabBar
import ar.com.caigo.app.ui.piezas.BadgeFalta
import ar.com.caigo.app.ui.piezas.Chip
import ar.com.caigo.app.ui.piezas.ChipPlus
import ar.com.caigo.app.ui.piezas.Fecha
import ar.com.caigo.app.ui.piezas.Foto
import ar.com.caigo.app.ui.piezas.Interruptor
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.ZonaAR
import ar.com.caigo.app.ui.piezas.nombreCategoria
import ar.com.caigo.app.ui.piezas.rememberCarga
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.estilo
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical
import java.time.LocalDate
import java.time.ZonedDateTime

private fun pexels(id: Long) = "https://images.pexels.com/photos/$id/pexels-photo-$id.jpeg?auto=compress&cs=tinysrgb&w=600"

/** Las categorías con foto de Explorar (las del prototipo, con sus mismas fotos de referencia). */
private val tilesExplorar = listOf(
    null to ("Todos" to pexels(19116858)),
    "deporte" to ("Deporte" to pexels(27572834)),
    "juego" to ("Juego" to pexels(8551084)),
    "previa" to ("Previa" to pexels(6760903)),
    "salida" to ("Salida" to pexels(2962035)),
)

private val gelTarjetaFila = Gel(vertical(C.blanco, C.blanco), listOf(interna(1f, 0f, C.blanco), externa(2f, 0f, C.borde2), externa(12f, 24f, rgba(80, 60, 160, .22f), spread = -12f)))

/** 04 · Explorar: "¿Qué tenés ganas de hacer?", categorías con foto, días, recurrentes y agenda. */
@Composable
fun Explorar(ir: Ir) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var categoria by rememberSaveable { mutableStateOf<String?>(null) }
    var dia by rememberSaveable { mutableIntStateOf(0) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var verificados by rememberSaveable { mutableStateOf(false) }
    var faltan by rememberSaveable { mutableStateOf<Int?>(null) }
    var edad by rememberSaveable { mutableIntStateOf(0) }
    var gratis by rememberSaveable { mutableStateOf(false) }
    var ciudad by rememberSaveable { mutableStateOf<String?>(null) }
    var filtros by rememberSaveable { mutableStateOf(false) }
    var todos by rememberSaveable { mutableStateOf(false) }
    val hoy = LocalDate.now(ZonaAR)
    // Los filtros finos son de Caigo Plus: sin Plus no se mandan (la API respondería 403 `plus`).
    val plus = rememberCarga { runCatching { c.api.plus().estado }.getOrNull() }
    val conPlus = plus.dato?.activo == true
    val (edadDesde, edadHasta) = rangosEdad[edad].second
    val finos = if (conPlus) FiltrosFinos(verificados, faltan, edadDesde, edadHasta, gratis) else FiltrosFinos()
    val semana = rememberCarga(categoria, ciudad, finos) {
        val desde = ZonedDateTime.now(ZonaAR)
        val hasta = hoy.plusDays(6).atStartOfDay(ZonaAR).plusDays(1)
        c.api.explorar(desde = Fecha.iso(desde), hasta = Fecha.iso(hasta), categoria = categoria, ciudad = ciudad, finos = finos).planes
    }
    val planes = semana.dato.orEmpty().filter { busqueda.isBlank() || it.titulo.contains(busqueda.trim(), true) || it.lugarNombre.contains(busqueda.trim(), true) }
    val delDia = planes.filter { Fecha.parse(it.empiezaEn)?.toLocalDate() == hoy.plusDays(dia.toLong()) }
    val recurrente = planes.firstOrNull { it.recurrente }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = arriba + 8.dp, bottom = altoTabBar()),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T("Explorar", 40f, 800, p.texto, alto = 1f, espaciado = px(-1.4f, 40f))
                Row(
                    Modifier.fillMaxWidth().height(60.dp)
                        .gel(22.dp, if (p.oscuro) Gels.tarjetaOscura else Gel(vertical(C.blanco, C.blanco), listOf(interna(1.5f, 0f, C.blanco), externa(2f, 0f, C.borde2), externa(16f, 30f, rgba(80, 60, 160, .3f), spread = -12f))))
                        .padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icono(I.lupaChica, 22.dp, if (p.oscuro) C.lima else C.violeta, 2.4f)
                    Box(Modifier.weight(1f)) {
                        if (busqueda.isEmpty()) T("¿Qué tenés ganas de hacer?", 16f, 500, C.inactivo, unRenglon = true)
                        BasicTextField(busqueda, { busqueda = it }, singleLine = true, textStyle = estilo(16f, 500, p.texto), cursorBrush = SolidColor(C.violeta), modifier = Modifier.fillMaxWidth())
                    }
                    Box(Modifier.size(46.dp).toque { filtros = !filtros }.gel(16.dp, Gels.violeta.copy(sombras = Gels.violeta.sombras.map { if (!it.inset && it.blur == 0f) it.copy(y = 4f) else if (!it.inset) it.copy(y = 16f, blur = 26f) else it })), contentAlignment = Alignment.Center) {
                        Icono(I.filtros, 20.dp, C.blanco, 2.4f)
                    }
                }
                if (filtros) Filtros(
                    conPlus, { ir.plus("perfil") }, ciudad, { ciudad = it },
                    verificados, { verificados = it }, faltan, { faltan = it }, edad, { edad = it }, gratis, { gratis = it },
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 20.dp, top = 6.dp, bottom = 14.dp, start = 4.dp)) {
                items(tilesExplorar) { (k, v) ->
                    val (l, foto) = v
                    val on = k == categoria
                    Box(
                        Modifier.size(96.dp, 112.dp).tocable { categoria = k }
                            .then(if (on) Modifier.border(3.dp, C.violeta, RoundedCornerShape(27.dp)).padding(5.dp) else Modifier.padding(0.dp))
                            .gel(if (on) 20.dp else 24.dp, null, listOf(externa(12f, 22f, rgba(36, 33, 45, .45f), spread = -10f)))
                            .clip(RoundedCornerShape(if (on) 20.dp else 24.dp)),
                    ) {
                        Foto(foto, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.4f to rgba(32, 29, 43, 0f), 1f to rgba(32, 29, 43, .85f))))
                        T(l, 15f, 700, C.blanco, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 6).forEach { i ->
                    val d = hoy.plusDays(i.toLong())
                    val on = i == dia
                    val hay = planes.any { Fecha.parse(it.empiezaEn)?.toLocalDate() == d }
                    Column(
                        Modifier.weight(1f).tocable { dia = i; todos = false }
                            .gel(18.dp, if (on) Gel(vertical(0f to Color(0xFF9A84FF), .55f to C.violeta, 1f to Color(0xFF6243E6)), listOf(interna(2f, 1f, rgba(255, 255, 255, .4f)), interna(-3f, 0f, rgba(40, 20, 140, .3f)), externa(10f, 18f, rgba(116, 86, 241, .6f), spread = -6f)))
                            else if (p.oscuro) Gels.chipOscuro else Gel(vertical(C.blanco, C.tinte5), listOf(interna(1f, 0f, C.blanco), externa(2f, 0f, C.borde2))))
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        val fg = if (on) C.blanco else p.texto
                        T(if (i == 0) "Hoy" else Fecha.diaCorto(d.atStartOfDay(ZonaAR)), 12f, 700, fg)
                        T("${d.dayOfMonth}", 22f, 700, fg, alto = 1.1f)
                        Box(Modifier.size(5.dp).background(if (on) C.lima else if (hay) C.coral else Color.Transparent, CircleShape))
                    }
                }
            }
        }
        if (recurrente != null) item { TarjetaRecurrente(recurrente, ir) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val d = hoy.plusDays(dia.toLong())
                T(if (dia == 0) "Hoy" else "${Fecha.diaLargo(d.atStartOfDay(ZonaAR))} ${d.dayOfMonth}", 22f, 700, p.texto, modifier = Modifier.weight(1f))
                if (delDia.size > 4 && !todos) T("Ver los ${delDia.size}", 13f, 700, if (p.oscuro) C.lima else C.violeta, modifier = Modifier.tocable { todos = true })
            }
        }
        when {
            semana.dato == null -> item {
                Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                    if (semana.error != null) T(semana.error!!, 15f, 500, p.texto2, alinear = TextAlign.Center) else Puntitos()
                }
            }
            delDia.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T("Todavía no hay planes para este día. ¿Armás uno?", 15f, 500, p.texto2, alinear = TextAlign.Center)
                    Chip("Armá un plan", true, { ir.crear() })
                }
            }
            else -> items(if (todos) delDia else delDia.take(4), key = { it.id }) { FilaAgenda(it, ir) }
        }
    }
}

/** Edad de quien organiza (filtro fino): etiqueta y rango para la API. */
private val rangosEdad = listOf(
    "Todas" to (null to null),
    "18–30" to (18 to 30),
    "31–45" to (31 to 45),
    "46+" to (46 to null),
)

/**
 * Panel de filtros: ciudad para todos; los finos (verificados, que falten al menos N, edad de quien
 * organiza y sólo gratis) con el chip «Plus» del prototipo. Sin Plus, tocarlos abre el paywall.
 */
@Composable
private fun Filtros(
    conPlus: Boolean, alPaywall: () -> Unit, ciudad: String?, onCiudad: (String?) -> Unit,
    verificados: Boolean, onVerificados: (Boolean) -> Unit,
    faltan: Int?, onFaltan: (Int?) -> Unit,
    edad: Int, onEdad: (Int) -> Unit,
    gratis: Boolean, onGratis: (Boolean) -> Unit,
) {
    @Composable
    fun Separador() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.lavanda))

    /** Título de una fila fina, con el chip «Plus» si no lo tenés. */
    @Composable
    fun Titulo(texto: String, modifier: Modifier = Modifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        T(texto, 14f, 700, C.carbon, unRenglon = true)
        if (!conPlus) ChipPlus()
    }

    @Composable
    fun FilaInterruptor(texto: String, on: Boolean, onCambio: (Boolean) -> Unit) =
        Row(Modifier.fillMaxWidth().tocable { if (conPlus) onCambio(!on) else alPaywall() }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Titulo(texto, Modifier.weight(1f))
            Interruptor(on && conPlus, { if (conPlus) onCambio(it) else alPaywall() })
        }

    @Composable
    fun FilaChips(texto: String, opciones: List<String>, elegida: Int, onElegir: (Int) -> Unit) =
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Titulo(texto)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                opciones.forEachIndexed { i, l ->
                    Chip(l, i == elegida, { if (conPlus) onElegir(i) else alPaywall() }, tam = 13f, padH = 12.dp, padV = 8.dp, radio = 13.dp)
                }
            }
        }

    Column(Modifier.fillMaxWidth().gel(22.dp, Gels.neutra)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "Todas", "Mar del Plata" to "Mar del Plata", "Villa Gesell" to "Villa Gesell").forEach { (k, l) ->
                Chip(l, ciudad == k, { onCiudad(k) }, tam = 13f, padH = 12.dp, padV = 8.dp, radio = 13.dp)
            }
        }
        Separador()
        FilaInterruptor("Solo verificados", verificados, onVerificados)
        Separador()
        FilaChips("Que falten al menos", listOf("Cualquiera", "1", "2", "3"), faltan ?: 0) { onFaltan(it.takeIf { n -> n > 0 }) }
        Separador()
        FilaChips("Edad de quien organiza", rangosEdad.map { it.first }, edad, onEdad)
        Separador()
        FilaInterruptor("Solo gratis", gratis, onGratis)
    }
}

/** Recurrente: "Todos los sábados · 10:00 · Pádel mixto en Gesell". */
@Composable
private fun TarjetaRecurrente(p: Plan, ir: Ir) {
    val t = Fecha.parse(p.empiezaEn)
    val dias = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábados", "domingos")
    Box(
        Modifier.fillMaxWidth().height(168.dp).toque(alBajar = 0.dp) { ir.plan(p.id) }
            .gel(28.dp, null, listOf(externa(18f, 32f, rgba(36, 33, 45, .5f), spread = -14f))).clip(RoundedCornerShape(28.dp)),
    ) {
        Foto(p.foto.ifBlank { p.lugar?.foto }, Modifier.fillMaxSize(), colorDeCategoria(p.categoria))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to rgba(32, 29, 43, .88f), .75f to rgba(32, 29, 43, .2f))))
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.gel(11.dp, vertical(rgba(255, 255, 255, .18f), rgba(255, 255, 255, .18f))).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    T(if (t != null) "Todos los ${dias[t.dayOfWeek.value - 1]} · ${Fecha.hora(t)}" else "Todas las semanas", 12f, 800, C.blanco, unRenglon = true)
                }
                if (p.destacado) ChipDestacado()
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(p.titulo, 24f, 700, C.blanco, alto = 1.05f, maxRenglones = 2)
                    T(listOf(p.lugar?.nombre ?: p.lugarNombre, if (p.soloVerificados) "verificados" else "").filter { it.isNotBlank() }.joinToString(" · "), 13f, 400, Color(0xFFDAD6E6), unRenglon = true)
                }
                BadgeFalta(p.faltan, tam = 17f)
            }
        }
    }
}

/** Fila de la agenda: foto 64, "18:00 · PÁDEL", título, lugar y badge. */
@Composable
private fun FilaAgenda(p: Plan, ir: Ir) {
    val pal = LocalPaleta.current
    Row(
        Modifier.fillMaxWidth().toque(alBajar = 0.dp) { ir.plan(p.id) }
            .gel(24.dp, if (pal.oscuro) Gels.tarjetaOscura else gelTarjetaFila).padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Foto(p.foto.ifBlank { p.lugar?.foto }, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)), colorDeCategoria(p.categoria))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Destacado (Plus): la estrella del chip «Destacado» delante del renglón violeta; la palabra no entra.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (p.destacado) Icono(I.estrella, 12.dp, Color.Transparent, 0f, relleno = if (pal.oscuro) C.lima else C.violeta)
                T("${Fecha.hora(p.empiezaEn)} · ${nombreCategoria(p.categoria).uppercase()}", 12f, 800, if (pal.oscuro) C.lima else C.violeta, unRenglon = true)
            }
            T(p.titulo, 15f, 700, pal.texto, alto = 1.25f, maxRenglones = 2)
            T(lugarYDistancia(p) + if (p.soloVerificados) " · Verificados" else "", 12f, 400, pal.texto2, unRenglon = true)
        }
        val f = p.faltan
        Box(
            Modifier.gel(12.dp, when {
                Cupo.completo(f) -> Gel(vertical(C.cupoLleno1, C.cupoLleno2), emptyList())
                f == 1 -> Gel(vertical(0f to Color(0xFFFFA597), .5f to C.coral, 1f to Color(0xFFF2604F)), emptyList())
                else -> Gel(vertical(C.tinte2, Color(0xFFEAE4FF)), emptyList())
            }).padding(horizontal = 10.dp, vertical = 7.dp),
        ) { T(Cupo.badge(f), 15f, 700, C.carbon, alto = 1f, unRenglon = true) }
    }
}
