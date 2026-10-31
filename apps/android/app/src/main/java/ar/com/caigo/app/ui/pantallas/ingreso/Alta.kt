package ar.com.caigo.app.ui.pantallas.ingreso

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.datos.CambiosPerfil
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Chip
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.estilo
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.px
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

/** Título y bajada de los pasos del registro: `700 34px/1.05`, -.8px; `15px/1.5` gris. */
@Composable
internal fun ingresoTitulo(titulo: String, bajada: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T(titulo, 34f, 700, C.carbon, alto = 1.05f, espaciado = px(-.8f, 34f))
        T(bajada, 15f, 400, C.texto2, alto = 1.5f)
    }
}

/** DD/MM/AAAA → fecha, o null si no es válida. */
private fun fechaDe(t: String): LocalDate? {
    val d = t.filter(Char::isDigit)
    if (d.length != 8) return null
    return runCatching { LocalDate.of(d.substring(4).toInt(), d.substring(2, 4).toInt(), d.substring(0, 2).toInt()) }.getOrNull()
}

/**
 * Muestra las barras de DD/MM/AAAA sin meterlas en el texto: el estado guarda sólo los dígitos y así
 * el cursor nunca queda antes de una barra (escribir rápido desordenaba la fecha).
 */
private val conBarras = androidx.compose.ui.text.input.VisualTransformation { t ->
    val d = t.text
    val salida = buildString {
        d.forEachIndexed { i, ch ->
            if (i == 2 || i == 4) append('/')
            append(ch)
        }
    }
    val mapa = object : androidx.compose.ui.text.input.OffsetMapping {
        override fun originalToTransformed(offset: Int) = offset + (if (offset > 2) 1 else 0) + (if (offset > 4) 1 else 0)
        override fun transformedToOriginal(offset: Int) = (offset - (if (offset > 2) 1 else 0) - (if (offset > 5) 1 else 0)).coerceIn(0, d.length)
    }
    androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(salida), mapa)
}

/**
 * Datos básicos (nombre, nacimiento, ciudad y zona). No está diseñada: se arma con las piezas de
 * 01d (título de 34 px, campos de 60 px, CTA lima).
 */
@Composable
fun Datos(ir: Ir) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    val yo = c.yo.value
    var nombre by remember { mutableStateOf(yo?.nombre.orEmpty()) }
    var nacimiento by remember { mutableStateOf("") }
    var ciudad by remember { mutableStateOf(yo?.ciudad?.takeIf { it.isNotBlank() } ?: "Mar del Plata") }
    var zona by remember { mutableStateOf(yo?.zona.orEmpty()) }
    var cargando by remember { mutableStateOf(false) }
    val fecha = fechaDe(nacimiento)
    val listo = nombre.isNotBlank() && fecha != null && zona.isNotBlank()

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 88.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                ingresoTitulo("¿Cómo te llamás?", "Así te ven en los planes. Tu fecha de nacimiento no aparece en tu perfil.")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T("Nombre", 14f, 700, C.carbon)
                    ingresoCampo(nombre, { nombre = it.take(40) }, Modifier.fillMaxWidth(), pista = "Tu nombre")
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T("Fecha de nacimiento", 14f, 700, C.carbon)
                    ingresoCampo(nacimiento, { nacimiento = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth(), pista = "DD/MM/AAAA", teclado = KeyboardType.Number, transformacion = conBarras)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T("¿Dónde estás?", 14f, 700, C.carbon)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Mar del Plata", "Villa Gesell").forEach { cdad ->
                            Chip(cdad, ciudad == cdad, { ciudad = cdad })
                        }
                    }
                    ingresoCampo(zona, { zona = it.take(60) }, Modifier.fillMaxWidth(), pista = if (ciudad == "Villa Gesell") "Zona" else "Güemes")
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 36.dp + abajo, top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                T("Caigo es solo para mayores de 18.", 13f, 400, C.texto2)
                Cta("Seguir", {
                    val f = fecha ?: return@Cta
                    if (Period.between(f, LocalDate.now()).years < 18) {
                        avisos.error("Caigo es sólo para mayores de 18.")
                        return@Cta
                    }
                    alcance.launch {
                        cargando = true
                        try {
                            c.yo.value = c.api.actualizarYo(
                                CambiosPerfil(nombre = nombre.trim(), nacimiento = f.toString(), ciudad = ciudad, zona = zona.trim()),
                            )
                            ir.alta(rutaDeAlta(c))
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            avisos.error(ingresoMensaje(e))
                        } finally {
                            cargando = false
                        }
                    }
                }, Modifier.fillMaxWidth(), habilitado = listo, cargando = cargando)
            }
        }
    }
}

/** 01d · Teléfono (Paso 1 de 2): número, código de 6 dígitos por SMS y "Reenviar en 0:24". */
@Composable
fun Telefono(ir: Ir) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var numero by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var pedido by remember { mutableStateOf(false) }
    var espera by remember { mutableIntStateOf(0) }
    var cargando by remember { mutableStateOf(false) }
    val foco = remember { FocusRequester() }

    LaunchedEffect(espera > 0) {
        while (espera > 0) { delay(1000); espera-- }
    }
    val pedir: () -> Unit = {
        alcance.launch {
            cargando = true
            try {
                val r = c.api.pedirCodigo(numero.filter(Char::isDigit))
                pedido = true
                espera = 30
                codigo = ""
                // El SMS está simulado: en debug la API devuelve el código y se completa solo.
                if (BuildConfig.DEBUG && r.codigoDePrueba.isNotBlank()) codigo = r.codigoDePrueba
                else runCatching { foco.requestFocus() }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(ingresoMensaje(e))
            } finally {
                cargando = false
            }
        }
    }

    Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba)) {
            ingresoBarraArriba(I.atras, { ir.alta("datos") }, { T("Paso 1 de 2", 14f, 700, C.texto2) },
                Modifier.padding(start = 20.dp, end = 24.dp, top = 12.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                ingresoTitulo("¿Cuál es tu número?", "Te mandamos un código por SMS. No aparece en tu perfil.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.height(60.dp).background(C.blanco, RoundedCornerShape(18.dp))
                            .border(1.5.dp, Color(0xFFE6E1F2), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { T("AR +54", 17f, 700, C.carbon) }
                    ingresoCampo(numero, { v -> numero = v.filter { it.isDigit() || it == ' ' }.take(16) }, Modifier.weight(1f), pista = "223 555 0142", teclado = KeyboardType.Phone)
                }
                AnimatedVisibility(pedido, enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { 20 }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            T("Código", 14f, 700, C.carbon)
                            if (espera > 0) T("Reenviar en 0:%02d".format(espera), 14f, 700, C.violeta)
                            else Box(Modifier.tocable { if (!cargando) pedir() }) { T("Reenviar", 14f, 700, C.violeta) }
                        }
                        ingresoCasillas(codigo, { codigo = it.filter(Char::isDigit).take(6) }, foco)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 36.dp + abajo, top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                T("Caigo es solo para mayores de 18.", 13f, 400, C.texto2)
                Cta("Seguir", {
                    if (!pedido) pedir() else alcance.launch {
                        cargando = true
                        try {
                            c.api.confirmarCodigo(codigo)
                            ir.alta(rutaDeAlta(c))
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            avisos.error(ingresoMensaje(e))
                        } finally {
                            cargando = false
                        }
                    }
                }, Modifier.fillMaxWidth(),
                    habilitado = if (pedido) codigo.length == 6 else numero.count(Char::isDigit) >= 8,
                    cargando = cargando)
            }
        }
    }
}

/** Las 6 casillas del código: las completas en gel lavanda, la actual con borde violeta. */
@Composable
private fun ingresoCasillas(codigo: String, alCambiar: (String) -> Unit, foco: FocusRequester) {
    BasicTextField(
        codigo, alCambiar,
        Modifier.fillMaxWidth().focusRequester(foco),
        singleLine = true,
        textStyle = estilo(1f, 400, Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = { campo ->
            Box {
                Box(Modifier.height(1.dp)) { campo() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { i ->
                        val d = codigo.getOrNull(i)
                        val m = Modifier.weight(1f).height(58.dp)
                        when {
                            d != null -> Box(m.gel(16.dp, Gels.chipClaro), contentAlignment = Alignment.Center) { T(d.toString(), 26f, 700, C.carbon) }
                            i == codigo.length -> Box(m.background(C.blanco, RoundedCornerShape(16.dp)).border(2.dp, C.violeta, RoundedCornerShape(16.dp)))
                            else -> Box(m.background(C.blanco, RoundedCornerShape(16.dp)).border(1.5.dp, Color(0xFFE6E1F2), RoundedCornerShape(16.dp)))
                        }
                    }
                }
            }
        },
    )
}
