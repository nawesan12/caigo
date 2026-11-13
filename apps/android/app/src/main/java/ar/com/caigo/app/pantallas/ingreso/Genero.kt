package ar.com.caigo.app.pantallas.ingreso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Contenedor
import ar.com.caigo.app.nucleo.CambiosPerfil
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.pantallas.mios.MiosHoja
import ar.com.caigo.app.piezas.Chip
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Interruptor
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import kotlinx.coroutines.launch

/** Género autodeclarado (contrato §1): la clave de la API y cómo se muestra. */
val generos = listOf("mujer" to "Mujer", "varon" to "Varón", "no_binario" to "No binario")

/**
 * Género autodeclarado (contrato §1): chips Mujer / Varón / No binario. No se muestra a nadie; sólo
 * sirve para los planes «Solo mujeres». La diversidad no se pregunta acá ni se guarda: se confirma al
 * sumarse a un plan «Solo diversidades» ([HojaDiversidad], AAIP 1.2).
 * No está en el handoff: se arma con los chips del alta.
 */
@Composable
fun ElegirGenero(genero: String, onGenero: (String) -> Unit) {
    val p = LocalPaleta.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            generos.forEach { (k, l) -> Chip(l, genero == k, { onGenero(if (genero == k) "" else k) }) }
        }
        T("No se lo mostramos a nadie. Sólo sirve para sumarte a planes de mujeres.", 12f, 400, p.texto2, alto = 1.4f)
    }
}

/** El nombre viejo (lo usa Editar perfil): es [ElegirGenero]. */
@Composable
fun CamposGenero(genero: String, onGenero: (String) -> Unit) = ElegirGenero(genero, onGenero)

/**
 * La hoja que pide el género cuando la API dice `falta_genero` (plan «Solo mujeres»). Responde a
 * [Contenedor.preguntarGenero]: true si se guardó, y quien preguntó reintenta.
 */
@Composable
fun BoxScope.HojaGenero(c: Contenedor) {
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val pregunta by c.preguntaGenero.collectAsState()
    var genero by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    LaunchedEffect(pregunta) {
        if (pregunta != null) genero = c.yo.value?.genero.orEmpty()
    }
    MiosHoja(pregunta != null, { if (!guardando) pregunta?.complete(false) }, "Antes de sumarte") {
        T("Este plan es sólo para mujeres. Contanos con qué género te identificás.", 15f, 400, LocalPaleta.current.texto, alto = 1.45f)
        ElegirGenero(genero) { genero = it }
        Cta("Guardar y seguir", {
            alcance.launch {
                guardando = true
                try {
                    c.yo.value = c.api.actualizarYo(CambiosPerfil(genero = genero))
                    pregunta?.complete(true)
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    avisos.error(mensajeDe(e))
                } finally {
                    guardando = false
                }
            }
        }, Modifier.fillMaxWidth(), habilitado = genero.isNotEmpty(), cargando = guardando)
    }
}

/**
 * La hoja que aparece cuando la API dice `confirmar_diversidad` (plan «Solo diversidades»): la
 * persona lo confirma (el interruptor arranca apagado) y se reintenta el pedido con
 * `diversidad: true`. Responde a [Contenedor.confirmarDiversidad]. No se guarda en ningún lado.
 */
@Composable
fun BoxScope.HojaDiversidad(c: Contenedor) {
    val p = LocalPaleta.current
    val pregunta by c.preguntaDiversidad.collectAsState()
    var soy by remember { mutableStateOf(false) }
    LaunchedEffect(pregunta) {
        if (pregunta != null) soy = false
    }
    MiosHoja(pregunta != null, { pregunta?.complete(false) }, "Antes de sumarte") {
        T("Este plan es sólo para la diversidad LGBTIQ+.", 15f, 400, p.texto, alto = 1.45f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Soy parte de la diversidad LGBTIQ+", 14f, 600, p.texto, Modifier.weight(1f))
            Interruptor(soy, { soy = it })
        }
        T("No lo guardamos: sólo vale para sumarte a este plan.", 12f, 400, p.texto2, alto = 1.4f)
        Cta("Sumarme", { pregunta?.complete(true) }, Modifier.fillMaxWidth(), habilitado = soy)
    }
}

/**
 * Corre [f] y, si la API responde `falta_genero`, pregunta el género y prueba una vez más; si
 * responde `confirmar_diversidad`, pide la confirmación y prueba una vez más con `diversidad = true`
 * (vale sólo para ese pedido). Los demás errores pasan tal cual.
 */
suspend fun <R> conGenero(c: Contenedor, f: suspend (diversidad: Boolean) -> R): R = try {
    f(false)
} catch (e: ar.com.caigo.app.nucleo.ErrorApi) {
    when {
        e.codigo == "falta_genero" && c.preguntarGenero() -> f(false)
        e.codigo == "confirmar_diversidad" && c.confirmarDiversidad() -> f(true)
        else -> throw e
    }
}
