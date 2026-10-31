package ar.com.caigo.app.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import ar.com.caigo.app.Contenedor
import ar.com.caigo.app.Destino
import ar.com.caigo.app.LocalApp
import ar.com.caigo.app.ui.pantallas.ingreso.*
import ar.com.caigo.app.ui.pantallas.mios.*
import ar.com.caigo.app.ui.pantallas.planes.*
import ar.com.caigo.app.ui.piezas.Avisos
import ar.com.caigo.app.ui.piezas.CapaAvisos
import ar.com.caigo.app.ui.piezas.LocalAvisos
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.PaletaClara
import ar.com.caigo.app.ui.tema.PaletaOscura

/** Pestañas de la tab bar flotante. */
enum class Pestana { Ahora, Explorar, Mis, Perfil }

/**
 * Las acciones de navegación que usan las pantallas. Ninguna pantalla toca el NavController:
 * así cada una se puede armar y probar sola.
 */
class Ir(private val nav: NavController) {
    private fun ir(ruta: String) = nav.navigate(ruta) { launchSingleTop = true }
    private fun e(s: String) = Uri.encode(s)

    fun atras() { if (!nav.popBackStack()) nav.navigate("principal") }

    fun principal(pestana: Pestana = Pestana.Ahora, modo: String? = null) = nav.navigate("principal?tab=${pestana.name}&modo=${modo.orEmpty()}") {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }

    fun intro() = nav.navigate("intro") { popUpTo(0) { inclusive = true } }
    fun ingresar() = ir("ingresar")
    fun debug() = ir("debug")
    /** Siguiente paso del alta (datos → teléfono → selfie → intenciones) o la app. */
    fun alta(paso: String) = nav.navigate(paso) { popUpTo(0) { inclusive = true } }
    fun telefono() = ir("telefono")
    fun selfie(volver: Boolean = false) = ir("selfie?volver=$volver")
    fun intenciones(soloEditar: Boolean = false) = ir("intenciones?editar=$soloEditar")

    fun plan(id: String) = ir("plan/${e(id)}")
    fun planSlug(slug: String) = ir("plan/slug/${e(slug)}")
    fun crear() = ir("crear")
    fun editarPlan(id: String) = ir("editarplan/${e(id)}")
    fun publicado(id: String) = nav.navigate("publicado/${e(id)}") { popUpTo("crear") { inclusive = true } }
    fun solicitudes(planId: String) = ir("solicitudes/${e(planId)}")
    fun chat(planId: String) = ir("chat/${e(planId)}")
    fun privados() = ir("privados")
    fun privado(conId: String, nombre: String) = ir("privado/${e(conId)}?nombre=${e(nombre)}")
    fun checkin(planId: String) = ir("checkin/${e(planId)}")
    fun repetimos(planId: String) = ir("repetimos/${e(planId)}")
    fun qrOrganizador(planId: String) = ir("qr/${e(planId)}")
    fun escanearQr(planId: String) = ir("escanear/${e(planId)}")
    fun persona(id: String, desde: String = "perfil") = ir("persona/${e(id)}?desde=${e(desde)}")
    fun recibidos(modo: String, pestana: Int = 0) = ir("recibidos?modo=$modo&tab=$pestana")
    fun plus(motivo: String = "perfil") = ir("plus?motivo=$motivo")
    fun verificacion() = ir("verificacion")
    fun dni(planId: String? = null) = ir("dni?plan=${planId.orEmpty()}")
    fun checkout(planId: String, cantidad: Int) = ir("checkout/${e(planId)}?cantidad=$cantidad")
    fun entrada(entradaId: String, planId: String) = ir("entrada/${e(entradaId)}?plan=${e(planId)}")
    fun qrEntrada(entradaId: String) = ir("qrentrada/${e(entradaId)}")
    fun pases() = ir("pases")
    fun tap(planId: String? = null) = ir("tap?plan=${planId.orEmpty()}")
    fun adentro() = nav.navigate("adentro") { popUpTo("tap?plan={plan}") { inclusive = true } }
    fun editarPerfil() = ir("editar")
    fun gente() = ir("gente")
}

private fun NavBackStackEntry.arg(k: String): String = arguments?.getString(k).orEmpty()

/** Claro u oscuro según lo elegido en Perfil (auto: el del sistema). */
@Composable
fun CaigoTema(tema: String = "auto", contenido: @Composable () -> Unit) {
    val oscuro = when (tema) { "claro" -> false; "oscuro" -> true; else -> isSystemInDarkTheme() }
    val p = if (oscuro) PaletaOscura else PaletaClara
    // Íconos de la barra de estado según el tema elegido (no el del sistema).
    val vista = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val w = (vista.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(w, vista).apply {
            isAppearanceLightStatusBars = !oscuro
            isAppearanceLightNavigationBars = !oscuro
        }
    }
    CompositionLocalProvider(LocalPaleta provides p, contenido)
}

private val entrar: androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260)) }
private val salir: androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { fadeOut(tween(200)) }
private val volverEntrar: androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { fadeIn(tween(200)) }
private val volverSalir: androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(240)) }

/** Las pantallas que suben desde abajo (Crear, Plus, Tap…), como los overlays del prototipo. */
private fun NavGraphBuilder.hoja(ruta: String, contenido: @Composable (NavBackStackEntry) -> Unit) = composable(
    ruta,
    enterTransition = { slideInVertically(tween(300)) { it / 4 } + fadeIn(tween(300)) },
    exitTransition = { fadeOut(tween(200)) },
    popEnterTransition = { fadeIn(tween(200)) },
    popExitTransition = { slideOutVertically(tween(260)) { it / 4 } + fadeOut(tween(260)) },
) { contenido(it) }

@Composable
fun CaigoNav(c: Contenedor) {
    val nav = rememberNavController()
    val ir = remember(nav) { Ir(nav) }
    val avisos = remember { Avisos() }
    val token by c.sesion.token.collectAsState()
    val destino by c.destino.collectAsState()
    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route

    // Links, avisos y placas: se resuelven cuando hay sesión.
    LaunchedEffect(destino, token, rutaActual) {
        val d = destino ?: return@LaunchedEffect
        if (d is Destino.VueltaClerk) return@LaunchedEffect // la toma la pantalla de ingreso
        if (token == null) return@LaunchedEffect
        // Se espera a que termine el arranque y el alta: si no, el arranque limpia la pila y se pierde.
        if (rutaActual == null || rutaActual == "arranque" || rutaActual == "intro" || rutaActual == "ingresar" ||
            rutaActual == "debug" || rutaActual == "datos" || rutaActual == "telefono" || rutaActual.startsWith("selfie?") ||
            rutaActual.startsWith("intenciones?")) return@LaunchedEffect
        c.destino.value = null
        when (d) {
            is Destino.Plan -> ir.plan(d.id)
            is Destino.PlanSlug -> ir.planSlug(d.slug)
            is Destino.Tap -> { c.tapPendiente = d; ir.tap() }
            is Destino.Pantalla -> when (d.pantalla) {
                "solicitudes" -> d.plan?.let { ir.solicitudes(it) }
                "chat" -> d.plan?.let { ir.chat(it) }
                // Avisos del worker (contrato §3): recordatorio, cancelado, cambiado, sacada, repetimos…
                "detalle" -> d.plan?.let { ir.plan(it) }
                "checkin" -> d.plan?.let { ir.checkin(it) }
                "repetimos" -> d.plan?.let { ir.repetimos(it) }
                "recibidos" -> ir.recibidos(d.modo ?: "gente", 0)
                "invitaciones" -> ir.recibidos("gente", 1)
                "pases" -> ir.pases()
                else -> when {
                    d.privado != null -> ir.privado(d.privado, "")
                    d.plan != null -> ir.plan(d.plan)
                }
            }
            is Destino.VueltaClerk -> Unit
        }
    }

    val tema by c.sesion.tema.collectAsState()
    val actualizar by c.actualizar.collectAsState()
    CompositionLocalProvider(LocalApp provides c, LocalAvisos provides avisos) {
        CaigoTema(tema) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    nav, startDestination = "arranque",
                    enterTransition = entrar, exitTransition = salir,
                    popEnterTransition = volverEntrar, popExitTransition = volverSalir,
                ) {
                    composable("arranque") { Arranque(ir) }
                    composable("intro") { Intro(ir) }
                    hoja("ingresar") { Ingresar(ir) }
                    composable("debug") { DebugToken(ir) }
                    composable("datos") { Datos(ir) }
                    composable("telefono") { Telefono(ir) }
                    composable("selfie?volver={volver}") { Selfie(ir, it.arg("volver") == "true") }
                    composable("intenciones?editar={editar}") { Intenciones(ir, it.arg("editar") == "true") }
                    hoja("verificacion") { Verificacion(ir) }
                    hoja("dni?plan={plan}") { Dni(ir, it.arg("plan").ifBlank { null }) }

                    composable("principal?tab={tab}&modo={modo}", enterTransition = { fadeIn(tween(250)) }) {
                        val tab = runCatching { Pestana.valueOf(it.arg("tab")) }.getOrDefault(Pestana.Ahora)
                        Principal(ir, tab, it.arg("modo").ifBlank { null })
                    }
                    composable("plan/{id}") { DetallePlan(ir, id = it.arg("id")) }
                    composable("plan/slug/{slug}") { DetallePlan(ir, slug = it.arg("slug")) }
                    hoja("crear") { Crear(ir) }
                    hoja("editarplan/{id}") { Crear(ir, editar = it.arg("id")) }
                    composable("publicado/{id}") { Publicado(ir, it.arg("id")) }
                    composable("persona/{id}?desde={desde}") { PerfilPersona(ir, it.arg("id"), it.arg("desde")) }
                    composable("recibidos?modo={modo}&tab={tab}") { Recibidos(ir, it.arg("modo").ifBlank { "gente" }, it.arg("tab").toIntOrNull() ?: 0) }

                    composable("solicitudes/{id}") { Solicitudes(ir, it.arg("id")) }
                    composable("chat/{id}") { Chat(ir, it.arg("id")) }
                    composable("privados") { Privados(ir) }
                    composable("privado/{id}?nombre={nombre}") { Privado(ir, it.arg("id"), it.arg("nombre")) }
                    hoja("checkin/{id}") { Checkin(ir, it.arg("id")) }
                    composable("repetimos/{id}") { Repetimos(ir, it.arg("id")) }
                    hoja("qr/{id}") { QrOrganizador(ir, it.arg("id")) }
                    hoja("escanear/{id}") { EscanearQr(ir, it.arg("id")) }
                    hoja("plus?motivo={motivo}") { Plus(ir, it.arg("motivo").ifBlank { "perfil" }) }
                    composable("checkout/{id}?cantidad={cantidad}") { Checkout(ir, it.arg("id"), it.arg("cantidad").toIntOrNull() ?: 1) }
                    composable("entrada/{id}?plan={plan}") { Entrada(ir, it.arg("id"), it.arg("plan")) }
                    hoja("qrentrada/{id}") { QrEntrada(ir, it.arg("id")) }
                    composable("pases") { Pases(ir) }
                    hoja("tap?plan={plan}") { Tap(ir, it.arg("plan").ifBlank { null }) }
                    composable("adentro") { Adentro(ir) }
                    composable("editar") { EditarPerfil(ir) }
                    composable("gente") { MiGente(ir) }
                }
                HojaGenero(c)
                if (actualizar) Actualizar()
                CapaAvisos(avisos, WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            }
        }
    }
}
