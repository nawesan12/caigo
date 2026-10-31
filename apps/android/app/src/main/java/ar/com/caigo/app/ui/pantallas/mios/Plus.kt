package ar.com.caigo.app.ui.pantallas.mios

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.datos.PlanPlus
import ar.com.caigo.app.datos.mensajeDe
import ar.com.caigo.app.logica.Plata
import ar.com.caigo.app.pagos.Facturacion
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Fecha
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.rememberCarga
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.EspaciadoTitulo
import ar.com.caigo.app.ui.tema.Gel
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
import com.android.billingclient.api.ProductDetails
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.roundToLong

/** Fondo del paywall: violeta profundo arriba sobre grafito. */
private val fondoPlus = radial(.5f, .12f, .9f, .45f, 0f to Color(0xFF3B2F78), .7f to C.grafito)

private val textoSuave = Color(0xFFD3CEE2)
private val textoTenue = Color(0xFFC9C4DA)

/** "20:03:24" hasta [hasta] (o medianoche de Argentina si no vino). */
private fun reloj(hasta: ZonedDateTime?, ahora: ZonedDateTime): String {
    val fin = hasta ?: ahora.toLocalDate().plusDays(1).atStartOfDay(ahora.zone)
    val t = Duration.between(ahora, fin).seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(t / 3600, (t / 60) % 60, t % 60)
}

/**
 * Caigo Plus (paywall del prototipo). [motivo]: `limit` (se quedó sin sumadas), `likes` (quiere
 * ver quién lo sumó) o `perfil`. Se paga con Google Play.
 */
@Composable
fun Plus(ir: Ir, motivo: String) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    LugaresBarraEstado(clara = true)

    val plus = rememberCarga { c.api.plus() }
    val recibidos = rememberCarga { runCatching { c.api.recibidos("gente") }.getOrNull() }
    val play = remember { Facturacion(ctx.applicationContext) }
    DisposableEffect(Unit) { onDispose { play.cerrar() } }
    var productos by remember { mutableStateOf<Map<String, ProductDetails>>(emptyMap()) }
    var errorPlay by remember { mutableStateOf<String?>(null) }
    var elegido by remember { mutableStateOf("mes") }
    var comprando by remember { mutableStateOf(false) }
    var listo by remember { mutableStateOf(false) }
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(plus.dato) { if (plus.dato?.estado?.activo == true) listo = true }
    LaunchedEffect(Unit) {
        try {
            productos = play.productos(Facturacion.PRODUCTOS).associateBy { it.productId }
            if (productos.isEmpty()) errorPlay = "Caigo Plus todavía no está disponible en Google Play."
        } catch (e: Throwable) {
            errorPlay = e.message
        }
    }
    // Contador en vivo hasta que se renuevan las sumadas.
    LaunchedEffect(Unit) { while (true) { delay(1000); ahora = System.currentTimeMillis() } }

    /** Manda la compra a la API para que la verifique y active Plus. */
    suspend fun registrar(token: String) {
        try {
            val e = c.api.plusGoogle(token)
            if (e.activo) listo = true else avisos.error("Google Play todavía no confirmó la compra. Probá de nuevo en un rato.")
        } catch (e: Throwable) {
            avisos.error(mensajeDe(e))
        }
    }

    LaunchedEffect(Unit) {
        play.resultados.collect { r ->
            when (r) {
                is Facturacion.Resultado.Compras -> {
                    val compra = r.compras.firstOrNull { it.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED }
                    if (compra != null) registrar(compra.purchaseToken)
                    else if (r.compras.any { it.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PENDING }) {
                        avisos.ok("Tu pago quedó pendiente en Google Play. Cuando se acredite, se activa Plus.")
                    }
                    comprando = false
                }
                Facturacion.Resultado.Cancelada -> comprando = false
                is Facturacion.Resultado.Fallo -> { comprando = false; avisos.error(r.mensaje) }
            }
        }
    }

    val planes: List<PlanPlus> = plus.dato?.planes.orEmpty()
    val porClave = planes.associateBy { it.plan }
    val mes = porClave["mes"]
    val prodMes = mes?.let { productos[it.producto] }
    val diasGratis = Facturacion.diasGratis(prodMes?.let { Facturacion.oferta(it) })

    fun comprar() {
        val p = porClave[elegido] ?: return
        val prod = productos[p.producto]
        val oferta = prod?.let { Facturacion.oferta(it) }
        val act = ctx.lugaresActividad()
        if (prod == null || oferta == null || act == null) {
            avisos.error(errorPlay ?: "Caigo Plus todavía no está disponible en Google Play.")
            return
        }
        comprando = true
        play.comprar(act, prod, oferta.offerToken, c.sesion.usuarioId)
    }

    fun restaurar() = alcance.launch {
        try {
            val compras = play.compras()
            val token = compras.firstOrNull { cp -> cp.products.any { it in Facturacion.PRODUCTOS } }?.purchaseToken
            if (token == null) avisos.error("No encontramos compras de Caigo Plus en esta cuenta de Google.") else registrar(token)
        } catch (e: Throwable) {
            avisos.error(mensajeDe(e))
        }
    }

    val fotosTeaser = recibidos.dato?.personas.orEmpty().map { it.foto }.filter { it.isNotBlank() }.take(4)

    Pantalla(oscuro = true, brillo = fondoPlus) { arriba, abajo ->
        if (listo) {
            PlusListo(fotosTeaser, Modifier.padding(top = arriba, bottom = abajo)) {
                c.alcance.launch { c.recargarYo() }
                ir.atras()
            }
            return@Pantalla
        }
        val n = recibidos.dato?.personas?.size ?: 0
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = arriba + 6.dp, start = 20.dp, end = 20.dp, bottom = 190.dp + abajo),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth().zIndex(2f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).toque { ir.atras() }
                        .gel(16.dp, Gel(vertical(rgba(255, 255, 255, .1f), rgba(255, 255, 255, .1f)), listOf(interna(1f, 0f, rgba(255, 255, 255, .15f))))),
                    contentAlignment = Alignment.Center,
                ) { Icono(I.cruz, 20.dp, C.blanco, 2.6f) }
                Box(Modifier.weight(1f))
                T("Restaurar compra", 13f, 700, textoTenue, Modifier.tocable { restaurar() })
            }
            // "Caigo" grande con la pill PLUS
            Box(Modifier.fillMaxWidth().height(190.dp).offset(y = (-40).dp).padding(top = 0.dp)) {
                T(
                    "Caigo", 80f, 700, C.blancoCalido, Modifier.fillMaxWidth().padding(top = 56.dp),
                    alto = 1f, espaciado = EspaciadoTitulo, alinear = TextAlign.Center,
                )
                Box(Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 26.dp)) { LugaresPop(200) { LugaresPillPlus() } }
            }
            Column(
                Modifier.fillMaxWidth().offset(y = (-40).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val titulo = when (motivo) {
                    "limit" -> "Te quedaste sin sumadas por hoy"
                    "likes" -> "Mirá quién te quiere conocer"
                    else -> "Más planes. Más gente. Sin límites."
                }
                T(titulo, 32f, 800, C.blanco, alto = 1.02f, espaciado = px(-1f, 32f), alinear = TextAlign.Center)
                if (motivo == "limit") {
                    val hasta = plus.dato?.estado?.reiniciaEn?.let { Fecha.parse(it) }
                    val t = reloj(hasta, java.time.Instant.ofEpochMilli(ahora).atZone(ar.com.caigo.app.ui.piezas.ZonaAR))
                    Row(
                        Modifier.background(rgba(255, 255, 255, .08f), RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        T("Se renuevan en", 13f, 700, Color(0xFFE4E0EE))
                        Box(Modifier.background(C.coral, RoundedCornerShape(9.dp)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                            T(t, 15f, 800, C.carbon, alto = 1f, espaciado = px(.5f, 15f))
                        }
                    }
                } else {
                    val sub = if (motivo == "likes") "Con Plus ves a todos los que te sumaron o te invitaron a sus planes, y los sumás de vuelta."
                    else "Sumá a quien quieras, mirá quién te sumó y destacá tus planes."
                    T(sub, 15f, 400, textoSuave, alto = 1.5f, alinear = TextAlign.Center)
                }
            }
            Column(Modifier.offset(y = (-40).dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (n > 0) TeaserRecibidos(fotosTeaser, n)
                TablaPlus()
                T("Elegí tu plan", 20f, 700, C.blanco, Modifier.padding(top = 4.dp))
                if (plus.cargando && planes.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Puntitos(C.lima) }
                }
                val semanaC = porClave["semana"]?.centavos
                val mesC = mes?.centavos
                planes.forEach { p ->
                    val (desc, por, etiqueta, ahorro) = when (p.plan) {
                        "semana" -> listOf("Para probar un finde largo", "por semana", "", "")
                        "mes" -> listOf(
                            if (diasGratis > 0) "$diasGratis días gratis para arrancar" else "",
                            "${Plata.pesos(redondearPeso(p.centavos / 4.0))} por semana", "Más elegido", "",
                        )
                        "temporada" -> listOf(
                            "Todo el verano, 3 meses", "${Plata.pesos(redondearPeso(p.centavos / 13.0))} por semana", "",
                            if (mesC != null && mesC > 0) "−${((1 - p.centavos.toDouble() / (mesC * 3)) * 100).roundToLong()}%" else "",
                        )
                        else -> listOf("", "", "", "")
                    }
                    OpcionPlan(p.nombre.ifBlank { p.plan }, Plata.pesos(p.centavos), desc, if (semanaC == null && p.plan == "semana") "" else por, etiqueta, ahorro, elegido == p.plan) { elegido = p.plan }
                }
                if (motivo == "limit") {
                    T("Prefiero esperar a mañana", 14f, 700, textoTenue, Modifier.align(Alignment.CenterHorizontally).tocable { ir.atras() }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        // CTA fija abajo con el degradé
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(0f to C.grafito.copy(alpha = 0f), .3f to C.grafito))
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 30.dp + abajo),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val p = porClave[elegido]
            val cta = when {
                p == null -> "Elegí tu plan"
                p.plan == "mes" && diasGratis > 0 -> "Probar $diasGratis días gratis"
                else -> "Pagar ${Plata.pesos(p.centavos)}"
            }
            Box(
                Modifier.fillMaxWidth().height(64.dp).toque(!comprando && p != null, 4.dp) { comprar() }.gel(24.dp, lugaresGelLimaNfc),
                contentAlignment = Alignment.Center,
            ) {
                if (comprando) Puntitos(C.carbon) else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(34.dp).offset(x = (-6).dp))
                    T(cta, 18f, 800, C.carbon, unRenglon = true)
                }
            }
            if (p != null) {
                val fino = when {
                    p.plan == "mes" && diasGratis > 0 -> "Gratis $diasGratis días, después ${Plata.pesos(p.centavos)} por mes."
                    p.plan == "temporada" -> "Pagás ${Plata.pesos(p.centavos)} hoy por 3 meses."
                    else -> "Pagás ${Plata.pesos(p.centavos)} hoy."
                }
                T("$fino Cancelás cuando quieras · Google Play", 12f, 600, textoTenue, Modifier.fillMaxWidth(), alto = 1.45f, alinear = TextAlign.Center)
            }
        }
    }
}

private fun redondearPeso(centavos: Double): Long = (centavos / 100).roundToLong() * 100

/** "4 personas te sumaron · Con Plus ves quiénes son", con las caras borrosas. */
@Composable
private fun TeaserRecibidos(fotos: List<String>, n: Int) {
    Row(
        Modifier.fillMaxWidth().gel(
            24.dp,
            Gel(vertical(rgba(116, 86, 241, .35f), rgba(116, 86, 241, .15f)), listOf(interna(1f, 0f, rgba(255, 255, 255, .15f)), interna(0f, 0f, rgba(163, 143, 255, .35f), spread = -1f))),
        ).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(start = 10.dp)) {
            fotos.forEachIndexed { i, f ->
                Box(
                    Modifier.offset(x = (-12 * i).dp).size(44.dp).clip(CircleShape).background(C.oscuro2)
                        .border(2.5.dp, Color(0xFF2E2850), CircleShape),
                ) {
                    AsyncImage(f, null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }.blur(6.dp), contentScale = ContentScale.Crop)
                }
            }
        }
        Column(Modifier.weight(1f).offset(x = (-12 * (fotos.size - 1).coerceAtLeast(0)).dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(if (n == 1) "1 persona te sumó" else "$n personas te sumaron", 15f, 800, C.blanco)
            T(if (n == 1) "Con Plus ves quién es" else "Con Plus ves quiénes son", 12f, 600, textoSuave)
        }
        Box(Modifier.size(34.dp).background(rgba(255, 255, 255, .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icono(I.candado, 16.dp, Color.Transparent, 0f, relleno = C.blanco)
        }
    }
}

/** Comparación Gratis / Plus (la tabla del prototipo). */
@Composable
private fun TablaPlus() {
    val filas = listOf(
        Triple("Sumadas en Gente", "3 por día", "Sin límite"),
        Triple("Ver quién te sumó", "—", "✓"),
        Triple("Me encanta en Citas", "1 por día", "5 por día"),
        Triple("Tu plan destacado", "—", "1 por semana"),
        Triple("Filtros finos", "Básicos", "Todos"),
    )
    Box(
        Modifier.fillMaxWidth().gel(24.dp, Gel(vertical(rgba(255, 255, 255, .05f), rgba(255, 255, 255, .05f)), listOf(interna(0f, 0f, rgba(255, 255, 255, .08f), spread = -1f)))),
    ) {
        // Columna Plus resaltada (29 % a la derecha)
        Row(Modifier.matchParentSize()) {
            Box(Modifier.weight(.71f))
            Box(
                Modifier.weight(.29f).fillMaxSize().clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                    .background(vertical(rgba(116, 86, 241, .45f), rgba(116, 86, 241, .2f)))
                    .background(Brush.horizontalGradient(0f to rgba(163, 143, 255, .4f), .01f to Color.Transparent)),
            )
        }
        Column {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1.5f))
                T("GRATIS", 11f, 800, C.apagado, Modifier.weight(1f).padding(top = 12.dp, bottom = 4.dp), espaciado = px(.8f, 11f), alinear = TextAlign.Center)
                T("PLUS", 11f, 800, C.lima, Modifier.weight(1f).padding(top = 12.dp, bottom = 4.dp), espaciado = px(.8f, 11f), alinear = TextAlign.Center)
            }
            filas.forEachIndexed { i, (l, g, p) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(rgba(255, 255, 255, .08f)))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(l, 13f, 700, C.blancoCalido, Modifier.weight(1.5f).padding(start = 14.dp, top = 12.dp, bottom = 12.dp))
                    T(g, 12f, 600, C.apagado, Modifier.weight(1f).padding(horizontal = 4.dp), alinear = TextAlign.Center)
                    Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                        if (p == "✓") Icono(I.check, 16.dp, C.lima, 3.4f) else T(p, 12f, 800, C.lima, alinear = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun OpcionPlan(nombre: String, precio: String, desc: String, por: String, etiqueta: String, ahorro: String, on: Boolean, onClick: () -> Unit) {
    val gel = if (on) Gel(
        vertical(C.blanco, C.blanco),
        listOf(interna(0f, 0f, C.lima, spread = -3f), externa(4f, 0f, C.limaBase), externa(18f, 30f, rgba(200, 243, 107, .35f), spread = -12f)),
    ) else Gel(vertical(rgba(255, 255, 255, .06f), rgba(255, 255, 255, .06f)), listOf(interna(0f, 0f, rgba(255, 255, 255, .14f), spread = -1.5f)))
    val fg = if (on) C.carbon else C.blanco
    val sub = if (on) C.texto2 else textoTenue
    Box(Modifier.fillMaxWidth().padding(top = if (etiqueta.isNotBlank()) 10.dp else 0.dp)) {
        Row(
            Modifier.fillMaxWidth().toque(alBajar = 2.dp, onClick = onClick).gel(22.dp, gel).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(24.dp).background(C.blanco, CircleShape)
                    .border(if (on) 7.dp else 2.dp, if (on) C.violeta else rgba(255, 255, 255, .35f), CircleShape),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(nombre, 16f, 800, fg)
                    if (ahorro.isNotBlank()) Box(Modifier.background(C.lima, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                        T(ahorro, 11f, 800, C.carbon)
                    }
                }
                if (desc.isNotBlank()) T(desc, 12f, 600, sub)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(precio, 22f, 800, fg, alto = 1f)
                if (por.isNotBlank()) T(por, 11f, 700, sub)
            }
        }
        if (etiqueta.isNotBlank()) {
            Box(
                Modifier.offset(x = 18.dp, y = (-10).dp)
                    .gel(9.dp, Gel(vertical(0f to C.coral0, .6f to C.coral, 1f to C.coral2), listOf(externa(2f, 0f, Color(0xFFC9432F)))))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { T(etiqueta, 11f, 800, C.carbon) }
        }
    }
}

/** "Ya sos Plus" (paywall_done). */
@Composable
private fun PlusListo(fotos: List<String>, modifier: Modifier, seguir: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Box {
            LugaresPop {
                Box(Modifier.size(150.dp).gel(46.dp, null, listOf(externa(26f, 30f, rgba(116, 86, 241, .6f)))))
                Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(190.dp))
            }
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 14.dp, y = (-20).dp)) { LugaresPop(250) { LugaresPillPlus(tam = 18f) } }
        }
        T("Ya sos Plus", 42f, 800, C.blanco, alto = 1f, espaciado = px(-1.4f, 42f), alinear = TextAlign.Center)
        T(
            "Sumá a toda la gente que quieras. Ya podés ver quién te sumó y tu próximo plan sale destacado.",
            16f, 400, textoSuave, alto = 1.5f, alinear = TextAlign.Center,
        )
        if (fotos.isNotEmpty()) Row(Modifier.padding(top = 12.dp)) {
            fotos.forEachIndexed { i, f ->
                Box(Modifier.offset(x = (-12 * i).dp).size(56.dp).clip(CircleShape).background(C.oscuro2).border(3.dp, C.grafito, CircleShape)) {
                    AsyncImage(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 10.dp).height(62.dp).toque(alBajar = 4.dp, onClick = seguir).gel(22.dp, lugaresGelLimaNfc),
            contentAlignment = Alignment.Center,
        ) { T("Seguir", 18f, 800, C.carbon) }
        Box(Modifier.width(1.dp))
    }
}
