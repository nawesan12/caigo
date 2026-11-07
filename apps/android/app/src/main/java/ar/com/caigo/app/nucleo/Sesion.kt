package ar.com.caigo.app.nucleo

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
    private val kGrupo = stringPreferencesKey("grupo")
    private val kTema = stringPreferencesKey("tema")
    private val kAvisos = stringPreferencesKey("avisos_pedidos")
    private val kTokenPush = stringPreferencesKey("token_push")

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token
    var usuarioId: String = ""
        private set
    /** Ya vio la intro (splash + 01a–01c). */
    var vioIntro: Boolean = false
        private set

    /** 01b · «Voy solo / Somos 2 / Somos 4»: con cuántos sale (1, 2 o 4). Es el valor por defecto al sumarse. */
    var grupo: Int = 1
        private set
    /** Ya eligió alguna vez con cuántos sale (`UserDefaults.object(forKey: "somos") != nil` del iPhone). */
    var grupoElegido: Boolean = false
        private set

    /** Tema elegido en Perfil: auto (el del sistema) | claro | oscuro. */
    private val _tema = MutableStateFlow("auto")
    val tema: StateFlow<String> = _tema

    /** Ya se pidió el permiso de avisos (Android 13+) una vez después del alta. */
    var avisosPedidos: Boolean = false
        private set

    /** El token de FCM registrado: se borra de la API al salir o borrar la cuenta. */
    var tokenPush: String = ""
        private set

    /** Lee la sesión guardada. Se llama una vez al arrancar, antes de pintar. */
    fun cargar() = runBlocking {
        val p = ctx.almacen.data.first()
        _token.value = p[kToken]?.let { descifrar(it) }
        usuarioId = p[kUsuario].orEmpty()
        vioIntro = p[kIntro] == "1"
        grupo = p[kGrupo]?.toIntOrNull()?.takeIf { it in listOf(1, 2, 4) } ?: 1
        grupoElegido = p[kGrupo] != null
        _tema.value = p[kTema] ?: "auto"
        avisosPedidos = p[kAvisos] == "1"
        tokenPush = p[kTokenPush].orEmpty()
    }

    suspend fun guardarGrupo(n: Int) {
        grupo = n
        grupoElegido = true
        ctx.almacen.edit { it[kGrupo] = n.toString() }
    }

    suspend fun guardarTema(t: String) {
        _tema.value = t
        ctx.almacen.edit { it[kTema] = t }
    }

    suspend fun marcarAvisosPedidos() {
        avisosPedidos = true
        ctx.almacen.edit { it[kAvisos] = "1" }
    }

    suspend fun guardarTokenPush(t: String) {
        tokenPush = t
        ctx.almacen.edit { it[kTokenPush] = t }
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
            it.remove(kTokenPush)
            it.remove(kAvisos)
        }
        tokenPush = ""
        avisosPedidos = false
        usuarioId = ""
        _token.value = null
    }

    private fun cifrar(t: String): String =
        Base64.encodeToString(aead.encrypt(t.toByteArray(), "caigo".toByteArray()), Base64.NO_WRAP)

    private fun descifrar(t: String): String? = runCatching {
        String(aead.decrypt(Base64.decode(t, Base64.NO_WRAP), "caigo".toByteArray()))
    }.getOrNull()
}
