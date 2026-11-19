package ar.com.caigo.app.pantallas.mios


import ar.com.caigo.app.piezas.asistencia
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.R
import ar.com.caigo.app.nucleo.CambiosPerfil
import ar.com.caigo.app.nucleo.Prompt
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.OcultarTabBar
import ar.com.caigo.app.app.Pestana
import ar.com.caigo.app.nucleo.EstadoPlus
import ar.com.caigo.app.nucleo.EstadoVerificacion
import ar.com.caigo.app.nucleo.PersonaCorta
import ar.com.caigo.app.piezas.Avatares
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import ar.com.caigo.app.app.altoTabBar
import ar.com.caigo.app.pantallas.ingreso.Legales
import ar.com.caigo.app.piezas.Avatar
import ar.com.caigo.app.piezas.Sistema
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.piezas.Chip
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Encabezado
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.jpegDe
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.radial
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que buscás (intenciones de la API → los nombres cortos del prototipo). */
private val intencionesCortas = linkedMapOf("planes" to "Planes", "gente" to "Gente nueva", "completar" to "Organizar", "citas" to "Citas")

/** Intereses del onboarding (LIKES del prototipo). */
private val intereses = listOf(
    "Fútbol 5", "Pádel", "Truco", "Previas", "After beach", "Surf", "Cine", "Asado", "Running",
    "Juegos de mesa", "Música en vivo", "Mate en la playa",
)

/** Las preguntas de los perfiles del prototipo. */
private val preguntas = listOf(
    "Mi domingo ideal", "Me sumo seguro si…", "Te gano en…", "Un plan que nunca falla",
    "Lo más espontáneo que hice", "Mi plan ideal", "Lo que más me gusta de Mar del Plata",
    "Mi talento oculto", "Estoy buscando", "Una cita ideal",
)

/** Tarjeta violeta de Caigo Plus del perfil del prototipo. */
private val gelPlus = Gel(
    radial(1f, 0f, 1.2f, 1f, 0f to Color(0xFFA38FFF), .5f to C.violeta, 1f to Color(0xFF5A3DD8)),
    listOf(interna(2f, 0f, rgba(255, 255, 255, .3f)), externa(4f, 0f, C.violetaBase), externa(16f, 28f, rgba(116, 86, 241, .7f), spread = -12f)),
    grano = true,
)

private val gelVerificacion = Gel(
    vertical(C.blanco, C.blanco),
    listOf(interna(1f, 0f, C.blanco), externa(3f, 0f, C.borde1), externa(12f, 24f, rgba(80, 60, 160, .25f), spread = -12f)),
)

// PARA EL NÚCLEO: `Gels.neutraOscura` (y `Gels.neutra(p)`) como en iOS; mientras, local.
private val neutraOscura = Gel(
    vertical(C.oscuro3, C.oscuro4),
    listOf(interna(1f, 0f, rgba(255, 255, 255, .08f)), externa(3f, 0f, C.oscuro7)),
)

/** 11 · Perfil propio (mockup 11 + perfil del prototipo: verificación, Plus, lo que buscás). Es `MiPerfil` en iOS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Perfil(ir: Ir) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val alcance = rememberCoroutineScope()
    val avisos = LocalAvisos.current
    val ctx = LocalContext.current
    val yo by c.yo.collectAsState()
    var opciones by remember { mutableStateOf(false) }
    var borrando by remember { mutableStateOf(false) }
    var borrandoAhora by remember { mutableStateOf(false) }
    // El mail que escribe la persona para confirmar la baja.
    var mailBaja by remember { mutableStateOf("") }
    // El mail escrito coincide con el de la cuenta. Si la API todavía no lo manda, alcanza con un mail válido.
    val mailBajaOk = mailBaja.trim().lowercase().let { escrito ->
        val cuenta = yo?.email.orEmpty().trim().lowercase()
        if (cuenta.isNotEmpty()) escrito == cuenta else escrito.contains("@") && escrito.contains(".")
    }
    var fallo by remember { mutableStateOf(false) }
    var gente by remember { mutableStateOf<List<PersonaCorta>>(emptyList()) }
    var verif by remember { mutableStateOf<EstadoVerificacion?>(null) }
    var plus by remember { mutableStateOf<EstadoPlus?>(null) }
    var refrescando by remember { mutableStateOf(false) }
    // «Traé a alguien» abierta, lo que trae la API y el código de quien me invitó.
    var trayendo by remember { mutableStateOf(false) }
    var referidos by remember { mutableStateOf<ar.com.caigo.app.nucleo.Referidos?>(null) }
    var codigo by remember { mutableStateOf("") }
    var usandoCodigo by remember { mutableStateOf(false) }

    suspend fun cargar() {
        fallo = c.recargarYo() == null
        coroutineScope {
            val g = async { runCatching { c.api.miGente().gente }.getOrNull() }
            val v = async { runCatching { c.api.verificacion() }.getOrNull() }
            val pl = async { runCatching { c.api.plus().estado }.getOrNull() }
            gente = g.await() ?: gente
            verif = v.await() ?: verif
            plus = pl.await() ?: plus
        }
    }
    LaunchedEffect(Unit) { cargar() }
    OcultarTabBar(opciones || borrando || trayendo)
    LaunchedEffect(trayendo) {
        if (!trayendo) return@LaunchedEffect
        try {
            referidos = c.api.referidos()
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            avisos.error(ar.com.caigo.app.nucleo.mensajeDe(e))
            trayendo = false
        }
    }

    // PATCH /v1/yo `ocultarInsignias`: se ve al toque; si la API falla, vuelve.
    fun ocultarInsignias(v: Boolean) {
        val antes = c.yo.value ?: return
        val l = antes.logros ?: return
        c.yo.value = antes.copy(logros = l.copy(ocultas = v))
        alcance.launch {
            try {
                c.yo.value = c.api.actualizarYo(ar.com.caigo.app.nucleo.CambiosPerfil(ocultarInsignias = v))
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                c.yo.value = antes
                avisos.error(ar.com.caigo.app.nucleo.mensajeDe(e))
            }
        }
    }

    fun usarCodigo() {
        val cod = codigo.trim()
        if (cod.isEmpty() || usandoCodigo) return
        usandoCodigo = true
        alcance.launch {
            try {
                c.api.usarCodigo(cod)
                codigo = ""
                avisos.ok("Listo.")
                referidos = runCatching { c.api.referidos() }.getOrNull() ?: referidos
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(ar.com.caigo.app.nucleo.mensajeDe(e))
            } finally {
                usandoCodigo = false
            }
        }
    }

    Pantalla { arriba, _ ->
        val y = yo
        if (y == null) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                if (fallo) MiosVacio("No pudimos cargar tu perfil", "No hay conexión. Revisá internet y probá de nuevo.", "Probar de nuevo", { alcance.launch { cargar() } })
                else Puntitos(p.acento)
            }
        } else {
            PullToRefreshBox(
                refrescando,
                { alcance.launch { refrescando = true; try { cargar() } finally { refrescando = false } } },
                Modifier.fillMaxSize(),
            ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = arriba + 16.dp, bottom = altoTabBar()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Cabecera: foto 112, nombre con badge y zona.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // La foto lleva a editar el perfil y las fotos, con el lápiz a la vista (como en Tinder).
                    Box(
                        Modifier.size(112.dp).gel(56.dp, null, listOf(externa(14f, 26f, rgba(80, 60, 160, .45f), spread = -10f)))
                            .toque { ir.editarPerfil() }
                            .semantics { role = Role.Button; contentDescription = "Editar perfil y fotos" },
                    ) {
                        Avatar(y.nombre, y.fotos.firstOrNull(), 112.dp, borde = 4.dp, colorBorde = C.blanco, tamTexto = 40f)
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(36.dp).background(C.violeta, CircleShape).border(3.dp, C.blanco, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icono(I.lapiz, 16.dp, C.blanco, 2.4f) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(listOfNotNull(y.nombre.ifEmpty { null }, y.edad?.toString()).joinToString(", "), 28f, 700, alto = 1f)
                        if (y.verificada) MiosVerificado()
                    }
                    val estado = when {
                        y.verificada -> "Verificada con DNI"
                        y.selfie -> "Selfie verificada · falta DNI"
                        else -> null
                    }
                    val zona = listOf(y.zona, y.ciudad).filter { it.isNotEmpty() }.joinToString(", ")
                    T(listOfNotNull(zona.ifEmpty { null }, estado).joinToString(" · "), 14f, 400, p.texto2, alinear = TextAlign.Center)
                    // Lo de todos los días, a la vista: antes estaba escondido en la hoja de la cuenta.
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
                        AccionRapida(I.lapiz, "Editar perfil") { ir.editarPerfil() }
                        AccionRapida(I.billetera, "Mis pases") { ir.pases() }
                        AccionRapida(I.ajustes, "Ajustes") { opciones = true }
                    }
                }

                // Verificación y seguridad.
                val dni = if (y.verificada) "aprobada" else verif?.dni ?: "ninguna"
                Row(
                    // Sin DNI (ni uno en revisión): la pantalla amable de antes del DNI.
                    Modifier.fillMaxWidth().toque(alBajar = 2.dp) { if (dni == "aprobada" || dni == "pendiente") ir.verificacion() else ir.pedirDni("gente") }
                        .gel(24.dp, if (p.oscuro) Gels.tarjetaOscura else gelVerificacion)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(48.dp).gel(17.dp, miosTileVioleta), contentAlignment = Alignment.Center) { Icono(I.escudoCheck, 24.dp, C.lima, 2.4f) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        T("Verificación y seguridad", 15f, 800)
                        T(
                            when (dni) {
                                "aprobada" -> "Tu DNI ya está verificado"
                                "pendiente" -> "Ya estamos revisando tu DNI."
                                else -> "Verificá tu DNI"
                            },
                            12f, 600, p.texto2,
                        )
                    }
                    when (dni) {
                        "aprobada" -> MiosPill("Completa", Brush.verticalGradient(listOf(C.lima, C.lima)), C.carbon, tam = 12f)
                        "pendiente" -> Unit
                        else -> MiosPill("1 paso", Brush.verticalGradient(listOf(C.coral, C.coral)), C.carbon, tam = 12f)
                    }
                }

                // Caigo Plus.
                val e = plus
                Row(
                    Modifier.fillMaxWidth().toque { ir.plus("perfil") }.gel(24.dp, gelPlus).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(54.dp).rotate(-8f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        T("Caigo Plus", 19f, 800, C.blanco, alto = 1.1f)
                        val vence = e?.vence?.let { Fecha.parse(it) }?.let { "%02d/%02d".format(it.dayOfMonth, it.monthValue) }
                        T(
                            when {
                                e?.activo == true && vence != null -> if (e.renueva) "Activo · se renueva el $vence" else "Activo hasta el $vence"
                                e?.activo == true -> "Activo"
                                else -> "Sumadas ilimitadas y planes destacados"
                            },
                            12f, 600, C.blanco,
                        )
                    }
                    if (e?.activo == true) {
                        Box(Modifier.size(32.dp).background(C.lima, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Icono(I.check, 16.dp, C.carbon, 3.4f) }
                    } else {
                        Box(Modifier.background(C.lima, RoundedCornerShape(13.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) { T("Ver", 13f, 800, C.carbon) }
                    }
                }

                // Con logros: cabecera sobria (nivel y planes que cuentan, asistencia, racha, insignias y lo que falta).
                // Sin DNI, lo que falta va primero (la tarjeta de verificación) y lo de ir a planes, después de verificarse.
                val logros = y.logros?.takeIf { y.verificada }
                if (logros != null) CabeceraLogros(y, logros)
                // Asistencia real y planes (las dos del mismo alto).
                else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val a = y.asistencia
                    Column(Modifier.weight(1.2f).fillMaxHeight().gel(22.dp, miosOscura).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(if (a.de > 0) "${a.fue * 100 / a.de}%" else "—", 40f, 800, C.lima, alto = 1f)
                        T("Asistencia real", 13f, 600, C.blancoCalido)
                        T(if (a.de > 0) "${a.fue} de ${a.de} con check-in" else "Todavía sin planes con check-in", 12f, 400, C.apagado2)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().gel(22.dp, if (p.oscuro) Gels.chipOscuro else Gels.chipClaro).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T("${y.planes}", 40f, 800, if (p.oscuro) C.lima else C.violetaTexto, alto = 1f)
                        T("Planes", 13f, 600)
                        T(if (y.organizados == 1) "1 organizado" else "${y.organizados} organizados", 12f, 400, p.texto2)
                    }
                }

                if (y.verificada) MiosGrupo {
                    MiosFila(I.gente, "Traé a alguien", ultima = logros == null) { codigo = ""; trayendo = true }
                    // Toda la fila es el interruptor (y se lee como uno, con su nombre).
                    if (logros != null) Row(
                        Modifier.fillMaxWidth()
                            .toggleable(value = logros.ocultas, role = Role.Switch, onValueChange = { ocultarInsignias(it) })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icono(I.ojoTachado, 20.dp, if (p.oscuro) C.lima else C.violeta, 2f)
                        T("Ocultar mis insignias", 14f, 800, modifier = Modifier.weight(1f))
                        ar.com.caigo.app.piezas.Interruptor(logros.ocultas, { ocultarInsignias(it) }, Modifier.clearAndSetSemantics {})
                    }
                }

                // Lo que busca.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        MiosSeccion("Busca en Caigo", Modifier.weight(1f))
                        T("Cambiar", 13f, 800, if (p.oscuro) C.lima else C.violetaTexto, Modifier.tocable { ir.intenciones(true) })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val b = y.intenciones.mapNotNull { intencionesCortas[it] }.ifEmpty { listOf("Sin definir") }
                        b.forEach { t ->
                            Box(Modifier.gel(14.dp, Gels.sel).padding(horizontal = 13.dp, vertical = 9.dp)) { T(t, 13f, 800, C.blanco) }
                        }
                    }
                }

                // Le copa.
                if (y.intereses.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiosSeccion("Le copa")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            y.intereses.forEach { t ->
                                Box(Modifier.gel(13.dp, if (p.oscuro) Gels.chipOscuro else Gels.chipClaro).padding(horizontal = 12.dp, vertical = 8.dp)) { T(t, 13f, 600) }
                            }
                        }
                    }
                }

                // Mi gente.
                Row(
                    Modifier.fillMaxWidth().tocable { ir.gente() }.gel(22.dp, miosGelTarjeta()).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiosSeccion("Mi gente · ${y.miGente}")
                        if (gente.isEmpty()) {
                            T("Los que sumás después de un plan aparecen acá.", 12f, 400, p.texto2)
                        } else {
                            Avatares(gente.map { it.nombre to it.foto }, total = maxOf(y.miGente, gente.size), max = 4, tam = 34.dp)
                        }
                    }
                    Icono(I.adelante, 20.dp, p.texto, 2.2f)
                }

                // Gente y Citas salieron de la cabecera de Ahora (18/11): se abren desde acá, sin protagonismo.
                // Citas, sólo para quien eligió esa intención. Sin DNI, antes «verificamos que seas vos».
                val citas = y.intenciones.contains("citas")
                MiosGrupo {
                    MiosFila(I.usuario, "Conocer gente", "Gente cerca que sale a lo mismo que vos", ultima = !citas) {
                        if (c.conDni(ir, "gente")) ir.mazo("gente")
                    }
                    if (citas) MiosFila(I.corazonChico, "Citas", ultima = true) { if (c.conDni(ir, "gente")) ir.mazo("citas") }
                }

                // Al final, la acción destructiva (con el texto de peligro de «Cancelar el plan»).
                T(
                    "Borrar mi cuenta", 15f, 700, C.coral2,
                    Modifier.align(Alignment.CenterHorizontally).tocable { mailBaja = ""; borrando = true }.padding(horizontal = 18.dp, vertical = 16.dp),
                )
                // Que no quede tapado por el botón de Crear de la barra.
                Spacer(Modifier.height(28.dp))
            }
            }
        }
        BotonIcono(
            I.ajustes, { opciones = true },
            Modifier.align(Alignment.TopEnd).padding(top = arriba + 8.dp, end = 20.dp).semantics { contentDescription = "Ajustes" },
            iconoTam = 21.dp, trazo = 2f, gel = if (p.oscuro) Gels.chipOscuro else Gels.chipClaro, color = p.texto,
        )
        // «Traé a alguien»: el link para compartir, el premio, cuántos invité y, en los primeros días, el código de
        // quien me invitó (POST /v1/yo/invitado).
        MiosHoja(trayendo, { trayendo = false }, "Traé a alguien") {
            val r = referidos
            if (r != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).height(52.dp).gel(16.dp, Gels.chip(p)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        T(r.link, 14f, 700, unRenglon = true)
                    }
                    Cta("Compartir", { Sistema.compartir(ctx, r.link) }, Modifier.width(124.dp), gel = Gels.violeta, alto = 52.dp, radio = 16.dp, tam = 15f, color = C.blanco)
                }
                T("Cuando vaya a su primer plan, los dos tienen una semana de Plus.", 14f, 400, p.texto2, alto = 1.45f)
                T("${r.invitados} ${if (r.invitados == 1) "invitado" else "invitados"} · ${r.premiados} ya fueron", 13f, 700)
                if (r.puedeUsarCodigo) {
                    T("¿Te invitó alguien? Poné su código", 14f, 700, modifier = Modifier.padding(top = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiosCampo(
                            codigo, { codigo = it }, Modifier.weight(1f), placeholder = "Código", maximo = 24,
                            mayusculas = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                            teclado = androidx.compose.ui.text.input.KeyboardType.Ascii,
                        )
                        Cta(
                            "Listo", { usarCodigo() }, Modifier.width(100.dp), alto = 60.dp, radio = 18.dp, tam = 16f, color = C.carbon,
                            habilitado = codigo.trim().isNotEmpty(), cargando = usandoCodigo,
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Puntitos(p.acento) }
            }
        }
        MiosHoja(opciones, { opciones = false }, "Tu cuenta") {
            MiosGrupo {
                MiosFila(I.usuario, "Editar perfil") { opciones = false; ir.editarPerfil() }
                MiosFila(I.corazonChico, "Lo que buscás") { opciones = false; ir.intenciones(true) }
                MiosFila(I.billetera, "Mis pases") { opciones = false; ir.pases() }
                MiosFila(I.enviar, "Mensajes privados") { opciones = false; ir.privados() }
                // PARA LA NAVEGACIÓN: Ir.ajustesAvisos() → ruta "avisos" → AjustesDeAvisos(ir) (Ajustes.kt).
                MiosFila(I.campana, "Avisos") { opciones = false; ir.ajustesAvisos() }
                MiosFila(I.escudo, "Verificación y seguridad", ultima = true) { opciones = false; ir.verificacion() }
            }
            // Los legales, en la web (como el «Al entrar aceptás…» del ingreso).
            MiosGrupo {
                MiosFila(I.candado, "Términos y condiciones") { opciones = false; Sistema.abrir(ctx, Legales.TERMINOS) }
                MiosFila(I.escudoCheck, "Política de privacidad", ultima = true) {
                    opciones = false
                    Sistema.abrir(ctx, Legales.PRIVACIDAD)
                }
            }
            MiosGrupo {
                MiosFila(I.flecha, "Salir", colorIcono = C.coral2, ultima = true) {
                    opciones = false
                    // iOS deja «Ingresar» como base de la pila.
                    alcance.launch { c.salir(); ir.intro(); ir.ingresar() }
                }
            }
        }
        // «¿Borrar tu cuenta?» (App Store 5.1.1(v)): se confirma escribiendo el mail, DELETE /v1/cuenta, se olvida la
        // sesión y vuelve al ingreso. La cuenta se desactiva ahora y los datos se borran a los 30 días.
        MiosHoja(borrando, { if (!borrandoAhora) borrando = false }, "¿Borrar tu cuenta?") {
            T("Tu cuenta se desactiva ahora y tus datos personales se borran a los 30 días.", 15f, 600, p.texto, alto = 1.5f)
            T("En esos 30 días la recuperás escribiendo a hola@caigo.com.ar. Después no se puede deshacer.", 15f, 400, p.texto2, alto = 1.5f)
            T("Si tenés Caigo Plus, cancelalo en Google Play.", 15f, 400, p.texto2, alto = 1.5f)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Para confirmar, escribí tu mail", 14f, 700)
                MiosCampo(
                    mailBaja, { mailBaja = it }, placeholder = "tu@mail.com", maximo = 120,
                    mayusculas = KeyboardCapitalization.None, teclado = KeyboardType.Email,
                )
            }
            MiosGrupo {
                Box(
                    Modifier.fillMaxWidth().tocable {
                        if (borrandoAhora || !mailBajaOk) return@tocable
                        borrandoAhora = true
                        alcance.launch {
                            try {
                                c.borrarCuenta()
                                borrando = false
                                // Como «Salir»: Ingresar sobre la intro.
                                ir.intro(); ir.ingresar()
                            } catch (e: Throwable) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                avisos.error(mensajeDe(e))
                            } finally {
                                borrandoAhora = false
                            }
                        }
                    }.semantics { role = Role.Button }.padding(horizontal = 18.dp, vertical = 16.dp),
                ) { if (borrandoAhora) Puntitos(C.coral2) else T("Borrar mi cuenta", 15f, 700, C.coral2.copy(alpha = if (mailBajaOk) 1f else .4f)) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (p.oscuro) p.separador else C.lavanda))
                Box(Modifier.fillMaxWidth().tocable { if (!borrandoAhora) borrando = false }.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    T("Cancelar", 15f, 700)
                }
            }
        }
    }
}

/** Mi gente: la gente que sumaste (GET /v1/gente). */
@Composable
fun MiGente(ir: Ir) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val gente = rememberCarga("gente") { c.api.miGente().gente }
    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp)) {
            Encabezado(
                "Mi gente", gente.dato?.let { if (it.size == 1) "1 persona" else "${it.size} personas" }, { ir.atras() },
                Modifier.padding(horizontal = 16.dp),
            )
            val lista = gente.dato
            when {
                lista == null && gente.error != null -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    MiosVacio("No pudimos cargar tu gente", gente.error!!, "Probar de nuevo", { gente.recargar() })
                }
                lista == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Puntitos(p.acento) }
                lista.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    MiosVacio("Todavía no sumaste a nadie", "Después de cada plan te preguntamos con quién volverías a salir.", "Ver mis planes", { ir.principal(Pestana.Mis) })
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = abajo + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lista, key = { it.id }) { g ->
                        Row(
                            Modifier.fillMaxWidth().tocable { ir.persona(g.id, "gente") }.gel(20.dp, miosGelTarjeta()).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(g.nombre, g.foto, 44.dp, borde = 0.dp, tamTexto = 17f)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    T(listOfNotNull(g.nombre, g.edad?.toString()).joinToString(", "), 15f, 700, unRenglon = true)
                                    if (g.verificada) MiosVerificado(15.dp)
                                }
                                T(miosAsistencia(g.asistencia), 12f, 400, p.texto2)
                            }
                            Icono(I.adelante, 18.dp, p.texto3, 2.2f)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Editar perfil: fotos, nombre, bio, trabajo, zona, preguntas e intereses (PATCH /v1/yo).
 * Con [completar]: «Completá tu perfil», con los mínimos de la API: después del DNI del primer «Caigo»
 * ([apilado]: al guardar vuelve a donde estaba) o como ruta vieja del alta (al guardar, a Ahora).
 */
@Composable
fun EditarPerfil(ir: Ir, completar: Boolean = false, apilado: Boolean = false) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val yo by c.yo.collectAsState()
    LaunchedEffect(Unit) { if (c.yo.value == null) c.recargarYo() }
    val y = yo

    var listo by remember { mutableStateOf(false) }
    val fotos = remember { mutableStateListOf<String>() }
    var nombre by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var trabajo by remember { mutableStateOf("") }
    var zona by remember { mutableStateOf("") }
    val prompts = remember { mutableStateListOf<Prompt>() }
    val gustos = remember { mutableStateListOf<String>() }
    var subiendo by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }

    LaunchedEffect(y) {
        if (y != null && !listo) {
            fotos.addAll(y.fotos); nombre = y.nombre; bio = y.bio; trabajo = y.trabajo; zona = y.zona
            prompts.addAll(y.prompts); gustos.addAll(y.intereses); listo = true
        }
    }

    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        subiendo = true
        alcance.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    // Achicada al decodificar y derecha según el EXIF (jpegDeFoto, Camara.kt).
                    ar.com.caigo.app.piezas.jpegDeFoto(ctx, uri)
                        ?: throw ar.com.caigo.app.nucleo.ErrorApi(0, ar.com.caigo.app.nucleo.Problema(detail = "No pudimos leer esa foto. Probá con otra."))
                }
                fotos.add(c.api.subir("foto", jpeg).url)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            } finally {
                subiendo = false
            }
        }
    }

    Pantalla { arriba, abajo ->
        if (!listo) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Puntitos(p.acento) }
            return@Pantalla
        }
        // Los mínimos que faltan (los mismos de personas.Faltantes en la API).
        val faltan = buildList {
            if (zona.isBlank()) add("tu zona")
            if (bio.trim().length < 30) add("contar algo sobre vos (30 letras o más)")
            if (fotos.size < 2) add("2 fotos")
            if (gustos.size < 3) add("3 cosas que te copen")
        }
        Column(Modifier.fillMaxSize().padding(top = if (completar) arriba + 14.dp else arriba + 6.dp)) {
            if (completar) {
                // Con los colores de la paleta (el título del alta es para el fondo claro).
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("Completá tu perfil", 34f, 700, p.texto, alto = 1.05f, espaciado = px(-.8f, 34f))
                    T("Así la gente sabe con quién sale. Mínimo 2 fotos, unas líneas sobre vos y 3 cosas que te copen.", 15f, 400, p.texto2, alto = 1.5f)
                }
            } else {
                Encabezado("Editar perfil", null, { ir.atras() }, Modifier.padding(horizontal = 16.dp))
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiosSeccion("Fotos")
                    T("La primera es la que ve todo el mundo. Tocá otra para ponerla primera.", 13f, 500, p.texto2, alto = 1.4f)
                    val celdas = fotos.toList() + if (fotos.size < 6) listOf("") else emptyList()
                    celdas.chunked(3).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            fila.forEach { url ->
                                Box(Modifier.weight(1f).aspectRatio(.8f)) {
                                    if (url.isNotEmpty()) {
                                        val i = fotos.indexOf(url)
                                        Foto(
                                            url,
                                            Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                                                .then(if (i == 0) Modifier.border(3.dp, C.violeta, RoundedCornerShape(18.dp)) else Modifier)
                                                .tocable { if (i > 0) { fotos.removeAt(i); fotos.add(0, url) } }
                                                .semantics { contentDescription = if (i == 0) "Foto principal" else "Foto ${i + 1}, usar como principal" },
                                        )
                                        if (i == 0) {
                                            T(
                                                "Principal", 11f, 800, C.blanco,
                                                Modifier.align(Alignment.BottomStart).padding(6.dp).background(C.violeta, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                        Box(
                                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).background(C.carbon, CircleShape).tocable { fotos.remove(url) }
                                                .semantics { contentDescription = "Sacar foto"; role = Role.Button },
                                            contentAlignment = Alignment.Center,
                                        ) { Icono(I.cruz, 14.dp, C.blanco, 3f) }
                                    } else {
                                        Box(
                                            Modifier.fillMaxSize().background(p.superficie, RoundedCornerShape(18.dp))
                                                .drawBehind {
                                                    val r = 18.dp.toPx()
                                                    drawRoundRect(
                                                        Color(0xFFB9ACF5), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                                                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                                                    )
                                                }
                                                .tocable { if (!subiendo) elegir.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                                .semantics { contentDescription = "Sumar foto"; role = Role.Button },
                                            contentAlignment = Alignment.Center,
                                        ) { if (subiendo) Puntitos() else Icono(I.mas, 26.dp, C.violeta, 3f) }
                                    }
                                }
                            }
                            repeat(3 - fila.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                Etiquetado("Nombre") { MiosCampo(nombre, { nombre = it }, maximo = 40, mayusculas = KeyboardCapitalization.Words) }
                Etiquetado("Sobre mí") { MiosCampo(bio, { bio = it }, renglones = 3, maximo = 300) }
                Etiquetado("Trabajo") { MiosCampo(trabajo, { trabajo = it }, maximo = 60) }
                Etiquetado("Zona") { MiosCampo(zona, { zona = it }, placeholder = "Güemes", maximo = 60, mayusculas = KeyboardCapitalization.Words) }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiosSeccion("Preguntas")
                    prompts.forEachIndexed { i, pr ->
                        Column(Modifier.fillMaxWidth().gel(22.dp, if (p.oscuro) neutraOscura else Gels.neutra).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T(pr.p.uppercase(), 12f, 800, p.texto2, Modifier.weight(1f), espaciado = px(1f, 12f))
                                Icono(I.cruz, 16.dp, p.texto3, 2.4f, modifier = Modifier.tocable { if (i < prompts.size) prompts.removeAt(i) })
                            }
                            MiosCampo(pr.r, { if (i < prompts.size) prompts[i] = prompts[i].copy(r = it) }, renglones = 2, maximo = 150)
                        }
                    }
                    if (prompts.size < 3) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            preguntas.filter { q -> prompts.none { it.p == q } }.forEach { q ->
                                Chip(q, false, { prompts.add(Prompt(q, "")) }, tam = 13f)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiosSeccion("¿Qué te copa?")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        (intereses + gustos.filter { it !in intereses }).forEach { g ->
                            Chip(g, g in gustos, { if (g in gustos) gustos.remove(g) else if (gustos.size < 12) gustos.add(g) })
                        }
                    }
                }
            }
            if (completar && faltan.isNotEmpty()) {
                T(
                    "Falta: ${faltan.joinToString(", ")}.", 13f, 600, p.texto2,
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp), alinear = TextAlign.Center,
                )
            }
            Cta(
                if (completar) (if (apilado) "Listo, seguir" else "Listo, entrar") else "Guardar", {
                    guardando = true
                    alcance.launch {
                        try {
                            c.yo.value = c.api.actualizarYo(
                                CambiosPerfil(
                                    nombre = nombre.trim(), bio = bio.trim(), trabajo = trabajo.trim(), zona = zona.trim(),
                                    fotos = fotos.toList(), intereses = gustos.toList(),
                                    prompts = prompts.filter { it.r.isNotBlank() }.map { it.copy(r = it.r.trim()) },
                                ),
                            )
                            if (completar) {
                                avisos.ok("¡Listo! Ya podés sumarte a planes.")
                                if (apilado) ir.atras() else ir.principal(Pestana.Ahora)
                            } else {
                                avisos.ok("Listo, actualizamos tu perfil.")
                                ir.atras()
                            }
                        } catch (e: Throwable) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            avisos.error(mensajeDe(e))
                        } finally {
                            guardando = false
                        }
                    }
                },
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = abajo + 20.dp),
                habilitado = nombre.isNotBlank() && !subiendo && (!completar || faltan.isEmpty()), cargando = guardando,
            )
        }
    }
}

@Composable
private fun Etiquetado(etiqueta: String, campo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T(etiqueta, 14f, 700)
        campo()
    }
}

/**
 * Cabecera sobria de lo que se gana yendo: nivel y planes que cuentan, la asistencia real, la racha (desde 2
 * semanas), las insignias en chips chicos y cuánto falta para el siguiente nivel. Sin puntos ni festejos.
 */
@Composable
private fun CabeceraLogros(y: ar.com.caigo.app.nucleo.Perfil, l: ar.com.caigo.app.nucleo.Logros) {
    val a = y.asistencia
    Column(Modifier.fillMaxWidth().gel(22.dp, miosOscura).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T(ar.com.caigo.app.nucleo.TextoLogros.cabecera(l), 15f, 800, C.blancoCalido, unRenglon = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            T(if (a.de > 0) "${a.fue * 100 / a.de}%" else "—", 40f, 800, C.lima, alto = 1f)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T("Asistencia real", 13f, 600, C.blancoCalido)
                T(if (a.de > 0) "${a.fue} de ${a.de} con check-in" else "Todavía sin planes con check-in", 12f, 400, C.apagado2)
            }
        }
        ar.com.caigo.app.nucleo.TextoLogros.racha(l)?.let { T(it, 12f, 600, C.apagado2) }
        if (l.insignias.isNotEmpty()) {
            // Cada insignia con su chip propio y el detalle a la vista (no escondido para el lector de pantalla).
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                l.insignias.forEach { i ->
                    Row(
                        Modifier.semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ar.com.caigo.app.pantallas.planes.ChipInsignia(i.nombre, sobreOscuro = true)
                        T(i.detalle, 12f, 400, C.apagado2, unRenglon = true)
                    }
                }
            }
        }
        ar.com.caigo.app.nucleo.TextoLogros.siguiente(l)?.let { T(it, 12f, 400, C.apagado2) }
    }
}

@Composable
private fun AccionRapida(icono: String, titulo: String, onClick: () -> Unit) {
    val p = LocalPaleta.current
    Column(
        Modifier.toque { onClick() }.semantics(mergeDescendants = true) { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(54.dp).gel(27.dp, if (p.oscuro) Gels.chipOscuro else Gels.chipClaro), contentAlignment = Alignment.Center) {
            Icono(icono, 22.dp, if (p.oscuro) C.lima else C.violeta, 2.2f)
        }
        T(titulo, 12f, 700, p.texto2)
    }
}
