package ar.com.caigo.app.pantallas.gente

import ar.com.caigo.app.piezas.colorDeCategoria
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.nucleo.Encantas
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Puntitos
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
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.launch

/** Botones redondos gel del mazo. */
internal val genteGelPaso = Gel(
    vertical(C.blanco, Color(0xFFF3F0FA)),
    listOf(interna(2f, 0f, C.blanco), interna(-4f, 0f, rgba(255, 120, 104, .18f)), externa(4f, 0f, Color(0xFFE4D6DA)), externa(14f, 22f, rgba(80, 60, 160, .35f), spread = -10f)),
)
internal val genteGelCorazon = Gel(
    vertical(0f to C.coral0, .35f to Color(0xFFFF8A7B), .6f to C.coral, 1f to Color(0xFFE85544)),
    listOf(interna(3f, 2f, rgba(255, 255, 255, .6f)), interna(-5f, 0f, rgba(160, 40, 30, .3f)), externa(4f, 0f, Color(0xFFC9432F)), externa(16f, 26f, rgba(255, 120, 104, .8f), spread = -10f)),
)
internal val genteGelVioletaRedondo = Gel(
    Gels.violeta.fondo,
    listOf(interna(3f, 2f, rgba(255, 255, 255, .45f)), interna(-4f, 0f, rgba(40, 20, 140, .35f)), externa(4f, 0f, C.violetaBase), externa(14f, 22f, rgba(116, 86, 241, .75f), spread = -10f)),
)

/** Los «me encanta» de Citas: 1 por día sin Plus, 5 con Plus (la lógica vive en `nucleo/Logica.kt · Encantas`). */
internal object GenteEncantas {
    fun quedan(hoy: Int, limite: Int): Int = Encantas.quedan(hoy, limite)

    /** Lo que dice la API al pasarse (gente.Reaccionar), para no hacer volar la carta en vano. */
    fun aviso(plus: Boolean, limite: Int): String = Encantas.aviso(plus, limite)
}

/** El toast después de reaccionar (prendo | interesa | encanta). */
internal fun genteMensaje(nombre: String, accion: String, modo: String, mutuo: Boolean): String = when {
    mutuo -> "¡Es mutuo con $nombre! Ya pueden hablar."
    accion == "encanta" -> "Le mandaste un «me encanta» a $nombre. Si es mutuo, se abre el chat."
    modo == "citas" -> "Le avisamos a $nombre que te interesa. Si es mutuo, se abre el chat."
    else -> "$nombre se suma a tu gente. Le van a llegar tus próximos planes."
}

/** Botón redondo gel del mazo (pasar, sumar, me encanta, invitar). */
@Composable
internal fun GenteBotonRedondo(tam: Dp, gel: Gel, etiqueta: String, modifier: Modifier = Modifier, accion: () -> Unit, contenido: @Composable () -> Unit) {
    Box(
        modifier.size(tam).toque(onClick = accion).gel(tam / 2, gel)
            .semantics { contentDescription = etiqueta; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { contenido() }
}

/** Elegir a cuál de mis planes invito a la persona (armado con las tarjetas del handoff). */
@Composable
internal fun GenteHojaInvitar(ir: Ir, personaId: String, nombre: String, cerrar: () -> Unit, alInvitar: (Plan) -> Unit) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var mios by remember { mutableStateOf<List<Plan>?>(null) }
    LaunchedEffect(Unit) { mios = runCatching { c.api.mios("organizo").planes }.getOrNull() ?: emptyList() }
    ar.com.caigo.app.app.OcultarTabBar(true)
    // Siempre con la paleta clara (como `.environment(\.paleta, .clara)` de iOS).
    CompositionLocalProvider(LocalPaleta provides PaletaClara) {
        Box(Modifier.fillMaxSize().zIndex(30f).background(rgba(32, 29, 43, .45f)).tocable(cerrar)) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().tocable { }
                    .background(C.blancoCalido, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 34.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                T("¿A qué plan invitás a $nombre?", 22f, 700, C.carbon, alto = 1.1f, espaciado = px(-.4f, 22f))
                val ps = mios
                when {
                    ps == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Puntitos() }
                    ps.isEmpty() -> {
                        T("Todavía no organizás nada. Armá un plan y después invitás.", 15f, 400, C.texto2, alto = 1.5f)
                        Cta("Armá un plan", { cerrar(); ir.crear() }, Modifier.fillMaxWidth(), alto = 56.dp, tam = 16f)
                    }
                    else -> LazyColumn(
                        Modifier.heightIn(max = (minOf(ps.size, 4) * 84).dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(ps, key = { it.id }) { p ->
                            Row(
                                Modifier.fillMaxWidth().toque(alBajar = 2.dp) {
                                    alcance.launch {
                                        try {
                                            c.api.invitar(p.id, personaId)
                                            cerrar()
                                            alInvitar(p)
                                        } catch (e: Throwable) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            avisos.error(mensajeDe(e))
                                        }
                                    }
                                }.gel(20.dp, Gels.neutra).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Foto(p.foto, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)), colorDeCategoria(p.categoria))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    T(p.titulo, 15f, 700, C.carbon, unRenglon = true)
                                    T(Fecha.cuando(p.empiezaEn) + " · " + Cupo.badge(p.faltan), 12f, 500, C.texto2, unRenglon = true)
                                }
                                Icono(I.adelante, 18.dp, C.violeta, 2.4f)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Píldora de vidrio sobre foto (chips de la carta). */
@Composable
internal fun GentePildora(modifier: Modifier = Modifier, contenido: @Composable RowScope.() -> Unit) {
    Row(
        modifier.gel(12.dp, vertical(rgba(255, 255, 255, .16f), rgba(255, 255, 255, .16f)), listOf(interna(1f, 0f, rgba(255, 255, 255, .2f))))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
        content = contenido,
    )
}
