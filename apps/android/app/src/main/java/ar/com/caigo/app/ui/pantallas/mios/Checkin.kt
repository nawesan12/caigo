package ar.com.caigo.app.ui.pantallas.mios

import android.Manifest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.datos.Api
import ar.com.caigo.app.datos.ErrorApi
import ar.com.caigo.app.datos.Opinion
import ar.com.caigo.app.datos.Reporte
import ar.com.caigo.app.datos.mensajeDe
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Avatar
import ar.com.caigo.app.ui.piezas.BotonIcono
import ar.com.caigo.app.ui.piezas.CodigoQr
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.EscanerCodigos
import ar.com.caigo.app.ui.piezas.Fecha
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.ZonaAR
import ar.com.caigo.app.ui.piezas.compartir
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
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.radial
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Brillo arriba a la izquierda de los círculos y avatares. */
private fun conBrillo(base: Color): Brush = radial(.3f, .25f, 0f, 0f, 0f to rgba(255, 255, 255, .6f), .42f to rgba(255, 255, 255, 0f), circulo = true)

/** Tile violeta grande del check-in (120 px, radio 40). */
private val tileCheckin = Gel(
    vertical(0f to C.violeta1, .30f to C.violeta2, .62f to C.violeta, 1f to C.violeta4),
    // En el CSS la segunda box-shadow pisa a la primera: queda sólo la difusa.
    listOf(externa(18f, 40f, rgba(116, 86, 241, .4f))),
)

/** Animación tk-pop: escala .4 → 1.08 → 1 y rotación −20° → 6° → 0°. */
private suspend fun tkPop(escala: Animatable<Float, *>, giro: Animatable<Float, *>) = kotlinx.coroutines.coroutineScope {
    launch { escala.snapTo(.4f); escala.animateTo(1f, keyframes { durationMillis = 450; 1.08f at 270 }) }
    launch { giro.snapTo(-20f); giro.animateTo(0f, keyframes { durationMillis = 450; 6f at 270 }) }
}

/** 09a · Check-in: "Llegué" con la ubicación; si estás lejos, el QR del organizador. */
@Composable
fun Checkin(ir: Ir, planId: String) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val plan = rememberCarga(planId) { c.api.plan(planId) }
    var ocupado by remember { mutableStateOf(false) }
    var lejos by remember { mutableStateOf(false) }
    var listo by remember { mutableStateOf(false) }
    var compartiendo by remember { mutableStateOf(false) }
    val escala = remember { Animatable(1f) }
    val giro = remember { Animatable(0f) }

    fun llegue() {
        ocupado = true
        alcance.launch {
            try {
                val u = c.ubicacion()
                if (u == null) {
                    avisos.error("No pudimos saber dónde estás. Escaneá el QR del organizador.")
                    lejos = true
                    return@launch
                }
                c.api.checkin(planId, Api.Llegada("geo", u.first, u.second))
                listo = true
                tkPop(escala, giro)
                avisos.ok("Listo, tu asistencia quedó registrada.")
                delay(1200)
                ir.atras()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is ErrorApi && e.codigo == "lejos") lejos = true
                avisos.error(mensajeDe(e))
            } finally {
                ocupado = false
            }
        }
    }
    val (_, pedirUbicacion) = rememberPermiso(Manifest.permission.ACCESS_FINE_LOCATION) { llegue() }

    Pantalla { arriba, abajo ->
        val pl = plan.dato
        Column(Modifier.fillMaxSize().padding(top = arriba + 8.dp, bottom = abajo + 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonIcono(I.cruz, { ir.atras() }, iconoTam = 20.dp, trazo = 2.4f, gel = if (p.oscuro) Gels.chipOscuro else Gels.icono, color = p.texto)
                val ahora = ZonedDateTime.now(ZonaAR)
                T(
                    listOfNotNull(pl?.titulo, Fecha.hora(ahora)).joinToString(" · "), 14f, 700, p.texto2,
                    Modifier.weight(1f).padding(horizontal = 8.dp), alinear = TextAlign.Center, unRenglon = true,
                )
                Spacer(Modifier.width(44.dp))
            }
            Spacer(Modifier.weight(.6f))
            // Círculos concéntricos con el tile violeta y el pin lima.
            Box(
                Modifier.size(300.dp).background(if (p.oscuro) C.oscuro3 else C.lavanda, CircleShape).background(conBrillo(C.lavanda), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(210.dp).background(if (p.oscuro) C.oscuro2 else C.pista, CircleShape).background(conBrillo(C.pista), CircleShape), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(120.dp).graphicsLayer { scaleX = escala.value; scaleY = escala.value; rotationZ = giro.value }.gel(40.dp, tileCheckin),
                        contentAlignment = Alignment.Center,
                    ) { Icono(if (listo) I.check else I.pin, 54.dp, C.lima, if (listo) 3f else 2.4f) }
                }
            }
            Spacer(Modifier.height(30.dp))
            Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val lugar = pl?.lugar?.nombre ?: pl?.lugarNombre?.ifBlank { null } ?: pl?.zona.orEmpty()
                T(if (listo) "¡Llegaste!" else "Estás en $lugar", 32f, 700, alto = 1.05f, espaciado = px(-.8f, 32f), alinear = TextAlign.Center)
                T("Hacé check-in para que cuente tu asistencia.", 15f, 400, p.texto2, alto = 1.5f, alinear = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Plan de un lugar: el QR personal es lo que valida la puerta (contrato §6).
                if (pl?.lugar != null && pl.miEstado == "aceptada" && !listo) {
                    Cta("Mostrar mi QR en la puerta", { ir.qrOrganizador(planId) }, Modifier.fillMaxWidth(), gel = Gels.blanco, icono = I.qr, iconoTrazo = 2.2f, iconoTam = 20.dp)
                }
                if (lejos && !listo) {
                    Cta("Escanear el QR del organizador", { ir.escanearQr(planId) }, Modifier.fillMaxWidth(), gel = Gels.violeta, color = C.blanco, icono = I.qr, iconoTrazo = 2.2f, iconoTam = 20.dp)
                }
                Cta(
                    "Llegué", { if (c.tienePermisoUbicacion()) llegue() else pedirUbicacion() }, Modifier.fillMaxWidth(),
                    icono = I.check, iconoTrazo = 3f, iconoTam = 20.dp, cargando = ocupado, habilitado = !listo && pl != null,
                )
                Row(
                    Modifier.tocable {
                        if (compartiendo) return@tocable
                        compartiendo = true
                        alcance.launch {
                            try {
                                compartir(ctx, c.api.enVivo(planId).link)
                            } catch (e: Throwable) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                avisos.error(mensajeDe(e))
                            } finally {
                                compartiendo = false
                            }
                        }
                    }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val col = if (p.oscuro) C.lima else C.violetaTexto
                    Icono(I.escudo, 16.dp, col, 2.2f)
                    T("Compartir mi plan en vivo", 14f, 700, col)
                }
            }
        }
    }
}

/** Fondo violeta de "¿Repetimos?": `radial-gradient(120% 70% at 85% 0%, #A38FFF, #7456F1 45%, #5A3DD8)`. */
private val fondoRepetimos = radial(.85f, 0f, 1.2f, .7f, 0f to Color(0xFFA38FFF), .45f to C.violeta, 1f to Color(0xFF5A3DD8))

private val motivos = listOf(
    "acoso" to "Acoso", "falso" to "Perfil falso", "no_vino" to "No vino", "inapropiado" to "Algo inapropiado",
    "inseguro" to "Me sentí insegura o inseguro", "otro" to "Otro",
)

/** 09b · ¿Repetimos? y "¿Volverías a salir con…?" (privado). */
@Composable
fun Repetimos(ir: Ir, planId: String) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val plan = rememberCarga(planId) { c.api.plan(planId) }
    val volveria = remember { mutableStateMapOf<String, Boolean>() }
    var mandando by remember { mutableStateOf<Boolean?>(null) }
    var reportar by remember { mutableStateOf(false) }

    fun responder(repetir: Boolean) {
        mandando = repetir
        alcance.launch {
            try {
                val r = c.api.repetimos(planId, repetir, volveria.filterValues { it }.map { Opinion(it.key, true) })
                val nuevo = r.nuevo
                if (repetir && nuevo != null) {
                    avisos.ok("Listo, ya está el plan de la semana que viene.")
                    ir.plan(nuevo.id)
                } else {
                    avisos.ok("Gracias. Nadie ve tus respuestas.")
                    ir.atras()
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            } finally {
                mandando = null
            }
        }
    }

    Pantalla(fondo = Modifier.background(C.violeta).background(fondoRepetimos)) { arriba, abajo ->
        val pl = plan.dato
        if (pl == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (plan.error != null) T(plan.error!!, 15f, 600, C.blanco, alinear = TextAlign.Center) else Puntitos(C.lima)
            }
            return@Pantalla
        }
        val t = Fecha.parse(pl.empiezaEn)
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = arriba + 26.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BotonIcono(I.cruz, { ir.atras() }, Modifier.padding(end = 10.dp), tam = 32.dp, radio = 11.dp, iconoTam = 16.dp, trazo = 2.6f,
                        gel = Gel(vertical(C.violetaTexto, C.violetaTexto), emptyList()), color = C.blanco)
                    val cuando = t?.let {
                        when (ChronoUnit.DAYS.between(it.toLocalDate(), Fecha.hoy())) {
                            0L -> "hoy"
                            1L -> if (it.hour >= 19) "anoche" else "ayer"
                            else -> "el ${Fecha.diaLargo(it).lowercase()}"
                        }
                    }
                    Box(Modifier.background(C.violetaTexto, RoundedCornerShape(11.dp)).padding(horizontal = 11.dp, vertical = 6.dp)) {
                        T(listOfNotNull(pl.titulo, cuando).joinToString(" · "), 13f, 700, C.blanco, unRenglon = true)
                    }
                }
                T("¿Repetimos?", 52f, 800, C.blanco, alto = .95f, espaciado = px(-2f, 52f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Cta(
                        t?.let { "Sí, el ${Fecha.diaLargo(it).lowercase()}" } ?: "Sí", { responder(true) }, Modifier.weight(1.4f),
                        alto = 54.dp, radio = 18.dp, tam = 16f, color = C.carbon, cargando = mandando == true, habilitado = mandando == null,
                    )
                    Box(
                        Modifier.weight(1f).height(54.dp).toque(mandando == null) { responder(false) }
                            .border(2.dp, Color(0xFFA897F7), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (mandando == false) Puntitos(C.blanco) else T("Esta vez no", 16f, 700, C.blanco) }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().background(p.fondo, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = abajo + 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val gente = (listOf(pl.organizador) + pl.van).distinctBy { it.id }.filter { it.id.isNotBlank() && it.id != c.sesion.usuarioId }
                if (gente.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T("¿Volverías a salir con…?", 22f, 700)
                        T("Los que sumes a tu gente ven primero tus próximos planes. Nadie ve tus respuestas.", 13f, 400, p.texto2, alto = 1.45f)
                    }
                    gente.forEach { g ->
                        val si = volveria[g.id] == true || g.enMiGente
                        Row(
                            Modifier.fillMaxWidth().gel(20.dp, miosGelTarjeta()).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(g.nombre, g.foto, 44.dp, Modifier.tocable { ir.persona(g.id, "repetimos") }, borde = 0.dp, tamTexto = 17f)
                            Column(Modifier.weight(1f)) {
                                T(miosPrimer(g.nombre), 15f, 700)
                                if (g.organizador || g.id == pl.organizador.id) T("Organizó el plan", 12f, 400, p.texto2)
                            }
                            if (si) {
                                Row(
                                    Modifier.height(40.dp).toque(!g.enMiGente) { volveria[g.id] = false }.gel(14.dp, Gels.limaChico).padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icono(I.check, 14.dp, C.carbon, 3f)
                                    T("Tu gente", 13f, 800, C.carbon)
                                }
                            } else {
                                BotonIcono(I.mas, { volveria[g.id] = true }, tam = 40.dp, radio = 14.dp, iconoTam = 16.dp, trazo = 3f, color = C.violeta,
                                    gel = if (p.oscuro) Gels.chipOscuro else Gels.chipClaro)
                            }
                        }
                    }
                }
                Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp).tocable { reportar = true }) {
                    T("¿Pasó algo? ", 13f, 600, p.texto2)
                    T("Reportar", 13f, 700, if (p.oscuro) C.lima else C.violetaTexto)
                }
            }
        }
        MiosHoja(reportar, { reportar = false }, "¿Qué pasó?") {
            T("Lo revisa una persona del equipo, no un bot.", 13f, 400, p.texto2)
            MiosGrupo {
                motivos.forEachIndexed { i, (k, l) ->
                    MiosFila(I.bloqueo, l, colorIcono = C.coral2, ultima = i == motivos.lastIndex) {
                        reportar = false
                        alcance.launch {
                            try {
                                c.api.reportar(Reporte(planId = planId, motivo = k))
                                avisos.ok("Gracias. Lo revisamos en menos de 24 h.")
                            } catch (e: Throwable) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                avisos.error(mensajeDe(e))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Recuadro blanco con anillo lima y base (el del QR de "Tu entrada" del prototipo). */
private val gelQr = Gel(
    vertical(C.blanco, C.blanco),
    listOf(interna(0f, 0f, C.lima, spread = -3f), externa(5f, 0f, C.limaBase), externa(30f, 50f, rgba(80, 60, 160, .45f), spread = -24f)),
)

/** QR del check-in que muestra quien organiza (GET /v1/planes/{id}/qr). */
@Composable
fun QrOrganizador(ir: Ir, planId: String) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val plan = rememberCarga(planId) { c.api.plan(planId) }
    val qr = rememberCarga(planId, "qr") { c.api.qr(planId).codigo }
    Pantalla { arriba, abajo ->
        Column(
            Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = arriba + 8.dp, bottom = abajo + 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BotonIcono(I.cruz, { ir.atras() }, iconoTam = 20.dp, trazo = 2.6f, color = p.texto,
                    gel = if (p.oscuro) Gels.chipOscuro else Gel(vertical(C.blanco, Color(0xFFF3F0FA)), listOf(interna(2f, 0f, C.blanco), externa(3f, 0f, C.borde3))))
                T("Check-in", 26f, 800, alto = 1f, espaciado = px(-.8f, 26f))
            }
            plan.dato?.let { pl ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(pl.titulo, 20f, 700, alto = 1.15f)
                    T(listOf(Fecha.cuando(pl.empiezaEn), pl.lugar?.nombre ?: pl.lugarNombre.ifBlank { pl.zona }).filter { it.isNotBlank() }.joinToString(" · "), 13f, 600, p.texto2)
                }
            }
            Box(Modifier.padding(top = 10.dp).fillMaxWidth(.82f).aspectRatio(1f).gel(30.dp, gelQr).padding(20.dp), contentAlignment = Alignment.Center) {
                val cod = qr.dato
                when {
                    cod != null -> CodigoQr(cod, Modifier.fillMaxSize())
                    qr.error != null -> T(qr.error!!, 14f, 600, C.texto2, alinear = TextAlign.Center)
                    else -> Puntitos()
                }
            }
            // Quien organiza muestra el del plan; alguien aceptado, su QR personal (lo valida el lugar).
            val mio = plan.dato?.let { it.miEstado != "organizador" } == true
            T(
                if (mio) "Mostralo en la puerta: lo escanean y queda tu check-in." else "Mostralo cuando llegue la gente: lo escanean y cuenta su asistencia.",
                14f, 600, p.texto2, alto = 1.5f, alinear = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.background(p.superficie, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                T("Subí el brillo si está oscuro.", 13f, 700, if (p.oscuro) C.lima else C.violetaTexto)
            }
        }
    }
}

/** Escanear el QR del organizador para hacer check-in (metodo qr). */
@Composable
fun EscanearQr(ir: Ir, planId: String) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var leyendo by remember { mutableStateOf(false) }
    val (permiso, pedir) = rememberPermiso(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) { if (!permiso) pedir() }

    Pantalla(fondo = Modifier.background(C.oscuro6)) { arriba, abajo ->
        if (permiso) {
            EscanerCodigos(Modifier.fillMaxSize()) { codigo ->
                if (leyendo) return@EscanerCodigos
                leyendo = true
                alcance.launch {
                    try {
                        c.api.checkin(planId, Api.Llegada("qr", codigo = codigo))
                        avisos.ok("Listo, tu asistencia quedó registrada.")
                        ir.atras()
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        avisos.error(mensajeDe(e))
                        delay(2500)
                        leyendo = false
                    }
                }
            }
        }
        // Marco con esquinas lima (como el del frente del DNI).
        Box(
            Modifier.align(Alignment.Center).size(250.dp).drawBehind {
                val l = 34.dp.toPx(); val w = 4.dp.toPx()
                val esquinas = listOf(Offset(0f, 0f) to (1 to 1), Offset(size.width, 0f) to (-1 to 1), Offset(0f, size.height) to (1 to -1), Offset(size.width, size.height) to (-1 to -1))
                esquinas.forEach { (o, d) ->
                    drawLine(C.lima, o, Offset(o.x + d.first * l, o.y), w, StrokeCap.Round)
                    drawLine(C.lima, o, Offset(o.x, o.y + d.second * l), w, StrokeCap.Round)
                }
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = arriba + 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BotonIcono(I.cruz, { ir.atras() }, iconoTam = 20.dp, trazo = 2.4f, gel = Gels.chipOscuro, color = C.blanco)
            T("Escaneá el QR del plan", 22f, 700, C.blanco)
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = abajo + 40.dp, start = 24.dp, end = 24.dp)
                .background(rgba(255, 255, 255, .12f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            T(
                if (!permiso) "Necesitamos la cámara para leer el QR." else if (leyendo) "Leyendo…" else "Pedile el QR a quien organiza y apuntalo con la cámara.",
                13f, 700, C.blanco, alinear = TextAlign.Center,
            )
        }
    }
}
