package ar.com.caigo.app.logica

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CupoTest {
    @Test fun badge() {
        assertEquals("Completo", Cupo.badge(0))
        assertEquals("Completo", Cupo.badge(-2))
        assertEquals("Falta 1", Cupo.badge(1))
        assertEquals("Faltan 2", Cupo.badge(2))
        assertEquals("Faltan 12", Cupo.badge(12))
    }

    @Test fun faltanConExtra() {
        // f = total − (sumados + extra): "Somos 2" suma 2 en una sola solicitud.
        assertEquals(2, Cupo.faltan(10, 8))
        assertEquals(0, Cupo.faltan(10, 8, 2))
        assertEquals(0, Cupo.faltan(4, 3, 5))
        assertEquals("Somos 8 de 10", Cupo.somos(8, 10))
    }

    @Test fun segmentos() {
        assertEquals(listOf(true, true, true, false), Cupo.segmentos(3, 4))
    }
}

class PlataTest {
    @Test fun formato() {
        assertEquals("$4.500", Plata.pesos(450000))
        assertEquals("$17.200", Plata.pesos(1720000))
        assertEquals("$13.999", Plata.pesos(1399900))
        assertEquals("$0", Plata.pesos(0))
        assertEquals("$12,50", Plata.pesos(1250))
    }

    @Test fun cargoRedondeadoACien() {
        // 7,5 % de $8.000 = $600; de $4.500 = $337,50 → $300; de $5.000 = $375 → $400.
        assertEquals(60000, Plata.cargo(800000))
        assertEquals(30000, Plata.cargo(450000))
        assertEquals(40000, Plata.cargo(500000))
    }

    @Test fun desgloseEntrada() {
        // El checkout del prototipo: 2 entradas de $8.000 → $16.000 + $1.200 de cargo = $17.200.
        val d = Plata.desglose(800000, "entrada", null, 2)
        assertEquals(1600000, d.alLugar)
        assertEquals(120000, d.cargo)
        assertEquals(1720000, d.total)
        assertEquals(0, d.saldo)
    }

    @Test fun desgloseSena() {
        // Seña del 30 % de $8.000 = $2.400 por persona; el resto ($5.600) se paga en el lugar.
        val d = Plata.desglose(800000, "sena", 30, 1)
        assertEquals(240000, d.alLugar)
        assertEquals(60000, d.cargo)
        assertEquals(300000, d.total)
        assertEquals(560000, d.saldo)
        // La seña se redondea a $100, como la API: 30 % de $11.110 = $3.333 → $3.300.
        assertEquals(330000, Plata.alLugar(1111000, "sena", 30))
        assertEquals(170000, Plata.alLugar(550000, "sena", 30))
    }
}

class Pdf417DniTest {
    @Test fun formatoNuevo() {
        val d = Pdf417Dni.leer("00654321987@GARCIA LOPEZ@MARIA  SOL@F@35123456@B@14/03/1994@20/05/2019@239")!!
        assertEquals("00654321987", d.tramite)
        assertEquals("GARCIA LOPEZ", d.apellido)
        assertEquals("MARIA SOL", d.nombres)
        assertEquals("F", d.sexo)
        assertEquals("35123456", d.numero)
        assertEquals("B", d.ejemplar)
        assertEquals("1994-03-14", d.nacimiento)
    }

    @Test fun formatoViejo() {
        val d = Pdf417Dni.leer("@17463537    A@1@PEREZ@JUAN CARLOS@ARGENTINA@08/07/1965@M@15/07/2009@00065327459@7174 @15/07/2024@317@0@ILR:2.01 C:110608.03 (No Cap.)@UNIDAD #20 || S/N: 0040>2008>>0006")!!
        assertEquals("17463537", d.numero)
        assertEquals("PEREZ", d.apellido)
        assertEquals("JUAN CARLOS", d.nombres)
        assertEquals("M", d.sexo)
        assertEquals("1965-07-08", d.nacimiento)
        assertEquals("00065327459", d.tramite)
    }

    @Test fun basura() {
        assertNull(Pdf417Dni.leer("hola"))
        assertNull(Pdf417Dni.leer("1@2@3@4@5@6@7@8"))
        assertNull(Pdf417Dni.leer("00654321987@A@B@F@12@B@14/03/1994@20/05/2019"))
    }
}

class MazoTest {
    private fun r(e: Mazo.Estado, vararg a: Mazo.Accion) = a.fold(e) { x, y -> Mazo.reducir(x, y) }

    @Test fun pasaElUmbralYVuela() {
        val e = r(Mazo.Estado(), Mazo.Accion.Empieza, Mazo.Accion.Mueve(140f, 10f), Mazo.Accion.Suelta)
        assertEquals(1, e.vuela)
        assertEquals(Mazo.SALIDA to -30f, Mazo.desplazamiento(e))
        val sig = Mazo.reducir(e, Mazo.Accion.Siguiente)
        assertEquals(1, sig.indice)
        assertEquals(0, sig.vuela)
        assertEquals(0f, sig.dx)
    }

    @Test fun noLlegaYVuelve() {
        val e = r(Mazo.Estado(), Mazo.Accion.Empieza, Mazo.Accion.Mueve(-90f, 0f), Mazo.Accion.Suelta)
        assertEquals(0, e.vuela)
        assertEquals(0f, e.dx)
    }

    @Test fun izquierdaEsPaso() {
        val e = r(Mazo.Estado(), Mazo.Accion.Empieza, Mazo.Accion.Mueve(-101f, 0f), Mazo.Accion.Suelta)
        assertEquals(-1, e.vuela)
    }

    @Test fun fisicas() {
        assertEquals(10f, Mazo.rotacion(160f), 0.001f)
        assertEquals(.93f, Mazo.escalaSiguiente(0f), 0.001f)
        assertEquals(1f, Mazo.escalaSiguiente(200f), 0.001f)
        assertEquals(1f, Mazo.alfaMeGusta(120f), 0.001f)
        assertEquals(0f, Mazo.alfaPaso(120f), 0.001f)
        assertTrue(Mazo.alfaSiguiente(70f) in .55f..1f)
    }

    @Test fun fotos() {
        val e = r(Mazo.Estado(), Mazo.Accion.Foto(1, 3), Mazo.Accion.Foto(1, 3), Mazo.Accion.Foto(1, 3))
        assertEquals(2, e.foto)
        assertEquals(0, r(e, Mazo.Accion.Foto(-5, 3)).foto)
    }
}

class EncantasTest {
    @Test fun quedan() {
        assertEquals(1, Encantas.quedan(0, 1))
        assertEquals(0, Encantas.quedan(1, 1))
        assertEquals(3, Encantas.quedan(2, 5))
        assertEquals(0, Encantas.quedan(7, 5))
    }

    @Test fun aviso() {
        assertEquals("Ya usaste tu me encanta de hoy. Con Plus tenés 5 por día.", Encantas.aviso(false, 1))
        assertEquals("Ya usaste los 5 me encanta de hoy.", Encantas.aviso(true, 5))
    }
}

class FiltrosFinosTest {
    @Test fun sinFinosNoMandaNada() {
        val f = ar.com.caigo.app.datos.FiltrosFinos()
        assertTrue(!f.alguno)
        assertTrue(f.query().all { it.second == null })
    }

    @Test fun finos() {
        val f = ar.com.caigo.app.datos.FiltrosFinos(verificados = true, faltan = 2, edadDesde = 26, edadHasta = 35, gratis = true)
        assertTrue(f.alguno)
        assertEquals(
            listOf("verificados" to "1", "faltan" to 2, "edadDesde" to 26, "edadHasta" to 35, "gratis" to "1"),
            f.query().toList(),
        )
    }
}

class SenaTest {
    @Test fun pagarTodoEnUnPlanConSena() {
        // En un plan con seña, «Pagar todo» cobra la entrada completa: $8.000 + $600.
        val d = Plata.desglose(800000, "entrada", 30, 1)
        assertEquals(860000, d.total)
        assertEquals(0, d.saldo)
    }

    @Test fun senaRedondeadaACien() {
        // 25 % de $4.500 = $1.125 → $1.100; cargo 7,5 % de $4.500 = $337,50 → $300.
        val d = Plata.desglose(450000, "sena", 25, 2)
        assertEquals(220000, d.alLugar)
        assertEquals(60000, d.cargo)
        assertEquals(280000, d.total)
        assertEquals(680000, d.saldo)
    }
}

class VacioTest {
    @Test fun domingoALaSiestaSigueComoEnElHandoff() {
        assertEquals("Domingo a la siesta, la costa duerme. Si publicás algo ahora, sos lo primero que ven.", Vacio.frase(7, 15))
    }

    @Test fun cambiaSegunElDiaYLaHora() {
        assertTrue(Vacio.frase(5, 22).startsWith("Viernes a la noche"))
        assertTrue(Vacio.frase(2, 22).startsWith("Todavía nadie"))
        assertTrue(Vacio.frase(3, 9).startsWith("La mañana"))
        assertTrue(Vacio.frase(3, 3).startsWith("A esta hora"))
        // Ninguna nombra al domingo fuera del domingo.
        (1..6).forEach { d -> (0..23).forEach { h -> assertTrue(!Vacio.frase(d, h).contains("Domingo")) } }
    }
}
