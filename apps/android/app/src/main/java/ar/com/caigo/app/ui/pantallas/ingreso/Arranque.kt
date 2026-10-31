package ar.com.caigo.app.ui.pantallas.ingreso

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.Destino
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.datos.ErrorApi
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.EspaciadoTitulo
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.dibujarGrano
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.radial
import ar.com.caigo.app.ui.tema.rgba
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

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
        modifier.size(tam).background(
            radial(.5f, .5f, 0f, 0f,
                0f to claro.copy(alpha = alfa), .35f to medio.copy(alpha = alfa), .55f to oscuro.copy(alpha = alfa * .7f), .72f to oscuro.copy(alpha = 0f),
                circulo = true),
        ),
    )
}

/**
 * 00 · Splash. Mientras se ve, decide a dónde ir: sin sesión, a la intro (la primera vez) o al
 * ingreso; con sesión, al paso del alta que falte o a la app.
 */
@Composable
fun Arranque(ir: Ir) {
    val c = LocalApp.current
    val progreso = remember { Animatable(.15f) }
    LaunchedEffect(Unit) {
        val minimo = async { progreso.animateTo(.55f, tween(900, easing = LinearEasing)) }
        val ruta: String = when {
            c.sesion.token.value == null -> if (c.sesion.vioIntro || c.destino.value is Destino.VueltaClerk) "ingresar" else "intro"
            else -> try {
                rutaDeAlta(c)
            } catch (e: ErrorApi) {
                if (e.estado == 401) "ingresar" else ingresoRutaApp
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Sin conexión: se entra igual y cada pantalla avisa.
                ingresoRutaApp
            }
        }
        minimo.await()
        progreso.animateTo(1f, tween(250))
        delay(80)
        ir.alta(ruta)
    }
    Pantalla(fondo = Modifier.ingresoFondoSplash()) { _, abajo ->
        ingresoMancha(180.dp + 60.dp, Color(0xFFF2FFC8), C.lima, Color(0xFF9CCB3A), .5f, Modifier.offset((-60 - 30).dp, (120 - 30).dp))
        ingresoMancha(150.dp + 60.dp, Color(0xFFFFC4BA), C.coral, Color(0xFFE0503F), .45f, Modifier.align(Alignment.BottomEnd).offset((50 + 30).dp, (-150 + 30).dp - abajo))
        Column(
            Modifier.fillMaxSize().padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // drop-shadow(0 30px 34px rgba(0,0,0,.5)) del isotipo
                Box(Modifier.size(150.dp).gel(42.dp, null, listOf(externa(30f, 34f, rgba(0, 0, 0, .5f)))))
                Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(188.dp))
            }
            T("Caigo", 46f, 700, C.blancoCalido, alto = 1f, espaciado = EspaciadoTitulo)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp + abajo),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            T("Hacé planes. Conocé gente.", 20f, 700, C.blanco, espaciado = px(-.2f, 20f))
            Box(Modifier.width(120.dp).height(5.dp).background(rgba(255, 255, 255, .22f), RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progreso.value).background(C.lima, RoundedCornerShape(3.dp)))
            }
        }
    }
}
