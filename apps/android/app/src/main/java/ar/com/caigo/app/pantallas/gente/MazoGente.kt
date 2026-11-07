package ar.com.caigo.app.pantallas.gente

import ar.com.caigo.app.pantallas.planes.colorDeCategoria

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.Tarjeta
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.nucleo.Encantas
import ar.com.caigo.app.nucleo.Mazo
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.altoTabBar
import ar.com.caigo.app.piezas.Avatar
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
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
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** "98% asistencia" o "Nuevo en Caigo". */
internal fun asistencia(fue: Int, de: Int): String = if (de > 0) "${(fue * 100 + de / 2) / de}% asistencia" else "Nuevo en Caigo"

/** Botones redondos gel del mazo. */
internal val gelPaso = Gel(
    vertical(C.blanco, Color(0xFFF3F0FA)),
    listOf(interna(2f, 0f, C.blanco), interna(-4f, 0f, rgba(255, 120, 104, .18f)), externa(4f, 0f, Color(0xFFE4D6DA)), externa(14f, 22f, rgba(80, 60, 160, .35f), spread = -10f)),
)
internal val gelCorazon = Gel(
    vertical(0f to C.coral0, .35f to Color(0xFFFF8A7B), .6f to C.coral, 1f to Color(0xFFE85544)),
    listOf(interna(3f, 2f, rgba(255, 255, 255, .6f)), interna(-5f, 0f, rgba(160, 40, 30, .3f)), externa(4f, 0f, Color(0xFFC9432F)), externa(16f, 26f, rgba(255, 120, 104, .8f), spread = -10f)),
)
internal val gelVioletaRedondo = Gel(
    Gels.violeta.fondo,
    listOf(interna(3f, 2f, rgba(255, 255, 255, .45f)), interna(-4f, 0f, rgba(40, 20, 140, .35f)), externa(4f, 0f, C.violetaBase), externa(14f, 22f, rgba(116, 86, 241, .75f), spread = -10f)),
)

/**
 * Modos Gente y Citas del prototipo: mazo de personas arrastrables. Derecha (más de 100 px) = me
 * prendo / me interesa; izquierda = paso. Tocar arriba a los costados cambia de foto; tocar abajo abre
 * el perfil.
 */
@Composable
internal fun MazoGente(ir: Ir, modo: String, modifier: Modifier = Modifier) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val esCitas = modo == "citas"
    val mazo = rememberCarga(modo) { c.api.mazo(modo).personas }
    val recibidos = rememberCarga(modo) { runCatching { c.api.recibidos(modo) }.getOrNull() }
    val plus = rememberCarga { runCatching { c.api.plus().estado }.getOrNull() }
    var estado by remember(modo) { mutableStateOf(Mazo.Estado()) }
    var sumados by remember { mutableIntStateOf(0) }
    var invitando by remember { mutableStateOf<Tarjeta?>(null) }
    val dx = remember(modo) { Animatable(0f) }
    val dy = remember(modo) { Animatable(0f) }
    val densidad = LocalDensity.current.density
    val personas = mazo.dato.orEmpty()
    val arriba = personas.getOrNull(estado.indice)
    val siguiente = personas.getOrNull(estado.indice + 1)

    /** Reacciona y hace volar la carta. accion: prendo | interesa | encanta | paso. */
    fun reaccionar(accion: String, mensaje: String? = null) {
        val t = arriba ?: return
        alcance.launch {
            val lado = if (accion == "paso") -1 else 1
            if (lado > 0) {
                val e = plus.dato
                if (e != null && !e.activo && e.sumadasHoy >= e.limite) { volver(dx, dy); ir.plus("limit"); return@launch }
                if (accion == "encanta" && e != null && Encantas.quedan(e.encantasHoy, e.limiteEncantas) == 0) {
                    volver(dx, dy)
                    avisos.error(Encantas.aviso(e.activo, e.limiteEncantas))
                    if (!e.activo) ir.plus("perfil")
                    return@launch
                }
            }
            // La carta vuela ya; la API responde mientras tanto.
            estado = Mazo.reducir(estado, Mazo.Accion.Vuela(lado))
            val r = runCatching { c.api.reaccionar(t.id, modo, accion) }
            launch { dy.animateTo(dy.value - 40f, tween(260, easing = FastOutSlowInEasing)) }
            dx.animateTo(lado * Mazo.SALIDA, tween(260, easing = FastOutSlowInEasing))
            r.onSuccess { res ->
                plus.dato = res.plus
                if (lado > 0) {
                    sumados++
                    avisos.ok(
                        mensaje ?: when {
                            res.mutuo -> "¡Es mutuo con ${t.nombre}! Ya pueden hablar."
                            accion == "encanta" -> "Le mandaste un «me encanta» a ${t.nombre}. Si es mutuo, se abre el chat."
                            esCitas -> "Le avisamos a ${t.nombre} que te interesa. Si es mutuo, se abre el chat."
                            else -> "${t.nombre} se suma a tu gente. Le van a llegar tus próximos planes."
                        },
                    )
                }
                estado = Mazo.reducir(estado, Mazo.Accion.Siguiente)
                dx.snapTo(0f); dy.snapTo(0f)
            }.onFailure { e ->
                estado = estado.copy(vuela = 0)
                volver(dx, dy)
                when {
                    e is ErrorApi && e.codigo == "limite" -> ir.plus("limit")
                    e is ErrorApi && e.codigo == "limite_encanta" -> {
                        avisos.error(e.detalle)
                        plus.dato = plus.dato?.let { it.copy(encantasHoy = it.limiteEncantas) }
                        if (plus.dato?.activo != true) ir.plus("perfil")
                    }
                    else -> avisos.error(mensajeDe(e))
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        // Fila de arriba: quién te sumó y cuántas sumadas te quedan.
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 54.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val rs = recibidos.dato
            Row(
                Modifier.toque(alBajar = 2.dp) { ir.recibidos(modo, 0) }.gel(17.dp, Gels.neutra).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.padding(start = 8.dp)) {
                    rs?.personas.orEmpty().take(3).forEachIndexed { i, r ->
                        Avatar("·", r.foto, 26.dp, Modifier.offset(x = (-8 * i).dp).then(if (rs?.bloqueada == true) Modifier.blur(3.dp) else Modifier), fondo = C.oscuro2 to C.oscuro2)
                    }
                }
                val n = rs?.personas?.size ?: 0
                T(if (esCitas) "$n les interesás" else "$n te sumaron", 13f, 800, C.carbon, unRenglon = true)
            }
            val e = plus.dato
            Row(
                Modifier.toque(alBajar = 2.dp) { if (e?.activo == true) avisos.ok("Ya sos Caigo Plus. Disfrutalo.") else ir.plus("perfil") }
                    .gel(13.dp, Gels.neutra).padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val sumadas = ((e?.limite ?: 3) - (e?.sumadasHoy ?: 0)).coerceAtLeast(0)
                Icono(I.mas, 13.dp, C.violetaTexto, 3.4f)
                if (esCitas) {
                    // En Citas la misma pill suma los «me encanta» que quedan hoy (1 sin Plus, 5 con
                    // Plus), con la estrella del botón; con las palabras no entra junto a «les interesás».
                    T(if (e?.activo == true) "Plus" else "$sumadas", 12f, 800, C.violetaTexto, unRenglon = true)
                    T("·", 12f, 800, C.violetaTexto)
                    Icono(I.estrella, 13.dp, Color.Transparent, 0f, relleno = C.violetaTexto)
                    T("${Encantas.quedan(e?.encantasHoy ?: 0, e?.limiteEncantas ?: 1)}", 12f, 800, C.violetaTexto, unRenglon = true)
                } else {
                    T(if (e?.activo == true) "Plus · sin límite" else "$sumadas sumadas hoy", 12f, 800, C.violetaTexto, unRenglon = true)
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 98.dp, bottom = altoTabBar() + 96.dp, start = 14.dp, end = 14.dp)) {
            val alto = maxHeight
            when {
                mazo.dato == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (mazo.error != null) T(mazo.error!!, 15f, 500, LocalPaleta.current.texto2, alinear = TextAlign.Center) else Puntitos()
                }
                arriba == null -> MazoVacio(sumados) { mazo.recargar(); estado = Mazo.Estado() }
                else -> {
                    val k = Mazo.fuerza(dx.value)
                    if (siguiente != null) {
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer {
                                    val s = Mazo.escalaSiguiente(dx.value)
                                    scaleX = s; scaleY = s
                                    translationY = 16f * (1f - k) * density
                                    alpha = Mazo.alfaSiguiente(dx.value)
                                }
                                .clip(RoundedCornerShape(34.dp)).background(C.oscuro2),
                        ) {
                            AsyncImage(siguiente.fotos.firstOrNull(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(.8f, .8f, .8f, 1f) }))
                        }
                    }
                    CartaPersona(
                        arriba, esCitas, estado.foto,
                        Modifier.fillMaxSize().zIndex(1f)
                            // La entrada va antes de la transformación: así las posiciones son de la pantalla y no de la carta que se mueve.
                            .pointerInput(arriba.id) {
                                awaitEachGesture {
                                    val inicio = awaitFirstDown()
                                    estado = Mazo.reducir(estado, Mazo.Accion.Empieza)
                                    var tx = 0f
                                    var ty = 0f
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == inicio.id } ?: break
                                        // Desde donde arrancó el dedo (incluye el último movimiento antes de soltar).
                                        tx = (ch.position.x - inicio.position.x) / densidad
                                        ty = (ch.position.y - inicio.position.y) / densidad
                                        ch.consume()
                                        if (!ch.pressed) break
                                        alcance.launch { dx.snapTo(tx); dy.snapTo(ty) }
                                        estado = Mazo.reducir(estado, Mazo.Accion.Mueve(tx, ty))
                                    }
                                    estado = Mazo.reducir(estado, Mazo.Accion.Mueve(tx, ty))
                                    if (kotlin.math.abs(tx) < 6 && kotlin.math.abs(ty) < 6) {
                                        // Toque: abajo abre el perfil, arriba cambia de foto.
                                        estado = estado.copy(arrastrando = false)
                                        val ancho = size.width
                                        if (inicio.position.y > size.height * .58f) ir.persona(arriba.id, if (esCitas) "citas" else "gente")
                                        else estado = Mazo.reducir(estado, Mazo.Accion.Foto(if (inicio.position.x < ancho / 2) -1 else 1, arriba.fotos.size))
                                    } else {
                                        val sale = Mazo.reducir(estado, Mazo.Accion.Suelta)
                                        if (sale.vuela != 0) {
                                            estado = estado.copy(arrastrando = false)
                                            reaccionar(if (sale.vuela < 0) "paso" else if (esCitas) "interesa" else "prendo")
                                        } else {
                                            estado = sale
                                            alcance.launch { volver(dx, dy) }
                                        }
                                    }
                                }
                            }
                            .graphicsLayer {
                                translationX = dx.value * density
                                translationY = dy.value * density
                                rotationZ = Mazo.rotacion(dx.value)
                            },
                        tinte = dx.value,
                        alto = alto,
                        onPerfil = { ir.persona(arriba.id, if (esCitas) "citas" else "gente") },
                    )
                }
            }
        }

        // Botones: pasar, sumar / me interesa y el tercero (invitar a un plan / me encanta).
        if (arriba != null) {
            val k = Mazo.fuerza(dx.value)
            val escMeGusta by animateFloatAsState(1f + .15f * (if (dx.value > 0) k else 0f), tween(150), label = "mg")
            val escPaso by animateFloatAsState(1f + .18f * (if (dx.value < 0) k else 0f), tween(150), label = "ps")
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = altoTabBar() + 14.dp).height(76.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                BotonRedondo(60.dp, gelPaso, Modifier.scale(escPaso), { reaccionar("paso") }) { Icono(I.cruz, 24.dp, C.coral2, 3.2f) }
                if (esCitas) {
                    BotonRedondo(76.dp, gelCorazon, Modifier.scale(escMeGusta), { reaccionar("interesa") }) { Icono(I.corazon, 34.dp, Color.Transparent, 0f, relleno = C.blanco) }
                    BotonRedondo(60.dp, gelVioletaRedondo, Modifier, { reaccionar("encanta") }) { Icono(I.estrella, 26.dp, Color.Transparent, 0f, relleno = C.lima) }
                } else {
                    BotonRedondo(76.dp, Gels.limaChico, Modifier.scale(escMeGusta), { reaccionar("prendo") }) { Icono(I.mas, 32.dp, C.carbon, 3.4f) }
                    BotonRedondo(60.dp, gelVioletaRedondo, Modifier, { invitando = arriba }) { Icono(I.calendarioSimple, 24.dp, C.blanco, 2.4f) }
                }
            }
        }
        invitando?.let { t -> HojaInvitar(ir, t, { invitando = null }) { plan -> reaccionar("prendo", "Invitaste a ${t.nombre} a ${plan.titulo}. Le llega como solicitud.") } }
    }
}

private suspend fun volver(dx: Animatable<Float, *>, dy: Animatable<Float, *>) {
    kotlinx.coroutines.coroutineScope {
        launch { dx.animateTo(0f, tween(260)) }
        launch { dy.animateTo(0f, tween(260)) }
    }
}

@Composable
internal fun BotonRedondo(tam: Dp, gel: Gel, modifier: Modifier, onClick: () -> Unit, contenido: @Composable () -> Unit) {
    Box(modifier.size(tam).toque(onClick = onClick).gel(tam / 2, gel), contentAlignment = Alignment.Center) { contenido() }
}

/** Badge de verificado (lima con tilde). */
@Composable
internal fun Verificado(tam: Dp = 24.dp, color: Color = C.lima, tilde: Color = C.carbon) {
    Box(contentAlignment = Alignment.Center) {
        Icono(I.verificado, tam, Color.Transparent, 0f, relleno = color)
        Icono(I.checkChico, tam * .75f, tilde, 2.6f)
    }
}

/** La carta de una persona (foto con barras, nombre y edad, zona, chips). */
@Composable
private fun CartaPersona(t: Tarjeta, esCitas: Boolean, foto: Int, modifier: Modifier, tinte: Float, alto: Dp, onPerfil: () -> Unit) {
    Box(
        modifier
            .gel(34.dp, null, listOf(externa(28f, 44f, rgba(36, 33, 45, .65f), spread = -18f), externa(0f, 0f, rgba(36, 33, 45, .06f), spread = 1f)))
            .clip(RoundedCornerShape(34.dp)).background(C.oscuro2),
    ) {
        AsyncImage(t.fotos.getOrNull(foto) ?: t.fotos.firstOrNull(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(0f, -.4f))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to rgba(32, 29, 43, .4f), .16f to rgba(32, 29, 43, 0f), .6f to rgba(32, 29, 43, 0f),
                    .78f to rgba(32, 29, 43, .65f), 1f to rgba(32, 29, 43, .95f),
                ),
            ),
        )
        // Tinte lima (me prendo) o coral (paso) según el lado del arrastre.
        val k = Mazo.fuerza(tinte)
        if (k > 0f) {
            val col = if (tinte > 0) C.lima else C.coral
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.35f to col.copy(alpha = 0f), 1f to col.copy(alpha = .4f * k))))
        }
        // Barras de fotos.
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 14.dp, end = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            t.fotos.forEachIndexed { i, _ ->
                Box(Modifier.weight(1f).height(4.dp).background(if (i == foto) C.blanco else rgba(255, 255, 255, .38f), RoundedCornerShape(2.dp)))
            }
        }
        // Sellos ME PRENDO / ME INTERESA y PASO.
        Sello(if (esCitas) "ME INTERESA" else "ME PRENDO", C.lima, -12f, Mazo.alfaMeGusta(tinte), Modifier.padding(top = 44.dp, start = 22.dp))
        Sello("PASO", C.coral, 12f, Mazo.alfaPaso(tinte), Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 22.dp))

        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 16.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(t.nombre, 32f, 800, C.blanco, alto = 1f, espaciado = px(-.8f, 32f), unRenglon = true)
                        t.edad?.let { T("$it", 28f, 500, Color(0xFFE4E0EE), alto = 1f) }
                        if (t.verificada) Verificado()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icono(I.pin, 14.dp, Color(0xFFE4E0EE), 2.4f)
                        val meta = listOf(t.zona, if (esCitas) t.trabajo else asistencia(t.asistencia.fue, t.asistencia.de)).filter { it.isNotBlank() }.joinToString(" · ")
                        T(meta, 13f, 600, Color(0xFFE4E0EE), unRenglon = true)
                    }
                }
                Box(
                    Modifier.size(40.dp).toque(onClick = onPerfil).gel(20.dp, vertical(rgba(255, 255, 255, .22f), rgba(255, 255, 255, .22f)), listOf(interna(1f, 0f, rgba(255, 255, 255, .35f)))),
                    contentAlignment = Alignment.Center,
                ) { Icono(I.arriba, 18.dp, C.blanco, 2.8f) }
            }
            if (esCitas) {
                t.prompts.firstOrNull()?.r?.takeIf { it.isNotBlank() }?.let { r ->
                    Row(
                        Modifier.gel(12.dp, vertical(rgba(255, 255, 255, .16f), rgba(255, 255, 255, .16f)), listOf(interna(1f, 0f, rgba(255, 255, 255, .2f)))).padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icono(I.corazonChico, 13.dp, Color.Transparent, 0f, relleno = C.coral)
                        T(r, 12f, 700, C.blanco, unRenglon = true)
                    }
                }
            } else {
                t.proximos.firstOrNull()?.let { pr ->
                    Row(
                        Modifier.background(C.lima, RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icono(I.calendarioSimple, 12.dp, C.carbon, 3f)
                        T("Va a ${pr.titulo}", 12f, 800, C.carbon, unRenglon = true)
                    }
                }
                val tags = (t.enComun + t.intereses).distinct().take(4)
                if (tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { g ->
                        Box(Modifier.gel(12.dp, vertical(rgba(255, 255, 255, .16f), rgba(255, 255, 255, .16f)), listOf(interna(1f, 0f, rgba(255, 255, 255, .18f)))).padding(horizontal = 11.dp, vertical = 6.dp)) {
                            T(g, 12f, 700, C.blanco, unRenglon = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sello(texto: String, color: Color, grados: Float, alfa: Float, modifier: Modifier) {
    if (alfa <= 0f) return
    Box(
        modifier.alpha(alfa).rotate(grados).background(rgba(32, 29, 43, .35f), RoundedCornerShape(14.dp))
            .border(3.dp, color, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { T(texto, 26f, 800, color, alto = 1f, unRenglon = true) }
}

/** "Ya viste a todos por hoy". */
@Composable
private fun MazoVacio(sumados: Int, onOtraVez: () -> Unit) {
    Column(
        Modifier.fillMaxSize().gel(34.dp, vertical(Color(0xFFF3EFFF), Color(0xFFE9E2FF))).padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(120.dp))
        T("Ya viste a todos por hoy", 26f, 700, C.carbon, alto = 1.1f, alinear = TextAlign.Center)
        T("Sumaste $sumados a tu gente. ¿Y si armás algo con ellos?", 14f, 400, C.texto2, alto = 1.5f, alinear = TextAlign.Center)
        Box(
            Modifier.toque(onClick = onOtraVez).gel(16.dp, vertical(C.blanco, Color(0xFFF3F0FA)), listOf(interna(2f, 0f, C.blanco), interna(-3f, 0f, rgba(116, 86, 241, .1f)), externa(3f, 0f, C.borde3)))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) { T("Ver de nuevo", 14f, 800, C.carbon) }
    }
}

/** Elegir a cuál de mis planes invito a la persona (armado con las tarjetas del handoff). */
@Composable
internal fun HojaInvitar(ir: Ir, t: Tarjeta, cerrar: () -> Unit, alInvitar: (Plan) -> Unit) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val mios = rememberCarga { c.api.mios("organizo").planes }
    ar.com.caigo.app.app.OcultarTabBar(true)
    Box(Modifier.fillMaxSize().zIndex(30f).background(rgba(32, 29, 43, .45f)).tocable(cerrar)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().tocable { }
                .gel(28.dp, vertical(C.blancoCalido, C.blancoCalido)).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 34.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T("¿A qué plan invitás a ${t.nombre}?", 22f, 700, C.carbon, alto = 1.1f, espaciado = px(-.4f, 22f))
            val ps = mios.dato
            when {
                ps == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Puntitos() }
                ps.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T("Todavía no organizás nada. Armá un plan y después invitás.", 15f, 400, C.texto2, alto = 1.5f)
                    ar.com.caigo.app.piezas.Cta("Armá un plan", { cerrar(); ir.crear() }, Modifier.fillMaxWidth(), alto = 56.dp, tam = 16f)
                }
                else -> LazyColumn(Modifier.height((ps.size.coerceAtMost(4) * 84).dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ps) { p ->
                        Row(
                            Modifier.fillMaxWidth().toque(alBajar = 2.dp) {
                                alcance.launch {
                                    runCatching { c.api.invitar(p.id, t.id) }
                                        .onSuccess { cerrar(); alInvitar(p) }
                                        .onFailure { avisos.error(mensajeDe(it)) }
                                }
                            }.gel(20.dp, Gels.neutra).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ar.com.caigo.app.piezas.Foto(p.foto, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)), colorDeCategoria(p.categoria))
                            Column(Modifier.weight(1f)) {
                                T(p.titulo, 15f, 700, C.carbon, unRenglon = true)
                                T(ar.com.caigo.app.piezas.Fecha.cuando(p.empiezaEn) + " · " + ar.com.caigo.app.nucleo.Cupo.badge(p.faltan), 12f, 500, C.texto2, unRenglon = true)
                            }
                            Icono(I.adelante, 18.dp, C.violeta, 2.4f)
                        }
                    }
                }
            }
        }
    }
}
