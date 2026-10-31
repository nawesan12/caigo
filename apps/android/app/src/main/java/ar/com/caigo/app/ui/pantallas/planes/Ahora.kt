package ar.com.caigo.app.ui.pantallas.planes

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.LAT_MDP
import ar.com.caigo.app.LNG_MDP
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.datos.Invitaciones
import ar.com.caigo.app.datos.Plan
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.Pestana
import ar.com.caigo.app.ui.altoTabBar
import ar.com.caigo.app.ui.piezas.Avatar
import ar.com.caigo.app.ui.piezas.BotonIcono
import ar.com.caigo.app.ui.piezas.Categorias
import ar.com.caigo.app.ui.piezas.Chip
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.Segmentado
import ar.com.caigo.app.ui.piezas.rememberCarga
import ar.com.caigo.app.ui.piezas.rememberPermiso
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.background

/** Las categorías del filtro de Ahora (las del mockup 02). */
private val filtrosAhora = listOf(null to "Todos", "deporte" to "Deporte", "juego" to "Juego", "previa" to "Previa", "cita_doble" to "Cita doble", "salida" to "Salida", "taller" to "Taller")

/**
 * Ahora (02 lista · 03 mapa · 13a vacío · 14a oscuro) con los modos del prototipo: Planes, Gente y
 * Citas (Citas sólo si la persona eligió esa intención).
 */
@Composable
fun Ahora(ir: Ir, modoInicial: String?) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val yo by c.yo.collectAsState()
    var modo by rememberSaveable { mutableStateOf(modoInicial ?: "planes") }
    var mapa by rememberSaveable { mutableStateOf(false) }
    var categoria by rememberSaveable { mutableStateOf<String?>(null) }
    var punto by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var pidio by rememberSaveable { mutableStateOf(false) }
    val (permiso, pedir) = rememberPermiso(Manifest.permission.ACCESS_FINE_LOCATION)
    LaunchedEffect(Unit) { if (yo == null) c.recargarYo() }
    LaunchedEffect(permiso) {
        if (!permiso && !pidio) { pidio = true; pedir() }
        punto = c.ubicacion() ?: (LAT_MDP to LNG_MDP)
    }
    val conCitas = yo?.intenciones?.contains("citas") == true
    if (modo == "citas" && !conCitas && yo != null) modo = "planes"
    val planes = rememberCarga(punto, categoria) {
        val (la, ln) = punto ?: return@rememberCarga null
        c.api.ahora(la, ln, categoria).planes
    }
    val invitaciones = rememberCarga { runCatching { c.api.invitaciones() }.getOrDefault(Invitaciones()) }
    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    if (modo == "planes" && mapa) {
        MapaAhora(ir, planes.dato.orEmpty(), punto, categoria, { categoria = it }, onLista = { mapa = false })
        return
    }

    Box(Modifier.fillMaxSize()) {
        if (modo != "planes") {
            MazoGente(ir, modo, Modifier.padding(top = arriba + 68.dp))
        }
        Column(Modifier.padding(top = arriba + 6.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Ubicación y aviso de "te sumaron / te invitan".
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icono(I.pin, 16.dp, if (p.oscuro) C.lima else C.violeta, 2.2f)
                    val lugar = listOf(yo?.zona.orEmpty(), yo?.ciudad.orEmpty()).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Mar del Plata" }
                    T(lugar, 14f, 600, if (p.oscuro) C.apagado2 else C.texto2, unRenglon = true)
                }
                if (p.oscuro) {
                    T("Caigo", 25f, 700, C.blancoCalido, espaciado = ar.com.caigo.app.ui.tema.EspaciadoTitulo)
                } else Box {
                    BotonIcono(I.campana, { ir.recibidos(if (modo == "citas") "citas" else "gente", if (invitaciones.dato?.invitaciones?.isNotEmpty() == true) 1 else 0) },
                        tam = 42.dp, radio = 15.dp, iconoTam = 20.dp, trazo = 2f)
                    if (invitaciones.dato?.invitaciones?.isNotEmpty() == true) {
                        Box(Modifier.align(Alignment.TopEnd).offset((-10).dp, 9.dp).size(9.dp).background(C.coral, CircleShape)
                            .then(Modifier.background(C.coral, CircleShape)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(when (modo) { "gente" -> "Gente"; "citas" -> "Citas"; else -> "Ahora" }, 40f, 800, p.texto, alto = 1f, espaciado = px(-1.4f, 40f), unRenglon = true)
                Box(Modifier.weight(1f))
                val modos = listOfNotNull("planes" to "Planes", "gente" to "Gente", if (conCitas) "citas" to "Citas" else null)
                Segmentado(
                    modos.map { it.second }, modos.indexOfFirst { it.first == modo }.coerceAtLeast(0), { modo = modos[it].first },
                    radio = 14.dp, radioOpcion = 11.dp, alto = 42.dp, llenar = false, padOpcion = if (modos.size > 2) 10.dp else 13.dp,
                    gelOn = { if (modos[it].first == "citas") Gels.coralBadge else null },
                    colorOn = { if (modos[it].first == "citas") C.carbon else null },
                )
            }
            if (modo == "planes") {
                LazyRow(Modifier.padding(end = 0.dp).fillMaxWidth().offset(x = 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 20.dp, bottom = 6.dp)) {
                    items(filtrosAhora) { (k, l) ->
                        val on = k == categoria
                        Chip(
                            l, on, { categoria = k },
                            gelOn = if (p.oscuro) Gels.limaChico else Gels.oscuroSel,
                            gelOff = if (p.oscuro) Gels.chipOscuro else Gels.chipClaro,
                            colorOn = if (p.oscuro) C.carbon else C.blancoCalido,
                            colorOff = p.texto,
                        )
                    }
                }
            }
        }
        if (modo == "planes") {
            ListaAhora(ir, planes.dato, planes.cargando, planes.error, invitaciones.dato, Modifier.padding(top = arriba + 182.dp), { mapa = true }, { planes.recargar() })
        }
    }
}

@Composable
private fun ListaAhora(
    ir: Ir, planes: List<Plan>?, cargando: Boolean, error: String?, inv: Invitaciones?, modifier: Modifier,
    onMapa: () -> Unit, recargar: () -> Unit,
) {
    val p = LocalPaleta.current
    val abajo = altoTabBar()
    if (planes != null && planes.isEmpty() && !cargando) {
        VacioAhora(ir, modifier)
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = abajo),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val invs = inv?.invitaciones.orEmpty()
        if (invs.isNotEmpty()) item { BannerInvitaciones(ir, invs.size, inv!!.bloqueada, invs.mapNotNull { it.titulo.takeIf { t -> t.isNotBlank() } }, invs.map { it.de?.foto ?: it.foto }) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("Próximas 3 horas", 15f, 700, p.texto, unRenglon = true)
                    if (planes != null) T("${planes.size} ${if (planes.size == 1) "plan cerca" else "planes cerca"}", 13f, 400, p.texto2, unRenglon = true)
                }
                Segmentado(listOf("Lista", "Mapa"), 0, { if (it == 1) onMapa() }, radio = 14.dp, radioOpcion = 11.dp, alto = 40.dp, tam = 14f, llenar = false)
            }
        }
        if (planes == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    if (error != null) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        T(error, 15f, 500, p.texto2, alinear = TextAlign.Center)
                        Chip("Probar de nuevo", false, recargar)
                    } else Puntitos()
                }
            }
        } else items(planes, key = { it.id }) { TarjetaPlan(it, ir, alCambiar = { recargar() }) }
    }
}

/** El banner oscuro del prototipo: "3 personas te invitan a sus planes" (borroso sin Plus). */
@Composable
private fun BannerInvitaciones(ir: Ir, n: Int, bloqueada: Boolean, titulos: List<String>, fotos: List<String>) {
    Row(
        Modifier.fillMaxWidth().toque(alBajar = 0.dp) { ir.recibidos("gente", 1) }
            .gel(22.dp, Gels.oscuroSel.copy(sombras = listOf(ar.com.caigo.app.ui.tema.interna(1f, 0f, rgba(255, 255, 255, .08f)), externa(14f, 24f, rgba(36, 33, 45, .6f), spread = -14f))))
            .padding(start = 10.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(start = 8.dp)) {
            fotos.take(3).forEachIndexed { i, f ->
                Avatar("·", f, 36.dp, Modifier.offset(x = (-8 * i).dp).then(if (bloqueada) Modifier.blur(3.dp) else Modifier), colorBorde = C.carbon, fondo = C.oscuro2 to C.oscuro2)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(if (n == 1) "1 persona te invita a su plan" else "$n personas te invitan a sus planes", 14f, 800, C.blancoCalido)
            if (titulos.isNotEmpty()) T(titulos.joinToString(", ") { it.substringBefore(" en ") }, 12f, 600, Color(0xFFC9C4DA), unRenglon = true)
        }
        if (bloqueada) Box(Modifier.gel(11.dp, vertical(C.lima, C.lima)).padding(horizontal = 10.dp, vertical = 6.dp)) { T("Plus", 11f, 800, C.carbon) }
        Icono(I.adelante, 18.dp, Color(0xFFC9C4DA), 2.6f)
    }
}

/** 13a · No hay planes cerca. */
@Composable
private fun VacioAhora(ir: Ir, modifier: Modifier) {
    val p = LocalPaleta.current
    Column(
        modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(130.dp).rotate(-10f).gel(36.dp, null, listOf(externa(22f, 24f, rgba(116, 86, 241, .35f)))))
            Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(170.dp).rotate(-10f))
        }
        T("No hay planes cerca. ¿Armás uno?", 30f, 700, p.texto, alto = 1.08f, espaciado = px(-.6f, 30f), alinear = TextAlign.Center)
        T("Domingo a la siesta, la costa duerme. Si publicás algo ahora, sos lo primero que ven.", 15f, 400, p.texto2, alto = 1.5f, alinear = TextAlign.Center)
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Cta("Armá un plan", { ir.crear() }, Modifier.fillMaxWidth(), alto = 58.dp, tam = 16f)
            T("Ver lo que hay mañana", 14f, 700, C.violetaTexto, modifier = Modifier.tocable { ir.principal(Pestana.Explorar) })
        }
    }
}
