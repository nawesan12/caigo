package ar.com.caigo.app.datos

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.almacen by preferencesDataStore("sesion")

/**
 * La sesión de Caigo: el token propio `cs_…` (60 días) y el id de la persona. Se guarda cifrado con
 * Tink (AES-GCM, la clave vive en el Android Keystore) dentro de un DataStore.
 */
class Sesion(private val ctx: Context) {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(ctx, "caigo_llavero", "caigo_llaves")
            .withKeyTemplate(com.google.crypto.tink.KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://caigo_maestra")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private val kToken = stringPreferencesKey("token")
    private val kUsuario = stringPreferencesKey("usuario")
    private val kIntro = stringPreferencesKey("intro")

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token
    var usuarioId: String = ""
        private set
    /** Ya vio la intro (splash + 01a–01c). */
    var vioIntro: Boolean = false
        private set

    /** Lee la sesión guardada. Se llama una vez al arrancar, antes de pintar. */
    fun cargar() = runBlocking {
        val p = ctx.almacen.data.first()
        _token.value = p[kToken]?.let { descifrar(it) }
        usuarioId = p[kUsuario].orEmpty()
        vioIntro = p[kIntro] == "1"
    }

    suspend fun guardar(token: String, usuarioId: String) {
        this.usuarioId = usuarioId
        ctx.almacen.edit {
            it[kToken] = cifrar(token)
            it[kUsuario] = usuarioId
        }
        _token.value = token
    }

    suspend fun marcarIntro() {
        vioIntro = true
        ctx.almacen.edit { it[kIntro] = "1" }
    }

    suspend fun borrar() {
        ctx.almacen.edit {
            it.remove(kToken)
            it.remove(kUsuario)
        }
        usuarioId = ""
        _token.value = null
    }

    private fun cifrar(t: String): String =
        Base64.encodeToString(aead.encrypt(t.toByteArray(), "caigo".toByteArray()), Base64.NO_WRAP)

    private fun descifrar(t: String): String? = runCatching {
        String(aead.decrypt(Base64.decode(t, Base64.NO_WRAP), "caigo".toByteArray()))
    }.getOrNull()
}
