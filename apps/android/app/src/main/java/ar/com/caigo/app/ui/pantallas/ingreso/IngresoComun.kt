package ar.com.caigo.app.ui.pantallas.ingreso

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.Contenedor
import ar.com.caigo.app.datos.ErrorApi
import ar.com.caigo.app.datos.mensajeDe
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.estilo
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical

/** Rutas del alta (las de Navegacion.kt). */
internal const val ingresoRutaApp = "principal?tab=Ahora&modo="

/**
 * El paso del alta que le falta a la persona: datos → teléfono → selfie → intenciones → la app.
 * Lee GET /v1/yo y GET /v1/verificacion y deja el perfil en [Contenedor.yo].
 */
suspend fun rutaDeAlta(c: Contenedor): String {
    val yo = c.api.yo()
    c.yo.value = yo
    val v = c.api.verificacion()
    return when {
        yo.nombre.isBlank() || yo.edad == null -> "datos"
        v.telefono.isBlank() -> "telefono"
        !v.selfie -> "selfie?volver=false"
        yo.intenciones.isEmpty() -> "intenciones?editar=false"
        else -> ingresoRutaApp
    }
}

/** El error para el toast: el detalle del problema o el primer campo con problemas. */
internal fun ingresoMensaje(e: Throwable): String =
    if (e is ErrorApi && e.problema.issues.isNotEmpty()) e.problema.issues.first().message else mensajeDe(e)

/** Botón volver / cerrar del prototipo: blanco a lavanda con base `#DCD5EE`. */
internal val ingresoGelVolver = Gel(
    vertical(C.blanco, Color(0xFFF3F0FA)),
    listOf(interna(2f, 0f, C.blanco), externa(3f, 0f, C.borde3)),
)

/** CTA lima grande del prototipo (62 px, radio 22, base de 5 px). */
@Composable
internal fun ingresoCtaGrande(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, cargando: Boolean = false, icono: String? = null) =
    ar.com.caigo.app.ui.piezas.Cta(
        texto, onClick, modifier.fillMaxWidth(), gel = ar.com.caigo.app.ui.tema.Gels.lima, alto = 62.dp, radio = 22.dp,
        tam = 18f, color = C.carbon, habilitado = habilitado, cargando = cargando, icono = icono, iconoTam = 18.dp, iconoTrazo = 4f,
    )

/** Campo de 60 px del registro (01d): blanco, radio 18, borde lavanda o violeta con foco. */
@Composable
internal fun ingresoCampo(
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    pista: String = "",
    teclado: KeyboardType = KeyboardType.Text,
    tam: Float = 18f,
    peso: Int = 600,
    espaciado: Float = .5f,
) {
    var foco by remember { mutableStateOf(false) }
    BasicTextField(
        valor, alCambiar,
        modifier.height(60.dp).onFocusChanged { foco = it.isFocused },
        singleLine = true,
        textStyle = estilo(tam, peso, C.carbon, espaciado = px(espaciado, tam)),
        cursorBrush = SolidColor(C.violeta),
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        decorationBox = { campo ->
            Box(
                Modifier.fillMaxWidth().height(60.dp)
                    .background(C.blanco, RoundedCornerShape(18.dp))
                    .border(if (foco) 2.dp else 1.5.dp, if (foco) C.violeta else Color(0xFFE6E1F2), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (valor.isEmpty() && pista.isNotEmpty()) T(pista, tam, 500, C.apagado, unRenglon = true)
                campo()
            }
        },
    )
}

/** Barra de arriba de los pasos: volver (44×44) y "Paso N de 2" o el candado "Encriptado". */
@Composable
internal fun ingresoBarraArriba(icono: String, onVolver: (() -> Unit)?, derecha: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (onVolver != null) ar.com.caigo.app.ui.piezas.BotonIcono(icono, onVolver, gel = ingresoGelVolver, trazo = 2.4f, iconoTam = if (icono == I.cruz) 20.dp else 22.dp)
        else Box(Modifier.size(44.dp))
        derecha()
    }
}

@Composable
internal fun ingresoEncriptado() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icono(I.candado, 14.dp, Color.Transparent, 0f, relleno = C.texto2)
        T("Encriptado", 13f, 700, C.texto2)
    }
}

/** Puntitos del onboarding: el activo mide 26 px. */
@Composable
internal fun ingresoPuntos(activo: Int, total: Int, on: Color, off: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val w by animateDpAsState(if (i == activo) 26.dp else 8.dp, tween(200), label = "punto")
            Box(Modifier.width(w).height(8.dp).background(if (i == activo) on else off, RoundedCornerShape(4.dp)))
        }
    }
}

/** tk-pop: escala .4 → 1.08 → 1 con rotación −20° → 6° → 0° (confirmaciones). */
@Composable
internal fun Modifier.ingresoPop(clave: Any? = Unit, ms: Int = 450): Modifier {
    val a = remember(clave) { Animatable(0f) }
    LaunchedEffect(clave) { a.animateTo(1f, keyframes { durationMillis = ms; .7f at (ms * .6f).toInt() using FastOutSlowInEasing }) }
    return graphicsLayer {
        val t = a.value
        val (s, r) = when {
            t < .6f -> (.4f + (1.08f - .4f) * (t / .6f)) to (-20f + 26f * (t / .6f))
            else -> (1.08f - .08f * ((t - .6f) / .4f)) to (6f - 6f * ((t - .6f) / .4f))
        }
        scaleX = s; scaleY = s; rotationZ = r
    }
}

/** tk-up: entra subiendo 20 px y apareciendo. */
@Composable
internal fun Modifier.ingresoSube(clave: Any? = Unit, ms: Int = 250, retraso: Int = 0): Modifier {
    val a = remember(clave) { Animatable(0f) }
    LaunchedEffect(clave) { a.animateTo(1f, tween(ms, retraso)) }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 20.dp.toPx() }
}

/** Chip chico lima con check (los requisitos de la selfie) o apagado si no se cumple. */
@Composable
internal fun ingresoChipRequisito(texto: String, ok: Boolean, modifier: Modifier = Modifier) {
    val gel = if (ok) Gel(
        vertical(0f to C.lima0, .32f to C.lima1, .62f to C.lima, 1f to C.lima3),
        listOf(interna(3f, 2f, rgba(255, 255, 255, .85f)), interna(-5f, 0f, rgba(96, 140, 10, .32f)), externa(4f, 0f, C.limaBase), externa(16f, 26f, rgba(120, 170, 30, .7f), spread = -10f)),
    ) else ar.com.caigo.app.ui.tema.Gels.chipClaro
    Row(
        modifier.gel(12.dp, gel).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (ok) Icono(I.check, 14.dp, C.carbon, 3f)
        T(texto, 13f, 700, if (ok) C.carbon else C.texto2, alto = 1.25f)
    }
}

/** Obturador del DNI: círculo blanco de 82 con anillo carbón de 6 y lima de 9. */
@Composable
internal fun ingresoObturador(onClick: () -> Unit, habilitado: Boolean = true, tam: Dp = 82.dp) {
    Box(
        Modifier.size(tam + 18.dp).toque(habilitado, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(tam + 18.dp).gel(
                100.dp, vertical(C.lima, C.lima),
                listOf(externa(14f, 24f, rgba(0, 0, 0, .4f), spread = -8f)),
            ),
        )
        Box(Modifier.size(tam + 12.dp).background(C.carbon, androidx.compose.foundation.shape.CircleShape))
        Box(Modifier.size(tam).background(C.blanco, androidx.compose.foundation.shape.CircleShape))
    }
}
