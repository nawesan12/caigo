package ar.com.caigo.app.pantallas.ingreso

import ar.com.caigo.app.piezas.Verificado
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import ar.com.caigo.app.pantallas.mios.MiosHoja
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.rgba

/** "+5492235550142" → "+54 223 555 0142". */
private fun telefonoLindo(t: String): String {
    val d = t.removePrefix("+549").removePrefix("+54")
    return if (d.length == 10 && d.all(Char::isDigit)) "+54 ${d.substring(0, 3)} ${d.substring(3, 6)} ${d.substring(6)}" else t
}

/** Casilla de 36 px: lima con check si está hecho, o el número con borde coral punteado. */
@Composable
internal fun ingresoCasillaPaso(hecho: Boolean, numero: Int) {
    if (hecho) {
        Box(Modifier.size(36.dp).background(C.lima, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icono(I.check, 16.dp, C.carbon, 3.4f)
        }
    } else {
        Box(
            Modifier.size(36.dp).drawBehind {
                val g = 2.dp.toPx()
                drawRoundRect(
                    C.coral, topLeft = androidx.compose.ui.geometry.Offset(g / 2, g / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - g, size.height - g),
                    cornerRadius = CornerRadius(11.dp.toPx()),
                    style = Stroke(g, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            },
            contentAlignment = Alignment.Center,
        ) { T(numero.toString(), 16f, 800, C.coral) }
    }
}

private val gelTarjetaOscura = Gel(Gels.oscuroSel.fondo, listOf(externa(16f, 28f, rgba(36, 33, 45, .6f), spread = -14f)), grano = true)

/** Proto · "Tu confianza en Caigo": teléfono, selfie y DNI con el estado real de la API. */
@Composable
fun Verificacion(ir: Ir) {
    val c = LocalApp.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val avisos = ar.com.caigo.app.piezas.LocalAvisos.current
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    val estado = rememberCarga {
        if (c.yo.value == null) c.recargarYo()
        c.api.verificacion()
    }
    // El .task de iOS corre otra vez al volver a la pantalla (después de la selfie o el DNI).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { estado.recargar() }
    val v = estado.dato
    val yo by c.yo.collectAsState()
    var contacto by remember { mutableStateOf("") }
    var editandoContacto by remember { mutableStateOf(false) }
    var guardandoContacto by remember { mutableStateOf(false) }
    var compartiendo by remember { mutableStateOf(false) }

    // «Compartir mi plan en vivo»: el link de /vivo del próximo plan en el que estoy adentro.
    fun compartirEnVivo() {
        if (compartiendo) return
        compartiendo = true
        alcance.launch {
            try {
                val ahora = java.time.ZonedDateTime.now(ar.com.caigo.app.nucleo.ZonaAR)
                val plan = c.api.mios("me_sume").planes
                    .filter { p ->
                        val t = if (p.miEstado == "aceptada") ar.com.caigo.app.nucleo.Fecha.parse(p.empiezaEn) else null
                        t != null && t.plusSeconds(p.duracionMin.toLong() * 60).isAfter(ahora)
                    }
                    .minByOrNull { it.empiezaEn }
                if (plan == null) {
                    avisos.error("Cuando te acepten en un plan, lo compartís desde acá.")
                } else {
                    ar.com.caigo.app.piezas.compartir(ctx, c.api.enVivo(plan.id).link)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                avisos.error(ar.com.caigo.app.nucleo.mensajeDe(e))
            } finally {
                compartiendo = false
            }
        }
    }

    ingresoClaro { Pantalla(oscuro = false) { arriba, abajo ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = arriba + 8.dp, bottom = 40.dp + abajo),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ingresoBarraArriba(I.cruz, { ir.atras() }, { ingresoEncriptado() })
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("Tu confianza en Caigo", 34f, 800, C.carbon, alto = 1.02f, espaciado = px(-1f, 34f))
                T("Así la gente sabe que sos vos de verdad.", 15f, 400, C.texto2, alto = 1.5f)
            }
            if (v == null) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    if (estado.error != null) T(estado.error.orEmpty(), 15f, 600, C.texto2, alinear = TextAlign.Center)
                    else Puntitos()
                }
                return@Column
            }
            Column(Modifier.fillMaxWidth().gel(26.dp, gelTarjetaOscura).padding(6.dp)) {
                fila(v.telefono.isNotEmpty(), 1, "Teléfono", if (v.telefono.isEmpty()) "Te mandamos un código por mail" else telefonoLindo(v.telefono),
                    primera = true) { if (v.telefono.isEmpty()) ir.telefono() }
                fila(v.selfie, 2, "Selfie con prueba de vida", "Confirma que sos una persona real") { if (!v.selfie) ir.selfie(volver = true) }
                fila(
                    v.dni == "aprobada", 3, "DNI · badge Verificado",
                    when (v.dni) {
                        "pendiente" -> "Ya estamos revisando tu DNI."
                        "rechazada" -> v.dniMotivo.ifEmpty { "Habilita planes «solo verificados»" }
                        else -> "Habilita planes «solo verificados»"
                    },
                ) { if (v.dni == "ninguna" || v.dni == "rechazada") ir.dni() }
            }
            if (v.dni == "ninguna" || v.dni == "rechazada") {
                ingresoCtaGrande("Verificar con DNI · 2 minutos", { ir.dni() })
            }
            T("Herramientas para salir tranqui", 20f, 700, C.carbon, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.fillMaxWidth().gel(22.dp, Gels.neutra)) {
                herramienta(I.pin, C.violeta, "Compartir mi plan en vivo", "Tu contacto ve dónde estás hasta que termina", true,
                    cargando = compartiendo) { compartirEnVivo() }
                herramienta(I.usuario, C.violeta, "Contacto de confianza",
                    yo?.contactoConfianza.orEmpty().ifEmpty { "Sumá a alguien que sepa dónde estás" }, true) {
                    contacto = yo?.contactoConfianza.orEmpty()
                    editandoContacto = true
                }
                herramienta(I.bloqueo, C.coral2, "Bloqueos y reportes", "Los revisa una persona del equipo, no un bot", false) { ir.bloqueos() }
            }
            T(
                "Nunca mostramos tu DNI ni tu dirección. Solo el badge.", 12f, 400, C.texto2, alto = 1.5f,
                alinear = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        // Contacto de confianza (PATCH /v1/yo `contactoConfianza`): teléfono o mail.
        MiosHoja(editandoContacto, { if (!guardandoContacto) editandoContacto = false }, "Contacto de confianza") {
            T("A quién le mandás tu plan en vivo. Un teléfono o un mail, con un nombre si querés.", 15f, 400, C.texto2, alto = 1.45f)
            ar.com.caigo.app.pantallas.mios.MiosCampo(contacto, { contacto = it }, placeholder = "Mamá · +54 223 444 1290", maximo = 80)
            ar.com.caigo.app.piezas.Cta("Guardar", {
                guardandoContacto = true
                alcance.launch {
                    try {
                        c.yo.value = c.api.actualizarYo(ar.com.caigo.app.nucleo.CambiosPerfil(contactoConfianza = contacto.trim()))
                        editandoContacto = false
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        avisos.error(ar.com.caigo.app.nucleo.mensajeDe(e))
                    } finally {
                        guardandoContacto = false
                    }
                }
            }, Modifier.fillMaxWidth(), cargando = guardandoContacto)
        }
    } }
}

@Composable
private fun fila(hecho: Boolean, n: Int, titulo: String, sub: String, primera: Boolean = false, onClick: () -> Unit) {
    Column {
        if (!primera) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3A3550)))
        Row(
            Modifier.fillMaxWidth().tocable(onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ingresoCasillaPaso(hecho, n)
            Column(Modifier.weight(1f)) {
                T(titulo, 15f, 800, C.blancoCalido)
                T(sub, 12f, 400, Color(0xFFC9C4DA))
            }
        }
    }
}

@Composable
private fun herramienta(icono: String, color: Color, titulo: String, sub: String?, borde: Boolean, cargando: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().tocable(onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icono(icono, 20.dp, color, 2.2f)
            Column(Modifier.weight(1f)) {
                T(titulo, 14f, 800, C.carbon)
                if (sub != null) T(sub, 12f, 400, C.texto2)
            }
            if (cargando) Puntitos(C.violeta)
        }
        if (borde) Box(Modifier.fillMaxWidth().height(1.dp).background(C.lavanda))
    }
}
