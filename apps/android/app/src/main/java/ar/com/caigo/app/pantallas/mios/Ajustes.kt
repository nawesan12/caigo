package ar.com.caigo.app.pantallas.mios

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.R
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.AjustesAvisos
import ar.com.caigo.app.nucleo.Bloqueado
import ar.com.caigo.app.nucleo.CambiosPerfil
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.piezas.Avatar
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Encabezado
import ar.com.caigo.app.piezas.Interruptor
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.MensajeCentro
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.gel
import kotlinx.coroutines.launch

// Pantallas que el handoff no dibuja (ajustes de avisos, bloqueos, actualizar la app): se arman con
// las piezas de Mis planes y Perfil (Encabezado, MiosGrupo, Interruptor, MiosVacio, Cta).

/** Ajustes de avisos push (contrato §3): planes cerca y planes de tu gente (PATCH /v1/yo `avisos`). */
@Composable
fun AjustesDeAvisos(ir: Ir) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val avisosApp = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var avisos by remember { mutableStateOf<AjustesAvisos?>(null) }

    LaunchedEffect(Unit) {
        if (c.yo.value == null) c.recargarYo()
        avisos = c.yo.value?.avisos ?: AjustesAvisos()
    }

    fun guardar(cambio: (AjustesAvisos) -> AjustesAvisos) {
        val antes = avisos ?: return
        val a = cambio(antes)
        avisos = a
        alcance.launch {
            try {
                c.yo.value = c.api.actualizarYo(CambiosPerfil(avisos = a))
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos = antes
                avisosApp.error(mensajeDe(e))
            }
        }
    }

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp)) {
            Encabezado("Avisos", null, { ir.atras() }, Modifier.padding(horizontal = 16.dp))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp + abajo),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val a = avisos
                if (a != null) {
                    MiosGrupo {
                        FilaAviso("Planes cerca", "Cuando alguien arma algo a menos de 5 km que arranca en un rato.", a.planesCerca, ultima = false) { v ->
                            guardar { it.copy(planesCerca = v) }
                        }
                        FilaAviso("De tu gente", "Cuando alguien de Mi gente publica un plan.", a.tuGente, ultima = true) { v ->
                            guardar { it.copy(tuGente = v) }
                        }
                    }
                    T("Los avisos de tus planes (solicitudes, chat, recordatorios y cambios) llegan siempre.", 12f, 400, p.texto2, alto = 1.5f)
                } else {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { Puntitos(p.acento) }
                }
            }
        }
    }
}

@Composable
private fun FilaAviso(titulo: String, sub: String, on: Boolean, ultima: Boolean, onCambio: (Boolean) -> Unit) {
    val p = LocalPaleta.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(titulo, 14f, 800)
                T(sub, 12f, 400, p.texto2, alto = 1.4f)
            }
            Interruptor(on, onCambio)
        }
        if (!ultima) Box(Modifier.fillMaxWidth().height(1.dp).background(if (p.oscuro) p.separador else C.lavanda))
    }
}

/** Bloqueos (contrato §4): la gente que bloqueaste, con «Desbloquear». */
@Composable
fun VistaBloqueos(ir: Ir) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<Bloqueado>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sacando by remember { mutableStateOf<String?>(null) }
    var vuelta by remember { mutableStateOf(0) }

    LaunchedEffect(vuelta) {
        try {
            lista = c.api.bloqueos().bloqueos
            error = null
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = mensajeDe(e)
        }
    }

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp)) {
            Encabezado("Bloqueos y reportes", null, { ir.atras() }, Modifier.padding(horizontal = 16.dp))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val l = lista
                val e = error
                when {
                    l != null && l.isEmpty() -> MiosVacio(
                        "No bloqueaste a nadie",
                        "Si alguien te incomoda, bloquealo o reportalo desde su perfil o el chat. Lo revisa una persona del equipo, no un bot.",
                        "Volver", { ir.atras() }, Modifier.padding(28.dp),
                    )
                    l != null -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp + abajo),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MiosGrupo {
                            l.forEachIndexed { i, b ->
                                Column {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Avatar(b.nombre, b.foto, 40.dp, borde = 0.dp, tamTexto = 16f)
                                        T(b.nombre.ifEmpty { "Alguien" }, 15f, 700, modifier = Modifier.weight(1f), unRenglon = true)
                                        Box(
                                            Modifier.toque {
                                                if (sacando != null) return@toque
                                                sacando = b.id
                                                alcance.launch {
                                                    try {
                                                        c.api.desbloquear(b.id)
                                                        lista = lista?.filter { it.id != b.id }
                                                        avisos.ok("Listo, desbloqueaste a ${if (b.nombre.isEmpty()) "esa persona" else miosPrimer(b.nombre)}.")
                                                    } catch (e: Throwable) {
                                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                                        avisos.error(mensajeDe(e))
                                                    }
                                                    sacando = null
                                                }
                                            }.gel(13.dp, if (p.oscuro) Gels.chipOscuro else Gels.chipClaro)
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (sacando == b.id) Puntitos(p.acento)
                                            else T("Desbloquear", 13f, 800, if (p.oscuro) C.lima else C.violetaTexto)
                                        }
                                    }
                                    if (i != l.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(if (p.oscuro) p.separador else C.lavanda))
                                }
                            }
                        }
                        T("No ven tus planes ni tu perfil, y vos no ves los suyos.", 12f, 400, p.texto2, alto = 1.5f)
                    }
                    e != null -> MiosVacio("No pudimos cargar tus bloqueos", e, "Probar de nuevo", { vuelta++ }, Modifier.padding(28.dp))
                    else -> Puntitos(p.acento)
                }
            }
        }
    }
}

/**
 * GET /v1/version dice que este build ya no anda: a pantalla completa, sin seguir. Reemplaza a
 * `piezas/Actualizar.kt` (el par de `ActualizarApp` de iOS vive en Ajustes.swift).
 */
@Composable
fun ActualizarApp() {
    val ctx = LocalContext.current
    Pantalla(Modifier.zIndex(50f).toque(alBajar = 0.dp) { }) { _, abajo ->
        Column(
            Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = 28.dp + abajo),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(120.dp))
            MensajeCentro("Hay una versión nueva", "Esta versión de Caigo ya no anda. Actualizala para seguir sumándote a planes.")
            Spacer(Modifier.weight(1f))
            Cta("Actualizar en Google Play", {
                val id = ctx.packageName
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
            }, Modifier.fillMaxWidth())
        }
    }
}
