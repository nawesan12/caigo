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

/**
 * Contacto de confianza: nombre y teléfono o mail. `avisa` (en «¿Todo bien?»): "contacto" (es un mail: le
 * escribimos nosotros) o "vos" (es un teléfono: no hay SMS, te avisamos a vos para que le escribas). No se
 * manda de vuelta a la API.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class Contacto(
    val nombre: String = "",
    val contacto: String = "",
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val avisa: String = "",
) {
    /** Le escribimos nosotros (mail); si no, te avisamos a vos. */
    val leEscribimos: Boolean get() = if (avisa.isEmpty()) contacto.contains("@") else avisa == "contacto"
    /** «Le escribimos a Mamá si no llegás» / «Te avisamos para que le escribas a Sofi». */
    val filaTodoBien: String
        get() = if (leEscribimos) "Le escribimos a $mostrar si no llegás" else "Te avisamos para que le escribas a $mostrar"
    /** El aviso al activarlo. */
    val activado: String get() = if (leEscribimos) "Si no llegás, le escribimos a $mostrar." else "Si no hacés check-in, te avisamos."
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
    /** Ya le preguntamos «¿Llegaste?» (el aviso sale 10 min después). */
    val preguntado: Boolean = false,
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
    /** «14 planes · 3 en lugares aliados · 1 de tu gente» (cada parte sin cortes adentro). */
    val resumen: String
        get() = listOfNotNull(
            if (total == 1) "1 plan" else "$total planes",
            when (aliados) { 0 -> null; 1 -> "1 en un lugar aliado"; else -> "$aliados en lugares aliados" },
            if (deTuGente == 0) null else "$deTuGente de tu gente",
        ).joinToString(" · ") { it.replace(" ", "\u00A0") }

    /** Las cifras de la pantalla: sólo las que dicen algo (un cero grande hace parecer vacía la ciudad). */
    val cifras: List<Pair<Int, String>>
        get() = listOf(total to (if (total == 1) "plan" else "planes"), aliados to "aliados", deTuGente to "de tu gente")
            .filterIndexed { i, x -> i == 0 || x.first > 0 }
}

/**
 * Las caritas del 1 al 5 (como Timeleft): privadas, sólo suman al promedio de quien organiza. Se dibujan con
 * los íconos `carita1`…`carita5` (del mismo trazo que el resto), no con emojis del sistema.
 */
object Caras {
    data class Cara(val valor: Int, val texto: String)
    val todas = listOf(Cara(1, "Mal"), Cara(2, "Flojo"), Cara(3, "Bien"), Cara(4, "Muy bien"), Cara(5, "Genial"))

    /** «4,5 · 10+ opiniones» (el promedio llega a medio punto y la cantidad en escalones). */
    fun texto(c: Caritas?): String? {
        if (c == null || c.cantidad < 3) return null
        val p = String.format(java.util.Locale.ROOT, "%.1f", c.promedio).replace(".", ",")
        return "$p\u00A0·\u00A0${c.cantidad}+\u00A0opiniones"
    }
}

object Dia {
    /**
     * «Hoy» va desde 3 h antes hasta 2 h después del final: la vuelta también es parte del plan («Me quiero
     * ir», compartir la ubicación). Activar «¿Todo bien?» sigue hasta el final (puedeActivarTodoBien).
     */
    fun enElDia(p: Plan, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): Boolean {
        if (p.cancelado) return false
        val t = Fecha.parse(p.empiezaEn) ?: return false
        return !ahora.isBefore(t.minusHours(3)) && ahora.isBefore(t.plusMinutes(p.duracionMin.toLong()).plusHours(2))
    }

    /** «¿Todo bien?» se activa hasta el final del plan (como la API). */
    fun puedeActivarTodoBien(p: Plan, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): Boolean {
        val t = Fecha.parse(p.empiezaEn) ?: return false
        return ahora.isBefore(t.plusMinutes(p.duracionMin.toLong()))
    }

    /** Los minutos que faltan, para arriba: la misma cuenta en el chip «Ahora · en N» y en «Hoy». */
    fun minutosHasta(t: ZonedDateTime, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): Int {
        val seg = ChronoUnit.SECONDS.between(ahora, t)
        return Math.floorDiv(seg + 59, 60L).toInt()
    }

    /** «Es tuyo hasta las 21:40» (el lugar guardado de la lista de espera); null si ya venció. */
    fun lugarGuardado(venceEn: String, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String? {
        val t = Fecha.parse(venceEn) ?: return null
        if (!t.isAfter(ahora)) return null
        return "Es tuyo hasta las ${Fecha.hora(t)}"
    }

    /** «en 25 min» · «en 2 h 10» · «Ya arrancó». */
    fun cuentaRegresiva(empieza: String, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String {
        val t = Fecha.parse(empieza) ?: return ""
        val min = minutosHasta(t, ahora)
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

    /**
     * El número para WhatsApp. Con «+», tal cual (otro país). Si no, Argentina: sin el 0 de larga distancia y
     * sin el 15 de celular que va después de la característica (2 a 4 dígitos), con 549 adelante.
     * «0223 15 592-6772», «223 15 5926772» y «2235926772» dan 5492235926772. Igual que dia.ts y Dia.swift.
     */
    fun numeroWhatsapp(tel: String): String {
        val d = tel.filter { it.isDigit() }
        if (tel.trim().startsWith("+")) return d
        var n = d.removePrefix("0")
        if (n.startsWith("549") && n.length == 13) return n
        if (n.startsWith("54") && n.length == 12) return "549" + n.drop(2)
        if (n.length == 12) {
            for (k in 2..4) {
                if (n.substring(k, k + 2) == "15") {
                    n = n.substring(0, k) + n.substring(k + 2)
                    break
                }
            }
        }
        return if (n.length == 10) "549$n" else d
    }

    /** wa.me con el número y el texto. */
    fun whatsapp(tel: String, texto: String): String =
        "https://wa.me/${numeroWhatsapp(tel)}?text=" + URLEncoder.encode(texto, "UTF-8").replace("+", "%20")

    /** Pedir un auto para volver ya. */
    const val PEDIR_AUTO = "https://m.uber.com/ul/?action=setPickup&pickup=my_location"
}
