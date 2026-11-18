package ar.com.caigo.app.pantallas.mios

import ar.com.caigo.app.pantallas.planes.HojaTodoBien
import ar.com.caigo.app.pantallas.planes.HojaConfirmar
import ar.com.caigo.app.pantallas.planes.fotoDe
import ar.com.caigo.app.piezas.colorDeCategoria
import ar.com.caigo.app.pantallas.pagos.Adentro
import ar.com.caigo.app.pantallas.pagos.Plus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ar.com.caigo.app.nucleo.linkPlan
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.pantallas.planes.ChipDestacado
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.nucleo.Moderacion
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.Pestana
import ar.com.caigo.app.app.altoTabBar
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.Segmentado
import ar.com.caigo.app.nucleo.ZonaAR
import ar.com.caigo.app.piezas.compartir
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.vertical
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private val cuales = listOf("organizo", "me_sume", "historial")

/** 10 · Mis planes: Organizo / Me sumé / Historial, agrupados en HOY, ESTA SEMANA y más adelante. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisPlanes(ir: Ir) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val organizo = rememberCarga("organizo") { c.api.mios("organizo").planes }
    val meSume = rememberCarga("me_sume") { c.api.mios("me_sume").planes }
    val historial = rememberCarga("historial") { c.api.mios("historial").planes }
    val cargas = listOf(organizo, meSume, historial)
    val actual = cargas[tab]

    fun etiqueta(l: String, n: Int?) = if (n != null && n > 0) "$l · $n" else l
    val alcance = rememberCoroutineScope()
    val avisos = LocalAvisos.current
    // «Repetir» del historial: se confirma con la fecha antes de publicar.
    var aRepetir by remember { mutableStateOf<Plan?>(null) }
    // El plan de hoy (fijo arriba, con «Llegué» y «¿Todo bien?»), de lo que ya se trajo, y su hoja.
    val hoyPlan = (organizo.dato.orEmpty() + meSume.dato.orEmpty())
        .filter { (it.miEstado == "aceptada" || it.miEstado == "organizador") && ar.com.caigo.app.nucleo.Dia.enElDia(it) }
        .minByOrNull { it.empiezaEn }
    var hojaHoy by remember { mutableStateOf(false) }
    var refrescando by remember { mutableStateOf(false) }
    // .refreshable: recarga las tres pestañas (los datos viejos quedan si falla).
    fun refrescar() {
        alcance.launch {
            refrescando = true
            try {
                coroutineScope { cargas.forEach { k -> launch { k.correr() } } }
            } finally {
                refrescando = false
            }
        }
    }

    Pantalla { arriba, _ ->
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = arriba + 8.dp)) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                T("Mis planes", 40f, 800, alto = 1f, espaciado = px(-1.4f, 40f), unRenglon = true)
                Segmentado(
                    listOf(etiqueta("Organizo", organizo.dato?.size), etiqueta("Me sumé", meSume.dato?.size), "Historial"),
                    tab, { tab = it }, Modifier.fillMaxWidth(), alto = 46.dp, padOpcion = 4.dp,
                )
            }
            val planes = actual.dato
            PullToRefreshBox(refrescando, { refrescar() }, Modifier.fillMaxSize()) {
            when {
                planes == null && actual.error != null -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    MiosVacio("No pudimos cargar tus planes", actual.error!!, "Probar de nuevo", { actual.recargar() })
                }
                planes == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Puntitos(p.acento) }
                planes.isEmpty() -> Box(Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(bottom = altoTabBar()), contentAlignment = Alignment.Center) {
                    when (tab) {
                        0 -> MiosVacio("Todavía no armaste ningún plan", "Armá uno en menos de 30 segundos. Siempre hay alguien que se prende.", "Armá un plan", { if (c.conDni(ir, "crear")) ir.crear() })
                        1 -> MiosVacio("Todavía no te sumaste a nada", "Hay una mesa de truco a la que le falta uno. Y sabemos que te la sabés.", "Ver planes cerca", { ir.principal(Pestana.Ahora) })
                        else -> MiosVacio("Todavía no fuiste a ningún plan", "Los planes que pasaron quedan acá, para repetirlos.", "Ver planes cerca", { ir.principal(Pestana.Ahora) })
                    }
                }
                else -> {
                    val grupos = if (tab == 2) listOf("" to planes) else agrupar(planes)
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = altoTabBar()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (hoyPlan != null && tab != 2) item(key = "hoy") { ar.com.caigo.app.pantallas.planes.HoyTarjeta(hoyPlan, ir) { hojaHoy = true } }
                        grupos.forEach { (titulo, lista) ->
                            if (titulo.isNotEmpty()) item(key = "t-$titulo") { MiosOverline(titulo) }
                            items(lista, key = { "${cuales[tab]}-${it.id}" }) { plan ->
                                TarjetaMio(plan, cuales[tab], ir, repetir = { aRepetir = plan })
                            }
                        }
                    }
                }
            }
            }
        }
        aRepetir?.let { pl ->
            HojaConfirmar(
                true, "¿Repetimos «${pl.titulo}»?", "${proximaFecha(pl)}. Se publica igual que la última vez.", "Publicar",
                cerrar = { aRepetir = null },
            ) {
                c.api.repetimos(pl.id, true, emptyList(), null).nuevo?.let {
                    aRepetir = null
                    avisos.ok("Listo, se repite. Ya está publicado.")
                    ir.plan(it.id)
                }
            }
        }
        hoyPlan?.let { h ->
            HojaTodoBien(
                ir, h, hojaHoy, cerrar = { hojaHoy = false }, reportar = { ir.plan(h.id) }, recargar = { refrescar() },
            )
        }
        }
    }
}

/** El mismo día y hora, la semana que viene (o la próxima que no pasó): «Jue 27/11 21:30». */
private fun proximaFecha(pl: Plan): String {
    var t = Fecha.parse(pl.empiezaEn) ?: return ""
    val ahora = java.time.ZonedDateTime.now(ar.com.caigo.app.nucleo.ZonaAR)
    do { t = t.plusWeeks(1) } while (!t.isAfter(ahora))
    return Fecha.cuando(t.toOffsetDateTime().toString())
}

/** HOY / ESTA SEMANA / MÁS ADELANTE según la fecha del plan. */
private fun agrupar(planes: List<Plan>): List<Pair<String, List<Plan>>> {
    val hoy = Fecha.hoy()
    val g = planes.groupBy { pl ->
        val d = Fecha.parse(pl.empiezaEn)?.toLocalDate() ?: hoy
        val dias = ChronoUnit.DAYS.between(hoy, d)
        when {
            dias <= 0 -> "HOY"
            dias < 7 -> "ESTA SEMANA"
            else -> "MÁS ADELANTE"
        }
    }
    return listOf("HOY", "ESTA SEMANA", "MÁS ADELANTE").mapNotNull { k -> g[k]?.let { k to it } }
}

/** "20:30 · Cancha Los Pinos" hoy; "Jue 21:30 · se repite cada semana" otro día. */
internal fun miosSubtitulo(pl: Plan): String {
    val t = Fecha.parse(pl.empiezaEn)
    val cuando = when {
        t == null -> ""
        t.toLocalDate() == Fecha.hoy() -> Fecha.hora(t)
        Fecha.diasEntre(java.time.ZonedDateTime.now(ar.com.caigo.app.nucleo.ZonaAR), t) in 1..6 -> "${Fecha.diaCorto(t)} ${Fecha.hora(t)}"
        // Fuera de esta semana, con el día y el mes (Fecha.cuando).
        else -> Fecha.cuando(pl.empiezaEn)
    }
    val donde = if (pl.recurrente) "se repite cada semana" else pl.lugar?.nombre ?: pl.lugarNombre.ifEmpty { pl.zona }
    return listOf(cuando, donde).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** El link público del plan para compartir. */
internal fun miosLinkCompartir(pl: Plan): String = "${pl.titulo} · https://${linkPlan(pl.slug)}"

@Composable
private fun TarjetaMio(pl: Plan, cual: String, ir: Ir, repetir: () -> Unit = {}) {
    val p = LocalPaleta.current
    val ctx = LocalContext.current
    val franja = cual == "organizo" && pl.pendientes > 0
    val hoy = Fecha.parse(pl.empiezaEn)?.toLocalDate() == Fecha.hoy()
    Column(
        Modifier.fillMaxWidth()
            .gel(24.dp, if (franja) (if (p.oscuro) Gels.tarjetaOscura else Gels.tarjeta) else miosGelTarjeta())
            .clip(RoundedCornerShape(24.dp))
            .tocable { ir.plan(pl.id) },
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Foto(fotoDe(pl), Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)), colorDeCategoria(pl.categoria), pl.categoria)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(pl.titulo, 18f, 700, alto = 1.15f)
                T(miosSubtitulo(pl), 12f, 400, p.texto2, unRenglon = true)
                // Moderación automática: el organizador ve si su plan está en revisión u oculto.
                if (pl.esMio) Moderacion.etiqueta(pl.moderacion)?.let { T(it, 12f, 700, if (pl.moderacion == "oculto") C.coral2 else p.texto2, unRenglon = true) }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (cual) {
                        "me_sume" -> if (pl.miEstado == "aceptada") {
                            MiosPill("Adentro", Brush.verticalGradient(listOf(C.lima, C.lima)), C.carbon, tam = 15f)
                        } else {
                            MiosPill("Esperando", Brush.verticalGradient(listOf(C.tinte1, C.tinte1)), C.violetaTexto, tam = 15f)
                        }
                        "historial" -> {
                            val enlace = if (p.oscuro) C.lima else C.violetaTexto
                            // Un link chico con 48 dp de alto para el dedo (sin agrandar el renglón).
                            val toque = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 4.dp)
                            // «Repetir» (quien organizó, con confirmación), «¿Cómo estuvo?» / «Tu gente» y las fotos, siempre.
                            if (pl.esMio && !pl.cancelado && pl.lugar == null) {
                                T("Repetir", 12f, 700, enlace, Modifier.tocable(onClick = repetir).then(toque))
                                T("·", 12f, 700, p.texto2)
                            }
                            if (!pl.cancelado) {
                                T(
                                    if (pl.esMio || pl.mio?.carita != null) "Tu gente" else "¿Cómo estuvo?", 12f, 700, enlace,
                                    Modifier.tocable { ir.repetimos(pl.id) }.then(toque),
                                )
                                T("·", 12f, 700, p.texto2)
                                val n = pl.mio?.fotos ?: 0
                                T(if (n > 0) "Fotos · $n" else "Fotos", 12f, 700, enlace, Modifier.tocable { ir.fotosPlan(pl.id) }.then(toque))
                            }
                        }
                        else -> {
                            BadgeMio(pl, urgente = hoy)
                            // Destacado con Plus: sólo la estrella del chip; la palabra no entra junto a «Compartir link».
                            if (pl.destacado) ChipDestacado(Modifier.semantics { contentDescription = "Destacado" }, soloEstrella = true)
                            if (!Cupo.completo(pl.faltan) && !hoy) {
                                T(
                                    "Compartir link", 12f, 700, if (p.oscuro) C.lima else C.violetaTexto,
                                    Modifier.tocable { compartir(ctx, miosLinkCompartir(pl)) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (franja) {
            Row(
                Modifier.fillMaxWidth().background(C.violeta).tocable { ir.solicitudes(pl.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val n = pl.pendientes
                T(if (n == 1) "1 persona quiere sumarse" else "$n personas quieren sumarse", 14f, 700, C.blanco, Modifier.weight(1f))
                Icono(I.flecha, 18.dp, C.lima, 2.6f)
            }
        }
    }
}

/** Badge del plan propio: coral si es hoy, lavanda si es más adelante, lima "Ya somos N" si está completo. */
@Composable
private fun BadgeMio(pl: Plan, urgente: Boolean) {
    val (gel, texto) = when {
        Cupo.completo(pl.faltan) -> Gels.limaChico to "Ya somos ${pl.somos}"
        urgente -> Gels.coralBadge to Cupo.badge(pl.faltan)
        else -> Gels.chipClaro to Cupo.badge(pl.faltan)
    }
    Box(Modifier.gel(10.dp, gel).padding(horizontal = 10.dp, vertical = 5.dp)) {
        T(texto, 15f, 700, C.carbon, alto = 1f, unRenglon = true)
    }
}
