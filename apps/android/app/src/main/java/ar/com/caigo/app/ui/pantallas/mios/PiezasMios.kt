package ar.com.caigo.app.ui.pantallas.mios

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.datos.Asist
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.estilo
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.radial
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical

/*
 * Piezas que comparten las pantallas de Mis planes, Perfil, Solicitudes, Chat y Check-in. Todas
 * salen del handoff (mismos gradientes y sombras); llevan el prefijo `mios` para no chocar con las
 * de otras pantallas del mismo paquete.
 */

/** Tarjeta blanca chica (`0 2px 0 #E8E3F4, 0 12px 24px -12px`), la de las filas de Mis planes y Solicitudes. */
internal val miosTarjeta = Gel(
    vertical(C.blanco, C.blanco),
    listOf(interna(1f, 0f, C.blanco), externa(2f, 0f, C.borde2), externa(12f, 24f, rgba(80, 60, 160, .22f), spread = -12f)),
)

internal val miosTarjetaOscura = Gel(
    vertical(C.oscuro3, C.oscuro4),
    listOf(interna(1f, 0f, rgba(255, 255, 255, .08f)), externa(12f, 24f, rgba(0, 0, 0, .5f), spread = -12f)),
)

@Composable
internal fun miosGelTarjeta(): Gel = if (LocalPaleta.current.oscuro) miosTarjetaOscura else miosTarjeta

/** Tarjeta oscura con grano (contador, asistencia real): `radial-gradient(120% 90% at 0% 0%, #3D3756, #24212D 62%)`. */
internal val miosOscura = Gel(
    radial(0f, 0f, 1.2f, .9f, 0f to C.oscuro1, .62f to C.carbon),
    listOf(interna(1f, 0f, rgba(255, 255, 255, .08f)), externa(12f, 26f, rgba(36, 33, 45, .55f), spread = -10f)),
    grano = true,
)

/** Tile violeta gel chico (ícono de "Verificación y seguridad"). */
internal val miosTileVioleta = Gel(
    vertical(0f to C.violeta1, .30f to C.violeta2, .62f to C.violeta, 1f to C.violeta4),
    listOf(interna(3f, 2f, rgba(255, 255, 255, .45f)), interna(-4f, 0f, rgba(40, 20, 140, .35f)), externa(3f, 0f, C.violetaBase)),
)

/** Botón violeta gel con base de 4 px (enviar del chat). */
internal val miosVioletaChico = Gel(
    vertical(0f to C.violeta1, .30f to C.violeta2, .62f to C.violeta, 1f to C.violeta4),
    listOf(
        interna(3f, 2f, rgba(255, 255, 255, .45f)),
        interna(-5f, 0f, rgba(40, 20, 140, .35f)),
        externa(4f, 0f, C.violetaBase),
        externa(16f, 26f, rgba(116, 86, 241, .75f), spread = -10f),
    ),
)

/** "94% asistencia · 11 planes", o "Nuevo en Caigo · todavía sin planes". */
internal fun miosAsistencia(a: Asist, extra: String? = null): String {
    val base = if (a.de <= 0) "Nuevo en Caigo · todavía sin planes" else "${a.fue * 100 / a.de}% asistencia · ${a.de} ${if (a.de == 1) "plan" else "planes"}"
    return if (extra != null && a.de > 0) "${a.fue * 100 / a.de}% asistencia · $extra" else base
}

/** Badge de verificado: el sello violeta con el check blanco del mockup 11. */
@Composable
internal fun MiosVerificado(tam: Dp = 22.dp) {
    Box(Modifier.size(tam), contentAlignment = Alignment.Center) {
        Icono(I.verificado, tam, Color.Transparent, 0f, relleno = C.violeta)
        Icono(I.checkChico, tam, C.blanco, 2.6f)
    }
}

/** Título de sección (`700 15px`). */
@Composable
internal fun MiosSeccion(texto: String, modifier: Modifier = Modifier) = T(texto, 15f, 700, modifier = modifier)

/** Overline ("HOY", "ESTA SEMANA"): 13 px, 800, letter-spacing 1 px. */
@Composable
internal fun MiosOverline(texto: String, modifier: Modifier = Modifier) =
    T(texto, 13f, 800, LocalPaleta.current.texto2, modifier, espaciado = px(1f, 13f))

/**
 * Estado vacío como 13b: dos círculos punteados lavanda + el "+" lima, título `700 30px`, texto y CTA.
 */
@Composable
internal fun MiosVacio(titulo: String, cuerpo: String, cta: String, onCta: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(2) { i ->
                Box(
                    Modifier.offset(x = (-18 * i).dp).size(76.dp).background(LocalPaleta.current.superficie, CircleShape)
                        .drawBehind {
                            drawCircle(
                                Color(0xFFB9ACF5), radius = size.minDimension / 2 - 1.5.dp.toPx(),
                                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                            )
                        },
                )
            }
            Box(
                Modifier.offset(x = (-36).dp).size(84.dp)
                    .gel(42.dp, radial(.32f, .26f, 0f, 0f, 0f to Color(0xFFF2FFC8), .4f to C.lima, 1f to Color(0xFFA6D640), circulo = true),
                        listOf(interna(-4f, 8f, rgba(110, 150, 20, .3f)), externa(12f, 22f, rgba(140, 190, 40, .6f), spread = -8f)))
                    .border(4.dp, LocalPaleta.current.fondo, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icono(I.mas, 34.dp, C.carbon, 3f) }
        }
        T(titulo, 30f, 700, alto = 1.08f, espaciado = px(-.6f, 30f), alinear = TextAlign.Center)
        T(cuerpo, 15f, 400, LocalPaleta.current.texto2, alto = 1.5f, alinear = TextAlign.Center)
        Cta(cta, onCta, Modifier.fillMaxWidth().padding(top = 8.dp), alto = 58.dp, tam = 16f)
    }
}

/**
 * Campo de texto con el estilo del registro (01d): 60 px de alto, radio 18, borde 1,5 px `#E6E1F2`
 * que pasa a 2 px violeta cuando tiene foco.
 */
@Composable
internal fun MiosCampo(
    valor: String,
    onValor: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    renglones: Int = 1,
    maximo: Int = 200,
    mayusculas: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val p = LocalPaleta.current
    var foco by remember { mutableStateOf(false) }
    BasicTextField(
        valor,
        { if (it.length <= maximo) onValor(it) },
        modifier
            .fillMaxWidth()
            .heightIn(min = if (renglones > 1) 96.dp else 60.dp)
            .background(if (p.oscuro) p.superficie else C.blanco, RoundedCornerShape(18.dp))
            .border(if (foco) 2.dp else 1.5.dp, if (foco) C.violeta else if (p.oscuro) C.oscuro1 else Color(0xFFE6E1F2), RoundedCornerShape(18.dp))
            .onFocusChanged { foco = it.isFocused },
        textStyle = estilo(16f, 600, p.texto, alto = 1.4f),
        singleLine = renglones == 1,
        minLines = renglones,
        maxLines = if (renglones == 1) 1 else 6,
        cursorBrush = SolidColor(C.violeta),
        keyboardOptions = KeyboardOptions(capitalization = mayusculas),
        decorationBox = { campo ->
            Box(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), contentAlignment = if (renglones == 1) Alignment.CenterStart else Alignment.TopStart) {
                if (valor.isEmpty()) T(placeholder, 16f, 500, C.inactivo)
                campo()
            }
        },
    )
}

/** Fila de una tarjeta de opciones (como "Herramientas para salir tranqui"): ícono, título y bajada. */
@Composable
internal fun MiosFila(
    icono: String,
    titulo: String,
    sub: String? = null,
    colorIcono: Color = C.violeta,
    ultima: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalPaleta.current
    Column {
        Row(
            Modifier.fillMaxWidth().tocable(onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icono(icono, 20.dp, colorIcono, 2.2f)
            Column(Modifier.weight(1f)) {
                T(titulo, 14f, 800)
                if (sub != null) T(sub, 12f, 400, p.texto2)
            }
            Icono(I.adelante, 18.dp, p.texto3, 2.2f)
        }
        if (!ultima) Box(Modifier.fillMaxWidth().height(1.dp).background(if (p.oscuro) p.separador else C.lavanda))
    }
}

/** Tarjeta neutra que agrupa filas (`radio 22`, `0 3px 0 #E6E0F3`). */
@Composable
internal fun MiosGrupo(contenido: @Composable ColumnScope.() -> Unit) {
    val p = LocalPaleta.current
    Column(
        Modifier.fillMaxWidth().gel(22.dp, if (p.oscuro) Gels.tarjetaOscura else Gels.neutra),
        content = contenido,
    )
}

/**
 * Hoja que sube desde abajo sobre un velo oscuro (como los overlays del prototipo), armada con la
 * tarjeta neutra.
 */
@Composable
internal fun BoxScope.MiosHoja(abierta: Boolean, onCerrar: () -> Unit, titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    val p = LocalPaleta.current
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    ar.com.caigo.app.ui.OcultarTabBar(abierta)
    AnimatedVisibility(abierta, Modifier.fillMaxSize().zIndex(30f), enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(rgba(21, 19, 28, .45f)).tocable(onCerrar))
    }
    AnimatedVisibility(
        abierta,
        Modifier.align(Alignment.BottomCenter).zIndex(31f),
        enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(p.fondo, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .tocable { }
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp + abajo),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).background(C.borde6, RoundedCornerShape(3.dp)))
            T(titulo, 20f, 700, espaciado = px(-.3f, 20f))
            contenido()
        }
    }
}

/** Pill chica (etiquetas "Adentro", "Esperando", "Completan"). */
@Composable
internal fun MiosPill(texto: String, fondo: Brush, color: Color, modifier: Modifier = Modifier, gel: Gel? = null, tam: Float = 13f) {
    Box(
        modifier.then(if (gel != null) Modifier.gel(10.dp, gel) else Modifier.background(fondo, RoundedCornerShape(10.dp)))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) { T(texto, tam, 800, color, unRenglon = true) }
}

/** "Lu", "Lu y Nico", "Lu, Nico y Cami". */
internal fun miosNombres(n: List<String>): String = when (n.size) {
    0 -> ""
    1 -> n[0]
    else -> n.dropLast(1).joinToString(", ") + " y " + n.last()
}

/** Primer nombre. */
internal fun miosPrimer(nombre: String) = nombre.trim().substringBefore(' ')
