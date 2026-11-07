package ar.com.caigo.app.nucleo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Ingreso con Google y Apple por Clerk, la identidad compartida de Grupo Vimo, sin el SDK: se habla
 * con la Frontend API en modo nativo (`_is_native=1`), con el token del cliente de Clerk en
 * `Authorization`. Es el mismo flujo que el iPhone (Nucleo/Clerk.swift) y la web de Tu Local Web:
 *
 * 1. [iniciar] crea el intento (`oauth_google` / `oauth_apple`) con `redirect_url =
 *    ar.com.caigo.app://callback` y devuelve la URL del proveedor, que se abre en una Custom Tab.
 * 2. El proveedor vuelve a Clerk y Clerk a `ar.com.caigo.app://callback?rotating_token_nonce=…`.
 * 3. [completar] recarga el intento con el nonce (o lo pasa a alta si la persona es nueva), pide el
 *    JWT de la sesión de Clerk y cierra esa sesión: la API lo verifica y lo canjea por la de Caigo.
 */
class Clerk(private val ctx: Context, private val http: OkHttpClient, publicable: String) {
    class ErrorClerk(val codigo: String) : Exception(mensajeDe(codigo)) {
        val mensaje: String get() = mensajeDe(codigo)

        private companion object {
            fun mensajeDe(codigo: String): String = when (codigo) {
                "cancelado" -> "Se canceló el ingreso."
                "sin_clave" -> "Falta configurar el ingreso en esta versión."
                else -> "No pudimos completar el ingreso. Probá de nuevo."
            }
        }
    }

    val fapi: String? = fapiDe(publicable)
    val disponible get() = fapi != null

    @Serializable
    private data class Verificacion(val status: String? = null, val external_verification_redirect_url: String? = null)

    @Serializable
    private data class Recurso(
        val id: String? = null,
        val status: String? = null,
        val created_session_id: String? = null,
        val first_factor_verification: Verificacion? = null,
    )

    @Serializable
    private data class ErrorFapi(val code: String? = null)

    @Serializable
    private data class Respuesta(val response: Recurso? = null, val jwt: String? = null, val errors: List<ErrorFapi> = emptyList())

    private data class Resultado(val r: Respuesta, val cliente: String)

    private val prefs get() = ctx.getSharedPreferences("caigo_clerk", Context.MODE_PRIVATE)

    private suspend fun pedir(ruta: String, metodo: String = "GET", cuerpo: Map<String, String>? = null, cliente: String? = null): Resultado =
        withContext(Dispatchers.IO) {
            val base = fapi ?: throw ErrorClerk("sin_clave")
            val url = "$base/v1$ruta".toHttpUrl().newBuilder()
                .addQueryParameter("_is_native", "1")
                .addQueryParameter("__clerk_api_version", VERSION_FAPI)
                .build()
            val b = Request.Builder().url(url)
            if (!cliente.isNullOrBlank()) b.header("Authorization", cliente)
            val body = if (metodo == "POST") {
                formulario(cuerpo.orEmpty()).toRequestBody("application/x-www-form-urlencoded".toMediaType())
            } else null
            b.method(metodo, body)
            http.newCall(b.build()).execute().use { res ->
                val texto = res.body?.string().orEmpty()
                val r = runCatching { json.decodeFromString<Respuesta>(texto) }.getOrElse { Respuesta() }
                if (!res.isSuccessful) throw ErrorClerk(r.errors.firstOrNull()?.code ?: "clerk_no_responde")
                // Clerk rota el token del cliente: vale el último que mandó.
                Resultado(r, res.header("authorization").takeUnless { it.isNullOrBlank() } ?: cliente.orEmpty())
            }
        }

    /** Arranca el ingreso y devuelve la URL del proveedor a abrir en la Custom Tab. */
    suspend fun iniciar(proveedor: String): String {
        val (r, cliente) = pedir(
            "/client/sign_ins", "POST",
            mapOf("strategy" to "oauth_$proveedor", "redirect_url" to VUELTA),
        )
        val url = r.response?.first_factor_verification?.external_verification_redirect_url
        val intento = r.response?.id
        if (url.isNullOrBlank() || cliente.isBlank() || intento.isNullOrBlank()) throw ErrorClerk("clerk_no_responde")
        prefs.edit().putString("cliente", cliente).putString("intento", intento).apply()
        return url
    }

    /** Termina el ingreso con el nonce de la vuelta y devuelve el JWT de sesión de Clerk. */
    suspend fun completar(nonce: String?): String {
        val clienteGuardado = prefs.getString("cliente", null)
        val intento = prefs.getString("intento", null)
        prefs.edit().clear().apply()
        if (clienteGuardado.isNullOrBlank() || intento == null || !Regex("^sia_[A-Za-z0-9]+$").matches(intento)) {
            throw ErrorClerk("estado_invalido")
        }
        // Con cuenta en Clerk la vuelta trae el nonce; alguien nuevo vuelve sin él (va al alta).
        val ruta = if (!nonce.isNullOrBlank()) "/client/sign_ins/$intento?rotating_token_nonce=${java.net.URLEncoder.encode(nonce, "UTF-8")}"
        else "/client/sign_ins/$intento"
        var ingreso = try {
            pedir(ruta, cliente = clienteGuardado)
        } catch (e: ErrorClerk) {
            if (e.codigo != "external_account_not_found") throw e
            Resultado(Respuesta(Recurso(first_factor_verification = Verificacion(status = "transferable"))), clienteGuardado)
        }
        var cliente = ingreso.cliente
        var sesion = ingreso.r.response?.created_session_id
        if (sesion.isNullOrBlank() && ingreso.r.response?.first_factor_verification?.status == "transferable") {
            ingreso = pedir("/client/sign_ups", "POST", mapOf("transfer" to "true"), cliente)
            cliente = ingreso.cliente
            sesion = ingreso.r.response?.created_session_id
        }
        if (sesion.isNullOrBlank() || !Regex("^sess_[A-Za-z0-9]+$").matches(sesion)) throw ErrorClerk("cancelado")
        val t = pedir("/client/sessions/$sesion/tokens", "POST", cliente = cliente)
        val jwt = t.r.jwt?.takeIf { it.isNotEmpty() } ?: throw ErrorClerk("clerk_no_responde")
        // Caigo sigue con su propia sesión: la de Clerk se cierra. Si falla, vence sola.
        runCatching { pedir("/client/sessions/$sesion/remove", "POST", cliente = t.cliente) }
        return jwt
    }

    companion object {
        const val VERSION_FAPI = "2025-11-10"
        const val VUELTA = "ar.com.caigo.app://callback"
        const val ESQUEMA = "ar.com.caigo.app"

        /** La Frontend API sale de la clave publicable: pk_<entorno>_<base64("host$")>. */
        fun fapiDe(publicable: String?): String? {
            val partes = publicable?.split("_", limit = 3) ?: return null
            if (partes.size != 3 || partes[0] != "pk" || (partes[1] != "live" && partes[1] != "test")) return null
            return runCatching {
                var b = partes[2].replace('-', '+').replace('_', '/')
                while (b.length % 4 != 0) b += "="
                val host = String(java.util.Base64.getDecoder().decode(b), Charsets.UTF_8).removeSuffix("$")
                if (Regex("^[a-z0-9.-]+\\.[a-z]{2,}$", RegexOption.IGNORE_CASE).matches(host)) "https://$host" else null
            }.getOrNull()
        }

        /** Cuerpo `application/x-www-form-urlencoded`, con las claves ordenadas (como el iPhone). */
        fun formulario(d: Map<String, String>): String = d.toSortedMap().entries.joinToString("&") { (k, v) -> "$k=${codificar(v)}" }

        private fun codificar(v: String): String = buildString {
            v.toByteArray(Charsets.UTF_8).forEach { byte ->
                val c = byte.toInt() and 0xFF
                val ch = c.toChar()
                if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') append(ch)
                else append('%').append(String.format(java.util.Locale.ROOT, "%02X", c))
            }
        }

        /** El nonce de la URL de vuelta (`ar.com.caigo.app://callback?rotating_token_nonce=…`). */
        fun nonce(url: String): String? = runCatching { java.net.URI(url).rawQuery }.getOrNull()
            ?.split("&")
            ?.firstOrNull { it.substringBefore("=") == "rotating_token_nonce" }
            ?.substringAfter("=", "")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }
}
