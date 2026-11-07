package ar.com.caigo.app.nucleo

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Hora de Argentina: todo lo que se muestra va en esta zona. */
val ZonaAR: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")

/** Fechas en hora de Argentina: todo lo que se muestra va en esta zona. */
object Fecha {
    val zona: ZoneId get() = ZonaAR

    /** RFC 3339 como lo manda Go (con o sin fracción de segundo, de cualquier largo). */
    fun parse(iso: String): ZonedDateTime? =
        if (iso.isEmpty()) null else runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZonaAR) }.getOrNull()

    /** Para mandar a la API: RFC 3339 con el desfase de Argentina. */
    fun iso(t: ZonedDateTime): String =
        t.withZoneSameInstant(ZonaAR).truncatedTo(ChronoUnit.SECONDS).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private val dias = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private val diasLargos = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    private val meses = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    fun hora(t: ZonedDateTime): String = t.withZoneSameInstant(ZonaAR).let { String.format(java.util.Locale.ROOT, "%02d:%02d", it.hour, it.minute) }
    fun hora(iso: String): String = parse(iso)?.let(::hora).orEmpty()

    fun diaCorto(t: ZonedDateTime): String = dias[t.withZoneSameInstant(ZonaAR).dayOfWeek.value - 1]
    fun diaLargo(t: ZonedDateTime): String = diasLargos[t.withZoneSameInstant(ZonaAR).dayOfWeek.value - 1]
    fun mes(t: ZonedDateTime): String = meses[t.withZoneSameInstant(ZonaAR).monthValue - 1]

    fun hoy(): LocalDate = LocalDate.now(ZonaAR)

    /** Días de calendario entre dos fechas (en Argentina). */
    fun diasEntre(a: ZonedDateTime, b: ZonedDateTime): Int =
        ChronoUnit.DAYS.between(a.withZoneSameInstant(ZonaAR).toLocalDate(), b.withZoneSameInstant(ZonaAR).toLocalDate()).toInt()

    /** "Hoy 20:30", "Mañana 21:00", "Sáb 10:00". */
    fun cuando(iso: String, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String {
        val t = parse(iso) ?: return ""
        return when (diasEntre(ahora, t)) {
            0 -> "Hoy ${hora(t)}"
            1 -> "Mañana ${hora(t)}"
            else -> "${diaCorto(t)} ${hora(t)}"
        }
    }

    /** "Ahora · en 2 h", "Ahora · en 40 min" o null si falta más de 3 h. */
    fun enCuanto(iso: String, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String? {
        val t = parse(iso) ?: return null
        val min = ChronoUnit.SECONDS.between(ahora, t) / 60
        return when {
            min <= 0 -> "Ahora"
            min < 60 -> "Ahora · en $min min"
            min <= 180 -> "Ahora · en ${(min + 30) / 60} h"
            else -> null
        }
    }

    /** "Hoy, 20:30 a 21:30" */
    fun rango(iso: String, duracionMin: Int, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String {
        val t = parse(iso) ?: return ""
        val fin = t.plusMinutes(duracionMin.toLong())
        val dia = when (diasEntre(ahora, t)) {
            0 -> "Hoy"
            1 -> "Mañana"
            else -> "${diaLargo(t)} ${t.dayOfMonth}"
        }
        return "$dia, ${hora(t)} a ${hora(fin)}"
    }

    /** "hace 20 min", "hace 2 h", "ayer", "hace 3 días". */
    fun hace(iso: String, ahora: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String {
        val t = parse(iso) ?: return ""
        val min = ChronoUnit.SECONDS.between(t, ahora) / 60
        val dias = diasEntre(t, ahora)
        return when {
            min < 1 -> "recién"
            min < 60 -> "hace $min min"
            dias == 0 -> "hace ${min / 60} h"
            dias == 1 -> "ayer"
            else -> "hace $dias días"
        }
    }
}
