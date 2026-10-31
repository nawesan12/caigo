package ar.com.caigo.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ar.com.caigo.app.App
import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.Contenedor
import ar.com.caigo.app.MainActivity
import ar.com.caigo.app.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Push con Firebase Cloud Messaging, sin google-services.json: FirebaseApp se arma a mano con los
 * datos de local.properties (caigo.firebase.*). Si no están, el push queda apagado y la app anda igual.
 */
object Push {
    const val CANAL = "caigo"

    val disponible: Boolean
        get() = BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_PROYECTO.isNotBlank() && BuildConfig.FIREBASE_REMITENTE.isNotBlank()

    fun iniciar(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Planes y mensajes", NotificationManager.IMPORTANCE_HIGH))
        if (!disponible || FirebaseApp.getApps(ctx).isNotEmpty()) return
        runCatching {
            FirebaseApp.initializeApp(
                ctx,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROYECTO)
                    .setGcmSenderId(BuildConfig.FIREBASE_REMITENTE)
                    .build(),
            )
        }
    }

    /** Pide el token de FCM y lo registra en la API (`PUT /v1/dispositivos`). */
    suspend fun registrar(c: Contenedor) {
        if (!disponible || FirebaseApp.getApps(c.ctx).isEmpty()) return
        runCatching {
            val t = FirebaseMessaging.getInstance().token.await()
            c.api.registrarDispositivo(t)
        }
    }
}

/** Recibe los avisos y los muestra; al tocarlos se abre la pantalla que dice `data.pantalla`. */
class ServicioPush : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val c = (application as App).contenedor
        if (c.sesion.token.value != null) c.alcance.launch { runCatching { c.api.registrarDispositivo(token) } }
    }

    override fun onMessageReceived(m: RemoteMessage) {
        val titulo = m.notification?.title ?: m.data["titulo"] ?: return
        val cuerpo = m.notification?.body ?: m.data["cuerpo"].orEmpty()
        val abrir = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            m.data.forEach { (k, v) -> putExtra(k, v) }
        }
        val pi = PendingIntent.getActivity(this, m.data.hashCode(), abrir, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, Push.CANAL)
            .setSmallIcon(R.drawable.ic_aviso)
            .setColor(0xFFFF7868.toInt())
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { getSystemService(android.app.NotificationManager::class.java).notify(m.data.hashCode(), n) }
    }
}
