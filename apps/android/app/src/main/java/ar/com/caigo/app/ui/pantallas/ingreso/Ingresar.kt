package ar.com.caigo.app.ui.pantallas.ingreso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.Destino
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.datos.Clerk
import ar.com.caigo.app.ui.Ir
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.piezas.abrirPestana
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.tema.C
import ar.com.caigo.app.ui.tema.Gel
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.vertical
import kotlinx.coroutines.launch

/** Botón negro como "Agregar a Wallet" del prototipo (base `#333333`). */
internal val ingresoGelNegro = Gel(vertical(C.negro, C.negro), listOf(externa(4f, 0f, Color(0xFF333333))))

/** Mensajes para los códigos de error del ida y vuelta con Clerk. */
private fun mensajeClerk(codigo: String): String = when (codigo) {
    "cancelado" -> "Cancelaste el ingreso. Probá de nuevo cuando quieras."
    "sin_clave" -> "El ingreso todavía no está disponible."
    else -> "No pudimos completar el ingreso. Probá de nuevo."
}

/**
 * Ingresar con Google o Apple (por Clerk). No está en el handoff: se arma con la composición y el
 * fondo violeta de 01c, el CTA lima y el botón negro de Wallet.
 */
@Composable
fun Ingresar(ir: Ir) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val avisos = LocalAvisos.current
    val alcance = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf<String?>(null) }
    val destino by c.destino.collectAsState()
    ingresoBarraClara(true)

    // La vuelta de la Custom Tab: ar.com.caigo.app://callback?rotating_token_nonce=…
    LaunchedEffect(destino) {
        val d = destino as? Destino.VueltaClerk ?: return@LaunchedEffect
        c.destino.value = null
        ocupado = ocupado ?: "google"
        try {
            val jwt = c.clerk.completar(d.nonce)
            val s = c.api.crearSesion(jwt)
            c.entrar(s.token, s.usuarioId)
            ir.alta(rutaDeAlta(c))
        } catch (e: Clerk.ErrorClerk) {
            avisos.error(mensajeClerk(e.codigo))
        } catch (e: Exception) {
            avisos.error(ingresoMensaje(e))
        } finally {
            ocupado = null
        }
    }

    val entrarCon: (String) -> Unit = { proveedor ->
        alcance.launch {
            ocupado = proveedor
            try {
                abrirPestana(ctx, c.clerk.iniciar(proveedor))
            } catch (e: Clerk.ErrorClerk) {
                avisos.error(mensajeClerk(e.codigo))
                ocupado = null
            } catch (e: Exception) {
                avisos.error(ingresoMensaje(e))
                ocupado = null
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().ingresoFondoVioleta()) {
        val e = ingresoEscala()
        ingresoContenido { _, nav ->
            ingresoComposicionFogon(e)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 34.dp + nav),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T("Hacé planes. Conocé gente.", 46f, 800, C.blanco, alto = .98f, espaciado = px(-1.8f, 46f))
                }
                Spacer(Modifier.height(e.y(18f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Cta(
                        "Seguir con Google", { entrarCon("google") }, Modifier.fillMaxWidth(),
                        gel = Gels.limaChico, alto = 62.dp, radio = 22.dp, tam = 18f, color = C.carbon,
                        habilitado = c.clerk.disponible && ocupado == null, cargando = ocupado == "google",
                    )
                    Cta(
                        "Seguir con Apple", { entrarCon("apple") }, Modifier.fillMaxWidth(),
                        gel = ingresoGelNegro, alto = 56.dp, radio = 20.dp, tam = 16f, color = C.blanco,
                        habilitado = c.clerk.disponible && ocupado == null, cargando = ocupado == "apple",
                    )
                    if (BuildConfig.DEBUG) {
                        Box(Modifier.tocable { ir.debug() }.padding(4.dp)) { T("Entrar con token", 15f, 700, C.blanco) }
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
    var usuario by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    if (!BuildConfig.DEBUG) {
        LaunchedEffect(Unit) { ir.atras() }
        return
    }
    ar.com.caigo.app.ui.piezas.Pantalla { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = arriba + 12.dp, bottom = 36.dp + abajo)) {
            ingresoBarraArriba(I.atras, { ir.atras() }, { T("Debug", 14f, 700, C.texto2) }, Modifier.padding(end = 0.dp))
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Entrar con token", 34f, 700, C.carbon, alto = 1.05f, espaciado = px(-.8f, 34f))
                T("Pegá un token cs_… de la base de desarrollo.", 15f, 400, C.texto2, alto = 1.5f)
            }
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ingresoCampo(token, { token = it.trim() }, Modifier.fillMaxWidth(), pista = "cs_…", tam = 15f)
                ingresoCampo(usuario, { usuario = it.trim() }, Modifier.fillMaxWidth(), pista = "id de usuario (opcional)", tam = 15f)
            }
            Spacer(Modifier.weight(1f))
            Cta("Seguir", {
                alcance.launch {
                    cargando = true
                    try {
                        c.entrar(token, usuario)
                        val yo = c.api.yo()
                        if (usuario.isBlank()) c.sesion.guardar(token, yo.id)
                        ir.alta(rutaDeAlta(c))
                    } catch (e: Exception) {
                        c.sesion.borrar()
                        avisos.error(ingresoMensaje(e))
                    } finally {
                        cargando = false
                    }
                }
            }, Modifier.fillMaxWidth(), habilitado = token.startsWith("cs_"), cargando = cargando)
        }
    }
}
