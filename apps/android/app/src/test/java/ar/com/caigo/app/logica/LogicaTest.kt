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
        // La seña se redondea al peso.
        assertEquals(333300, Plata.alLugar(1111000, "sena", 30))
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
