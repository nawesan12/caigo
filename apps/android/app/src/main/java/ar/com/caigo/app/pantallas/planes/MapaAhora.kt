package ar.com.caigo.app.pantallas.planes

import ar.com.caigo.app.tema.LocalPaleta
import android.os.Bundle
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.app.LAT_MDP
import ar.com.caigo.app.app.LNG_MDP
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.piezas.BadgeFalta
import ar.com.caigo.app.piezas.Fecha
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.nombreCategoria
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.estilo
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
import kotlin.math.roundToInt

private val sombraFlotante = listOf(externa(8f, 24f, rgba(36, 33, 45, .1f)))
private val blanco = vertical(C.blanco, C.blanco)

/** 03 · Ahora mapa: pines con lo que falta, buscador, filtros y la tarjeta flotante del plan elegido. */
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
    val arriba = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pal = LocalPaleta.current
    val oscuro = pal.oscuro
    // Mapa oscuro en modo oscuro (14a) y los controles flotantes con las piezas oscuras.
    val fondoFlotante = if (oscuro) vertical(C.oscuro4, C.oscuro4) else blanco
    val textoFlotante = if (oscuro) C.blancoCalido else C.carbon
    var elegido by remember { mutableStateOf<String?>(null) }
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    val posiciones = remember { mutableStateMapOf<String, Offset>() }
    var yoEnPantalla by remember { mutableStateOf<Offset?>(null) }
    // La búsqueda la hace la API (`q`: título, zona o lugar).
    val visibles = planes
    if (elegido == null || visibles.none { it.id == elegido }) elegido = visibles.firstOrNull()?.id

    fun reproyectar() {
        val m = mapa ?: return
        posiciones.clear()
        visibles.forEach { p ->
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
                    m.cameraPosition = CameraPosition.Builder().target(LatLng(punto?.first ?: LAT_MDP, punto?.second ?: LNG_MDP)).zoom(13.5).build()
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
    LaunchedEffect(mapa, visibles.map { it.id }, punto) { reproyectar() }

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
        visibles.forEach { p ->
            val o = posiciones[p.id] ?: return@forEach
            Pin(Cupo.badge(p.faltan), seleccionado = p.id == elegido, completo = Cupo.completo(p.faltan),
                modifier = Modifier.zIndex(if (p.id == elegido) 2f else 1f).offset {
                    IntOffset(o.x.roundToInt(), o.y.roundToInt())
                }.tocable { elegido = p.id })
        }

        // Buscador, Lista/Mapa y filtros.
        // Buscador y filtros por encima de los pines.
        Column(Modifier.zIndex(5f).padding(top = arriba + 8.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).height(50.dp).gel(18.dp, fondoFlotante, sombraFlotante).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icono(I.lupaChica, 18.dp, if (oscuro) C.lima else C.carbon, 2.2f)
                    Box(Modifier.weight(1f)) {
                        if (busqueda.isEmpty()) T("Buscá un plan o un lugar", 15f, 400, if (oscuro) C.apagado2 else C.texto2, unRenglon = true)
                        BasicTextField(busqueda, { onBusqueda(it.take(60)) }, singleLine = true, textStyle = estilo(15f, 500, textoFlotante), cursorBrush = SolidColor(C.violeta), modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(Modifier.gel(16.dp, fondoFlotante, sombraFlotante).padding(4.dp)) {
                    Box(Modifier.height(42.dp).tocable(onLista).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { T("Lista", 14f, 600, if (oscuro) C.apagado2 else C.texto2) }
                    Box(Modifier.height(42.dp).gel(12.dp, Gels.oscuroSel).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { T("Mapa", 14f, 700, C.blancoCalido) }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                val filtros = listOf(null to "Próximas 3 h", "deporte" to "Deporte", "previa" to "Previa", "juego" to "Juego", "salida" to "Salida", "cita_doble" to "Cita doble", "taller" to "Taller")
                items(filtros) { (k, l) ->
                    val on = k == categoria
                    Box(
                        Modifier.toque { onCategoria(k) }.gel(
                            13.dp,
                            when {
                                on && oscuro -> Gels.limaChico
                                on -> Gels.violeta.copy(sombras = Gels.violeta.sombras.map { if (!it.inset && it.blur == 0f) it.copy(y = 4f) else if (!it.inset) it.copy(y = 16f, blur = 26f) else it })
                                oscuro -> Gels.chipOscuro
                                else -> Gel(blanco, emptyList())
                            },
                        ).padding(horizontal = 13.dp, vertical = 8.dp),
                    ) { T(l, 13f, if (on) 700 else 600, if (on) (if (oscuro) C.carbon else C.blanco) else textoFlotante, unRenglon = true) }
                }
            }
        }

        // Volver a mi ubicación.
        val seleccionado = visibles.firstOrNull { it.id == elegido }
        Box(
            Modifier.zIndex(5f).align(Alignment.BottomEnd).padding(end = 16.dp, bottom = abajo + if (seleccionado != null) 262.dp else 130.dp)
                .size(46.dp).toque {
                    punto?.let { (la, ln) -> mapa?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(la, ln), 14.0)) }
                }.gel(16.dp, fondoFlotante, listOf(externa(8f, 20f, rgba(36, 33, 45, .12f)))),
            contentAlignment = Alignment.Center,
        ) { Icono(I.ubicacion, 20.dp, if (oscuro) C.lima else C.violeta, 2.2f) }

        // Tarjeta flotante del plan elegido.
        if (seleccionado != null) TarjetaFlotante(seleccionado, ir, Modifier.zIndex(5f).align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = abajo + 118.dp))
    }
}

/** Pin con el contador: coral (falta), lima (completo) o violeta (elegido), borde blanco y pico. */
@Composable
private fun Pin(texto: String, seleccionado: Boolean, completo: Boolean, modifier: Modifier) {
    val fondo: Brush = when {
        seleccionado -> vertical(0f to Color(0xFF9A84FF), .55f to C.violeta, 1f to Color(0xFF6243E6))
        completo -> vertical(0f to C.cupoLleno1, .5f to C.lima, 1f to C.cupoLleno2)
        else -> vertical(0f to Color(0xFFFFA597), .5f to C.coral, 1f to Color(0xFFF2604F))
    }
    val color = if (seleccionado) C.blanco else C.carbon
    // El offset ubica la punta del pico sobre el punto: se corre medio ancho y todo el alto.
    Column(
        modifier.layoutCentrado(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.gel(13.dp, null, listOf(externa(6f, 14f, rgba(36, 33, 45, .18f))))
                .clip(RoundedCornerShape(13.dp)).background(C.blanco).padding(2.5.dp)
                .background(fondo, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
        ) { T(texto, 15f, 700, color, alto = 1f, unRenglon = true) }
        Box(Modifier.offset(y = (-6).dp).size(10.dp).rotate(45f).background(C.blanco).padding(end = 2.5.dp, bottom = 2.5.dp).background(fondo))
    }
}

/** Corre el pin para que la punta quede en el punto del mapa. */
private fun Modifier.layoutCentrado(): Modifier = this.then(
    Modifier.layout { m, c ->
        val p = m.measure(c)
        layout(p.width, p.height) { p.place(-p.width / 2, -p.height + 6) }
    },
)

@Composable
private fun TarjetaFlotante(p: Plan, ir: Ir, modifier: Modifier) {
    val pal = LocalPaleta.current
    Row(
        modifier.fillMaxWidth().toque(alBajar = 0.dp) { ir.plan(p.id) }
            .gel(26.dp, if (pal.oscuro) Gels.tarjetaOscura else Gel(blanco, listOf(externa(16f, 40f, rgba(36, 33, 45, .18f))))).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Foto(p.foto.ifBlank { p.lugar?.foto }, Modifier.size(96.dp, 120.dp).clip(RoundedCornerShape(18.dp)), colorDeCategoria(p.categoria))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.background(C.lavanda, RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { T(nombreCategoria(p.categoria), 11f, 700, C.carbon, unRenglon = true) }
                Box(Modifier.background(C.carbon, RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { T(Fecha.cuando(p.empiezaEn), 11f, 700, C.blancoCalido, unRenglon = true) }
            }
            T(p.titulo, 18f, 700, pal.texto, alto = 1.15f, maxRenglones = 2)
            T(lugarYDistancia(p), 12f, 400, pal.texto2, unRenglon = true)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BadgeFalta(p.faltan, tam = 16f) }
                if (p.miEstado == "ninguno" && !Cupo.completo(p.faltan)) {
                    Box(Modifier.size(40.dp).toque { ir.plan(p.id) }.gel(14.dp, Gels.limaChico), contentAlignment = Alignment.Center) {
                        Icono(I.mas, 18.dp, C.carbon, 4f)
                    }
                }
            }
        }
    }
}
