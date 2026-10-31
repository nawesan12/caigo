package ar.com.caigo.app.ui.pantallas.ingreso

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Avatar
import ar.com.caigo.app.ui.piezas.BadgeFalta
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.EspaciadoTitulo
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.radial
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical
import kotlinx.coroutines.launch

/** Íconos de la barra de estado claros (sobre fondos oscuros) mientras la pantalla está a la vista. */
@Composable
internal fun ingresoBarraClara(clara: Boolean) {
    val vista = LocalView.current
    val oscuroSistema = isSystemInDarkTheme()
    DisposableEffect(clara) {
        val w = (vista.context as? Activity)?.window
        if (w != null) WindowCompat.getInsetsController(w, vista).isAppearanceLightStatusBars = !clara
        onDispose { if (w != null) WindowCompat.getInsetsController(w, vista).isAppearanceLightStatusBars = !oscuroSistema }
    }
}

/** Las medidas del mockup (390×844) llevadas al tamaño real de la pantalla. */
internal class IngresoEscala(val w: Dp, val h: Dp) {
    fun x(v: Float): Dp = w * (v / 390f)
    fun y(v: Float): Dp = h * (v / 844f)
}

@Composable
internal fun BoxWithConstraintsScope.ingresoEscala() = remember(maxWidth, maxHeight) { IngresoEscala(maxWidth, maxHeight) }

/** "Saltar": píldora translúcida sobre la foto. */
@Composable
internal fun ingresoSaltar(alfa: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.tocable(onClick).background(rgba(255, 255, 255, alfa), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { T("Saltar", 14f, 700, C.blanco) }
}

/** CTA "Siguiente →" de la intro. */
@Composable
private fun siguiente(alto: Dp, onClick: () -> Unit) {
    Row(
        Modifier.height(alto).toque(onClick = onClick).gel(22.dp, Gels.limaChico).padding(start = 26.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("Siguiente", 17f, 800, C.grafito)
        Icono(I.flecha, 22.dp, C.grafito, 2.6f)
    }
}

/** 01a–01c: tres slides con swipe. Al terminar queda marcada la intro y se va a ingresar. */
@Composable
fun Intro(ir: Ir) {
    val c = LocalApp.current
    val pager = rememberPagerState { 3 }
    val alcance = rememberCoroutineScope()
    val terminar: () -> Unit = {
        alcance.launch {
            c.sesion.marcarIntro()
            ir.alta("ingresar")
        }
    }
    val avanzar: () -> Unit = { alcance.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
    ingresoBarraClara(pager.currentPage != 1)
    HorizontalPager(pager, Modifier.fillMaxSize()) { pagina ->
        when (pagina) {
            0 -> slidePlanes(terminar, avanzar)
            1 -> slideAmigos(terminar, avanzar)
            else -> slideAfuera(terminar)
        }
    }
}

/** 01a · Encontrá planes. */
@Composable
private fun slidePlanes(saltar: () -> Unit, avanzar: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(C.grafito)) {
        val e = ingresoEscala()
        Image(
            painterResource(R.drawable.ingreso_previa), null,
            Modifier.fillMaxWidth().height(e.y(560f)),
            contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(-.2f, -.3f),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to rgba(32, 29, 43, .55f), .18f to rgba(32, 29, 43, 0f), .46f to rgba(32, 29, 43, 0f),
                    .60f to rgba(32, 29, 43, .85f), .67f to C.grafito, 1f to C.grafito,
                ),
            ),
        )
        ingresoContenido { arriba, nav ->
            Row(
                Modifier.fillMaxWidth().padding(top = arriba + 12.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("Caigo", 30f, 700, C.blancoCalido, alto = 1f, espaciado = EspaciadoTitulo)
                ingresoSaltar(.18f, saltar)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 38.dp + nav)) {
                // Tarjeta flotante de ejemplo del handoff.
                Row(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                        .gel(24.dp, Gel(vertical(rgba(255, 255, 255, .97f), rgba(246, 243, 255, .94f)), listOf(interna(1.5f, 0f, C.blanco), externa(22f, 40f, rgba(0, 0, 0, .6f), spread = -14f))))
                        .padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row {
                        listOf("N" to (C.violeta to C.blanco), "L" to (C.coral to C.carbon), "C" to (C.lima to C.carbon)).forEachIndexed { i, (l, col) ->
                            Avatar(l, null, 32.dp, Modifier.offset(x = (-9 * i).dp), borde = 2.5.dp, colorBorde = C.blanco, fondo = col, tamTexto = 12f)
                        }
                    }
                    Column(Modifier.weight(1f).offset(x = (-18).dp)) {
                        T("Previa en La Perla", 14f, 800, C.carbon, unRenglon = true)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(7.dp).background(C.coral, CircleShape))
                            T("Ahora · 900 m", 12f, 600, C.texto2, unRenglon = true)
                        }
                    }
                    BadgeFalta(1, tam = 17f)
                }
                Spacer(Modifier.height(e.y(64f)))
                Column(Modifier.padding(horizontal = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T("Planes a los que les falta gente.", 40f, 800, C.blancoCalido, alto = 1f, espaciado = px(-1.4f, 40f))
                    T("Una previa, un partido, una mesa de truco. Pasando ahora, cerca tuyo.", 15f, 400, Color(0xFFC9C4DA), alto = 1.5f)
                }
                Spacer(Modifier.height(e.y(42f)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ingresoPuntos(0, 3, C.lima, Color(0xFF4A4460))
                    siguiente(58.dp, avanzar)
                }
            }
        }
    }
}

/** Capa con los insets de las barras del sistema (arriba: barra de estado; abajo: navegación). */
@Composable
internal fun ingresoContenido(contenido: @Composable androidx.compose.foundation.layout.BoxScope.(Dp, Dp) -> Unit) {
    val a = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val n = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) { contenido(a, n) }
}

/** 01b · Sumate solo o con amigos. */
@Composable
private fun slideAmigos(saltar: () -> Unit, avanzar: () -> Unit) {
    var cuantos by remember { mutableIntStateOf(1) }
    BoxWithConstraints(Modifier.fillMaxSize().background(C.blancoCalido).ingresoGrano()) {
        val e = ingresoEscala()
        val altoTotal = maxHeight
        Image(painterResource(R.drawable.ingreso_amigos), null, Modifier.fillMaxWidth().height(maxHeight - 300.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(rgba(32, 29, 43, .5f), rgba(32, 29, 43, 0f)))))
        ingresoContenido { arriba, nav ->
            ingresoSaltar(.22f, saltar, Modifier.align(Alignment.TopEnd).padding(top = arriba + 12.dp, end = 24.dp))
          Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
            // Píldora "Cita doble" sobre la foto, siempre arriba de la hoja.
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 22.dp)
                    .gel(22.dp, Gel(vertical(rgba(255, 255, 255, .95f), rgba(248, 246, 255, .9f)), listOf(interna(1.5f, 0f, C.blanco), externa(20f, 40f, rgba(20, 10, 60, .55f), spread = -14f))))
                    .padding(start = 18.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row {
                    Avatar("C", null, 38.dp, Modifier.offset(x = 0.dp), colorBorde = C.blanco, fondo = C.violeta to C.blanco, tamTexto = 15f)
                    Avatar("L", null, 38.dp, Modifier.offset(x = (-8).dp), colorBorde = C.blanco, fondo = C.coral to C.carbon, tamTexto = 15f)
                }
                Box(
                    Modifier.offset(x = (-8).dp).size(34.dp).gel(12.dp, Gel(Gels.violeta.fondo, listOf(
                        interna(3f, 2f, rgba(255, 255, 255, .45f)), interna(-5f, 0f, rgba(40, 20, 140, .35f)), externa(4f, 0f, C.violetaBase),
                        externa(16f, 26f, rgba(116, 86, 241, .75f), spread = -10f),
                    ))),
                    contentAlignment = Alignment.Center,
                ) { Icono(I.mas, 16.dp, C.lima, 4f) }
                T("Cita doble", 17f, 700, C.carbon, modifier = Modifier.offset(x = (-8).dp))
            }
            }
            // Hoja de abajo: alto natural, pegada abajo; la foto y la píldora ocupan lo que sobra.
            Column(
                Modifier.fillMaxWidth()
                    .background(C.blancoCalido, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .ingresoGrano()
                    .padding(start = 26.dp, end = 26.dp, top = 30.dp, bottom = 40.dp + nav),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().gel(20.dp, vertical(C.tinte1, C.tinte5), listOf(interna(2f, 4f, rgba(116, 86, 241, .15f)))).padding(5.dp),
                ) {
                    listOf("Voy solo", "Somos 2", "Somos 4").forEachIndexed { i, o ->
                        val on = i == cuantos
                        Box(
                            Modifier.weight(1f).height(46.dp).tocable { cuantos = i }
                                .then(if (on) Modifier.gel(16.dp, Gel(Gels.violeta.fondo, listOf(
                                    interna(3f, 2f, rgba(255, 255, 255, .45f)), interna(-5f, 0f, rgba(40, 20, 140, .35f)), externa(4f, 0f, C.violetaBase),
                                    externa(16f, 26f, rgba(116, 86, 241, .75f), spread = -10f),
                                ))) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { T(o, 15f, if (on) 800 else 600, if (on) C.blanco else C.texto2, unRenglon = true) }
                    }
                }
                T("Sumate solo o con amigos.", 40f, 800, C.carbon, alto = 1f, espaciado = px(-1.4f, 40f))
                T("Entrás con tu gente en un toque. Así salen las citas dobles y los partidos grupo contra grupo.", 16f, 400, C.texto2, alto = 1.5f)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ingresoPuntos(1, 3, C.violeta, Color(0xFFD9D3EE))
                    siguiente(60.dp, avanzar)
                }
            }
          }
        }
    }
}

/** 01c · Lo mejor pasa afuera. */
@Composable
private fun slideAfuera(terminar: () -> Unit) {
    Box(Modifier.fillMaxSize().ingresoFondoVioleta()) {
        ingresoContenido { _, nav ->
            Column(Modifier.fillMaxSize()) {
                ingresoZonaFogon(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 34.dp + nav),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        T("Lo mejor pasa afuera.", 46f, 800, C.blanco, alto = .98f, espaciado = px(-1.8f, 46f))
                        T("Compartís un plan y conocés gente. Sin swipes, sin chats eternos.", 16f, 400, C.blanco, alto = 1.5f)
                    }
                    Spacer(Modifier.height(14.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Cta("Empezar", terminar, Modifier.fillMaxWidth(), alto = 62.dp, radio = 22.dp, tam = 18f, color = C.carbon, icono = I.mas, iconoTam = 18.dp, iconoTrazo = 4f)
                        Box(Modifier.tocable(terminar).padding(4.dp)) { T("Ya tengo cuenta", 15f, 700, C.blanco) }
                    }
                }
            }
        }
    }
}

/**
 * La composición del fogón (y 110…530 del mockup) en el espacio que queda arriba del texto: se escala
 * a ese alto para que nunca se pise con el título en pantallas más bajas que el mockup.
 */
@Composable
internal fun ingresoZonaFogon(modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val e = remember(maxWidth, maxHeight) { IngresoEscala(minOf(maxWidth, maxHeight * (390f / 520f)), maxHeight * (844f / 560f)) }
        Box(Modifier.align(Alignment.TopCenter).width(e.w).fillMaxHeight()) { ingresoComposicionFogon(e) }
    }
}

/** Fondo violeta de 01c (y de Ingresar): radial lavanda arriba a la derecha + grano. */
internal fun Modifier.ingresoFondoVioleta(): Modifier = this
    .background(Color(0xFF5A3DD8))
    .background(radial(.85f, 0f, 1.2f, .7f, 0f to Color(0xFFA38FFF), .45f to C.violeta, 1f to Color(0xFF5A3DD8)))
    .ingresoGrano()

/** La foto del fogón inclinada, el isotipo y el chip "Ya somos 6" de 01c. */
@Composable
internal fun ingresoComposicionFogon(e: IngresoEscala) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.offset(e.x(36f), e.y(110f)).size(e.x(300f), e.y(360f)).rotate(-4f)
                .gel(34.dp, null, listOf(externa(30f, 60f, rgba(20, 10, 80, .7f), spread = -20f)))
                .clip(RoundedCornerShape(34.dp))
                .border(6.dp, C.blancoCalido, RoundedCornerShape(34.dp)),
        ) {
            Image(painterResource(R.drawable.ingreso_fogon), null, Modifier.fillMaxSize().padding(0.dp), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().border(6.dp, C.blancoCalido, RoundedCornerShape(34.dp)))
        }
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = -e.x(22f), y = e.y(400f)).size(130.dp).rotate(12f),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(104.dp).gel(30.dp, null, listOf(externa(22f, 22f, rgba(20, 10, 80, .5f)))))
            Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(130.dp))
        }
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = -e.x(30f), y = e.y(128f)).rotate(6f)
                .gel(14.dp, Gels.limaChico).padding(horizontal = 12.dp, vertical = 8.dp),
        ) { T("Ya somos 6", 15f, 700, C.carbon, alto = 1f) }
    }
}
