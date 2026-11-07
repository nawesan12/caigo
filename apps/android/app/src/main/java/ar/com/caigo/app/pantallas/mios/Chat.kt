package ar.com.caigo.app.pantallas.mios


import ar.com.caigo.app.piezas.HojaReporte
import ar.com.caigo.app.piezas.HojaOpciones
import ar.com.caigo.app.nucleo.Reporte
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Mensaje
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.Privado as PrivadoMsj
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.piezas.Avatar
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.piezas.Encabezado
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.piezas.Foto
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Puntitos
import ar.com.caigo.app.piezas.rememberCarga
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.piezas.toque
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.estilo
import ar.com.caigo.app.tema.gel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import ar.com.caigo.app.app.Pestana
import ar.com.caigo.app.nucleo.Perfil

/** Un renglón del chat, sea del plan o privado. */
internal data class MiosLinea(val id: Long, val texto: String, val mio: Boolean, val sistema: Boolean, val autor: String, val foto: String)

private fun Mensaje.linea(yo: String) = MiosLinea(id, texto, autor?.id == yo, autor == null, autor?.nombre.orEmpty(), autor?.foto.orEmpty())

private fun PrivadoMsj.linea(yo: String, nombre: String) = MiosLinea(id, texto, deId == yo, false, if (deId == yo) "" else nombre, "")

/** 08 · Chat del plan, en tiempo real por el WebSocket. */
@Composable
fun Chat(ir: Ir, planId: String) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val yo = c.sesion.usuarioId
    // try? de iOS: si falla, el chat se ve igual, sin la tarjeta del plan.
    var plan by remember(planId) { mutableStateOf<Plan?>(null) }
    val lineas = remember(planId) { mutableStateListOf<MiosLinea>() }
    var cargado by remember(planId) { mutableStateOf(false) }
    var error by remember(planId) { mutableStateOf<String?>(null) }
    var escribe by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var opciones by remember { mutableStateOf(false) }
    var reportando by remember { mutableStateOf(false) }
    var confirmando by remember { mutableStateOf(false) }

    LaunchedEffect(planId) { plan = runCatching { c.api.plan(planId) }.getOrNull() }
    LaunchedEffect(planId) {
        try {
            val ms = c.api.mensajes(planId).mensajes
            lineas.clear(); lineas.addAll(ms.map { it.linea(yo) })
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = mensajeDe(e)
        }
        cargado = true
    }
    LaunchedEffect(planId) {
        c.tiempoReal.encender()
        c.tiempoReal.eventos.collect { ev ->
            val m = ev.mensaje
            val q = ev.quien
            if (ev.tipo == "mensaje" && m != null && m.planId == planId) {
                if (lineas.none { it.id == m.id }) lineas.add(m.linea(yo))
                val n = m.autor?.nombre
                if (n != null && miosPrimer(n) == escribe?.first) escribe = null
            } else if (ev.tipo == "escribiendo" && ev.planId == planId && q != null && q.id != yo) {
                escribe = miosPrimer(q.nombre) to System.currentTimeMillis()
            }
        }
    }
    // "Lu está escribiendo…" se apaga a los 4 s.
    LaunchedEffect(escribe) { if (escribe != null) { delay(4000); escribe = null } }

    fun salir(organizo: Boolean) {
        alcance.launch {
            try {
                if (organizo) {
                    c.api.cancelarPlan(planId)
                    avisos.ok("Cancelaste el plan. Les avisamos a todos.")
                } else {
                    c.api.salir(planId)
                    avisos.ok("Saliste del plan.")
                }
                ir.principal(Pestana.Mis)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            }
        }
    }

    Pantalla { arriba, abajo ->
        val pl = plan
        Column(Modifier.fillMaxSize()) {
            // Encabezado: volver, título, cupo y avatares.
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = arriba + 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BotonIcono(I.atras, { ir.atras() }, gel = if (p.oscuro) Gels.chipOscuro else Gels.chipClaro, color = p.texto)
                Column(Modifier.weight(1f)) {
                    T(pl?.titulo.orEmpty(), 19f, 700, alto = 1.15f, unRenglon = true)
                    if (pl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).background(Color(0xFF8DC63F), CircleShape))
                            T(
                                "${pl.somos} de ${pl.total} · " + if (Cupo.completo(pl.faltan)) "plan completo" else Cupo.badge(pl.faltan).lowercase(),
                                12f, 600, p.texto2, unRenglon = true,
                            )
                        }
                    }
                }
                if (pl != null) {
                    val gente = (listOf(pl.organizador) + pl.van).distinctBy { it.id }.filter { it.id != yo }
                    Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                        gente.take(3).forEachIndexed { i, g ->
                            Avatar(g.nombre, g.foto, 30.dp, Modifier.zIndex(5f - i), colorBorde = p.fondo, tamTexto = 11f)
                        }
                    }
                }
                BotonIcono(
                    I.puntos, { opciones = true }, Modifier.semantics { contentDescription = "Más opciones" },
                    gel = if (p.oscuro) Gels.chipOscuro else Gels.chipClaro, color = Color.Transparent, relleno = p.texto, iconoTam = 20.dp, trazo = 0f,
                )
            }
            // Tarjeta fijada del plan.
            if (pl != null) {
                val t = Fecha.parse(pl.empiezaEn)
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .background(p.superficie, RoundedCornerShape(20.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Foto(pl.foto, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val lugar = pl.lugar?.nombre ?: pl.lugarNombre.ifEmpty { pl.zona }
                        T(listOf(Fecha.cuando(pl.empiezaEn), lugar).filter { it.isNotEmpty() }.joinToString(" · "), 14f, 700, unRenglon = true)
                        val desde = t?.minusMinutes(30)?.let { "check-in desde ${Fecha.hora(it)}" }
                        val dir = pl.direccion?.texto?.ifEmpty { null } ?: pl.zona.ifEmpty { null }
                        T(listOfNotNull(dir, desde).joinToString(" · "), 12f, 400, p.texto2, unRenglon = true)
                    }
                    T("Ver plan", 13f, 700, if (p.oscuro) C.lima else C.violetaTexto, Modifier.tocable { ir.plan(planId) })
                }
            }
            MiosVistaHilo(lineas, cargado, error, escribe?.first, Modifier.weight(1f))
            MiosEntrada(abajo, { c.tiempoReal.escribiendo(planId) }) { t ->
                try {
                    val m = c.api.escribir(planId, t)
                    if (lineas.none { it.id == m.id }) lineas.add(m.linea(yo))
                    true
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    avisos.error(mensajeDe(e)); false
                }
            }
        }
        // Menú del chat: reportar el plan y salir (el organizador no sale: cancela el plan).
        val organizo = pl?.miEstado == "organizador"
        if (opciones) HojaOpciones({ opciones = false }, listOf(
            "Reportar el plan" to { reportando = true },
            (if (organizo) "Cancelar el plan" else "Salir del plan") to { confirmando = true },
        ))
        if (reportando) HojaReporte({ reportando = false }) { motivo ->
            alcance.launch {
                try {
                    c.api.reportar(Reporte(planId = planId, motivo = motivo))
                    avisos.ok("Gracias. Lo revisa una persona del equipo en menos de 24 h.")
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    avisos.error(mensajeDe(e))
                }
            }
        }
        if (confirmando) HojaOpciones({ confirmando = false }, listOf(
            (if (organizo) "Cancelar el plan y avisarles" else "Salir del plan") to { salir(organizo) },
            (if (organizo) "No, lo mantengo" else "Me quedo") to {},
        ))
    }
}

/** Los mensajes (abajo de todo el último) y "X está escribiendo…". */
@Composable
internal fun MiosVistaHilo(lineas: List<MiosLinea>, cargado: Boolean, error: String?, escribe: String?, modifier: Modifier, foto: String? = null) {
    val p = LocalPaleta.current
    val lista = rememberLazyListState()
    // Baja al final cuando llega un mensaje o alguien escribe.
    LaunchedEffect(lineas.size, escribe) {
        val n = lineas.size + (if (escribe != null) 1 else 0)
        if (n > 0) lista.animateScrollToItem(n - 1)
    }
    Box(modifier.fillMaxWidth()) {
        when {
            !cargado -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Puntitos(p.acento) }
            error != null && lineas.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                T(error, 15f, 500, p.texto2, alto = 1.5f, alinear = TextAlign.Center)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(), state = lista,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                items(lineas, key = { it.id }) { l -> MiosBurbuja(if (foto != null) l.copy(foto = foto) else l) }
                if (escribe != null) item(key = "escribe") {
                    T("$escribe está escribiendo…", 12f, 400, p.texto2, Modifier.fillMaxWidth().padding(start = 36.dp))
                }
            }
        }
    }
}

@Composable
private fun MiosBurbuja(l: MiosLinea) {
    val p = LocalPaleta.current
    when {
        l.sistema -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.gel(13.dp, Gels.limaChico).padding(horizontal = 14.dp, vertical = 8.dp)) { T(l.texto, 13f, 700, C.carbon, alinear = TextAlign.Center) }
        }
        l.mio -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier.widthIn(max = 250.dp)
                    .background(C.violeta, RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { T(l.texto, 15f, 400, C.blanco, alto = 1.4f) }
        }
        else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(l.autor, l.foto, 28.dp, borde = 0.dp, tamTexto = 11f)
            Column(Modifier.widthIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (l.autor.isNotEmpty()) T(miosPrimer(l.autor), 11f, 700, p.texto2)
                Box(
                    // Sombra de tarjeta y fondo con la punta de abajo a la izquierda (radio 6).
                    Modifier.gel(18.dp, null, miosGelTarjeta().sombras.filter { !it.inset })
                        .background(p.tarjetaPlano, RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) { T(l.texto, 15f, 400, alto = 1.4f) }
            }
        }
    }
}

/** Barra de escribir: "Escribí algo…" + botón violeta. Enter envía. */
@Composable
internal fun MiosEntrada(abajo: androidx.compose.ui.unit.Dp, alTipear: () -> Unit, enviar: suspend (String) -> Boolean) {
    val p = LocalPaleta.current
    val alcance = rememberCoroutineScope()
    var texto by remember { mutableStateOf("") }
    var mandando by remember { mutableStateOf(false) }
    fun mandar() {
        val t = texto.trim()
        if (t.isEmpty() || mandando) return
        mandando = true
        alcance.launch {
            if (enviar(t)) texto = ""
            mandando = false
        }
    }
    Column(Modifier.fillMaxWidth().background(if (p.oscuro) C.oscuro4 else C.blanco)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (p.oscuro) p.separador else Color(0xFFECE8F2)))
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp + abajo),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicTextField(
                texto,
                { nuevo ->
                    // Como iOS: se recorta a 2000 y avisa «escribiendo» cuando crece.
                    val v = if (nuevo.length > 2000) nuevo.take(2000) else nuevo
                    if (v.length > texto.length) alTipear()
                    texto = v
                },
                Modifier.weight(1f).height(44.dp)
                    .background(if (p.oscuro) p.superficie else Color(0xFFF6F4F0), RoundedCornerShape(15.dp))
                    .onPreviewKeyEvent { e -> if (e.key == Key.Enter) { mandar(); true } else false },
                textStyle = estilo(15f, 400, p.texto),
                singleLine = true,
                cursorBrush = SolidColor(C.violeta),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { mandar() }),
                decorationBox = { campo ->
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        if (texto.isEmpty()) T("Escribí algo…", 15f, 400, C.inactivo)
                        campo()
                    }
                },
            )
            Box(
                Modifier.size(44.dp).toque(!mandando) { mandar() }.gel(15.dp, miosVioletaChico).semantics { contentDescription = "Enviar" },
                contentAlignment = Alignment.Center,
            ) {
                Icono(I.flecha, 18.dp, C.blanco, 2.4f)
            }
        }
    }
}

/** Mensajes privados: las conversaciones con la gente que compartió un plan con vos. Es `VistaPrivados` en iOS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Privados(ir: Ir) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<ar.com.caigo.app.nucleo.Conversacion>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refrescando by remember { mutableStateOf(false) }
    suspend fun cargar() {
        try {
            lista = c.api.conversaciones().conversaciones
            error = null
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = mensajeDe(e)
        }
    }
    LaunchedEffect(Unit) { cargar() }
    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp)) {
            Encabezado("Mensajes", null, { ir.atras() }, Modifier.padding(horizontal = 16.dp))
            val l = lista
            val e = error
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    l != null && l.isEmpty() ->
                        MiosVacio("Todavía no hay mensajes", "Podés escribirle a la gente con la que compartiste un plan.", "Ver mi gente", { ir.gente() }, Modifier.padding(28.dp))
                    l != null -> PullToRefreshBox(
                        refrescando,
                        { alcance.launch { refrescando = true; try { cargar() } finally { refrescando = false } } },
                        Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = abajo + 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(l, key = { it.con.id }) { cv ->
                                Row(
                                    Modifier.fillMaxWidth().tocable { ir.privado(cv.con.id, cv.con.nombre) }.gel(20.dp, miosGelTarjeta()).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Avatar(cv.con.nombre, cv.con.foto, 44.dp, borde = 0.dp, tamTexto = 17f)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            T(cv.con.nombre, 15f, 700, modifier = Modifier.weight(1f), unRenglon = true)
                                            T(Fecha.hace(cv.ultimo.creadoEn), 12f, 400, p.texto2, unRenglon = true)
                                        }
                                        T(
                                            (if (cv.ultimo.deId == c.sesion.usuarioId) "Vos: " else "") + cv.ultimo.texto, 13f,
                                            if (cv.sinLeer > 0) 700 else 400, if (cv.sinLeer > 0) p.texto else p.texto2, unRenglon = true,
                                        )
                                    }
                                    if (cv.sinLeer > 0) {
                                        Box(Modifier.size(22.dp).background(C.coral, CircleShape), contentAlignment = Alignment.Center) {
                                            T("${cv.sinLeer}", 11f, 800, C.carbon)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    e != null -> MiosVacio("No pudimos cargar tus mensajes", e, "Probar de nuevo", { alcance.launch { cargar() } }, Modifier.padding(28.dp))
                    else -> Puntitos(p.acento)
                }
            }
        }
    }
}

/** Chat 1 a 1 (GET/POST /v1/privados/{id}), con las piezas del chat del plan. Es `VistaPrivado` en iOS. */
@Composable
fun Privado(ir: Ir, conId: String, nombre: String) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val yo = c.sesion.usuarioId
    var persona by remember(conId) { mutableStateOf<Perfil?>(null) }
    val quien = nombre.ifEmpty { persona?.nombre.orEmpty() }
    val lineas = remember(conId) { mutableStateListOf<MiosLinea>() }
    var cargado by remember(conId) { mutableStateOf(false) }
    var error by remember(conId) { mutableStateOf<String?>(null) }
    var escribe by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var opciones by remember { mutableStateOf(false) }
    var reportando by remember { mutableStateOf(false) }
    /** Último aviso de «escribiendo» que se mandó (como mucho uno cada 2 s). */
    var ultimoEscribiendo by remember { mutableStateOf(0L) }

    LaunchedEffect(conId) {
        // Si llega desde un aviso no trae el nombre (ni la foto): salen del perfil.
        persona = runCatching { c.api.persona(conId) }.getOrNull()
        val q = nombre.ifEmpty { persona?.nombre.orEmpty() }
        try {
            lineas.clear(); lineas.addAll(c.api.privados(conId).privados.map { it.linea(yo, q) })
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = mensajeDe(e)
        }
        cargado = true
    }
    LaunchedEffect(conId) {
        c.tiempoReal.encender()
        c.tiempoReal.eventos.collect { ev ->
            val m = ev.privado
            if (ev.tipo == "privado" && m != null && (m.deId == conId || m.aId == conId)) {
                if (lineas.none { it.id == m.id }) lineas.add(m.linea(yo, nombre.ifEmpty { persona?.nombre.orEmpty() }))
                if (m.deId == conId) escribe = null
            } else if (ev.tipo == "escribiendo" && ev.privadoDe == conId) {
                // «escribiendo» en privados (contrato §10): llega con el id de quien escribe.
                val n = nombre.ifEmpty { persona?.nombre.orEmpty() }
                escribe = (if (n.isEmpty()) "Escribiendo" else miosPrimer(n)) to System.currentTimeMillis()
            }
        }
    }
    LaunchedEffect(escribe) { if (escribe != null) { delay(4000); escribe = null } }

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize()) {
            Encabezado(quien, null, { ir.atras() }, Modifier.padding(start = 16.dp, end = 16.dp, top = arriba + 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(quien, persona?.fotos?.firstOrNull(), 36.dp, Modifier.tocable { ir.persona(conId, "privado") }, tamTexto = 14f)
                    BotonIcono(
                        I.puntos, { opciones = true }, Modifier.semantics { contentDescription = "Más opciones" },
                        gel = if (p.oscuro) Gels.chipOscuro else Gels.chipClaro, color = Color.Transparent, relleno = p.texto, iconoTam = 20.dp, trazo = 0f,
                    )
                }
            }
            MiosVistaHilo(lineas, cargado, error, escribe?.first, Modifier.weight(1f), foto = persona?.fotos?.firstOrNull().orEmpty())
            MiosEntrada(abajo, {
                val ahora = System.currentTimeMillis()
                if (ahora - ultimoEscribiendo >= 2000) {
                    ultimoEscribiendo = ahora
                    c.tiempoReal.escribiendoPrivado(conId)
                }
            }) { t ->
                try {
                    val m = c.api.escribirPrivado(conId, t)
                    if (lineas.none { it.id == m.id }) lineas.add(m.linea(yo, quien))
                    true
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    avisos.error(mensajeDe(e)); false
                }
            }
        }
        // Menú: reportar o bloquear a la otra persona.
        val n = miosPrimer(quien)
        if (opciones) HojaOpciones({ opciones = false }, listOf(
            "Reportar a $n" to { reportando = true },
            "Bloquear a $n" to {
                alcance.launch {
                    try {
                        c.api.bloquear(conId)
                        avisos.ok("Bloqueaste a $n. No se van a ver más.")
                        ir.atras()
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        avisos.error(mensajeDe(e))
                    }
                }
            },
        ))
        if (reportando) HojaReporte({ reportando = false }) { motivo ->
            alcance.launch {
                try {
                    c.api.reportar(Reporte(usuarioId = conId, motivo = motivo))
                    avisos.ok("Gracias. Revisamos el perfil de $n en menos de 24 h.")
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    avisos.error(mensajeDe(e))
                }
            }
        }
    }
}
