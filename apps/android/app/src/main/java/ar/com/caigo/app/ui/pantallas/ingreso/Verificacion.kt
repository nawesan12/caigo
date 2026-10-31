package ar.com.caigo.app.ui.pantallas.ingreso

import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import ar.com.caigo.app.ui.pantallas.mios.MiosHoja
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
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.rememberCarga
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.rgba

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
    val estado = rememberCarga { c.api.verificacion() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { estado.recargar() }
    val v = estado.dato
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val avisos = ar.com.caigo.app.ui.piezas.LocalAvisos.current
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    var contacto by remember { mutableStateOf(c.yo.value?.contactoConfianza ?: c.sesion.contacto) }
    var editandoContacto by remember { mutableStateOf(false) }
    var verBloqueos by remember { mutableStateOf(false) }
    var compartiendo by remember { mutableStateOf(false) }

    /** «Compartir mi plan en vivo»: el plan de hoy en el que estás, con el link para tu contacto. */
    fun compartirEnVivo() {
        if (compartiendo) return
        compartiendo = true
        alcance.launch {
            try {
                val ahora = java.time.ZonedDateTime.now(ar.com.caigo.app.ui.piezas.ZonaAR)
                val plan = c.api.mios("me_sume").planes
                    .filter { it.miEstado == "aceptada" && !it.cancelado }
                    .filter { p ->
                        val t = ar.com.caigo.app.ui.piezas.Fecha.parse(p.empiezaEn) ?: return@filter false
                        t.plusMinutes(p.duracionMin.toLong()).isAfter(ahora) && t.toLocalDate() <= ahora.toLocalDate().plusDays(1)
                    }
                    .minByOrNull { it.empiezaEn }
                if (plan == null) {
                    avisos.error("Cuando estés en un plan de hoy, lo compartís desde acá.")
                } else {
                    val link = c.api.enVivo(plan.id).link
                    ar.com.caigo.app.ui.piezas.compartirWhatsApp(ctx, "Estoy en «${plan.titulo}». Seguilo en vivo hasta que termine: $link")
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(ar.com.caigo.app.datos.mensajeDe(e))
            } finally {
                compartiendo = false
            }
        }
    }

    Pantalla { arriba, abajo ->
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
            val dniHecho = v.dni == "aprobada"
            Column(Modifier.fillMaxWidth().gel(26.dp, gelTarjetaOscura).padding(6.dp)) {
                fila(v.telefono.isNotBlank(), 1, "Teléfono", if (v.telefono.isNotBlank()) telefonoLindo(v.telefono) else "Te mandamos un código por SMS",
                    primera = true) { if (v.telefono.isBlank()) ir.telefono() }
                fila(v.selfie, 2, "Selfie con prueba de vida", "Confirma que sos una persona real") { if (!v.selfie) ir.selfie(volver = true) }
                fila(
                    dniHecho, 3, "DNI · badge Verificado",
                    when (v.dni) {
                        "pendiente" -> "Ya estamos revisando tu DNI."
                        "rechazada" -> v.dniMotivo.ifBlank { "Habilita planes «solo verificados»" }
                        else -> "Habilita planes «solo verificados»"
                    },
                ) {}
            }
            if (v.dni == "ninguna" || v.dni == "rechazada") {
                ingresoCtaGrande("Verificar con DNI · 2 minutos", { ir.dni() })
            }
            T("Herramientas para salir tranqui", 20f, 700, C.carbon, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.fillMaxWidth().gel(22.dp, Gels.neutra)) {
                herramienta(I.pin, C.violeta, "Compartir mi plan en vivo", "Tu contacto ve dónde estás hasta que termina", true) { compartirEnVivo() }
                herramienta(I.usuario, C.violeta, "Contacto de confianza", contacto.ifBlank { "Sumá a alguien que sepa dónde estás" }, true) { editandoContacto = true }
                herramienta(I.bloqueo, C.coral2, "Bloqueos y reportes", "Los revisa una persona del equipo, no un bot", false) { verBloqueos = true }
            }
            T(
                "Nunca mostramos tu DNI ni tu dirección. Solo el badge.", 12f, 400, C.texto2, alto = 1.5f,
                alinear = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        HojaContacto(editandoContacto, contacto, { editandoContacto = false }) { contacto = it }
        HojaBloqueos(verBloqueos) { verBloqueos = false }
    }
}

/** Contacto de confianza (PATCH /v1/yo `contactoConfianza`): un teléfono o un mail. Con las piezas de editar perfil. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.HojaContacto(abierta: Boolean, actual: String, cerrar: () -> Unit, alGuardar: (String) -> Unit) {
    val c = LocalApp.current
    val avisos = ar.com.caigo.app.ui.piezas.LocalAvisos.current
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    var texto by remember(abierta) { mutableStateOf(actual) }
    var guardando by remember { mutableStateOf(false) }
    MiosHoja(abierta, { if (!guardando) cerrar() }, "Contacto de confianza") {
        T("Alguien que reciba tu plan en vivo cuando lo compartas. Un teléfono o un mail.", 15f, 400, C.texto2, alto = 1.5f)
        ar.com.caigo.app.ui.pantallas.mios.MiosCampo(texto, { texto = it }, placeholder = "Mamá · 223 444 1290", maximo = 80)
        ar.com.caigo.app.ui.piezas.Cta("Guardar", {
            alcance.launch {
                guardando = true
                try {
                    val t = texto.trim()
                    c.api.actualizarYo(ar.com.caigo.app.datos.CambiosPerfil(contactoConfianza = t))
                    c.sesion.guardarContacto(t)
                    alGuardar(t)
                    avisos.ok(if (t.isBlank()) "Sacamos tu contacto de confianza." else "Listo. Guardamos tu contacto de confianza.")
                    cerrar()
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    avisos.error(ar.com.caigo.app.datos.mensajeDe(e))
                } finally {
                    guardando = false
                }
            }
        }, Modifier.fillMaxWidth(), cargando = guardando)
    }
}

/** Bloqueos (GET /v1/bloqueos, DELETE /v1/bloqueos/{id}), con las filas de la tarjeta de opciones. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.HojaBloqueos(abierta: Boolean, cerrar: () -> Unit) {
    val c = LocalApp.current
    val avisos = ar.com.caigo.app.ui.piezas.LocalAvisos.current
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    val lista = ar.com.caigo.app.ui.piezas.rememberCarga(abierta) { if (abierta) c.api.bloqueos().bloqueos else emptyList() }
    MiosHoja(abierta, cerrar, "Gente que bloqueaste") {
        val l = lista.dato
        when {
            l == null && lista.error != null -> T(lista.error.orEmpty(), 15f, 500, C.texto2, alto = 1.5f)
            l == null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Puntitos() }
            l.isEmpty() -> T("No bloqueaste a nadie. Si alguien te incomoda, lo bloqueás o lo reportás desde su perfil o el chat.", 15f, 400, C.texto2, alto = 1.5f)
            else -> ar.com.caigo.app.ui.pantallas.mios.MiosGrupo {
                l.forEachIndexed { i, b ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ar.com.caigo.app.ui.piezas.Avatar(b.nombre, b.foto, 36.dp, borde = 0.dp, tamTexto = 14f)
                        T(b.nombre, 15f, 700, C.carbon, Modifier.weight(1f), unRenglon = true)
                        T("Desbloquear", 14f, 700, C.violeta, Modifier.tocable {
                            alcance.launch {
                                runCatching { c.api.desbloquear(b.id) }
                                    .onSuccess { avisos.ok("Desbloqueaste a ${b.nombre}."); lista.recargar() }
                                    .onFailure { avisos.error(ar.com.caigo.app.datos.mensajeDe(it)) }
                            }
                        })
                    }
                    if (i < l.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(C.lavanda))
                }
            }
        }
    }
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
private fun herramienta(icono: String, color: Color, titulo: String, sub: String?, borde: Boolean, onClick: () -> Unit) {
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
        }
        if (borde) Box(Modifier.fillMaxWidth().height(1.dp).background(C.lavanda))
    }
}
