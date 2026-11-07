package ar.com.caigo.app.nucleo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * El WebSocket de tiempo real (`/v1/tiempo-real?token=cs_…`): mensajes del chat, privados y
 * "escribiendo". Se conecta mientras la app está abierta y se reconecta con espera creciente
 * (1 s, 2 s, 4 s… hasta 30 s).
 */
class TiempoReal(
    private val base: String,
    private val http: OkHttpClient,
    private val token: () -> String?,
    private val alcance: CoroutineScope,
) {
    private val _eventos = MutableSharedFlow<Evento>(extraBufferCapacity = 64)
    val eventos: SharedFlow<Evento> = _eventos
    private val _conectado = MutableStateFlow(false)
    val conectado: StateFlow<Boolean> = _conectado

    private var ws: WebSocket? = null
    private var bucle: Job? = null
    private var intentos = 0

    fun url(t: String): String {
        val b = base.trimEnd('/')
        val wsBase = when {
            b.startsWith("https://") -> "wss://" + b.removePrefix("https://")
            b.startsWith("http://") -> "ws://" + b.removePrefix("http://")
            else -> b
        }
        return "$wsBase/v1/tiempo-real?token=" + java.net.URLEncoder.encode(t, "UTF-8")
    }

    fun encender() {
        if (bucle?.isActive == true) return
        bucle = alcance.launch {
            while (true) {
                val t = token()
                if (t.isNullOrBlank()) { delay(2000); continue }
                val cerrado = kotlinx.coroutines.CompletableDeferred<Unit>()
                ws = http.newWebSocket(Request.Builder().url(url(t)).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        intentos = 0
                        _conectado.value = true
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        leer(text)?.let { _eventos.tryEmit(it) }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        _conectado.value = false
                        cerrado.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        _conectado.value = false
                        cerrado.complete(Unit)
                    }
                })
                cerrado.await()
                delay(espera(intentos++))
            }
        }
    }

    fun apagar() {
        bucle?.cancel()
        bucle = null
        ws?.close(1000, "chau")
        ws = null
        _conectado.value = false
    }

    @Serializable
    private data class Escribiendo(val tipo: String = "escribiendo", val planId: String)

    /** Avisa que la persona está escribiendo en el chat del plan (la API lo limita a uno cada 2 s). */
    fun escribiendo(planId: String) {
        ws?.send(json.encodeToString(Escribiendo.serializer(), Escribiendo(planId = planId)))
    }

    @Serializable
    private data class EscribiendoPrivado(val tipo: String = "escribiendo", val privado: String)

    /** Avisa que la persona está escribiendo en un privado con [conId] (contrato §10). */
    fun escribiendoPrivado(conId: String) {
        ws?.send(json.encodeToString(EscribiendoPrivado.serializer(), EscribiendoPrivado(privado = conId)))
    }

    companion object {
        /**
         * Lee un evento. «escribiendo» en un privado trae `privado` como texto (el id de quien escribe),
         * no como mensaje: se separa antes de decodificar.
         */
        fun leer(texto: String): Evento? = runCatching {
            val o = json.parseToJsonElement(texto) as? kotlinx.serialization.json.JsonObject ?: return null
            val pv = o["privado"]
            if (pv is kotlinx.serialization.json.JsonPrimitive && pv.isString) {
                val sin = kotlinx.serialization.json.JsonObject(o - "privado")
                json.decodeFromJsonElement(Evento.serializer(), sin).copy(escribePrivado = pv.content)
            } else json.decodeFromJsonElement(Evento.serializer(), o)
        }.getOrNull()

        /** Espera creciente entre reconexiones: 1, 2, 4, 8, 16, 30, 30… segundos. */
        fun espera(intento: Int): Long = minOf(30_000L, 1000L shl minOf(intento, 5))
    }
}
