package ar.com.caigo.app.pantallas.planes

import ar.com.caigo.app.piezas.colorDeCategoria
import android.os.Bundle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.altoTabBar
import ar.com.caigo.app.nucleo.Categorias
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.latMDP
import ar.com.caigo.app.nucleo.lngMDP
import ar.com.caigo.app.piezas.BadgeFalta
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt

private val sombraFlotante = listOf(externa(8f, 24f, rgba(36, 33, 45, .1f)))

/** Los filtros del mapa (03). */
private val filtrosMapa = listOf(
    null to "Próximas 3 h", "deporte" to "Deporte", "previa" to "Previa", "juego" to "Juego",
    "salida" to "Salida", "cita_doble" to "Cita doble", "taller" to "Taller",
)

/**
 * El zoom de MapLibre que muestra [metros] a lo ancho de [anchoDp] (la región de N metros de MapKit,
 * que entra por el lado más corto: el ancho).
 */
internal fun zoomPara(lat: Double, metros: Double, anchoDp: Float): Double =
    ln(78271.517 * cos(Math.toRadians(lat)) * anchoDp / metros) / ln(2.0)

/**
 * 03 · Ahora mapa (MapLibre en vez de MapKit): pines con lo que falta, buscador, filtros y la tarjeta
 * flotante del plan elegido.
 */
@Composable
internal fun MapaAhora(
    ir: Ir,
    planes: List<Plan>,
    punto: Pair<Double, Double>?,
    categoria: String?,
    onCategoria: (String?) -> Unit,
    busqueda: String,
    onBusqueda: (String) -> Unit,
    onLista: () -> Unit,
) {
    val ctx = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val densidad = LocalDensity.current
    val ancho = LocalConfiguration.current.screenWidthDp.toFloat()
    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pal = LocalPaleta.current
    val oscuro = pal.oscuro
    // Modo oscuro (14a): mapa oscuro y buscador, interruptor y chips con los gels oscuros.
    val gelFlotante: Brush? = if (oscuro) Gels.tarjetaOscura.fondo else vertical(C.blanco, C.blanco)
    var elegido by remember { mutableStateOf<String?>(null) }
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    var ubicado by remember { mutableStateOf(false) }
    val posiciones = remember { mutableStateMapOf<String, Offset>() }
    var yoEnPantalla by remember { mutableStateOf<Offset?>(null) }
    val vis = planes
    val seleccionado = vis.firstOrNull { it.id == elegido } ?: vis.firstOrNull()

    fun reproyectar() {
        val m = mapa ?: return
        posiciones.clear()
        vis.forEach { p ->
            val s = m.projection.toScreenLocation(LatLng(p.lat, p.lng))
            posiciones[p.id] = Offset(s.x, s.y)
        }
        yoEnPantalla = punto?.let { (la, ln) -> m.projection.toScreenLocation(LatLng(la, ln)).let { Offset(it.x, it.y) } }
    }

    val vista = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply {
            onCreate(Bundle())
            getMapAsync { m ->
                m.uiSettings.isAttributionEnabled = true
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isCompassEnabled = false
                m.uiSettings.isRotateGesturesEnabled = false
                m.setStyle(if (oscuro) BuildConfig.MAPA_ESTILO_OSCURO else BuildConfig.MAPA_ESTILO) {
                    val (la, ln) = punto ?: (latMDP to lngMDP)
                    m.cameraPosition = CameraPosition.Builder().target(LatLng(la, ln)).zoom(zoomPara(la, 3500.0, ancho)).build()
                    if (punto != null) ubicado = true
                    mapa = m
                }
                m.addOnCameraMoveListener { reproyectar() }
                m.addOnCameraIdleListener { reproyectar() }
            }
        }
    }
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> vista.onStart()
                Lifecycle.Event.ON_RESUME -> vista.onResume()
                Lifecycle.Event.ON_PAUSE -> vista.onPause()
                Lifecycle.Event.ON_STOP -> vista.onStop()
                else -> Unit
            }
        }
        ciclo.lifecycle.addObserver(obs)
        vista.onStart(); vista.onResume()
        onDispose {
            ciclo.lifecycle.removeObserver(obs)
            vista.onPause(); vista.onStop(); vista.onDestroy()
        }
    }
    // Si la ubicación llega después de abrir el mapa, se centra una vez.
    LaunchedEffect(mapa, punto?.first) {
        val m = mapa ?: return@LaunchedEffect
        val (la, ln) = punto ?: return@LaunchedEffect
        if (!ubicado) { m.cameraPosition = CameraPosition.Builder().target(LatLng(la, ln)).zoom(zoomPara(la, 3500.0, ancho)).build(); ubicado = true }
    }
    LaunchedEffect(mapa, vis.map { it.id }, punto) { reproyectar() }

    Box(Modifier.fillMaxSize().background(if (oscuro) C.grafito else Color(0xFFF2EFE9))) {
        AndroidView({ vista }, Modifier.fillMaxSize())

        // Mi ubicación: punto violeta con halo.
        yoEnPantalla?.let { o ->
            Box(
                Modifier.offset { IntOffset((o.x - with(densidad) { 28.dp.toPx() }).roundToInt(), (o.y - with(densidad) { 28.dp.toPx() }).roundToInt()) }
                    .size(56.dp).background(rgba(116, 86, 241, .18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(18.dp).gel(9.dp, vertical(C.violeta, C.violeta), listOf(externa(2f, 6f, rgba(36, 33, 45, .3f)))).border(3.dp, C.blanco, CircleShape))
            }
        }

        // Pines.
        vis.forEach { p ->
            val o = posiciones[p.id] ?: return@forEach
            val sel = p.id == seleccionado?.id
            PinMapa(
                Cupo.badge(p.faltan), seleccionado = sel, completo = Cupo.completo(p.faltan),
                modifier = Modifier.zIndex(if (sel) 2f else 1f).offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }.tocable { elegido = p.id },
            )
        }

        // Buscador, Lista/Mapa y filtros (por encima de los pines).
        Column(Modifier.zIndex(5f).padding(top = arriba + 8.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).height(50.dp).gel(18.dp, gelFlotante, sombraFlotante).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icono(I.lupaChica, 18.dp, pal.texto, 2.2f)
                    PlanesCampo(busqueda, onBusqueda, "Buscá un plan o un lugar", Modifier.weight(1f), colorPista = pal.texto2, color = pal.texto)
                    if (busqueda.isNotEmpty()) {
                        Box(Modifier.semantics { contentDescription = "Borrar la búsqueda" }.tocable { onBusqueda("") }) {
                            Icono(I.cruz, 14.dp, pal.texto2, 2.6f)
                        }
                    }
                }
                Row(Modifier.gel(16.dp, gelFlotante, sombraFlotante).padding(4.dp)) {
                    Box(Modifier.height(42.dp).tocable(onLista).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { T("Lista", 14f, 600, pal.texto2) }
                    Box(Modifier.height(42.dp).gel(12.dp, if (oscuro) Gels.limaChico else Gels.oscuroSel).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        T("Mapa", 14f, 700, if (oscuro) C.carbon else C.blancoCalido)
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp, bottom = 8.dp)) {
                items(filtrosMapa, key = { it.second }) { (k, l) ->
                    val on = k == categoria
                    Box(
                        Modifier.toque { onCategoria(k) }.gel(
                            13.dp,
                            when {
                                on && oscuro -> Gels.limaChico
                                on -> planesVioletaChico
                                oscuro -> Gels.chipOscuro
                                else -> Gel(vertical(C.blanco, C.blanco), emptyList())
                            },
                        ).padding(horizontal = 13.dp, vertical = 8.dp),
                    ) { T(l, 13f, if (on) 700 else 600, if (on) (if (oscuro) C.carbon else C.blanco) else pal.texto, unRenglon = true) }
                }
            }
        }

        // Volver a mi ubicación y la tarjeta flotante del plan elegido.
        Column(
            Modifier.zIndex(5f).align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = altoTabBar()),
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.padding(end = 4.dp).size(46.dp).semantics { contentDescription = "Volver a mi ubicación" }
                    .toque { punto?.let { (la, ln) -> mapa?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(la, ln), zoomPara(la, 2200.0, ancho))) } }
                    .gel(16.dp, gelFlotante, listOf(externa(8f, 20f, rgba(36, 33, 45, .12f)))),
                contentAlignment = Alignment.Center,
            ) { Icono(I.ubicacion, 20.dp, if (oscuro) C.lima else C.violeta, 2.2f) }
            if (seleccionado != null) TarjetaFlotante(seleccionado, ir)
        }
    }
}

/** Pin con el contador: coral (falta), lima (completo) o violeta (elegido), borde blanco y pico. */
@Composable
private fun PinMapa(texto: String, seleccionado: Boolean, completo: Boolean, modifier: Modifier) {
    val fondo: Brush = when {
        seleccionado -> vertical(0f to Color(0xFF9A84FF), .55f to C.violeta, 1f to Color(0xFF6243E6))
        completo -> vertical(0f to C.cupoLleno1, .5f to C.lima, 1f to C.cupoLleno2)
        else -> vertical(0f to Color(0xFFFFA597), .5f to C.coral, 1f to Color(0xFFF2604F))
    }
    val escala by animateFloatAsState(if (seleccionado) 1.06f else 1f, tween(200), label = "pin")
    // El ancla es abajo al centro (anchor: .bottom de MapKit).
    Column(modifier.anclaAbajo().scale(escala), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.gel(13.dp, null, listOf(externa(6f, 14f, rgba(36, 33, 45, .18f))))
                .clip(RoundedCornerShape(13.dp)).background(C.blanco).padding(2.5.dp)
                .background(fondo, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
        ) { T(texto, 15f, 700, if (seleccionado) C.blanco else C.carbon, alto = 1f, unRenglon = true) }
        // El pico: un cuadrado de 7 con borde blanco abajo y a la derecha, girado 45°, subido 6 y en 4 de alto.
        Box(Modifier.height(4.dp).wrapContentHeight(Alignment.Top, unbounded = true)) {
            Box(
                Modifier.offset(y = (-6).dp).rotate(45f).size(9.5.dp).background(C.blanco)
                    .padding(end = 2.5.dp, bottom = 2.5.dp).background(fondo),
            )
        }
    }
}

/** Corre el pin para que el borde de abajo, al centro, quede en el punto del mapa. */
private fun Modifier.anclaAbajo(): Modifier = this.then(
    Modifier.layout { m, c ->
        val p = m.measure(c)
        layout(p.width, p.height) { p.place(-p.width / 2, -p.height) }
    },
)

/** Tarjeta flotante del plan elegido (foto 96×120, radio 26). */
@Composable
private fun TarjetaFlotante(p: Plan, ir: Ir) {
    val pal = LocalPaleta.current
    Row(
        Modifier.fillMaxWidth().toque(alBajar = 0.dp) { ir.plan(p.id) }
            .gel(26.dp, if (pal.oscuro) Gels.tarjetaOscura.fondo else vertical(C.blanco, C.blanco), listOf(externa(16f, 40f, rgba(36, 33, 45, .18f))))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Foto(fotoDe(p), Modifier.size(96.dp, 120.dp).clip(RoundedCornerShape(18.dp)), colorDeCategoria(p.categoria))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.background(C.lavanda, RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { T(Categorias.nombre(p.categoria), 11f, 700, C.carbon, unRenglon = true) }
                Box(Modifier.background(C.carbon, RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { T(Fecha.cuando(p.empiezaEn), 11f, 700, C.blancoCalido, unRenglon = true) }
            }
            T(p.titulo, 18f, 700, pal.texto, alto = 1.15f, maxRenglones = 2)
            T(lugarYDistancia(p), 12f, 400, pal.texto2, unRenglon = true)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BadgeFalta(p.faltan, tam = 16f) }
                if (p.miEstado == "ninguno" && !Cupo.completo(p.faltan)) {
                    Box(
                        Modifier.size(40.dp).semantics { contentDescription = "Sumarme" }.toque { ir.plan(p.id) }.gel(14.dp, Gels.limaChico),
                        contentAlignment = Alignment.Center,
                    ) { Icono(I.mas, 18.dp, C.carbon, 4f) }
                }
            }
        }
    }
}
