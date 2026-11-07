package ar.com.caigo.app.pantallas.ingreso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
 * Género y diversidad, con los chips y el interruptor del handoff (06b «Quiénes» y 06c). Lo usan el
 * alta, editar perfil y la hoja que aparece ante `falta_genero`. No se muestra a nadie.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CamposGenero(genero: String, diversidad: Boolean, onGenero: (String) -> Unit, onDiversidad: (Boolean) -> Unit) {
    val p = LocalPaleta.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            generos.forEach { (k, l) -> Chip(l, genero == k, { onGenero(if (genero == k) "" else k) }) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                T("Me identifico como parte de la diversidad", 14f, 700, p.texto)
                T("Para los planes «solo diversidades».", 12f, 400, p.texto2)
            }
            Interruptor(diversidad, onDiversidad)
        }
    }
}

/**
 * La hoja que pide el género cuando la API dice `falta_genero` (plan «solo mujeres» o «solo
 * diversidades»). Responde a [Contenedor.preguntarGenero]: true si se guardó, y quien preguntó reintenta.
 */
@Composable
fun BoxScope.HojaGenero(c: Contenedor) {
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val pregunta by c.preguntaGenero.collectAsState()
    var genero by remember { mutableStateOf("") }
    var diversidad by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    LaunchedEffect(pregunta) {
        if (pregunta != null) {
            genero = c.yo.value?.genero.orEmpty()
            diversidad = c.yo.value?.diversidad == true
        }
    }
    MiosHoja(pregunta != null, { if (!guardando) pregunta?.complete(false) }, "¿Cómo te identificás?") {
        T(
            "Este plan es para un grupo en particular. Lo usamos sólo para eso y no se lo mostramos a nadie.",
            15f, 400, LocalPaleta.current.texto2, alto = 1.5f,
        )
        CamposGenero(genero, diversidad, { genero = it }, { diversidad = it })
        Cta("Guardar y seguir", {
            alcance.launch {
                guardando = true
                try {
                    c.yo.value = c.api.actualizarYo(CambiosPerfil(genero = genero, diversidad = diversidad))
                    pregunta?.complete(true)
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    avisos.error(mensajeDe(e))
                } finally {
                    guardando = false
                }
            }
        }, Modifier.fillMaxWidth(), habilitado = genero.isNotBlank() || diversidad, cargando = guardando)
    }
}

/**
 * Corre [f] y, si la API responde `falta_genero`, pregunta el género y prueba una vez más. Los demás
 * errores pasan tal cual.
 */
suspend fun <R> conGenero(c: Contenedor, f: suspend () -> R): R = try {
    f()
} catch (e: ar.com.caigo.app.nucleo.ErrorApi) {
    if (e.codigo == "falta_genero" && c.preguntarGenero()) f() else throw e
}
