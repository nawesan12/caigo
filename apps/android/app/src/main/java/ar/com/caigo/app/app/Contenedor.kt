package ar.com.caigo.app.app

import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.R

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ar.com.caigo.app.nucleo.Api
import ar.com.caigo.app.nucleo.Clerk
import ar.com.caigo.app.nucleo.EstadoPlus
import ar.com.caigo.app.nucleo.Perfil
import ar.com.caigo.app.nucleo.ResultadoTap
import ar.com.caigo.app.nucleo.Sesion
import ar.com.caigo.app.nucleo.TiempoReal
import ar.com.caigo.app.app.Push
import ar.com.caigo.app.tema.Grano
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** A dónde hay que ir por un link, un aviso o una placa. */
sealed interface Destino {
    data class Plan(val id: String) : Destino
    data class PlanSlug(val slug: String) : Destino
    data class Pantalla(val pantalla: String, val plan: String?, val privado: String?, val modo: String?) : Destino
    data class Tap(val placa: String, val picc: String, val cmac: String) : Destino
    data class VueltaClerk(val nonce: String?) : Destino
}

/**
 * Todo lo que comparten las pantallas: API, sesión, tiempo real, ubicación y destinos pendientes
 * (como Contenedor.swift del iPhone).
 */
class Contenedor(val ctx: Context) {
    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sesion = Sesion(ctx)
    val http = Api.http()
    val api = Api(BuildConfig.API, http, { sesion.token.value }, alVencer = { alcance.launch { salirLocal() } })
    val clerk = Clerk(ctx, http, BuildConfig.CLERK_PUBLICABLE)
    val tiempoReal = TiempoReal(BuildConfig.API, http, { sesion.token.value }, alcance)
    /** Los avisos (toasts) de toda la app: los usa también MainActivity al leer placas NFC. */
    val avisos = ar.com.caigo.app.piezas.Avisos()

    /** Mi perfil (se recarga al entrar y al editar). */
    val yo = MutableStateFlow<Perfil?>(null)
    /** Estado de Plus (se recarga al entrar a Perfil, al comprar y en el paywall). */
    val plus = MutableStateFlow<EstadoPlus?>(null)
    val destino = MutableStateFlow<Destino?>(null)
    /** Resultado del último tap en una placa (pantalla "Adentro"). */
    var ultimoTap: ResultadoTap? = null
    /** Placa leída (link o NFC) que la pantalla de Caigo Tap tiene que mandar a la API. */
    var tapPendiente: Destino.Tap? = null
    /** Mientras la pantalla de Caigo Tap está abierta, MainActivity lee placas NFC en primer plano. */
    val nfcActivo = MutableStateFlow(false)

    /** Recarga mi perfil; si falla, queda el que había. */
    suspend fun recargarYo(): Perfil? = runCatching { api.yo() }.getOrNull()?.also { yo.value = it } ?: yo.value

    /** Recarga el estado de Plus; si falla, queda el que había. */
    suspend fun recargarPlus(): EstadoPlus? = runCatching { api.plus() }.getOrNull()?.estado?.also { plus.value = it } ?: plus.value

    suspend fun entrar(token: String, usuarioId: String) {
        sesion.guardar(token, usuarioId)
        tiempoReal.encender()
        Push.registrar(this)
        alcance.launch { mandarUbicacion() }
    }

    suspend fun salir() {
        Push.olvidar(this)
        api.cerrarSesion()
        salirLocal()
    }

    /** Borra la cuenta en la API y, si salió bien, la sesión local (como al salir). */
    suspend fun borrarCuenta() {
        // El dispositivo se borra antes: después de la baja la sesión ya no sirve.
        Push.olvidar(this)
        api.borrarCuenta()
        salirLocal()
    }

    @Volatile
    private var ubicacionMandada: Long? = null

    /**
     * PUT /v1/yo/ubicacion (avisos de planes cerca): al abrir y al volver a primer plano, como mucho
     * una vez cada 30 minutos. Sólo si ya hay permiso: no se pide permiso para esto.
     */
    suspend fun mandarUbicacion() {
        if (sesion.token.value == null || !tienePermisoUbicacion()) return
        val u = ubicacionMandada
        if (u != null && System.currentTimeMillis() - u < 30 * 60_000L) return
        ubicacionMandada = System.currentTimeMillis()
        val (la, ln) = ubicacion() ?: run { ubicacionMandada = null; return }
        runCatching { api.ubicacion(la, ln) }.onFailure { ubicacionMandada = null }
    }

    /**
     * El pedido de avisos (Android 13+) ya se contestó o no hacía falta. Ahora espera esto para pedir la
     * ubicación: dos pedidos de permiso juntos hacen que Android descarte uno.
     */
    val avisosResueltos = MutableStateFlow(false)

    /** La app es más vieja que la mínima que pide la API (`/v1/version`): hay que actualizar. */
    val actualizar = MutableStateFlow(false)

    suspend fun chequearVersion() {
        val v = runCatching { api.version() }.getOrNull() ?: return
        if (v.pideActualizar(BuildConfig.VERSION_CODE)) actualizar.value = true
    }

    /** Pregunta de género pendiente (ante `falta_genero`): la hoja la responde con true si se guardó. */
    val preguntaGenero = MutableStateFlow<kotlinx.coroutines.CompletableDeferred<Boolean>?>(null)

    /** Abre la hoja de género y espera la respuesta. */
    suspend fun preguntarGenero(): Boolean {
        val d = kotlinx.coroutines.CompletableDeferred<Boolean>()
        preguntaGenero.value = d
        return try { d.await() } finally { if (preguntaGenero.value === d) preguntaGenero.value = null }
    }

    /** Cierra la sesión sólo en el teléfono (sin avisarle a la API). */
    suspend fun salirLocal() {
        tiempoReal.apagar()
        sesion.borrar()
        yo.value = null
        plus.value = null
    }

    fun tienePermisoUbicacion() =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Última ubicación conocida: sirve para no esperar al GPS cada vez que se abre una pantalla. */
    @Volatile
    private var ultima: Pair<Double, Double>? = null

    /** La última ubicación que ya se conoce, sin esperar (para no frenar la carga de una pantalla). */
    val ubicacionConocida: Pair<Double, Double>? get() = ultima

    /** Ubicación actual (FusedLocation). Sin permiso o sin señal: null. Usa la última si es reciente. */
    @SuppressLint("MissingPermission")
    suspend fun ubicacion(): Pair<Double, Double>? {
        if (!tienePermisoUbicacion()) return null
        ultima?.let { return it }
        val f = LocationServices.getFusedLocationProviderClient(ctx)
        return runCatching {
            withTimeoutOrNull(3500) {
                val ultima = f.lastLocation.await()
                val loc = if (ultima != null && System.currentTimeMillis() - ultima.time < 5 * 60_000) ultima
                else f.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await() ?: ultima
                loc?.let { it.latitude to it.longitude }
            }
        }.getOrNull()?.also { ultima = it }
    }

    /** Interpreta un link entrante: ar.com.caigo.app://…, https://caigo.com.ar/p/… o /t/…. */
    fun destinoDe(uri: Uri?): Destino? {
        if (uri == null) return null
        val seg = uri.pathSegments.orEmpty()
        return when {
            uri.scheme == Clerk.ESQUEMA && uri.host == "callback" -> Destino.VueltaClerk(uri.getQueryParameter("rotating_token_nonce"))
            uri.scheme == Clerk.ESQUEMA && uri.host == "p" && seg.isNotEmpty() -> Destino.PlanSlug(seg[0])
            uri.scheme == Clerk.ESQUEMA && uri.host == "plan" && seg.isNotEmpty() -> Destino.Plan(seg[0])
            uri.scheme == "https" && seg.size >= 2 && seg[0] == "p" -> Destino.PlanSlug(seg[1])
            (uri.scheme == "https" || uri.scheme == "ar.com.caigo.app") && seg.size >= 2 && seg[0] == "t" -> Destino.Tap(
                seg[1], uri.getQueryParameter("picc_data").orEmpty(), uri.getQueryParameter("cmac").orEmpty(),
            )
            uri.scheme == "ar.com.caigo.app" && uri.host == "t" && seg.isNotEmpty() -> Destino.Tap(
                seg[0], uri.getQueryParameter("picc_data").orEmpty(), uri.getQueryParameter("cmac").orEmpty(),
            )
            else -> null
        }
    }
}

val LocalApp = staticCompositionLocalOf<Contenedor> { error("Sin contenedor") }

class App : Application(), SingletonImageLoader.Factory {
    lateinit var contenedor: Contenedor
        private set

    override fun onCreate() {
        super.onCreate()
        Grano.cargar(this)
        contenedor = Contenedor(this)
        contenedor.sesion.cargar()
        Push.iniciar(this)
        // El WebSocket vive mientras la app está en primer plano.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                contenedor.alcance.launch { contenedor.chequearVersion() }
                if (contenedor.sesion.token.value != null) {
                    contenedor.tiempoReal.encender()
                    contenedor.alcance.launch { contenedor.mandarUbicacion() }
                }
            }

            override fun onStop(owner: LifecycleOwner) = contenedor.tiempoReal.apagar()
        })
        if (contenedor.sesion.token.value != null) contenedor.alcance.launch { Push.registrar(contenedor) }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { contenedor.http })) }
        // Las fotos aparecen con un fundido de 0,2 s, como ImagenRed del iPhone.
        .crossfade(200)
        .build()
}
