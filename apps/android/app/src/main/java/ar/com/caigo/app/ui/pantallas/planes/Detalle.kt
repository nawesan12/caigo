package ar.com.caigo.app.ui.pantallas.planes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.datos.ErrorApi
import ar.com.caigo.app.datos.Plan
import ar.com.caigo.app.datos.Reporte
import ar.com.caigo.app.datos.mensajeDe
import ar.com.caigo.app.logica.Cupo
import ar.com.caigo.app.logica.Plata
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Avatar
import ar.com.caigo.app.ui.piezas.Avatares
import ar.com.caigo.app.ui.piezas.BarraCupo
import ar.com.caigo.app.ui.piezas.BotonIcono
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.Fecha
import ar.com.caigo.app.ui.piezas.Foto
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.Segmentado
import ar.com.caigo.app.ui.piezas.compartir
import ar.com.caigo.app.ui.piezas.distancia
import ar.com.caigo.app.ui.piezas.fondoCaigo
import ar.com.caigo.app.ui.piezas.nombreCategoria
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.dibujarGrano
import ar.com.caigo.app.ui.tema.estilo
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.radial
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Botón claro sobre la foto del detalle (#FAF9F6 en claro; gradiente grafito en oscuro). */
@Composable
private fun gelSobreFoto(): Gel = if (LocalPaleta.current.oscuro)
    Gel(vertical(Color(0xFF36314A), C.grafito), listOf(interna(1f, 0f, rgba(255, 255, 255, .12f))))
else Gel(vertical(C.blancoCalido, C.blancoCalido), emptyList())

internal fun textoCosto(p: Plan): String = when {
    p.lugar != null && p.precioCentavos != null -> Plata.pesos(p.precioCentavos.toLong()) + " por persona" + if (p.incluye.isNotBlank()) " · ${p.incluye}" else ""
    p.costoCentavos != null && p.costoCentavos > 0 -> Plata.pesos(p.costoCentavos.toLong()) + " por persona"
    else -> "Cada uno lo suyo"
}

internal fun textoIngreso(p: Plan): String = when {
    p.lugar != null && p.cobro != "gratis" -> "Con entrada"
    p.ingreso == "libre" -> "Libre"
    p.ingreso == "mi_gente" -> "Solo mi gente"
    else -> "Con aprobación"
}

private fun requisitos(p: Plan): List<String> = buildList {
    if (p.soloVerificados) add("Solo verificados")
    add(textoIngreso(p))
    when (p.genero) { "mujeres" -> add("Solo mujeres"); "diversidades" -> add("Solo diversidades") }
    if (p.edadMin != null || p.edadMax != null) add("${p.edadMin ?: 18} a ${p.edadMax ?: 99}")
    if (p.visibilidad == "mi_gente") add("Para mi gente")
}

/** El check-in abre media hora antes y cierra cuando termina el plan. */
private fun enVentanaDeCheckin(p: Plan): Boolean {
    val t = Fecha.parse(p.empiezaEn) ?: return false
    val ahora = ZonedDateTime.now(t.zone)
    return ahora.isAfter(t.minusMinutes(30)) && ahora.isBefore(t.plusMinutes(p.duracionMin.toLong()))
}

private fun yaPaso(p: Plan): Boolean {
    val t = Fecha.parse(p.empiezaEn) ?: return false
    return ZonedDateTime.now(t.zone).isAfter(t.plusMinutes(p.duracionMin.toLong()))
}

/**
 * 05 · Detalle del plan (y 14b en oscuro), con los estados del prototipo: esperando, adentro, plan de
 * lugar con entrada, beneficio y Caigo Tap.
 */
@Composable
fun DetallePlan(ir: Ir, id: String? = null, slug: String? = null) {
    val c = LocalApp.current
    var planId by remember { mutableStateOf(id) }
    var plan by remember { mutableStateOf<Plan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var vuelta by remember { mutableIntStateOf(0) }
    LaunchedEffect(slug, vuelta) {
        try {
            val pid = planId ?: c.api.publico(slug!!).id.also { planId = it }
            val punto = c.ubicacionConocida
            plan = c.api.plan(pid, punto?.first, punto?.second)
            error = null
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = mensajeDe(e)
        }
    }
    // Al volver de Mercado Pago, de la verificación o del chat, se refresca.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vuelta++ }

    val p = plan
    if (p == null) {
        Box(Modifier.fillMaxSize().fondoCaigo(LocalPaleta.current.oscuro), contentAlignment = Alignment.Center) {
            if (error != null) Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                T(error!!, 16f, 600, LocalPaleta.current.texto, alinear = TextAlign.Center)
                Cta("Volver", { ir.atras() }, Modifier.fillMaxWidth(), alto = 56.dp, tam = 16f)
            } else Puntitos()
        }
        return
    }
    ContenidoDetalle(ir, p) { vuelta++ }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContenidoDetalle(ir: Ir, p: Plan, recargar: () -> Unit) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val pal = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var grupo by remember { mutableIntStateOf(0) }
    var cantidad by remember { mutableIntStateOf(2) }
    var mensaje by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var opciones by remember { mutableStateOf(false) }
    var reportando by remember { mutableStateOf(false) }
    val deLugar = esDeLugar(p)
    val adentro = adentro(p)
    val organizo = p.miEstado == "organizador"
    val link = "https://caigo.com.ar/p/${p.slug}"

    Box(Modifier.fillMaxSize().background(if (pal.oscuro) C.grafito else C.blancoCalido)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                Foto(p.foto.ifBlank { p.lugar?.foto }, Modifier.fillMaxSize(), colorDeCategoria(p.categoria))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to rgba(32, 29, 43, .45f), .35f to rgba(32, 29, 43, 0f))))
            }
            // La hoja de contenido sube 30 px sobre la foto (top:270 en el mockup).
            Column(
                Modifier.offset(y = (-30).dp).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(if (pal.oscuro) C.grafito else C.blancoCalido)
                    .then(if (pal.oscuro) Modifier.fondoCaigo(true, Brush.verticalGradient(listOf(C.grafito, C.grafito))) else Modifier)
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 230.dp + abajo),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ChipsDetalle(p)
                T(p.titulo, 30f, 700, pal.texto, alto = 1.05f, espaciado = px(-.8f, 30f))
                Contador(p)
                Organizador(ir, p)
                Info(p)
                if (p.descripcion.isNotBlank()) T(p.descripcion, 15f, 400, pal.texto2, alto = 1.5f)
                if (p.lugar != null) TarjetaEntrada(p)
                if (p.beneficio.isNotBlank()) Beneficio(p.beneficio, desbloqueado = false)
                if (p.soloVerificados) Row(
                    Modifier.fillMaxWidth().background(pal.superficie, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icono(I.escudoCheck, 20.dp, if (pal.oscuro) C.lima else C.violetaTexto, 2.2f)
                    T("Solo gente verificada con DNI", 13f, 700, pal.texto)
                }
                val reqs = requisitos(p)
                if (reqs.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("Requisitos", 13f, 700, pal.texto2, modifier = Modifier.padding(vertical = 6.dp))
                    reqs.forEach {
                        Box(Modifier.border(1.5.dp, if (pal.oscuro) Color(0xFF3A3550) else C.pista, RoundedCornerShape(11.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            T(it, 12f, 700, pal.texto, unRenglon = true)
                        }
                    }
                }
            }
        }

        // Volver, compartir y más.
        Row(Modifier.fillMaxWidth().padding(top = arriba + 8.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val g = gelSobreFoto()
            val col = pal.texto
            BotonIcono(I.atras, { ir.atras() }, gel = g, color = col)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonIcono(I.compartir, { compartir(ctx, "${p.titulo} · ${Cupo.badge(p.faltan)}. Sumate en Caigo: $link") }, gel = g, color = col, iconoTam = 20.dp)
                BotonIcono(I.puntos, { opciones = true }, gel = g, color = Color.Transparent, relleno = col, iconoTam = 20.dp, trazo = 0f)
            }
        }

        // Barra de abajo: según el estado de quien mira.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(if (pal.oscuro) rgba(26, 24, 35, .94f) else C.blanco)
                .then(Modifier.background(Color.Transparent))
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp + abajo),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).offset(y = (-12).dp).background(if (pal.oscuro) Color(0xFF332E45) else Color(0xFFECE8F2)))
            when {
                p.cancelado -> T("Este plan se canceló.", 15f, 700, C.coral2, alinear = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                organizo -> {
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
                ) { T("Le avisamos a ${p.organizador.nombre}. Esperá un toque…", 15f, 800, C.violetaTexto, alinear = TextAlign.Center, modifier = Modifier.padding(horizontal = 14.dp)) }
                adentro -> {
                    if (yaPaso(p)) {
                        Cta("¿Repetimos?", { ir.repetimos(p.id) }, Modifier.fillMaxWidth(), gel = Gels.violeta, color = C.blanco, alto = 58.dp)
                    } else {
                        T(
                            if (deLugar) "Tenés tu entrada. Cuando llegues, hacé check-in." else "Estás adentro. La dirección ya está en el chat.",
                            14f, 800, if (pal.oscuro) C.lima else C.limaTexto, alinear = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        if (deLugar) {
                            Cta("Apoyar el celu en la placa", { ir.tap(p.id) }, Modifier.fillMaxWidth(), alto = 56.dp, icono = I.nfc, iconoTrazo = 2.6f, iconoTam = 20.dp)
                            Cta("Mostrar QR de entrada", {
                                alcance.launch {
                                    runCatching { c.api.pases().entradas.firstOrNull { it.planId == p.id } }
                                        .onSuccess { e -> if (e != null) ir.qrEntrada(e.id) else avisos.error("Todavía no está tu entrada. Probá en un ratito.") }
                                        .onFailure { avisos.error(mensajeDe(it)) }
                                }
                            }, Modifier.fillMaxWidth(), gel = Gels.blanco, alto = 52.dp, tam = 16f, icono = I.qr, iconoTrazo = 2.2f, iconoTam = 20.dp)
                        } else if (enVentanaDeCheckin(p)) {
                            Cta("Llegué", { ir.checkin(p.id) }, Modifier.fillMaxWidth(), alto = 56.dp, icono = I.check, iconoTrazo = 2.6f, iconoTam = 20.dp)
                        }
                        Cta("Ir al chat del plan", { ir.chat(p.id) }, Modifier.fillMaxWidth(), gel = Gels.violeta, color = C.blanco, alto = 62.dp, radio = 22.dp, tam = 18f)
                    }
                }
                Cupo.completo(p.faltan) -> Box(Modifier.fillMaxWidth().height(58.dp).gel(20.dp, Gels.limaBadge), contentAlignment = Alignment.Center) {
                    T("Completo", 17f, 800, C.carbon)
                }
                yaPaso(p) -> T("Este plan ya pasó.", 15f, 700, pal.texto2, alinear = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                else -> {
                    val permiteGrupo = p.aceptaGrupos && p.faltan > 1
                    if (permiteGrupo) {
                        Segmentado(
                            listOf("Voy solo", if (grupo == 1) "Somos $cantidad" else "Somos 2+"),
                            grupo, { grupo = it }, radio = 15.dp, radioOpcion = 12.dp, alto = 44.dp,
                        )
                        if (grupo == 1) Row(
                            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BotonIcono("M6 12h12", { cantidad = (cantidad - 1).coerceAtLeast(2) }, tam = 36.dp, radio = 12.dp, iconoTam = 16.dp, trazo = 3f)
                            T("Somos $cantidad", 16f, 800, pal.texto)
                            BotonIcono(I.mas, { cantidad = (cantidad + 1).coerceAtMost(minOf(10, p.faltan)) }, tam = 36.dp, radio = 12.dp, iconoTam = 16.dp, trazo = 3f)
                        }
                    }
                    if (!deLugar && p.ingreso != "libre") CampoMensaje(mensaje) { mensaje = it.take(200) }
                    val n = if (grupo == 1 && permiteGrupo) cantidad else 1
                    Cta(
                        when {
                            deLugar -> if (n > 1) "Comprar $n entradas" else "Comprar entrada"
                            n > 1 -> "Sumarnos"
                            else -> "Sumarme"
                        },
                        {
                            if (deLugar) ir.checkout(p.id, n)
                            else alcance.launch {
                                enviando = true
                                if (sumarse(c, ir, avisos, p, n, mensaje.trim()) != null) recargar()
                                enviando = false
                            }
                        },
                        Modifier.fillMaxWidth(), alto = 58.dp, icono = I.mas, iconoTrazo = 4f, iconoTam = 18.dp, cargando = enviando, color = C.carbon,
                    )
                }
            }
        }

        if (opciones) HojaOpciones(
            cerrar = { opciones = false },
            filas = buildList {
                add("Compartir link" to { compartir(ctx, link) })
                if (organizo && !p.cancelado && !yaPaso(p) && !p.destacado) add("Destacar plan" to {
                    alcance.launch {
                        try {
                            c.api.destacar(p.id)
                            avisos.ok("Listo. Tu plan sale destacado en Ahora y Explorar.")
                            recargar()
                        } catch (e: ErrorApi) {
                            // Sin Plus: el paywall. Ya usado esta semana: el detalle trae desde cuándo.
                            if (e.codigo == "plus") ir.plus("perfil") else avisos.error(e.detalle)
                        } catch (e: Throwable) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            avisos.error(mensajeDe(e))
                        }
                    }
                })
                if (organizo && !p.cancelado && !yaPaso(p)) add("Cancelar el plan" to {
                    alcance.launch {
                        runCatching { c.api.cancelarPlan(p.id) }.onSuccess { avisos.ok("Cancelaste el plan. Le avisamos a la gente."); recargar() }.onFailure { avisos.error(mensajeDe(it)) }
                    }
                })
                if (!organizo && (adentro || p.miEstado == "pendiente") && !deLugar) add("Bajarme del plan" to {
                    alcance.launch {
                        runCatching { c.api.salir(p.id) }.onSuccess { avisos.ok("Listo, te bajaste del plan."); recargar() }.onFailure { avisos.error(mensajeDe(it)) }
                    }
                })
                if (!organizo) add("Reportar" to { reportando = true })
            },
        )
        if (reportando) HojaReporte(cerrar = { reportando = false }) { motivo ->
            alcance.launch {
                runCatching { c.api.reportar(Reporte(planId = p.id, motivo = motivo)) }
                    .onSuccess { avisos.ok("Gracias. Lo revisa una persona del equipo en menos de 24 h.") }
                    .onFailure { avisos.error(mensajeDe(it)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsDetalle(p: Plan) {
    val pal = LocalPaleta.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val claro = if (pal.oscuro) Gel(vertical(Color(0xFF2E2A3D), Color(0xFF2E2A3D)), emptyList()) else Gels.chipClaro
        ChipFoto(nombreCategoria(p.categoria), false, fondo = claro, color = pal.texto)
        val pronto = Fecha.enCuanto(p.empiezaEn)
        if (pronto != null) ChipFoto(pronto, false, fondo = Gels.coralBadge, color = C.carbon)
        else ChipFoto(Fecha.cuando(p.empiezaEn), true, fondo = if (pal.oscuro) Gels.coralBadge else Gels.oscuroSel, color = if (pal.oscuro) C.carbon else C.blancoCalido)
        if (p.nivel.isNotBlank()) ChipFoto(p.nivel, false, fondo = claro, color = pal.texto)
        if (p.destacado) ChipDestacado()
    }
}

/** Contador protagonista: "Faltan 2 / Somos 8 de 10" con avatares y barra segmentada. */
@Composable
private fun Contador(p: Plan) {
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
            if (adentro(p) || p.lugar != null) {
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
    Row(
        Modifier.fillMaxWidth().tocable { if (o.id.isNotBlank() && p.miEstado != "organizador") ir.persona(o.id) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(p.lugar?.nombre ?: o.nombre, p.lugar?.foto ?: o.foto, 44.dp, borde = 0.dp, tamTexto = 18f,
            texto = (p.lugar?.nombre ?: o.nombre).split(" ").take(2).joinToString("") { it.take(1) }.uppercase().take(if (p.lugar != null) 2 else 1),
            fondo = C.violeta to C.blanco)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T("Organiza ${p.lugar?.nombre ?: o.nombre}", 15f, 700, pal.texto, unRenglon = true)
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
    Column(Modifier.fillMaxWidth().gel(20.dp, gel)) {
        Fila(I.relojCirculo, acento) { T(Fecha.rango(p.empiezaEn, p.duracionMin), 14f, 600, pal.texto) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(sep))
        Fila(I.pin, acento) {
            Column {
                val zona = listOf(p.zona.ifBlank { p.ciudad }.let { if (it.startsWith("Zona")) it else "Zona $it" }, distancia(p.distanciaM)).filter { it.isNotBlank() && it != "Zona " }.joinToString(" · ")
                T(zona, 14f, 600, pal.texto)
                val dir = p.direccion?.texto
                T(
                    when {
                        !dir.isNullOrBlank() -> listOf(p.lugarNombre, dir).filter { it.isNotBlank() }.joinToString(" · ")
                        p.ocultarDireccion -> "La dirección exacta te llega cuando te aceptan"
                        else -> p.lugarNombre
                    },
                    12f, 400, pal.texto2,
                )
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
    Row(
        Modifier.fillMaxWidth().gel(22.dp, Gels.neutra).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(if (p.cobro == "sena") "SEÑA ${p.senaPct ?: 0}%" else "ENTRADA", 11f, 800, C.texto2, espaciado = px(.8f, 11f))
            T(Plata.pesos(precio.toLong()), 26f, 800, C.carbon, alto = 1f)
            val cargo = if (p.cargoCentavos > 0) p.cargoCentavos else Plata.cargo(precio.toLong())
            T("+ ${Plata.pesos(cargo)} de servicio" + if (p.incluye.isNotBlank()) " · ${p.incluye}" else "", 12f, 600, C.texto2)
        }
        Box(Modifier.background(C.lima, RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 7.dp)) { T("Lugar aliado", 12f, 800, C.carbon, unRenglon = true) }
    }
}

@Composable
internal fun Beneficio(texto: String, desbloqueado: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .gel(22.dp, if (desbloqueado) Gel(vertical(C.lima0, C.lima), listOf(interna(2f, 0f, rgba(255, 255, 255, .7f)), externa(3f, 0f, C.limaBase))) else Gel(vertical(C.lavanda, C.lavanda), emptyList()))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).background(if (desbloqueado) C.carbon else C.blanco, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icono(I.regalo, 22.dp, if (desbloqueado) C.lima else C.violetaTexto, 2.2f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(texto, 14f, 800, C.carbon)
            T(if (desbloqueado) "Desbloqueado · mostralo en la caja" else "Se desbloquea cuando hacés check-in en el lugar", 12f, 600, if (desbloqueado) C.limaTexto else C.texto2)
        }
    }
}

/** Campo de mensaje opcional para el pedido (con las piezas del input del chat 08). */
@Composable
private fun CampoMensaje(valor: String, alCambiar: (String) -> Unit) {
    val pal = LocalPaleta.current
    Box(
        Modifier.fillMaxWidth().height(48.dp).background(if (pal.oscuro) Color(0xFF2E2A3D) else Color(0xFFF2F0EC), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (valor.isEmpty()) T("Escribí algo para quien organiza (opcional)", 15f, 400, pal.texto3, unRenglon = true)
        BasicTextField(valor, alCambiar, singleLine = true, textStyle = estilo(15f, 500, pal.texto), cursorBrush = SolidColor(C.violeta), modifier = Modifier.fillMaxWidth())
    }
}

/** Hoja de opciones (armada con la tarjeta de filas de "Herramientas para salir tranqui"). */
@Composable
internal fun HojaOpciones(cerrar: () -> Unit, filas: List<Pair<String, () -> Unit>>) {
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().zIndex(30f).background(rgba(32, 29, 43, .45f)).tocable(cerrar)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp + abajo).tocable { }
                .gel(24.dp, Gels.neutra),
        ) {
            filas.forEachIndexed { i, (t, f) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.lavanda))
                Box(Modifier.fillMaxWidth().tocable { cerrar(); f() }.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    T(t, 15f, 700, if (t.startsWith("Reportar") || t.startsWith("Cancelar") || t.startsWith("Bloquear")) C.coral2 else C.carbon)
                }
            }
        }
    }
}

/** Motivos de reporte (personas.go · motivos). */
internal val motivosReporte = listOf(
    "acoso" to "Acoso o mal trato", "falso" to "Perfil o plan falso", "no_vino" to "No vino",
    "inapropiado" to "Contenido inapropiado", "inseguro" to "Me sentí insegura/o", "otro" to "Otro motivo",
)

@Composable
internal fun HojaReporte(cerrar: () -> Unit, alElegir: (String) -> Unit) =
    HojaOpciones(cerrar, motivosReporte.map { (k, l) -> l to { alElegir(k) } })
