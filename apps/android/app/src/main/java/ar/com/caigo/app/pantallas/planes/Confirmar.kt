package ar.com.caigo.app.pantallas.planes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.pantallas.mios.MiosGrupo
import ar.com.caigo.app.pantallas.mios.MiosHoja
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import kotlinx.coroutines.launch

/**
 * Confirmación antes de algo que no tiene vuelta (cancelar un plan, sacar a alguien, cancelar una
 * entrada). Misma hoja que «¿Borrar tu cuenta?» del perfil: texto, la acción en coral y «Volver».
 *
 * Sin [accion], la hoja sólo explica (ej.: entrada sin reembolso) y ofrece «Volver».
 * [confirmar] hace lo que hay que hacer; si tira, se muestra el error y la hoja queda abierta.
 */
@Composable
internal fun BoxScope.HojaConfirmar(
    abierta: Boolean,
    titulo: String,
    cuerpo: String,
    accion: String?,
    cerrar: () -> Unit,
    confirmar: suspend () -> Unit,
) {
    val p = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf(false) }
    MiosHoja(abierta, { if (!ocupado) cerrar() }, titulo) {
        T(cuerpo, 15f, 400, p.texto2, alto = 1.5f)
        MiosGrupo {
            if (accion != null) {
                Box(
                    Modifier.fillMaxWidth().tocable {
                        if (ocupado) return@tocable
                        ocupado = true
                        alcance.launch {
                            try {
                                confirmar()
                                cerrar()
                            } catch (e: Throwable) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                avisos.error(mensajeDe(e))
                            }
                            ocupado = false
                        }
                    }.padding(horizontal = 18.dp, vertical = 16.dp),
                ) { if (ocupado) Puntitos(C.coral2) else T(accion, 15f, 700, C.coral2) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (p.oscuro) p.separador else C.lavanda))
            }
            Box(Modifier.fillMaxWidth().tocable { if (!ocupado) cerrar() }.padding(horizontal = 18.dp, vertical = 16.dp)) {
                T("Volver", 15f, 700)
            }
        }
    }
}
