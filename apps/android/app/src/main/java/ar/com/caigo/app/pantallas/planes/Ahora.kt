package ar.com.caigo.app.pantallas.planes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.R
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.app.Pestana
import ar.com.caigo.app.app.altoTabBar
import ar.com.caigo.app.nucleo.Api
import ar.com.caigo.app.nucleo.FiltrosFinos
import ar.com.caigo.app.nucleo.Invitaciones
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.VacioAhora
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.Avatar
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.piezas.Chip
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.Segmentado
import ar.com.caigo.app.piezas.rememberPermisoUbicacion
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.EspaciadoTitulo
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
import ar.com.caigo.app.piezas.Sistema
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/** Las categorías del filtro de Ahora (las del mockup 02). */
private val filtrosAhora = listOf(
    null to "Todos", "deporte" to "Deporte", "juego" to "Juego", "previa" to "Previa",
    "cita_doble" to "Cita doble", "salida" to "Salida", "taller" to "Taller",
)

/** Lo que dispara una carga (el `Clave` de iOS). */
private data class ClaveAhora(
    val lat: Double?, val lng: Double?, val sinUbicacion: Boolean, val categoria: String?, val chicas: Boolean, val q: String?,
    val vuelta: Int,
)

/**
 * Ahora (02 lista · 03 mapa · 13a vacío · 14a oscuro): sólo planes. Gente y Citas salieron de la
 * cabecera (18/11: un mazo de caras con «Me encanta» cambiaba la lectura de toda la app); se abren desde
 * Perfil › Mi gente (un aviso que pide Ahora en modo gente/citas abre el mazo: ver [Ir.principal]).
 * `modoInicial` queda por compatibilidad y no se usa.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Ahora(ir: Ir, modoInicial: String?) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val yo by c.yo.collectAsState()
    var mapa by rememberSaveable { mutableStateOf(false) }
    var categoria by rememberSaveable { mutableStateOf<String?>(null) }
    // «Sólo chicas» (planes «Solo mujeres»): gratis, como toda la seguridad.
    var chicas by rememberSaveable { mutableStateOf(false) }
    var punto by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // Sin permiso de ubicación (o sin señal): se consulta por la ciudad del perfil.
    var sinUbicacion by remember { mutableStateOf(false) }
    // Barrio y ciudad de la ubicación (geocodificación inversa) para el header.
    var lugarActual by remember { mutableStateOf<String?>(null) }
    // Lo que se busca en el mapa (va a la API como `q`).
    var busqueda by rememberSaveable { mutableStateOf("") }
    var planes by remember { mutableStateOf<List<Plan>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var invitaciones by remember { mutableStateOf<Invitaciones?>(null) }
    var vuelta by remember { mutableIntStateOf(0) }
    var pidio by rememberSaveable { mutableStateOf(false) }
    // «No me interesa» y su «Deshacer».
    val ocultos = remember { PlanesOcultos() }
    // «Decidí rápido» abierto: los planes del mazo (los de la lista al abrirlo).
    // Los ids del mazo (rememberSaveable): «Ver más» navega a otra entrada y, al volver, el mazo sigue abierto y
    // se arma de nuevo con los planes que sigan en la lista.
    var decidiendoIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
    // El plan de hoy (fijo arriba, con «Llegué» y «¿Todo bien?») y su hoja.
    var hoy by remember { mutableStateOf<Plan?>(null) }
    var hojaHoy by remember { mutableStateOf(false) }
    val (permiso, pedir) = rememberPermisoUbicacion()

    LaunchedEffect(Unit) { if (yo == null) c.recargarYo() }
    // Pide el permiso si hace falta. Sin ubicación se consulta por ciudad, y si después llega el permiso
    // (la persona lo acepta en el cartel del sistema) se pasa a la ubicación.
    LaunchedEffect(permiso) {
        // Android: primero se contesta el pedido de avisos (Principal); dos pedidos juntos se pisan.
        c.avisosResueltos.first { it }
        if (!permiso && !pidio) { pidio = true; pedir() }
        val u = c.ubicacion()
        if (u != null) { punto = u; sinUbicacion = false; c.mandarUbicacion() } else { sinUbicacion = true }
    }
    LaunchedEffect(Unit) { invitaciones = runCatching { c.api.invitaciones() }.getOrNull() }
    LaunchedEffect(vuelta, yo?.verificada) { if (yo?.verificada == true) hoy = planDeHoy(c) }
    // «Esta semana en <ciudad>»: de lunes a miércoles, con 5 planes o más.
    var semana by remember { mutableStateOf<ar.com.caigo.app.nucleo.Semana?>(null) }
    LaunchedEffect(Unit) {
        if (ar.com.caigo.app.nucleo.Dia.semanaVisible()) semana = runCatching { c.api.semana() }.getOrNull()?.takeIf { it.total >= 5 }
    }
    val q = if (mapa) Api.busqueda(busqueda) else null
    LaunchedEffect(ClaveAhora(punto?.first, punto?.second, sinUbicacion, categoria, chicas, q, vuelta)) {
        if (punto == null && !sinUbicacion) return@LaunchedEffect
        // Mientras se escribe en el buscador, se espera a que la persona pare (y la tarea anterior se cancela).
        if (q != null) delay(350)
        cargando = true
        try {
            val ciudad = if (punto == null) (yo?.ciudad?.ifEmpty { null } ?: "Mar del Plata") else null
            planes = c.api.ahora(punto?.first, punto?.second, categoria, q = q, finos = FiltrosFinos(chicas = chicas), ciudad = ciudad).planes
            error = null
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = mensajeDe(e)
        }
        cargando = false
    }
    LaunchedEffect(punto) {
        val (la, ln) = punto ?: return@LaunchedEffect
        lugarActual = barrio(ctx, la, ln)
    }

    if (mapa) {
        MapaAhora(ir, ocultos.visibles(planes.orEmpty()), punto, categoria, { categoria = it }, busqueda, { busqueda = it }, onLista = { mapa = false })
        return
    }

    val decidiendo = decidiendoIds?.let { ids ->
        val por = planes.orEmpty().associateBy { it.id }
        ids.mapNotNull { por[it] }.takeIf { it.isNotEmpty() || planes == null }
    }
    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Encabezado(ir, lugarActual, invitaciones)
            Chips(categoria, { categoria = it }, chicas, { chicas = it })
            Box(Modifier.weight(1f)) {
                Lista(
                    ir, planes?.let { ocultos.visibles(it) }, cargando, error, invitaciones, semana, ocultos,
                    alMapa = { mapa = true }, recargar = { vuelta++ }, alDecidir = { decidiendoIds = it.map { p -> p.id } },
                    hoy = hoy, alTodoBien = { hojaHoy = true },
                )
            }
        }
        AvisoDeshacer(ocultos, altoTabBar())
        hoy?.let { h ->
            HojaTodoBien(ir, h, hojaHoy, cerrar = { hojaHoy = false }, reportar = { ir.plan(h.id) }, recargar = { vuelta++ })
        }
        androidx.compose.animation.AnimatedVisibility(
            decidiendo != null, Modifier.fillMaxSize(),
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
        ) {
            val ultimo = remember { arrayOfNulls<List<Plan>>(1) }
            decidiendo?.let { ultimo[0] = it }
            ultimo[0]?.let { d -> DecidiRapido(ir, d, ocultos) { decidiendoIds = null; vuelta++ } }
        }
        ar.com.caigo.app.app.OcultarTabBar(decidiendo != null)
    }
}

/** "Güemes, Mar del Plata" de unas coordenadas (Geocoder, en es-AR). */
private suspend fun barrio(ctx: android.content.Context, la: Double, ln: Double): String? {
    val a = sitioEn(ctx, la, ln) ?: return null
    val vistas = listOfNotNull(a.subLocality, a.locality).filter { it.isNotEmpty() }.distinct()
    return if (vistas.isEmpty()) null else vistas.joinToString(", ")
}

@Composable
private fun Encabezado(ir: Ir, lugarActual: String?, invitaciones: Invitaciones?) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val yo by c.yo.collectAsState()
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icono(I.pin, 16.dp, if (p.oscuro) C.lima else C.violeta, 2.2f)
            val delPerfil = listOf(yo?.zona.orEmpty(), yo?.ciudad.orEmpty()).filter { it.isNotEmpty() }.joinToString(", ")
            val lugar = lugarActual ?: delPerfil
            T(lugar.ifEmpty { "Mar del Plata" }, 14f, 600, if (p.oscuro) C.apagado2 else C.texto2, Modifier.weight(1f), unRenglon = true)
            Spacer(Modifier.width(8.dp))
            if (p.oscuro) {
                T("Caigo", 25f, 700, C.blancoCalido, espaciado = EspaciadoTitulo)
            } else {
                val hay = invitaciones?.invitaciones?.isNotEmpty() == true
                Box {
                    BotonIcono(
                        I.campana, { ir.recibidos("gente", if (hay) 1 else 0) },
                        Modifier.semantics { contentDescription = "Te sumaron y te invitan" },
                        tam = 42.dp, radio = 15.dp, iconoTam = 20.dp, trazo = 2f,
                    )
                    if (hay) Box(Modifier.align(Alignment.TopEnd).offset((-10).dp, 9.dp).size(9.dp).background(C.coral, CircleShape))
                }
            }
        }
        T("Ahora", 40f, 800, p.texto, alto = 1f, espaciado = px(-1.4f, 40f), unRenglon = true)
    }
}

@Composable
private fun Chips(categoria: String?, alElegir: (String?) -> Unit, chicas: Boolean, alChicas: (Boolean) -> Unit) {
    val p = LocalPaleta.current
    LazyRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
    ) {
        // Primero y separado: un interruptor (se suma a la categoría elegida), no otra categoría. La seguridad a la
        // vista, gratis (igual que la web).
        item(key = "chicas") { InterruptorChicas(chicas) { alChicas(!chicas) } }
        item(key = "divisor") {
            Box(Modifier.padding(top = 9.dp).width(1.dp).height(22.dp).background(if (p.oscuro) Color(0xFF3A3550) else C.lavanda))
        }
        items(filtrosAhora, key = { it.second }) { (k, l) ->
            Chip(
                l, k == categoria, { alElegir(k) },
                gelOn = if (p.oscuro) Gels.limaChico else Gels.oscuroSel,
                gelOff = if (p.oscuro) Gels.chipOscuro else Gels.chipClaro,
                colorOn = if (p.oscuro) C.carbon else C.blancoCalido,
                colorOff = p.texto,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Lista(
    ir: Ir, planes: List<Plan>?, cargando: Boolean, error: String?, inv: Invitaciones?,
    semana: ar.com.caigo.app.nucleo.Semana?, ocultos: PlanesOcultos,
    alMapa: () -> Unit, recargar: () -> Unit, alDecidir: (List<Plan>) -> Unit,
    hoy: Plan? = null, alTodoBien: () -> Unit = {},
) {
    val p = LocalPaleta.current
    val c = LocalApp.current
    val avisos = ar.com.caigo.app.piezas.LocalAvisos.current
    val vista = androidx.compose.ui.platform.LocalView.current
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    val abajo = altoTabBar()
    if (planes != null && planes.isEmpty() && !cargando) {
        PullToRefreshBox(false, recargar, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = abajo)) { PlanesVacioAhora(ir) }
        }
        return
    }
    PullToRefreshBox(false, recargar, Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = abajo),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (inv != null && inv.invitaciones.isNotEmpty()) item { BannerInvitaciones(ir, inv) }
            if (hoy != null) item(key = "hoy") { HoyTarjeta(hoy, ir, alTodoBien) }
            if (semana != null) item { TarjetaSemana(semana, ir) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("Próximas 3 horas", 15f, 700, p.texto, unRenglon = true)
                        if (planes != null) {
                            T("${planes.size} ${if (planes.size == 1) "plan cerca" else "planes cerca"}", 13f, 400, p.texto2, unRenglon = true)
                            val mazo = ar.com.caigo.app.nucleo.Gestos.paraDecidir(planes, c.sesion.usuarioId)
                            if (mazo.size >= ar.com.caigo.app.nucleo.Gestos.minimoDecidir) {
                                Chip(
                                    "Decidí rápido · ${mazo.size}", false, { alDecidir(mazo) }, Modifier.padding(top = 8.dp),
                                    gelOff = Gels.chip(p), colorOff = p.texto, tam = 13f, padH = 12.dp, padV = 7.dp, radio = 12.dp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Segmentado(listOf("Lista", "Mapa"), 0, { if (it == 1) alMapa() }, radio = 14.dp, radioOpcion = 11.dp, alto = 40.dp, tam = 14f, llenar = false)
                }
            }
            when {
                planes != null -> items(planes, key = { it.id }) { pl ->
                    DeslizarPlan(
                        24.dp,
                        alCaigo = { alcance.launch { if (planesCaigo(c, ir, avisos, pl) { Sistema.vibrar(vista) } != null) recargar() } },
                        alNoMeInteresa = { ocultos.ocultar(c, avisos, pl) },
                        pista = pl.id == planes.firstOrNull()?.id,
                    ) { m -> TarjetaPlan(pl, ir, m, alCambiar = { recargar() }) }
                }
                error != null -> item {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        T(error, 15f, 500, p.texto2, alinear = TextAlign.Center)
                        Chip("Probar de nuevo", false, recargar)
                    }
                }
                else -> item { Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { Puntitos(p.acento) } }
            }
        }
    }
}

/** El banner oscuro del prototipo: "3 personas te invitan a sus planes" (borroso sin Plus). */
@Composable
private fun BannerInvitaciones(ir: Ir, inv: Invitaciones) {
    val n = inv.invitaciones.size
    val titulos = inv.invitaciones.map { it.titulo }.filter { it.isNotEmpty() }
    Row(
        Modifier.fillMaxWidth().semantics { role = Role.Button }.toque(alBajar = 0.dp) { ir.recibidos("gente", 1) }
            .gel(22.dp, Gels.oscuroSel.copy(sombras = listOf(interna(1f, 0f, rgba(255, 255, 255, .08f)), externa(14f, 24f, rgba(36, 33, 45, .6f), spread = -14f)), grano = true))
            .padding(start = 10.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(start = 8.dp)) {
            inv.invitaciones.take(3).forEachIndexed { i, x ->
                Avatar(
                    "·", x.de?.foto ?: x.foto, 36.dp, Modifier.offset(x = (-8 * i).dp).then(if (inv.bloqueada) Modifier.blur(3.dp) else Modifier),
                    colorBorde = C.carbon, fondo = C.oscuro2 to C.oscuro2,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(if (n == 1) "1 persona te invita a su plan" else "$n personas te invitan a sus planes", 14f, 800, C.blancoCalido)
            if (titulos.isNotEmpty()) T(titulos.joinToString(", ") { it.split(" en ").first() }, 12f, 600, Color(0xFFC9C4DA), unRenglon = true)
        }
        if (inv.bloqueada) PlanesChipPlus()
        Icono(I.adelante, 18.dp, Color(0xFFC9C4DA), 2.6f)
    }
}

/** 13a · No hay planes cerca. */
@Composable
internal fun PlanesVacioAhora(ir: Ir) {
    val p = LocalPaleta.current
    val c = LocalApp.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.height(180.dp).rotate(-10f), contentAlignment = Alignment.Center) {
            Box(Modifier.size(130.dp).gel(36.dp, null, listOf(externa(22f, 24f, rgba(116, 86, 241, .35f)))))
            Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(170.dp))
        }
        T(VacioAhora.titulo, 30f, 700, p.texto, alto = 1.08f, espaciado = px(-.6f, 30f), alinear = TextAlign.Center)
        T(VacioAhora.cuerpo(), 15f, 400, p.texto2, alto = 1.5f, alinear = TextAlign.Center)
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Cta("Armá un plan", { if (c.conDni(ir, "crear")) ir.crear() }, Modifier.fillMaxWidth(), alto = 58.dp, tam = 16f)
            T("Ver lo que hay mañana", 14f, 700, C.violetaTexto, modifier = Modifier.tocable { ir.principal(Pestana.Explorar) })
        }
    }
}

/** «Sólo chicas» como interruptor (no como otra categoría): borde y escudo apagado, lima con tilde prendido. */
@Composable
internal fun InterruptorChicas(on: Boolean, alCambiar: () -> Unit) {
    val p = LocalPaleta.current
    val acento = if (p.oscuro) C.lima else C.violeta
    Row(
        Modifier
            .then(
                if (on) Modifier.background(C.lima, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                else Modifier.border(1.5.dp, acento.copy(alpha = .4f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
            )
            .toggleable(value = on, role = Role.Switch, onValueChange = { alCambiar() })
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icono(if (on) I.check else I.escudo, 15.dp, if (on) C.carbon else acento, if (on) 3f else 2.2f)
        T("Sólo chicas", 14f, if (on) 700 else 600, if (on) C.carbon else (if (p.oscuro) C.lima else C.violetaTexto), unRenglon = true)
    }
}
