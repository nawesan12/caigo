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
/** La ruta de PedirDni en el NavHost. */
const val RUTA_PEDIR_DNI = "pedirdni?motivo={motivo}&plan={plan}"

class Ir(private val nav: NavController) {
    /** Pestaña de Principal (se puede cambiar desde cualquier pantalla). */
    var pestana by mutableStateOf(Pestana.Ahora)
    var modoAhora by mutableStateOf<String?>(null)

    private fun ir(ruta: String) = nav.navigate(ruta) { launchSingleTop = true }
    private fun e(s: String) = Uri.encode(s)

    /** Hay una pantalla abajo (se llegó apilando, no como paso del alta). */
    val apilada: Boolean get() = nav.previousBackStackEntry != null

    fun atras() {
        // Sin nada apilado no se hace nada: desde el alta no se saltean pasos.
        if (nav.previousBackStackEntry != null) nav.popBackStack()
    }

    fun principal(pestana: Pestana = Pestana.Ahora, modo: String? = null) {
        this.pestana = pestana
        // Gente y Citas ya no son modos de Ahora (18/11): Ahora y, encima, el mazo.
        val modoMazo = modo?.takeIf { it == "gente" || it == "citas" }
        modoAhora = if (modoMazo != null) null else modo
        nav.navigate("principal?tab=${pestana.name}&modo=${modoAhora.orEmpty()}") {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
        if (modoMazo != null) mazo(modoMazo)
    }

    fun intro() = nav.navigate("intro") { popUpTo(0) { inclusive = true } }
    fun ingresar() = ir("ingresar")
    fun debug() = ir("debug")
    /** Entrar con mail y contraseña (o código) por Clerk. */
    fun ingresoMail() = ir("mail")
    /** Siguiente paso del alta (datos → teléfono → selfie → DNI → intenciones → completar) o la app. */
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

    /**
     * «Para sumarte, verificamos que seas vos»: antes del DNI, al primer «Caigo», crear, chatear o la gente.
     * motivo: sumarte | crear | chatear | gente.
     */
    fun pedirDni(motivo: String, planId: String? = null) {
        if (nav.currentDestination?.route == RUTA_PEDIR_DNI) return
        ir("pedirdni?motivo=${e(motivo)}&plan=${planId.orEmpty()}")
    }

    /** De «Verificarme» al DNI: reemplaza la pantalla que lo pidió. */
    fun aDni(planId: String?) = nav.navigate("dni?plan=${planId.orEmpty()}") {
        popUpTo(RUTA_PEDIR_DNI) { inclusive = true }
        launchSingleTop = true
    }

    /** Al terminar el DNI fuera del alta, si al perfil le falta algo: reemplaza el DNI por completarlo. */
    fun completarPerfilDespuesDelDni() = nav.navigate("completarapilado") {
        popUpTo("dni?plan={plan}") { inclusive = true }
        launchSingleTop = true
    }

    /** El mazo de Gente o Citas (salió de la cabecera de Ahora; se abre desde Perfil › Mi gente). */
    fun mazo(modo: String) = ir("mazo?modo=$modo")
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
    /** «Esta semana en <ciudad>» (el ritual de los lunes). */
    fun semana() = ir("semana")
    /** Fotos del plan (sólo para quienes fueron). */
    fun fotosPlan(planId: String) = ir("fotos/${e(planId)}")
}
