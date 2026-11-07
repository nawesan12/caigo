package ar.com.caigo.app.pantallas.ingreso

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Disparador
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.EscanerCodigos
import ar.com.caigo.app.piezas.VistaCamara
import ar.com.caigo.app.piezas.rememberPermiso
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.gel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Cámara frontal con la detección de caras conectada a [vida]. Como iOS (Vision), una cara cuenta si
 * ocupa más del 20 % del ancho del cuadro.
 */
@Composable
internal fun ingresoCamaraVida(vida: IngresoVida, disparador: Disparador, modifier: Modifier = Modifier) {
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(.2f)
                .build(),
        )
    }
    DisposableEffect(Unit) { onDispose { detector.close() } }
    VistaCamara(modifier, frontal = true, disparador = disparador) { img ->
        val media = ingresoImagenDe(img)
        if (media == null) { img.close(); return@VistaCamara }
        val lum = ingresoLuminancia(img)
        val rot = img.imageInfo.rotationDegrees
        val w = if (rot % 180 == 0) img.width else img.height
        val h = if (rot % 180 == 0) img.height else img.width
        detector.process(InputImage.fromMediaImage(media, rot))
            .addOnSuccessListener { caras ->
                val f = caras.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (f == null || f.boundingBox.width().toFloat() / w <= .2f) {
                    vida.cuadro(false, 0f, false, false, lum)
                } else {
                    val r: Rect = f.boundingBox
                    val cx = r.exactCenterX() / w
                    val cy = r.exactCenterY() / h
                    // Ojos abiertos (con lentes oscuros ML Kit no los ve abiertos).
                    val ojos = (f.leftEyeOpenProbability ?: 1f) > .5f && (f.rightEyeOpenProbability ?: 1f) > .5f
                    vida.cuadro(true, f.headEulerAngleY, abs(cx - .5f) < .17f && abs(cy - .5f) < .22f, ojos, lum)
                }
            }
            .addOnCompleteListener { img.close() }
    }
}

/** Cámara trasera que lee el PDF417 del DNI y saca la foto. */
@Composable
internal fun ingresoCamaraDni(disparador: Disparador, modifier: Modifier = Modifier, alLeer: (String) -> Unit) {
    EscanerCodigos(modifier, intArrayOf(Barcode.FORMAT_PDF417), disparador, alLeer)
}

/** 01e · Selfie con prueba de vida (Paso 2 de 2). */
@Composable
fun Selfie(ir: Ir, volver: Boolean) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val vista = LocalView.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val vida = remember { IngresoVida() }
    val disparador = remember { Disparador() }
    var foto by remember { mutableStateOf<ByteArray?>(null) }
    var cargando by remember { mutableStateOf(false) }
    val (permiso, pedirPermiso) = rememberIngresoPermisoCamara()
    LaunchedEffect(Unit) { if (!permiso) pedirPermiso() }

    // Al terminar la prueba de vida se saca la foto sola.
    LaunchedEffect(vida.fase) {
        if (vida.fase == 3 && foto == null) {
            try {
                foto = disparador.foto(ctx)
                ingresoVibrar(vista)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                avisos.error("No pudimos sacar la foto. Probá de nuevo.")
                vida.reiniciar()
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

    ingresoClaro { Pantalla(oscuro = false) { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba), horizontalAlignment = Alignment.CenterHorizontally) {
            ingresoBarraArriba(I.atras, { if (volver) ir.atras() else ir.alta("telefono") }, {
                if (!volver) T("Paso 2 de 2", 14f, 700, C.texto2)
            }, Modifier.padding(start = 20.dp, end = 24.dp, top = 12.dp))
            Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 32.dp)) {
                ingresoTitulo("Ahora, una selfie", "Es para confirmar que sos una persona real. No se publica.")
            }
            Spacer(Modifier.weight(1f).heightIn(min = 20.dp))
            ingresoAnillo(276.dp, 8.dp, if (foto != null) 1f else vida.progreso, C.violeta, C.pista) {
                val f = foto
                when {
                    f != null -> ingresoImagenDatos(f)
                    permiso -> ingresoCamaraVida(vida, disparador, Modifier.fillMaxSize())
                    else -> Icono(I.usuario, 64.dp, C.violeta, 2f)
                }
            }
            Spacer(Modifier.height(24.dp))
            ingresoPillOscura(instruccion)
            Spacer(Modifier.height(18.dp))
            // En pantallas angostas los chips bajan enteros al renglón de abajo (IngresoFilas de iOS).
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ingresoChipRequisito("Buena luz", (vida.luz && permiso) || foto != null)
                ingresoChipRequisito("Cara centrada", vida.centrada || foto != null)
                ingresoChipRequisito("Sin lentes de sol", vida.ojos || foto != null)
            }
            Spacer(Modifier.weight(1f).heightIn(min = 20.dp))
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
                    cargando = true
                    alcance.launch {
                        try {
                            val a = c.api.subir("selfie", f)
                            c.api.selfie(a.id)
                            if (volver) ir.atras() else ir.alta(ingresoRutaDeAlta(c))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            avisos.error(ingresoMensaje(e))
                        } finally {
                            cargando = false
                        }
                    }
                }, Modifier.fillMaxWidth(), habilitado = !permiso || foto != null, cargando = cargando)
            }
        }
    } }
}
