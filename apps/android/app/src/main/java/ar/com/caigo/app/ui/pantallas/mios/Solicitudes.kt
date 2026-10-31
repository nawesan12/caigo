package ar.com.caigo.app.ui.pantallas.mios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.datos.Plan
import ar.com.caigo.app.datos.Solicitud
import ar.com.caigo.app.datos.mensajeDe
import ar.com.caigo.app.logica.Cupo
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Avatar
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.Encabezado
import ar.com.caigo.app.ui.piezas.Fecha
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.compartir
import ar.com.caigo.app.ui.piezas.rememberCarga
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.interna
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.radial
import ar.com.caigo.app.ui.tema.rgba
import kotlinx.coroutines.launch

/** 07 · Solicitudes de un plan propio (y el vacío 13c). */
@Composable
fun Solicitudes(ir: Ir, planId: String) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val plan = rememberCarga(planId) { c.api.plan(planId) }
    val sols = rememberCarga(planId, "sol") { c.api.solicitudes(planId).solicitudes.filter { it.estado == "pendiente" } }
    val respondidas = remember { mutableStateListOf<String>() }
    var ocupado by remember { mutableStateOf<String?>(null) }

    fun responder(s: Solicitud, aceptar: Boolean) {
        ocupado = s.id
        alcance.launch {
            try {
                c.api.responder(s.id, aceptar)
                respondidas += s.id
                if (aceptar) avisos.ok("${miosPrimer(s.persona.nombre)} ya está adentro.")
                plan.recargar()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            } finally {
                ocupado = null
            }
        }
    }

    Pantalla { arriba, abajo ->
        val pl = plan.dato
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp)) {
            Encabezado(
                "Solicitudes",
                pl?.let { listOf(it.titulo, Fecha.cuando(it.empiezaEn)).filter { s -> s.isNotBlank() }.joinToString(" · ") },
                { ir.atras() }, Modifier.padding(horizontal = 16.dp),
            )
            val lista = sols.dato?.filter { it.id !in respondidas }
            when {
                pl == null || lista == null -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    val err = plan.error ?: sols.error
                    if (err != null) MiosVacio("No pudimos cargar las solicitudes", err, "Probar de nuevo", { plan.recargar(); sols.recargar() })
                    else Puntitos()
                }
                lista.isEmpty() -> Vacio(pl) { compartir(ctx, "${pl.titulo} · ${BuildConfig.SITIO.trimEnd('/')}/p/${pl.slug}") }
                else -> {
                    // La primera solicitud que alcanza para llenar el cupo va destacada ("Completan").
                    val completa = lista.firstOrNull { pl.faltan > 0 && it.cantidad >= pl.faltan }?.id
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = abajo + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Resumen(pl, lista.size) }
                        items(lista, key = { it.id }) { s ->
                            Tarjeta(s, s.id == completa, ocupado == s.id, { responder(s, true) }, { responder(s, false) }) { ir.persona(s.persona.id, "solicitud") }
                        }
                    }
                }
            }
        }
    }
}

/** Contador oscuro: "Faltan 2 / Somos 8 de 10" y "3 pedidos". */
@Composable
private fun Resumen(pl: Plan, pedidos: Int) {
    Row(
        Modifier.fillMaxWidth().background(C.carbon, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(Cupo.badge(pl.faltan), 34f, 800, if (Cupo.completo(pl.faltan)) C.lima else C.coral, alto = 1f)
            T(Cupo.somos(pl.somos, pl.total), 13f, 600, C.blancoCalido)
        }
        Box(Modifier.background(Color(0xFF3A3550), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            T(if (pedidos == 1) "1 pedido" else "$pedidos pedidos", 13f, 700, C.blancoCalido)
        }
    }
}

@Composable
private fun Tarjeta(s: Solicitud, completan: Boolean, ocupado: Boolean, aceptar: () -> Unit, rechazar: () -> Unit, verPersona: () -> Unit) {
    val p = LocalPaleta.current
    val per = s.persona
    Column(
        Modifier.fillMaxWidth()
            .then(
                if (completan) Modifier.background(p.tarjetaPlano, RoundedCornerShape(24.dp)).border(2.dp, C.lima, RoundedCornerShape(24.dp))
                else Modifier.gel(24.dp, miosGelTarjeta()),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().tocable(verPersona), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Avatares solapados 14 px: el ancho del Row ya descuenta el solape (el offset no lo haría).
            Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                Avatar(per.nombre, per.foto, 44.dp, borde = if (s.cantidad > 1) 2.dp else 0.dp, tamTexto = 17f)
                if (s.cantidad > 1) {
                    Avatar("+", null, 44.dp, texto = "+${s.cantidad - 1}", fondo = C.lima to C.carbon, tamTexto = 15f)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val nombre = if (s.cantidad > 1) "${miosPrimer(per.nombre)} · somos ${s.cantidad}" else listOfNotNull(miosPrimer(per.nombre), per.edad?.toString()).joinToString(", ")
                    T(nombre, 15f, 700, modifier = Modifier.weight(1f, fill = false), unRenglon = true)
                    if (per.verificada) MiosVerificado(15.dp)
                }
                T(if (per.enMiGente) miosAsistencia(per.asistencia, "está en tu gente") else miosAsistencia(per.asistencia), 12f, 400, p.texto2)
            }
            if (completan) {
                Box(Modifier.gel(10.dp, Gels.limaChico).padding(horizontal = 9.dp, vertical = 5.dp)) { T("Completan", 11f, 800, C.carbon) }
            }
        }
        if (s.mensaje.isNotBlank()) {
            Box(Modifier.fillMaxWidth().gel(14.dp, if (p.oscuro) Gels.chipOscuro else Gels.chipClaro).padding(horizontal = 12.dp, vertical = 10.dp)) {
                T(s.mensaje, 14f, 400, alto = 1.4f)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(46.dp).toque(!ocupado, onClick = rechazar)
                    .border(1.5.dp, if (p.oscuro) C.oscuro1 else C.pista, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) { T("Ahora no", 15f, 700, p.texto2) }
            Cta(
                if (s.cantidad > 1) "Aceptar a los ${s.cantidad}" else "Aceptar", aceptar,
                Modifier.weight(1.4f), alto = 46.dp, radio = 15.dp, tam = 15f, cargando = ocupado, color = C.carbon,
            )
        }
    }
}

/** 13c · Nadie pidió sumarse todavía. */
@Composable
private fun Vacio(pl: Plan, compartirLink: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Column(
            Modifier.rotate(4f)
                .gel(28.dp, radial(0f, 0f, 1.2f, .9f, 0f to C.oscuro1, .62f to C.carbon),
                    listOf(interna(1f, 0f, rgba(255, 255, 255, .08f)), externa(22f, 36f, rgba(36, 33, 45, .6f), spread = -14f)), grano = true)
                .padding(horizontal = 26.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(Cupo.badge(pl.faltan), 56f, 800, if (Cupo.completo(pl.faltan)) C.lima else C.coral, alto = 1f, espaciado = px(-2f, 56f))
            T(Cupo.somos(pl.somos, pl.total), 13f, 600, C.blancoCalido)
        }
        T("Nadie pidió sumarse todavía", 30f, 700, alto = 1.08f, espaciado = px(-.6f, 30f), alinear = TextAlign.Center)
        T("Pasá el link por el grupo. Siempre hay alguien que se prende.", 15f, 400, LocalPaleta.current.texto2, alto = 1.5f, alinear = TextAlign.Center)
        Cta(
            "Compartir link del plan", compartirLink, Modifier.fillMaxWidth().padding(top = 8.dp),
            alto = 58.dp, tam = 16f, icono = I.compartir, iconoTrazo = 2.4f, iconoTam = 18.dp,
        )
    }
}
