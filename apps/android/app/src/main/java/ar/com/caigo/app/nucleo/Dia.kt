package ar.com.caigo.app.nucleo

import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/*
 * El día del plan, después del plan y el ritual de los lunes (informe del 18/11): lo de quien mira en un
 * plan (`mio`), «¿Todo bien?», la lista de espera, las fotos del plan y «Esta semana». Igual que
 * Nucleo/Dia.swift en el iPhone.
 */

/** Lo de quien mira en un plan donde está. */
@Serializable
data class Mio(
    val llegue: Boolean = false,
    val confirme: Boolean = false,
    val carita: Int? = null,
    val todoBien: Boolean = false,
    val fotos: Int = 0,
    val espera: EsperaMia? = null,
)

/** En la lista de espera: `esperando` (con su lugar en la fila) o `avisado` (se liberó un lugar, hasta `venceEn`). */
@Serializable
data class EsperaMia(val estado: String = "esperando", val posicion: Int = 0, val venceEn: String = "")

@Serializable
data class Caritas(val promedio: Double = 0.0, val cantidad: Int = 0)

/** Contacto de confianza: nombre y teléfono o mail. */
@Serializable
data class Contacto(val nombre: String = "", val contacto: String = "") {
    /** Lo que se muestra: el nombre, o el teléfono o mail si no tiene. */
    val mostrar: String get() = nombre.ifEmpty { contacto }
    /** Es un teléfono (se puede llamar o escribirle por WhatsApp). */
    val esTelefono: Boolean get() = !contacto.contains("@") && contacto.count { it.isDigit() } >= 6
}

/** GET /v1/planes/{id}/todo-bien. */
@Serializable
data class TodoBien(
    val activo: Boolean = false,
    val contacto: Contacto? = null,
    val avisaEn: String = "",
    val avisado: Boolean = false,
    val contactos: List<Contacto> = emptyList(),
)

@Serializable
data class FotoPlan(val id: String = "", val url: String = "", val autor: PersonaCorta = PersonaCorta(), val mia: Boolean = false, val creadaEn: String = "")

@Serializable
data class FotosPlan(val fotos: List<FotoPlan> = emptyList())

@Serializable
data class DiaSemana(val dia: String = "", val cantidad: Int = 0)

/** GET /v1/semana: «Esta semana en <ciudad>». */
@Serializable
data class Semana(
    val ciudad: String = "",
    val total: Int = 0,
    val aliados: Int = 0,
    val deTuGente: Int = 0,
    val dias: List<DiaSemana> = emptyList(),
    val planes: List<Plan> = emptyList(),
) {
    /** «14 planes · 3 en lugares aliados · 1 de tu gente». */
    val resumen: String
        get() = listOfNotNull(
            if (total == 1) "1 plan" else "$total planes",
            when (aliados) { 0 -> null; 1 -> "1 en un lugar aliado"; else -> "$aliados en lugares aliados" },
            if (deTuGente == 0) null else "$deTuGente de tu gente",
        ).joinToString(" · ")
}

/** Las caritas del 1 al 5 (como Timeleft): privadas, sólo suman al promedio de quien organiza. */
object Caras {
    data class Cara(val valor: Int, val cara: String, val texto: String)
    val todas = listOf(Cara(1, "😣", "Mal"), Cara(2, "😕", "Flojo"), Cara(3, "🙂", "Bien"), Cara(4, "😄", "Muy bien"), Cara(5, "🤩", "Genial"))
}

object Dia {
    /** «Hoy» va desde 3 h antes hasta el final del plan. */
    fun enElDia(p: Plan, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): Boolean {
        if (p.cancelado) return false
        val t = Fecha.parse(p.empiezaEn) ?: return false
        return !ahora.isBefore(t.minusHours(3)) && ahora.isBefore(t.plusMinutes(p.duracionMin.toLong()))
    }

    /** «en 25 min» · «en 2 h 10» · «Ya arrancó». */
    fun cuentaRegresiva(empieza: String, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String {
        val t = Fecha.parse(empieza) ?: return ""
        val seg = ChronoUnit.SECONDS.between(ahora, t)
        val min = ((seg + 59) / 60).toInt()
        if (min <= 0) return "Ya arrancó"
        if (min < 60) return "en $min min"
        val h = min / 60
        val m = min % 60
        return if (m == 0) "en $h h" else String.format(java.util.Locale.ROOT, "en %d h %02d", h, m)
    }

    /** La víspera: el plan es mañana. */
    fun esVispera(p: Plan, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): Boolean {
        val t = Fecha.parse(p.empiezaEn) ?: return false
        return Fecha.diasEntre(ahora, t) == 1
    }

    /** La tarjeta «Esta semana» arriba de Ahora: de lunes a miércoles. */
    fun semanaVisible(ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): Boolean =
        ahora.withZoneSameInstant(ZonaAR).dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)

    fun comoLlegar(lat: Double, lng: Double): String = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"

    fun linkUbicacion(lat: Double, lng: Double): String =
        String.format(java.util.Locale.ROOT, "https://maps.google.com/?q=%.5f,%.5f", lat, lng)

    /** wa.me con el número (Argentina por defecto) y el texto. */
    fun whatsapp(tel: String, texto: String): String {
        var n = tel.filter { it.isDigit() }
        if (!tel.trim().startsWith("+")) n = "549" + n.removePrefix("0").removePrefix("15")
        return "https://wa.me/$n?text=" + URLEncoder.encode(texto, "UTF-8").replace("+", "%20")
    }

    /** Pedir un auto para volver ya. */
    const val PEDIR_AUTO = "https://m.uber.com/ul/?action=setPickup&pickup=my_location"
}
