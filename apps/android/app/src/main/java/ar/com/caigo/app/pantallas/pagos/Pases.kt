package ar.com.caigo.app.pantallas.pagos

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.EntradaMia
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.PaseSocio
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.MensajeCentro
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.rgba
import kotlinx.coroutines.launch

/** Fondo del pase de socio: violeta con brillo arriba a la derecha y grano. */
private val gelSocio = Gel(radial(1f, 0f, 1.2f, .8f, 0f to C.violeta2, .6f to Color(0xFF5A3DD8), 1f to Color(0xFF3B2A9E)), emptyList(), grano = true)

private val numeros = listOf("", "Una", "Dos", "Tres", "Cuatro", "Cinco", "Seis", "Siete", "Ocho", "Nueve", "Diez")

/** "Dos visitas más y tenés: Taller gratis" (o el beneficio ganado, si hay para canjear). */
internal fun pagosTextoSocio(s: PaseSocio): String {
    if (s.pendientes > 0) return "Ya ganaste: ${s.beneficio}. Mostralo en el lugar."
    val faltan = maxOf(0, s.meta - s.visitas)
    if (faltan == 0) return s.beneficio
    val n = if (faltan < numeros.size) numeros[faltan] else "$faltan"
    return "$n ${if (faltan == 1) "visita" else "visitas"} más y tenés: ${s.beneficio}"
}

/** Mis pases (Pases NFC 1f): las entradas pagadas y los pases de socio de los lugares. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pases(ir: Ir) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val p = LocalPaleta.current
    val pases = rememberCarga { c.api.pases() }
    var bajando by remember { mutableStateOf<String?>(null) }
    // Google Wallet: si la API responde `sin_wallet`, se dejan de ofrecer (PagosWallet.disponible del iPhone).
    var conWallet by remember { mutableStateOf(true) }

    fun agregar(clave: String, bajar: suspend () -> String) {
        if (bajando != null) return
        alcance.launch {
            bajando = clave
            try {
                PagosWallet.agregar(ctx, bajar)
            } catch (e: ErrorApi) {
                if (e.codigo == "sin_wallet") conWallet = false
                avisos.error(e.detalle)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            } finally {
                bajando = null
            }
        }
    }

    Pantalla { arriba, abajo ->
        PullToRefreshBox(
            isRefreshing = pases.cargando && pases.dato != null,
            onRefresh = { pases.recargar() },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(top = arriba + 6.dp, start = 18.dp, end = 18.dp, bottom = abajo + 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotonIcono(I.atras, { ir.atras() })
                    T("Mis pases", 36f, 800, alto = 1f, espaciado = px(-1.3f, 36f), unRenglon = true)
                }
                val d = pases.dato
                when {
                    d != null && d.entradas.isEmpty() && d.socios.isEmpty() -> {
                        MensajeCentro(
                            "Todavía no tenés pases",
                            "Cuando compres una entrada o sumes visitas en un lugar con Caigo Tap, aparecen acá.",
                            Modifier.padding(top = 60.dp, bottom = 10.dp),
                        )
                        Cta("Ver planes cerca", { ir.principal() }, Modifier.fillMaxWidth())
                    }
                    d != null -> {
                        if (d.entradas.isNotEmpty()) {
                            T("ENTRADAS", 12f, 800, p.texto2, espaciado = px(1.2f, 12f))
                            d.entradas.forEach { e ->
                                FilaEntrada(e, conWallet, bajando == e.id, { ir.entrada(e.id, e.planId) }) {
                                    agregar(e.id) { c.api.walletEntrada(e.id).link }
                                }
                            }
                        }
                        if (d.socios.isNotEmpty()) {
                            T("PASES DE SOCIO", 12f, 800, p.texto2, Modifier.padding(top = 6.dp), espaciado = px(1.2f, 12f))
                            d.socios.forEach { s ->
                                Socio(s, conWallet, bajando == "socio:" + s.lugarId) {
                                    if (conWallet) agregar("socio:" + s.lugarId) { c.api.walletSocio(s.lugarId).link }
                                }
                            }
                        }
                    }
                    pases.cargando -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Puntitos() }
                    else -> MensajeCentro("No pudimos cargar tus pases", pases.error.orEmpty(), Modifier.padding(top = 60.dp))
                }
                Row(
                    Modifier.fillMaxWidth().background(if (p.oscuro) p.superficie else C.lavanda, RoundedCornerShape(22.dp))
                        .tocable { ir.tap() }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icono(I.nfc, 22.dp, if (p.oscuro) C.violeta1 else C.violetaTexto, 2.4f)
                    T("Los lugares con Caigo Tap te dejan entrar apoyando el celu.", 13f, 700, p.texto, Modifier.weight(1f), alto = 1.4f)
                }
            }
        }
    }
}

@Composable
private fun FilaEntrada(e: EntradaMia, conWallet: Boolean, bajando: Boolean, abrir: () -> Unit, agregar: () -> Unit) {
    val c = LocalApp.current
    // La foto sale del plan (la entrada no la trae).
    val foto = rememberCarga(e.planId) { runCatching { c.api.plan(e.planId).foto }.getOrNull() }
    Row(
        Modifier.fillMaxWidth().tocable(abrir).gel(22.dp, Gels.neutra).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Foto(foto.dato, Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(e.titulo, 15f, 800, C.carbon, maxRenglones = 2)
            val detalle = if (e.modo == "sena") "Señada" else if (e.cantidad > 1) "${e.cantidad} entradas" else "1 entrada"
            T("${Fecha.cuando(e.empiezaEn)} · $detalle", 12f, 600, C.texto2, unRenglon = true)
        }
        if (conWallet) {
            Box(
                Modifier.toque(onClick = agregar).background(C.carbon, RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (bajando) Puntitos(C.blanco, 5.dp) else T("+ Wallet", 11f, 800, C.blanco, alinear = TextAlign.Center, unRenglon = true)
            }
        }
    }
}

@Composable
private fun Socio(s: PaseSocio, conWallet: Boolean, bajando: Boolean, tocar: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().gel(24.dp, gelSocio).tocable(tocar).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(s.lugar, 15f, 800, C.blanco, Modifier.weight(1f))
            T("${s.visitas} de ${s.meta}", 12f, 800, C.lima, unRenglon = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(minOf(maxOf(s.meta, 1), 20)) { i ->
                Box(
                    Modifier.weight(1f).height(9.dp)
                        .background(if (i < s.visitas) C.lima else rgba(255, 255, 255, .22f), RoundedCornerShape(50)),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(pagosTextoSocio(s), 12f, 600, Color(0xFFE4DDFF), Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            if (conWallet) {
                if (bajando) Puntitos(C.lima, 5.dp) else Icono(I.billetera, 18.dp, C.lima, 2f)
            }
        }
    }
}
