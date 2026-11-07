package ar.com.caigo.app.piezas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.dibujarGrano
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.fondoPantalla
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/** Fondo de pantalla del handoff: color + brillo lavanda (o violeta en oscuro) + grano. */
fun Modifier.fondoCaigo(oscuro: Boolean, brillo: Brush? = null): Modifier = this
    .background(if (oscuro) C.grafito else C.blancoCalido)
    .drawBehind {
        val p = Path().apply { addRect(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, size)) }
        drawPath(p, brillo ?: fondoPantalla(if (oscuro) ar.com.caigo.app.tema.PaletaOscura else ar.com.caigo.app.tema.PaletaClara))
        dibujarGrano(p)
    }

/** Toca sin ripple; mientras está apretado baja 3 px (el `style-active` de los botones gel). */
@Composable
fun Modifier.toque(habilitado: Boolean = true, alBajar: Dp = 3.dp, onClick: () -> Unit): Modifier {
    val fuente = remember { MutableInteractionSource() }
    val apretado by fuente.collectIsPressedAsState()
    val y by animateDpAsState(if (apretado && habilitado) alBajar else 0.dp, tween(80), label = "toque")
    val px = with(LocalDensity.current) { y.toPx() }
    return this
        .graphicsLayer { translationY = px }
        .clickable(interactionSource = fuente, indication = null, enabled = habilitado, onClick = onClick)
}

/** Simple: clickable sin ripple ni movimiento. */
@Composable
fun Modifier.tocable(onClick: () -> Unit): Modifier =
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)

/**
 * CTA gel (lima por defecto, 60 px de alto, radio 20, `800 17px`). Lo usan todos los "Seguir",
 * "Sumarme", "Publicar plan"…
 */
@Composable
fun Cta(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gel: Gel = Gels.limaChico,
    alto: Dp = 60.dp,
    radio: Dp = 20.dp,
    tam: Float = 17f,
    peso: Int = 800,
    color: Color = C.grafito,
    icono: String? = null,
    iconoTrazo: Float = 3.4f,
    iconoTam: Dp = 16.dp,
    habilitado: Boolean = true,
    cargando: Boolean = false,
) {
    Box(
        modifier
            .height(alto)
            .alpha(if (habilitado) 1f else .5f)
            .toque(habilitado && !cargando, onClick = onClick)
            .gel(radio, gel),
        contentAlignment = Alignment.Center,
    ) {
        if (cargando) {
            Puntitos(color)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icono != null) Icono(icono, iconoTam, color, iconoTrazo)
                T(texto, tam, peso, color, unRenglon = true)
            }
        }
    }
}

/** Tres puntitos que laten: el "cargando" de los botones, armado con los colores del handoff. */
@Composable
fun Puntitos(color: Color = C.violeta, tam: Dp = 8.dp) {
    var fase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(220); fase = (fase + 1) % 3 } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by animateFloatAsState(if (i == fase) 1f else .35f, tween(200), label = "p")
            Box(Modifier.size(tam).alpha(a).background(color, CircleShape))
        }
    }
}

/** Botón ícono claro de 44×44 (volver, cerrar, compartir). */
@Composable
fun BotonIcono(
    d: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tam: Dp = 44.dp,
    radio: Dp = 16.dp,
    iconoTam: Dp = 22.dp,
    trazo: Float = 2.2f,
    gel: Gel = Gels.icono,
    color: Color = C.carbon,
    relleno: Color? = null,
) {
    Box(
        modifier.size(tam).toque(onClick = onClick).gel(radio, gel),
        contentAlignment = Alignment.Center,
    ) { Icono(d, iconoTam, color, trazo, relleno) }
}

/** Botón ícono sobre foto (detalle del plan): vidrio oscuro translúcido. */
val GelVidrio = Gel(
    vertical(rgba(255, 255, 255, .92f), rgba(240, 236, 255, .92f)),
    listOf(ar.com.caigo.app.tema.interna(1.5f, 0f, C.blanco), ar.com.caigo.app.tema.interna(-2f, 0f, rgba(116, 86, 241, .1f))),
)

/** Chip lima «Plus» del prototipo (lo que es de Caigo Plus, cuando no lo tenés): 800 11px, 6×10, radio 11. */
@Composable
fun ChipPlus(texto: String = "Plus", modifier: Modifier = Modifier) {
    Box(modifier.gel(11.dp, Brush.verticalGradient(listOf(C.lima, C.lima))).padding(horizontal = 10.dp, vertical = 6.dp)) {
        T(texto, 11f, 800, C.carbon, unRenglon = true)
    }
}

/** Chip de selección (SEL violeta / OFF lavanda), `600 14px`, padding 9×14, radio 14. */
@Composable
fun Chip(
    texto: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gelOn: Gel = Gels.sel,
    gelOff: Gel = Gels.off,
    colorOn: Color = C.blanco,
    colorOff: Color = C.carbon,
    tam: Float = 14f,
    pesoOn: Int = 700,
    pesoOff: Int = 600,
    padH: Dp = 14.dp,
    padV: Dp = 9.dp,
    radio: Dp = 14.dp,
) {
    Box(
        modifier.toque(onClick = onClick).gel(radio, if (on) gelOn else gelOff).padding(horizontal = padH, vertical = padV),
        contentAlignment = Alignment.Center,
    ) { T(texto, tam, if (on) pesoOn else pesoOff, if (on) colorOn else colorOff, unRenglon = true) }
}

/** Segmentado: pista lavanda con padding 4 y la opción activa blanca con sombra. */
@Composable
fun Segmentado(
    opciones: List<String>,
    seleccion: Int,
    onSeleccion: (Int) -> Unit,
    modifier: Modifier = Modifier,
    radio: Dp = 16.dp,
    radioOpcion: Dp = 12.dp,
    alto: Dp = 44.dp,
    tam: Float = 14f,
    llenar: Boolean = true,
    padOpcion: Dp = 14.dp,
    gelOn: (Int) -> Gel? = { null },
    colorOn: (Int) -> Color? = { null },
) {
    val p = LocalPaleta.current
    Row(
        modifier
            .gel(radio, Brush.verticalGradient(listOf(p.segPista, p.segPista)),
                if (p.oscuro) listOf(ar.com.caigo.app.tema.interna(2f, 4f, rgba(0, 0, 0, .3f))) else emptyList())
            .padding(4.dp),
    ) {
        opciones.forEachIndexed { i, o ->
            val on = i == seleccion
            Box(
                (if (llenar) Modifier.weight(1f) else Modifier)
                    .height(alto - 8.dp)
                    .tocable { onSeleccion(i) }
                    .then(if (on) Modifier.gel(radioOpcion, gelOn(i) ?: if (p.oscuro) Gels.segOnOscuro else Gels.segOn) else Modifier)
                    .padding(horizontal = padOpcion),
                contentAlignment = Alignment.Center,
            ) {
                T(o, tam, if (on) 700 else 600, if (on) (colorOn(i) ?: p.segOnTexto) else p.segOffTexto, unRenglon = true)
            }
        }
    }
}

/** Badge "Faltan N" (coral gel) o "Completo" (lima). */
@Composable
fun BadgeFalta(faltan: Int, modifier: Modifier = Modifier, tam: Float = 18f, texto: String = Cupo.badge(faltan)) {
    val completo = Cupo.completo(faltan)
    Box(
        modifier.gel(13.dp, if (completo) Gels.limaBadge else Gels.coralBadge).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { T(texto, tam, 700, C.carbon, alto = 1f, unRenglon = true) }
}

/** Colores de los avatares con inicial (los del mockup: violeta, lima, coral, lavanda, grafito). */
private val coloresAvatar = listOf(
    C.violeta to C.blanco, C.lima to C.carbon, C.coral to C.carbon, C.lavanda to C.violetaTexto, C.grafito to C.blanco,
)

fun colorAvatar(semilla: String): Pair<Color, Color> = coloresAvatar[(semilla.hashCode() and 0x7fffffff) % coloresAvatar.size]

/** Avatar redondo: foto si hay, si no la inicial sobre color con brillo arriba a la izquierda. */
@Composable
fun Avatar(
    nombre: String,
    foto: String?,
    tam: Dp = 30.dp,
    modifier: Modifier = Modifier,
    borde: Dp = 2.dp,
    colorBorde: Color = LocalPaleta.current.bordeAvatar,
    texto: String? = null,
    fondo: Pair<Color, Color>? = null,
    tamTexto: Float = (tam.value * .4f),
) {
    val (bg, fg) = fondo ?: colorAvatar(nombre)
    Box(
        modifier
            .size(tam)
            .clip(CircleShape)
            .background(bg)
            .background(radial(.3f, .25f, 0f, 0f, 0f to rgba(255, 255, 255, .6f), .42f to rgba(255, 255, 255, 0f), circulo = true))
            .then(if (borde > 0.dp) Modifier.border(borde, colorBorde, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!foto.isNullOrBlank()) {
            AsyncImage(foto, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            if (borde > 0.dp) Box(Modifier.fillMaxSize().border(borde, colorBorde, CircleShape))
        } else {
            T(texto ?: nombre.take(1).uppercase(), tamTexto, 800, fg)
        }
    }
}

/** Avatares apilados (margen -8) con "+N" lavanda al final. */
@Composable
fun Avatares(
    personas: List<Pair<String, String?>>,
    total: Int = personas.size,
    max: Int = 3,
    tam: Dp = 30.dp,
    solape: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        personas.take(max).forEachIndexed { i, (n, f) ->
            Avatar(n, f, tam, Modifier.offset(x = -solape * i).zIndex((max - i).toFloat()))
        }
        val resto = total - minOf(max, personas.size)
        if (resto > 0) {
            Avatar("+", null, tam, Modifier.offset(x = -solape * minOf(max, personas.size)), texto = "+$resto", fondo = C.pista to C.violetaTexto)
        }
    }
}

/** Foto de red (Coil) con un tinte de fondo mientras carga. */
@Composable
fun Foto(url: String?, modifier: Modifier = Modifier, tinte: Color = C.oscuro2) {
    Box(modifier.background(tinte)) {
        if (!url.isNullOrBlank()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Interruptor del handoff: 50×30, violeta prendido, perilla blanca de 24. */
@Composable
fun Interruptor(on: Boolean, onCambio: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val x by animateDpAsState(if (on) 23.dp else 3.dp, tween(200), label = "x")
    Box(
        modifier
            .size(50.dp, 30.dp)
            .tocable { onCambio(!on) }
            .gel(15.dp, Brush.verticalGradient(listOf(if (on) C.violeta else Color(0xFFDDD8EA), if (on) C.violeta else Color(0xFFDDD8EA))),
                listOf(ar.com.caigo.app.tema.interna(2f, 4f, rgba(0, 0, 0, .15f)))),
    ) {
        Box(
            Modifier.offset(x = x, y = 3.dp).size(24.dp)
                .gel(12.dp, Brush.verticalGradient(listOf(C.blanco, C.blanco)), listOf(externa(2f, 4f, rgba(0, 0, 0, .25f)))),
        )
    }
}

/** Barra de progreso de pasos (Crear plan, DNI): segmentos de 6 px, violeta / `#E4DEFA`. */
@Composable
fun BarraPasos(paso: Int, total: Int, modifier: Modifier = Modifier, actual: Color = C.violeta, siguiente: Color? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val c = when {
                i < paso -> actual
                i == paso && siguiente != null -> siguiente
                else -> C.pista
            }
            Box(Modifier.weight(1f).height(6.dp).background(c, RoundedCornerShape(3.dp)))
        }
    }
}

/** Barra segmentada del cupo: ocupados lima, vacíos con borde coral punteado. */
@Composable
fun BarraCupo(somos: Int, total: Int, modifier: Modifier = Modifier, alto: Dp = 10.dp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (total > 14) 3.dp else 5.dp)) {
        Cupo.segmentos(somos, total).forEach { lleno ->
            Box(
                Modifier.weight(1f).height(alto)
                    .then(
                        if (lleno) Modifier.background(vertical(C.cupoLleno1, C.cupoLleno2), RoundedCornerShape(alto / 2))
                        else Modifier.drawBehind {
                            val r = alto.toPx() / 2
                            drawRoundRect(
                                C.coral, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                            )
                        },
                    ),
            )
        }
    }
}

// ——— Toast (2,6 s, arriba, como el prototipo) ———

data class Aviso(val texto: String, val error: Boolean = false, val id: Long = System.nanoTime())

class Avisos {
    var actual by mutableStateOf<Aviso?>(null)
        private set

    fun ok(t: String) { actual = Aviso(t) }
    fun error(t: String) { actual = Aviso(t, error = true) }
    fun limpiar() { actual = null }
}

val LocalAvisos = staticCompositionLocalOf { Avisos() }

@Composable
fun BoxScope.CapaAvisos(avisos: Avisos, arriba: Dp) {
    val a = avisos.actual
    LaunchedEffect(a?.id) { if (a != null) { delay(2600); avisos.limpiar() } }
    AnimatedVisibility(
        a != null,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = arriba + 10.dp, start = 20.dp, end = 20.dp).zIndex(40f),
        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { 20 },
        exit = fadeOut(tween(200)),
    ) {
        val v = a ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth()
                .gel(20.dp, Brush.verticalGradient(listOf(C.carbon, C.carbon)), listOf(externa(18f, 30f, rgba(0, 0, 0, .5f), spread = -12f)))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(28.dp).background(if (v.error) C.coral else C.lima, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icono(if (v.error) I.cruz else I.check, 14.dp, C.carbon, 3.4f)
            }
            T(v.texto, 14f, 700, C.blancoCalido, alto = 1.4f, modifier = Modifier.weight(1f))
        }
    }
}

/** Fila con espacio que empuja. */
@Composable
fun RowScope.Empuje() = Spacer(Modifier.weight(1f))

/** Encabezado "volver + título + subtítulo" (Solicitudes, Chat, Tu entrada). */
@Composable
fun Encabezado(titulo: String, sub: String? = null, onVolver: () -> Unit, modifier: Modifier = Modifier, icono: String = I.atras, derecha: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonIcono(icono, onVolver)
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            T(titulo, 22f, 700, espaciado = ar.com.caigo.app.tema.px(-.4f, 22f), unRenglon = true)
            if (sub != null) T(sub, 13f, 500, LocalPaleta.current.texto2, unRenglon = true)
        }
        derecha()
    }
}

/** Texto centrado de pantalla vacía / error, armado con las piezas de los vacíos 13a–13c. */
@Composable
fun MensajeCentro(titulo: String, cuerpo: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T(titulo, 26f, 700, alinear = TextAlign.Center, alto = 1.1f, espaciado = ar.com.caigo.app.tema.px(-.6f, 26f))
        T(cuerpo, 15f, 400, LocalPaleta.current.texto2, alinear = TextAlign.Center, alto = 1.5f)
    }
}

@Composable
fun Separador(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(LocalPaleta.current.separador))

@Composable
fun Alto(d: Dp) = Spacer(Modifier.height(d))

@Composable
fun Ancho(d: Dp) = Spacer(Modifier.width(d))

/**
 * Pantalla completa con el fondo del handoff. El contenido recibe el alto de la barra de estado
 * ([arriba]: en el mockup son los 50 px del "9:41") y el de la barra de navegación ([abajo]).
 * Las medidas del mockup se pasan restando 50: `top:56px` → `arriba + 6.dp`.
 */
@Composable
fun Pantalla(
    modifier: Modifier = Modifier,
    oscuro: Boolean = LocalPaleta.current.oscuro,
    brillo: Brush? = null,
    fondo: Modifier? = null,
    contenido: @Composable BoxScope.(arriba: Dp, abajo: Dp) -> Unit,
) {
    val arriba = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val abajo = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier.fillMaxSize().then(fondo ?: Modifier.fondoCaigo(oscuro, brillo))
            .androidx_ime(),
    ) { contenido(arriba, abajo) }
}

/** Corre el contenido cuando aparece el teclado. */
@Composable
private fun Modifier.androidx_ime(): Modifier = this.then(Modifier.imePadding())
