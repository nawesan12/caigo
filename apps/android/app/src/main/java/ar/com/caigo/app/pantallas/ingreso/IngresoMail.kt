package ar.com.caigo.app.pantallas.ingreso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.nucleo.ClerkMail
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Pantalla
import ar.com.caigo.app.piezas.Sistema
import ar.com.caigo.app.piezas.tocable
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.Icono
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.gel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * «Entrar con mail» (clon de Pantallas/Ingreso/IngresoMail.swift, como Tengo Libre, Corredor y la web): según la
 * cuenta, la contraseña o un código de 6 números; si es la primera vez se crea la cuenta (con código, y la
 * contraseña si la pide). «¿No podés entrar?» pasa a recuperar el acceso en la misma pantalla. No está en el
 * handoff: se arma con los pasos del registro (01d). Termina igual que Google: el JWT de Clerk va a POST /v1/sesion.
 */
@Composable
fun IngresoMail(ir: Ir) {
    val c = LocalApp.current
    val vista = LocalView.current
    val alcance = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var paso by remember { mutableStateOf<ClerkMail.Paso?>(null) }
    var flujo by remember { mutableStateOf(ClerkMail.configurado(c.clerk)) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    // Recuperar el acceso: null (no), "mail", "codigo", "confianza" (teléfono nuevo para Clerk) o "nueva".
    var recuperar by remember { mutableStateOf<String?>(null) }
    var enviado by remember { mutableStateOf(false) }
    val focoCodigo = remember { FocusRequester() }

    val textoCta = when (recuperar) {
        "mail" -> "Mandarme el código"
        "nueva" -> "Guardar y entrar"
        null -> when (val p = paso) {
            null -> "Seguir"
            ClerkMail.Paso.Clave -> "Entrar"
            is ClerkMail.Paso.Codigo -> if (p.alta) "Crear mi cuenta" else "Entrar"
            is ClerkMail.Paso.ClaveNueva, is ClerkMail.Paso.Listo -> "Guardar y entrar"
        }
        else -> "Seguir"
    }
    val habilitado = flujo != null && when (recuperar) {
        "mail" -> email.contains("@")
        "nueva" -> clave.isNotEmpty()
        null -> when (paso) {
            null -> email.contains("@")
            ClerkMail.Paso.Clave, is ClerkMail.Paso.ClaveNueva -> clave.isNotEmpty()
            is ClerkMail.Paso.Codigo -> codigo.length == 6
            is ClerkMail.Paso.Listo -> false
        }
        else -> codigo.length == 6
    }
    val mail = email.trim().lowercase()

    // Igual que Google (Ingresar.canjear). Si la API responde 409 `cuenta_borrada`, se ve su detalle.
    suspend fun canjear(jwt: String) {
        val s = c.api.crearSesion(jwt)
        c.entrar(s.token, s.usuarioId)
        Sistema.vibrar(vista)
        ir.alta(ingresoRutaDeAlta(c))
    }

    suspend fun seguirIngreso(f: ClerkMail) {
        val sig = when (paso) {
            null -> f.empezar(mail)
            ClerkMail.Paso.Clave -> f.entrarConClave(clave)
            is ClerkMail.Paso.Codigo -> f.confirmarCodigo(codigo)
            is ClerkMail.Paso.ClaveNueva -> f.guardarClave(clave)
            is ClerkMail.Paso.Listo -> return
        }
        if (sig is ClerkMail.Paso.Listo) return canjear(sig.token)
        if (sig is ClerkMail.Paso.ClaveNueva) clave = ""
        if (sig is ClerkMail.Paso.Codigo) codigo = ""
        paso = sig
    }

    suspend fun seguirRecuperar(f: ClerkMail, r: String) {
        when (r) {
            "mail" -> {
                val hay = f.pedirRecuperar(mail)
                enviado = true
                if (hay) { codigo = ""; recuperar = "codigo" }
            }
            "codigo", "confianza" -> {
                val sig = f.confirmarCodigo(codigo, recuperar = r == "codigo")
                if (sig is ClerkMail.Paso.ClaveNueva) { clave = ""; recuperar = "nueva" }
                if (sig is ClerkMail.Paso.Listo) canjear(sig.token)
            }
            else -> {
                val sig = f.guardarClave(clave)
                if (sig is ClerkMail.Paso.Listo) canjear(sig.token)
                // Teléfono nuevo para Clerk: un código más por mail para terminar.
                if (sig is ClerkMail.Paso.Codigo) { codigo = ""; enviado = false; recuperar = "confianza" }
            }
        }
    }

    fun seguir() {
        val f = flujo ?: return
        if (ocupado || !habilitado) return
        if ((recuperar == null && paso is ClerkMail.Paso.ClaveNueva) || recuperar == "nueva") {
            val (largo, comun) = ClerkMail.requisitos(clave)
            if (!largo || !comun) { error = "La contraseña todavía no cumple los requisitos."; return }
        }
        error = ""
        ocupado = true
        alcance.launch {
            try {
                val r = recuperar
                if (r != null) seguirRecuperar(f, r) else seguirIngreso(f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = ClerkMail.mensajeDe(e)
                Sistema.vibrar(vista, exito = false)
            } finally {
                ocupado = false
            }
        }
    }

    // Atrás: de recuperar al ingreso, de un paso al mail, y del mail a la pantalla anterior.
    val volver: () -> Unit = {
        if (!ocupado) {
            if (recuperar != null || paso != null) {
                recuperar = null
                paso = null
                clave = ""; codigo = ""; error = ""; enviado = false
                flujo = ClerkMail.configurado(c.clerk)
            } else {
                ir.atras()
            }
        }
    }

    // El código se completa solo: con 6 números, sigue.
    LaunchedEffect(codigo) { if (codigo.length == 6 && (paso is ClerkMail.Paso.Codigo || recuperar == "codigo" || recuperar == "confianza")) seguir() }
    LaunchedEffect(paso, recuperar) {
        if (paso is ClerkMail.Paso.Codigo || recuperar == "codigo" || recuperar == "confianza") runCatching { focoCodigo.requestFocus() }
    }

    ingresoClaro { Pantalla(oscuro = false) { arriba, abajo ->
        Column(Modifier.fillMaxSize().padding(top = arriba)) {
            ingresoBarraArriba(I.atras, volver, { T("Con mail", 14f, 700, C.texto2) },
                Modifier.padding(start = 20.dp, end = 24.dp, top = 12.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                val r = recuperar
                if (r == null) {
                    when (val p = paso) {
                        null -> {
                            ingresoTitulo("Tu mail", "Si es tu primera vez, creamos tu cuenta con un código que te llega por mail.")
                            ingresoCampo(email, { email = it }, Modifier.fillMaxWidth(), pista = "tu@mail.com", teclado = KeyboardType.Email,
                                tam = 17f, contenido = IngresoContenidoCampo.Email)
                        }
                        ClerkMail.Paso.Clave -> {
                            ingresoTitulo("Tu contraseña", email)
                            ingresoCampo(clave, { clave = it }, Modifier.fillMaxWidth(), teclado = KeyboardType.Password, tam = 17f,
                                transformacion = PasswordVisualTransformation(), contenido = IngresoContenidoCampo.Clave)
                        }
                        is ClerkMail.Paso.Codigo -> {
                            ingresoTitulo("Revisá tu mail",
                                "Te mandamos un código a $email. ${if (p.alta) "Con él creamos tu cuenta." else "Escribilo para entrar."}")
                            ingresoCasillas(codigo, { codigo = it.filter(Char::isDigit).take(6) }, focoCodigo)
                        }
                        is ClerkMail.Paso.ClaveNueva -> {
                            ingresoTitulo("Elegí una contraseña", "La vas a usar para entrar con tu mail.")
                            ingresoCampo(clave, { clave = it }, Modifier.fillMaxWidth(), teclado = KeyboardType.Password, tam = 17f,
                                transformacion = PasswordVisualTransformation(), contenido = IngresoContenidoCampo.ClaveNueva)
                            IngresoRequisitosClave(clave)
                        }
                        is ClerkMail.Paso.Listo -> {}
                    }
                } else if (r == "nueva") {
                    ingresoTitulo("Elegí una contraseña nueva", "Cerramos las otras sesiones abiertas por seguridad.")
                    ingresoCampo(clave, { clave = it }, Modifier.fillMaxWidth(), teclado = KeyboardType.Password, tam = 17f,
                        transformacion = PasswordVisualTransformation(), contenido = IngresoContenidoCampo.ClaveNueva)
                    IngresoRequisitosClave(clave)
                } else {
                    ingresoTitulo(
                        "¿No podés entrar?",
                        "Si entrás con Google, usá ese botón: no necesitás contraseña. Si usás mail, te mandamos un código para crear una nueva.",
                    )
                    if (r == "mail") {
                        ingresoCampo(email, { email = it }, Modifier.fillMaxWidth(), pista = "tu@mail.com", teclado = KeyboardType.Email,
                            tam = 17f, contenido = IngresoContenidoCampo.Email)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            T("Código que te llegó a $email", 14f, 700, C.carbon)
                            ingresoCasillas(codigo, { codigo = it.filter(Char::isDigit).take(6) }, focoCodigo)
                        }
                    }
                    if (enviado) {
                        Row(
                            Modifier.fillMaxWidth().gel(16.dp, Gels.chipClaro).padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icono(I.check, 16.dp, C.carbon, 3f)
                            T("Listo: si ese mail tiene cuenta, te llega un código en un minuto. Revisá también spam.", 14f, 600, C.carbon, alto = 1.4f)
                        }
                    }
                }
                if (error.isNotEmpty()) T(error, 14f, 600, C.coral2, alto = 1.4f)
                if (flujo == null) {
                    T("El ingreso con mail no está disponible en este momento. Probá de nuevo en un rato.", 14f, 600, C.coral2, alto = 1.4f)
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 36.dp + abajo, top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Cta(textoCta, { seguir() }, Modifier.fillMaxWidth(), habilitado = habilitado, cargando = ocupado)
                if (recuperar == null && paso == ClerkMail.Paso.Clave) {
                    Box(Modifier.tocable {
                        clave = ""; codigo = ""; error = ""; enviado = false
                        recuperar = "mail"
                    }.padding(4.dp)) { T("¿No podés entrar?", 15f, 700, C.violeta) }
                }
            }
        }
    } }
}

/** Los requisitos de la contraseña nueva, que se tildan solos mientras se escribe (los chips de la selfie). */
@Composable
internal fun IngresoRequisitosClave(clave: String) {
    val (largo, comun) = ClerkMail.requisitos(clave)
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ingresoChipRequisito("8 caracteres o más", largo)
        ingresoChipRequisito("No es de las más usadas", comun)
    }
}
