package ar.com.caigo.app.pantallas.gente

import ar.com.caigo.app.piezas.colorDeCategoria
import ar.com.caigo.app.piezas.asistencia
import ar.com.caigo.app.piezas.Verificado
import ar.com.caigo.app.piezas.HojaReporte
import ar.com.caigo.app.piezas.HojaOpciones

import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.piezas.Foto
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
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.nucleo.Perfil
import ar.com.caigo.app.nucleo.Reporte
import ar.com.caigo.app.nucleo.Tarjeta
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.Segmentado
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.nucleo.Categorias
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.PaletaClara

/**
 * Perfil de persona (prototipo · pp). [desde] dice qué botones van abajo:
 * "gente" / "citas" (vino del mazo), "recibido-gente" / "recibido-citas" (te sumó / le interesás),
 * "invitacion:<planId>" (te invita a un plan) o "perfil" (desde un plan o tu gente).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerfilPersona(ir: Ir, id: String, desde: String) {
    // Siempre con la paleta clara (como `.environment(\.paleta, .clara)` de iOS).
    CompositionLocalProvider(LocalPaleta provides PaletaClara) { PerfilPersonaClaro(ir, id, desde) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerfilPersonaClaro(ir: Ir, id: String, desde: String) {
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

    fun accion(f: suspend () -> Unit) = alcance.launch {
        try {
            f()
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            avisos.error(mensajeDe(e))
        }
    }
    val modo = if (esCitas) "citas" else "gente"

    Pantalla(oscuro = false) { arribaP, abajo ->
        val p = persona.dato
        if (p == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (persona.error != null) T(persona.error!!, 16f, 600, C.carbon, alinear = TextAlign.Center, modifier = Modifier.padding(30.dp)) else Puntitos()
            }
            BotonIcono(I.atras, { ir.atras() }, Modifier.padding(start = 16.dp, top = arribaP + 8.dp))
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
                        .background(rgba(32, 29, 43, .45f), RoundedCornerShape(15.dp))
                        .semantics { contentDescription = "Volver"; role = Role.Button },
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
                    val sub = listOf(p.trabajo, p.zona).filter { it.isNotEmpty() }.joinToString(" · ")
                    if (sub.isNotEmpty()) T(sub, 14f, 600, Color(0xFFE4E0EE))
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (p.compartidos > 0) Box(Modifier.background(C.lima, RoundedCornerShape(13.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    T(if (p.compartidos == 1) "1 plan en común" else "${p.compartidos} planes en común", 13f, 800, C.carbon)
                }
                if (p.bio.isNotEmpty()) TarjetaPerfil {
                    T("SOBRE MÍ", 12f, 800, C.texto2, espaciado = px(1.4f, 12f))
                    T(p.bio, 15f, 400, C.carbon, alto = 1.5f)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Estadistica("${p.planes}", "planes", Modifier.weight(1f))
                    Estadistica(if (p.asistencia.de > 0) "${(p.asistencia.fue * 100 + p.asistencia.de / 2) / p.asistencia.de}%" else "—", "asistencia", Modifier.weight(1f))
                    Estadistica(if (p.verificada) "DNI" else "Selfie", "verificado", Modifier.weight(1f))
                }
                p.prompts.filter { it.r.isNotEmpty() }.forEach { pr ->
                    TarjetaPerfil {
                        T(pr.p, 13f, 700, C.texto2)
                        T(pr.r, 22f, 700, C.carbon, alto = 1.2f, espaciado = px(-.4f, 22f))
                    }
                }
                if (p.intereses.isNotEmpty()) TarjetaPerfil {
                    T("LE COPA", 12f, 800, C.texto2, espaciado = px(1.4f, 12f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.intereses.forEach { g -> Box(Modifier.gel(14.dp, Gels.off).padding(horizontal = 13.dp, vertical = 8.dp)) { T(g, 13f, 700, C.carbon, unRenglon = true) } }
                    }
                }
                // Sus próximos planes (contrato §5), con la fila del prototipo (pp · ppPlans).
                if (p.proximos.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("Sus próximos planes", 15f, 800, C.carbon)
                    p.proximos.forEach { pr ->
                        Row(
                            Modifier.fillMaxWidth().toque(alBajar = 0.dp) { ir.plan(pr.id) }
                                .semantics(mergeDescendants = true) { role = Role.Button }.gel(22.dp, Gels.neutra)
                                .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Foto(pr.foto, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), colorDeCategoria(pr.categoria))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                T(pr.titulo, 15f, 800, C.carbon, unRenglon = true)
                                T(listOf(Fecha.cuando(pr.empiezaEn), Categorias.nombre(pr.categoria)).filter { it.isNotEmpty() }.joinToString(" · "), 12f, 600, C.texto2, unRenglon = true)
                            }
                            Icono(I.adelante, 18.dp, Color(0xFF9A95AD), 2.6f)
                        }
                    }
                }
                T(
                    "Reportar o bloquear a ${p.nombre}", 13f, 700, C.texto2,
                    modifier = Modifier.fillMaxWidth().tocable { opciones = true }.padding(8.dp), alinear = TextAlign.Center,
                )
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp + abajo).background(Brush.verticalGradient(0f to C.blancoCalido.copy(alpha = 0f), .6f to C.blancoCalido)))
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 34.dp + abajo),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                desde == "gente" || desde == "citas" -> {
                    GenteBotonRedondo(60.dp, genteGelPaso, "Paso", accion = { accion { c.api.reaccionar(p.id, modo, "paso"); ir.atras() } }) {
                        Icono(I.cruz, 24.dp, C.coral2, 3.2f)
                    }
                    if (esCitas) {
                        GenteBotonRedondo(76.dp, genteGelCorazon, "Me interesa", accion = { accion { reaccionarYAvisar(c, avisos, p, modo, "interesa"); ir.atras() } }) {
                            Icono(I.corazon, 34.dp, Color.Transparent, 0f, relleno = C.blanco)
                        }
                        GenteBotonRedondo(60.dp, genteGelVioletaRedondo, "Me encanta", accion = {
                            accion {
                                try {
                                    reaccionarYAvisar(c, avisos, p, modo, "encanta"); ir.atras()
                                } catch (e: ErrorApi) {
                                    if (e.codigo != "limite_encanta") throw e
                                    avisos.error(e.detalle)
                                    // iOS mira `c.plus`; acá se pregunta (sin Plus, a la pantalla de Plus).
                                    if (runCatching { c.api.plus().estado.activo }.getOrDefault(false) != true) ir.plus("perfil")
                                }
                            }
                        }) { Icono(I.estrella, 26.dp, Color.Transparent, 0f, relleno = C.lima) }
                    } else {
                        GenteBotonRedondo(76.dp, Gels.limaChico, "Me prendo", accion = {
                            accion {
                                try {
                                    reaccionarYAvisar(c, avisos, p, modo, "prendo"); ir.atras()
                                } catch (e: ErrorApi) {
                                    if (e.codigo != "limite") throw e
                                    ir.plus("limit")
                                }
                            }
                        }) { Icono(I.mas, 32.dp, C.carbon, 3.4f) }
                        GenteBotonRedondo(60.dp, genteGelVioletaRedondo, "Invitar a un plan", accion = { invitando = true }) {
                            Icono(I.calendarioSimple, 24.dp, C.blanco, 2.4f)
                        }
                    }
                }
                planInvita != null || desde.startsWith("recibido") -> {
                    GenteBotonRedondo(60.dp, genteGelPaso, "Ahora no", accion = {
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
                                    // 422 `falta_genero` / `confirmar_diversidad`: conGenero pregunta y reintenta
                                    // (la HojaGenero / HojaDiversidad de iOS).
                                    ar.com.caigo.app.pantallas.ingreso.conGenero(c) { d -> c.api.responderInvitacion(planInvita, true, d) }
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
        if (invitando) GenteHojaInvitar(ir, p.id, p.nombre, { invitando = false }) { plan ->
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

private suspend fun reaccionarYAvisar(c: ar.com.caigo.app.app.Contenedor, avisos: ar.com.caigo.app.piezas.Avisos, p: Perfil, modo: String, accion: String) {
    val r = c.api.reaccionar(p.id, modo, accion)
    avisos.ok(genteMensaje(p.nombre, accion, modo, r.mutuo))
}

@Composable
private fun TarjetaPerfil(contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().gel(24.dp, Gels.neutra).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contenido() }
}

@Composable
private fun Estadistica(v: String, l: String, modifier: Modifier) {
    Column(modifier.gel(20.dp, Gels.neutra).padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(v, 22f, 800, C.violetaTexto, alto = 1f, unRenglon = true)
        T(l, 12f, 700, C.texto2, unRenglon = true)
    }
}
