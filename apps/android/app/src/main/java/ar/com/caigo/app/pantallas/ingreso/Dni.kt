package ar.com.caigo.app.pantallas.ingreso

import ar.com.caigo.app.piezas.Sistema
import android.Manifest
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.nucleo.DatosDNI
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.DatosDni
import ar.com.caigo.app.nucleo.Pdf417Dni
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.piezas.Avatar
import ar.com.caigo.app.piezas.Disparador
import ar.com.caigo.app.piezas.EscanerCodigos
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.rememberPermiso
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.rgba
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pasos de la verificación con DNI (verify del prototipo). */
private object PasoDni {
    const val CARGANDO = 0
    const val FRENTE = 1
    const val DORSO = 2
    const val SELFIE = 3
    const val REVISANDO = 4
    const val ENVIADO = 5
    const val APROBADO = 6
}

/** Barras de progreso de los tres pasos (7 px): hechos violeta, el actual `#B3A3FF`. */
@Composable
private fun progresoDni(paso: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..3).forEach { i ->
            val col = when {
                i < paso || paso >= PasoDni.REVISANDO -> C.violeta
                i == paso -> C.violeta1
                else -> C.pista
            }
            Box(Modifier.weight(1f).height(7.dp).background(col, RoundedCornerShape(4.dp)))
        }
    }
}

/** Las cuatro esquinas lima del encuadre (34 px, 4 px de trazo, radio 10). */
@Composable
private fun esquinas(modifier: Modifier) {
    Canvas(modifier) {
        val m = 22.dp.toPx() + 2.dp.toPx()
        val l = 34.dp.toPx() - 2.dp.toPx()
        val r = 10.dp.toPx()
        val s = Stroke(4.dp.toPx(), cap = StrokeCap.Butt)
        fun esquina(x: Float, y: Float, dx: Float, dy: Float) {
            val p = Path().apply {
                moveTo(x, y + dy * l)
                lineTo(x, y + dy * r)
                quadraticTo(x, y, x + dx * r, y)
                lineTo(x + dx * l, y)
            }
            drawPath(p, C.lima, style = s)
        }
        esquina(m, m, 1f, 1f)
        esquina(size.width - m, m, -1f, 1f)
        esquina(m, size.height - m, 1f, -1f)
        esquina(size.width - m, size.height - m, -1f, -1f)
    }
}

/** Ícono de Caigo latiendo (tk-pop alternado) mientras se revisa. */
@Composable
private fun iconoLatiendo() {
    val t = rememberInfiniteTransition(label = "pop")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(600, easing = androidx.compose.animation.core.EaseInOut), RepeatMode.Reverse), label = "k")
    Image(
        painterResource(R.drawable.caigo_icon), null,
        Modifier.size(120.dp).graphicsLayer {
            val s = .9f + .18f * k
            scaleX = s; scaleY = s; rotationZ = -6f + 10f * k
        },
    )
}

/**
 * Verificación con DNI del prototipo: frente (con lectura del PDF417), dorso, selfie con prueba
 * de vida y el envío. La revisión la hace una persona del equipo: queda pendiente.
 */
@Composable
fun Dni(ir: Ir, planId: String?) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val vista = androidx.compose.ui.platform.LocalView.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var paso by remember { mutableIntStateOf(PasoDni.CARGANDO) }
    var datos by remember { mutableStateOf<DatosDni?>(null) }
    var frente by remember { mutableStateOf<ByteArray?>(null) }
    var dorso by remember { mutableStateOf<ByteArray?>(null) }
    var selfie by remember { mutableStateOf<ByteArray?>(null) }
    var disparo by remember { mutableStateOf(false) }
    val vida = remember { IngresoVida() }
    val disparador = remember(paso) { Disparador() }
    val (permiso, pedirPermiso) = rememberIngresoPermisoCamara()
    val etiquetaFin = if (planId != null) "Seguir con el plan" else "Listo"

    LaunchedEffect(Unit) {
        paso = runCatching { c.api.verificacion() }.getOrNull()?.let {
            when (it.dni) {
                "pendiente" -> PasoDni.ENVIADO
                "aprobada" -> PasoDni.APROBADO
                else -> PasoDni.FRENTE
            }
        } ?: PasoDni.FRENTE
        if (!permiso && paso == PasoDni.FRENTE) pedirPermiso()
    }

    val enviar: () -> Unit = {
        alcance.launch {
            paso = PasoDni.REVISANDO
            try {
                val d = datos ?: error("sin datos")
                val f = c.api.subir("dni", frente!!)
                val r = c.api.subir("dni", dorso!!)
                val s = c.api.subir("selfie", selfie!!)
                c.api.dni(DatosDNI(f.id, r.id, s.id, d.numero, d.apellido, d.nombres, d.nacimiento, d.tramite))
                paso = PasoDni.ENVIADO
            } catch (e: ErrorApi) {
                if (e.codigo == "pendiente") paso = PasoDni.ENVIADO
                else {
                    avisos.error(ingresoMensaje(e))
                    vida.reiniciar(); selfie = null; frente = null; dorso = null; datos = null
                    paso = PasoDni.FRENTE
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(ingresoMensaje(e))
                vida.reiniciar(); selfie = null
                paso = PasoDni.SELFIE
            }
        }
    }

    // La selfie se saca sola al completar la prueba de vida.
    LaunchedEffect(vida.fase, paso) {
        if (paso == PasoDni.SELFIE && vida.fase == 3 && selfie == null) {
            selfie = runCatching { disparador.foto(ctx) }.getOrNull()
            if (selfie == null) { avisos.error("No pudimos sacar la foto. Probá de nuevo."); vida.reiniciar() }
            else { delay(400); enviar() }
        }
    }

    val obturador: () -> Unit = obt@{
        if (!permiso) { pedirPermiso(); return@obt }
        if (disparo) return@obt
        alcance.launch {
            val foto = runCatching { disparador.foto(ctx) }.getOrNull()
            if (foto == null) { avisos.error("No pudimos sacar la foto. Probá de nuevo."); return@launch }
            if (paso == PasoDni.FRENTE) frente = foto else dorso = foto
            disparo = true
            Sistema.vibrar(vista)
            delay(650)
            disparo = false
            if (paso == PasoDni.DORSO && datos == null) {
                // El código está en el frente (DNI nuevo) o en el dorso (DNI viejo): sin leerlo no se puede seguir.
                avisos.error("No pudimos leer el número de DNI.")
                dorso = null
                return@launch
            }
            paso += 1
        }
    }

    ingresoClaro { Pantalla(oscuro = false) { arriba, abajo ->
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = paso != PasoDni.FRENTE && paso != PasoDni.DORSO && paso != PasoDni.SELFIE)
                .padding(start = 20.dp, end = 20.dp, top = arriba + 8.dp, bottom = 40.dp + abajo),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ingresoBarraArriba(I.cruz, { ir.atras() }, { ingresoEncriptado() })
            // withAnimation(.easeOut(duration: 0.25)) al pasar de paso: el paso nuevo aparece. Sin
            // AnimatedContent: dos cámaras a la vez se pisan el ProcessCameraProvider.
            val aparece = remember(paso) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(paso) { aparece.animateTo(1f, tween(250, easing = androidx.compose.animation.core.EaseOut)) }
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = aparece.value }, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            when (paso) {
                PasoDni.CARGANDO -> Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { Puntitos() }

                PasoDni.FRENTE, PasoDni.DORSO -> {
                    progresoDni(paso)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(if (paso == PasoDni.FRENTE) "Frente del DNI" else "Dorso del DNI", 32f, 800, C.carbon, alto = 1.02f, espaciado = px(-1f, 32f))
                        T("Apoyalo en una superficie oscura y encuadralo.", 15f, 400, C.texto2, alto = 1.5f)
                    }
                    Box(
                        Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(28.dp)).background(C.oscuro6),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (permiso) {
                            androidx.compose.runtime.key(paso) {
                                ingresoCamaraDni(disparador, Modifier.fillMaxSize()) { crudo ->
                                    if (datos == null) Pdf417Dni.leer(crudo)?.let { datos = it; Sistema.vibrar(vista) }
                                }
                            }
                        }
                        esquinas(Modifier.fillMaxSize())
                        androidx.compose.animation.AnimatedVisibility(
                            disparo,
                            enter = androidx.compose.animation.fadeIn(tween(200, easing = androidx.compose.animation.core.EaseOut)),
                            exit = androidx.compose.animation.fadeOut(tween(200, easing = androidx.compose.animation.core.EaseOut)),
                        ) {
                            Box(Modifier.fillMaxSize().background(rgba(32, 29, 43, .6f)), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(72.dp).ingresoPop().background(C.lima, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                                    Icono(I.check, 34.dp, C.carbon, 3.2f)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        Box(Modifier.background(C.lavanda, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            T("Sin reflejos", 13f, 700, C.carbon)
                        }
                        if (datos != null) ingresoChipRequisito("Que se lean los datos", true)
                        else Box(Modifier.background(C.lavanda, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            T("Que se lean los datos", 13f, 700, C.carbon)
                        }
                    }
                    if (!disparo) {
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                            ingresoObturador(obturador)
                        }
                    }
                }

                PasoDni.SELFIE -> {
                    progresoDni(paso)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T("Comparamos con tu cara", 32f, 800, C.carbon, alto = 1.02f, espaciado = px(-1f, 32f))
                        T("Así confirmamos que el DNI es tuyo.", 15f, 400, C.texto2, alto = 1.5f)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(260.dp).gel(130.dp, null, listOf(externa(20f, 40f, rgba(80, 60, 160, .5f), spread = -16f))))
                        ingresoAnillo(260.dp, 9.dp, vida.progreso, C.lima, rgba(255, 255, 255, .18f)) {
                            val s = selfie
                            if (s != null) {
                                ingresoImagenDatos(s)
                            } else if (permiso) {
                                ingresoCamaraVida(vida, disparador, Modifier.fillMaxSize())
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val pista = when {
                            vida.fase == 1 -> "Girá despacio a la izquierda"
                            vida.fase >= 2 -> "Ahora a la derecha"
                            else -> "Mirá a la cámara"
                        }
                        Box(Modifier.background(C.carbon, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                            T(pista, 14f, 700, C.blancoCalido)
                        }
                        T("${(vida.progreso * 100).toInt()}%", 22f, 800, C.violetaTexto)
                    }
                }

                PasoDni.REVISANDO, PasoDni.ENVIADO -> {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        iconoLatiendo()
                        T("Revisando tus datos…", 26f, 700, C.carbon, alto = 1.1f, alinear = TextAlign.Center)
                        T(
                            if (paso == PasoDni.REVISANDO) "Comparamos tu selfie con la foto del DNI. Tarda unos segundos."
                            else "Los revisa una persona del equipo, no un bot.",
                            14f, 400, C.texto2, alto = 1.5f, alinear = TextAlign.Center,
                        )
                        if (paso == PasoDni.ENVIADO) {
                            Spacer(Modifier.height(12.dp))
                            ingresoCtaGrande(etiquetaFin, { ir.atras() })
                        }
                    }
                }

                PasoDni.APROBADO -> {
                    val yo = c.yo.value
                    Column(
                        Modifier.fillMaxWidth().padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box {
                            Box(Modifier.size(150.dp).gel(75.dp, null, listOf(externa(18f, 30f, rgba(80, 60, 160, .5f), spread = -12f))))
                            Avatar(yo?.nombre.orEmpty(), yo?.fotos?.firstOrNull(), 150.dp, borde = 5.dp, colorBorde = C.blanco)
                            Box(
                                Modifier.align(Alignment.BottomEnd).offset(4.dp, (-4).dp).size(52.dp).ingresoPop()
                                    .gel(18.dp, Gel(Gels.violeta.fondo, listOf(interna(3f, 2f, rgba(255, 255, 255, .45f)), externa(4f, 0f, C.violetaBase)))),
                                contentAlignment = Alignment.Center,
                            ) { Icono(I.check, 26.dp, C.lima, 3.2f) }
                        }
                        T("Tu DNI ya está verificado", 34f, 800, C.carbon, alto = 1.02f, espaciado = px(-1f, 34f), alinear = TextAlign.Center)
                        T("Tu perfil muestra el badge y podés sumarte a planes «solo verificados».", 15f, 400, C.texto2, alto = 1.5f, alinear = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        ingresoCtaGrande(etiquetaFin, { ir.atras() })
                    }
                }
            }
            }
        }
    } }
}
