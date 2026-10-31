package ar.com.caigo.app.ui.piezas

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Para sacar fotos desde una [VistaCamara]. */
class Disparador {
    internal var captura: ImageCapture? = null

    /** Saca una foto y la devuelve como JPEG (girada derecha y, si es frontal, sin espejo). */
    suspend fun foto(ctx: android.content.Context, calidad: Int = 88, ladoMax: Int = 1600): ByteArray {
        val c = captura ?: error("La cámara no está lista")
        val img: ImageProxy = suspendCancellableCoroutine { cont ->
            c.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = cont.resume(image)
                override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
            })
        }
        return img.use { jpegDe(it.toBitmap(), it.imageInfo.rotationDegrees, calidad, ladoMax) }
    }
}

fun jpegDe(b: Bitmap, rotacion: Int, calidad: Int = 88, ladoMax: Int = 1600): ByteArray {
    val k = minOf(1f, ladoMax.toFloat() / maxOf(b.width, b.height))
    val m = Matrix().apply { postRotate(rotacion.toFloat()); postScale(k, k) }
    val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    return ByteArrayOutputStream().use { r.compress(Bitmap.CompressFormat.JPEG, calidad, it); it.toByteArray() }
}

/**
 * Vista previa de CameraX. [analizar] recibe cada cuadro (en un hilo aparte; hay que cerrarlo).
 * Con [disparador] se pueden sacar fotos.
 */
@Composable
fun VistaCamara(
    modifier: Modifier = Modifier,
    frontal: Boolean = false,
    disparador: Disparador? = null,
    analizar: ((ImageProxy) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val analizador = rememberUpdatedState(analizar)
    val hilo = remember { Executors.newSingleThreadExecutor() }
    val vista = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    DisposableEffect(frontal) {
        val futuro = ProcessCameraProvider.getInstance(ctx)
        var proveedor: ProcessCameraProvider? = null
        futuro.addListener({
            val p = futuro.get().also { proveedor = it }
            val preview = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
            val usos = mutableListOf<androidx.camera.core.UseCase>(preview)
            if (analizar != null) {
                usos += ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { a ->
                    a.setAnalyzer(hilo) { img -> analizador.value?.invoke(img) ?: img.close() }
                }
            }
            if (disparador != null) {
                usos += ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build().also { disparador.captura = it }
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(ciclo, if (frontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, *usos.toTypedArray())
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { runCatching { proveedor?.unbindAll() } }
    }
    DisposableEffect(Unit) { onDispose { hilo.shutdown() } }
    AndroidView({ vista }, modifier)
}

/**
 * Escanea códigos con ML Kit (QR por defecto; PDF417 para el DNI) y avisa el primero que lee.
 */
@Composable
fun EscanerCodigos(
    modifier: Modifier = Modifier,
    formatos: IntArray = intArrayOf(Barcode.FORMAT_QR_CODE),
    disparador: Disparador? = null,
    alLeer: (String) -> Unit,
) {
    val lector = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(formatos.first(), *formatos.drop(1).toIntArray()).build())
    }
    val avisar = rememberUpdatedState(alLeer)
    DisposableEffect(Unit) { onDispose { lector.close() } }
    VistaCamara(modifier, frontal = false, disparador = disparador) { img ->
        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        val media = img.image
        if (media == null) { img.close(); return@VistaCamara }
        lector.process(InputImage.fromMediaImage(media, img.imageInfo.rotationDegrees))
            .addOnSuccessListener { cs -> cs.firstNotNullOfOrNull { it.rawValue }?.let { v -> avisar.value(v) } }
            .addOnCompleteListener { img.close() }
    }
}

/** Matriz de un QR (ZXing). */
fun matrizQr(texto: String): Array<BooleanArray> {
    val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    return Array(m.height) { y -> BooleanArray(m.width) { x -> m[x, y] } }
}

/** QR dibujado con módulos cuadrados en carbón (como el de "Tu entrada"). */
@Composable
fun CodigoQr(texto: String, modifier: Modifier = Modifier, color: Color = Color(0xFF24212D)) {
    val m = remember(texto) { matrizQr(texto) }
    Canvas(modifier) {
        val n = m.size
        val lado = size.minDimension / n
        for (y in 0 until n) for (x in 0 until n) if (m[y][x]) {
            drawRect(color, androidx.compose.ui.geometry.Offset(x * lado, y * lado), androidx.compose.ui.geometry.Size(lado + .5f, lado + .5f))
        }
    }
}
