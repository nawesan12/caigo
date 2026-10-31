package ar.com.caigo.app.ui.pantallas.mios

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.datos.Compra
import ar.com.caigo.app.datos.ErrorApi
import ar.com.caigo.app.datos.Plan
import ar.com.caigo.app.datos.mensajeDe
import ar.com.caigo.app.logica.Plata
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.CodigoQr
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.Foto
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.MensajeCentro
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.abrirPestana
import ar.com.caigo.app.ui.piezas.rememberCarga
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.radial
import ar.com.caigo.app.ui.tema.rgba
import ar.com.caigo.app.ui.tema.vertical
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Botón volver de "Tu entrada" (blanco con base lavanda). */
private val gelVolver = Gel(vertical(C.blanco, Color(0xFFF3F0FA)), listOf(interna(2f, 0f, C.blanco), externa(3f, 0f, C.borde3)))

/** Tarjeta de la entrada: radial grafito con grano. */
private val gelTicket = Gel(
    radial(0f, 0f, 1.2f, .9f, 0f to C.oscuro1, .62f to C.carbon),
    listOf(externa(30f, 50f, rgba(36, 33, 45, .7f), spread = -24f)),
    grano = true,
)

/** Opciones de cobro del checkout (coModes): elegida lavanda con borde violeta, la otra blanca. */
private val gelModoOn = Gel(vertical(C.lavanda, C.lavanda), listOf(externa(3f, 0f, C.borde5)))
private val gelModoOff = Gel(vertical(C.blanco, C.blanco), listOf(interna(1f, 0f, C.blanco), externa(3f, 0f, C.borde1)))

/** Lima gel del CTA de checkout (base de 5 px). */
private val gelPagar = Gels.lima

@Composable
private fun EncabezadoEntrada(titulo: String, icono: String, tam: Float, onVolver: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).toque(onClick = onVolver).gel(16.dp, gelVolver), contentAlignment = Alignment.Center) {
            Icono(icono, if (icono == I.cruz) 20.dp else 22.dp, C.carbon, if (icono == I.cruz) 2.6f else 2.4f)
        }
        T(titulo, tam, 800, LocalPaleta.current.texto, alto = 1f, espaciado = px(-.8f, tam))
    }
}

private fun lugarDe(p: Plan) = p.lugar?.nombre?.ifBlank { null } ?: p.lugarNombre

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
    val p = LocalPaleta.current
    val carga = rememberCarga(planId) { c.api.plan(planId) }
    var q by remember { mutableIntStateOf(cantidad.coerceIn(1, 10)) }
    var pagando by remember { mutableStateOf(false) }
    var compra by remember { mutableStateOf<Compra?>(null) }
    var revisando by remember { mutableStateOf(false) }
    // En un plan con seña se elige: pagar todo (por defecto, como el prototipo) o señar.
    var modo by remember { mutableStateOf("entrada") }

    /** Busca la entrada ya pagada en "Mis pases" (el webhook de Mercado Pago la marca como pagada). */
    suspend fun revisar(): Boolean {
        val id = compra?.entradaId ?: return false
        revisando = true
        val ok = runCatching { c.api.pases().entradas.any { it.id == id } }.getOrDefault(false)
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
    LaunchedEffect(compra) {
        if (compra == null) return@LaunchedEffect
        repeat(30) { delay(4000); if (revisar()) return@LaunchedEffect }
    }

    fun pagar(plan: Plan) = alcance.launch {
        pagando = true
        try {
            val r = ar.com.caigo.app.ui.pantallas.ingreso.conGenero(c) { c.api.comprarEntradas(plan.id, q, if (plan.cobro == "sena" && plan.senaPct != null) modo else "entrada") }
            when {
                r.link.isNotBlank() -> { compra = r; abrirPestana(ctx, r.link) }
                r.entradaId.isNotBlank() -> ir.entrada(r.entradaId, plan.id)
                else -> { avisos.ok("Ya tenés tu lugar en este plan."); ir.atras() }
            }
        } catch (e: ErrorApi) {
            when (e.codigo) {
                "falta_selfie" -> { avisos.error(e.detalle); ir.selfie(true) }
                "falta_dni" -> { avisos.error(e.detalle); ir.dni(plan.id) }
                else -> avisos.error(e.detalle)
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            avisos.error(mensajeDe(e))
        } finally {
            pagando = false
        }
    }

    Pantalla { arriba, abajo ->
        val plan = carga.dato
        if (plan == null) {
            Column(Modifier.fillMaxSize().padding(top = arriba + 8.dp, start = 20.dp, end = 20.dp)) {
                EncabezadoEntrada("Tu entrada", I.atras, 28f) { ir.atras() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (carga.cargando) Puntitos() else MensajeCentro("No pudimos cargar el plan", carga.error.orEmpty())
                }
            }
            return@Pantalla
        }
        val esperando = compra
        if (esperando != null) {
            Column(
                Modifier.fillMaxSize().padding(top = arriba + 8.dp, start = 24.dp, end = 24.dp, bottom = abajo + 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth()) { EncabezadoEntrada("Tu entrada", I.atras, 28f) { compra = null } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    Puntitos()
                    MensajeCentro("Esperando la confirmación de Mercado Pago…", "Cuando se acredite el pago, tu entrada aparece acá y en Mis pases.")
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Cta("Ya pagué, revisar", { alcance.launch { if (!revisar()) avisos.error("Todavía no nos llegó el pago. Esperá un ratito y probá de nuevo.") } }, Modifier.fillMaxWidth(), cargando = revisando)
                    T("Volver a abrir Mercado Pago", 15f, 700, C.violetaTexto, Modifier.align(Alignment.CenterHorizontally).tocable { abrirPestana(ctx, esperando.link) }.padding(8.dp))
                }
            }
            return@Pantalla
        }

        val conSena = plan.cobro == "sena" && plan.senaPct != null
        val sena = conSena && modo == "sena"
        val precio = (plan.precioCentavos ?: 0).toLong()
        val porPersona = Plata.desglose(precio, "sena", plan.senaPct, 1, plan.cargoCentavos.takeIf { it > 0 })
        val d = Plata.desglose(precio, if (sena) "sena" else "entrada", plan.senaPct, q, plan.cargoCentavos.takeIf { it > 0 })
        val maximo = if (plan.aceptaGrupos) minOf(10, plan.faltan.coerceAtLeast(1)) else 1

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = arriba + 8.dp, start = 20.dp, end = 20.dp, bottom = 150.dp + abajo),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EncabezadoEntrada("Tu entrada", I.atras, 28f) { ir.atras() }
            Row(
                Modifier.fillMaxWidth().gel(24.dp, Gels.neutra).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Foto(plan.foto, Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(plan.titulo, 17f, 700, C.carbon, alto = 1.15f)
                    T(lugaresCuandoDonde(plan.empiezaEn, lugarDe(plan)), 13f, 600, C.texto2)
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
                    BotonCantidad("−", q > 1) { q-- }
                    T("$q", 24f, 800, C.carbon, Modifier.widthIn(min = 20.dp), alto = 1f, alinear = TextAlign.Center)
                    BotonCantidad("+", q < maximo) { q++ }
                }
            }
            // Plan con seña: «Pagar todo» o «Señar N%», las dos opciones del prototipo (coModes).
            if (conSena) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("entrada", "Pagar todo", "Entrada completa ahora"),
                    Triple("sena", "Señar ${plan.senaPct}%", "Reservás con ${Plata.pesos(porPersona.alLugar)}; el resto en el lugar"),
                ).forEach { (k, l, sub) ->
                    val on = modo == k
                    Column(
                        Modifier.weight(1f).fillMaxHeight().toque(alBajar = 3.dp) { modo = k }
                            .gel(20.dp, if (on) gelModoOn else gelModoOff)
                            // El anillo violeta de 2,5 (inset 0 0 0 2.5px #7456F1) de la opción elegida.
                            .then(if (on) Modifier.border(2.5.dp, C.violeta, RoundedCornerShape(20.dp)) else Modifier)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        T(l, 15f, 800, C.carbon)
                        T(sub, 12f, 600, C.texto2, alto = 1.35f)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().gel(22.dp, Gels.neutra).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilaDesglose((if (sena) "Seña ${plan.senaPct}%" else "Entrada") + " × $q", Plata.pesos(d.alLugar))
                FilaDesglose("Cargo por servicio", Plata.pesos(d.cargo))
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.lavanda))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    T(if (sena) "Pagás hoy" else "Total", 15f, 800, C.carbon, Modifier.weight(1f))
                    T(Plata.pesos(d.total), 28f, 800, C.carbon, alto = 1f)
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
                    T(Plata.pesos(d.saldo), 20f, 800, Color(0xFF7A2416), alto = 1f)
                }
            }
            T("Si el plan se cancela, te devolvemos todo. Para el lugar, vender en Caigo es gratis.", 12f, 600, p.texto2, alto = 1.5f)
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(0f to p.fondo.copy(alpha = 0f), .25f to p.fondo))
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 30.dp + abajo),
        ) {
            Cta((if (sena) "Señar " else "Pagar ") + Plata.pesos(d.total), { pagar(plan) }, Modifier.fillMaxWidth(), gel = gelPagar, alto = 62.dp, radio = 22.dp, tam = 18f, color = C.carbon, cargando = pagando)
        }
    }
}

@Composable
private fun BotonCantidad(signo: String, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).graphicsLayer { alpha = if (habilitado) 1f else .45f }
            .toque(habilitado, onClick = onClick).background(C.lavanda, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) { T(signo, 22f, 800, C.violetaTexto, alto = 1f) }
}

@Composable
private fun FilaDesglose(izq: String, der: String) {
    Row(Modifier.fillMaxWidth()) {
        T(izq, 14f, 600, C.texto2, Modifier.weight(1f))
        T(der, 14f, 800, C.carbon)
    }
}

/**
 * "Tenés tu entrada" (prototipo checkout_done + Pases NFC 1c): la tarjeta con el código, el
 * beneficio del check-in, agregar a Google Wallet y volver al plan.
 */
@Composable
fun Entrada(ir: Ir, entradaId: String, planId: String) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val pases = rememberCarga(entradaId) { c.api.pases() }
    val plan = rememberCarga(planId) { if (planId.isBlank()) null else runCatching { c.api.plan(planId) }.getOrNull() }
    // Google Wallet: si la API dice `sin_wallet`, el botón no se muestra.
    val wallet = rememberCarga(entradaId) {
        try { c.api.walletEntrada(entradaId).link } catch (e: ErrorApi) { if (e.codigo == "sin_wallet") "" else throw e }
    }
    val entrada = pases.dato?.entradas?.firstOrNull { it.id == entradaId }
    var cancelando by remember { mutableStateOf(false) }

    Pantalla { arriba, abajo ->
        if (entrada == null) {
            Column(Modifier.fillMaxSize().padding(top = arriba + 8.dp, start = 20.dp, end = 20.dp)) {
                EncabezadoEntrada("Tu entrada", I.atras, 28f) { ir.atras() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (pases.cargando) Puntitos()
                    else MensajeCentro("Tu entrada todavía no está", pases.error ?: "Cuando Mercado Pago confirme el pago, aparece acá y en Mis pases.")
                }
            }
            return@Pantalla
        }
        val titulo = remember { Animatable(20f) }
        LaunchedEffect(Unit) { titulo.animateTo(0f, tween(400)) }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = arriba + 20.dp, start = 24.dp, end = 24.dp, bottom = abajo + 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            T(
                "Tenés tu entrada", 36f, 800, modifier = Modifier.graphicsLayer { translationY = titulo.value * density; alpha = 1f - titulo.value / 20f },
                alto = 1f, espaciado = px(-1.2f, 36f), alinear = TextAlign.Center,
            )
            T("También la tenés siempre en Mis pases.", 15f, 600, LocalPaleta.current.texto2, alinear = TextAlign.Center)
            LugaresPop {
                Column(Modifier.fillMaxWidth().gel(30.dp, gelTicket).clip(RoundedCornerShape(30.dp)).tocable { ir.qrEntrada(entrada.id) }) {
                    Foto(plan.dato?.foto, Modifier.fillMaxWidth().height(150.dp))
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(entrada.titulo, 22f, 700, C.blancoCalido, alto = 1.1f)
                        T(lugaresCuandoDonde(entrada.empiezaEn, entrada.lugar), 13f, 600, Color(0xFFD3CEE2))
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
                            T(entrada.codigo, 24f, 800, C.lima, alto = 1f, espaciado = px(1f, 24f))
                        }
                        val etiqueta = (if (entrada.cantidad == 1) "1 entrada" else "${entrada.cantidad} entradas") + if (entrada.modo == "sena") " · señada" else ""
                        Box(Modifier.background(rgba(255, 255, 255, .12f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            T(etiqueta, 13f, 800, C.blancoCalido)
                        }
                    }
                }
            }
            if (entrada.beneficio.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().background(C.lavanda, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icono(I.regalo, 24.dp, C.violetaTexto, 2.2f)
                    T("Hacé check-in al llegar y desbloqueás: ${entrada.beneficio}", 13f, 700, C.carbon, Modifier.weight(1f))
                }
            }
            LugaresBoton("Mostrar QR de entrada", LugaresEstiloBoton.Blanco, { ir.qrEntrada(entrada.id) }, Modifier.fillMaxWidth(), icono = I.qr, radio = 20.dp, peso = 800)
            val link = wallet.dato
            if (!link.isNullOrBlank()) {
                LugaresBoton("Agregar a Google Wallet", LugaresEstiloBoton.Negro, { abrirPestana(ctx, link) }, Modifier.fillMaxWidth(), icono = lugaresIconoWallet, radio = 20.dp, peso = 800)
            } else if (wallet.error != null) {
                LaunchedEffect(wallet.error) { avisos.error(wallet.error!!) }
            }
            Cta("Ver el plan", { ir.plan(entrada.planId) }, Modifier.fillMaxWidth(), gel = Gels.violeta, radio = 22.dp, color = C.blanco)
            // Cancelar según la política del plan (el texto de peligro de «Borrar mi cuenta»).
            if (!entrada.validada && entrada.estado != "cancelada") T(
                "Cancelar mi entrada", 15f, 700, C.coral2,
                Modifier.tocable { cancelando = true }.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
        HojaCancelarEntrada(cancelando, { cancelando = false }, entrada.id, plan.dato?.cancelacion.orEmpty()) { pases.recargar() }
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
        val v = vista.context.lugaresActividad()?.window
        val antes = v?.attributes?.screenBrightness
        v?.attributes = v?.attributes?.apply { screenBrightness = 1f }
        onDispose { v?.attributes = v?.attributes?.apply { screenBrightness = antes ?: -1f } }
    }
    Pantalla { arriba, abajo ->
        Column(
            Modifier.fillMaxSize().padding(top = arriba + 8.dp, start = 22.dp, end = 22.dp, bottom = abajo + 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth()) { EncabezadoEntrada("Tu entrada", I.cruz, 26f) { ir.atras() } }
            if (entrada == null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (pases.cargando) Puntitos() else MensajeCentro("No encontramos tu entrada", pases.error ?: "Fijate en Mis pases.")
                }
                return@Column
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(entrada.titulo, 20f, 700, alto = 1.15f)
                T(lugaresCuandoDonde(entrada.empiezaEn, entrada.lugar), 13f, 600, LocalPaleta.current.texto2)
            }
            Box(
                Modifier.padding(top = 10.dp).gel(
                    30.dp,
                    Gel(vertical(C.blanco, C.blanco), listOf(interna(0f, 0f, C.lima, spread = -3f), externa(5f, 0f, C.limaBase), externa(30f, 50f, rgba(80, 60, 160, .45f), spread = -24f))),
                ).padding(20.dp),
            ) { CodigoQr(entrada.codigo, Modifier.size(225.dp)) }
            T(entrada.codigo, 28f, 800, alto = 1f, espaciado = px(1.5f, 28f))
            T("Mostralo en la puerta. Subí el brillo si está oscuro.", 14f, 600, LocalPaleta.current.texto2, alto = 1.5f, alinear = TextAlign.Center)
        }
    }
}


/** Lo que dice la política de cancelación del plan (flex | mod | strict), con las reglas del contrato §2. */
internal fun textoPolitica(cancelacion: String): String = when (cancelacion) {
    "flex" -> "Hasta 24 h antes te devolvemos todo, menos el cargo por servicio."
    "mod" -> "Hasta 72 h antes te devolvemos todo, menos el cargo por servicio. Después, lo que pagaste no se devuelve."
    "strict" -> "Este plan no devuelve las entradas si cancelás."
    else -> "Te devolvemos según la política del lugar, menos el cargo por servicio."
}

/** Hoja para cancelar mi entrada (DELETE /v1/entradas/{id}); con `strict` sólo explica que no se devuelve. */
@Composable
internal fun BoxScope.HojaCancelarEntrada(abierta: Boolean, cerrar: () -> Unit, entradaId: String, cancelacion: String, alCancelar: () -> Unit) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var enviando by remember { mutableStateOf(false) }
    val sinDevolucion = cancelacion == "strict"
    MiosHoja(abierta, { if (!enviando) cerrar() }, if (sinDevolucion) "Esta entrada no se devuelve" else "¿Cancelar tu entrada?") {
        T(textoPolitica(cancelacion), 15f, 400, p.texto2, alto = 1.5f)
        MiosGrupo {
            if (!sinDevolucion) {
                Box(
                    Modifier.fillMaxWidth().tocable {
                        if (enviando) return@tocable
                        enviando = true
                        alcance.launch {
                            try {
                                val r = c.api.cancelarEntrada(entradaId)
                                avisos.ok(
                                    if (r.reembolsoCentavos > 0) "Cancelaste tu entrada. Te devolvemos ${Plata.pesos(r.reembolsoCentavos)}."
                                    else "Cancelaste tu entrada. Esta vez no hay devolución.",
                                )
                                cerrar(); alCancelar()
                            } catch (e: Throwable) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                avisos.error(mensajeDe(e))
                            } finally {
                                enviando = false
                            }
                        }
                    }.padding(horizontal = 18.dp, vertical = 16.dp),
                ) { if (enviando) Puntitos(C.coral2) else T("Cancelar mi entrada", 15f, 700, C.coral2) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (p.oscuro) p.separador else C.lavanda))
            }
            Box(Modifier.fillMaxWidth().tocable { if (!enviando) cerrar() }.padding(horizontal = 18.dp, vertical = 16.dp)) {
                T(if (sinDevolucion) "Entendido" else "Volver", 15f, 700)
            }
        }
    }
}
