package ar.com.caigo.app.app

import ar.com.caigo.app.BuildConfig
import ar.com.caigo.app.R

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ar.com.caigo.app.app.CaigoNav
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val c get() = (application as App).contenedor

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) atender(intent)
        setContent { CaigoNav(c) }

        // Caigo Tap en primer plano: mientras la pantalla de la placa está abierta, se leen las placas
        // acá (reader mode) en vez de que Android abra otra cosa.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                c.nfcActivo.collect { activo -> lectorNfc(activo) }
            }
        }
    }

    override fun onPause() {
        lectorNfc(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        atender(intent)
    }

    private fun lectorNfc(activo: Boolean) {
        val nfc = NfcAdapter.getDefaultAdapter(this) ?: return
        if (!activo) {
            runCatching { nfc.disableReaderMode(this) }
            return
        }
        nfc.enableReaderMode(this, { tag ->
            val ndef = Ndef.get(tag) ?: return@enableReaderMode
            val uri = runCatching {
                ndef.connect()
                val m = ndef.ndefMessage ?: ndef.cachedNdefMessage
                ndef.close()
                m?.records?.firstNotNullOfOrNull { it.toUri() }
            }.getOrNull()
            c.destinoDe(uri)?.let { d -> runOnUiThread { c.destino.value = d } }
        }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, null)
    }

    /** Links (Clerk, planes, placas), placas NFC con la app cerrada y avisos push. */
    private fun atender(i: Intent?) {
        if (i == null) return
        val d: Destino? = when (i.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val msgs = if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
                else @Suppress("DEPRECATION") i.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.filterIsInstance<NdefMessage>()?.toTypedArray()
                val uri: Uri? = msgs?.flatMap { (it as NdefMessage).records.toList() }?.firstNotNullOfOrNull { it.toUri() } ?: i.data
                c.destinoDe(uri)
            }
            Intent.ACTION_VIEW -> c.destinoDe(i.data)
            else -> {
                val pantalla = i.getStringExtra("pantalla")
                val plan = i.getStringExtra("plan")
                val privado = i.getStringExtra("privado")
                if (pantalla != null || plan != null || privado != null) Destino.Pantalla(pantalla.orEmpty(), plan, privado, i.getStringExtra("modo")) else null
            }
        }
        if (d != null) c.destino.value = d
    }
}
