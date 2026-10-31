package ar.com.caigo.app.ui.tema

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ar.com.caigo.app.R

/** Geist, la tipografía final del handoff (v5), embebida en la app. */
val Geist = FontFamily(
    Font(R.font.geist_400, FontWeight.Normal),
    Font(R.font.geist_500, FontWeight.Medium),
    Font(R.font.geist_600, FontWeight.SemiBold),
    Font(R.font.geist_700, FontWeight.Bold),
    Font(R.font.geist_800, FontWeight.ExtraBold),
)

/**
 * Corte de renglones "balanceado": reparte las palabras parejo entre los renglones, así nunca queda
 * una palabra sola en el último (regla de Nahuel: sin renglones viudos).
 */
val SinViudas = LineBreak(
    strategy = LineBreak.Strategy.Balanced,
    strictness = LineBreak.Strictness.Strict,
    wordBreak = LineBreak.WordBreak.Phrase,
)

fun peso(w: Int): FontWeight = when {
    w >= 800 -> FontWeight.ExtraBold
    w >= 700 -> FontWeight.Bold
    w >= 600 -> FontWeight.SemiBold
    w >= 500 -> FontWeight.Medium
    else -> FontWeight.Normal
}

/**
 * Estilo como en el CSS del handoff: `font: <peso> <tamaño>px/<alto>`, letter-spacing en px o em.
 * El alto de renglón va como múltiplo (1, 1.05, 1.15…); null usa ~1.35 como el navegador.
 */
fun estilo(
    tam: Float,
    peso: Int = 400,
    color: Color = Color.Unspecified,
    alto: Float? = null,
    espaciado: TextUnit = TextUnit.Unspecified,
    alinear: TextAlign = TextAlign.Unspecified,
): TextStyle = TextStyle(
    fontFamily = Geist,
    fontWeight = peso(peso),
    fontSize = tam.sp,
    color = color,
    lineHeight = (tam * (alto ?: 1.35f)).sp,
    letterSpacing = espaciado,
    textAlign = alinear,
    lineBreak = SinViudas,
    hyphens = Hyphens.None,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** letter-spacing en px del CSS → em, para que escale con el tamaño. */
fun px(espaciado: Float, tam: Float): TextUnit = (espaciado / tam).em

/** Texto con el estilo del handoff. Es el único texto que usan las pantallas. */
@Composable
fun T(
    texto: String,
    tam: Float,
    peso: Int = 400,
    color: Color = LocalPaleta.current.texto,
    modifier: Modifier = Modifier,
    alto: Float? = null,
    espaciado: TextUnit = TextUnit.Unspecified,
    alinear: TextAlign = TextAlign.Unspecified,
    maxRenglones: Int = Int.MAX_VALUE,
    unRenglon: Boolean = false,
) {
    BasicText(
        text = texto,
        modifier = modifier,
        style = estilo(tam, peso, color, alto, espaciado, alinear),
        maxLines = if (unRenglon) 1 else maxRenglones,
        softWrap = !unRenglon,
        overflow = if (unRenglon || maxRenglones != Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip,
    )
}

/** Títulos de pantalla: `700 30px/1`, -0.045em (o el 800 40px de Ahora). */
val EspaciadoTitulo = (-0.045f).em
