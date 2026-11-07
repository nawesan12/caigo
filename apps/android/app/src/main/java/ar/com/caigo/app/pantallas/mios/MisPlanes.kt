package ar.com.caigo.app.pantallas.mios

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
import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.pantallas.planes.ChipDestacado
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.Pestana
import ar.com.caigo.app.app.altoTabBar
import ar.com.caigo.app.piezas.Fecha
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.Segmentado
import ar.com.caigo.app.piezas.ZonaAR
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

    Pantalla { arriba, _ ->
        Column(Modifier.fillMaxSize().padding(top = arriba + 8.dp)) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                T("Mis planes", 40f, 800, alto = 1f, espaciado = px(-1.4f, 40f))
                Segmentado(
                    listOf(etiqueta("Organizo", organizo.dato?.size), etiqueta("Me sumé", meSume.dato?.size), "Historial"),
                    tab, { tab = it }, Modifier.fillMaxWidth(), alto = 46.dp, padOpcion = 4.dp,
                )
            }
            val planes = actual.dato
            when {
                planes == null && actual.error != null -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    MiosVacio("No pudimos cargar tus planes", actual.error!!, "Probar de nuevo", { actual.recargar() })
                }
                planes == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Puntitos() }
                planes.isEmpty() -> Box(Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(bottom = altoTabBar()), contentAlignment = Alignment.Center) {
                    when (tab) {
                        0 -> MiosVacio("Todavía no armaste ningún plan", "Armá uno en menos de 30 segundos. Siempre hay alguien que se prende.", "Armá un plan", { ir.crear() })
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
                        grupos.forEach { (titulo, lista) ->
                            if (titulo.isNotEmpty()) item(key = "t-$titulo") { MiosOverline(titulo) }
                            items(lista, key = { "${cuales[tab]}-${it.id}" }) { plan ->
                                TarjetaMio(plan, cuales[tab], ir)
                            }
                        }
                    }
                }
            }
        }
    }
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
private fun subtitulo(pl: Plan): String {
    val t = Fecha.parse(pl.empiezaEn)
    val cuando = when {
        t == null -> ""
        t.toLocalDate() == Fecha.hoy() -> Fecha.hora(t)
        else -> "${Fecha.diaCorto(t)} ${Fecha.hora(t)}"
    }
    val donde = if (pl.recurrente) "se repite cada semana" else pl.lugar?.nombre ?: pl.lugarNombre.ifBlank { pl.zona }
    return listOf(cuando, donde).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun TarjetaMio(pl: Plan, cual: String, ir: Ir) {
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
            Foto(pl.foto, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(pl.titulo, 18f, 700, alto = 1.15f)
                T(subtitulo(pl), 12f, 400, p.texto2, unRenglon = true)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (cual) {
                        "me_sume" -> if (pl.miEstado == "aceptada") {
                            MiosPill("Adentro", Brush.verticalGradient(listOf(C.lima, C.lima)), C.carbon, tam = 15f)
                        } else {
                            MiosPill("Esperando", Brush.verticalGradient(listOf(C.tinte1, C.tinte1)), C.violetaTexto, tam = 15f)
                        }
                        "historial" -> T("¿Repetimos?", 12f, 700, if (p.oscuro) C.lima else C.violetaTexto, Modifier.tocable { ir.repetimos(pl.id) })
                        else -> {
                            BadgeMio(pl, urgente = hoy)
                            // Destacado con Plus: sólo la estrella del chip; la palabra no entra junto a «Compartir link».
                            if (pl.destacado) ChipDestacado(soloEstrella = true)
                            if (!Cupo.completo(pl.faltan) && !hoy) {
                                T(
                                    "Compartir link", 12f, 700, if (p.oscuro) C.lima else C.violetaTexto,
                                    Modifier.tocable { compartir(ctx, "${pl.titulo} · ${BuildConfig.SITIO.trimEnd('/')}/p/${pl.slug}") },
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
