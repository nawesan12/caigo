package ar.com.caigo.app.pantallas.planes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.nucleo.FotoPlan
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.Problema
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.pantallas.mios.MiosHoja
import ar.com.caigo.app.pantallas.mios.MiosVacio
import ar.com.caigo.app.pantallas.mios.miosPrimer
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Encabezado
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.jpegDe
import ar.com.caigo.app.piezas.jpegDeFoto
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Fotos del plan (informe del 18/11, B4): las suben y las ven sólo quienes fueron; los links vencen a la
 * hora. Sin pantalla en el handoff: el encabezado de Solicitudes, la grilla con el radio de las tarjetas,
 * el Cta lima y la hoja que sube para cada foto. Igual que FotosPlan.swift en el iPhone.
 */
@Composable
fun FotosDelPlan(ir: Ir, planId: String) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val p = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var plan by remember { mutableStateOf<Plan?>(null) }
    var fotos by remember { mutableStateOf<List<FotoPlan>?>(null) }
    var cerrado by remember { mutableStateOf<String?>(null) }
    var abierta by remember { mutableStateOf<FotoPlan?>(null) }
    var subiendo by remember { mutableStateOf(false) }
    var vuelta by remember { mutableIntStateOf(0) }
    // Un error que no es «no fuiste»: no es «no hay fotos».
    var error by remember { mutableStateOf<String?>(null) }
    var confirmarBorrar by remember { mutableStateOf(false) }

    LaunchedEffect(vuelta) {
        plan = runCatching { c.api.plan(planId) }.getOrNull()
        try {
            fotos = c.api.fotosPlan(planId).fotos
            cerrado = null
            error = null
        } catch (e: ErrorApi) {
            if (e.estado == 404) cerrado = e.detalle else if (fotos == null) error = e.detalle else avisos.error(e.detalle)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (fotos == null) error = mensajeDe(e) else avisos.error(mensajeDe(e))
        }
    }

    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        subiendo = true
        alcance.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    // Achicada al decodificar y derecha según el EXIF (jpegDeFoto, Camara.kt).
                    jpegDeFoto(ctx, uri) ?: throw ErrorApi(0, Problema(detail = "No pudimos leer esa foto. Probá con otra."))
                }
                c.api.subirFotoPlan(planId, jpeg)
                avisos.ok("Foto subida.")
                vuelta++
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            } finally {
                subiendo = false
            }
        }
    }

    fun accion(f: FotoPlan, motivo: String = "inapropiada") {
        alcance.launch {
            try {
                if (f.mia) {
                    c.api.borrarFotoPlan(planId, f.id)
                    avisos.ok("Foto borrada.")
                } else {
                    c.api.reportarFotoPlan(planId, f.id, motivo)
                    avisos.ok("Listo, ya no se ve. La revisamos.")
                }
                confirmarBorrar = false
                abierta = null
                vuelta++
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            }
        }
    }

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp)) {
            Encabezado("Fotos del plan", plan?.let { "${it.titulo} · ${Fecha.cuando(it.empiezaEn)}" }, { ir.atras() }, Modifier.padding(horizontal = 16.dp))
            val lista = fotos
            when {
                cerrado != null -> MiosVacio("Sólo para quienes fueron", cerrado!!, "Volver", { ir.atras() }, Modifier.padding(28.dp))
                error != null && fotos == null -> MiosVacio("No pudimos traer las fotos", error!!, "Probar de nuevo", {
                    error = null
                    vuelta++
                }, Modifier.padding(28.dp))
                lista != null -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp + abajo),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    T("Sólo las ven quienes fueron. Subí las que estén bien para todos los que salen. Se borran a los 6\u00A0meses.", 13f, 400, p.texto2, alto = 1.4f)
                    if (lista.isEmpty()) T("Todavía no hay fotos. Subí la primera.", 15f, 600, p.texto2, Modifier.padding(vertical = 24.dp))
                    lista.chunked(3).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            fila.forEach { f ->
                                Foto(f.url, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).tocable { abierta = f; confirmarBorrar = false }
                                    .semantics { contentDescription = "Foto de ${miosPrimer(f.autor.nombre)}"; role = Role.Button })
                            }
                            repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Cta(if (subiendo) "Subiendo…" else "Subir una foto", {
                        if (!subiendo) elegir.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, Modifier.fillMaxWidth(), alto = 56.dp, color = C.carbon, cargando = subiendo)
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Puntitos(p.acento) }
            }
        }
        val f = abierta
        MiosHoja(f != null, { abierta = null }, f?.let { "De ${miosPrimer(it.autor.nombre)}" } ?: "Foto") {
            if (f != null) {
                Foto(f.url, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)))
                val enlace = if (p.oscuro) C.lima else C.violetaTexto
                if (f.mia) {
                    if (confirmarBorrar) {
                        T("¿Borrar tu foto?", 15f, 700, p.texto, Modifier.align(Alignment.CenterHorizontally))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Cta(
                                "Ahora no", { confirmarBorrar = false }, Modifier.weight(1f), gel = ar.com.caigo.app.tema.Gels.blanco,
                                alto = 50.dp, radio = 16.dp, tam = 15f, color = C.carbon,
                            )
                            Cta("Sí, borrarla", { accion(f) }, Modifier.weight(1f), alto = 50.dp, radio = 16.dp, tam = 15f, color = C.carbon)
                        }
                    } else {
                        T("Borrar mi foto", 14f, 700, enlace, Modifier.align(Alignment.CenterHorizontally).tocable { confirmarBorrar = true }.padding(vertical = 12.dp))
                    }
                } else {
                    // «Salgo yo» la oculta al instante; «No va» la manda a revisar (también se oculta).
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Cta(
                            "Salgo yo y no quiero", { accion(f, "salgo_yo") }, Modifier.weight(1f), gel = ar.com.caigo.app.tema.Gels.blanco,
                            alto = 50.dp, radio = 16.dp, tam = 14f, color = C.carbon,
                        )
                        Cta(
                            "No va", { accion(f, "inapropiada") }, Modifier.weight(1f), gel = ar.com.caigo.app.tema.Gels.blanco,
                            alto = 50.dp, radio = 16.dp, tam = 14f, color = C.carbon,
                        )
                    }
                }
            }
        }
    }
}
