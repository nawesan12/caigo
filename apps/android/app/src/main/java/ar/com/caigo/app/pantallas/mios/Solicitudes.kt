package ar.com.caigo.app.pantallas.mios

import ar.com.caigo.app.piezas.asistencia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import ar.com.caigo.app.pantallas.planes.HojaConfirmar
import ar.com.caigo.app.nucleo.ZonaAR
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.Solicitud
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.piezas.Avatar
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Encabezado
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.compartir
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.rgba
import kotlinx.coroutines.launch

/** 07 · Solicitudes de un plan propio (y el vacío 13c). Es `VistaSolicitudes` en iOS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Solicitudes(ir: Ir, planId: String) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var plan by remember(planId) { mutableStateOf<Plan?>(null) }
    var sols by remember(planId) { mutableStateOf<List<Solicitud>?>(null) }
    var error by remember(planId) { mutableStateOf<String?>(null) }
    val respondidas = remember(planId) { mutableStateListOf<String>() }
    var ocupado by remember { mutableStateOf<String?>(null) }
    /** Los que ya van (aceptados): el organizador puede sacarlos (contrato §2). */
    val van = remember(planId) { mutableStateListOf<Solicitud>() }
    var sacando by remember { mutableStateOf<Solicitud?>(null) }
    var refrescando by remember { mutableStateOf(false) }

    suspend fun cargar() {
        try {
            coroutineScope {
                val a = async { c.api.plan(planId) }
                val b = async { c.api.solicitudes(planId) }
                val pl = a.await()
                val s = b.await()
                plan = pl
                sols = s.solicitudes.filter { it.estado == "pendiente" }
                van.clear()
                van.addAll(s.solicitudes.filter { it.estado == "aceptada" })
                error = null
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = mensajeDe(e)
        }
    }
    LaunchedEffect(planId) { cargar() }
    // En vivo: llega un pedido nuevo o alguien sale, y la lista se actualiza sola.
    LaunchedEffect(planId) {
        c.tiempoReal.eventos.collect { e -> if (e.tipo == "plan" && e.planId == planId) launch { cargar() } }
    }

    fun responder(s: Solicitud, aceptar: Boolean) {
        ocupado = s.id
        alcance.launch {
            try {
                c.api.responder(s.id, aceptar)
                respondidas += s.id
                if (aceptar) van.add(s.copy(estado = "aceptada"))
                if (aceptar) avisos.ok("${miosPrimer(s.persona.nombre)} ya está adentro.")
                runCatching { c.api.plan(planId) }.getOrNull()?.let { plan = it }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            }
            ocupado = null
        }
    }

    Pantalla { arriba, abajo ->
        val pl = plan
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp)) {
            Encabezado(
                "Solicitudes",
                pl?.let { listOf(it.titulo, Fecha.cuando(it.empiezaEn)).filter { s -> s.isNotEmpty() }.joinToString(" · ") },
                { ir.atras() }, Modifier.padding(horizontal = 16.dp),
            )
            val todas = sols
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    pl != null && todas != null -> {
                        val lista = todas.filter { it.id !in respondidas }
                        if (lista.isEmpty() && van.isEmpty()) {
                            MiosVacioSolicitudes(pl) { compartir(ctx, miosLinkCompartir(pl)) }
                        } else {
                            // La primera solicitud que alcanza para llenar el cupo va destacada ("Completan").
                            val completa = lista.firstOrNull { pl.faltan > 0 && it.cantidad >= pl.faltan }?.id
                            PullToRefreshBox(
                                refrescando,
                                { alcance.launch { refrescando = true; try { cargar() } finally { refrescando = false } } },
                                Modifier.fillMaxSize(),
                            ) {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = abajo + 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    item(key = "resumen") { Resumen(pl, lista.size) }
                                    items(lista, key = { it.id }) { s ->
                                        Tarjeta(s, s.id == completa, ocupado == s.id, { responder(s, true) }, { responder(s, false) }) { ir.persona(s.persona.id, "solicitud") }
                                    }
                                    if (van.isNotEmpty()) item(key = "van") { YaVan(pl, van, { sacando = it }) { ir.persona(it, "solicitud") } }
                                }
                            }
                        }
                    }
                    error != null -> MiosVacio("No pudimos cargar las solicitudes", error!!, "Probar de nuevo", { alcance.launch { cargar() } }, Modifier.padding(28.dp))
                    else -> Puntitos(p.acento)
                }
            }
        }
        HojaConfirmar(
            sacando != null, "¿Sacar a ${miosPrimer(sacando?.persona?.nombre.orEmpty())} del plan?",
            "Le avisamos que ya no está en el plan y su lugar vuelve a quedar libre.",
            "Sacar del plan", { sacando = null },
        ) {
            val s = sacando ?: return@HojaConfirmar
            c.api.sacarPersona(planId, s.persona.id)
            avisos.ok("Listo, ${miosPrimer(s.persona.nombre)} ya no está en el plan.")
            van.removeAll { it.id == s.id }
            runCatching { c.api.plan(planId) }.getOrNull()?.let { plan = it }
        }
    }
}

/** «Ya van»: los aceptados, con «Sacar» (antes de que empiece el plan). */
@Composable
private fun YaVan(pl: Plan, van: List<Solicitud>, sacar: (Solicitud) -> Unit, verPersona: (String) -> Unit) {
    val p = LocalPaleta.current
    val empezo = Fecha.parse(pl.empiezaEn)?.let { java.time.ZonedDateTime.now(ZonaAR).isAfter(it) } ?: false
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MiosSeccion("Ya van · ${van.sumOf { it.cantidad }}", Modifier.padding(top = 10.dp))
        van.forEach { s ->
            val per = s.persona
            Row(
                Modifier.fillMaxWidth().gel(20.dp, miosGelTarjeta()).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(per.nombre, per.foto, 44.dp, borde = 0.dp, tamTexto = 17f)
                Column(Modifier.weight(1f).tocable { verPersona(per.id) }, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        T(
                            if (s.cantidad > 1) "${miosPrimer(per.nombre)} · somos ${s.cantidad}" else miosPrimer(per.nombre),
                            15f, 700, modifier = Modifier.weight(1f, fill = false), unRenglon = true,
                        )
                        if (per.verificada) MiosVerificado(15.dp)
                    }
                    T(miosAsistencia(per.asistencia), 12f, 400, p.texto2)
                }
                if (!empezo && !pl.cancelado) {
                    Box(
                        Modifier.tocable { sacar(s) }
                            .semantics { contentDescription = "Sacar a ${miosPrimer(per.nombre)} del plan"; role = Role.Button }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) { T("Sacar", 14f, 700, C.coral2, unRenglon = true) }
                }
            }
        }
    }
}

/** Contador oscuro: "Faltan 2 / Somos 8 de 10" y "3 pedidos". */
@Composable
private fun Resumen(pl: Plan, pedidos: Int) {
    Row(
        Modifier.fillMaxWidth().background(C.carbon, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(Cupo.badge(pl.faltan), 34f, 800, if (Cupo.completo(pl.faltan)) C.lima else C.coral, alto = 1f, unRenglon = true)
            T(Cupo.somos(pl.somos, pl.total), 13f, 600, C.blancoCalido)
        }
        Box(Modifier.background(Color(0xFF3A3550), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            T(if (pedidos == 1) "1 pedido" else "$pedidos pedidos", 13f, 700, C.blancoCalido)
        }
    }
}

@Composable
private fun Tarjeta(s: Solicitud, completan: Boolean, ocupado: Boolean, aceptar: () -> Unit, rechazar: () -> Unit, verPersona: () -> Unit) {
    val p = LocalPaleta.current
    val per = s.persona
    Column(
        Modifier.fillMaxWidth()
            .then(
                if (completan) Modifier.background(p.tarjetaPlano, RoundedCornerShape(24.dp)).border(2.dp, C.lima, RoundedCornerShape(24.dp))
                else Modifier.gel(24.dp, miosGelTarjeta()),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().tocable(verPersona), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Avatares solapados 14 px: el ancho del Row ya descuenta el solape (el offset no lo haría).
            Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                Avatar(per.nombre, per.foto, 44.dp, borde = if (s.cantidad > 1) 2.dp else 0.dp, tamTexto = 17f)
                if (s.cantidad > 1) {
                    Avatar("+", null, 44.dp, texto = "+${s.cantidad - 1}", fondo = C.lima to C.carbon, tamTexto = 15f)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val nombre = if (s.cantidad > 1) "${miosPrimer(per.nombre)} · somos ${s.cantidad}" else listOfNotNull(miosPrimer(per.nombre), per.edad?.toString()).joinToString(", ")
                    T(nombre, 15f, 700, modifier = Modifier.weight(1f, fill = false), unRenglon = true)
                    if (per.verificada) MiosVerificado(15.dp)
                }
                T(if (per.enMiGente) miosAsistencia(per.asistencia, "está en tu gente") else miosAsistencia(per.asistencia), 12f, 400, p.texto2)
            }
            if (completan) {
                Box(Modifier.gel(10.dp, Gels.limaChico).padding(horizontal = 9.dp, vertical = 5.dp)) { T("Completan", 11f, 800, C.carbon) }
            }
        }
        if (s.mensaje.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().gel(14.dp, if (p.oscuro) Gels.chipOscuro else Gels.chipClaro).padding(horizontal = 12.dp, vertical = 10.dp)) {
                T(s.mensaje, 14f, 400, alto = 1.4f)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(46.dp).toque(!ocupado, onClick = rechazar)
                    .border(1.5.dp, if (p.oscuro) C.oscuro1 else C.pista, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) { T("Ahora no", 15f, 700, p.texto2) }
            Cta(
                if (s.cantidad > 1) "Aceptar a los ${s.cantidad}" else "Aceptar", aceptar,
                Modifier.weight(1.4f), alto = 46.dp, radio = 15.dp, tam = 15f, cargando = ocupado, color = C.carbon,
            )
        }
    }
}

/** 13c · Nadie pidió sumarse todavía. */
@Composable
internal fun MiosVacioSolicitudes(pl: Plan, compartirLink: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Column(
            Modifier.rotate(4f)
                .gel(28.dp, radial(0f, 0f, 1.2f, .9f, 0f to C.oscuro1, .62f to C.carbon),
                    listOf(interna(1f, 0f, rgba(255, 255, 255, .08f)), externa(22f, 36f, rgba(36, 33, 45, .6f), spread = -14f)), grano = true)
                .padding(horizontal = 26.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(Cupo.badge(pl.faltan), 56f, 800, if (Cupo.completo(pl.faltan)) C.lima else C.coral, alto = 1f, espaciado = px(-2f, 56f), unRenglon = true)
            T(Cupo.somos(pl.somos, pl.total), 13f, 600, C.blancoCalido)
        }
        T("Nadie pidió sumarse todavía", 30f, 700, alto = 1.08f, espaciado = px(-.6f, 30f), alinear = TextAlign.Center)
        T("Pasá el link por el grupo. Siempre hay alguien que se prende.", 15f, 400, LocalPaleta.current.texto2, alto = 1.5f, alinear = TextAlign.Center)
        Cta(
            "Compartir link del plan", compartirLink, Modifier.fillMaxWidth().padding(top = 8.dp),
            alto = 58.dp, tam = 16f, icono = I.compartir, iconoTrazo = 2.4f, iconoTam = 18.dp,
        )
    }
}
