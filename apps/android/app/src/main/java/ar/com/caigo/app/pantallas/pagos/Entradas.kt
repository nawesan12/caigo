package ar.com.caigo.app.pantallas.pagos

import ar.com.caigo.app.pantallas.mios.MiosGrupo
import ar.com.caigo.app.pantallas.mios.MiosHoja

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Compra
import ar.com.caigo.app.nucleo.EntradaMia
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.Plata
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.MensajeCentro
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.abrirPestana
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.PaletaClara
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.estilo
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tarjeta de la entrada: radial grafito con grano. */
private val gelTicket = Gel(
    radial(0f, 0f, 1.2f, .9f, 0f to C.oscuro1, .62f to C.carbon),
    listOf(externa(30f, 50f, rgba(36, 33, 45, .7f), spread = -24f)),
    grano = true,
)

/** Opciones de cobro del checkout (coModes): elegida lavanda con anillo violeta, la otra blanca. */
private val gelModoOn = Gel(vertical(C.lavanda, C.lavanda), listOf(interna(0f, 0f, C.violeta, spread = -2.5f), externa(3f, 0f, C.borde5)))
private val gelModoOff = Gel(vertical(C.blanco, C.blanco), listOf(interna(1f, 0f, C.blanco), externa(3f, 0f, C.borde1)))

/**
 * Lo que el detalle dejó elegido para pagar ("entrada" o "sena"): `UserDefaults` en el iPhone
 * (`claveModoCheckout` de PlanesComun.swift).
 * PARA EL NÚCLEO / planes: DetallePlan tiene que guardarlo al elegir «Señar» o «Pagar todo» en
 * SharedPreferences "caigo", clave `checkout.modo.<planId>`.
 */
private fun modoGuardado(ctx: android.content.Context, planId: String): String? =
    runCatching { ctx.getSharedPreferences("caigo", android.content.Context.MODE_PRIVATE).getString("checkout.modo.$planId", null) }.getOrNull()

/**
 * "Tu entrada" (checkout del prototipo): cantidad, cómo se cobra (entrada entera o seña, lo
 * define el lugar), desglose con el cargo por servicio y pago con Mercado Pago.
 */
@Composable
fun Checkout(ir: Ir, planId: String, cantidad: Int) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val carga = rememberCarga(planId) { c.api.plan(planId) }
    var q by remember { mutableIntStateOf(cantidad.coerceIn(1, 10)) }
    // El detalle deja elegido "Pagar todo" o "Señar" (como el Prototipo).
    var modo by remember { mutableStateOf(modoGuardado(ctx, planId)?.takeIf { it == "sena" || it == "entrada" } ?: "entrada") }
    var pagando by remember { mutableStateOf(false) }
    var compra by remember { mutableStateOf<Compra?>(null) }
    var revisando by remember { mutableStateOf(false) }

    /** Busca la entrada ya pagada en "Mis pases" (el webhook de Mercado Pago la marca como pagada). */
    suspend fun revisar(): Boolean {
        val id = compra?.entradaId
        if (id.isNullOrEmpty()) return false
        revisando = true
        val ok = try {
            c.api.pases().entradas.any { it.id == id }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
        revisando = false
        if (ok) ir.entrada(id, planId)
        return ok
    }

    // Al volver de Mercado Pago (Custom Tab), se fija si ya se acreditó.
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME && compra != null) alcance.launch { revisar() } }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }
    // Mientras espera, vuelve a mirar cada 4 s (el aviso de Mercado Pago puede tardar).
    LaunchedEffect(compra?.entradaId) {
        if (compra == null) return@LaunchedEffect
        repeat(30) {
            delay(4000)
            if (revisar()) return@LaunchedEffect
        }
    }

    fun pagar(plan: Plan) = alcance.launch {
        pagando = true
        try {
            val m = if (plan.cobro == "sena" && plan.senaPct != null) modo else "entrada"
            // Plan «Solo mujeres» / «Solo diversidades»: se confirma y se reintenta (no se guarda).
            val r = ar.com.caigo.app.pantallas.ingreso.conConfirmacion(c) { conf -> c.api.comprarEntradas(plan.id, q, m, conf) }
            when {
                r.link.isNotEmpty() -> { compra = r; abrirPestana(ctx, r.link) }
                r.entradaId.isNotEmpty() -> ir.entrada(r.entradaId, plan.id)
                else -> { avisos.ok("Ya tenés tu lugar en este plan."); ir.atras() }
            }
        } catch (e: ErrorApi) {
            if (ar.com.caigo.app.nucleo.Generos.pide(e.codigo) == null) {
                avisos.error(e.detalle)
                if (e.codigo == "falta_selfie") ir.selfie(true)
                if (e.codigo == "falta_dni") ir.dni(plan.id)
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            avisos.error(mensajeDe(e))
        } finally {
            pagando = false
        }
    }

    CompositionLocalProvider(LocalPaleta provides PaletaClara) {
        val p = LocalPaleta.current
        PagosBarraEstado(clara = false)
        Pantalla(oscuro = false) { arriba, abajo ->
            val plan = carga.dato
            if (plan == null) {
                Column(Modifier.fillMaxSize().padding(top = arriba + 8.dp, start = 20.dp, end = 20.dp, bottom = abajo)) {
                    PagosEncabezado("Tu entrada") { ir.atras() }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val err = carga.error
                        if (err != null && !carga.cargando) MensajeCentro("No pudimos cargar el plan", err) else Puntitos()
                    }
                }
                return@Pantalla
            }
            val esperando = compra
            if (esperando != null) {
                Column(
                    Modifier.fillMaxSize().padding(top = arriba + 8.dp, start = 24.dp, end = 24.dp, bottom = abajo + 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    PagosEncabezado("Tu entrada") { compra = null }
                    Spacer(Modifier.weight(1f))
                    Puntitos()
                    MensajeCentro("Esperando la confirmación de Mercado Pago…", "Cuando se acredite el pago, tu entrada aparece acá y en Mis pases.")
                    Spacer(Modifier.weight(1f))
                    Cta(
                        "Ya pagué, revisar",
                        { alcance.launch { if (!revisar()) avisos.error("Todavía no nos llegó el pago. Esperá un ratito y probá de nuevo.") } },
                        Modifier.fillMaxWidth(), cargando = revisando,
                    )
                    T("Volver a abrir Mercado Pago", 15f, 700, C.violetaTexto, Modifier.tocable { abrirPestana(ctx, esperando.link) }.padding(8.dp))
                }
                return@Pantalla
            }

            val conSena = plan.cobro == "sena" && plan.senaPct != null
            val sena = conSena && modo == "sena"
            val precio = (plan.precioCentavos ?: 0).toLong()
            val cargo = plan.cargoCentavos.takeIf { it > 0 }
            val porPersona = Plata.desglose(precio, "sena", plan.senaPct, 1, cargo)
            val d = Plata.desglose(precio, if (sena) "sena" else "entrada", plan.senaPct, q, cargo)
            val maximo = if (plan.aceptaGrupos) minOf(10, maxOf(plan.faltan, 1)) else 1

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(top = arriba + 8.dp, start = 20.dp, end = 20.dp, bottom = 150.dp + abajo),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PagosEncabezado("Tu entrada") { ir.atras() }
                Row(
                    Modifier.fillMaxWidth().gel(24.dp, Gels.neutra).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Foto(plan.foto, Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(plan.titulo, 17f, 700, C.carbon, alto = 1.15f)
                        T(pagosCuandoDonde(plan.empiezaEn, pagosLugarDe(plan)), 13f, 600, C.texto2)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().gel(22.dp, Gels.neutra).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        T("Entradas", 15f, 800, C.carbon)
                        T(if (q == 1) "Vas solo" else "Van $q juntos", 12f, 600, C.texto2)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BotonCantidad("−", q > 1) { q -= 1 }
                        // monospacedDigit: Geist con cifras tabulares.
                        BasicText(
                            "$q", Modifier.widthIn(min = 20.dp),
                            style = estilo(24f, 800, C.carbon, 1f, alinear = TextAlign.Center).copy(fontFeatureSettings = "tnum"),
                        )
                        BotonCantidad("+", q < maximo) { q += 1 }
                    }
                }
                // Plan con seña: «Pagar todo» o «Señar N%», las dos opciones del prototipo (coModes).
                if (conSena) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OpcionModo(modo == "entrada", "Pagar todo", "Entrada completa ahora") { modo = "entrada" }
                    OpcionModo(modo == "sena", "Señar ${plan.senaPct ?: 0}%", "Reservás con ${Plata.pesos(porPersona.alLugar)}; el resto en el lugar") { modo = "sena" }
                }
                Column(
                    Modifier.fillMaxWidth().gel(22.dp, Gels.neutra).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilaDesglose((if (sena) "Seña ${plan.senaPct ?: 0}%" else "Entrada") + " × $q", Plata.pesos(d.alLugar))
                    FilaDesglose("Cargo por servicio", Plata.pesos(d.cargo))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.lavanda))
                    Row(Modifier.fillMaxWidth()) {
                        T(if (sena) "Pagás hoy" else "Total", 15f, 800, C.carbon, Modifier.weight(1f).alignByBaseline())
                        T(Plata.pesos(d.total), 28f, 800, C.carbon, Modifier.alignByBaseline(), alto = 1f, unRenglon = true)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().gel(22.dp, Gel(vertical(C.lavanda, C.lavanda), listOf(interna(0f, 0f, C.violeta, spread = -2f))))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).background(Color(0xFF009EE3), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        T("MP", 15f, 800, C.blanco, alto = 1f)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        T("Mercado Pago", 15f, 800, C.carbon)
                        T("Dinero en cuenta, débito o crédito", 12f, 600, C.texto2)
                    }
                    Box(Modifier.size(24.dp).background(C.blanco, CircleShape).border(7.dp, C.violeta, CircleShape))
                }
                if (sena) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFFFE9E4), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T("En el lugar pagás", 14f, 700, Color(0xFF7A2416), Modifier.weight(1f))
                        T(Plata.pesos(d.saldo), 20f, 800, Color(0xFF7A2416), alto = 1f, unRenglon = true)
                    }
                }
                T("Si el plan se cancela, te devolvemos todo. Para el lugar, vender en Caigo es gratis.", 12f, 600, p.texto2, Modifier.fillMaxWidth(), alto = 1.5f)
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to p.fondo.copy(alpha = 0f), .25f to p.fondo))
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 30.dp + abajo),
            ) {
                Cta(
                    (if (sena) "Señar " else "Pagar ") + Plata.pesos(d.total), { pagar(plan) }, Modifier.fillMaxWidth(),
                    gel = Gels.lima, alto = 62.dp, radio = 22.dp, tam = 18f, color = C.carbon, cargando = pagando,
                )
            }
        }
    }
}

@Composable
private fun RowScope.OpcionModo(on: Boolean, l: String, sub: String, elegir: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .semantics { selected = on }
            .toque(alBajar = 3.dp, onClick = elegir)
            .gel(20.dp, if (on) gelModoOn else gelModoOff)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        T(l, 15f, 800, C.carbon)
        T(sub, 12f, 600, C.texto2, alto = 1.35f)
    }
}

@Composable
private fun BotonCantidad(signo: String, habilitado: Boolean, accion: () -> Unit) {
    Box(
        Modifier.size(40.dp).graphicsLayer { alpha = if (habilitado) 1f else .45f }
            .semantics { contentDescription = if (signo == "+") "Una entrada más" else "Una entrada menos" }
            .toque(habilitado, onClick = accion).background(C.lavanda, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) { T(signo, 22f, 800, C.violetaTexto, alto = 1f) }
}

@Composable
private fun FilaDesglose(izq: String, der: String) {
    Row(Modifier.fillMaxWidth()) {
        T(izq, 14f, 600, C.texto2, Modifier.weight(1f))
        T(der, 14f, 800, C.carbon, unRenglon = true)
    }
}

/**
 * "Tenés tu entrada" (prototipo checkout_done + Pases NFC 1c): la tarjeta con el código, el
 * beneficio del check-in, guardar en Google Wallet y volver al plan.
 */
@Composable
fun Entrada(ir: Ir, entradaId: String, planId: String) {
    val c = LocalApp.current
    val pases = rememberCarga(entradaId) { c.api.pases() }
    val entrada = pases.dato?.entradas?.firstOrNull { it.id == entradaId }
    val idPlan = planId.ifEmpty { entrada?.planId.orEmpty() }
    val plan = rememberCarga(idPlan) { if (idPlan.isEmpty()) null else runCatching { c.api.plan(idPlan) }.getOrNull() }
    var cancelando by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalPaleta provides PaletaClara) {
        val p = LocalPaleta.current
        PagosBarraEstado(clara = false)
        Pantalla(oscuro = false) { arriba, abajo ->
            if (entrada == null) {
                Column(Modifier.fillMaxSize().padding(top = arriba + 8.dp, start = 20.dp, end = 20.dp, bottom = abajo)) {
                    PagosEncabezado("Tu entrada") { ir.atras() }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (pases.cargando && pases.dato == null) Puntitos()
                        else MensajeCentro("Tu entrada todavía no está", pases.error ?: "Cuando Mercado Pago confirme el pago, aparece acá y en Mis pases.")
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(top = arriba + 20.dp, start = 24.dp, end = 24.dp, bottom = abajo + 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    PagosSube {
                        T("Tenés tu entrada", 36f, 800, alto = 1f, espaciado = px(-1.2f, 36f), alinear = TextAlign.Center)
                    }
                    T("También la tenés siempre en Mis pases.", 15f, 600, p.texto2, alinear = TextAlign.Center)
                    PagosPop { Ticket(entrada, plan.dato?.foto) { ir.qrEntrada(entrada.id) } }
                    if (entrada.beneficio.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().background(C.lavanda, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icono(I.regalo, 24.dp, C.violetaTexto, 2.2f)
                            T("Hacé check-in al llegar y desbloqueás: ${entrada.beneficio}", 13f, 700, C.carbon, Modifier.weight(1f))
                        }
                    }
                    PagosBoton("Mostrar QR de entrada", PagosEstiloBoton.Blanco, icono = I.qr, radio = 20.dp, peso = 800) { ir.qrEntrada(entrada.id) }
                    PagosBotonWallet { c.api.walletEntrada(entrada.id).link }
                    Cta("Ver el plan", { ir.plan(entrada.planId) }, Modifier.fillMaxWidth(), gel = Gels.violeta, radio = 22.dp, color = C.blanco)
                    if (!entrada.validada) T(
                        "Cancelar mi entrada", 15f, 700, C.coral2,
                        Modifier.tocable { cancelando = true }.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
            // Cancelar según la política del plan (contrato §2): flex, mod o strict.
            HojaCancelarEntrada(cancelando, { cancelando = false }, entradaId, plan.dato?.cancelacion.orEmpty()) { ir.atras() }
        }
    }
}

@Composable
private fun Ticket(e: EntradaMia, foto: String?, tocar: () -> Unit) {
    Column(Modifier.fillMaxWidth().gel(30.dp, gelTicket).clip(RoundedCornerShape(30.dp)).tocable(tocar)) {
        Foto(foto, Modifier.fillMaxWidth().height(150.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(e.titulo, 22f, 700, C.blancoCalido, alto = 1.1f)
            T(pagosCuandoDonde(e.empiezaEn, e.lugar), 13f, 600, Color(0xFFD3CEE2))
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(2.dp).drawBehind {
                drawLine(
                    rgba(255, 255, 255, .2f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            },
        )
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T("CÓDIGO", 11f, 800, Color(0xFFC9C4DA), espaciado = px(.8f, 11f))
                T(e.codigo, 24f, 800, C.lima, alto = 1f, espaciado = px(1f, 24f), unRenglon = true)
            }
            val etiqueta = (if (e.cantidad == 1) "1 entrada" else "${e.cantidad} entradas") + if (e.modo == "sena") " · señada" else ""
            Box(Modifier.background(rgba(255, 255, 255, .12f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                T(etiqueta, 13f, 800, C.blancoCalido, unRenglon = true)
            }
        }
    }
}

/** "Tu entrada" con el QR para mostrar en la puerta (con el brillo al máximo). */
@Composable
fun QrEntrada(ir: Ir, entradaId: String) {
    val c = LocalApp.current
    val vista = LocalView.current
    val pases = rememberCarga(entradaId) { c.api.pases() }
    val entrada = pases.dato?.entradas?.firstOrNull { it.id == entradaId }
    DisposableEffect(Unit) {
        val v = vista.context.pagosActividad()?.window
        val antes = v?.attributes?.screenBrightness
        v?.attributes = v?.attributes?.apply { screenBrightness = 1f }
        onDispose { v?.attributes = v?.attributes?.apply { screenBrightness = antes ?: -1f } }
    }
    CompositionLocalProvider(LocalPaleta provides PaletaClara) {
        val p = LocalPaleta.current
        PagosBarraEstado(clara = false)
        Pantalla(oscuro = false) { arriba, abajo ->
            Column(
                Modifier.fillMaxSize().padding(top = arriba + 8.dp, start = 22.dp, end = 22.dp, bottom = abajo + 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PagosEncabezado("Tu entrada", icono = I.cruz, tam = 26f) { ir.atras() }
                if (entrada != null) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(entrada.titulo, 20f, 700, alto = 1.15f)
                        T(pagosCuandoDonde(entrada.empiezaEn, entrada.lugar), 13f, 600, p.texto2)
                    }
                    Box(
                        Modifier.padding(top = 10.dp).gel(
                            30.dp,
                            Gel(vertical(C.blanco, C.blanco), listOf(interna(0f, 0f, C.lima, spread = -3f), externa(5f, 0f, C.limaBase), externa(30f, 50f, rgba(80, 60, 160, .45f), spread = -24f))),
                        ).padding(20.dp),
                    ) { PagosCodigoQr(entrada.codigo, Modifier.size(225.dp)) }
                    T(entrada.codigo, 28f, 800, alto = 1f, espaciado = px(1.5f, 28f), unRenglon = true)
                    T("Mostralo en la puerta. Subí el brillo si está oscuro.", 14f, 600, p.texto2, alto = 1.5f, alinear = TextAlign.Center)
                    Spacer(Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (pases.cargando && pases.dato == null) Puntitos() else MensajeCentro("No encontramos tu entrada", pases.error ?: "Fijate en Mis pases.")
                    }
                }
            }
        }
    }
}

/** Política de cancelación de una entrada (contrato §2), dicha para quien compró (`planesPolitica` del iPhone). */
internal fun textoPolitica(cancelacion: String): String = when (cancelacion) {
    "mod" -> "Hasta 72 h antes te devolvemos la entrada. Después podés cancelar igual para liberar el lugar, pero sin reembolso. El cargo por servicio no se devuelve."
    "strict" -> "El lugar no devuelve entradas de este plan. Si el plan se cancela, te devolvemos todo."
    else -> "Hasta 24 h antes te devolvemos la entrada. Después podés cancelar igual para liberar el lugar, pero sin reembolso. El cargo por servicio no se devuelve."
}

/**
 * Hoja para cancelar mi entrada (DELETE /v1/entradas/{id}): la `HojaConfirmar` del iPhone con la
 * política del plan; con `strict` sólo explica y ofrece «Volver». La usan Entrada y el detalle.
 */
@Composable
internal fun BoxScope.HojaCancelarEntrada(abierta: Boolean, cerrar: () -> Unit, entradaId: String, cancelacion: String, alCancelar: () -> Unit) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf(false) }
    val sinReembolso = cancelacion == "strict"
    MiosHoja(abierta, { if (!ocupado) cerrar() }, if (sinReembolso) "Tu entrada no tiene reembolso" else "¿Cancelar tu entrada?") {
        T(textoPolitica(cancelacion), 15f, 400, p.texto2, alto = 1.5f)
        MiosGrupo {
            if (!sinReembolso) {
                Box(
                    Modifier.fillMaxWidth().tocable {
                        if (ocupado) return@tocable
                        ocupado = true
                        alcance.launch {
                            try {
                                val r = c.api.cancelarEntrada(entradaId)
                                avisos.ok(
                                    if (r.reembolsoCentavos > 0) "Listo. Te devolvemos ${Plata.pesos(r.reembolsoCentavos)} por Mercado Pago."
                                    else "Listo, cancelaste tu entrada.",
                                )
                                cerrar()
                                alCancelar()
                            } catch (e: ErrorApi) {
                                avisos.error(if (e.codigo == "sin_reembolso" && e.problema.detail.isEmpty()) "El lugar no devuelve entradas de este plan." else e.detalle)
                            } catch (e: Throwable) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                avisos.error(mensajeDe(e))
                            } finally {
                                ocupado = false
                            }
                        }
                    }.padding(horizontal = 18.dp, vertical = 16.dp),
                ) { if (ocupado) Puntitos(C.coral2) else T("Cancelar mi entrada", 15f, 700, C.coral2) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (p.oscuro) p.separador else C.lavanda))
            }
            Box(Modifier.fillMaxWidth().tocable { if (!ocupado) cerrar() }.padding(horizontal = 18.dp, vertical = 16.dp)) {
                T("Volver", 15f, 700)
            }
        }
    }
}
