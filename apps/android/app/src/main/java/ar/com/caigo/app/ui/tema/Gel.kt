package ar.com.caigo.app.ui.tema

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.R

/**
 * Una sombra de CSS (`box-shadow`): desplazamiento, desenfoque, extensión, color y si es interna.
 * Los valores van en px del handoff (= dp).
 */
data class Sombra(
    val y: Float,
    val blur: Float = 0f,
    val spread: Float = 0f,
    val color: Color,
    val inset: Boolean = false,
    val x: Float = 0f,
)

fun interna(y: Float, blur: Float, color: Color, spread: Float = 0f, x: Float = 0f) =
    Sombra(y, blur, spread, color, inset = true, x = x)

fun externa(y: Float, blur: Float, color: Color, spread: Float = 0f, x: Float = 0f) =
    Sombra(y, blur, spread, color, inset = false, x = x)

/** rgba(r,g,b,a) del CSS. */
fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r, g, b, (a * 255).toInt().coerceIn(0, 255))

/** Fondo + sombras de un elemento "gel / 3D soft". */
data class Gel(val fondo: Brush?, val sombras: List<Sombra>, val grano: Boolean = false)

/** La textura de ruido del handoff (`assets/grain.png`), en mosaico de 160×160 px. */
object Grano {
    @Volatile
    var bitmap: android.graphics.Bitmap? = null

    fun cargar(ctx: Context) {
        if (bitmap == null) bitmap = BitmapFactory.decodeResource(ctx.resources, R.drawable.grain)
    }
}

/** `linear-gradient(180deg, …)` con paradas. */
fun vertical(vararg paradas: Pair<Float, Color>): Brush = Brush.verticalGradient(colorStops = paradas)
fun vertical(a: Color, b: Color): Brush = Brush.verticalGradient(listOf(a, b))

/**
 * `radial-gradient(<rx>% <ry>% at <cx>% <cy>%, …)`: elíptico, con radios y centro relativos a la caja.
 * Si [circulo] es true, es `circle at …` con el radio hasta la esquina más lejana (como el CSS).
 */
fun radial(
    cx: Float, cy: Float, rx: Float, ry: Float,
    vararg paradas: Pair<Float, Color>,
    circulo: Boolean = false,
): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val c = Offset(size.width * cx, size.height * cy)
        val (radioX, radioY) = if (circulo) {
            val dx = maxOf(c.x, size.width - c.x)
            val dy = maxOf(c.y, size.height - c.y)
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            r to r
        } else {
            size.width * rx to size.height * ry
        }
        val g = RadialGradient(
            c.x, c.y, maxOf(radioX, 0.01f),
            paradas.map { it.second.toArgb() }.toIntArray(),
            paradas.map { it.first }.toFloatArray(),
            android.graphics.Shader.TileMode.CLAMP,
        )
        if (!circulo && radioX > 0f) {
            g.setLocalMatrix(Matrix().apply { setScale(1f, radioY / radioX, c.x, c.y) })
        }
        return g
    }
}

private fun redondeado(r: Rect, radio: Float): RoundRect {
    val rr = radio.coerceIn(0f, minOf(r.width, r.height) / 2f)
    return RoundRect(r, CornerRadius(rr, rr))
}

private fun camino(rr: RoundRect) = Path().apply { addRoundRect(rr) }

private fun pintura(color: Color, blurPx: Float) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    // El blur de CSS es 2σ; BlurMaskFilter toma un radio ≈ σ / 0,57735.
    if (blurPx > 0.5f) maskFilter = BlurMaskFilter(blurPx * 0.866f, BlurMaskFilter.Blur.NORMAL)
}

fun DrawScope.dibujarGrano(forma: Path, alfa: Float = 1f) {
    val b = Grano.bitmap ?: return
    val p = android.graphics.Paint().apply {
        shader = BitmapShader(b, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(density * 160f / b.width, density * 160f / b.height) })
        }
        alpha = (alfa * 255).toInt()
    }
    drawIntoCanvas { it.nativeCanvas.drawPath(forma.asAndroidPath(), p) }
}

/**
 * Dibuja el elemento como en el CSS: sombras externas (por fuera de la forma), fondo, grano y sombras
 * internas (highlight arriba, sombra abajo). El contenido va encima. [radio] en dp; un radio enorme
 * hace un círculo.
 */
fun Modifier.gel(radio: Dp, gel: Gel): Modifier = gel(radio, gel.fondo, gel.sombras, gel.grano)

fun Modifier.gel(radio: Dp, fondo: Brush?, sombras: List<Sombra> = emptyList(), grano: Boolean = false): Modifier =
    this.drawWithCache {
        val r = radio.toPx()
        val caja = Rect(Offset.Zero, size)
        val forma = camino(redondeado(caja, r))
        val externas = sombras.filter { !it.inset }.asReversed()
        val internas = sombras.filter { it.inset }.asReversed()
        val pathsExt = externas.map { s ->
            val spread = s.spread.dp.toPx()
            val rect = caja.translate(s.x.dp.toPx(), s.y.dp.toPx()).inflate(spread)
            Triple(camino(redondeado(rect, r + spread)), pintura(s.color, s.blur.dp.toPx()), s)
        }
        val pathsInt = internas.map { s ->
            val spread = s.spread.dp.toPx()
            val blur = s.blur.dp.toPx()
            val hueco = caja.translate(s.x.dp.toPx(), s.y.dp.toPx()).deflate(spread)
            val grande = Path().apply { addRect(caja.inflate(blur * 2 + 60f)) }
            val p = Path().apply {
                op(grande, camino(redondeado(hueco, (r - spread).coerceAtLeast(0f))), PathOperation.Difference)
            }
            p to pintura(s.color, blur)
        }
        onDrawWithContent {
            if (pathsExt.isNotEmpty()) {
                clipPath(forma, ClipOp.Difference) {
                    drawIntoCanvas { c -> pathsExt.forEach { (p, paint, _) -> c.nativeCanvas.drawPath(p.asAndroidPath(), paint) } }
                }
            }
            if (fondo != null) drawPath(forma, fondo)
            if (grano) dibujarGrano(forma)
            if (pathsInt.isNotEmpty()) {
                clipPath(forma) {
                    drawIntoCanvas { c -> pathsInt.forEach { (p, paint) -> c.nativeCanvas.drawPath(p.asAndroidPath(), paint) } }
                }
            }
            drawContent()
        }
    }

/** Los fondos y sombras que se repiten en todo el handoff. */
object Gels {
    val blancoAlfa85 = rgba(255, 255, 255, .85f)

    /** CTA lima grande (5px de base). */
    val lima = Gel(
        vertical(0f to C.lima0, .32f to C.lima1, .62f to C.lima, 1f to C.lima3),
        listOf(
            interna(3f, 2f, rgba(255, 255, 255, .85f)),
            interna(-5f, 0f, rgba(96, 140, 10, .32f)),
            externa(5f, 0f, C.limaBase),
            externa(18f, 28f, rgba(120, 170, 30, .7f), spread = -10f),
        ),
    )

    /** Lima chico (botón "Sumarme" de la tarjeta, chips seleccionados del modo oscuro). */
    val limaChico = Gel(
        lima.fondo,
        listOf(
            interna(3f, 2f, rgba(255, 255, 255, .85f)),
            interna(-5f, 0f, rgba(96, 140, 10, .32f)),
            externa(4f, 0f, C.limaBase),
            externa(16f, 26f, rgba(120, 170, 30, .7f), spread = -10f),
        ),
    )

    val violeta = Gel(
        vertical(0f to C.violeta1, .30f to C.violeta2, .62f to C.violeta, 1f to C.violeta4),
        listOf(
            interna(3f, 2f, rgba(255, 255, 255, .45f)),
            interna(-5f, 0f, rgba(40, 20, 140, .35f)),
            externa(5f, 0f, C.violetaBase),
            externa(18f, 28f, rgba(116, 86, 241, .75f), spread = -10f),
        ),
    )

    val coralBadge = Gel(
        vertical(0f to C.coral0, .55f to C.coral, 1f to C.coral2),
        listOf(
            interna(2f, 1f, rgba(255, 255, 255, .6f)),
            interna(-3f, 0f, rgba(160, 40, 30, .25f)),
            externa(3f, 0f, C.coralBase),
        ),
    )

    /** Badge "Completo" (lima). */
    val limaBadge = Gel(
        vertical(0f to C.cupoLleno1, .5f to C.lima, 1f to C.cupoLleno2),
        listOf(
            interna(2f, 1f, rgba(255, 255, 255, .6f)),
            interna(-3f, 0f, rgba(96, 140, 10, .25f)),
            externa(3f, 0f, C.limaBase),
        ),
    )

    /** Chip seleccionado (SEL). */
    val sel = Gel(
        violeta.fondo,
        listOf(
            interna(2f, 1f, rgba(255, 255, 255, .45f)),
            interna(-3f, 0f, rgba(40, 20, 140, .35f)),
            externa(3f, 0f, C.violetaBase),
        ),
    )

    /** Chip apagado (OFF). */
    val off = Gel(
        vertical(C.tinte2, C.tinte1),
        listOf(interna(1.5f, 0f, C.blanco), externa(2f, 0f, C.borde3)),
    )

    /** Chip oscuro seleccionado (filtro de categoría). */
    val oscuroSel = Gel(
        radial(0f, 0f, 1.2f, .9f, 0f to C.oscuro1, .62f to C.carbon),
        listOf(interna(1f, 0f, rgba(255, 255, 255, .08f)), externa(12f, 26f, rgba(36, 33, 45, .55f), spread = -10f)),
        grano = true,
    )

    /** Chip oscuro del prototipo (DARKSEL). */
    val darkSel = Gel(
        vertical(C.oscuro1, C.carbon),
        listOf(interna(1f, 0f, rgba(255, 255, 255, .15f)), externa(3f, 0f, C.oscuro7)),
    )

    /** Chip de categoría apagado del mockup (sin base, con sombra interna lavanda). */
    val chipClaro = Gel(
        vertical(C.tinte2, C.tinte1),
        listOf(interna(1.5f, 0f, C.blanco), interna(-2f, 0f, rgba(116, 86, 241, .10f))),
    )
    val chipOscuro = Gel(
        vertical(Color(0xFF36314A), C.oscuro5),
        listOf(interna(1f, 0f, rgba(255, 255, 255, .08f))),
    )

    /** Botón icono claro (44×44). */
    val icono = chipClaro

    val segOn = Gel(
        vertical(C.blanco, C.blanco),
        listOf(interna(1f, 0f, C.blanco), externa(3f, 8f, rgba(80, 60, 160, .3f), spread = -2f)),
    )
    val segOnOscuro = Gel(
        vertical(Color(0xFF56506C), Color(0xFF454058)),
        listOf(interna(1f, 0f, rgba(255, 255, 255, .15f))),
    )

    /** Tarjeta de plan. */
    val tarjeta = Gel(
        vertical(C.blanco, C.blanco),
        listOf(interna(1f, 0f, C.blanco), externa(2f, 0f, C.borde2), externa(16f, 30f, rgba(80, 60, 160, .22f), spread = -10f)),
    )
    val tarjetaOscura = Gel(
        vertical(C.oscuro3, C.oscuro4),
        listOf(interna(1f, 0f, rgba(255, 255, 255, .08f)), externa(18f, 30f, rgba(0, 0, 0, .6f), spread = -14f)),
    )

    /** Tarjeta neutra del prototipo / panel. */
    val neutra = Gel(vertical(C.blanco, C.blanco), listOf(interna(1f, 0f, C.blanco), externa(3f, 0f, C.borde1)))

    /** Opción seleccionada (radio-card). */
    val opcionSel = Gel(vertical(C.lavanda, C.lavanda), listOf(interna(0f, 0f, C.violeta, spread = -2f), externa(3f, 0f, C.borde5)))

    /** Tarjeta oscura de contador ("Faltan 2 / Somos 8 de 10"). */
    val contador = Gel(
        radial(0f, 0f, 1.2f, 1.2f, 0f to C.oscuro2, .7f to C.carbon),
        listOf(interna(1f, 0f, rgba(255, 255, 255, .1f)), externa(18f, 30f, rgba(36, 33, 45, .45f), spread = -12f)),
        grano = true,
    )

    /** Tab bar flotante. */
    val tabBar = Gel(
        vertical(Color(0xF7FFFFFF), Color(0xF0F4F0FF)),
        listOf(
            interna(1.5f, 0f, C.blanco),
            interna(-2f, 0f, rgba(116, 86, 241, .08f)),
            externa(22f, 40f, rgba(60, 40, 140, .45f), spread = -14f),
            externa(0f, 0f, rgba(116, 86, 241, .07f), spread = 1f),
        ),
    )
    val tabBarOscura = Gel(
        vertical(rgba(58, 52, 80, .96f), rgba(38, 35, 52, .96f)),
        listOf(interna(1f, 0f, rgba(255, 255, 255, .12f)), externa(18f, 36f, rgba(0, 0, 0, .7f), spread = -10f)),
    )
    val tabPill = Gel(
        vertical(C.tinte5, Color(0xFFE6DEFF)),
        listOf(interna(1.5f, 0f, C.blanco), interna(-2f, 0f, rgba(116, 86, 241, .14f)), externa(4f, 10f, rgba(116, 86, 241, .35f), spread = -4f)),
    )
    val tabPillOscura = Gel(
        vertical(Color(0xFF4A4362), Color(0xFF332E45)),
        listOf(interna(1f, 0f, rgba(255, 255, 255, .14f)), externa(4f, 10f, rgba(0, 0, 0, .6f), spread = -4f)),
    )

    /** Botón blanco con borde ("Ahora no", "Mostrar QR de entrada"). */
    val blanco = Gel(
        vertical(C.blanco, C.blanco),
        listOf(interna(0f, 0f, C.borde4, spread = -1.5f), externa(4f, 0f, C.borde4)),
    )
}

/** Fondo de pantalla clara: grano sobre el brillo lavanda arriba a la derecha. */
fun fondoPantalla(p: Paleta): Brush =
    if (p.oscuro) radial(1f, 0f, 1f, .4f, 0f to Color(0xFF352B6E), .65f to C.grafito)
    else radial(1f, 0f, .9f, .38f, 0f to C.tinte3, .7f to C.tinte3.copy(alpha = 0f))
