package ar.com.caigo.app.tema

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Los colores del handoff (README · Design Tokens), con los mismos nombres que usa el diseño. */
object C {
    // Marca
    val coral = Color(0xFFFF7868)
    val violeta = Color(0xFF7456F1)
    val lima = Color(0xFFC8F36B)
    val blancoCalido = Color(0xFFFAF9F6)
    val grafito = Color(0xFF201D2B)
    val carbon = Color(0xFF24212D)
    val lavanda = Color(0xFFF0ECFF)
    val blanco = Color(0xFFFFFFFF)
    val negro = Color(0xFF000000)

    // Texto
    val texto2 = Color(0xFF5C586B)
    val texto3 = Color(0xFF6B6780)
    val inactivo = Color(0xFF8A8699)
    val apagado = Color(0xFFA8A3BD)
    val apagado2 = Color(0xFFB9B4CC)

    // Lavandas de borde y base
    val borde1 = Color(0xFFE6E0F3)
    val borde2 = Color(0xFFE8E3F4)
    val borde3 = Color(0xFFDCD5EE)
    val borde4 = Color(0xFFE2DCF0)
    val borde5 = Color(0xFFC9BEF5)
    val borde6 = Color(0xFFCFC8E6)
    val pista = Color(0xFFE4DEFA)

    // Violetas
    val violeta1 = Color(0xFFB3A3FF)
    val violeta2 = Color(0xFF8A70FF)
    val violeta4 = Color(0xFF5F41E0)
    val violetaTexto = Color(0xFF5B43C9)
    val violetaBase = Color(0xFF4A2FC2)
    val tinte1 = Color(0xFFEDE8FF)
    val tinte2 = Color(0xFFF8F6FF)
    val tinte3 = Color(0xFFECE5FF)
    val tinte4 = Color(0xFFE3DAFF)
    val tinte5 = Color(0xFFF6F3FF)

    // Limas
    val lima0 = Color(0xFFF0FFC2)
    val lima1 = Color(0xFFD6FA80)
    val lima3 = Color(0xFFABDB4B)
    val limaBase = Color(0xFF94C43A)
    val limaTexto = Color(0xFF3F6A00)
    val cupoLleno1 = Color(0xFFE2FF9C)
    val cupoLleno2 = Color(0xFFB2E04F)

    // Corales
    val coral0 = Color(0xFFFFB2A6)
    val coral2 = Color(0xFFF05E4C)
    val coralBase = Color(0xFFD04A3A)

    // Oscuros
    val oscuro1 = Color(0xFF3D3756)
    val oscuro2 = Color(0xFF3A3452)
    val oscuro3 = Color(0xFF312C42)
    val oscuro4 = Color(0xFF28243A)
    val oscuro5 = Color(0xFF2B2738)
    val oscuro6 = Color(0xFF15131C)
    val oscuro7 = Color(0xFF0E0D14)
    val oscuro8 = Color(0xFF0B0A10)

    val lienzo = Color(0xFFE9E6DF)
}

/** Lo que cambia entre el modo claro y el oscuro (14a–14b). El resto de los colores no cambia. */
@Immutable
data class Paleta(
    val oscuro: Boolean,
    val fondo: Color,
    /** El "glow" lavanda de arriba a la derecha (radial-gradient sobre el fondo). */
    val fondoBrillo: Color,
    val texto: Color,
    val texto2: Color,
    val texto3: Color,
    val tarjeta: Brush,
    val tarjetaPlano: Color,
    val bordeAvatar: Color,
    val chipApagado: Brush,
    val segPista: Color,
    val segOn: Brush,
    val segOnTexto: Color,
    val segOffTexto: Color,
    val acento: Color,
    val tabFondo: Brush,
    val tabActivo: Color,
    val tabInactivo: Color,
    val tabPill: Brush,
    val superficie: Color,
    val separador: Color,
)

val PaletaClara = Paleta(
    oscuro = false,
    fondo = C.blancoCalido,
    fondoBrillo = C.tinte3,
    texto = C.carbon,
    texto2 = C.texto2,
    texto3 = C.texto3,
    tarjeta = Brush.verticalGradient(listOf(C.blanco, C.blanco)),
    tarjetaPlano = C.blanco,
    bordeAvatar = C.blanco,
    chipApagado = Brush.verticalGradient(listOf(C.tinte2, C.tinte1)),
    segPista = C.lavanda,
    segOn = Brush.verticalGradient(listOf(C.blanco, C.blanco)),
    segOnTexto = C.carbon,
    segOffTexto = C.texto2,
    acento = C.violeta,
    tabFondo = Brush.verticalGradient(listOf(Color(0xF7FFFFFF), Color(0xF0F4F0FF))),
    tabActivo = C.violeta,
    tabInactivo = C.inactivo,
    tabPill = Brush.verticalGradient(listOf(C.tinte5, Color(0xFFE6DEFF))),
    superficie = C.lavanda,
    separador = Color(0xFFF0ECF8),
)

val PaletaOscura = Paleta(
    oscuro = true,
    fondo = C.grafito,
    fondoBrillo = Color(0xFF352B6E),
    texto = C.blancoCalido,
    texto2 = C.apagado,
    texto3 = C.apagado2,
    tarjeta = Brush.verticalGradient(listOf(C.oscuro3, C.oscuro4)),
    tarjetaPlano = C.oscuro3,
    bordeAvatar = Color(0xFF2E2A3F),
    chipApagado = Brush.verticalGradient(listOf(Color(0xFF36314A), C.oscuro5)),
    segPista = Color(0xFF2E2A3D),
    segOn = Brush.verticalGradient(listOf(Color(0xFF56506C), Color(0xFF454058))),
    segOnTexto = C.blancoCalido,
    segOffTexto = C.apagado,
    acento = C.lima,
    tabFondo = Brush.verticalGradient(listOf(Color(0xF53A3450), Color(0xF5262334))),
    tabActivo = C.lima,
    tabInactivo = Color(0xFF9A95B0),
    tabPill = Brush.verticalGradient(listOf(Color(0xFF4A4362), Color(0xFF332E45))),
    superficie = Color(0xFF2E2A3D),
    separador = Color(0x14FFFFFF),
)

val LocalPaleta = staticCompositionLocalOf { PaletaClara }
