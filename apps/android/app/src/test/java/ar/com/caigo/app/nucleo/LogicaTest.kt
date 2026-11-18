package ar.com.caigo.app.nucleo

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
        assertEquals("$1.234.567", Plata.pesos(123456700))
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
        assertEquals(550000, Plata.alLugar(550000, "entrada", 30))
    }
}

class Pdf417DniTest {
    @Test fun formatoNuevo() {
        val d = Pdf417Dni.leer("00654321987@GARCIA LOPEZ@MARIA  SOL@F@35123456@B@14/03/1994@20/05/2019@239")!!
        assertEquals("00654321987", d.tramite)
        assertEquals("GARCIA LOPEZ", d.apellido)
        assertEquals("MARIA SOL", d.nombres)
        assertEquals("35123456", d.numero)
        assertEquals("B", d.ejemplar)
        assertEquals("1994-03-14", d.nacimiento)
    }

    @Test fun formatoViejo() {
        val d = Pdf417Dni.leer("@17463537    A@1@PEREZ@JUAN CARLOS@ARGENTINA@08/07/1965@M@15/07/2009@00065327459@7174 @15/07/2024@317@0@ILR:2.01 C:110608.03 (No Cap.)@UNIDAD #20 || S/N: 0040>2008>>0006")!!
        assertEquals("17463537", d.numero)
        assertEquals("PEREZ", d.apellido)
        assertEquals("JUAN CARLOS", d.nombres)
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
    private fun r(e: FisicaMazo.Estado, vararg a: FisicaMazo.Accion) = a.fold(e) { x, y -> FisicaMazo.reducir(x, y) }

    @Test fun pasaElUmbralYVuela() {
        val e = r(FisicaMazo.Estado(), FisicaMazo.Accion.Empieza, FisicaMazo.Accion.Mueve(140f, 10f), FisicaMazo.Accion.Suelta)
        assertEquals(1, e.vuela)
        assertEquals(FisicaMazo.salida to -30f, FisicaMazo.desplazamiento(e))
        val sig = FisicaMazo.reducir(e, FisicaMazo.Accion.Siguiente)
        assertEquals(1, sig.indice)
        assertEquals(0, sig.vuela)
        assertEquals(0f, sig.dx)
    }

    @Test fun noLlegaYVuelve() {
        val e = r(FisicaMazo.Estado(), FisicaMazo.Accion.Empieza, FisicaMazo.Accion.Mueve(-90f, 0f), FisicaMazo.Accion.Suelta)
        assertEquals(0, e.vuela)
        assertEquals(0f, e.dx)
    }

    @Test fun izquierdaEsPaso() {
        val e = r(FisicaMazo.Estado(), FisicaMazo.Accion.Empieza, FisicaMazo.Accion.Mueve(-101f, 0f), FisicaMazo.Accion.Suelta)
        assertEquals(-1, e.vuela)
    }

    @Test fun fisicas() {
        assertEquals(10f, FisicaMazo.rotacion(160f), 0.001f)
        assertEquals(.93f, FisicaMazo.escalaSiguiente(0f), 0.001f)
        assertEquals(1f, FisicaMazo.escalaSiguiente(200f), 0.001f)
        assertEquals(1f, FisicaMazo.alfaMeGusta(120f), 0.001f)
        assertEquals(0f, FisicaMazo.alfaPaso(120f), 0.001f)
        assertTrue(FisicaMazo.alfaSiguiente(70f) in .55f..1f)
    }

    @Test fun fotos() {
        val e = r(FisicaMazo.Estado(), FisicaMazo.Accion.Foto(1, 3), FisicaMazo.Accion.Foto(1, 3), FisicaMazo.Accion.Foto(1, 3))
        assertEquals(2, e.foto)
        assertEquals(0, r(e, FisicaMazo.Accion.Foto(-5, 3)).foto)
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
        val f = ar.com.caigo.app.nucleo.FiltrosFinos()
        assertTrue(!f.activos)
        assertTrue(f.query().all { it.second == null })
    }

    @Test fun finos() {
        val f = ar.com.caigo.app.nucleo.FiltrosFinos(verificados = true, faltan = 2, edadDesde = 26, edadHasta = 35, gratis = true)
        assertTrue(f.activos)
        assertEquals(
            listOf("verificados" to "1", "chicas" to null, "faltan" to "2", "edadDesde" to "26", "edadHasta" to "35", "gratis" to "1"),
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
    @Test fun vacioSegunHora() {
        // Domingo 2 de noviembre de 2025, 15:00 en Argentina.
        val siesta = Fecha.parse("2025-11-02T15:00:00-03:00")!!
        assertTrue(VacioAhora.cuerpo(siesta).startsWith("Domingo a la siesta, la costa duerme."))
        val noche = Fecha.parse("2025-11-07T22:30:00-03:00")!!
        assertTrue(VacioAhora.cuerpo(noche).startsWith("Viernes a la noche"))
        assertEquals("No hay planes cerca. ¿Armás uno?", VacioAhora.titulo)
    }
}

class ConfianzaTest {
    private fun nb(x: String?) = x?.replace("\u00A0", " ")

    @Test fun linea() {
        val p = Plan(
            organizador = PersonaCorta(nombre = "Martín Pérez", asistencia = Asist(9, 10), anfitrion = true),
            confianza = Confianza(todosVerificados = true, soloChicas = true),
        )
        // En la tarjeta: lo que distingue al plan, sin «Todos verificados» ni cuántas chicas.
        assertEquals("Martín fue a 9 de 10 · Anfitrión · Plan de chicas", nb(lineaConfianza(p)))
        // En el detalle, sin quien organiza (ya tiene su fila).
        assertEquals("Todos verificados · Plan de chicas", nb(lineaConfianza(p, detalle = true)))
        // Sin asistencia, no hay nada que decir de quien organiza.
        assertNull(lineaConfianza(Plan(organizador = PersonaCorta(nombre = "Fede"))))
        assertNull(lineaConfianza(Plan()))
        val l = Plan(organizador = PersonaCorta(nombre = "Ana", asistencia = Asist(1, 2)), lugar = LugarCorto(nombre = "La Fonda"))
        assertEquals("Organiza\u00A0La\u00A0Fonda", lineaConfianza(l))
        assertEquals("fue a 3 de 5", asistenciaTexto(Asist(3, 5)))
        assertNull(asistenciaTexto(Asist()))
    }

    @Test fun diaDelPlan() {
        for (t in listOf("0223 15 592-6772", "223 15 5926772", "2235926772", "0223 592-6772", "+54 9 223 592-6772")) {
            assertEquals(t, "5492235926772", Dia.numeroWhatsapp(t))
        }
        assertEquals("5491144441290", Dia.numeroWhatsapp("011 15 4444-1290"))
        assertEquals("59899123456", Dia.numeroWhatsapp("+598 99 123 456"))
        assertEquals("Le escribimos a Mamá si no llegás", Contacto("Mamá", "mama@x.com", "contacto").filaTodoBien)
        assertEquals("Te avisamos para que le escribas a Sofi", Contacto("Sofi", "223 555 0101").filaTodoBien)
        val ahora = Fecha.parse("2026-11-18T20:00:00-03:00")!!
        assertEquals("Es tuyo hasta las 20:40", Dia.lugarGuardado("2026-11-18T20:40:00-03:00", ahora))
        assertNull(Dia.lugarGuardado("2026-11-18T19:40:00-03:00", ahora))
        val p = Plan(empiezaEn = "2026-11-18T17:00:00-03:00", duracionMin = 120)
        // Terminó a las 19: «Hoy» sigue para la vuelta, pero «¿Todo bien?» ya no se activa.
        assertTrue(Dia.enElDia(p, ahora))
        assertTrue(!Dia.puedeActivarTodoBien(p, ahora))
        assertEquals("Ayer 19:08", Fecha.cuando("2026-11-17T19:08:00-03:00", ahora))
        assertEquals("4,5 · 10+ opiniones", nb(Caras.texto(Caritas(4.5, 10))))
    }
}

class GestosTest {
    private fun plan(f: (Plan) -> Plan = { it }) = f(Plan(id = "x", ingreso = "libre", faltan = 2, organizador = PersonaCorta(id = "org")))

    @Test fun directoSoloSinNadaQueConfirmar() {
        assertTrue(Gestos.directo(plan()))
        assertTrue(!Gestos.directo(plan { it.copy(ingreso = "aprobacion") }))
        assertTrue(!Gestos.directo(plan { it.copy(cobro = "entrada") }))
        assertTrue(!Gestos.directo(plan { it.copy(genero = "mujeres") }))
        assertTrue(!Gestos.directo(plan { it.copy(miEstado = "cancelada") }))
        assertTrue(!Gestos.directo(plan { it.copy(faltan = 0) }))
    }

    @Test fun paraDecidirSoloLosQueSeSumanConUnToque() {
        val ps = listOf(
            plan(), plan { it.copy(faltan = 0) }, plan { it.copy(miEstado = "pendiente") }, plan { it.copy(organizador = PersonaCorta(id = "yo")) },
            plan { it.copy(ingreso = "aprobacion") }, plan { it.copy(genero = "mujeres") },
        )
        assertEquals(1, Gestos.paraDecidir(ps, "yo").size)
    }

    @Test fun textosDeLogros() {
        val l = Logros(nivel = "Habitué", planes = 7, siguiente = "De fierro", faltan = 8, racha = 3, comodin = true)
        assertEquals("Habitué · 7\u00A0con\u00A0check-in", TextoLogros.cabecera(l))
        assertEquals("Racha: 3\u00A0semanas", TextoLogros.racha(l))
        assertEquals("Tenés: Postre", TextoLogros.beneficio("Postre", l))
        assertEquals("Con 3 planes más: Postre", TextoLogros.beneficio("Postre", l.copy(planes = 2)))
        assertEquals("Te faltan 8 para De fierro", TextoLogros.siguiente(l))
        assertNull(TextoLogros.racha(l.copy(racha = 1)))
    }
}
