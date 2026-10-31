package ar.com.caigo.app.ui.pantallas.mios

import androidx.compose.runtime.derivedStateOf
import ar.com.caigo.app.datos.Reporte
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
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.datos.Mensaje
import ar.com.caigo.app.datos.Privado as PrivadoMsj
import ar.com.caigo.app.datos.mensajeDe
import ar.com.caigo.app.logica.Cupo
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Avatar
import ar.com.caigo.app.ui.piezas.BotonIcono
import ar.com.caigo.app.ui.piezas.Encabezado
import ar.com.caigo.app.ui.piezas.Fecha
import ar.com.caigo.app.ui.piezas.Foto
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.Puntitos
import ar.com.caigo.app.ui.piezas.rememberCarga
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.estilo
import ar.com.caigo.app.ui.tema.gel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Un renglón del chat, sea del plan o privado. */
private data class Linea(val id: Long, val texto: String, val mio: Boolean, val sistema: Boolean, val autor: String, val foto: String)

private fun Mensaje.linea(yo: String) = Linea(id, texto, autor?.id == yo, autor == null, autor?.nombre.orEmpty(), autor?.foto.orEmpty())

/** 08 · Chat del plan, en tiempo real por el WebSocket. */
@Composable
fun Chat(ir: Ir, planId: String) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val yo = c.sesion.usuarioId
    val plan = rememberCarga(planId) { c.api.plan(planId) }
    val lineas = remember { mutableStateListOf<Linea>() }
    var cargado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var escribe by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var hayMas by remember { mutableStateOf(false) }
    var opciones by remember { mutableStateOf(false) }
    var reportando by remember { mutableStateOf(false) }
    var saliendo by remember { mutableStateOf(false) }

    LaunchedEffect(planId) {
        try {
            val ms = c.api.mensajes(planId).mensajes
            hayMas = ms.size >= PAGINA
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
            when {
                ev.tipo == "mensaje" && ev.mensaje?.planId == planId -> {
                    val m = ev.mensaje
                    if (lineas.none { it.id == m.id }) lineas.add(m.linea(yo))
                    if (m.autor?.nombre == escribe?.first) escribe = null
                }
                ev.tipo == "escribiendo" && ev.planId == planId && ev.quien != null && ev.quien.id != yo ->
                    escribe = miosPrimer(ev.quien.nombre) to System.currentTimeMillis()
            }
        }
    }
    // "Lu está escribiendo…" se apaga a los 4 s.
    LaunchedEffect(escribe) { if (escribe != null) { delay(4000); escribe = null } }

    suspend fun mandar(t: String): Boolean = try {
        val m = c.api.escribir(planId, t)
        if (lineas.none { it.id == m.id }) lineas.add(m.linea(yo))
        true
    } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        avisos.error(mensajeDe(e)); false
    }

    Pantalla { arriba, abajo ->
        val pl = plan.dato
        Column(Modifier.fillMaxSize()) {
            // Encabezado: volver, título, cupo y avatares.
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = arriba + 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BotonIcono(I.atras, { ir.atras() }, gel = if (p.oscuro) Gels.chipOscuro else Gels.icono, color = p.texto)
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
                    Row(Modifier.padding(start = 8.dp)) {
                        gente.take(3).forEachIndexed { i, g ->
                            Avatar(g.nombre, g.foto, 30.dp, Modifier.offset(x = (-8 * i).dp).zIndex(5f - i), colorBorde = p.fondo, tamTexto = 11f)
                        }
                    }
                }
                // Más: reportar, salir del plan (el botón de puntos del detalle).
                BotonIcono(I.puntos, { opciones = true }, gel = if (p.oscuro) Gels.chipOscuro else Gels.icono, color = Color.Transparent, relleno = p.texto, iconoTam = 20.dp, trazo = 0f)
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
                        val lugar = pl.lugar?.nombre ?: pl.lugarNombre.ifBlank { pl.zona }
                        T(listOf(Fecha.cuando(pl.empiezaEn), lugar).filter { it.isNotBlank() }.joinToString(" · "), 14f, 700, unRenglon = true)
                        val desde = t?.minusMinutes(30)?.let { "check-in desde ${Fecha.hora(it)}" }
                        T(listOfNotNull(pl.direccion?.texto?.ifBlank { null } ?: pl.zona.ifBlank { null }, desde).joinToString(" · "), 12f, 400, p.texto2, unRenglon = true)
                    }
                    T("Ver plan", 13f, 700, if (p.oscuro) C.lima else C.violetaTexto, Modifier.tocable { ir.plan(planId) })
                }
            }
            Hilo(lineas, cargado, error, escribe?.first, Modifier.weight(1f), hayMas) {
                // Paginar hacia atrás: los 100 anteriores al más viejo que hay.
                val primero = lineas.firstOrNull()?.id ?: return@Hilo
                runCatching { c.api.mensajes(planId, antes = primero).mensajes }.onSuccess { ms ->
                    hayMas = ms.size >= PAGINA
                    lineas.addAll(0, ms.filter { m -> lineas.none { it.id == m.id } }.map { it.linea(yo) })
                }.onFailure { avisos.error(mensajeDe(it)) }
            }
            Entrada(abajo, { c.tiempoReal.escribiendo(planId) }) { t -> mandar(t) }
        }
        val pl2 = plan.dato
        if (opciones && pl2 != null) ar.com.caigo.app.ui.pantallas.planes.HojaOpciones({ opciones = false }, buildList {
            add("Ver el plan" to { ir.plan(planId) })
            if (pl2.miEstado != "organizador") add("Reportar el plan" to { reportando = true })
            if (pl2.miEstado == "aceptada") add("Salir del plan" to { saliendo = true })
        })
        if (reportando) ar.com.caigo.app.ui.pantallas.planes.HojaReporte({ reportando = false }) { motivo ->
            alcance.launch {
                runCatching { c.api.reportar(Reporte(planId = planId, motivo = motivo)) }
                    .onSuccess { avisos.ok("Gracias. Lo revisa una persona del equipo en menos de 24 h.") }
                    .onFailure { avisos.error(mensajeDe(it)) }
            }
        }
        MiosHoja(saliendo, { saliendo = false }, "¿Salir del plan?") {
            T("Le avisamos a ${pl2?.organizador?.nombre?.ifBlank { null } ?: "quien organiza"} y dejás de ver el chat.", 15f, 400, p.texto2, alto = 1.5f)
            MiosGrupo {
                Box(Modifier.fillMaxWidth().tocable {
                    saliendo = false
                    alcance.launch {
                        runCatching { c.api.salir(planId) }
                            .onSuccess { avisos.ok("Listo, te bajaste del plan."); ir.atras() }
                            .onFailure { avisos.error(mensajeDe(it)) }
                    }
                }.padding(horizontal = 18.dp, vertical = 16.dp)) { T("Salir del plan", 15f, 700, C.coral2) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (p.oscuro) p.separador else C.lavanda))
                Box(Modifier.fillMaxWidth().tocable { saliendo = false }.padding(horizontal = 18.dp, vertical = 16.dp)) { T("Volver", 15f, 700) }
            }
        }
    }
}

/** Mensajes por página que devuelve la API (chat.Mensajes / chat.Hilo). */
private const val PAGINA = 100

/** Los mensajes (abajo de todo el último) y "X está escribiendo…". */
@Composable
private fun Hilo(
    lineas: List<Linea>, cargado: Boolean, error: String?, escribe: String?, modifier: Modifier,
    hayMas: Boolean = false, cargarAnteriores: suspend () -> Unit = {},
) {
    val p = LocalPaleta.current
    val lista = rememberLazyListState()
    // Baja al final sólo cuando llega un mensaje nuevo (no al traer los anteriores).
    LaunchedEffect(lineas.lastOrNull()?.id, escribe) {
        val n = lineas.size + (if (escribe != null) 1 else 0)
        if (n > 0) lista.animateScrollToItem(n - 1)
    }
    // Al llegar arriba de todo, trae los anteriores.
    var trayendo by remember { mutableStateOf(false) }
    val arriba by remember { derivedStateOf { lista.firstVisibleItemIndex == 0 && lista.layoutInfo.totalItemsCount > 0 } }
    LaunchedEffect(arriba, hayMas) {
        if (arriba && hayMas && !trayendo && cargado) {
            trayendo = true
            cargarAnteriores()
            trayendo = false
        }
    }
    Box(modifier.fillMaxWidth()) {
        when {
            !cargado -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Puntitos() }
            error != null && lineas.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                T(error, 15f, 500, p.texto2, alto = 1.5f, alinear = androidx.compose.ui.text.style.TextAlign.Center)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(), state = lista,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                if (hayMas) item(key = "mas") { Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { Puntitos(tam = 6.dp) } }
                items(lineas, key = { it.id }) { l -> Burbuja(l) }
                if (escribe != null) item(key = "escribe") {
                    T("$escribe está escribiendo…", 12f, 400, p.texto2, Modifier.padding(start = 36.dp))
                }
            }
        }
    }
}

@Composable
private fun Burbuja(l: Linea) {
    val p = LocalPaleta.current
    when {
        l.sistema -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.gel(13.dp, Gels.limaChico).padding(horizontal = 14.dp, vertical = 8.dp)) { T(l.texto, 13f, 700, C.carbon) }
        }
        l.mio -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier.widthIn(max = 250.dp)
                    .background(C.violeta, RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) { T(l.texto, 15f, 400, C.blanco, alto = 1.4f) }
        }
        else -> Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(l.autor, l.foto, 28.dp, borde = 0.dp, tamTexto = 11f)
            Column(Modifier.widthIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (l.autor.isNotBlank()) T(miosPrimer(l.autor), 11f, 700, p.texto2)
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
private fun Entrada(abajo: androidx.compose.ui.unit.Dp, alTipear: () -> Unit, enviar: suspend (String) -> Boolean) {
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
                { if (it.length <= 2000) { if (it.length > texto.length) alTipear(); texto = it } },
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
            Box(Modifier.size(44.dp).toque(!mandando) { mandar() }.gel(15.dp, miosVioletaChico), contentAlignment = Alignment.Center) {
                Icono(I.flecha, 18.dp, C.blanco, 2.4f)
            }
        }
    }
}

/** Mensajes privados: las conversaciones con la gente que compartió un plan con vos. */
@Composable
fun Privados(ir: Ir) {
    val c = LocalApp.current
    val p = LocalPaleta.current
    val convs = rememberCarga("privados") { c.api.conversaciones().conversaciones }
    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba + 6.dp)) {
            Encabezado("Mensajes", null, { ir.atras() }, Modifier.padding(horizontal = 16.dp))
            val lista = convs.dato
            when {
                lista == null && convs.error != null -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    MiosVacio("No pudimos cargar tus mensajes", convs.error!!, "Probar de nuevo", { convs.recargar() })
                }
                lista == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Puntitos() }
                lista.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    MiosVacio("Todavía no hay mensajes", "Podés escribirle a la gente con la que compartiste un plan.", "Ver mi gente", { ir.gente() })
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = abajo + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lista, key = { it.con.id }) { cv ->
                        Row(
                            Modifier.fillMaxWidth().tocable { ir.privado(cv.con.id, cv.con.nombre) }.gel(20.dp, miosGelTarjeta()).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(cv.con.nombre, cv.con.foto, 44.dp, borde = 0.dp, tamTexto = 17f)
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    T(cv.con.nombre, 15f, 700, modifier = Modifier.weight(1f), unRenglon = true)
                                    T(Fecha.hace(cv.ultimo.creadoEn), 12f, 400, p.texto2)
                                }
                                T((if (cv.ultimo.deId == c.sesion.usuarioId) "Vos: " else "") + cv.ultimo.texto, 13f, if (cv.sinLeer > 0) 700 else 400, if (cv.sinLeer > 0) p.texto else p.texto2, unRenglon = true)
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
        }
    }
}

private fun PrivadoMsj.linea(yo: String, nombre: String) = Linea(id, texto, deId == yo, false, if (deId == yo) "" else nombre, "")

/** Chat 1 a 1 (GET/POST /v1/privados/{id}), con las piezas del chat del plan. */
@Composable
fun Privado(ir: Ir, conId: String, nombre: String) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val yo = c.sesion.usuarioId
    // Si llega desde un aviso no trae el nombre: sale del perfil.
    val persona = rememberCarga(conId, "persona") { if (nombre.isBlank()) c.api.persona(conId) else null }
    val quien = nombre.ifBlank { persona.dato?.nombre.orEmpty() }
    val foto = persona.dato?.fotos?.firstOrNull()
    val lineas = remember { mutableStateListOf<Linea>() }
    var cargado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hayMas by remember { mutableStateOf(false) }
    var escribe by remember { mutableStateOf(0L) }
    var opciones by remember { mutableStateOf(false) }
    var reportando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    LaunchedEffect(conId, quien) {
        try {
            val ps = c.api.privados(conId).privados
            hayMas = ps.size >= PAGINA
            lineas.clear(); lineas.addAll(ps.map { it.linea(yo, quien) })
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = mensajeDe(e)
        }
        cargado = true
    }
    LaunchedEffect(conId) {
        c.tiempoReal.encender()
        c.tiempoReal.eventos.collect { ev ->
            // «escribiendo» en privados (contrato §10): llega con el id de quien escribe.
            if (ev.tipo == "escribiendo" && ev.escribePrivado == conId) { escribe = System.currentTimeMillis(); return@collect }
            val m = ev.privado ?: return@collect
            if (ev.tipo == "privado" && (m.deId == conId || m.aId == conId) && lineas.none { it.id == m.id }) {
                lineas.add(m.linea(yo, quien))
                if (m.deId == conId) escribe = 0L
            }
        }
    }
    LaunchedEffect(escribe) { if (escribe != 0L) { delay(4000); escribe = 0L } }

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize()) {
            Encabezado(quien, null, { ir.atras() }, Modifier.padding(start = 16.dp, end = 16.dp, top = arriba + 6.dp)) {
                Avatar(quien, foto, 36.dp, Modifier.tocable { ir.persona(conId, "privado") }, tamTexto = 14f)
                BotonIcono(I.puntos, { opciones = true }, gel = if (LocalPaleta.current.oscuro) Gels.chipOscuro else Gels.icono, color = Color.Transparent, relleno = LocalPaleta.current.texto, iconoTam = 20.dp, trazo = 0f)
            }
            Hilo(lineas.map { it.copy(foto = foto.orEmpty()) }, cargado, error, if (escribe != 0L) miosPrimer(quien) else null, Modifier.weight(1f), hayMas) {
                val primero = lineas.firstOrNull()?.id ?: return@Hilo
                runCatching { c.api.privados(conId, antes = primero).privados }.onSuccess { ps ->
                    hayMas = ps.size >= PAGINA
                    lineas.addAll(0, ps.filter { m -> lineas.none { it.id == m.id } }.map { it.linea(yo, quien) })
                }.onFailure { avisos.error(mensajeDe(it)) }
            }
            Entrada(abajo, { c.tiempoReal.escribiendoPrivado(conId) }) { t ->
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
        if (opciones) ar.com.caigo.app.ui.pantallas.planes.HojaOpciones({ opciones = false }, listOf(
            "Ver el perfil" to { ir.persona(conId, "privado") },
            "Reportar a ${miosPrimer(quien)}" to { reportando = true },
            "Bloquear a ${miosPrimer(quien)}" to {
                alcance.launch {
                    runCatching { c.api.bloquear(conId) }
                        .onSuccess { avisos.ok("Bloqueaste a ${miosPrimer(quien)}. No se van a ver más."); ir.atras() }
                        .onFailure { avisos.error(mensajeDe(it)) }
                }
            },
        ))
        if (reportando) ar.com.caigo.app.ui.pantallas.planes.HojaReporte({ reportando = false }) { motivo ->
            alcance.launch {
                runCatching { c.api.reportar(Reporte(usuarioId = conId, motivo = motivo)) }
                    .onSuccess { avisos.ok("Gracias. Revisamos el perfil de ${miosPrimer(quien)} en menos de 24 h.") }
                    .onFailure { avisos.error(mensajeDe(it)) }
            }
        }
    }
}
