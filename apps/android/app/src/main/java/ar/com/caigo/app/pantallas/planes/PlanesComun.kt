package ar.com.caigo.app.pantallas.planes

import ar.com.caigo.app.piezas.colorDeCategoria
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import ar.com.caigo.app.nucleo.Gestos
import ar.com.caigo.app.piezas.tocable
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Contenedor
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.lineaConfianza
import ar.com.caigo.app.nucleo.Plata
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.Avatares
import ar.com.caigo.app.piezas.Avisos
import ar.com.caigo.app.piezas.BadgeFalta
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.Sistema
import ar.com.caigo.app.nucleo.distancia
import ar.com.caigo.app.nucleo.Categorias
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.estilo
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.launch

// Piezas que comparten las pantallas de planes.

/** "Lugar · 1,2 km" (o la zona si no hay lugar). */
internal fun lugarYDistancia(p: Plan): String =
    listOf(p.lugar?.nombre ?: p.lugarNombre.ifEmpty { p.zona }, distancia(p.distanciaM)).filter { it.isNotEmpty() }.joinToString(" · ")

/** Plan de un lugar aliado que se paga (entrada o seña). */
internal fun esDeLugar(p: Plan) = p.lugar != null && p.cobro != "gratis"

internal fun textoCosto(p: Plan): String {
    if (p.lugar != null && p.precioCentavos != null) {
        return Plata.pesos(p.precioCentavos.toLong()) + " por persona" + if (p.incluye.isEmpty()) "" else " · ${p.incluye}"
    }
    val c = p.costoCentavos
    if (c != null && c > 0) return Plata.pesos(c.toLong()) + " por persona"
    return "Cada uno lo suyo"
}

internal fun textoIngreso(p: Plan): String {
    if (p.lugar != null && p.cobro != "gratis") return "Con entrada"
    return when (p.ingreso) {
        "libre" -> "Libre"
        "mi_gente" -> "Solo mi gente"
        else -> "Con aprobación"
    }
}

/** Foto del plan (o la del lugar). */
/**
 * La API deja sumarse hasta media hora después de que empieza (`ahoraAtras` en planes.go); después
 * responde 409 «Este plan ya arrancó». Las apps no ofrecen sumarse pasado ese punto.
 */
internal fun cerroParaSumarse(p: Plan): Boolean {
    val t = Fecha.parse(p.empiezaEn) ?: return false
    return java.time.ZonedDateTime.now(t.zone).isAfter(t.plusMinutes(30))
}

internal fun fotoDe(p: Plan): String = p.foto.ifEmpty { p.lugar?.foto.orEmpty() }

/** El violeta con la base de 4 y la sombra difusa más corta (botones chicos del mockup). */
internal val planesVioletaChico = Gels.violeta.copy(
    sombras = listOf(
        interna(3f, 2f, rgba(255, 255, 255, .45f)), interna(-5f, 0f, rgba(40, 20, 140, .35f)),
        externa(4f, 0f, C.violetaBase), externa(16f, 26f, rgba(116, 86, 241, .75f), spread = -10f),
    ),
)

/**
 * Clave donde el detalle deja lo que eligió la persona para pagar: "entrada" (todo) o "sena".
 * La lee la pantalla de Checkout (Pagos) para mandar `modo` a POST /v1/planes/{id}/entradas.
 * En Android vive en las SharedPreferences [PREFS_CHECKOUT] (en iOS, UserDefaults).
 */
internal fun claveModoCheckout(planId: String) = "checkout.modo.$planId"
internal const val PREFS_CHECKOUT = "caigo"

internal fun leerModoCheckout(ctx: Context, planId: String): String? =
    runCatching { ctx.getSharedPreferences(PREFS_CHECKOUT, Context.MODE_PRIVATE).getString(claveModoCheckout(planId), null) }.getOrNull()

internal fun guardarModoCheckout(ctx: Context, planId: String, modo: String) {
    runCatching { ctx.getSharedPreferences(PREFS_CHECKOUT, Context.MODE_PRIVATE).edit().putString(claveModoCheckout(planId), modo).apply() }
}

/** Chip chico sobre la foto (categoría clara, hora oscura con grano). */
@Composable
internal fun ChipFoto(texto: String, oscuro: Boolean, modifier: Modifier = Modifier, fondo: Gel? = null, color: Color? = null) {
    val p = LocalPaleta.current
    val invertido = oscuro == !p.oscuro
    val g = fondo ?: if (invertido) Gels.oscuroSel else Gel(vertical(C.blancoCalido, C.blancoCalido), emptyList())
    val c = color ?: if (invertido) C.blancoCalido else C.carbon
    Box(modifier.gel(11.dp, g).padding(horizontal = 10.dp, vertical = 6.dp)) { T(texto, 12f, 700, c, unRenglon = true) }
}

/** Plan destacado con Caigo Plus: chip lima con la estrella del me encanta. */
@Composable
internal fun ChipDestacado(modifier: Modifier = Modifier, soloEstrella: Boolean = false) {
    Row(
        modifier.gel(11.dp, Gel(vertical(C.lima, C.lima), emptyList())).padding(horizontal = if (soloEstrella) 8.dp else 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icono(I.estrella, if (soloEstrella) 14.dp else 12.dp, Color.Transparent, 0f, relleno = C.carbon)
        if (!soloEstrella) T("Destacado", 12f, 700, C.carbon, unRenglon = true)
    }
}

/** Chip «Plus» lima (filtros finos, invitaciones bloqueadas). */
@Composable
internal fun PlanesChipPlus(texto: String = "Plus") {
    Box(Modifier.gel(11.dp, Gel(vertical(C.lima, C.lima), emptyList())).padding(horizontal = 10.dp, vertical = 6.dp)) {
        T(texto, 11f, 800, C.carbon, unRenglon = true)
    }
}

/**
 * Sumarse a un plan desde la tarjeta o el detalle. Los errores de verificación llevan a la selfie o
 * al DNI; el resto se muestra tal cual en el toast. Devuelve el estado nuevo, o "" ante un 409 (el plan
 * cambió: ya estabas, se llenó o te rechazaron), para que quien llama recargue.
 *
 * `confirmar_mujer` y `confirmar_diversidad`: en Android los resuelve
 * [ar.com.caigo.app.pantallas.ingreso.conConfirmacion] (la hoja global «Antes de sumarte» de
 * Genero.kt pregunta y se reintenta una vez), en vez del `pedirConfirmacion` de iOS.
 */
internal suspend fun planesSumarse(
    c: Contenedor, ir: Ir, avisos: Avisos, plan: Plan, cantidad: Int, mensaje: String = "",
    /** false en «Decidí rápido»: sin el festejo a pantalla completa (el mazo marca «Adentro»). */
    festejar: Boolean = true,
    vibrar: () -> Unit = {},
): String? = if (!c.conDni(ir, "sumarte", plan.id)) {
    // Sin DNI se mira; para sumarse, primero «verificamos que seas vos» (y de ahí vuelve al plan).
    null
} else try {
    val e = ar.com.caigo.app.pantallas.ingreso.conConfirmacion(c) { conf -> c.api.sumarme(plan.id, cantidad, mensaje, conf) }.estado
    if (e == "aceptada") {
        // Adentro: lo festeja la raíz a pantalla completa (Festejo.kt), en vez del aviso.
        if (festejar) c.festejo.value = ar.com.caigo.app.app.PlanFestejado(plan, cantidad)
    } else {
        val quien = plan.organizador.nombre.ifEmpty { "quien organiza" }
        avisos.ok("Le pediste a $quien sumarte.")
    }
    vibrar()
    e
} catch (e: ErrorApi) {
    if (ar.com.caigo.app.nucleo.Generos.pide(e.codigo) != null) {
        // La hoja ya se cerró sin confirmar: como en iOS, no se muestra el error.
        null
    } else {
        avisos.error(e.detalle)
        if (e.codigo == "falta_selfie") ir.selfie(volver = true)
        // falta_dni: lo resuelve la navegación (Contenedor.faltaDni apila PedirDni).
        if (e.estado == 409) "" else null
    }
} catch (e: Throwable) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    avisos.error(mensajeDe(e))
    null
}

/**
 * «Caigo» desde el gesto, la acción de accesibilidad o «Decidí rápido»: si no hay nada que confirmar
 * ([Gestos.directo]) se suma de a uno sin pasar por el detalle; si no, abre el detalle (ahí se confirma).
 * Devuelve el estado nuevo si se mandó el pedido.
 */
internal suspend fun planesCaigo(
    c: Contenedor, ir: Ir, avisos: Avisos, plan: Plan, festejar: Boolean = true, vibrar: () -> Unit = {},
): String? {
    if (!Gestos.directo(plan) || cerroParaSumarse(plan) || plan.organizador.id == c.sesion.usuarioId) {
        ir.plan(plan.id)
        return null
    }
    return planesSumarse(c, ir, avisos, plan, 1, vibrar = vibrar, festejar = festejar)
}

/** El link público de un plan (el que comparte el detalle). */
internal fun planesLink(p: Plan) = "https://caigo.com.ar/p/${p.slug}"

/** «Reducir movimiento» de Android: las animaciones del sistema apagadas (como `accessibilityReduceMotion`). */
@Composable
internal fun reducirMovimiento(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/**
 * Cantidad con la que se suma por defecto: la del 01b («Voy solo / Somos 2 / Somos 4»), acotada a
 * lo que acepta el plan.
 */
internal fun planesCantidadInicial(somos: Int, p: Plan): Int {
    if (!p.aceptaGrupos || p.faltan <= 1) return 1
    return maxOf(1, minOf(somos, p.faltan, 10))
}

/** Política de cancelación de una entrada (contrato §2), dicha para quien compró. */
internal fun planesPolitica(cancelacion: String): String = when (cancelacion) {
    "mod" -> "Hasta 72 h antes te devolvemos la entrada. Después podés cancelar igual para liberar el lugar, pero sin reembolso. El cargo por servicio no se devuelve."
    "strict" -> "El lugar no devuelve entradas de este plan. Si el plan se cancela, te devolvemos todo."
    else -> "Hasta 24 h antes te devolvemos la entrada. Después podés cancelar igual para liberar el lugar, pero sin reembolso. El cargo por servicio no se devuelve."
}

/** Botón de acción de la tarjeta: Sumarme / Entrada · Esperando… · Adentro. */
@Composable
internal fun AccionTarjeta(plan: Plan, ir: Ir, alCambiar: (String) -> Unit) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val vista = LocalView.current
    val alcance = rememberCoroutineScope()
    var enviando by remember { mutableStateOf(false) }
    when {
        plan.miEstado == "pendiente" -> Box(
            Modifier.gel(16.dp, vertical(C.tinte1, C.tinte1), listOf(interna(2f, 4f, rgba(116, 86, 241, .18f)))).padding(horizontal = 16.dp, vertical = 11.dp),
        ) { T("Esperando…", 14f, 800, C.violetaTexto, unRenglon = true) }
        plan.adentro -> Row(
            Modifier.background(C.carbon, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icono(I.check, 14.dp, C.lima, 3.4f)
            T(if (plan.esMio) "Tu plan" else "Adentro", 14f, 800, C.blanco, unRenglon = true)
        }
        Cupo.completo(plan.faltan) || plan.miEstado == "rechazada" || cerroParaSumarse(plan) -> Unit
        else -> Row(
            Modifier
                .toque(!enviando) {
                    if (enviando) return@toque
                    if (esDeLugar(plan)) { ir.plan(plan.id); return@toque }
                    // Antes del launch: un segundo toque no llega a mandar otro pedido.
                    enviando = true
                    alcance.launch {
                        planesSumarse(c, ir, avisos, plan, planesCantidadInicial(c.sesion.grupo, plan), vibrar = { Sistema.vibrar(vista) })?.let(alCambiar)
                        enviando = false
                    }
                }
                .gel(14.dp, Gels.limaChico)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (enviando) Puntitos(C.carbon, 6.dp)
            else {
                Icono(I.mas, 14.dp, C.carbon, 4f)
                T(if (esDeLugar(plan)) "Entrada" else "Sumarme", 14f, 800, C.carbon, unRenglon = true)
            }
        }
    }
}

/**
 * Tarjeta de plan (02 · Ahora): foto de 150 con categoría y hora arriba, badge "Faltan N" abajo a la
 * derecha; título, lugar · distancia, avatares apilados, "Somos N de M" y el botón lima.
 */
@Composable
internal fun TarjetaPlan(
    plan: Plan, ir: Ir, modifier: Modifier = Modifier, altoFoto: Dp = 150.dp,
    /** false en «Decidí rápido»: el mazo tiene sus botones abajo (si no, había dos «Sumarme»). */
    conAccion: Boolean = true,
    alCambiar: (String) -> Unit = {},
) {
    val p = LocalPaleta.current
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { role = Role.Button }
            .toque(alBajar = 0.dp) { ir.plan(plan.id) }
            .gel(24.dp, if (p.oscuro) Gels.tarjetaOscura else Gels.tarjeta)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(altoFoto)) {
            Foto(fotoDe(plan), Modifier.fillMaxSize(), tinte = colorDeCategoria(plan.categoria), categoria = plan.categoria)
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipFoto(Categorias.nombre(plan.categoria), oscuro = false)
                ChipFoto(Fecha.cuando(plan.empiezaEn), oscuro = true)
                // Sobre la foto, sólo qué, cuándo y si es de un lugar aliado (Prototipo): «Anfitrión» va en la línea
                // de confianza, junto a quien organiza.
                if (plan.lugar != null) ChipFoto("Lugar aliado", false, fondo = Gel(vertical(C.lima, C.lima), emptyList()), color = C.carbon)
            }
            if (plan.destacado) ChipDestacado(Modifier.align(Alignment.BottomStart).padding(12.dp))
            BadgeFalta(plan.faltan, Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(plan.titulo, 20f, 700, p.texto, alto = 1.15f, espaciado = px(-.3f, 20f))
                T(lugarYDistancia(plan), 13f, 400, p.texto2, unRenglon = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Con "Adentro" / "Esperando…" el botón es más ancho: van sólo las cuentas. Con el botón al lado, dos
                // caras y el resto en «+N»: el cupo no se corta a 360 dp.
                val conAvatares = plan.van.isNotEmpty() && (plan.miEstado == "ninguno" || !conAccion)
                if (conAvatares) Avatares(plan.van.map { it.nombre to it.foto }, total = plan.somos, max = if (conAccion) 2 else 3)
                T(Cupo.somos(plan.somos, plan.total), 13f, 600, if (p.oscuro) C.apagado2 else C.texto2, Modifier.weight(1f), unRenglon = true)
                Spacer(Modifier.width(12.dp))
                if (conAccion) AccionTarjeta(plan, ir, alCambiar)
            }
            LineaConfianza(plan)
        }
    }
}

/**
 * La línea de confianza (18/11), con lo que distingue al plan: en la tarjeta «Martín fue a 9 de 10 · Anfitrión»;
 * en el detalle ([detalle]), con el escudo, «Todos verificados · Plan de chicas».
 */
@Composable
internal fun LineaConfianza(plan: Plan, tam: Float = 12f, detalle: Boolean = false) {
    val p = LocalPaleta.current
    val t = lineaConfianza(plan, detalle) ?: return
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (detalle && plan.confianza.todosVerificados) Icono(I.escudoCheck, (tam + 3).dp, if (p.oscuro) C.lima else C.violeta, 2.2f)
        T(t, tam, 600, if (p.oscuro) C.apagado2 else C.texto2, Modifier.weight(1f), alto = 1.35f)
    }
}

/**
 * Una insignia («Anfitrión», «De la mesa»…): el mismo chip en el detalle, el perfil y donde aparezca (grafito con
 * texto lima), distinto de los gustos (lavanda). Igual que ChipInsignia de iOS y .insignia de la web.
 */
@Composable
internal fun ChipInsignia(texto: String, modifier: Modifier = Modifier, sobreOscuro: Boolean = false) {
    val p = LocalPaleta.current
    val fondo = if (sobreOscuro || p.oscuro) Color(0xFF45405A) else C.grafito
    Box(modifier.background(fondo, RoundedCornerShape(9.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        T(texto, 12f, 800, C.lima, unRenglon = true)
    }
}

// ——— Deslizar una tarjeta: «Sumarme» y «No me interesa» ———

/**
 * Deslizar la tarjeta de un plan: a la derecha asoma «Sumarme» sobre lima, a la izquierda «No me interesa» sobre
 * grafito. Cuenta pasado el 35 % del ancho ([Gestos.umbral]), con un toque suave al cruzarlo; si no llega, vuelve a
 * su lugar. Sólo toma el arrastre horizontal: el vertical sigue siendo de la lista. Las mismas dos acciones van
 * como acciones de accesibilidad (en el modificador que recibe [contenido], el de la tarjeta). Con «reducir
 * movimiento», la tarjeta no vuela: desaparece.
 *
 * El dedo se sigue con [desplazamiento], sincrónico (cada evento suma lo suyo, sin esperar a una corrutina), y
 * se dibuja con translationX; [dx] (Animatable) sólo anima la vuelta, la salida y la pista. Igual que iOS, que
 * usa la traslación exacta.
 */
@Composable
internal fun DeslizarPlan(
    radio: Dp,
    alCaigo: () -> Unit,
    alNoMeInteresa: () -> Unit,
    modifier: Modifier = Modifier,
    /** La primera vez (una sola), la tarjeta se corre ~40 dp a cada lado y deja ver los dos fondos. */
    pista: Boolean = false,
    contenido: @Composable (Modifier) -> Unit,
) {
    val vista = LocalView.current
    val ctx = LocalContext.current
    val densidad = LocalDensity.current
    val sinMovimiento = reducirMovimiento()
    val alcance = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    var desplazamiento by remember { mutableFloatStateOf(0f) }
    var arrastrando by remember { mutableStateOf(false) }
    var ancho by remember { mutableFloatStateOf(1f) }
    var paso by remember { mutableStateOf(false) }
    val caigo by rememberUpdatedState(alCaigo)
    val noMeInteresa by rememberUpdatedState(alNoMeInteresa)
    val actual = if (arrastrando) desplazamiento else dx.value

    fun volver(desde: Float) {
        alcance.launch {
            dx.snapTo(desde)
            arrastrando = false
            if (sinMovimiento) dx.snapTo(0f) else dx.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(pista) {
        if (!pista || sinMovimiento) return@LaunchedEffect
        val prefs = ctx.getSharedPreferences("caigo", Context.MODE_PRIVATE)
        if (prefs.getBoolean("pista.gestos", false)) return@LaunchedEffect
        prefs.edit().putBoolean("pista.gestos", true).apply()
        val d = with(densidad) { 44.dp.toPx() }
        delay(700)
        dx.animateTo(d, tween(300, easing = FastOutSlowInEasing))
        delay(250)
        dx.animateTo(-d, tween(350, easing = FastOutSlowInEasing))
        delay(250)
        dx.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
    }

    Box(
        modifier
            .onSizeChanged { ancho = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(sinMovimiento) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        desplazamiento = 0f
                        arrastrando = true
                    },
                    onDragEnd = {
                        val w = desplazamiento
                        paso = false
                        when {
                            w >= ancho * Gestos.umbral -> { volver(w); caigo() }
                            w <= -ancho * Gestos.umbral -> alcance.launch {
                                dx.snapTo(w)
                                arrastrando = false
                                if (sinMovimiento) {
                                    dx.snapTo(0f)
                                    noMeInteresa()
                                } else {
                                    dx.animateTo(-ancho * 1.15f, tween(180, easing = LinearOutSlowInEasing))
                                    noMeInteresa()
                                    dx.snapTo(0f)
                                }
                            }
                            else -> volver(w)
                        }
                    },
                    onDragCancel = { paso = false; volver(desplazamiento) },
                ) { cambio, d ->
                    cambio.consume()
                    desplazamiento += d
                    val nuevo = desplazamiento
                    val pasa = kotlin.math.abs(nuevo) >= ancho * Gestos.umbral
                    if (pasa != paso) {
                        paso = pasa
                        if (pasa) Sistema.suave(vista)
                    }
                }
            },
    ) {
        val v = actual
        if (v > 0f) {
            Row(
                Modifier.matchParentSize().gel(radio, Gels.lima.fondo).padding(start = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icono(I.mas, 18.dp, C.carbon, 3.4f)
                T("Sumarme", 17f, 800, C.carbon, unRenglon = true)
            }
        } else if (v < 0f) {
            Row(
                Modifier.matchParentSize().background(C.grafito, RoundedCornerShape(radio)).padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                T("No me interesa", 15f, 800, C.blancoCalido, unRenglon = true)
                Icono(I.cruz, 16.dp, C.blancoCalido, 3f)
            }
        }
        contenido(
            Modifier
                .graphicsLayer { translationX = if (arrastrando) desplazamiento else dx.value }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Sumarme") { caigo(); true },
                        CustomAccessibilityAction("No me interesa") { noMeInteresa(); true },
                    )
                },
        )
    }
}

/** Los planes que la persona ocultó con «No me interesa» en esta pantalla, y el aviso «Deshacer» (5 s). */
@Stable
internal class PlanesOcultos {
    data class Oculto(val plan: Plan, val id: Long = System.nanoTime())

    var ids by mutableStateOf(emptySet<String>())
        private set
    /** El último que se ocultó: el aviso de abajo con «Deshacer». */
    var aviso by mutableStateOf<Oculto?>(null)

    fun visibles(planes: List<Plan>): List<Plan> = planes.filter { it.id !in ids }

    /** POST /v1/planes/{id}/ocultar. La tarjeta se va enseguida; si la API falla, vuelve. */
    fun ocultar(c: Contenedor, avisos: Avisos, p: Plan, avisar: Boolean = true) {
        ids = ids + p.id
        if (avisar) aviso = Oculto(p)
        c.alcance.launch {
            try {
                c.api.ocultarPlan(p.id)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ids = ids - p.id
                if (aviso?.plan?.id == p.id) aviso = null
                avisos.error(mensajeDe(e))
            }
        }
    }

    /** «Deshacer»: DELETE /v1/planes/{id}/ocultar y la tarjeta vuelve a su lugar. */
    fun deshacer(c: Contenedor, avisos: Avisos) {
        val p = aviso?.plan ?: return
        aviso = null
        ids = ids - p.id
        c.alcance.launch {
            try {
                c.api.mostrarPlan(p.id)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ids = ids + p.id
                avisos.error(mensajeDe(e))
            }
        }
    }
}

/** «Ocultaste {título} · Deshacer»: abajo, sobre la tab bar, con la pieza de los avisos (carbón, 5 s). */
@Composable
internal fun BoxScope.AvisoDeshacer(ocultos: PlanesOcultos, abajo: Dp) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val sinMovimiento = reducirMovimiento()
    val a = ocultos.aviso
    // Con TalkBack se anuncia (liveRegion) y dura lo que recomienda el sistema para algo con controles: llegar a
    // «Deshacer» lleva tiempo (WCAG 2.2.1).
    val accesibilidad = LocalAccessibilityManager.current
    val espera = accesibilidad?.calculateRecommendedTimeoutMillis(5000L, containsIcons = false, containsText = true, containsControls = true) ?: 5000L
    LaunchedEffect(a?.id) { if (a != null) { delay(espera); if (ocultos.aviso?.id == a.id) ocultos.aviso = null } }
    val ultimo = remember { arrayOfNulls<PlanesOcultos.Oculto>(1) }
    if (a != null) ultimo[0] = a
    AnimatedVisibility(
        a != null,
        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = abajo),
        enter = if (sinMovimiento) fadeIn(tween(250)) else fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
        exit = if (sinMovimiento) fadeOut(tween(200)) else fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
    ) {
        val v = a ?: ultimo[0] ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .gel(20.dp, vertical(C.carbon, C.carbon), listOf(externa(18f, 30f, rgba(0, 0, 0, .5f), spread = -12f)))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T("Ocultaste ${v.plan.titulo}", 14f, 700, C.blancoCalido, Modifier.weight(1f, fill = false), unRenglon = true)
            T("·", 14f, 700, C.apagado2)
            T("Deshacer", 14f, 800, C.lima, Modifier.tocable { ocultos.deshacer(c, avisos) }.padding(vertical = 4.dp), unRenglon = true)
        }
    }
}

/** Beneficio por check-in ("Café y medialuna de regalo"). */
@Composable
internal fun PlanesBeneficio(texto: String, desbloqueado: Boolean) {
    // Desbloqueado va en lima (igual en claro y oscuro); bloqueado, con la superficie de la paleta.
    val p = LocalPaleta.current
    val oscuro = p.oscuro && !desbloqueado
    Row(
        Modifier.fillMaxWidth()
            .gel(
                22.dp,
                if (desbloqueado) Gel(vertical(C.lima0, C.lima), listOf(interna(2f, 0f, rgba(255, 255, 255, .7f)), externa(3f, 0f, C.limaBase)))
                else Gel(vertical(p.superficie, p.superficie), emptyList()),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).background(if (desbloqueado) C.carbon else if (oscuro) C.oscuro4 else C.blanco, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icono(I.regalo, 22.dp, if (desbloqueado || oscuro) C.lima else C.violetaTexto, 2.2f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(texto, 14f, 800, if (desbloqueado) C.carbon else p.texto)
            T(
                if (desbloqueado) "Desbloqueado · mostralo en la caja" else "Se desbloquea cuando hacés check-in en el lugar", 12f, 600,
                if (desbloqueado) C.limaTexto else p.texto2,
            )
        }
    }
}

/** Campo de texto liso con pista (Geist), para buscadores y formularios. */
@Composable
internal fun PlanesCampo(
    texto: String,
    alCambiar: (String) -> Unit,
    pista: String,
    modifier: Modifier = Modifier,
    tam: Float = 15f,
    peso: Int = 500,
    colorPista: Color = C.texto2,
    color: Color? = null,
    teclado: KeyboardType = KeyboardType.Text,
) {
    val p = LocalPaleta.current
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (texto.isEmpty()) T(pista, tam, if (peso == 500) 400 else 600, colorPista, unRenglon = true)
        BasicTextField(
            texto, alCambiar, singleLine = true, textStyle = estilo(tam, peso, color ?: p.texto), cursorBrush = SolidColor(C.violeta),
            keyboardOptions = KeyboardOptions(keyboardType = teclado, autoCorrectEnabled = teclado == KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

