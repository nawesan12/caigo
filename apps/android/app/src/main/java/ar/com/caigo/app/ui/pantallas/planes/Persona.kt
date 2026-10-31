package ar.com.caigo.app.ui.pantallas.planes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.datos.Perfil
import ar.com.caigo.app.datos.Reporte
import ar.com.caigo.app.datos.Tarjeta
import ar.com.caigo.app.datos.mensajeDe
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.Fecha
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.Segmentado
import ar.com.caigo.app.ui.piezas.rememberCarga
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Perfil de persona (prototipo · pp). [desde] dice qué botones van abajo:
 * "gente" / "citas" (vino del mazo), "recibido-gente" / "recibido-citas" (te sumó / le interesás),
 * "invitacion:<planId>" (te invita a un plan) o "perfil" (desde un plan o tu gente).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerfilPersona(ir: Ir, id: String, desde: String) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val persona = rememberCarga(id) { c.api.persona(id) }
    var foto by remember { mutableIntStateOf(0) }
    var opciones by remember { mutableStateOf(false) }
    var reportando by remember { mutableStateOf(false) }
    var invitando by remember { mutableStateOf(false) }
    val esCitas = desde.endsWith("citas")
    val planInvita = desde.removePrefix("invitacion:").takeIf { desde.startsWith("invitacion:") }

    fun accion(f: suspend () -> Unit) = alcance.launch { runCatching { f() }.onFailure { avisos.error(mensajeDe(it)) } }

    Pantalla(oscuro = false) { arribaP, abajo ->
        val p = persona.dato
        if (p == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (persona.error != null) T(persona.error!!, 16f, 600, C.carbon, alinear = TextAlign.Center, modifier = Modifier.padding(30.dp)) else Puntitos()
            }
            return@Pantalla
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 150.dp + abajo)) {
            Box(Modifier.fillMaxWidth().height(540.dp).background(C.oscuro2)) {
                AsyncImage(p.fotos.getOrNull(foto) ?: p.fotos.firstOrNull(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to rgba(32, 29, 43, .5f), .2f to rgba(32, 29, 43, 0f), .55f to rgba(32, 29, 43, 0f), 1f to rgba(32, 29, 43, .9f))))
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(.4f).fillMaxSize().tocable { foto = (foto - 1).coerceAtLeast(0) })
                    Box(Modifier.weight(.6f).fillMaxSize().tocable { foto = (foto + 1).coerceAtMost((p.fotos.size - 1).coerceAtLeast(0)) })
                }
                val arriba = arribaP
                Row(Modifier.fillMaxWidth().padding(top = arriba + 4.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    p.fotos.forEachIndexed { i, _ -> Box(Modifier.weight(1f).height(4.dp).background(if (i == foto) C.blanco else rgba(255, 255, 255, .4f), RoundedCornerShape(2.dp))) }
                }
                Box(
                    Modifier.padding(top = arriba + 20.dp, start = 16.dp).size(42.dp).toque { ir.atras() }
                        .background(rgba(32, 29, 43, .45f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icono(if (desde == "gente" || desde == "citas") I.abajo else I.atras, 20.dp, C.blanco, 2.8f) }
                val badge = when {
                    planInvita != null -> "Te invita a un plan"
                    desde.startsWith("recibido") -> if (esCitas) "Le interesás" else "Te sumó"
                    else -> null
                }
                if (badge != null) Box(
                    Modifier.align(Alignment.TopEnd).padding(top = arriba + 26.dp, end = 16.dp).background(C.lima, RoundedCornerShape(13.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                ) { T(badge, 13f, 800, C.carbon) }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(p.nombre + (p.edad?.let { ", $it" } ?: ""), 34f, 800, C.blanco, alto = 1f, espaciado = px(-.8f, 34f), unRenglon = true)
                        if (p.verificada) Verificado()
                    }
                    val sub = listOf(p.trabajo, p.zona).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotBlank()) T(sub, 14f, 600, Color(0xFFE4E0EE))
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (p.compartidos > 0) Box(Modifier.background(C.lima, RoundedCornerShape(13.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    T(if (p.compartidos == 1) "1 plan en común" else "${p.compartidos} planes en común", 13f, 800, C.carbon)
                }
                if (p.bio.isNotBlank()) TarjetaPerfil {
                    T("SOBRE MÍ", 12f, 800, C.texto2, espaciado = px(1.4f, 12f))
                    T(p.bio, 15f, 400, C.carbon, alto = 1.5f)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Estadistica("${p.planes}", "planes", Modifier.weight(1f))
                    Estadistica(if (p.asistencia.de > 0) "${(p.asistencia.fue * 100 + p.asistencia.de / 2) / p.asistencia.de}%" else "—", "asistencia", Modifier.weight(1f))
                    Estadistica(if (p.verificada) "DNI" else "Selfie", "verificado", Modifier.weight(1f))
                }
                p.prompts.filter { it.r.isNotBlank() }.forEach { pr ->
                    TarjetaPerfil {
                        T(pr.p, 13f, 700, C.texto2)
                        T(pr.r, 22f, 700, C.carbon, alto = 1.2f, espaciado = px(-.4f, 22f))
                    }
                }
                if (p.intereses.isNotEmpty()) TarjetaPerfil {
                    T("LE COPA", 12f, 800, C.texto2, espaciado = px(1.4f, 12f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.intereses.forEach { g -> Box(Modifier.gel(14.dp, Gels.off).padding(horizontal = 13.dp, vertical = 8.dp)) { T(g, 13f, 700, C.carbon) } }
                    }
                }
                T("Reportar o bloquear a ${p.nombre}", 13f, 700, C.texto2, modifier = Modifier.align(Alignment.CenterHorizontally).tocable { opciones = true }.padding(8.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp + abajo).background(Brush.verticalGradient(0f to C.blancoCalido.copy(alpha = 0f), .6f to C.blancoCalido)))
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 34.dp + abajo),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            val modo = if (esCitas) "citas" else "gente"
            when {
                desde == "gente" || desde == "citas" -> {
                    BotonRedondo(60.dp, gelPaso, Modifier, { accion { c.api.reaccionar(p.id, modo, "paso"); ir.atras() } }) { Icono(I.cruz, 24.dp, C.coral2, 3.2f) }
                    if (esCitas) {
                        BotonRedondo(76.dp, gelCorazon, Modifier, { accion { reaccionarYAvisar(c, avisos, p, modo, "interesa"); ir.atras() } }) { Icono(I.corazon, 34.dp, Color.Transparent, 0f, relleno = C.blanco) }
                        BotonRedondo(60.dp, gelVioletaRedondo, Modifier, { accion { reaccionarYAvisar(c, avisos, p, modo, "encanta"); ir.atras() } }) { Icono(I.estrella, 26.dp, Color.Transparent, 0f, relleno = C.lima) }
                    } else {
                        BotonRedondo(76.dp, Gels.limaChico, Modifier, { accion { reaccionarYAvisar(c, avisos, p, modo, "prendo"); ir.atras() } }) { Icono(I.mas, 32.dp, C.carbon, 3.4f) }
                        BotonRedondo(60.dp, gelVioletaRedondo, Modifier, { invitando = true }) { Icono(I.calendarioSimple, 24.dp, C.blanco, 2.4f) }
                    }
                }
                planInvita != null || desde.startsWith("recibido") -> {
                    BotonRedondo(60.dp, gelPaso, Modifier, {
                        accion {
                            if (planInvita != null) c.api.responderInvitacion(planInvita, false) else c.api.descartarRecibido(p.id, modo)
                            ir.atras()
                        }
                    }) { Icono(I.cruz, 24.dp, C.coral2, 3.2f) }
                    Cta(
                        when { planInvita != null -> "Me sumo al plan"; esCitas -> "Me interesa también"; else -> "Sumar de vuelta" },
                        {
                            accion {
                                if (planInvita != null) {
                                    c.api.responderInvitacion(planInvita, true)
                                    avisos.ok("Te sumaste al plan. ${p.nombre} ya sabe que vas.")
                                    ir.plan(planInvita)
                                } else {
                                    reaccionarYAvisar(c, avisos, p, modo, if (esCitas) "interesa" else "prendo")
                                    ir.atras()
                                }
                            }
                        },
                        Modifier.weight(1f), alto = 62.dp, radio = 22.dp, icono = I.mas, iconoTrazo = 4f, iconoTam = 18.dp,
                    )
                }
                else -> Cta("Invitar a un plan", { invitando = true }, Modifier.fillMaxWidth(), gel = Gels.violeta, color = C.blanco, alto = 58.dp, icono = I.calendarioSimple, iconoTrazo = 2.4f, iconoTam = 20.dp)
            }
        }
        if (invitando) HojaInvitar(ir, Tarjeta(p.id, p.nombre), { invitando = false }) { plan ->
            avisos.ok("Invitaste a ${p.nombre} a ${plan.titulo}. Le llega como solicitud.")
        }
        if (opciones) HojaOpciones({ opciones = false }, listOf(
            "Reportar a ${p.nombre}" to { reportando = true },
            "Bloquear a ${p.nombre}" to {
                accion { c.api.bloquear(p.id); avisos.ok("Bloqueaste a ${p.nombre}. No se van a ver más."); ir.atras() }
            },
        ))
        if (reportando) HojaReporte({ reportando = false }) { motivo ->
            accion { c.api.reportar(Reporte(usuarioId = p.id, motivo = motivo)); avisos.ok("Gracias. Revisamos el perfil de ${p.nombre} en menos de 24 h.") }
        }
    }
}

private suspend fun reaccionarYAvisar(c: ar.com.caigo.app.Contenedor, avisos: ar.com.caigo.app.ui.piezas.Avisos, p: Perfil, modo: String, accion: String) {
    val r = c.api.reaccionar(p.id, modo, accion)
    avisos.ok(
        when {
            r.mutuo -> "¡Es mutuo con ${p.nombre}! Ya pueden hablar."
            accion == "encanta" -> "Le mandaste un «me encanta» a ${p.nombre}. Si es mutuo, se abre el chat."
            modo == "citas" -> "Le avisamos a ${p.nombre} que te interesa. Si es mutuo, se abre el chat."
            else -> "${p.nombre} se suma a tu gente. Le van a llegar tus próximos planes."
        },
    )
}

@Composable
private fun TarjetaPerfil(contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().gel(24.dp, Gels.neutra).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contenido() }
}

@Composable
private fun Estadistica(v: String, l: String, modifier: Modifier) {
    Column(modifier.gel(20.dp, Gels.neutra).padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(v, 22f, 800, C.violetaTexto, alto = 1f)
        T(l, 12f, 700, C.texto2)
    }
}

/** "Te quieren conocer" (prototipo · likes): te sumaron / les interesás y te invitan, borroso sin Plus. */
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

    Pantalla(oscuro = false) { arriba, abajo ->
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = arriba + 10.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).toque { ir.atras() }.gel(16.dp, Gels.neutra), contentAlignment = Alignment.Center) { Icono(I.atras, 20.dp, C.carbon, 2.6f) }
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
                Triple(r.foto, if (bloqueada || r.nombre.isBlank()) "Alguien cerca" else r.nombre + (r.edad?.let { ", $it" } ?: ""),
                    { if (bloqueada || r.id.isBlank()) ir.plus("likes") else ir.persona(r.id, "recibido-$modo") })
            } else invitaciones.dato?.invitaciones?.map { v ->
                Triple(v.de?.foto ?: v.foto, if (bloqueada) "Alguien cerca" else (v.de?.nombre ?: v.titulo),
                    { if (bloqueada || v.de?.id.isNullOrBlank()) ir.plus("likes") else ir.persona(v.de!!.id, "invitacion:${v.planId}") })
            }
            val subs: List<String> = if (tab == 0) recibidos.dato?.personas?.map { (if (esCitas) "Le interesás · " else "Te sumó ") + Fecha.hace(it.cuando) }.orEmpty()
            else invitaciones.dato?.invitaciones?.map { "Te invitó " + Fecha.hace(it.cuando) }.orEmpty()
            val planes: List<String?> = if (tab == 0) emptyList() else invitaciones.dato?.invitaciones?.map { v ->
                if (v.titulo.isBlank()) null else v.titulo.substringBefore(" en ") + " · " + Fecha.cuando(v.empiezaEn)
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
                            AsyncImage(f, null, Modifier.fillMaxSize().then(if (bloqueada) Modifier.blur(14.dp) else Modifier), contentScale = ContentScale.Crop)
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
