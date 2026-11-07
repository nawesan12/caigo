package ar.com.caigo.app.pantallas.pagos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Plata
import ar.com.caigo.app.nucleo.ResultadoTap
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.PaletaClara
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical

// En el iPhone la usan la app y el App Clip de Caigo Tap; en Android, sólo la app.

private val fondoAdentro = radial(.5f, .3f, 1f, .6f, 0f to C.lima0, .55f to C.lima, 1f to Color(0xFF9FD13B))
private val verdeOscuro = Color(0xFF2E4A00)

/** "Adentro" (Pases NFC 1e / prototipo `tapdone`): check-in hecho, beneficio y visitas. */
@Composable
fun Adentro(ir: Ir) {
    val c = LocalApp.current
    val r = c.ultimoTap
    PagosBarraEstado(clara = false)
    CompositionLocalProvider(LocalPaleta provides PaletaClara) {
        Pantalla(oscuro = false, brillo = fondoAdentro) { arriba, abajo ->
            if (r == null) {
                LaunchedEffect(Unit) { ir.atras() }
            } else {
                Contenido(ir, r, Modifier.padding(top = arriba, bottom = abajo))
            }
        }
    }
}

@Composable
private fun Contenido(ir: Ir, r: ResultadoTap, modifier: Modifier) {
    val c = LocalApp.current
    val nombre = c.yo.value?.nombre?.split(" ")?.firstOrNull { it.isNotEmpty() }.orEmpty()
    val plan = r.plan
    val socio = r.socio
    val texto = when {
        plan?.repetido == true -> "Ya habías hecho check-in en ${r.lugar}."
        nombre.isEmpty() -> "Check-in hecho en ${r.lugar}. Que lo disfrutes."
        else -> "Check-in hecho en ${r.lugar}. Que lo disfrutes, $nombre."
    }
    Column(
        modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PagosPop {
                Box(
                    Modifier.size(120.dp).gel(
                        40.dp,
                        Gel(vertical(C.carbon, C.carbon), listOf(externa(8f, 0f, C.oscuro7), externa(30f, 40f, rgba(36, 33, 45, .6f), spread = -16f))),
                    ),
                    contentAlignment = Alignment.Center,
                ) { Icono(I.check, 60.dp, C.lima, 3f) }
            }
            T("Adentro", 58f, 800, C.carbon, alto = 1f, espaciado = px(-2.4f, 58f), unRenglon = true)
            T(texto, 16f, 700, verdeOscuro, alto = 1.45f, alinear = TextAlign.Center)
            val b = plan?.beneficio
            if (!b.isNullOrEmpty()) TarjetaBeneficio("$b desbloqueado", "Mostralo en la barra")
            if (socio != null && socio.gano && socio.beneficio.isNotEmpty()) {
                TarjetaBeneficio(socio.beneficio, "Lo ganaste con tu pase de socio · mostralo en la barra")
            }
            val saldo = plan?.saldoCentavos ?: 0
            if (saldo > 0) {
                Box(Modifier.fillMaxWidth().background(rgba(36, 33, 45, .08f), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    T("Seña paga · en el lugar pagás ${Plata.pesos(saldo.toLong())}", 14f, 800, C.carbon)
                }
            }
        }
        if (socio != null && socio.sumo) {
            T("+1 visita en tu pase de ${r.lugar} (${socio.visitas} de ${socio.meta})", 13f, 700, verdeOscuro, alinear = TextAlign.Center)
        }
        PagosBoton(
            if (plan != null) "Volver al plan" else "Listo", PagosEstiloBoton.Grafito,
            alto = 60.dp, radio = 22.dp, tam = 17f, peso = 800,
        ) {
            val id = plan?.id.orEmpty()
            if (id.isNotEmpty()) ir.plan(id) else ir.atras()
        }
    }
}

@Composable
private fun TarjetaBeneficio(titulo: String, sub: String) {
    Row(
        Modifier.fillMaxWidth().gel(22.dp, Gel(vertical(C.blanco, C.blanco), listOf(externa(4f, 0f, C.limaBase)))).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp).background(C.carbon, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icono(I.regalo, 24.dp, C.lima, 2.2f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(titulo, 15f, 800, C.carbon)
            T(sub, 12f, 600, C.texto2)
        }
    }
}
