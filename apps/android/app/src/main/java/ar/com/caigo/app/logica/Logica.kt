package ar.com.caigo.app.logica

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToLong

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

/** Plata en centavos → "$4.500" (pesos argentinos, sin decimales si son redondos). */
object Plata {
    private val miles = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.forLanguageTag("es-AR")).apply { groupingSeparator = '.' })
    private val conDecimales = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.forLanguageTag("es-AR")).apply {
        groupingSeparator = '.'; decimalSeparator = ','
    })

    fun pesos(centavos: Long): String =
        if (centavos % 100 == 0L) "$" + miles.format(centavos / 100) else "$" + conDecimales.format(centavos / 100.0)

    /** Cargo por servicio por persona: 7,5 % del precio, redondeado a $100 (planes.Cargo en la API). */
    fun cargo(precioCentavos: Long): Long = (precioCentavos * 0.075 / 10000).roundToLong() * 10000

    /** Lo que se le paga al lugar por persona ahora: el precio entero o la seña redondeada a $100 (como la API y el panel). */
    fun alLugar(precioCentavos: Long, cobro: String, senaPct: Int?): Long =
        if (cobro == "sena" && senaPct != null) (precioCentavos * senaPct / 100.0 / 10000).roundToLong() * 10000
        else precioCentavos

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
 * ±520 px, umbral de 100 px, tinte lima/coral según el lado y la carta de abajo de .93 a 1.
 */
object Mazo {
    const val UMBRAL = 100f
    const val SALIDA = 520f

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
            kotlin.math.abs(e.dx) > UMBRAL -> e.copy(arrastrando = false, vuela = if (e.dx > 0) 1 else -1)
            else -> e.copy(arrastrando = false, dx = 0f, dy = 0f)
        }
        is Accion.Vuela -> e.copy(vuela = a.lado, arrastrando = false)
        Accion.Siguiente -> Estado(indice = e.indice + 1)
        is Accion.Foto -> e.copy(foto = (e.foto + a.delta).coerceIn(0, (a.cantidad - 1).coerceAtLeast(0)))
    }

    /** Desplazamiento a dibujar: si vuela, sale a ±520 y sube 40. */
    fun desplazamiento(e: Estado): Pair<Float, Float> =
        if (e.vuela != 0) e.vuela * SALIDA to e.dy - 40f else e.dx to e.dy

    fun rotacion(dx: Float): Float = dx / 16f

    /** 0..1: cuánto se nota el arrastre (tinte, escala de la carta de abajo). */
    fun fuerza(dx: Float): Float = (kotlin.math.abs(dx) / 140f).coerceAtMost(1f)

    fun escalaSiguiente(dx: Float): Float = .93f + .07f * fuerza(dx)
    fun alfaSiguiente(dx: Float): Float = .55f + .45f * fuerza(dx)
    fun alfaMeGusta(dx: Float): Float = (dx / 90f).coerceIn(0f, 1f)
    fun alfaPaso(dx: Float): Float = (-dx / 90f).coerceIn(0f, 1f)
}
