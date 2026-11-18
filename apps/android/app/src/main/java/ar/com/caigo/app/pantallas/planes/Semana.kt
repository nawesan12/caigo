package ar.com.caigo.app.pantallas.planes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.nucleo.Semana
import ar.com.caigo.app.pantallas.mios.MiosVacio
import ar.com.caigo.app.pantallas.mios.miosOscura
import ar.com.caigo.app.piezas.Encabezado
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.px

/*
 * «Esta semana en <ciudad>» (informe del 18/11, B2): la tarjeta de los lunes arriba de Ahora y la
 * pantalla con lo que queda de la semana, día por día. Sin pantalla en el handoff: el contador oscuro
 * del detalle, los chips de días de Explorar y las tarjetas de Ahora. Igual que Semana.swift en el iPhone.
 */

/** La tarjeta arriba de Ahora (de lunes a miércoles, con 5 planes o más). */
@Composable
internal fun TarjetaSemana(s: Semana, ir: Ir) {
    Row(
        Modifier.fillMaxWidth().toque { ir.semana() }.gel(24.dp, miosOscura).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("Esta semana en ${s.ciudad}", 22f, 800, C.lima, alto = 1.1f, espaciado = px(-.6f, 22f))
            T(s.resumen, 14f, 600, C.blancoCalido)
        }
        Icono(I.adelante, 22.dp, C.lima, 3f)
    }
}

/** La pantalla «Esta semana». */
@Composable
fun VistaSemana(ir: Ir) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val carga = rememberCarga("semana") { c.api.semana() }
    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp)) {
            Encabezado("Esta semana", carga.dato?.ciudad, { ir.atras() }, Modifier.padding(horizontal = 16.dp))
            val s = carga.dato
            when {
                s != null -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp + abajo),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth().gel(24.dp, miosOscura).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Sólo las cifras que dicen algo (un cero grande hace parecer vacía la ciudad).
                        s.cifras.forEach { (n, texto) -> Numero(n, texto, Modifier.weight(1f)) }
                    }
                    Dias(s)
                    if (s.planes.isEmpty()) {
                        MiosVacio("Esta semana armá el tuyo", "Si publicás algo, sos lo primero que ven.", "Armá un plan", {
                            if (c.conDni(ir, "crear")) ir.crear()
                        })
                    } else {
                        T("Lo mejor de la semana", 20f, 700)
                        s.planes.forEach { TarjetaPlan(it, ir) }
                    }
                }
                carga.error != null -> MiosVacio("No pudimos traer la semana", carga.error!!, "Probar de nuevo", { carga.recargar() }, Modifier.padding(28.dp))
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Puntitos(p.acento) }
            }
        }
    }
}

@Composable
private fun Numero(n: Int, texto: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T("$n", 34f, 800, C.lima, alto = 1f, espaciado = px(-1f, 34f))
        T(texto, 12f, 600, C.blancoCalido)
    }
}

@Composable
private fun Dias(s: Semana) {
    val p = LocalPaleta.current
    val maximo = maxOf(1, s.dias.maxOfOrNull { it.cantidad } ?: 1)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        s.dias.forEach { d ->
            val fecha = Fecha.parse(d.dia + "T12:00:00-03:00")
            // Cada día es un elemento: «lunes 17: 3 planes».
            val etiqueta = "${fecha?.let { Fecha.diaLargo(it).lowercase() }.orEmpty()} ${fecha?.dayOfMonth ?: ""}: " +
                if (d.cantidad == 1) "1 plan" else "${d.cantidad} planes"
            Column(
                Modifier.weight(1f).alpha(if (d.cantidad == 0) .55f else 1f)
                    .clearAndSetSemantics { contentDescription = etiqueta }
                    .gel(14.dp, if (p.oscuro) Gels.chipOscuro else Gels.chipClaro).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                T(fecha?.let { Fecha.diaCorto(it) }.orEmpty(), 11f, 700, p.texto2)
                T("${fecha?.dayOfMonth ?: ""}", 16f, 800)
                Box(Modifier.width(8.dp).height(36.dp).background(C.lavanda, RoundedCornerShape(4.dp)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(8.dp).fillMaxHeight(d.cantidad.toFloat() / maximo).background(C.violeta, RoundedCornerShape(4.dp)))
                }
                T("${d.cantidad}", 12f, 800)
            }
        }
    }
}
