package ar.com.caigo.app.app


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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import ar.com.caigo.app.app.Contenedor
import ar.com.caigo.app.app.Destino
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.pantallas.gente.*
import ar.com.caigo.app.pantallas.ingreso.*
import ar.com.caigo.app.pantallas.mios.*
import ar.com.caigo.app.pantallas.pagos.*
import ar.com.caigo.app.pantallas.planes.*
import ar.com.caigo.app.piezas.Avisos
import ar.com.caigo.app.piezas.CapaAvisos
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.tema.LocalPaleta
import ar.com.caigo.app.tema.PaletaClara
import ar.com.caigo.app.tema.PaletaOscura

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
    val avisos = c.avisos
    val token by c.sesion.token.collectAsState()
    val destino by c.destino.collectAsState()
    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route

    // Sin sesión (salió, borró la cuenta o la API respondió 401) estando en la app: de vuelta a Ingresar.
    val habiaSesion = remember { booleanArrayOf(token != null) }
    LaunchedEffect(token) {
        val antes = habiaSesion[0]
        habiaSesion[0] = token != null
        if (antes && token == null && nav.currentBackStackEntry?.destination?.route?.startsWith("principal") == true) ir.alta("ingresar")
    }

    // Links, avisos y placas: se resuelven cuando hay sesión.
    LaunchedEffect(destino, token, rutaActual) {
        val d = destino ?: return@LaunchedEffect
        if (d is Destino.VueltaClerk) return@LaunchedEffect // la toma la pantalla de ingreso
        if (token == null) return@LaunchedEffect
        // Se espera a que termine el arranque y el alta: si no, el arranque limpia la pila y se pierde.
        if (rutaActual == null || rutaActual == "arranque" || rutaActual == "intro" || rutaActual == "ingresar" ||
            rutaActual == "debug" || rutaActual == "datos" || rutaActual == "telefono" || rutaActual.startsWith("selfie?") ||
            rutaActual.startsWith("intenciones?") || rutaActual == "completar") return@LaunchedEffect
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
                // solicitud.rechazada / solicitud.sacada: de vuelta a Ahora a buscar otro plan.
                "ahora" -> ir.principal(Pestana.Ahora)
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
                    composable("completar") { EditarPerfil(ir, completar = true) }
                    composable("gente") { MiGente(ir) }
                    composable("bloqueos") { VistaBloqueos(ir) }
                    composable("ajustesavisos") { AjustesDeAvisos(ir) }
                }
                HojaGenero(c)
                if (actualizar) ActualizarApp()
                CapaAvisos(avisos, WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            }
        }
    }
}
