package ar.com.caigo.app.pantallas.ingreso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.app.Contenedor
import ar.com.caigo.app.app.Destino
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.Clerk
import ar.com.caigo.app.nucleo.Perfil
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gel
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.externa
import ar.com.caigo.app.tema.px
import ar.com.caigo.app.tema.vertical
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Botón negro como "Agregar a Wallet" del prototipo (base `#333333`). */
internal val ingresoGelNegro = Gel(vertical(C.negro, C.negro), listOf(externa(4f, 0f, Color(0xFF333333))))

/**
 * Ingresar con Google (por Clerk). No está en el handoff: se arma con la composición y el fondo violeta
 * de 01c y el CTA lima. En Android no hay «Seguir con Apple».
 */
@Composable
fun Ingresar(ir: Ir) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf<String?>(null) }
    // La Custom Tab está abierta y todavía no volvió (para notar si la cerraron sin terminar).
    var enPestana by remember { mutableStateOf(false) }
    val destino by c.destino.collectAsState()
    ingresoBarraClara(true)

    suspend fun canjear(jwt: String) {
        val s = c.api.crearSesion(jwt)
        c.entrar(s.token, s.usuarioId)
        ir.alta(ingresoRutaDeAlta(c))
    }

    // La vuelta de la Custom Tab: ar.com.caigo.app://callback?rotating_token_nonce=…
    LaunchedEffect(destino) {
        val d = destino as? Destino.VueltaClerk ?: return@LaunchedEffect
        c.destino.value = null
        enPestana = false
        ocupado = ocupado ?: "google"
        try {
            canjear(IngresoWeb.completar(c, d.nonce))
        } catch (e: Clerk.ErrorClerk) {
            avisos.error(ingresoMensajeClerk(e.codigo))
        } catch (e: IngresoErrorClerk) {
            avisos.error(ingresoMensajeClerk(e.codigo))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            avisos.error(ingresoMensaje(e))
        } finally {
            ocupado = null
        }
    }

    // Cerrar la Custom Tab sin terminar es el `.canceledLogin` de ASWebAuthenticationSession.
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val o = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME && enPestana) {
                alcance.launch {
                    // La vuelta (onNewIntent) llega antes que el resume; se le da un respiro igual.
                    delay(400)
                    if (enPestana && c.destino.value !is Destino.VueltaClerk) {
                        enPestana = false
                        ocupado = null
                        avisos.error(ingresoMensajeClerk("cancelado"))
                    }
                }
            }
        }
        ciclo.lifecycle.addObserver(o)
        onDispose { ciclo.lifecycle.removeObserver(o) }
    }

    // Google: la Frontend API de Clerk en una Custom Tab.
    val entrarCon: (String) -> Unit = { proveedor ->
        ocupado = proveedor
        alcance.launch {
            try {
                IngresoWeb.abrir(ctx, c, proveedor)
                enPestana = true
            } catch (e: Clerk.ErrorClerk) {
                avisos.error(ingresoMensajeClerk(e.codigo))
                ocupado = null
            } catch (e: IngresoErrorClerk) {
                avisos.error(ingresoMensajeClerk(e.codigo))
                ocupado = null
            } catch (e: CancellationException) {
                ocupado = null
                throw e
            } catch (e: Exception) {
                avisos.error(ingresoMensaje(e))
                ocupado = null
            }
        }
    }

    Box(Modifier.fillMaxSize().ingresoFondoVioleta()) {
        ingresoContenido { _, nav ->
            Column(Modifier.fillMaxSize()) {
                ingresoZonaFogon(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 34.dp + nav),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    T("Hacé planes. Conocé gente.", 40f, 800, C.blanco, Modifier.padding(horizontal = 2.dp), alto = 1f, espaciado = px(-1.4f, 40f))
                    if (!c.clerk.disponible) {
                        T("El ingreso todavía no está disponible en esta versión.", 14f, 600, C.blanco.copy(alpha = .8f), Modifier.padding(horizontal = 2.dp), alto = 1.4f)
                    }
                    Spacer(Modifier.height(14.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Cta(
                            "Seguir con Google", { entrarCon("google") }, Modifier.fillMaxWidth(),
                            gel = Gels.limaChico, alto = 62.dp, radio = 22.dp, tam = 18f, color = C.carbon,
                            habilitado = c.clerk.disponible && ocupado == null, cargando = ocupado == "google",
                        )
                        if (BuildConfig.DEBUG) {
                            Box(Modifier.tocable { ir.debug() }.padding(4.dp)) { T("Entrar con token", 15f, 700, C.blanco) }
                        }
                    }
                }
            }
        }
    }
}

/** Sólo en debug: entrar pegando un token `cs_…` creado a mano en la base de desarrollo. */
@Composable
fun DebugToken(ir: Ir) {
    val c = LocalApp.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    if (!BuildConfig.DEBUG) {
        LaunchedEffect(Unit) { ir.atras() }
        return
    }
    ingresoClaro { Pantalla(oscuro = false) { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = arriba + 12.dp, bottom = 36.dp + abajo)) {
            ingresoBarraArriba(I.atras, { ir.atras() }, { T("Debug", 14f, 700, C.texto2) })
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Entrar con token", 34f, 700, C.carbon, alto = 1.05f, espaciado = px(-.8f, 34f))
                T("Pegá un token cs_… de la base de desarrollo.", 15f, 400, C.texto2, alto = 1.5f)
            }
            Spacer(Modifier.height(28.dp))
            ingresoCampo(token, { token = it.trim() }, Modifier.fillMaxWidth(), pista = "cs_…", tam = 15f)
            Spacer(Modifier.weight(1f))
            Cta("Seguir", {
                cargando = true
                alcance.launch {
                    try {
                        // Se valida el token pidiendo /v1/yo con él antes de guardarlo.
                        val yo: Perfil = c.api.decodificar(c.api.crudo("GET", c.api.url("/v1/yo"), autorizacion = token))
                        c.entrar(token, yo.id)
                        ir.alta(ingresoRutaDeAlta(c))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        debugSalirLocal(c)
                        avisos.error(ingresoMensaje(e))
                    } finally {
                        cargando = false
                    }
                }
            }, Modifier.fillMaxWidth(), habilitado = token.startsWith("cs_"), cargando = cargando)
        }
    } }
}

// PARA EL NÚCLEO: `Contenedor.salirLocal()` es privado; esto hace lo mismo (sin tocar la API).
private suspend fun debugSalirLocal(c: Contenedor) {
    c.tiempoReal.apagar()
    c.sesion.borrar()
    c.yo.value = null
}
