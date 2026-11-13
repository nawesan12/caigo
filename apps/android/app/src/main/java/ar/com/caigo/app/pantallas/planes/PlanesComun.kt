package ar.com.caigo.app.pantallas.planes

import ar.com.caigo.app.piezas.colorDeCategoria
import android.content.Context
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
 * al DNI (y vuelven); el resto se muestra tal cual en el toast. Devuelve el estado nuevo.
 *
 * `confirmar_mujer` y `confirmar_diversidad`: en Android los resuelve
 * [ar.com.caigo.app.pantallas.ingreso.conConfirmacion] (la hoja global «Antes de sumarte» de
 * Genero.kt pregunta y se reintenta una vez), en vez del `pedirConfirmacion` de iOS.
 */
internal suspend fun planesSumarse(
    c: Contenedor, ir: Ir, avisos: Avisos, plan: Plan, cantidad: Int, mensaje: String = "", vibrar: () -> Unit = {},
): String? = try {
    val e = ar.com.caigo.app.pantallas.ingreso.conConfirmacion(c) { conf -> c.api.sumarme(plan.id, cantidad, mensaje, conf) }.estado
    val quien = plan.organizador.nombre.ifEmpty { "quien organiza" }
    avisos.ok(if (e == "aceptada") "¡Estás adentro!" else "Le pediste a $quien sumarte.")
    vibrar()
    e
} catch (e: ErrorApi) {
    if (ar.com.caigo.app.nucleo.Generos.pide(e.codigo) != null) {
        // La hoja ya se cerró sin confirmar: como en iOS, no se muestra el error.
        null
    } else {
        avisos.error(e.detalle)
        if (e.codigo == "falta_selfie") ir.selfie(volver = true)
        if (e.codigo == "falta_dni") ir.dni(plan.id)
        null
    }
} catch (e: Throwable) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    avisos.error(mensajeDe(e))
    null
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
        Cupo.completo(plan.faltan) -> Unit
        else -> Row(
            Modifier
                .toque(!enviando) {
                    if (enviando) return@toque
                    if (esDeLugar(plan)) { ir.plan(plan.id); return@toque }
                    alcance.launch {
                        enviando = true
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
internal fun TarjetaPlan(plan: Plan, ir: Ir, modifier: Modifier = Modifier, altoFoto: Dp = 150.dp, alCambiar: (String) -> Unit = {}) {
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
            Foto(fotoDe(plan), Modifier.fillMaxSize(), tinte = colorDeCategoria(plan.categoria))
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipFoto(Categorias.nombre(plan.categoria), oscuro = false)
                ChipFoto(Fecha.cuando(plan.empiezaEn), oscuro = true)
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
                // Con "Adentro" / "Esperando…" el botón es más ancho: van sólo las cuentas.
                val conAvatares = plan.van.isNotEmpty() && plan.miEstado == "ninguno"
                if (conAvatares) Avatares(plan.van.map { it.nombre to it.foto }, total = plan.somos)
                T(Cupo.somos(plan.somos, plan.total), 13f, 600, if (p.oscuro) C.apagado2 else C.texto2, Modifier.weight(1f), unRenglon = true)
                Spacer(Modifier.width(4.dp))
                AccionTarjeta(plan, ir, alCambiar)
            }
        }
    }
}

/** Beneficio por check-in ("Café y medialuna de regalo"). */
@Composable
internal fun PlanesBeneficio(texto: String, desbloqueado: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .gel(
                22.dp,
                if (desbloqueado) Gel(vertical(C.lima0, C.lima), listOf(interna(2f, 0f, rgba(255, 255, 255, .7f)), externa(3f, 0f, C.limaBase)))
                else Gel(vertical(C.lavanda, C.lavanda), emptyList()),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).background(if (desbloqueado) C.carbon else C.blanco, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icono(I.regalo, 22.dp, if (desbloqueado) C.lima else C.violetaTexto, 2.2f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(texto, 14f, 800, C.carbon)
            T(
                if (desbloqueado) "Desbloqueado · mostralo en la caja" else "Se desbloquea cuando hacés check-in en el lugar", 12f, 600,
                if (desbloqueado) C.limaTexto else C.texto2,
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

