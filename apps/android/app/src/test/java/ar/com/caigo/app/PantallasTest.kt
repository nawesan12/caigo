package ar.com.caigo.app

import android.app.Application
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.com.caigo.app.app.CaigoTema
import ar.com.caigo.app.app.Contenedor
import ar.com.caigo.app.app.Destino
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.app.Pestana
import ar.com.caigo.app.app.Principal
import ar.com.caigo.app.nucleo.Api
import ar.com.caigo.app.nucleo.Mazo
import ar.com.caigo.app.nucleo.Pases
import ar.com.caigo.app.nucleo.Perfil
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.nucleo.Planes
import ar.com.caigo.app.nucleo.Plus
import ar.com.caigo.app.nucleo.ResultadoTap
import ar.com.caigo.app.nucleo.Solicitudes
import ar.com.caigo.app.nucleo.json
import ar.com.caigo.app.pantallas.gente.*
import ar.com.caigo.app.pantallas.ingreso.*
import ar.com.caigo.app.pantallas.mios.*
import ar.com.caigo.app.pantallas.pagos.*
import ar.com.caigo.app.pantallas.planes.*
import ar.com.caigo.app.piezas.Avisos
import ar.com.caigo.app.piezas.CapaAvisos
import ar.com.caigo.app.piezas.LocalAvisos
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Grano
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.LocalPaleta
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.crossfade
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La API simulada (ApiFalsa en CaigoTests/PantallasTests.swift): responde por «MÉTODO ruta» o por ruta (GET) con
 *  pruebas/respuestas.json, el mismo archivo que lee iOS. La query no se mira. Lo que no está: GET → 404, lo demás →
 *  200 con `{}`. Las fotos (fotos.prueba/<nombre>.jpg) salen de res/drawable-nodpi de la app. */
class ApiFalsa(private val tabla: Map<String, String?>, private val app: Application) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val r = Response.Builder().request(req).protocol(Protocol.HTTP_1_1)
        if (req.url.host == "fotos.prueba") {
            val nombre = req.url.pathSegments.last().substringBeforeLast('.')
            val id = app.resources.getIdentifier(nombre, "drawable", app.packageName)
            if (id == 0) return r.code(404).message("No existe").body("".toResponseBody(null)).build()
            val bytes = app.resources.openRawResource(id).use { it.readBytes() }
            return r.code(200).message("OK").body(bytes.toResponseBody("image/jpeg".toMediaType())).build()
        }
        val ruta = req.url.encodedPath.let { java.net.URLDecoder.decode(it, "UTF-8") }
        val cuerpo = if (tabla.containsKey("${req.method} $ruta")) tabla["${req.method} $ruta"] else if (req.method == "GET") tabla[ruta] else "{}"
        val codigo = if (cuerpo == null) 404 else 200
        return r.code(codigo).message(if (codigo == 200) "OK" else "No existe")
            .body((cuerpo ?: "{}").toResponseBody("application/json".toMediaType())).build()
    }

    companion object {
        /** Las respuestas compartidas con iOS (apps/pruebas/respuestas.json, la copia herramientas/paridad.sh). */
        val respuestas: Map<String, String> by lazy {
            val raiz = json.parseToJsonElement(ApiFalsa::class.java.getResource("/pruebas/respuestas.json")!!.readText()).jsonObject
            raiz.filterKeys { !it.startsWith("_") }.mapValues { json.encodeToString(JsonElement.serializer(), it.value) }
        }
    }
}

/** Las rutas de CaigoNav (app/Navegacion.kt), vacías: una pantalla que navega (Arranque, «atrás») no rompe la prueba. */
private val rutas = listOf(
    "arranque", "intro", "ingresar", "debug", "mail", "datos", "telefono", "selfie?volver={volver}", "intenciones?editar={editar}",
    "verificacion", "dni?plan={plan}", "principal?tab={tab}&modo={modo}", "plan/{id}", "plan/slug/{slug}", "crear",
    "editarplan/{id}", "publicado/{id}", "persona/{id}?desde={desde}", "recibidos?modo={modo}&tab={tab}", "solicitudes/{id}",
    "chat/{id}", "privados", "privado/{id}?nombre={nombre}", "checkin/{id}", "repetimos/{id}", "qr/{id}", "escanear/{id}",
    "plus?motivo={motivo}", "checkout/{id}?cantidad={cantidad}", "entrada/{id}?plan={plan}", "qrentrada/{id}", "pases",
    "tap?plan={plan}", "adentro", "editar", "gente",
)

/** Clon de CaigoTests/PantallasTests.swift: monta cada pantalla contra la API simulada, con la sesión de Martina (u1), a
 *  390 × 844 dp como la ventana de iOS, y guarda la captura en claro y oscuro (build/capturas/<Nombre>-claro|oscuro.png:
 *  `testNombre` en iOS). Falla si alguna pantalla se rompe al montarse. Al sumar una pantalla, sumarla acá y en iOS. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Application pelada: App.onCreate arma su propio Contenedor (y Push) contra la API de verdad.
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class PantallasTest {
    @get:Rule val compose = createComposeRule()

    private inline fun <reified T> dato(ruta: String): T =
        json.decodeFromString(ApiFalsa.respuestas[ruta] ?: error("La API simulada no tiene $ruta"))

    private fun plan(id: String): Plan = dato("/v1/planes/$id")

    @After fun limpiar() { Api.interceptorPrueba = null }

    /** El Contenedor de la app contra la API simulada, con sesión y mi perfil ya cargados. `extra` pisa respuestas
     *  (null: la ruta da 404). */
    private fun contenedor(extra: Map<String, String?> = emptyMap(), preparar: (Contenedor) -> Unit = {}): Contenedor {
        val app = RuntimeEnvironment.getApplication()
        Grano.cargar(app)
        Api.interceptorPrueba = ApiFalsa(ApiFalsa.respuestas + extra, app)
        val c = Contenedor(app)
        // La sesión se guarda cifrada con Tink; sin Android Keystore (Robolectric) Tink guarda la llave en claro.
        runCatching { runBlocking { c.sesion.guardar("cs_prueba", "u1") } }
        c.yo.value = dato<Perfil>("/v1/yo")
        // En la app lo marca Principal al contestarse el pedido de avisos; Ahora lo espera para pedir la ubicación.
        c.avisosResueltos.value = true
        // Sin bitmaps de hardware (Robolectric no los dibuja), sin fundido y sin cambiar de hilo: la foto tiene que estar en la captura.
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(app).components { add(OkHttpNetworkFetcherFactory(callFactory = { c.http })) }
                .allowHardware(false).crossfade(false)
                // Todo en el mismo hilo: con el reloj quieto, la foto llega antes de la captura.
                .coroutineContext(kotlinx.coroutines.Dispatchers.Unconfined).build(),
        )
        preparar(c)
        return c
    }

    /** Deja correr la carga: las respuestas llegan por el hilo de OkHttp y vuelven al principal. */
    private fun asentar(ms: Long) {
        repeat(12) {
            Thread.sleep(60)
            compose.mainClock.advanceTimeBy(ms / 12)
            compose.waitForIdle()
        }
    }

    /** Monta la vista con el Contenedor, los avisos y un Ir de verdad (el NavigationStack de iOS) y captura claro y oscuro. */
    private fun montar(nombre: String, c: Contenedor = contenedor(), espera: Long = 1500, vista: @Composable (Ir) -> Unit) {
        val tema = mutableStateOf("claro")
        val avisos = Avisos()
        // El reloj lo mueve asentar(): con avance solo, las animaciones infinitas (el pulso de Ahora) no dejan quieta la app.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val nav = rememberNavController()
            val ir = remember(nav) { Ir(nav) }
            CompositionLocalProvider(LocalApp provides c, LocalAvisos provides avisos) {
                CaigoTema(tema.value) {
                    Box(Modifier.fillMaxSize().background(LocalPaleta.current.fondo)) {
                        NavHost(nav, startDestination = "prueba", enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                            composable("prueba") { vista(ir) }
                            rutas.forEach { composable(it) { Box(Modifier.fillMaxSize()) } }
                        }
                        CapaAvisos(avisos, 0.dp)
                    }
                }
            }
        }
        for (t in listOf("claro", "oscuro")) {
            tema.value = t
            asentar(espera)
            compose.onRoot().captureRoboImage("build/capturas/$nombre-$t.png")
        }
    }

    /** Las piezas sueltas, una abajo de la otra (el ScrollView + VStack de iOS). */
    @Composable private fun Piezas(contenido: @Composable ColumnScope.() -> Unit) =
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { contenido() }

    /** La respuesta de la API simulada se lee con los modelos de Android (si cambia una forma, salta acá). */
    @Test fun respuestasDecodifican() {
        assertEquals("Martina", dato<Perfil>("/v1/yo").nombre)
        assertEquals(6, dato<Planes>("/v1/planes/explorar").planes.size)
        assertEquals("entrada", plan("p3").cobro)
        assertEquals(2, dato<Solicitudes>("/v1/planes/p2/solicitudes").solicitudes.size)
        assertEquals(4, dato<Mazo>("/v1/gente/mazo").personas.size)
        assertEquals("TK-7Q2M9X", dato<Pases>("/v1/pases").entradas.first().codigo)
        assertEquals(3, dato<Plus>("/v1/plus").planes.size)
        assertEquals("El Faro Bar", dato<ResultadoTap>("POST /v1/tap").lugar)
    }

    // MARK: Principal (las cuatro pestañas)

    @Test fun principal() = montar("Principal") { Principal(it, Pestana.Ahora, null) }
    @Test fun principalExplorar() = montar("PrincipalExplorar") { Principal(it, Pestana.Explorar, null) }
    @Test fun principalMis() = montar("PrincipalMis") { Principal(it, Pestana.Mis, null) }
    @Test fun principalPerfil() = montar("PrincipalPerfil") { Principal(it, Pestana.Perfil, null) }

    // MARK: Planes

    @Test fun ahora() = montar("Ahora") { Ahora(it, null) }
    @Test fun ahoraGente() = montar("AhoraGente") { Ahora(it, "gente") }
    @Test fun ahoraCitas() = montar("AhoraCitas") { Ahora(it, "citas") }
    @Test fun ahoraVacio() = montar("AhoraVacio", contenedor(mapOf("/v1/planes/ahora" to """{"planes": []}"""))) { Ahora(it, null) }
    @Ignore("Falta en Android: PlanesVacioAhora (va privada adentro de Ahora.kt)")
    @Test fun planesVacioAhora() {}
    @Ignore("MapLibre no carga su librería nativa en Robolectric: hace falta que MapaAhora no arme el MapView en las pruebas")
    @Test fun mapaAhora() {
        val planes = dato<Planes>("/v1/planes/ahora").planes
        montar("MapaAhora") { MapaAhora(it, planes, -38.0023 to -57.5575, null, {}, "", {}, {}) }
    }
    @Test fun explorar() = montar("Explorar") { Explorar(it) }
    @Test fun detallePlan() = montar("DetallePlan") { DetallePlan(it, id = "p1") }
    @Test fun detallePlanMio() = montar("DetallePlanMio") { DetallePlan(it, id = "p2") }
    @Test fun detallePlanEntrada() = montar("DetallePlanEntrada") { DetallePlan(it, id = "p3") }
    @Test fun detallePlanSena() = montar("DetallePlanSena") { DetallePlan(it, id = "p6") }
    @Test fun detallePlanAjeno() = montar("DetallePlanAjeno") { DetallePlan(it, id = "p4") }
    @Test fun detallePlanSlug() = montar("DetallePlanSlug") { DetallePlan(it, slug = "futbol-5-playa-grande") }
    @Test fun crear() = montar("Crear") { Crear(it) }
    @Test fun crearEditar() = montar("CrearEditar") { Crear(it, editar = "p2") }
    @Test fun publicado() = montar("Publicado") { Publicado(it, "p2") }
    @Test fun hojaConfirmar() = montar("HojaConfirmar") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            HojaConfirmar(true, "¿Cancelás el plan?", "Les avisamos a los que van.", "Cancelar plan", {}, {})
        }
    }
    @Test fun piezasPlanes() {
        val p = plan("p1")
        montar("PiezasPlanes", espera = 500) { ir ->
            Piezas {
                TarjetaPlan(p, ir)
                TarjetaPlan(plan("p3"), ir)
                AccionTarjeta(plan("p4"), ir) {}
                PlanesBeneficio("2x1 en tragos hasta las 23", false)
                PlanesBeneficio("2x1 en tragos hasta las 23", true)
                ChipFoto("Faltan 3", true)
                ChipDestacado()
                PlanesChipPlus()
            }
        }
    }

    // MARK: Gente

    @Test fun mazoGente() = montar("MazoGente") { MazoGente(it, "gente") }
    @Test fun mazoCitas() = montar("MazoCitas") { MazoGente(it, "citas") }
    @Test fun mazoVacio() = montar("MazoVacio", contenedor(mapOf("/v1/gente/mazo" to """{"personas": []}"""))) { MazoGente(it, "gente") }
    @Ignore("Falta en Android: GenteCarta (va privada adentro de MazoGente.kt)")
    @Test fun genteCarta() {}
    @Test fun genteHojaInvitar() {
        val t = dato<Mazo>("/v1/gente/mazo").personas.first()
        montar("GenteHojaInvitar") { GenteHojaInvitar(it, t.id, t.nombre, {}, {}) }
    }
    @Test fun perfilPersona() = montar("PerfilPersona") { PerfilPersona(it, "u3", "mazo") }
    @Test fun perfilPersonaDeMiGente() = montar("PerfilPersonaDeMiGente") { PerfilPersona(it, "u2", "perfil") }
    @Test fun recibidos() = montar("Recibidos") { Recibidos(it, "gente", 0) }
    @Test fun recibidosInvitaciones() = montar("RecibidosInvitaciones") { Recibidos(it, "gente", 1) }
    @Test fun recibidosCitas() = montar("RecibidosCitas") { Recibidos(it, "citas", 0) }

    // MARK: Ingreso

    @Test fun arranque() = montar("Arranque") { Arranque(it) }
    @Test fun intro() = montar("Intro") { Intro(it) }
    @Ignore("Falta en Android: IngresoSlidePlanes/Amigos/Afuera, IngresoZonaFogon e IngresoComposicionFogon sueltas")
    @Test fun ingresoSlides() {}
    @Test fun ingresar() = montar("Ingresar") { Ingresar(it) }
    @Test fun debugToken() = montar("DebugToken") { DebugToken(it) }
    @Test fun ingresoMail() = montar("IngresoMail") { IngresoMail(it) }
    @Test fun datos() = montar("Datos") { Datos(it) }
    @Test fun telefono() = montar("Telefono") { Telefono(it) }
    @Test fun selfie() = montar("Selfie") { Selfie(it, false) }
    @Test fun intenciones() = montar("Intenciones") { Intenciones(it, false) }
    @Test fun intencionesEditar() = montar("IntencionesEditar") { Intenciones(it, true) }
    @Test fun verificacion() = montar("Verificacion") { Verificacion(it) }
    @Test fun verificacionPendiente() = montar("VerificacionPendiente",
        contenedor(mapOf("/v1/verificacion" to """{"telefono": "+5492235550142", "selfie": true, "dni": "ninguna"}"""))) { Verificacion(it) }
    @Test fun dni() = montar("Dni") { Dni(it, null) }
    // Suelta, como las demás piezas: con el margen de la pantalla (pegada al borde parecía rota).
    @Test fun elegirGenero() = montar("ElegirGenero", espera = 500) { Piezas { ElegirGenero("mujer", false, {}, {}) } }
    @Test fun hojaGenero() {
        val c = contenedor()
        montar("HojaGenero", c) {
            androidx.compose.runtime.LaunchedEffect(Unit) { c.preguntarGenero() }
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { HojaGenero(c) }
        }
    }
    @Ignore("Falta en Android: las piezas de IngresoComun (IngresoTitulo, IngresoCampo, IngresoCasillas…) como funciones públicas")
    @Test fun piezasIngreso() {}
    @Ignore("Falta en Android: IngresoCamaraVida e IngresoCamaraDni sueltas")
    @Test fun ingresoCamaras() {}

    // MARK: Míos

    @Test fun misPlanes() = montar("MisPlanes") { MisPlanes(it) }
    @Test fun misPlanesVacio() = montar("MisPlanesVacio", contenedor(mapOf("/v1/planes/mios" to """{"planes": []}"""))) { MisPlanes(it) }
    @Test fun miPerfil() = montar("MiPerfil") { Perfil(it) }
    @Test fun miGente() = montar("MiGente") { MiGente(it) }
    @Test fun editarPerfil() = montar("EditarPerfil") { EditarPerfil(it) }
    @Test fun completarPerfil() = montar("CompletarPerfil") { EditarPerfil(it, completar = true) }
    @Test fun solicitudes() = montar("Solicitudes") { Solicitudes(it, "p2") }
    @Test fun solicitudesVacio() = montar("SolicitudesVacio") { Solicitudes(it, "p1") }
    @Test fun miosVacioSolicitudes() = montar("MiosVacioSolicitudes", espera = 500) { MiosVacioSolicitudes(plan("p2")) {} }
    @Test fun chat() = montar("Chat") { Chat(it, "p1") }
    // `MiosHilo()` de iOS arranca sin cargar (los puntitos); cargado y vacío quedaba la pantalla en blanco.
    @Test fun miosVistaHilo() = montar("MiosVistaHilo", espera = 500) { MiosVistaHilo(emptyList(), false, null, null, Modifier.fillMaxSize()) }
    // Abajo, donde va en el chat (arriba de todo parecía una pantalla a medio cargar).
    @Test fun miosEntrada() = montar("MiosEntrada", espera = 500) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) { MiosEntrada(0.dp, {}, { true }) }
    }
    @Test fun privados() = montar("Privados") { Privados(it) }
    @Test fun privado() = montar("Privado") { Privado(it, "u3", "Lucía") }
    @Test fun checkin() = montar("Checkin") { Checkin(it, "p1") }
    @Test fun repetimos() = montar("Repetimos") { Repetimos(it, "p8") }
    @Test fun qrOrganizador() = montar("QrOrganizador") { QrOrganizador(it, "p2") }
    @Test fun escanearQr() = montar("EscanearQr") { EscanearQr(it, "p2") }
    @Ignore("Falta en Android: MiosCodigoQr suelta")
    @Test fun miosCodigoQr() {}
    @Ignore("Falta en Android: MiosEscaner suelto")
    @Test fun miosEscaner() {}
    @Test fun ajustesDeAvisos() = montar("AjustesDeAvisos") { AjustesDeAvisos(it) }
    @Test fun bloqueos() = montar("Bloqueos") { VistaBloqueos(it) }
    @Test fun actualizarApp() = montar("ActualizarApp", espera = 500) { ActualizarApp() }
    @Test fun piezasMios() = montar("PiezasMios", espera = 500) {
        Piezas {
            MiosVerificado()
            MiosSeccion("Tus planes")
            MiosOverline("Próximos")
            MiosVacio("Todavía no tenés planes", "Sumate a uno o armá el tuyo.", "Ver planes", {})
            MiosCampo("Diseñadora UX", {})
            MiosFila(I.gente, "Mi gente", "8 personas") {}
            MiosPill("Verificada", SolidColor(C.lima), C.carbon)
        }
    }

    // MARK: Pagos

    @Test fun plus() = montar("Plus") { Plus(it, "perfil") }
    @Test fun plusLimite() = montar("PlusLimite") { Plus(it, "limite") }
    @Test fun checkout() = montar("Checkout") { Checkout(it, "p3", 2) }
    @Test fun checkoutSena() = montar("CheckoutSena") { Checkout(it, "p6", 1) }
    @Test fun entrada() = montar("Entrada") { Entrada(it, "e1", "p3") }
    @Test fun entradaSena() = montar("EntradaSena") { Entrada(it, "e2", "p6") }
    @Test fun qrEntrada() = montar("QrEntrada") { QrEntrada(it, "e1") }
    @Test fun pases() = montar("Pases") { Pases(it) }
    @Test fun pasesVacio() = montar("PasesVacio", contenedor(mapOf("/v1/pases" to """{"entradas": [], "socios": []}"""))) { Pases(it) }
    @Test fun tap() = montar("Tap") { Tap(it, null) }
    @Ignore("Al responder POST /v1/tap navega a Adentro y Robolectric choca con el ciclo de vida del NavHost de prueba; Adentro tiene su test")
    @Test fun tapConPlaca() = montar("TapConPlaca", contenedor { it.tapPendiente = Destino.Tap("placa-faro", "", "") }) { Tap(it, "p3") }
    @Test fun adentro() = montar("Adentro", contenedor { it.ultimoTap = dato<ResultadoTap>("POST /v1/tap") }) { Adentro(it) }
    @Test fun piezasPagos() = montar("PiezasPagos", espera = 500) {
        Piezas {
            PagosPillPlus()
            PagosBoton("Pagar con Mercado Pago", PagosEstiloBoton.Negro) {}
            PagosBoton("Seña", PagosEstiloBoton.Lima) {}
            PagosEncabezado("Tu entrada") {}
            PagosCodigoQr("TK-7Q2M9X", Modifier.height(240.dp))
            PagosBotonWallet { "" }
        }
    }
    @Test fun pagosEscanerQr() = montar("PagosEscanerQr", espera = 500) { PagosEscanerQr(Modifier.fillMaxSize()) {} }
}
