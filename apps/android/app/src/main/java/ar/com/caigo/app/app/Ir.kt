package ar.com.caigo.app.app

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavController

/** Pestañas de la tab bar flotante. */
enum class Pestana { Ahora, Explorar, Mis, Perfil }

/**
 * Las acciones de navegación que usan las pantallas. Ninguna pantalla toca el NavController:
 * así cada una se puede armar y probar sola.
 */
class Ir(private val nav: NavController) {
    /** Pestaña de Principal (se puede cambiar desde cualquier pantalla). */
    var pestana by mutableStateOf(Pestana.Ahora)
    var modoAhora by mutableStateOf<String?>(null)

    private fun ir(ruta: String) = nav.navigate(ruta) { launchSingleTop = true }
    private fun e(s: String) = Uri.encode(s)

    fun atras() {
        // Sin nada apilado no se hace nada: desde el alta no se saltean pasos.
        if (nav.previousBackStackEntry != null) nav.popBackStack()
    }

    fun principal(pestana: Pestana = Pestana.Ahora, modo: String? = null) {
        this.pestana = pestana
        modoAhora = modo
        nav.navigate("principal?tab=${pestana.name}&modo=${modo.orEmpty()}") {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun intro() = nav.navigate("intro") { popUpTo(0) { inclusive = true } }
    fun ingresar() = ir("ingresar")
    fun debug() = ir("debug")
    /** Entrar con mail y contraseña (o código) por Clerk. */
    fun ingresoMail() = ir("mail")
    /** Siguiente paso del alta (datos → teléfono → selfie → intenciones → completar) o la app. */
    fun alta(paso: String) = nav.navigate(paso) { popUpTo(0) { inclusive = true } }
    fun telefono() = ir("telefono")
    fun selfie(volver: Boolean = false) = ir("selfie?volver=$volver")
    fun intenciones(soloEditar: Boolean = false) = ir("intenciones?editar=$soloEditar")

    fun plan(id: String) = ir("plan/${e(id)}")
    fun planSlug(slug: String) = ir("plan/slug/${e(slug)}")
    fun crear() = ir("crear")
    /** Después de publicar: reemplaza Crear por la confirmación. */
    fun publicado(id: String) = nav.navigate("publicado/${e(id)}") { popUpTo("crear") { inclusive = true } }
    fun solicitudes(planId: String) = ir("solicitudes/${e(planId)}")
    fun chat(planId: String) = ir("chat/${e(planId)}")
    fun privados() = ir("privados")
    fun privado(conId: String, nombre: String) = ir("privado/${e(conId)}?nombre=${e(nombre)}")
    fun checkin(planId: String) = ir("checkin/${e(planId)}")
    fun repetimos(planId: String) = ir("repetimos/${e(planId)}")
    fun qrOrganizador(planId: String) = ir("qr/${e(planId)}")
    fun escanearQr(planId: String) = ir("escanear/${e(planId)}")
    fun persona(id: String, desde: String = "perfil") = ir("persona/${e(id)}?desde=${e(desde)}")
    fun recibidos(modo: String, pestana: Int = 0) = ir("recibidos?modo=$modo&tab=$pestana")
    fun plus(motivo: String = "perfil") = ir("plus?motivo=$motivo")
    fun verificacion() = ir("verificacion")
    fun dni(planId: String? = null) = ir("dni?plan=${planId.orEmpty()}")
    fun checkout(planId: String, cantidad: Int) = ir("checkout/${e(planId)}?cantidad=$cantidad")
    fun entrada(entradaId: String, planId: String) = ir("entrada/${e(entradaId)}?plan=${e(planId)}")
    fun qrEntrada(entradaId: String) = ir("qrentrada/${e(entradaId)}")
    fun pases() = ir("pases")
    fun tap(planId: String? = null) = ir("tap?plan=${planId.orEmpty()}")
    /** Después de un tap: reemplaza la pantalla de la placa por "Adentro". */
    fun adentro() = nav.navigate("adentro") { popUpTo("tap?plan={plan}") { inclusive = true } }
    fun editarPerfil() = ir("editar")
    fun gente() = ir("gente")
    /** El organizador edita su plan (PATCH /v1/planes/{id}). */
    fun editarPlan(id: String) = ir("editarplan/${e(id)}")
    /** Gente que bloqueé (GET/DELETE /v1/bloqueos). */
    fun bloqueos() = ir("bloqueos")
    /** Ajustes de avisos push (planes cerca, tu gente). */
    fun ajustesAvisos() = ir("ajustesavisos")
}
