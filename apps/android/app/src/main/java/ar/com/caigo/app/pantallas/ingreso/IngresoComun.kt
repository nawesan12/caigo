package ar.com.caigo.app.pantallas.ingreso

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Contenedor
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.dibujarGrano
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.estilo
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical

/** Rutas del alta (las de Navegacion.kt). */
internal const val ingresoRutaApp = "principal?tab=Ahora&modo="

/**
 * El paso del alta que le falta a la persona: datos → teléfono → selfie → intenciones → la app.
 * Lee GET /v1/yo y GET /v1/verificacion y deja el perfil en [Contenedor.yo].
 */
suspend fun ingresoRutaDeAlta(c: Contenedor): String {
    val yo = c.api.yo()
    c.yo.value = yo
    val v = c.api.verificacion()
    return when {
        yo.nombre.isBlank() || yo.edad == null -> "datos"
        v.telefono.isEmpty() -> "telefono"
        !v.selfie -> "selfie?volver=false"
        yo.intenciones.isEmpty() -> "intenciones?editar=false"
        // Sin perfiles vacíos: bio, fotos e intereses mínimos (personas.Faltantes en la API).
        yo.faltan.isNotEmpty() -> "completar"
        else -> ingresoRutaApp
    }
}

/** El error para el toast: el detalle del problema o el primer campo con problemas. */
internal fun ingresoMensaje(e: Throwable): String {
    val primero = (e as? ErrorApi)?.problema?.issues?.firstOrNull()
    return if (primero != null && primero.message.isNotEmpty()) primero.message else mensajeDe(e)
}

/** `.environment(\.colorScheme, .light)` de los pasos del alta: la paleta clara, aunque la app esté en oscuro. */
@Composable
internal fun ingresoClaro(contenido: @Composable () -> Unit) =
    CompositionLocalProvider(ar.com.caigo.app.tema.LocalPaleta provides ar.com.caigo.app.tema.PaletaClara, contenido)

/** Título y bajada de los pasos del registro: `700 34px/1.05`, -.8px; `15px/1.5` gris. */
@Composable
internal fun ingresoTitulo(titulo: String, bajada: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T(titulo, 34f, 700, C.carbon, alto = 1.05f, espaciado = px(-.8f, 34f))
        T(bajada, 15f, 400, C.texto2, alto = 1.5f)
    }
}

/** Qué autocompleta el sistema en un [ingresoCampo] (el `textContentType` de iOS). */
internal enum class IngresoContenidoCampo { Ninguno, Nombre, Telefono }

/** Botón volver / cerrar del prototipo: blanco a lavanda con base `#DCD5EE`. */
internal val ingresoGelVolver = Gel(
    vertical(C.blanco, Color(0xFFF3F0FA)),
    listOf(interna(2f, 0f, C.blanco), externa(3f, 0f, C.borde3)),
)

/** CTA lima grande del prototipo (62 px, radio 22, base de 5 px). */
@Composable
internal fun ingresoCtaGrande(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, cargando: Boolean = false, icono: String? = null) =
    ar.com.caigo.app.piezas.Cta(
        texto, onClick, modifier.fillMaxWidth(), gel = ar.com.caigo.app.tema.Gels.lima, alto = 62.dp, radio = 22.dp,
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
    transformacion: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    contenido: IngresoContenidoCampo = IngresoContenidoCampo.Ninguno,
) {
    var foco by remember { mutableStateOf(false) }
    val tipo = when (contenido) {
        IngresoContenidoCampo.Ninguno -> null
        IngresoContenidoCampo.Nombre -> ContentType.PersonFirstName
        IngresoContenidoCampo.Telefono -> ContentType.PhoneNumber
    }
    BasicTextField(
        valor, alCambiar,
        modifier.height(60.dp).onFocusChanged { foco = it.isFocused }
            .then(if (tipo != null) Modifier.semantics { contentType = tipo } else Modifier),
        singleLine = true,
        visualTransformation = transformacion,
        textStyle = estilo(tam, peso, C.carbon, espaciado = px(espaciado, tam)),
        cursorBrush = SolidColor(C.violeta),
        keyboardOptions = KeyboardOptions(keyboardType = teclado, autoCorrectEnabled = teclado == KeyboardType.Text),
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
        if (onVolver != null) ar.com.caigo.app.piezas.BotonIcono(icono, onVolver, gel = ingresoGelVolver, trazo = 2.4f, iconoTam = if (icono == I.cruz) 20.dp else 22.dp)
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
            val w by animateDpAsState(if (i == activo) 26.dp else 8.dp, tween(200, easing = EaseOut), label = "punto")
            val color by animateColorAsState(if (i == activo) on else off, tween(200, easing = EaseOut), label = "color")
            Box(Modifier.width(w).height(8.dp).background(color, RoundedCornerShape(4.dp)))
        }
    }
}

/** tk-pop (`.pop()` de iOS): escala .4 → 1.08 → 1 con rotación −20° → 6° → 0° (confirmaciones). */
@Composable
internal fun Modifier.ingresoPop(clave: Any? = Unit, ms: Int = 450): Modifier {
    val a = remember(clave) { Animatable(0f) }
    // iOS: .easeOut(0.27) hasta 1.08/6° y .easeInOut(0.18) hasta 1/0°.
    LaunchedEffect(clave) {
        a.animateTo(1f, keyframes {
            durationMillis = ms
            0f at 0 using EaseOut
            .6f at (ms * .6f).toInt() using androidx.compose.animation.core.EaseInOut
        })
    }
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
    LaunchedEffect(clave) { a.animateTo(1f, tween(ms, retraso, EaseOut)) }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 20.dp.toPx() }
}

/** Chip chico lima con check (los requisitos de la selfie) o apagado si no se cumple. */
@Composable
internal fun ingresoChipRequisito(texto: String, ok: Boolean, modifier: Modifier = Modifier) {
    // .animation(.easeOut(duration: 0.2), value: ok): el lima aparece encima del lavanda.
    val alfa by animateFloatAsState(if (ok) 1f else 0f, tween(200, easing = EaseOut), label = "ok")
    val color by animateColorAsState(if (ok) C.carbon else C.texto2, tween(200, easing = EaseOut), label = "color")
    Box(modifier.gel(12.dp, Gels.chipClaro)) {
        if (alfa > 0f) Box(Modifier.matchParentSize().graphicsLayer { alpha = alfa }.gel(12.dp, Gels.limaChico))
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (ok) Icono(I.check, 14.dp, C.carbon, 3f)
            T(texto, 13f, 700, color, alto = 1.25f, unRenglon = true)
        }
    }
}

/** Obturador del DNI: círculo blanco de 82 con anillo carbón de 6 y lima de 9. */
@Composable
internal fun ingresoObturador(onClick: () -> Unit, habilitado: Boolean = true, tam: Dp = 82.dp) {
    Box(
        Modifier.size(tam + 18.dp).toque(habilitado, onClick = onClick)
            .semantics { contentDescription = "Sacar foto"; role = Role.Button },
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

/** "Saltar": píldora translúcida sobre la foto. */
@Composable
internal fun ingresoSaltar(alfa: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.tocable(onClick).background(rgba(255, 255, 255, alfa), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { T("Saltar", 14f, 700, C.blanco) }
}

/** Fondo del splash (00): grafito con un radial más claro en el centro, y grano. */
internal fun Modifier.ingresoFondoSplash(): Modifier = this
    .background(Color(0xFF17151F))
    .background(radial(.5f, .42f, .7f, .45f, 0f to Color(0xFF3A3450), .5f to Color(0xFF262233), 1f to Color(0xFF17151F)))
    .ingresoGrano()

/** El grano del handoff encima de un fondo. */
internal fun Modifier.ingresoGrano(): Modifier = this.drawBehind {
    dibujarGrano(Path().apply { addRect(Rect(Offset.Zero, size)) })
}

/** Mancha de color difuminada (las del splash): radial que se apaga hacia el borde. */
@Composable
internal fun ingresoMancha(tam: androidx.compose.ui.unit.Dp, claro: Color, medio: Color, oscuro: Color, alfa: Float, modifier: Modifier) {
    Box(
        modifier.size(tam).clip(androidx.compose.foundation.shape.CircleShape).background(
            radial(.5f, .5f, 0f, 0f,
                0f to claro.copy(alpha = alfa), .35f to medio.copy(alpha = alfa), .55f to oscuro.copy(alpha = alfa * .7f), .72f to oscuro.copy(alpha = 0f),
                circulo = true),
        ),
    )
}

/** Fondo violeta de 01c (y de Ingresar): radial lavanda arriba a la derecha + grano. */
internal fun Modifier.ingresoFondoVioleta(): Modifier = this
    .background(Color(0xFF5A3DD8))
    .background(radial(.85f, 0f, 1.2f, .7f, 0f to Color(0xFFA38FFF), .45f to C.violeta, 1f to Color(0xFF5A3DD8)))
    .ingresoGrano()

/** `Sistema.vibrar()` de iOS (UINotificationFeedbackGenerator .success). PARA EL NÚCLEO: `Sistema.vibrar` en piezas/. */
internal fun ingresoVibrar(v: android.view.View) {
    v.performHapticFeedback(
        if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.VIRTUAL_KEY,
    )
}
