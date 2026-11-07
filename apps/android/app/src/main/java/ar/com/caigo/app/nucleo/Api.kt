package ar.com.caigo.app.nucleo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Error de la API: [detalle] es lo que se le muestra a la persona, tal cual. */
class ErrorApi(val estado: Int, val problema: Problema) : Exception(detalleDe(problema)) {
    val codigo: String get() = problema.code
    val detalle: String get() = detalleDe(problema)

    private companion object {
        fun detalleDe(p: Problema): String = when {
            p.detail.isNotEmpty() -> p.detail
            p.title.isNotEmpty() -> p.title
            else -> "Algo salió mal. Probá de nuevo."
        }
    }
}

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** Mensaje para mostrar de cualquier error (de la API o de la red). */
fun mensajeDe(e: Throwable): String = when (e) {
    is ErrorApi -> e.detalle
    is IOException -> "No hay conexión. Revisá internet y probá de nuevo."
    is Clerk.ErrorClerk -> e.mensaje
    else -> e.message?.takeIf { it.isNotBlank() } ?: "Algo salió mal. Probá de nuevo."
}

/** Valor de query: null o vacío no se manda. */
typealias Query = Array<Pair<String, Any?>>

private suspend fun Call.esperar(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, _, _ -> response.close() }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/**
 * Cliente de la API de Caigo. Todo pedido lleva `Authorization: Bearer <token cs_…>`.
 * Si la API responde 401, se avisa a [alVencer] (la sesión se cierra en la app).
 */
class Api(
    val base: String,
    val http: OkHttpClient,
    private val token: () -> String?,
    private val alVencer: () -> Unit = {},
) {
    private val tipoJson = "application/json; charset=utf-8".toMediaType()

    fun url(ruta: String, vararg query: Pair<String, Any?>): HttpUrl {
        val b = (base.trimEnd('/') + ruta).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> if (v != null && v.toString().isNotEmpty()) b.addQueryParameter(k, v.toString()) }
        return b.build()
    }

    suspend fun crudo(metodo: String, url: HttpUrl, cuerpo: RequestBody? = null, autorizacion: String? = null): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).method(metodo, cuerpo ?: if (metodo == "POST" || metodo == "PUT" || metodo == "PATCH") "".toRequestBody(null) else null)
            val t = autorizacion ?: token()
            if (!t.isNullOrBlank()) req.header("Authorization", "Bearer $t")
            req.header("Accept", "application/json")
            http.newCall(req.build()).esperar().use { r ->
                val texto = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val p = runCatching { json.decodeFromString<Problema>(texto) }.getOrElse { Problema(title = "Error ${r.code}") }
                    if (r.code == 401 && autorizacion == null) alVencer()
                    throw ErrorApi(r.code, p.copy(status = r.code))
                }
                texto
            }
        }

    suspend inline fun <reified R> get(ruta: String, vararg query: Pair<String, Any?>): R =
        decodificar(crudo("GET", url(ruta, *query)))

    suspend inline fun <reified B, reified R> enviar(metodo: String, ruta: String, cuerpo: B, vararg query: Pair<String, Any?>): R =
        decodificar(crudo(metodo, url(ruta, *query), cuerpoJson(cuerpo, serializer<B>())))

    suspend inline fun <reified B> mandar(metodo: String, ruta: String, cuerpo: B) {
        crudo(metodo, url(ruta), cuerpoJson(cuerpo, serializer<B>()))
    }

    suspend fun sinCuerpo(metodo: String, ruta: String, vararg query: Pair<String, Any?>) {
        crudo(metodo, url(ruta, *query))
    }

    fun <B> cuerpoJson(cuerpo: B, s: KSerializer<B>): RequestBody = json.encodeToString(s, cuerpo).toRequestBody(tipoJson)

    inline fun <reified R> decodificar(texto: String): R =
        if (R::class == Unit::class) Unit as R else json.decodeFromString(serializer<R>(), texto.ifBlank { "{}" })

    // ——— Sesión ———

    /** Canjea el JWT de Clerk por el token propio de Caigo (cs_…, 60 días). */
    suspend fun crearSesion(jwtClerk: String): SesionNueva =
        decodificar(crudo("POST", url("/v1/sesion", "cliente" to "android"), autorizacion = jwtClerk))

    suspend fun cerrarSesion() = runCatching { sinCuerpo("DELETE", "/v1/sesion") }

    // ——— Yo y personas ———

    suspend fun yo(): Perfil = get("/v1/yo")
    suspend fun actualizarYo(c: CambiosPerfil): Perfil = enviar("PATCH", "/v1/yo", c)

    /** Borra la cuenta (perfil, fotos, selfie, DNI y teléfono; cancela los planes que no pasaron). 204. */
    suspend fun borrarCuenta() = sinCuerpo("DELETE", "/v1/yo")
    suspend fun persona(id: String): Perfil = get("/v1/personas/$id")
    suspend fun miGente(): Gente = get("/v1/gente")
    suspend fun sumarAMiGente(id: String) = sinCuerpo("PUT", "/v1/gente/$id")
    suspend fun sacarDeMiGente(id: String) = sinCuerpo("DELETE", "/v1/gente/$id")
    suspend fun bloquear(id: String) = sinCuerpo("PUT", "/v1/bloqueos/$id")
    suspend fun bloqueos(): Bloqueos = get("/v1/bloqueos")
    suspend fun desbloquear(id: String) = sinCuerpo("DELETE", "/v1/bloqueos/$id")

    /** Se manda al abrir la app (redondeada a ~1 km; la API la borra a las 24 h). */
    suspend fun ubicacion(lat: Double, lng: Double) = mandar("PUT", "/v1/yo/ubicacion", Ubicacion(lat, lng))
    suspend fun version(): Version = get("/v1/version")
    suspend fun reportar(r: Reporte) = mandar("POST", "/v1/reportes", r)

    // ——— Planes ———

    /** `q` (≥ 2 letras) busca en título, zona y lugar; `ciudad` se usa cuando no hay ubicación. */
    suspend fun ahora(
        lat: Double?, lng: Double?, categoria: String?, q: String? = null, finos: FiltrosFinos = FiltrosFinos(),
        zona: String? = null, ciudad: String? = null,
    ): Planes = get(
        "/v1/planes/ahora", "lat" to num(lat), "lng" to num(lng), "categoria" to categoria,
        "q" to busqueda(q), "zona" to zona, "ciudad" to ciudad, *finos.query(),
    )

    /** PARA LAS PANTALLAS: el iPhone no manda `radio` (queda por compatibilidad con Explorar.kt). */
    suspend fun explorar(
        desde: String? = null, hasta: String? = null, categoria: String? = null, ciudad: String? = null,
        lat: Double? = null, lng: Double? = null, finos: FiltrosFinos = FiltrosFinos(),
        q: String? = null, zona: String? = null, radio: Int? = null,
    ): Planes = get(
        "/v1/planes/explorar", "desde" to desde, "hasta" to hasta, "categoria" to categoria, "ciudad" to ciudad,
        "lat" to num(lat), "lng" to num(lng), "q" to busqueda(q), "zona" to zona, "radio" to radio, *finos.query(),
    )

    /** Las zonas de una ciudad (las de planes de los próximos 30 días + las fijas). */
    suspend fun zonas(ciudad: String): Zonas = get("/v1/publico/zonas", "ciudad" to ciudad)

    /** Sin sesión: lo público de Ahora, Explorar y el detalle. */
    suspend fun publicoExplorar(): Planes = get("/v1/publico/planes/explorar")

    suspend fun mios(cual: String): Planes = get("/v1/planes/mios", "cual" to cual)
    suspend fun plan(id: String, lat: Double? = null, lng: Double? = null): Plan = get("/v1/planes/$id", "lat" to num(lat), "lng" to num(lng))
    suspend fun publico(slug: String): Plan = get("/v1/publico/planes/$slug")
    suspend fun crearPlan(p: PlanNuevo): Plan = enviar("POST", "/v1/planes", p)
    suspend fun cancelarPlan(id: String) = sinCuerpo("DELETE", "/v1/planes/$id")
    suspend fun editarPlan(id: String, c: CambiosPlan): Plan = enviar("PATCH", "/v1/planes/$id", c)

    /** Organizador: saca a alguien aceptado (409 `con_entrada` si pagó entrada). */
    suspend fun sacarPersona(planId: String, usuarioId: String) = sinCuerpo("DELETE", "/v1/planes/$planId/personas/$usuarioId")

    /** Con Plus, un plan propio por semana sale primero en Ahora y Explorar. */
    suspend fun destacar(id: String) = sinCuerpo("POST", "/v1/planes/$id/destacar")

    @kotlinx.serialization.Serializable
    data class Sumarse(val cantidad: Int, val mensaje: String = "")

    suspend fun sumarme(id: String, cantidad: Int, mensaje: String = ""): Estado = enviar("POST", "/v1/planes/$id/sumarme", Sumarse(cantidad, mensaje))
    suspend fun salir(id: String) = sinCuerpo("DELETE", "/v1/planes/$id/sumarme")
    suspend fun solicitudes(id: String): Solicitudes = get("/v1/planes/$id/solicitudes")
    suspend fun responder(solicitudId: String, aceptar: Boolean) = sinCuerpo("POST", "/v1/solicitudes/$solicitudId/${if (aceptar) "aceptar" else "rechazar"}")
    suspend fun qr(id: String): Codigo = get("/v1/planes/$id/qr")

    @kotlinx.serialization.Serializable
    data class Llegada(val metodo: String, val lat: Double? = null, val lng: Double? = null, val codigo: String = "")

    suspend fun checkin(id: String, l: Llegada) = mandar("POST", "/v1/planes/$id/checkin", l)

    @kotlinx.serialization.Serializable
    data class Repetir(val repetir: Boolean, val volveria: List<Opinion>)

    suspend fun repetimos(id: String, repetir: Boolean, volveria: List<Opinion>): Nuevo = enviar("POST", "/v1/planes/$id/repetimos", Repetir(repetir, volveria))
    suspend fun enVivo(id: String): Link = decodificar(crudo("POST", url("/v1/planes/$id/en-vivo")))

    // ——— Gente, Citas, invitaciones ———

    suspend fun mazo(modo: String): Mazo = get("/v1/gente/mazo", "modo" to modo)

    @kotlinx.serialization.Serializable
    data class Reaccion(val modo: String, val accion: String)

    suspend fun reaccionar(id: String, modo: String, accion: String): ResultadoReaccion = enviar("POST", "/v1/gente/$id/reaccion", Reaccion(modo, accion))
    suspend fun recibidos(modo: String): Recibidos = get("/v1/gente/recibidos", "modo" to modo)
    suspend fun descartarRecibido(id: String, modo: String) = sinCuerpo("DELETE", "/v1/gente/recibidos/$id", "modo" to modo)

    @kotlinx.serialization.Serializable
    data class Invitar(val usuarioId: String)

    suspend fun invitar(planId: String, usuarioId: String) = mandar("POST", "/v1/planes/$planId/invitar", Invitar(usuarioId))
    suspend fun invitaciones(): Invitaciones = get("/v1/invitaciones")

    @kotlinx.serialization.Serializable
    data class Respuesta(val aceptar: Boolean)

    suspend fun responderInvitacion(planId: String, aceptar: Boolean): Adentro = enviar("POST", "/v1/invitaciones/$planId", Respuesta(aceptar))

    // ——— Chat ———

    @kotlinx.serialization.Serializable
    data class Texto(val texto: String)

    suspend fun mensajes(planId: String, antes: Long? = null): Mensajes = get("/v1/planes/$planId/mensajes", "antes" to antes)
    suspend fun escribir(planId: String, texto: String): Mensaje = enviar("POST", "/v1/planes/$planId/mensajes", Texto(texto))
    suspend fun conversaciones(): Conversaciones = get("/v1/privados")
    /** PARA LAS PANTALLAS: el iPhone no pagina los privados (`antes` queda por compatibilidad con Chat.kt). */
    suspend fun privados(conId: String, antes: Long? = null): Privados = get("/v1/privados/$conId", "antes" to antes)
    suspend fun escribirPrivado(conId: String, texto: String): Privado = enviar("POST", "/v1/privados/$conId", Texto(texto))

    // ——— Confianza ———

    /** Sube un archivo crudo (JPEG). tipo: foto | selfie | dni. */
    suspend fun subir(tipo: String, jpeg: ByteArray): Archivo =
        decodificar(crudo("POST", url("/v1/archivos", "tipo" to tipo), jpeg.toRequestBody("image/jpeg".toMediaType())))

    suspend fun verificacion(): EstadoVerificacion = get("/v1/verificacion")

    @kotlinx.serialization.Serializable
    data class Telefono(val telefono: String)

    @kotlinx.serialization.Serializable
    data class CodigoSms(val codigo: String)

    @kotlinx.serialization.Serializable
    data class ArchivoId(val archivoId: String)

    suspend fun pedirCodigo(telefono: String): CodigoDePrueba = enviar("POST", "/v1/verificacion/telefono", Telefono(telefono))
    suspend fun confirmarCodigo(codigo: String) = mandar("POST", "/v1/verificacion/telefono/confirmar", CodigoSms(codigo))
    suspend fun selfie(archivoId: String) = mandar("POST", "/v1/verificacion/selfie", ArchivoId(archivoId))
    suspend fun dni(d: DatosDNI) = mandar("POST", "/v1/verificacion/dni", d)

    // ——— Push ———

    @kotlinx.serialization.Serializable
    data class Dispositivo(val plataforma: String, val token: String)

    suspend fun registrarDispositivo(token: String) = mandar("PUT", "/v1/dispositivos", Dispositivo("android", token))
    suspend fun borrarDispositivo(token: String) = sinCuerpo("DELETE", "/v1/dispositivos/" + java.net.URLEncoder.encode(token, "UTF-8"))

    // ——— Plus ———

    suspend fun plus(): Plus = get("/v1/plus")

    @kotlinx.serialization.Serializable
    data class TokenCompra(val token: String)

    suspend fun plusGoogle(token: String): EstadoPlus = enviar("POST", "/v1/plus/google", TokenCompra(token))

    // ——— Entradas, pases y tap ———

    @kotlinx.serialization.Serializable
    data class Cantidad(val cantidad: Int, val modo: String = "")

    /** modo: `entrada` (pagar todo) o `sena` (sólo si el plan tiene seña); vacío, lo que diga el plan. */
    suspend fun comprarEntradas(planId: String, cantidad: Int, modo: String = ""): Compra =
        enviar("POST", "/v1/planes/$planId/entradas", Cantidad(cantidad, modo))
    suspend fun pases(): Pases = get("/v1/pases")

    /** Cancela la entrada según la política del plan; `strict` responde 409 `sin_reembolso`. */
    suspend fun cancelarEntrada(id: String): Reembolso = decodificar(crudo("DELETE", url("/v1/entradas/$id")))
    suspend fun walletEntrada(id: String): Link = get("/v1/pases/entradas/$id/google")
    suspend fun walletSocio(lugarId: String): Link = get("/v1/pases/socios/$lugarId/google")
    suspend fun tap(l: Lectura): ResultadoTap = enviar("POST", "/v1/tap", l)

    companion object {
        /** Coordenadas con 6 decimales, como el iPhone. */
        fun num(d: Double?): String? = d?.let { String.format(java.util.Locale.ROOT, "%.6f", it) }

        /** La búsqueda sólo viaja con 2 letras o más. */
        fun busqueda(q: String?): String? = q?.trim()?.takeIf { it.length >= 2 }

        /** ~1 km: dos decimales de grado. */
        fun redondear(v: Double): Double = Math.round(v * 100) / 100.0

        /** Sólo en las pruebas de pantallas: responde con la API simulada (apps/pruebas/respuestas.json). */
        @Volatile var interceptorPrueba: okhttp3.Interceptor? = null

        fun http(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .apply { interceptorPrueba?.let { addInterceptor(it) } }
            .build()
    }
}

/** Los filtros finos de Ahora/Explorar (Plus). Sin Plus, la API responde 403 `plus`. */
data class FiltrosFinos(
    val verificados: Boolean = false,
    val faltan: Int? = null,
    val edadDesde: Int? = null,
    val edadHasta: Int? = null,
    val gratis: Boolean = false,
) {
    val activos: Boolean get() = verificados || faltan != null || edadDesde != null || edadHasta != null || gratis

    fun query(): Query = arrayOf(
        "verificados" to (if (verificados) "1" else null),
        "faltan" to faltan?.toString(),
        "edadDesde" to edadDesde?.toString(),
        "edadHasta" to edadHasta?.toString(),
        "gratis" to (if (gratis) "1" else null),
    )
}
