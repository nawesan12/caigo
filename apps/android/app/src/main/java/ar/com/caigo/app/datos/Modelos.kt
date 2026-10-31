package ar.com.caigo.app.datos

import kotlinx.serialization.Serializable

/*
 * Lo que manda y recibe la API (apps/api/internal/dominio/<dominio>/ (archivos .go)). Los nombres de los
 * campos son los del JSON; todo lo opcional tiene valor por defecto para que un campo nuevo o que
 * falte no rompa la app.
 */

@Serializable
data class Asist(val fue: Int = 0, val de: Int = 0)

/** Vista corta de alguien en un plan (avatares, organizador, solicitudes). */
@Serializable
data class PersonaCorta(
    val id: String = "",
    val nombre: String = "",
    val foto: String = "",
    val edad: Int? = null,
    val verificada: Boolean = false,
    val asistencia: Asist = Asist(),
    val enMiGente: Boolean = false,
    val organizador: Boolean = false,
    /** Ya hizo check-in (sólo en la lista de los que van). */
    val llego: Boolean = false,
)

@Serializable
data class Direccion(val texto: String = "", val lat: Double = 0.0, val lng: Double = 0.0)

@Serializable
data class LugarCorto(val id: String = "", val slug: String = "", val nombre: String = "", val zona: String = "", val foto: String = "")

@Serializable
data class Plan(
    val id: String,
    val slug: String = "",
    val titulo: String = "",
    val categoria: String = "",
    val descripcion: String = "",
    val empiezaEn: String = "",
    val duracionMin: Int = 120,
    val recurrente: Boolean = false,
    val ciudad: String = "",
    val zona: String = "",
    val lugarNombre: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val distanciaM: Int? = null,
    val somos: Int = 0,
    val total: Int = 0,
    val faltan: Int = 0,
    val ingreso: String = "aprobacion",
    val visibilidad: String = "publico",
    val soloVerificados: Boolean = false,
    val ocultarDireccion: Boolean = false,
    val edadMin: Int? = null,
    val edadMax: Int? = null,
    val genero: String = "cualquiera",
    val nivel: String = "",
    val costoCentavos: Int? = null,
    val foto: String = "",
    val cancelado: Boolean = false,
    val organizador: PersonaCorta = PersonaCorta(),
    val van: List<PersonaCorta> = emptyList(),
    /** ninguno | pendiente | aceptada | rechazada | cancelada | organizador */
    val miEstado: String = "ninguno",
    val miCantidad: Int = 0,
    val direccion: Direccion? = null,
    val pendientes: Int = 0,
    val lugar: LugarCorto? = null,
    /** gratis | entrada | sena */
    val cobro: String = "gratis",
    val precioCentavos: Int? = null,
    val senaPct: Int? = null,
    val cargoCentavos: Long = 0,
    val incluye: String = "",
    val cancelacion: String = "",
    val beneficio: String = "",
    val aceptaGrupos: Boolean = true,
    /** Destacado con Caigo Plus: va primero en Ahora y Explorar. */
    val destacado: Boolean = false,
)

@Serializable
data class Planes(val planes: List<Plan> = emptyList())

/** Lo que se manda para crear un plan (struct Entrada de planes.go). */
@Serializable
data class PlanNuevo(
    val titulo: String = "",
    val categoria: String,
    val descripcion: String = "",
    val empiezaEn: String,
    val duracionMin: Int = 0,
    val recurrente: Boolean = false,
    val ciudad: String,
    val zona: String = "",
    val lugarNombre: String = "",
    val direccion: String = "",
    val lat: Double,
    val lng: Double,
    val somos: Int,
    val faltan: Int,
    val ingreso: String = "aprobacion",
    val visibilidad: String = "publico",
    val soloVerificados: Boolean? = null,
    val ocultarDireccion: Boolean? = null,
    val edadMin: Int? = null,
    val edadMax: Int? = null,
    val genero: String = "cualquiera",
    val nivel: String = "",
    val costoCentavos: Int? = null,
    val foto: String = "",
)

/** PATCH /v1/planes/{id}: sólo lo que cambia (null no se manda). */
@Serializable
data class CambiosPlan(
    val titulo: String? = null,
    val descripcion: String? = null,
    val empiezaEn: String? = null,
    val duracionMin: Int? = null,
    val total: Int? = null,
    val ingreso: String? = null,
    val visibilidad: String? = null,
    val soloVerificados: Boolean? = null,
    val ocultarDireccion: Boolean? = null,
    val edadMin: Int? = null,
    val edadMax: Int? = null,
    val genero: String? = null,
    val nivel: String? = null,
    val costoCentavos: Int? = null,
    val foto: String? = null,
    val zona: String? = null,
    val lugarNombre: String? = null,
    val direccion: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class Estado(val estado: String = "")

@Serializable
data class Solicitud(
    val id: String,
    val persona: PersonaCorta = PersonaCorta(),
    val cantidad: Int = 1,
    val mensaje: String = "",
    val estado: String = "",
    val creadaEn: String = "",
)

@Serializable
data class Solicitudes(val solicitudes: List<Solicitud> = emptyList())

@Serializable
data class Codigo(val codigo: String = "")

@Serializable
data class Link(val link: String = "")

@Serializable
data class Nuevo(val nuevo: Plan? = null)

@Serializable
data class Opinion(val usuarioId: String, val si: Boolean)

// ——— Personas ———

@Serializable
data class Prompt(val p: String = "", val r: String = "")

@Serializable
data class Perfil(
    val id: String = "",
    val nombre: String = "",
    val edad: Int? = null,
    val zona: String = "",
    val ciudad: String = "",
    val bio: String = "",
    val trabajo: String = "",
    val intereses: List<String> = emptyList(),
    val intenciones: List<String> = emptyList(),
    val prompts: List<Prompt> = emptyList(),
    val fotos: List<String> = emptyList(),
    val selfie: Boolean = false,
    val verificada: Boolean = false,
    val asistencia: Asist = Asist(),
    val planes: Int = 0,
    val organizados: Int = 0,
    val miGente: Int = 0,
    val enMiGente: Boolean = false,
    val compartidos: Int = 0,
    val equipo: Boolean = false,
    val telefono: String = "",
    /** Autodeclarado, no se muestra a nadie: "" | mujer | varon | no_binario. */
    val genero: String = "",
    /** «Me identifico como parte de la diversidad». */
    val diversidad: Boolean = false,
    val avisos: AjustesAvisos = AjustesAvisos(),
    /** Si la API lo devuelve (hoy sólo se manda en PATCH): teléfono o mail. */
    val contactoConfianza: String? = null,
    /** Próximos planes de otra persona que quien mira puede ver. */
    val proximos: List<ProximoPlan> = emptyList(),
)

@Serializable
data class AjustesAvisos(val planesCerca: Boolean = true, val tuGente: Boolean = true)

@Serializable
data class CambiosPerfil(
    val nombre: String? = null,
    val nacimiento: String? = null,
    val zona: String? = null,
    val ciudad: String? = null,
    val bio: String? = null,
    val trabajo: String? = null,
    val intereses: List<String>? = null,
    val intenciones: List<String>? = null,
    val prompts: List<Prompt>? = null,
    val fotos: List<String>? = null,
    val genero: String? = null,
    val diversidad: Boolean? = null,
    val avisos: AjustesAvisos? = null,
    val contactoConfianza: String? = null,
)

/** Mi gente: la API devuelve el perfil (con `fotos`); se pasa a la vista corta que usan las listas. */
@Serializable
data class Gente(@kotlinx.serialization.SerialName("gente") val perfiles: List<Perfil> = emptyList()) {
    val gente: List<PersonaCorta>
        get() = perfiles.map {
            PersonaCorta(it.id, it.nombre, it.fotos.firstOrNull().orEmpty(), it.edad, it.verificada, it.asistencia, enMiGente = true)
        }
}

@Serializable
data class Reporte(val usuarioId: String = "", val planId: String = "", val motivo: String, val detalle: String = "")

// ——— Gente / Citas ———

@Serializable
data class ProximoPlan(
    val id: String = "",
    val titulo: String = "",
    val categoria: String = "",
    val empiezaEn: String = "",
    val foto: String = "",
    val faltan: Int? = null,
)

@Serializable
data class Tarjeta(
    val id: String,
    val nombre: String = "",
    val edad: Int? = null,
    val zona: String = "",
    val trabajo: String = "",
    val bio: String = "",
    val intereses: List<String> = emptyList(),
    val intenciones: List<String> = emptyList(),
    val prompts: List<Prompt> = emptyList(),
    val fotos: List<String> = emptyList(),
    val verificada: Boolean = false,
    val asistencia: Asist = Asist(),
    val planes: Int = 0,
    val enComun: List<String> = emptyList(),
    val proximos: List<ProximoPlan> = emptyList(),
)

@Serializable
data class Mazo(val personas: List<Tarjeta> = emptyList())

@Serializable
data class EstadoPlus(
    val activo: Boolean = false,
    val plan: String = "",
    val origen: String = "",
    val vence: String? = null,
    val renueva: Boolean = false,
    val sumadasHoy: Int = 0,
    val limite: Int = 3,
    val reiniciaEn: String = "",
    /** «Me encanta» de hoy en Citas y cuántos hay por día (1 sin Plus, 5 con Plus). */
    val encantasHoy: Int = 0,
    val limiteEncantas: Int = 1,
    /** Desde cuándo puede destacar otro plan (null: ya puede, si tiene Plus). */
    val destacarDesde: String? = null,
)

@Serializable
data class ResultadoReaccion(val mutuo: Boolean = false, val plus: EstadoPlus = EstadoPlus())

@Serializable
data class Recibido(
    val id: String = "",
    val nombre: String = "",
    val edad: Int? = null,
    val foto: String = "",
    val accion: String = "",
    val cuando: String = "",
)

@Serializable
data class Recibidos(val personas: List<Recibido> = emptyList(), val bloqueada: Boolean = false)

@Serializable
data class Invitacion(
    val planId: String,
    val titulo: String = "",
    val categoria: String = "",
    val empiezaEn: String = "",
    val de: Recibido? = null,
    val foto: String = "",
    val cuando: String = "",
)

@Serializable
data class Invitaciones(val invitaciones: List<Invitacion> = emptyList(), val bloqueada: Boolean = false)

@Serializable
data class Adentro(val adentro: Boolean = false)

// ——— Chat ———

@Serializable
data class Autor(val id: String = "", val nombre: String = "", val foto: String = "")

@Serializable
data class Mensaje(
    val id: Long,
    val planId: String = "",
    /** Sin autor: mensaje del sistema ("Plan completo. A disfrutar."). */
    val autor: Autor? = null,
    val texto: String = "",
    val creadoEn: String = "",
)

@Serializable
data class Mensajes(val mensajes: List<Mensaje> = emptyList())

@Serializable
data class Privado(
    val id: Long,
    val deId: String = "",
    val aId: String = "",
    val texto: String = "",
    val creadoEn: String = "",
    val leidoEn: String? = null,
)

@Serializable
data class Privados(val privados: List<Privado> = emptyList())

@Serializable
data class Conversacion(val con: Autor = Autor(), val ultimo: Privado = Privado(0), val sinLeer: Int = 0)

@Serializable
data class Conversaciones(val conversaciones: List<Conversacion> = emptyList())

/**
 * Evento del WebSocket de tiempo real. En «escribiendo» de un privado, `privado` llega como el id de
 * quien escribe (texto): lo separa [TiempoReal] y queda en [escribePrivado].
 */
@Serializable
data class Evento(
    val tipo: String = "",
    val mensaje: Mensaje? = null,
    val privado: Privado? = null,
    val planId: String = "",
    val quien: Autor? = null,
    @kotlinx.serialization.Transient val escribePrivado: String = "",
)

@Serializable
data class Bloqueo(val id: String = "", val nombre: String = "", val foto: String = "")

@Serializable
data class Bloqueos(val bloqueos: List<Bloqueo> = emptyList())

@Serializable
data class Zonas(val zonas: List<String> = emptyList())

@Serializable
data class Reembolso(val reembolsoCentavos: Long = 0)

/** GET /v1/version: versión de la API y la mínima de cada app (versionCode / build). */
@Serializable
data class Version(val version: String = "", val minAndroid: Int = 0, val minIos: Int = 0)

// ——— Confianza ———

@Serializable
data class Archivo(val id: String = "", val url: String = "")

@Serializable
data class CodigoDePrueba(val codigoDePrueba: String = "")

@Serializable
data class EstadoVerificacion(
    val telefono: String = "",
    val selfie: Boolean = false,
    /** ninguna | pendiente | aprobada | rechazada */
    val dni: String = "ninguna",
    val dniMotivo: String = "",
)

@Serializable
data class DatosDNI(
    val frenteId: String,
    val dorsoId: String,
    val selfieId: String,
    val numero: String,
    val apellido: String,
    val nombres: String,
    val sexo: String,
    val nacimiento: String,
    val tramite: String,
)

// ——— Sesión ———

@Serializable
data class SesionNueva(val token: String = "", val usuarioId: String = "", val equipo: Boolean = false)

// ——— Plus ———

@Serializable
data class PlanPlus(val plan: String = "", val producto: String = "", val centavos: Long = 0, val nombre: String = "")

@Serializable
data class Plus(val estado: EstadoPlus = EstadoPlus(), val planes: List<PlanPlus> = emptyList())

// ——— Lugares, entradas, pases, tap ———

@Serializable
data class Compra(
    val entradaId: String = "",
    val link: String = "",
    val totalCentavos: Long = 0,
    val cargoCentavos: Long = 0,
    val saldoCentavos: Long = 0,
)

@Serializable
data class EntradaMia(
    val id: String,
    val codigo: String = "",
    val planId: String = "",
    val titulo: String = "",
    val lugar: String = "",
    val empiezaEn: String = "",
    val cantidad: Int = 1,
    val modo: String = "",
    val estado: String = "",
    val saldoCentavos: Int = 0,
    val beneficio: String = "",
    val validada: Boolean = false,
)

@Serializable
data class PaseSocio(
    val lugarId: String,
    val lugar: String = "",
    val visitas: Int = 0,
    val meta: Int = 0,
    val beneficio: String = "",
    val pendientes: Int = 0,
)

@Serializable
data class Pases(val entradas: List<EntradaMia> = emptyList(), val socios: List<PaseSocio> = emptyList())

@Serializable
data class Lectura(val placa: String, val picc: String = "", val cmac: String = "")

@Serializable
data class PlanTap(
    val id: String = "",
    val titulo: String = "",
    val beneficio: String = "",
    val saldoCentavos: Int = 0,
    val repetido: Boolean = false,
)

@Serializable
data class SocioTap(
    val visitas: Int = 0,
    val meta: Int = 0,
    val beneficio: String = "",
    val gano: Boolean = false,
    val canjeo: Boolean = false,
    val sumo: Boolean = false,
)

@Serializable
data class ResultadoTap(val lugar: String = "", val uso: String = "", val plan: PlanTap? = null, val socio: SocioTap? = null)

/** Error de la API (application/problem+json). [detalle] se muestra tal cual. */
@Serializable
data class Problema(
    val title: String = "",
    val detail: String = "",
    val code: String = "",
    val status: Int = 0,
    val issues: List<Campo> = emptyList(),
)

@Serializable
data class Campo(val path: String = "", val message: String = "")

/**
 * Filtros finos de Ahora/Explorar (Caigo Plus): sólo verificados, que falten al menos N, edad de
 * quien organiza (18–99) y sólo gratis. Sin Plus la API responde 403 `plus`: no se mandan.
 */
data class FiltrosFinos(
    val verificados: Boolean = false,
    val faltan: Int? = null,
    val edadDesde: Int? = null,
    val edadHasta: Int? = null,
    val gratis: Boolean = false,
) {
    val alguno: Boolean get() = verificados || (faltan ?: 0) > 0 || edadDesde != null || edadHasta != null || gratis

    fun query(): Array<Pair<String, Any?>> = arrayOf(
        "verificados" to (if (verificados) "1" else null),
        "faltan" to faltan?.takeIf { it > 0 },
        "edadDesde" to edadDesde?.coerceIn(18, 99),
        "edadHasta" to edadHasta?.coerceIn(18, 99),
        "gratis" to (if (gratis) "1" else null),
    )
}
