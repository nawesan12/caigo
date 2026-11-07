package ar.com.caigo.app.pantallas.ingreso

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import ar.com.caigo.app.piezas.tienePermiso
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.gel
import kotlin.math.abs
import kotlin.math.min

// La cámara de los pasos de verificación. La vista previa y las fotos son las de piezas/Camara.kt
// (CameraX: `VistaCamara`, `EscanerCodigos`, `Disparador`); acá va la lógica de la prueba de vida,
// el permiso, el anillo y la imagen en memoria, como en IngresoCamara.swift. Las cámaras ya armadas
// (`ingresoCamaraVida`, `ingresoCamaraDni`) están en Selfie.kt, como en iOS.

/**
 * Prueba de vida: mirar a la cámara, girar la cabeza a un lado y después al otro. Además lleva los
 * requisitos de la foto (buena luz, cara centrada, ojos a la vista). Los cuadros los analiza ML Kit.
 */
@Stable
internal class IngresoVida {
    /** 0 mirar a la cámara · 1 girar a un lado · 2 girar al otro lado · 3 listo. */
    var fase by mutableIntStateOf(0)
    var progreso by mutableFloatStateOf(0f)
    var luz by mutableStateOf(false)
    var centrada by mutableStateOf(false)
    var ojos by mutableStateOf(false)
    var hayCara by mutableStateOf(false)
    private var quieto = 0
    private var lado = 0f

    fun reiniciar() {
        fase = 0; progreso = 0f; quieto = 0; lado = 0f
    }

    /** Un cuadro analizado: [yaw] es el giro de la cabeza en grados (headEulerAngleY). */
    fun cuadro(cara: Boolean, yaw: Float, centro: Boolean, ojosAbiertos: Boolean, luminancia: Float) {
        luz = luminancia > 70f
        hayCara = cara
        centrada = cara && centro
        ojos = cara && ojosAbiertos
        if (!cara || fase == 3) return
        when (fase) {
            0 -> if (centrada && abs(yaw) < 12f) {
                quieto++
                progreso = min(.25f, quieto * .03f)
                if (quieto >= 8) fase = 1
            }
            1 -> {
                progreso = .25f + .4f * min(abs(yaw) / 22f, 1f)
                if (abs(yaw) >= 22f) { lado = if (yaw > 0) 1f else -1f; fase = 2 }
            }
            2 -> {
                val otro = -yaw * lado
                progreso = .65f + .3f * min(otro.coerceAtLeast(0f) / 22f, 1f)
                if (otro >= 22f) { progreso = 1f; fase = 3 }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
internal fun ingresoImagenDe(img: ImageProxy): android.media.Image? = img.image

/** Luminancia media del plano Y, muestreada cada 24 píxeles en las dos direcciones (como iOS). */
internal fun ingresoLuminancia(img: ImageProxy): Float {
    val plano = img.planes.firstOrNull() ?: return 128f
    val b = plano.buffer
    val fila = plano.rowStride
    val px = plano.pixelStride.coerceAtLeast(1)
    var suma = 0L
    var n = 0
    var y = 0
    while (y < img.height) {
        var x = 0
        while (x < img.width) {
            val i = y * fila + x * px
            if (i < b.limit()) { suma += (b.get(i).toInt() and 0xFF); n++ }
            x += 24
        }
        y += 24
    }
    return if (n == 0) 128f else suma.toFloat() / n
}

/**
 * Permiso de cámara (`IngresoPermisoCamara` de iOS). Devuelve (concedido, pedir()). Si ya lo negaron
 * para siempre, pedir() abre los ajustes de la app (iOS abre `app-settings:` si está denegado).
 */
@Composable
internal fun rememberIngresoPermisoCamara(): Pair<Boolean, () -> Unit> {
    val ctx = LocalContext.current
    var concedido by remember { mutableStateOf(tienePermiso(ctx, Manifest.permission.CAMERA)) }
    var negado by remember { mutableStateOf(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        concedido = ok
        val act = ctx.actividad()
        negado = !ok && act != null && !ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.CAMERA)
    }
    val pedir: () -> Unit = {
        when {
            tienePermiso(ctx, Manifest.permission.CAMERA) -> concedido = true
            negado -> runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            else -> lanzador.launch(Manifest.permission.CAMERA)
        }
    }
    return concedido to pedir
}

private tailrec fun Context.actividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}

/**
 * Círculo con anillo de progreso conic (01e: violeta sobre `#E4DEFA`; DNI: lima). Adentro va la
 * cámara o la foto, con el borde de 6 del color del fondo.
 */
@Composable
internal fun ingresoAnillo(
    tam: Dp,
    grosor: Dp,
    progreso: Float,
    color: Color,
    pista: Color,
    modifier: Modifier = Modifier,
    borde: Color = C.blancoCalido,
    contenido: @Composable () -> Unit,
) {
    val p by animateFloatAsState(progreso, tween(220, easing = EaseOut), label = "anillo")
    Box(modifier.size(tam), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(pista)
            // `conic-gradient(color 0 N%, …)`: el sector desde las 12 en sentido horario.
            if (p > 0f) drawArc(color, -90f, 360f * min(p, 1f), useCenter = true, topLeft = Offset.Zero, size = Size(size.width, size.height))
        }
        Box(
            Modifier.size(tam - grosor * 2).clip(CircleShape).background(Color(0xFFE9E4F7)),
            contentAlignment = Alignment.Center,
        ) {
            contenido()
            Box(Modifier.fillMaxSize().border(6.dp, borde, CircleShape))
        }
    }
}

/** Pill oscura de la instrucción. */
@Composable
internal fun ingresoPillOscura(texto: String) {
    Box(
        Modifier.animateContentSize(tween(200, easing = EaseOut)).gel(14.dp, Gels.oscuroSel).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        T(texto, 14f, 600, C.blancoCalido, alinear = TextAlign.Center)
    }
}

/** Imagen de un JPEG en memoria (llena la caja, como `scaledToFill`). */
@Composable
internal fun ingresoImagenDatos(datos: ByteArray, modifier: Modifier = Modifier) {
    val bmp = remember(datos) { runCatching { android.graphics.BitmapFactory.decodeByteArray(datos, 0, datos.size)?.asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bmp, null, modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}
