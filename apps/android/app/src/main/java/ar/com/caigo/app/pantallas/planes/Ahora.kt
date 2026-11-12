package ar.com.caigo.app.pantallas.planes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import ar.com.caigo.app.nucleo.Invitaciones
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.VacioAhora
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.pantallas.gente.MazoGente
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Las categorías del filtro de Ahora (las del mockup 02). */
private val filtrosAhora = listOf(
    null to "Todos", "deporte" to "Deporte", "juego" to "Juego", "previa" to "Previa",
    "cita_doble" to "Cita doble", "salida" to "Salida", "taller" to "Taller",
)

/** Lo que dispara una carga (el `Clave` de iOS). */
private data class ClaveAhora(
    val lat: Double?, val lng: Double?, val sinUbicacion: Boolean, val categoria: String?, val q: String?, val vuelta: Int,
)

/**
 * Ahora (02 lista · 03 mapa · 13a vacío · 14a oscuro) con los modos del prototipo: Planes, Gente y
 * Citas (Citas sólo si la persona eligió esa intención).
 */
@Composable
fun Ahora(ir: Ir, modoInicial: String?) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val yo by c.yo.collectAsState()
    var modo by rememberSaveable { mutableStateOf<String?>(null) }
    var mapa by rememberSaveable { mutableStateOf(false) }
    var categoria by rememberSaveable { mutableStateOf<String?>(null) }
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
    val (permiso, pedir) = rememberPermisoUbicacion()

    val conCitas = yo?.intenciones?.contains("citas") == true
    val m = modo ?: modoInicial ?: "planes"
    val modoActual = if (m == "citas" && !conCitas && yo != null) "planes" else m

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
    val q = if (mapa) Api.busqueda(busqueda) else null
    LaunchedEffect(ClaveAhora(punto?.first, punto?.second, sinUbicacion, categoria, q, vuelta)) {
        if (punto == null && !sinUbicacion) return@LaunchedEffect
        // Mientras se escribe en el buscador, se espera a que la persona pare (y la tarea anterior se cancela).
        if (q != null) delay(350)
        cargando = true
        try {
            val ciudad = if (punto == null) (yo?.ciudad?.ifEmpty { null } ?: "Mar del Plata") else null
            planes = c.api.ahora(punto?.first, punto?.second, categoria, q = q, ciudad = ciudad).planes
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

    if (modoActual == "planes" && mapa) {
        MapaAhora(ir, planes.orEmpty(), punto, categoria, { categoria = it }, busqueda, { busqueda = it }, onLista = { mapa = false })
        return
    }

    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        if (modoActual != "planes") {
            // Sin el renglón de ubicación y logo (ver encabezado): el mazo sube y gana lugar.
            MazoGente(ir, modoActual, Modifier.padding(top = arriba + 24.dp))
        }
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Encabezado(ir, modoActual, conCitas, lugarActual, invitaciones) { modo = it }
            if (modoActual == "planes") Chips(categoria) { categoria = it }
            if (modoActual == "planes") Box(Modifier.weight(1f)) {
                Lista(ir, planes, cargando, error, invitaciones, alMapa = { mapa = true }, recargar = { vuelta++ })
            }
        }
    }
}

/** "Güemes, Mar del Plata" de unas coordenadas (Geocoder, en es-AR). */
private suspend fun barrio(ctx: android.content.Context, la: Double, ln: Double): String? {
    val a = sitioEn(ctx, la, ln) ?: return null
    val vistas = listOfNotNull(a.subLocality, a.locality).filter { it.isNotEmpty() }.distinct()
    return if (vistas.isEmpty()) null else vistas.joinToString(", ")
}

@Composable
private fun Encabezado(ir: Ir, modoActual: String, conCitas: Boolean, lugarActual: String?, invitaciones: Invitaciones?, alModo: (String) -> Unit) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val yo by c.yo.collectAsState()
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // En Gente y Citas la ubicación y el logo se sacan: le roban lugar a la tarjeta (Nahuel, 1/11).
        if (modoActual == "planes") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        I.campana, { ir.recibidos(if (modoActual == "citas") "citas" else "gente", if (hay) 1 else 0) },
                        Modifier.semantics { contentDescription = "Te sumaron y te invitan" },
                        tam = 42.dp, radio = 15.dp, iconoTam = 20.dp, trazo = 2f,
                    )
                    if (hay) Box(Modifier.align(Alignment.TopEnd).offset((-10).dp, 9.dp).size(9.dp).background(C.coral, CircleShape))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(when (modoActual) { "gente" -> "Gente"; "citas" -> "Citas"; else -> "Ahora" }, 40f, 800, p.texto, alto = 1f, espaciado = px(-1.4f, 40f), unRenglon = true)
            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.weight(1f))
            val modos = listOf("planes" to "Planes", "gente" to "Gente") + if (conCitas) listOf("citas" to "Citas") else emptyList()
            Segmentado(
                modos.map { it.second }, maxOf(0, modos.indexOfFirst { it.first == modoActual }), { alModo(modos[it].first) },
                radio = 14.dp, radioOpcion = 11.dp, alto = 42.dp, llenar = false, padOpcion = if (modos.size > 2) 10.dp else 13.dp,
                gelOn = { if (modos[it].first == "citas") Gels.coralBadge else null },
                colorOn = { if (modos[it].first == "citas") C.carbon else null },
            )
        }
    }
}

@Composable
private fun Chips(categoria: String?, alElegir: (String?) -> Unit) {
    val p = LocalPaleta.current
    LazyRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
    ) {
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
    alMapa: () -> Unit, recargar: () -> Unit,
) {
    val p = LocalPaleta.current
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
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("Próximas 3 horas", 15f, 700, p.texto, unRenglon = true)
                        if (planes != null) T("${planes.size} ${if (planes.size == 1) "plan cerca" else "planes cerca"}", 13f, 400, p.texto2, unRenglon = true)
                    }
                    Spacer(Modifier.width(8.dp))
                    Segmentado(listOf("Lista", "Mapa"), 0, { if (it == 1) alMapa() }, radio = 14.dp, radioOpcion = 11.dp, alto = 40.dp, tam = 14f, llenar = false)
                }
            }
            when {
                planes != null -> items(planes, key = { it.id }) { TarjetaPlan(it, ir, alCambiar = { recargar() }) }
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
            Cta("Armá un plan", { ir.crear() }, Modifier.fillMaxWidth(), alto = 58.dp, tam = 16f)
            T("Ver lo que hay mañana", 14f, 700, C.violetaTexto, modifier = Modifier.tocable { ir.principal(Pestana.Explorar) })
        }
    }
}
