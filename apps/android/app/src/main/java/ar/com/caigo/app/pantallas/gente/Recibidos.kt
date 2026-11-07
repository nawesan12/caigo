package ar.com.caigo.app.pantallas.gente

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.R
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.Segmentado
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.PaletaClara
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import coil3.compose.AsyncImage

/**
 * "Te quieren conocer" (prototipo · likes): te sumaron / les interesás y te invitan, borroso sin Plus.
 * Es `VistaRecibidos` en iOS. Siempre con la paleta clara.
 */
@Composable
fun Recibidos(ir: Ir, modo: String, pestana: Int) {
    val c = LocalApp.current
    var tab by rememberSaveable { mutableIntStateOf(pestana) }
    val recibidos = rememberCarga(modo) { c.api.recibidos(modo) }
    val invitaciones = rememberCarga { c.api.invitaciones() }
    val esCitas = modo == "citas"
    val nR = recibidos.dato?.personas?.size ?: 0
    val nI = invitaciones.dato?.invitaciones?.size ?: 0
    val bloqueada = if (tab == 0) recibidos.dato?.bloqueada == true else invitaciones.dato?.bloqueada == true

    CompositionLocalProvider(LocalPaleta provides PaletaClara) {
    Pantalla(oscuro = false) { arriba, abajo ->
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = arriba + 10.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotonIcono(I.atras, { ir.atras() }, iconoTam = 20.dp, trazo = 2.6f, gel = Gels.neutra)
                    T("Te quieren conocer", 28f, 800, C.carbon, alto = 1f, espaciado = px(-.8f, 28f), unRenglon = true)
                }
                Segmentado(listOf("${if (esCitas) "Les interesás" else "Te sumaron"} · $nR", "Te invitan · $nI"), tab, { tab = it }, radio = 20.dp, radioOpcion = 16.dp, alto = 50.dp)
                T(
                    if (tab == 0) {
                        if (bloqueada) "$nR personas ya te sumaron. Con Plus ves quiénes son y las sumás de vuelta."
                        else "Tocá un perfil para verlo completo. Si sumás de vuelta, es mutuo y se abre el chat."
                    } else {
                        if (bloqueada) "$nI personas te quieren en sus planes. Con Plus ves quién organiza antes de sumarte."
                        else "Planes a los que te invitaron. Mirá quién organiza y sumate si te copa."
                    },
                    14f, 400, C.texto2, alto = 1.45f,
                )
            }
            val tarjetas: List<Triple<String, String, () -> Unit>>? = if (tab == 0) recibidos.dato?.personas?.map { r ->
                Triple(r.foto, if (bloqueada || r.nombre.isEmpty()) "Alguien cerca" else r.nombre + (r.edad?.let { ", $it" } ?: ""),
                    { if (bloqueada || r.id.isEmpty()) ir.plus("likes") else ir.persona(r.id, "recibido-$modo") })
            } else invitaciones.dato?.invitaciones?.map { v ->
                Triple(v.de?.foto?.ifEmpty { null } ?: v.foto, if (bloqueada) "Alguien cerca" else (v.de?.nombre ?: v.titulo),
                    { if (bloqueada || v.de?.id.isNullOrEmpty()) ir.plus("likes") else ir.persona(v.de!!.id, "invitacion:${v.planId}") })
            }
            val subs: List<String> = if (tab == 0) recibidos.dato?.personas?.map { (if (esCitas) "Le interesás · " else "Te sumó ") + Fecha.hace(it.cuando) }.orEmpty()
            else invitaciones.dato?.invitaciones?.map { "Te invitó " + Fecha.hace(it.cuando) }.orEmpty()
            val planes: List<String?> = if (tab == 0) emptyList() else invitaciones.dato?.invitaciones?.map { v ->
                if (v.titulo.isEmpty()) null else v.titulo.substringBefore(" en ") + " · " + Fecha.cuando(v.empiezaEn)
            }.orEmpty()
            when {
                tarjetas == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Puntitos() }
                tarjetas.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(96.dp))
                    T("Ya respondiste a todos", 22f, 700, C.carbon, alto = 1.15f, alinear = TextAlign.Center)
                    T("Cuando alguien te sume o te invite, aparece acá.", 14f, 400, C.texto2, alto = 1.5f, alinear = TextAlign.Center)
                }
                else -> LazyVerticalGrid(
                    GridCells.Fixed(2), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 160.dp + abajo),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tarjetas.size) { i ->
                        val (f, nombre, abrir) = tarjetas[i]
                        Box(
                            Modifier.fillMaxWidth().height(236.dp).toque(alBajar = 0.dp, onClick = abrir)
                                .gel(26.dp, null, listOf(externa(3f, 0f, C.borde1), externa(18f, 28f, rgba(60, 40, 140, .5f), spread = -16f)))
                                .clip(RoundedCornerShape(26.dp)).background(C.oscuro2),
                        ) {
                            // `blur` necesita Android 12; en los anteriores la foto queda nítida bajo el candado.
                            if (f.isNotEmpty()) AsyncImage(f, null, Modifier.fillMaxSize().then(if (bloqueada) Modifier.blur(14.dp) else Modifier), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to rgba(32, 29, 43, .25f), .35f to rgba(32, 29, 43, 0f), 1f to rgba(32, 29, 43, .85f))))
                            planes.getOrNull(i)?.let { pt ->
                                Box(Modifier.fillMaxWidth().padding(10.dp).background(rgba(250, 249, 246, .95f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                                    T(pt, 12f, 800, C.carbon, alto = 1.25f)
                                }
                            }
                            if (bloqueada) Box(
                                Modifier.align(Alignment.Center).size(46.dp).gel(23.dp, vertical(rgba(255, 255, 255, .2f), rgba(255, 255, 255, .2f)), listOf(interna(1f, 0f, rgba(255, 255, 255, .35f)))),
                                contentAlignment = Alignment.Center,
                            ) { Icono(I.candadoAbierto, 20.dp, C.blanco, 2.4f) }
                            Column(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                T(nombre, 16f, 800, C.blanco, unRenglon = true)
                                T(subs.getOrElse(i) { "" }, 12f, 600, Color(0xFFE4E0EE), unRenglon = true)
                            }
                        }
                    }
                }
            }
        }
        if (bloqueada && (if (tab == 0) nR else nI) > 0) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(0f to C.blancoCalido.copy(alpha = 0f), .4f to C.blancoCalido))
                .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 30.dp + abajo),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Cta("Ver quiénes son", { ir.plus("likes") }, Modifier.fillMaxWidth(), alto = 60.dp, radio = 22.dp)
            T("Con Caigo Plus · cancelás cuando quieras", 12f, 600, C.texto2)
        }
    }
    }
}
