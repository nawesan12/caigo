package ar.com.caigo.app.pantallas.ingreso

import android.content.Context
import ar.com.caigo.app.app.Contenedor
import ar.com.caigo.app.piezas.abrirPestana

// El ida y vuelta con Clerk (web). En Android se entra sólo con Google: no hay Sign in with Apple
// nativo (IngresoApple / IngresoClerkApple del iPhone no tienen par). El cliente de la Frontend API
// está en nucleo/Clerk.kt.

/** Error del ida y vuelta con Clerk armado en la app (además de [ar.com.caigo.app.nucleo.Clerk.ErrorClerk]). */
class IngresoErrorClerk(val codigo: String) : Exception(codigo)

/** Mensajes para los códigos de error del ida y vuelta con Clerk. */
fun ingresoMensajeClerk(codigo: String): String = when (codigo) {
    "cancelado" -> "Cancelaste el ingreso. Probá de nuevo cuando quieras."
    "sin_clave" -> "El ingreso todavía no está disponible en esta versión."
    else -> "No pudimos completar el ingreso. Probá de nuevo."
}

/**
 * La hoja web para el OAuth de Clerk (en iOS, ASWebAuthenticationSession): una Custom Tab. La vuelta
 * (`ar.com.caigo.app://callback?rotating_token_nonce=…`) llega a MainActivity y queda en
 * `Contenedor.destino` como `Destino.VueltaClerk`; la toma [Ingresar].
 */
internal object IngresoWeb {
    /** Arranca el intento en Clerk y abre la Custom Tab del proveedor. */
    suspend fun abrir(ctx: Context, c: Contenedor, proveedor: String) {
        val url = c.clerk.iniciar(proveedor)
        abrirPestana(ctx, url)
    }

    /** Termina el ingreso con el nonce de la vuelta y devuelve el JWT de sesión de Clerk. */
    suspend fun completar(c: Contenedor, nonce: String?): String = c.clerk.completar(nonce)
}
