package ar.com.caigo.app.pantallas.planes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Contenedor
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Confirma
import ar.com.caigo.app.nucleo.Contacto
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.nucleo.Dia
import ar.com.caigo.app.nucleo.ErrorApi
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.TodoBien
import ar.com.caigo.app.nucleo.ZonaAR
import ar.com.caigo.app.nucleo.mensajeDe
import ar.com.caigo.app.pantallas.mios.MiosFila
import ar.com.caigo.app.pantallas.mios.MiosGrupo
import ar.com.caigo.app.pantallas.mios.MiosHoja
import ar.com.caigo.app.pantallas.mios.miosNombres
import ar.com.caigo.app.pantallas.mios.miosOscura
import ar.com.caigo.app.pantallas.mios.miosPrimer
import ar.com.caigo.app.piezas.Avatares
import ar.com.caigo.app.piezas.Avisos
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.CtaVioleta
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Sistema
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.interna
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.rgba
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/*
 * El día del plan, la víspera y después (informe del 18/11). Se arma con piezas del handoff: el contador
 * oscuro del detalle (05), los Cta gel lima y blanco, la tarjeta neutra con filas de «Herramientas para
 * salir tranqui» y la hoja que sube (MiosHoja). Igual que DiaDelPlan.swift en el iPhone.
 */

/** «Hoy»: cuenta regresiva, Cómo llegar, «Llego en 10», Llegué y el escudo «¿Todo bien?». */
@Composable
internal fun DiaTarjeta(ir: Ir, p: Plan, todoBien: () -> Unit) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var ahora by remember { mutableStateOf(ZonedDateTime.now(ZonaAR)) }
    var avisando by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            ahora = ZonedDateTime.now(ZonaAR)
        }
    }
    val llegaron = p.van.filter { it.llego && it.id != c.sesion.usuarioId }
    val llegue = p.mio?.llegue == true
    Column(Modifier.fillMaxWidth().gel(24.dp, miosOscura).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(Dia.cuentaRegresiva(p.empiezaEn, ahora), 34f, 800, C.lima, alto = 1f, espaciado = px(-1.2f, 34f), unRenglon = true)
                T(
                    if (llegaron.isEmpty()) Cupo.somos(p.somos, p.total)
                    else "${miosNombres(llegaron.map { miosPrimer(it.nombre) })} ya ${if (llegaron.size == 1) "llegó" else "llegaron"}",
                    14f, 600, C.blancoCalido,
                )
            }
            Spacer(Modifier.width(8.dp))
            Avatares(p.van.map { it.nombre to it.foto }, total = p.somos, max = 4, tam = 30.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val lat = p.direccion?.lat ?: p.lat
            val lng = p.direccion?.lng ?: p.lng
            Cta("Cómo llegar", { Sistema.abrir(ctx, Dia.comoLlegar(lat, lng)) }, Modifier.weight(1f), gel = Gels.blanco, alto = 48.dp, radio = 16.dp, tam = 15f,
                icono = I.pin, iconoTrazo = 2.2f, iconoTam = 18.dp)
            // «Llego en 10»: un mensaje en el chat del plan (pasa por la moderación como cualquier otro).
            Cta("Llego en 10", {
                if (!avisando) {
                    avisando = true
                    alcance.launch {
                        try {
                            c.api.escribir(p.id, "Llego en 10")
                            avisos.ok("Les avisamos en el chat.")
                        } catch (e: Throwable) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            avisos.error(mensajeDe(e))
                        } finally {
                            avisando = false
                        }
                    }
                }
            }, Modifier.weight(1f), gel = Gels.blanco, alto = 48.dp, radio = 16.dp, tam = 15f, icono = I.relojCirculo, iconoTrazo = 2.2f, iconoTam = 18.dp,
                cargando = avisando)
        }
        if (llegue) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(C.lima, RoundedCornerShape(20.dp)),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icono(I.check, 18.dp, C.carbon, 3.4f)
                T("Llegaste", 17f, 800, C.carbon)
            }
        } else {
            Cta("Llegué", { ir.checkin(p.id) }, Modifier.fillMaxWidth(), alto = 56.dp, color = C.carbon, icono = I.check, iconoTrazo = 3.4f, iconoTam = 20.dp)
        }
        Row(
            Modifier.align(Alignment.CenterHorizontally).tocable(todoBien).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icono(I.escudo, 18.dp, C.lima, 2.2f)
            T("¿Todo bien?", 15f, 700, C.blancoCalido)
            if (p.mio?.todoBien == true) {
                Box(Modifier.background(C.lima, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    T("Activo", 11f, 800, C.carbon)
                }
            }
        }
    }
}

/** «¿Seguís yendo?» la víspera: Voy confirma; Me bajo libera el lugar (con la confirmación de siempre). */
@Composable
internal fun VisperaTarjeta(p: Plan, bajarme: () -> Unit, recargar: () -> Unit) {
    val c = LocalApp.current
    val pal = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var enviando by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().gel(22.dp, if (pal.oscuro) Gels.tarjetaOscura else Gels.neutra).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        T("${Fecha.cuando(p.empiezaEn)}. ¿Seguís yendo?", 18f, 700, pal.texto, alto = 1.2f)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cta("Voy", {
                enviando = true
                alcance.launch {
                    try {
                        c.api.confirmo(p.id)
                        avisos.ok("¡Genial! Te esperan.")
                        recargar()
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        avisos.error(mensajeDe(e))
                    } finally {
                        enviando = false
                    }
                }
            }, Modifier.weight(1f), alto = 50.dp, radio = 18.dp, tam = 16f, color = C.carbon, cargando = enviando)
            Cta("Me bajo", bajarme, Modifier.weight(1f), gel = Gels.blanco, alto = 50.dp, radio = 18.dp, tam = 16f)
        }
    }
}

/** Después del plan: «¿Cómo estuvo?» (o «¿Repetimos?» si organizó) y las fotos del plan. */
@Composable
internal fun DespuesTarjeta(ir: Ir, p: Plan) {
    MiosGrupo {
        MiosFila(I.estrella, if (p.esMio) "¿Repetimos?" else "¿Cómo estuvo?",
            if (p.mio?.carita != null) "Ya nos contaste. Gracias." else "Una carita y tu gente") { ir.repetimos(p.id) }
        val n = p.mio?.fotos ?: 0
        MiosFila(I.gente, if (n > 0) "Fotos del plan · $n" else "Fotos del plan", "Sólo las ven quienes fueron", ultima = true) { ir.fotosPlan(p.id) }
    }
}

/** Anota a la lista de espera. Un plan «Solo mujeres» / «Solo diversidades» pide confirmarlo (como al sumarse). */
internal suspend fun esperaAnotar(c: Contenedor, avisos: Avisos, p: Plan): Boolean = try {
    ar.com.caigo.app.pantallas.ingreso.conConfirmacion(c) { conf: Confirma -> c.api.esperar(p.id, confirma = conf) }
    avisos.ok("Te anotamos. Si se libera un lugar, te avisamos.")
    true
} catch (e: ErrorApi) {
    // La hoja de «Antes de sumarte» se cerró sin confirmar: como al sumarse, no se muestra el error.
    if (ar.com.caigo.app.nucleo.Generos.pide(e.codigo) == null) avisos.error(e.detalle)
    false
} catch (e: Throwable) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    avisos.error(mensajeDe(e))
    false
}

/** La lista de espera, abajo del detalle: anotarse en un plan completo, el lugar en la fila o «Se liberó un lugar». */
@Composable
internal fun EsperaAbajo(p: Plan, entrar: () -> Unit, recargar: () -> Unit, enviandoEntrar: Boolean) {
    val c = LocalApp.current
    val pal = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var enviando by remember { mutableStateOf(false) }
    val e = p.mio?.espera
    if (e != null) {
        if (e.estado == "avisado") {
            Box(Modifier.fillMaxWidth().background(C.lima, RoundedCornerShape(14.dp)).padding(10.dp), contentAlignment = Alignment.Center) {
                T("Se liberó un lugar. Es tuyo por 15 min.", 14f, 700, C.carbon, alinear = TextAlign.Center)
            }
            Cta("Entrar al plan", entrar, Modifier.fillMaxWidth(), alto = 58.dp, color = C.carbon, cargando = enviandoEntrar)
        } else {
            Box(
                Modifier.fillMaxWidth().height(58.dp).gel(20.dp, vertical(C.tinte1, C.tinte1), listOf(interna(3f, 6f, rgba(116, 86, 241, .2f)))),
                contentAlignment = Alignment.Center,
            ) { T("En la lista · ${e.posicion}°", 17f, 800, C.violetaTexto, alinear = TextAlign.Center) }
            T("Salir de la lista", 14f, 700, if (pal.oscuro) C.lima else C.violetaTexto, Modifier.tocable {
                alcance.launch {
                    try {
                        c.api.dejarDeEsperar(p.id)
                        recargar()
                    } catch (x: Throwable) {
                        if (x is kotlinx.coroutines.CancellationException) throw x
                        avisos.error(mensajeDe(x))
                    }
                }
            })
        }
    } else {
        Box(Modifier.fillMaxWidth().height(50.dp).gel(20.dp, Gels.limaBadge), contentAlignment = Alignment.Center) { T("Completo", 17f, 800, C.carbon) }
        CtaVioleta("Anotame en la lista", {
            if (!enviando) {
                enviando = true
                alcance.launch {
                    if (esperaAnotar(c, avisos, p)) recargar()
                    enviando = false
                }
            }
        }, Modifier.fillMaxWidth(), cargando = enviando)
    }
}

/**
 * «¿Todo bien?»: avisarle a un contacto si no hay check-in, compartir la ubicación, «Me quiero ir», 911,
 * Línea 144 y reportar.
 */
@Composable
internal fun BoxScope.HojaTodoBien(ir: Ir, p: Plan, abierta: Boolean, cerrar: () -> Unit, reportar: () -> Unit, recargar: () -> Unit) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val pal = LocalPaleta.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var estado by remember { mutableStateOf<TodoBien?>(null) }
    LaunchedEffect(abierta) { if (abierta) estado = runCatching { c.api.todoBien(p.id) }.getOrNull() }

    fun hacer(ok: String, f: suspend () -> Unit) {
        alcance.launch {
            try {
                f()
                avisos.ok(ok)
                cerrar()
                recargar()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                avisos.error(mensajeDe(e))
            }
        }
    }

    /** La ubicación actual va del teléfono al contacto: no pasa por Caigo ni se guarda. */
    fun compartirUbicacion(k: Contacto?) {
        alcance.launch {
            val u = c.ubicacion()
            if (u == null) {
                avisos.error("No pudimos saber dónde estás.")
                return@launch
            }
            val texto = "Estoy en ${p.titulo}: ${Dia.linkUbicacion(u.first, u.second)}"
            if (k != null && k.esTelefono) Sistema.abrir(ctx, Dia.whatsapp(k.contacto, texto)) else Sistema.compartir(ctx, texto)
        }
    }

    MiosHoja(abierta, cerrar, "¿Todo bien?") {
        val t = estado
        val contacto = t?.contacto ?: t?.contactos?.firstOrNull()
        MiosGrupo {
            val activo = t?.contacto
            if (t != null && t.activo && activo != null) {
                MiosFila(I.escudo, "Avisamos a ${activo.mostrar}", "Si a las ${Fecha.hora(t.avisaEn)} no hiciste check-in. Tocá para apagarlo.") {
                    hacer("Listo, lo apagamos.") { c.api.apagarTodoBien(p.id) }
                }
            } else if (t != null && t.contactos.isNotEmpty()) {
                t.contactos.forEachIndexed { i, k ->
                    MiosFila(I.escudo, "Avisale a ${k.mostrar} si no llego", "A la media hora, si no hice check-in") {
                        hacer("Si no llegás, le avisamos a ${k.mostrar}.") { estado = c.api.activarTodoBien(p.id, i) }
                    }
                }
            } else {
                MiosFila(I.usuario, "Sumá un contacto de confianza", "Le avisamos si no llegás") {
                    cerrar()
                    ir.verificacion()
                }
            }
            MiosFila(I.ubicacion, "Compartir mi ubicación", "Sale de tu teléfono; no la guardamos") { compartirUbicacion(contacto) }
            if (contacto != null && contacto.esTelefono) {
                MiosFila(I.usuario, "Me quiero ir", "Llamar a ${contacto.mostrar}") {
                    Sistema.abrir(ctx, "tel:" + contacto.contacto.filter { it.isDigit() || it == '+' })
                }
            }
            MiosFila(I.adelante, "Pedir un auto", "Para volver ya", ultima = true) { Sistema.abrir(ctx, Dia.PEDIR_AUTO) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cta("Llamar al 911", { Sistema.abrir(ctx, "tel:911") }, Modifier.weight(1f), gel = Gels.coralBadge, alto = 54.dp, radio = 18.dp, tam = 16f, color = C.carbon)
            CtaVioleta("Línea 144", { Sistema.abrir(ctx, "tel:144") }, Modifier.weight(1f))
        }
        T("La 144 atiende violencia de género, gratis y las 24 h.", 12f, 400, pal.texto2, Modifier.fillMaxWidth(), alinear = TextAlign.Center)
        T("Reportar algo del plan", 14f, 700, if (pal.oscuro) C.lima else C.violetaTexto, Modifier.align(Alignment.CenterHorizontally).tocable {
            cerrar()
            reportar()
        })
    }
}
