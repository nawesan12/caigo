package ar.com.caigo.app.ui.pantallas.ingreso

import android.Manifest
import android.graphics.Rect
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.Disparador
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.VistaCamara
import ar.com.caigo.app.ui.piezas.rememberPermiso
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.gel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Prueba de vida con ML Kit: mirar a la cámara, girar la cabeza a un lado y después al otro.
 * Además lleva los requisitos de la foto (buena luz, cara centrada, ojos a la vista).
 */
@Stable
internal class IngresoVida {
    /** 0 mirar a la cámara · 1 girar a la izquierda · 2 girar al otro lado · 3 listo. */
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

    /** Un cuadro analizado: [yaw] es el giro de la cabeza (headEulerAngleY). */
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
                progreso = .65f + .3f * (otro.coerceAtLeast(0f) / 22f).coerceAtMost(1f)
                if (otro >= 22f) { progreso = 1f; fase = 3 }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun imagenDe(img: ImageProxy): android.media.Image? = img.image

/** Luminancia media del plano Y (muestreada). */
private fun luminancia(img: ImageProxy): Float {
    val b = img.planes.firstOrNull()?.buffer ?: return 128f
    b.rewind()
    var suma = 0L
    var n = 0
    var i = 0
    val paso = 40
    while (i < b.limit()) { suma += (b.get(i).toInt() and 0xFF); n++; i += paso }
    return if (n == 0) 128f else suma.toFloat() / n
}

/** Cámara frontal con la detección de caras conectada a [vida]. */
@Composable
internal fun ingresoCamaraVida(vida: IngresoVida, disparador: Disparador, modifier: Modifier = Modifier) {
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(.25f)
                .build(),
        )
    }
    DisposableEffect(Unit) { onDispose { detector.close() } }
    VistaCamara(modifier, frontal = true, disparador = disparador) { img ->
        val media = imagenDe(img)
        if (media == null) { img.close(); return@VistaCamara }
        val lum = luminancia(img)
        val rot = img.imageInfo.rotationDegrees
        val w = if (rot % 180 == 0) img.width else img.height
        val h = if (rot % 180 == 0) img.height else img.width
        detector.process(InputImage.fromMediaImage(media, rot))
            .addOnSuccessListener { caras ->
                val f = caras.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (f == null) {
                    vida.cuadro(false, 0f, false, false, lum)
                } else {
                    val r: Rect = f.boundingBox
                    val cx = r.exactCenterX() / w
                    val cy = r.exactCenterY() / h
                    val ojos = (f.leftEyeOpenProbability ?: 1f) > .5f && (f.rightEyeOpenProbability ?: 1f) > .5f
                    vida.cuadro(true, f.headEulerAngleY, abs(cx - .5f) < .17f && abs(cy - .5f) < .22f, ojos, lum)
                }
            }
            .addOnCompleteListener { img.close() }
    }
}

/**
 * Círculo con anillo de progreso conic (01e: violeta sobre `#E4DEFA`; DNI: lima). Adentro va la
 * cámara o la foto, con el borde de 6 px del color del fondo.
 */
@Composable
internal fun ingresoAnillo(
    tam: Dp,
    grosor: Dp,
    progreso: Float,
    color: Color,
    pista: Color,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    val p by animateFloatAsState(progreso, tween(220), label = "anillo")
    Box(modifier.size(tam), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(pista)
            drawArc(color, -90f, 360f * p, useCenter = true, topLeft = Offset.Zero, size = Size(size.width, size.height))
        }
        Box(
            Modifier.size(tam - grosor * 2).clip(CircleShape).background(Color(0xFFE9E4F7))
                .border(6.dp, C.blancoCalido, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            contenido()
            Box(Modifier.fillMaxSize().border(6.dp, C.blancoCalido, CircleShape))
        }
    }
}

/** Pill oscura de la instrucción. */
@Composable
internal fun ingresoPillOscura(texto: String) {
    Box(Modifier.gel(14.dp, Gels.oscuroSel).padding(horizontal = 16.dp, vertical = 10.dp)) {
        T(texto, 14f, 600, C.blancoCalido)
    }
}

/** 01e · Selfie con prueba de vida (Paso 2 de 2). */
@Composable
fun Selfie(ir: Ir, volver: Boolean) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val vida = remember { IngresoVida() }
    val disparador = remember { Disparador() }
    var foto by remember { mutableStateOf<ByteArray?>(null) }
    var cargando by remember { mutableStateOf(false) }
    val (permiso, pedirPermiso) = rememberPermiso(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) { if (!permiso) pedirPermiso() }

    // Al terminar la prueba de vida se saca la foto sola.
    LaunchedEffect(vida.fase) {
        if (vida.fase == 3 && foto == null) {
            foto = runCatching { disparador.foto(ctx) }.getOrElse {
                avisos.error("No pudimos sacar la foto. Probá de nuevo.")
                vida.reiniciar()
                null
            }
        }
    }

    val instruccion = when {
        !permiso -> "Activá la cámara para seguir"
        foto != null -> "Listo"
        vida.fase == 1 -> "Girá la cabeza despacio a la izquierda"
        vida.fase == 2 -> "Ahora a la derecha"
        else -> "Mirá a la cámara"
    }

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba), horizontalAlignment = Alignment.CenterHorizontally) {
            ingresoBarraArriba(I.atras, { if (volver) ir.atras() else ir.alta("telefono") }, {
                if (!volver) T("Paso 2 de 2", 14f, 700, C.texto2)
            }, Modifier.padding(start = 20.dp, end = 24.dp, top = 12.dp))
            Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 32.dp)) {
                ingresoTitulo("Ahora, una selfie", "Es para confirmar que sos una persona real. No se publica.")
            }
            Spacer(Modifier.weight(1f).height(20.dp))
            ingresoAnillo(276.dp, 8.dp, if (foto != null) 1f else vida.progreso, C.violeta, C.pista) {
                val f = foto
                when {
                    f != null -> {
                        val bmp = remember(f) { android.graphics.BitmapFactory.decodeByteArray(f, 0, f.size).asImageBitmap() }
                        Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    permiso -> ingresoCamaraVida(vida, disparador, Modifier.fillMaxSize())
                    else -> Icono(I.usuario, 64.dp, C.violeta, 2f)
                }
            }
            Spacer(Modifier.height(24.dp))
            ingresoPillOscura(instruccion)
            Spacer(Modifier.height(18.dp))
            // En pantallas más angostas que el mockup los chips bajan enteros al renglón de abajo (nunca se parte el texto).
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ingresoChipRequisito("Buena luz", vida.luz && permiso)
                ingresoChipRequisito("Cara centrada", vida.centrada || foto != null)
                ingresoChipRequisito("Sin lentes de sol", vida.ojos || foto != null)
            }
            Spacer(Modifier.weight(1f).height(20.dp))
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 36.dp + abajo),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icono(I.escudo, 16.dp, C.violeta, 2f)
                    T("Prueba de vida · tus datos viajan encriptados", 13f, 400, C.texto2)
                }
                Cta("Verificarme", {
                    val f = foto
                    if (!permiso) { pedirPermiso(); return@Cta }
                    if (f == null) return@Cta
                    alcance.launch {
                        cargando = true
                        try {
                            val a = c.api.subir("selfie", f)
                            c.api.selfie(a.id)
                            if (volver) ir.atras() else ir.alta(rutaDeAlta(c))
                        } catch (e: Exception) {
                            avisos.error(ingresoMensaje(e))
                        } finally {
                            cargando = false
                        }
                    }
                }, Modifier.fillMaxWidth(), habilitado = !permiso || foto != null, cargando = cargando)
            }
        }
    }
}
