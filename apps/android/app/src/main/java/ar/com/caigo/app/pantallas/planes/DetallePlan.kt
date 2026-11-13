package ar.com.caigo.app.pantallas.planes

import ar.com.caigo.app.piezas.colorDeCategoria
import ar.com.caigo.app.piezas.asistencia
import ar.com.caigo.app.piezas.Verificado
import ar.com.caigo.app.piezas.HojaReporte
import ar.com.caigo.app.piezas.HojaOpciones
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.Plata
import ar.com.caigo.app.nucleo.Problema
import ar.com.caigo.app.nucleo.Reporte
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.Avatar
import ar.com.caigo.app.piezas.Avatares
import ar.com.caigo.app.piezas.BarraCupo
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.Sistema
import ar.com.caigo.app.piezas.Segmentado
import ar.com.caigo.app.piezas.compartir
import ar.com.caigo.app.nucleo.distancia
import ar.com.caigo.app.nucleo.Categorias
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

private fun requisitos(p: Plan): List<String> = buildList {
    if (p.soloVerificados) add("Solo verificados")
    add(textoIngreso(p))
    if (p.genero == "mujeres") add("Solo mujeres")
    if (p.genero == "diversidades") add("Solo diversidades")
    if (p.edadMin != null || p.edadMax != null) add("${p.edadMin ?: 18} a ${p.edadMax ?: 99}")
    if (p.visibilidad == "mi_gente") add("Para mi gente")
}

/** El check-in abre media hora antes y cierra cuando termina el plan. */
private fun enVentanaDeCheckin(p: Plan): Boolean {
    val t = Fecha.parse(p.empiezaEn) ?: return false
    val ahora = ZonedDateTime.now(t.zone)
    return ahora.isAfter(t.minusMinutes(30)) && ahora.isBefore(t.plusMinutes(p.duracionMin.toLong()))
}

private fun empezo(p: Plan): Boolean {
    val t = Fecha.parse(p.empiezaEn) ?: return false
    return ZonedDateTime.now(t.zone).isAfter(t)
}

private fun yaPaso(p: Plan): Boolean {
    val t = Fecha.parse(p.empiezaEn) ?: return false
    return ZonedDateTime.now(t.zone).isAfter(t.plusMinutes(p.duracionMin.toLong()))
}

/**
 * 05 · Detalle del plan (y 14b en oscuro), con los estados del prototipo: esperando, adentro, plan de
 * lugar con entrada (pagar todo o señar), beneficio y Caigo Tap.
 */
@Composable
fun DetallePlan(ir: Ir, id: String? = null, slug: String? = null) {
    val c = LocalApp.current
    val pal = LocalPaleta.current
    var planId by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<Plan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var vuelta by remember { mutableIntStateOf(0) }
    LaunchedEffect(vuelta) {
        try {
            var pid = planId ?: id
            if (pid == null && slug != null) {
                pid = c.api.publico(slug).id
                planId = pid
            }
            if (pid == null) return@LaunchedEffect
            val punto = c.ubicacionConocida
            plan = c.api.plan(pid, punto?.first, punto?.second)
            error = null
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (plan == null) error = mensajeDe(e)
        }
    }
    // Al volver de Mercado Pago, de la verificación o del chat, se refresca (y al volver a la app).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (plan != null) vuelta++ }
    // En vivo: si cambia el plan o sus solicitudes (te aceptan, alguien se suma), se recarga.
    LaunchedEffect(Unit) {
        c.tiempoReal.eventos.collect { e -> if (e.tipo == "plan" && e.planId == (planId ?: id)) vuelta++ }
    }

    val p = plan
    if (p != null) {
        ContenidoDetalle(ir, p) { vuelta++ }
        return
    }
    Pantalla { _, _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val e = error
            if (e != null) Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                T(e, 16f, 600, pal.texto, alinear = TextAlign.Center)
                Cta("Volver", { ir.atras() }, Modifier.fillMaxWidth(), alto = 56.dp, tam = 16f)
            } else Puntitos(pal.acento)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContenidoDetalle(ir: Ir, p: Plan, recargar: () -> Unit) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val vista = LocalView.current
    val pal = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 01b: con cuántos se suma por defecto.
    val inicial = remember { planesCantidadInicial(c.sesion.grupo, p) }
    var grupo by remember { mutableIntStateOf(if (inicial > 1) 1 else 0) }
    var cantidad by remember { mutableIntStateOf(if (inicial > 1) maxOf(2, inicial) else 2) }
    var mensaje by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var opciones by remember { mutableStateOf(false) }
    var reportando by remember { mutableStateOf(false) }
    var sena by remember { mutableStateOf(leerModoCheckout(ctx, p.id) == "sena" && p.cobro == "sena") }
    var cancelandoPlan by remember { mutableStateOf(false) }
    var cancelandoEntrada by remember { mutableStateOf(false) }
    val deLugar = esDeLugar(p)
    val link = "https://caigo.com.ar/p/${p.slug}"

    val cuerpoCancelarPlan = "Les avisamos a quienes se sumaron o pidieron sumarse. No tiene vuelta." +
        if (p.lugar != null) " A quienes pagaron entrada les devolvemos todo, cargo incluido." else ""

    fun sumarme() {
        val n = if (grupo == 1 && p.aceptaGrupos && p.faltan > 1) cantidad else 1
        alcance.launch {
            enviando = true
            if (planesSumarse(c, ir, avisos, p, n, mensaje.trim(), vibrar = { Sistema.vibrar(vista) }) != null) recargar()
            enviando = false
        }
    }

    /** DELETE /v1/entradas/{id}: devuelve según la política (flex, mod) o 409 `sin_reembolso`. */
    suspend fun cancelarMiEntrada() {
        val e = c.api.pases().entradas.firstOrNull { it.planId == p.id }
            ?: throw ErrorApi(0, Problema(detail = "No encontramos tu entrada. Probá en un ratito."))
        try {
            val r = c.api.cancelarEntrada(e.id)
            avisos.ok(if (r.reembolsoCentavos > 0) "Listo. Te devolvemos ${Plata.pesos(r.reembolsoCentavos)} por Mercado Pago." else "Listo, cancelaste tu entrada.")
            recargar()
        } catch (x: ErrorApi) {
            if (x.codigo != "sin_reembolso") throw x
            throw ErrorApi(x.estado, Problema(detail = x.problema.detail.ifEmpty { "El lugar no devuelve entradas de este plan." }))
        }
    }

    val filasOpciones = buildList<Pair<String, () -> Unit>> {
        add("Compartir link" to { compartir(ctx, link) })
        val vivo = !p.cancelado && !yaPaso(p)
        if (p.esMio && vivo && !p.destacado) add("Destacar plan" to {
            alcance.launch {
                try {
                    c.api.destacar(p.id)
                    avisos.ok("Listo. Tu plan sale destacado en Ahora y Explorar.")
                    recargar()
                } catch (e: ErrorApi) {
                    // Sin Plus: el paywall. Ya usado esta semana: el detalle dice desde cuándo.
                    if (e.codigo == "plus") ir.plus("destacar") else avisos.error(e.detalle)
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    avisos.error(mensajeDe(e))
                }
            }
        })
        if (p.esMio && vivo && !empezo(p) && p.lugar == null) add("Editar el plan" to { ir.editarPlan(p.id) })
        if (p.esMio && vivo) {
            add((if (p.somos > 1) "Gente del plan" else "Solicitudes") to { ir.solicitudes(p.id) })
            add("Cancelar el plan" to { cancelandoPlan = true })
        }
        if (!p.esMio && p.adentro && deLugar && vivo) add("Cancelar mi entrada" to { cancelandoEntrada = true })
        if (!p.esMio && (p.adentro || p.miEstado == "pendiente") && !deLugar) add("Bajarme del plan" to {
            alcance.launch {
                try {
                    c.api.salir(p.id)
                    avisos.ok("Listo, te bajaste del plan.")
                    recargar()
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    avisos.error(mensajeDe(e))
                }
            }
        })
        if (!p.esMio) add("Reportar" to { reportando = true })
    }

    Box(Modifier.fillMaxSize().background(if (pal.oscuro) C.grafito else C.blancoCalido)) {
        // La barra de abajo sube con el teclado (safeAreaInset de iOS).
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    Foto(fotoDe(p), Modifier.fillMaxSize(), colorDeCategoria(p.categoria))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to rgba(32, 29, 43, .45f), .35f to rgba(32, 29, 43, 0f))))
                }
                // La hoja de contenido sube 30 sobre la foto (top:270 en el mockup). El offset no achica el
                // alto: los 30 de abajo del mockup ya quedan como espacio, por eso no lleva padding abajo.
                Column(
                    Modifier.offset(y = (-30).dp).fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(if (pal.oscuro) C.grafito else C.blancoCalido)
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ChipsDetalle(p)
                    T(p.titulo, 30f, 700, pal.texto, alto = 1.05f, espaciado = px(-.8f, 30f))
                    PlanesContador(p)
                    Organizador(ir, p)
                    Info(p)
                    if (p.descripcion.isNotEmpty()) T(p.descripcion, 15f, 400, pal.texto2, alto = 1.5f)
                    if (p.lugar != null) TarjetaEntrada(p)
                    if (p.beneficio.isNotEmpty()) PlanesBeneficio(p.beneficio, desbloqueado = false)
                    if (p.soloVerificados) Row(
                        Modifier.fillMaxWidth().background(pal.superficie, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icono(I.escudoCheck, 20.dp, if (pal.oscuro) C.lima else C.violetaTexto, 2.2f)
                        T("Solo gente verificada con DNI", 13f, 700, pal.texto)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        T("Requisitos", 13f, 700, pal.texto2, modifier = Modifier.padding(vertical = 6.dp))
                        requisitos(p).forEach {
                            Box(Modifier.border(1.5.dp, if (pal.oscuro) Color(0xFF3A3550) else C.pista, RoundedCornerShape(11.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                T(it, 12f, 700, pal.texto, unRenglon = true)
                            }
                        }
                    }
                }
            }

            // Barra de abajo: según el estado de quien mira.
            Box(Modifier.fillMaxWidth().background(if (pal.oscuro) rgba(26, 24, 35, .94f) else C.blanco)) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp + abajo),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EstadoAbajo(
                        ir, p, grupo, { grupo = it }, cantidad, { cantidad = it }, sena, { sena = it }, mensaje, { mensaje = it.take(200) }, enviando,
                        alSumarme = {
                            val n = if (grupo == 1 && p.aceptaGrupos && p.faltan > 1) cantidad else 1
                            if (deLugar) {
                                guardarModoCheckout(ctx, p.id, if (p.cobro == "sena" && sena) "sena" else "entrada")
                                ir.checkout(p.id, n)
                            } else sumarme()
                        },
                        alQrEntrada = {
                            // Plan gratis de un lugar: no hay entrada; el QR personal sale de /v1/planes/{id}/qr.
                            if (!p.conEntrada) ir.qrOrganizador(p.id)
                            else alcance.launch {
                                try {
                                    val e = c.api.pases().entradas.firstOrNull { it.planId == p.id }
                                    if (e != null) ir.qrEntrada(e.id) else avisos.error("Todavía no está tu entrada. Probá en un ratito.")
                                } catch (e: Throwable) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    avisos.error(mensajeDe(e))
                                }
                            }
                        },
                    )
                }
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(if (pal.oscuro) Color(0xFF332E45) else Color(0xFFECE8F2)))
            }
        }

        // Volver, compartir y más.
        Row(Modifier.fillMaxWidth().padding(top = arriba + 8.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val g = gelSobreFoto()
            BotonIcono(I.atras, { ir.atras() }, Modifier.semantics { contentDescription = "Volver" }, gel = g, color = pal.texto)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonIcono(
                    I.compartir, { compartir(ctx, "${p.titulo} · ${Cupo.badge(p.faltan)}. Sumate en Caigo: $link") },
                    Modifier.semantics { contentDescription = "Compartir" }, iconoTam = 20.dp, gel = g, color = pal.texto,
                )
                BotonIcono(
                    I.puntos, { opciones = true }, Modifier.semantics { contentDescription = "Más opciones" },
                    iconoTam = 20.dp, trazo = 0f, gel = g, color = Color.Transparent, relleno = pal.texto,
                )
            }
        }

        if (opciones) HojaOpciones(cerrar = { opciones = false }, filas = filasOpciones)
        if (reportando) HojaReporte(cerrar = { reportando = false }) { motivo ->
            alcance.launch {
                try {
                    c.api.reportar(Reporte(planId = p.id, motivo = motivo))
                    avisos.ok("Gracias. Lo revisa una persona del equipo en menos de 24 h.")
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    avisos.error(mensajeDe(e))
                }
            }
        }
        HojaConfirmar(
            cancelandoPlan, "¿Cancelar el plan?", cuerpoCancelarPlan, "Cancelar el plan", { cancelandoPlan = false },
        ) {
            c.api.cancelarPlan(p.id)
            avisos.ok("Cancelaste el plan. Le avisamos a la gente.")
            recargar()
        }
        HojaConfirmar(
            cancelandoEntrada, if (p.cancelacion == "strict") "Tu entrada no tiene reembolso" else "¿Cancelar tu entrada?",
            planesPolitica(p.cancelacion), if (p.cancelacion == "strict") null else "Cancelar mi entrada", { cancelandoEntrada = false },
        ) { cancelarMiEntrada() }
        // «Antes de sumarte» (falta_genero / confirmar_diversidad): en Android son las hojas globales
        // de Genero.kt (conGenero).
    }
}

/** Botón claro sobre la foto del detalle (#FAF9F6 en claro; gradiente grafito en oscuro). */
@Composable
private fun gelSobreFoto(): Gel = if (LocalPaleta.current.oscuro)
    Gel(vertical(Color(0xFF36314A), C.grafito), listOf(interna(1f, 0f, rgba(255, 255, 255, .12f))))
else Gel(vertical(C.blancoCalido, C.blancoCalido), emptyList())

@Composable
private fun EstadoAbajo(
    ir: Ir, p: Plan,
    grupo: Int, alGrupo: (Int) -> Unit,
    cantidad: Int, alCantidad: (Int) -> Unit,
    sena: Boolean, alSena: (Boolean) -> Unit,
    mensaje: String, alMensaje: (String) -> Unit,
    enviando: Boolean,
    alSumarme: () -> Unit,
    alQrEntrada: () -> Unit,
) {
    val pal = LocalPaleta.current
    val deLugar = esDeLugar(p)
    when {
        p.cancelado -> T("Este plan se canceló.", 15f, 700, C.coral2, alinear = TextAlign.Center)
        p.esMio -> {
            if (yaPaso(p)) Cta("¿Repetimos?", { ir.repetimos(p.id) }, Modifier.fillMaxWidth(), gel = Gels.violeta, color = C.blanco, alto = 58.dp)
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Cta(if (p.pendientes > 0) "Solicitudes · ${p.pendientes}" else "Solicitudes", { ir.solicitudes(p.id) }, Modifier.weight(1f), gel = Gels.blanco, alto = 52.dp, tam = 15f)
                    Cta("QR de check-in", { ir.qrOrganizador(p.id) }, Modifier.weight(1f), gel = Gels.blanco, alto = 52.dp, tam = 15f, icono = I.qr, iconoTrazo = 2.2f, iconoTam = 18.dp)
                }
                Cta("Ir al chat del plan", { ir.chat(p.id) }, Modifier.fillMaxWidth(), gel = Gels.violeta, color = C.blanco, alto = 58.dp, tam = 18f)
            }
        }
        p.miEstado == "pendiente" -> Box(
            Modifier.fillMaxWidth().height(62.dp).gel(22.dp, vertical(C.tinte1, C.tinte1), listOf(interna(3f, 6f, rgba(116, 86, 241, .2f)))),
            contentAlignment = Alignment.Center,
        ) { T("Le avisamos a ${p.organizador.nombre}. Esperá un toque…", 15f, 800, C.violetaTexto, Modifier.padding(horizontal = 14.dp), alinear = TextAlign.Center) }
        p.adentro -> {
            if (yaPaso(p)) {
                Cta("¿Repetimos?", { ir.repetimos(p.id) }, Modifier.fillMaxWidth(), gel = Gels.violeta, color = C.blanco, alto = 58.dp)
            } else {
                T(
                    if (deLugar) "Tenés tu entrada. Cuando llegues, hacé check-in." else "Estás adentro. La dirección ya está en el chat.",
                    14f, 800, if (pal.oscuro) C.lima else C.limaTexto, alinear = TextAlign.Center,
                )
                if (deLugar) {
                    Cta("Apoyar el celu en la placa", { ir.tap(p.id) }, Modifier.fillMaxWidth(), alto = 56.dp, icono = I.nfc, iconoTrazo = 2.6f, iconoTam = 20.dp)
                    Cta("Mostrar QR de entrada", alQrEntrada, Modifier.fillMaxWidth(), gel = Gels.blanco, alto = 52.dp, tam = 16f, icono = I.qr, iconoTrazo = 2.2f, iconoTam = 20.dp)
                } else if (enVentanaDeCheckin(p)) {
                    Cta("Llegué", { ir.checkin(p.id) }, Modifier.fillMaxWidth(), alto = 56.dp, icono = I.check, iconoTrazo = 2.6f, iconoTam = 20.dp)
                }
                Cta("Ir al chat del plan", { ir.chat(p.id) }, Modifier.fillMaxWidth(), gel = Gels.violeta, color = C.blanco, alto = 62.dp, radio = 22.dp, tam = 18f)
            }
        }
        Cupo.completo(p.faltan) -> Box(Modifier.fillMaxWidth().height(58.dp).gel(20.dp, Gels.limaBadge), contentAlignment = Alignment.Center) {
            T("Completo", 17f, 800, C.carbon)
        }
        yaPaso(p) -> T("Este plan ya pasó.", 15f, 700, pal.texto2, alinear = TextAlign.Center)
        else -> {
            val permiteGrupo = p.aceptaGrupos && p.faltan > 1
            val n = if (grupo == 1 && permiteGrupo) cantidad else 1
            if (permiteGrupo) {
                Segmentado(
                    listOf("Voy solo", if (grupo == 1) "Somos $cantidad" else "Somos 2+"), grupo, alGrupo,
                    Modifier.fillMaxWidth(), radio = 15.dp, radioOpcion = 12.dp, alto = 44.dp,
                )
                if (grupo == 1) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonIcono("M6 12h12", { alCantidad(maxOf(2, cantidad - 1)) }, Modifier.semantics { contentDescription = "Uno menos" }, tam = 36.dp, radio = 12.dp, iconoTam = 16.dp, trazo = 3f)
                    T("Somos $cantidad", 16f, 800, pal.texto)
                    BotonIcono(I.mas, { alCantidad(minOf(minOf(10, p.faltan), cantidad + 1)) }, Modifier.semantics { contentDescription = "Uno más" }, tam = 36.dp, radio = 12.dp, iconoTam = 16.dp, trazo = 3f)
                }
            }
            val pct = p.senaPct
            val precio = p.precioCentavos
            if (deLugar && p.cobro == "sena" && pct != null && precio != null) {
                // Prototipo: "Pagar todo" o "Señar N %"; el modo lo manda el checkout a la API.
                val d = Plata.desglose(precio.toLong(), if (sena) "sena" else "entrada", pct, n, if (p.cargoCentavos > 0) p.cargoCentavos else null)
                Segmentado(listOf("Pagar todo", "Señar $pct%"), if (sena) 1 else 0, { alSena(it == 1) }, Modifier.fillMaxWidth(), radio = 15.dp, radioOpcion = 12.dp, alto = 44.dp)
                T(if (sena) "Reservás con ${Plata.pesos(d.alLugar)}; el resto en el lugar" else "Entrada completa ahora", 13f, 600, pal.texto2, alinear = TextAlign.Center)
            }
            if (!deLugar && p.ingreso != "libre") CampoMensaje(mensaje, alMensaje)
            Cta(
                if (deLugar) (if (n > 1) "Comprar $n entradas" else "Comprar entrada") else (if (n > 1) "Sumarnos" else "Sumarme"),
                alSumarme, Modifier.fillMaxWidth(), alto = 58.dp, color = C.carbon, icono = I.mas, iconoTrazo = 4f, iconoTam = 18.dp, cargando = enviando,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsDetalle(p: Plan) {
    val pal = LocalPaleta.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val claro = if (pal.oscuro) Gel(vertical(Color(0xFF2E2A3D), Color(0xFF2E2A3D)), emptyList()) else Gels.chipClaro
        ChipFoto(Categorias.nombre(p.categoria), false, fondo = claro, color = pal.texto)
        val pronto = Fecha.enCuanto(p.empiezaEn)
        if (pronto != null) ChipFoto(pronto, false, fondo = Gels.coralBadge, color = C.carbon)
        else ChipFoto(Fecha.cuando(p.empiezaEn), true, fondo = if (pal.oscuro) Gels.coralBadge else Gels.oscuroSel, color = if (pal.oscuro) C.carbon else C.blancoCalido)
        if (p.nivel.isNotEmpty()) ChipFoto(p.nivel, false, fondo = claro, color = pal.texto)
        if (p.destacado) ChipDestacado()
    }
}

/** Contador protagonista: "Faltan 2 / Somos 8 de 10" con avatares y barra segmentada. */
@Composable
internal fun PlanesContador(p: Plan) {
    val pal = LocalPaleta.current
    val completo = Cupo.completo(p.faltan)
    val gel = if (pal.oscuro) Gel(radial(0f, 0f, 1.2f, 1f, 0f to Color(0xFF3F3860), .65f to C.oscuro5), listOf(interna(1f, 0f, rgba(255, 255, 255, .08f))), grano = true)
    else Gels.oscuroSel
    Column(
        Modifier.fillMaxWidth().gel(24.dp, gel)
            .then(if (pal.oscuro) Modifier.border(1.dp, Color(0xFF3A3550), RoundedCornerShape(24.dp)) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(Cupo.badge(p.faltan), 46f, 800, if (completo) C.lima else C.coral, alto = 1f, espaciado = px(-1.5f, 46f), unRenglon = true)
                T(Cupo.somos(p.somos, p.total), 14f, 600, C.blancoCalido)
            }
            Spacer(Modifier.width(8.dp))
            if (p.adentro || p.lugar != null) {
                Box(Modifier.background(Color(0xFF3A3550), RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 7.dp)) {
                    T(textoIngreso(p), 12f, 800, C.blancoCalido, unRenglon = true)
                }
            } else if (p.van.isNotEmpty()) {
                Avatares(p.van.map { it.nombre to it.foto }, total = p.somos, max = 4, tam = 32.dp)
            }
        }
        BarraCupo(p.somos, p.total, alto = 8.dp)
    }
}

@Composable
private fun Organizador(ir: Ir, p: Plan) {
    val pal = LocalPaleta.current
    val o = p.organizador
    val nombre = p.lugar?.nombre ?: o.nombre
    val iniciales = nombre.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase()
    Row(
        Modifier.fillMaxWidth().tocable { if (o.id.isNotEmpty() && !p.esMio && p.lugar == null) ir.persona(o.id, "plan") },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            nombre, p.lugar?.foto ?: o.foto, 44.dp, borde = 0.dp, tamTexto = 18f,
            texto = iniciales.take(if (p.lugar != null) 2 else 1), fondo = C.violeta to C.blanco,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T("Organiza $nombre", 15f, 700, pal.texto, Modifier.weight(1f, fill = false), unRenglon = true)
                if (o.verificada) Verificado(16.dp, color = if (pal.oscuro) C.lima else C.violeta, tilde = if (pal.oscuro) C.carbon else C.blanco)
            }
            T(
                if (p.lugar != null) "Lugar aliado · verificado por Caigo"
                else if (o.asistencia.de > 0) "Fue a ${o.asistencia.fue} de ${o.asistencia.de} planes · ${asistencia(o.asistencia.fue, o.asistencia.de)}"
                else "Nuevo en Caigo",
                13f, 400, pal.texto2,
            )
        }
    }
}

@Composable
private fun Info(p: Plan) {
    val pal = LocalPaleta.current
    val acento = if (pal.oscuro) C.lima else C.violeta
    val sep = if (pal.oscuro) Color(0xFF3A3550) else C.lavanda
    val gel = if (pal.oscuro) Gel(vertical(Color(0xFF2F2A40), C.oscuro5), listOf(interna(1f, 0f, rgba(255, 255, 255, .06f))))
    else Gel(vertical(C.blanco, C.blanco), listOf(interna(1f, 0f, C.blanco), externa(2f, 0f, C.borde2), externa(12f, 24f, rgba(80, 60, 160, .22f), spread = -12f)))
    val zonaBase = p.zona.ifEmpty { p.ciudad }
    val zona = listOf(if (zonaBase.isEmpty()) "" else if (zonaBase.startsWith("Zona")) zonaBase else "Zona $zonaBase", distancia(p.distanciaM))
        .filter { it.isNotEmpty() }.joinToString(" · ")
    val dir = p.direccion?.texto.orEmpty()
    val detalleDir = if (dir.isNotEmpty()) listOf(p.lugarNombre, dir).filter { it.isNotEmpty() }.joinToString(" · ")
    else if (p.ocultarDireccion) "La dirección exacta te llega cuando te aceptan" else p.lugarNombre
    Column(Modifier.fillMaxWidth().gel(20.dp, gel)) {
        Fila(I.relojCirculo, acento) { T(Fecha.rango(p.empiezaEn, p.duracionMin), 14f, 600, pal.texto) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sep))
        Fila(I.pin, acento) {
            Column {
                T(zona, 14f, 600, pal.texto)
                if (detalleDir.isNotEmpty()) T(detalleDir, 12f, 400, pal.texto2)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sep))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { T("$", 17f, 800, acento) }
            T(textoCosto(p), 14f, 600, pal.texto)
        }
    }
}

@Composable
private fun Fila(icono: String, color: Color, contenido: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icono(icono, 20.dp, color, 2f)
        Box(Modifier.weight(1f)) { contenido() }
    }
}

/** Tarjeta "ENTRADA $8.000 + $600 de servicio" del plan de lugar aliado (prototipo). */
@Composable
private fun TarjetaEntrada(p: Plan) {
    val precio = p.precioCentavos ?: return
    val cargo = if (p.cargoCentavos > 0) p.cargoCentavos else Plata.cargo(precio.toLong())
    Row(
        Modifier.fillMaxWidth().gel(22.dp, Gels.neutra).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(if (p.cobro == "sena") "SEÑA ${p.senaPct ?: 0}%" else "ENTRADA", 11f, 800, C.texto2, espaciado = px(.8f, 11f))
            T(Plata.pesos(precio.toLong()), 26f, 800, C.carbon, alto = 1f)
            T("+ ${Plata.pesos(cargo)} de servicio" + if (p.incluye.isEmpty()) "" else " · ${p.incluye}", 12f, 600, C.texto2)
        }
        Box(Modifier.background(C.lima, RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 7.dp)) { T("Lugar aliado", 12f, 800, C.carbon, unRenglon = true) }
    }
}

/** Campo de mensaje opcional para el pedido (con las piezas del input del chat 08). */
@Composable
private fun CampoMensaje(valor: String, alCambiar: (String) -> Unit) {
    val pal = LocalPaleta.current
    PlanesCampo(
        valor, alCambiar, "Escribí algo para quien organiza (opcional)",
        Modifier.fillMaxWidth().height(48.dp).background(if (pal.oscuro) Color(0xFF2E2A3D) else Color(0xFFF2F0EC), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
        colorPista = pal.texto3,
    )
}
