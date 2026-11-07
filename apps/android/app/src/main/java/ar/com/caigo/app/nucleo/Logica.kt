package ar.com.caigo.app.nucleo

import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

/** El cupo del plan: el badge "Falta 1 / Faltan N / Completo" (README · Interactions). */
object Cupo {
    fun badge(faltan: Int): String = when {
        faltan <= 0 -> "Completo"
        faltan == 1 -> "Falta 1"
        else -> "Faltan $faltan"
    }

    fun somos(somos: Int, total: Int): String = "Somos $somos de $total"

    fun completo(faltan: Int) = faltan <= 0

    /** Cuántos faltan si se suman [extra] personas más: f = total − (sumados + extra). */
    fun faltan(total: Int, somos: Int, extra: Int = 0): Int = (total - (somos + extra)).coerceAtLeast(0)

    /** Segmentos de la barra: true = ocupado (lima), false = vacío (borde coral punteado). */
    fun segmentos(somos: Int, total: Int): List<Boolean> = List(total.coerceIn(0, 60)) { it < somos }
}

/** «Me encanta» en Citas: 1 por día sin Plus, 5 con Plus (la API corta con 409 `limite_encanta`). */
object Encantas {
    fun quedan(hoy: Int, limite: Int): Int = (limite - hoy).coerceAtLeast(0)

    /** Lo que dice la API al pasarse (gente.Reaccionar), para no hacer volar la carta en vano. */
    fun aviso(plus: Boolean, limite: Int): String =
        if (plus) "Ya usaste los $limite me encanta de hoy." else "Ya usaste tu me encanta de hoy. Con Plus tenés 5 por día."
}

/** Plata en centavos → "$4.500" (pesos argentinos, sin decimales si son redondos). */
object Plata {
    private fun miles(n: Long): String {
        val s = abs(n).toString()
        val out = StringBuilder()
        s.forEachIndexed { i, ch ->
            if (i > 0 && (s.length - i) % 3 == 0) out.append('.')
            out.append(ch)
        }
        return (if (n < 0) "-" else "") + out
    }

    fun pesos(centavos: Long): String {
        if (centavos % 100 == 0L) return "$" + miles(centavos / 100)
        val c = abs(centavos % 100)
        return "$" + miles(centavos / 100) + "," + (if (c < 10) "0$c" else "$c")
    }

    /** Redondeo a $100 (10.000 centavos), como Math.round en la API. */
    internal fun aCien(centavos: Double): Long = Math.round(centavos / 10000) * 10000

    /** Cargo por servicio por persona: 7,5 % del precio, redondeado a $100 (planes.Cargo en la API). */
    fun cargo(precioCentavos: Long): Long = aCien(precioCentavos * 0.075)

    /** Lo que se le paga al lugar por persona ahora: el precio entero o la seña redondeada a $100. */
    fun alLugar(precioCentavos: Long, cobro: String, senaPct: Int?): Long =
        if (cobro == "sena" && senaPct != null) aCien(precioCentavos.toDouble() * senaPct / 100) else precioCentavos

    data class Desglose(val alLugar: Long, val cargo: Long, val total: Long, val saldo: Long)

    /** El desglose del checkout para [cantidad] entradas (igual que lugares.Comprar). */
    fun desglose(precioCentavos: Long, cobro: String, senaPct: Int?, cantidad: Int, cargoPorPersona: Long? = null): Desglose {
        val q = cantidad.toLong()
        val c = cargoPorPersona ?: cargo(precioCentavos)
        val lugar = alLugar(precioCentavos, cobro, senaPct)
        return Desglose(lugar * q, c * q, (lugar + c) * q, (precioCentavos - lugar) * q)
    }
}

/**
 * Lo que trae el PDF417 del frente del DNI argentino. Formato nuevo (desde 2012):
 * `trámite@apellido@nombres@sexo@número@ejemplar@nacimiento@emisión@…`.
 * Formato viejo: `@número@ejemplar@?@apellido@nombres@nacionalidad@nacimiento@sexo@emisión@trámite@…`.
 */
data class DatosDni(
    val tramite: String,
    val apellido: String,
    val nombres: String,
    val sexo: String,
    val numero: String,
    val ejemplar: String,
    /** YYYY-MM-DD */
    val nacimiento: String,
)

object Pdf417Dni {
    private val fecha = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$")

    private fun iso(f: String): String? = fecha.find(f.trim())?.destructured?.let { (d, m, a) -> "$a-$m-$d" }

    private fun limpio(s: String) = s.trim().replace(Regex("\\s+"), " ")

    fun leer(crudo: String): DatosDni? {
        val c = crudo.trim().split('@').map { it.trim() }
        if (c.size < 8) return null
        // Nuevo: el primero es el número de trámite (11 dígitos) y el 7.º la fecha de nacimiento.
        if (c[0].all(Char::isDigit) && c[0].isNotEmpty() && iso(c[6]) != null) {
            val numero = c[4].filter(Char::isDigit)
            if (numero.length !in 7..8) return null
            return DatosDni(
                tramite = c[0], apellido = limpio(c[1]), nombres = limpio(c[2]), sexo = c[3].uppercase(),
                numero = numero, ejemplar = c[5], nacimiento = iso(c[6])!!,
            )
        }
        // Viejo: arranca con "@", el número va en el 2.º campo (puede tener letra al final).
        if (c[0].isEmpty() && c.size >= 10) {
            val numero = c[1].filter(Char::isDigit)
            val nacimiento = iso(c[6]) ?: return null
            if (numero.length !in 7..8) return null
            val tramite = c.drop(8).firstOrNull { it.length >= 9 && it.all(Char::isDigit) }.orEmpty()
            return DatosDni(
                tramite = tramite, apellido = limpio(c[3]), nombres = limpio(c[4]), sexo = c[7].uppercase(),
                numero = numero, ejemplar = c[2], nacimiento = nacimiento,
            )
        }
        return null
    }
}

/**
 * El mazo arrastrable de Gente / Citas, con las físicas del prototipo: rotación dx/16°, salida a
 * ±520 pt, umbral de 100 pt, tinte lima/coral según el lado y la carta de abajo de .93 a 1.
 */
object FisicaMazo {
    const val umbral = 100f
    const val salida = 520f

    data class Estado(
        val indice: Int = 0,
        val dx: Float = 0f,
        val dy: Float = 0f,
        val arrastrando: Boolean = false,
        /** -1 sale a la izquierda, 1 a la derecha, 0 nada. */
        val vuela: Int = 0,
        val foto: Int = 0,
    )

    sealed interface Accion {
        data object Empieza : Accion
        data class Mueve(val dx: Float, val dy: Float) : Accion
        /** Soltó: si pasó el umbral, vuela; si no, vuelve. */
        data object Suelta : Accion
        data class Vuela(val lado: Int) : Accion
        /** Terminó la animación de salida: pasa a la siguiente carta. */
        data object Siguiente : Accion
        data class Foto(val delta: Int, val cantidad: Int) : Accion
    }

    fun reducir(e: Estado, a: Accion): Estado = when (a) {
        Accion.Empieza -> e.copy(arrastrando = true, dx = 0f, dy = 0f)
        is Accion.Mueve -> if (e.arrastrando) e.copy(dx = a.dx, dy = a.dy) else e
        Accion.Suelta -> when {
            !e.arrastrando -> e
            kotlin.math.abs(e.dx) > umbral -> e.copy(arrastrando = false, vuela = if (e.dx > 0) 1 else -1)
            else -> e.copy(arrastrando = false, dx = 0f, dy = 0f)
        }
        is Accion.Vuela -> e.copy(vuela = a.lado, arrastrando = false)
        Accion.Siguiente -> Estado(indice = e.indice + 1)
        is Accion.Foto -> e.copy(foto = (e.foto + a.delta).coerceIn(0, (a.cantidad - 1).coerceAtLeast(0)))
    }

    /** Desplazamiento a dibujar: si vuela, sale a ±520 y sube 40. */
    fun desplazamiento(e: Estado): Pair<Float, Float> =
        if (e.vuela != 0) e.vuela * salida to e.dy - 40f else e.dx to e.dy

    fun rotacion(dx: Float): Float = dx / 16f

    /** 0..1: cuánto se nota el arrastre (tinte, escala de la carta de abajo). */
    fun fuerza(dx: Float): Float = (kotlin.math.abs(dx) / 140f).coerceAtMost(1f)

    fun escalaSiguiente(dx: Float): Float = .93f + .07f * fuerza(dx)
    fun alfaSiguiente(dx: Float): Float = .55f + .45f * fuerza(dx)
    fun alfaMeGusta(dx: Float): Float = (dx / 90f).coerceIn(0f, 1f)
    fun alfaPaso(dx: Float): Float = (-dx / 90f).coerceIn(0f, 1f)
}

/** Categorías de plan (planes.go · Categorias), en el orden del handoff. */
object Categorias {
    val todas: List<Pair<String, String>> = listOf(
        "deporte" to "Deporte", "juego" to "Juego", "previa" to "Previa", "salida" to "Salida",
        "cita_doble" to "Cita doble", "cultura" to "Cultura", "gastronomia" to "Gastronomía", "otro" to "Otro",
        "taller" to "Taller", "musica" to "Música en vivo", "fiesta" to "Fiesta",
    )

    /** Las 8 que se eligen al crear un plan (06a). */
    val paraCrear: List<Pair<String, String>> = todas.take(8)

    fun nombre(clave: String): String =
        todas.firstOrNull { it.first == clave }?.second ?: (clave.take(1).uppercase() + clave.drop(1))
}

/** "800 m", "1,2 km". */
fun distancia(m: Int?): String = when {
    m == null -> ""
    m < 100 -> "$m m"
    m < 1000 -> "${((m + 50) / 100) * 100} m"
    m < 10_000 -> String.format(Locale.ROOT, "%.1f km", m / 1000.0).replace('.', ',')
    else -> "${m / 1000} km"
}

/** Link público de un plan. */
fun linkPlan(slug: String) = "caigo.com.ar/p/$slug"

/** Centro de Mar del Plata: donde arranca el mapa si no hay permiso de ubicación. */
const val latMDP = -38.0023
const val lngMDP = -57.5575

/** 13a · Ahora sin planes: la primera frase cambia según el día y la hora (en Argentina). */
object VacioAhora {
    const val titulo = "No hay planes cerca. ¿Armás uno?"

    fun cuerpo(d: ZonedDateTime = ZonedDateTime.now(ZonaAR)): String {
        val t = d.withZoneSameInstant(ZonaAR)
        val dia = Fecha.diaLargo(t)
        val frase = when (t.hour) {
            in 0 until 6 -> "$dia de madrugada, la costa duerme."
            in 6 until 12 -> "$dia a la mañana, la costa recién se despierta."
            in 12 until 17 -> "$dia a la siesta, la costa duerme."
            in 17 until 20 -> "$dia a la tarde, la costa está tranqui."
            else -> "$dia a la noche y nadie armó nada todavía."
        }
        return "$frase Si publicás algo ahora, sos lo primero que ven."
    }
}
