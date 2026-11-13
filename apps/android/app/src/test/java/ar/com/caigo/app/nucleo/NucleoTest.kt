package ar.com.caigo.app.nucleo

import ar.com.caigo.app.tema.I
import androidx.compose.ui.graphics.vector.PathParser
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Los casos de apps/ios/Nucleo/Tests/NucleoTests/NucleoTests.swift que no estaban en LogicaTest.

class ModelosTest {
    private fun ejemplo(nombre: String): String =
        javaClass.classLoader!!.getResource("Ejemplos/$nombre.json")!!.readText()

    /** Respuesta real de https://api.caigo.com.ar/v1/publico/planes/explorar (31/10/2026). */
    @Test fun planesDeProduccion() {
        val p = json.decodeFromString<Planes>(ejemplo("explorar")).planes
        assertEquals(4, p.size)
        val previa = p[0]
        assertEquals("Previa al atardecer en Playa Grande", previa.titulo)
        assertEquals("previa", previa.categoria)
        assertTrue(previa.somos == 4 && previa.total == 8 && previa.faltan == 4)
        assertEquals("libre", previa.ingreso)
        assertTrue(previa.soloVerificados)
        assertTrue(previa.organizador.organizador)
        assertEquals(1, previa.van.size)
        assertEquals("gratis", previa.cobro)
        assertNull(previa.precioCentavos)
        assertTrue(previa.aceptaGrupos)
        assertEquals(150, previa.duracionMin)
        assertNotNull(Fecha.parse(previa.empiezaEn))
        assertEquals("18:30", Fecha.hora(previa.empiezaEn))
    }

    @Test fun problema() {
        val p = json.decodeFromString<Problema>(ejemplo("problema"))
        assertEquals("sin_sesion", p.code)
        assertEquals("Tu sesión venció o no ingresaste.", ErrorApi(401, p).detalle)
        assertEquals("Algo salió mal. Probá de nuevo.", ErrorApi(500, Problema()).detalle)
    }

    @Test fun planConEntradaYCamposQueFaltan() {
        // Sin "total":"raro" del iPhone: kotlinx no cae al valor por defecto con un tipo equivocado.
        val texto = """
        {"id":"p1","titulo":"Taller de cerámica","cobro":"sena","precioCentavos":800000,"senaPct":30,"cargoCentavos":60000,
         "lugar":{"id":"l1","nombre":"Café Molienda","zona":"Güemes"},"distanciaM":900,"miEstado":"aceptada","miCantidad":2,
         "direccion":{"texto":"Güemes 2950","lat":-38.01,"lng":-57.54},"destacado":true,"campoNuevo":{"x":1},"total":null}
        """
        val p = json.decodeFromString<Plan>(texto)
        assertTrue(p.conEntrada && p.adentro && p.destacado)
        assertTrue(p.senaPct == 30 && p.cargoCentavos == 60000L)
        assertEquals("Café Molienda", p.lugar?.nombre)
        assertEquals("Güemes 2950", p.direccion?.texto)
        assertEquals(0, p.total)
        assertEquals("aprobacion", p.ingreso)
        assertEquals("900 m", distancia(p.distanciaM))
    }

    @Test fun planVacio() {
        val p = json.decodeFromString<Plan>("{}")
        assertEquals("", p.id)
        assertFalse(p.esMio)
    }

    @Test fun evento() {
        val texto = """{"tipo":"mensaje","mensaje":{"id":42,"planId":"p1","autor":{"id":"u1","nombre":"Lu"},"texto":"¡Llego en 10!","creadoEn":"2026-10-31T21:15:03.123456789-03:00"}}"""
        val e = TiempoReal.leer(texto)!!
        assertEquals(42L, e.mensaje?.id)
        assertEquals("Lu", e.mensaje?.autor?.nombre)
        assertEquals("21:15", Fecha.hora(e.mensaje!!.creadoEn))
    }

    @Test fun cuerposQueSeMandan() {
        val p = PlanNuevo(categoria = "juego", empiezaEn = "2026-10-31T21:30:00-03:00", ciudad = "Mar del Plata", lat = -38.0, lng = -57.5, somos = 3, faltan = 1, soloVerificados = true)
        val s = json.encodeToString(PlanNuevo.serializer(), p)
        assertTrue(s.contains("\"categoria\":\"juego\""))
        assertTrue(s.contains("\"soloVerificados\":true"))
        assertFalse(s.contains("costoCentavos"))
    }

    @Test fun cambiosPlanSoloLoQueCambia() {
        assertEquals("""{"total":6}""", json.encodeToString(CambiosPlan.serializer(), CambiosPlan(total = 6)))
    }

    @Test fun perfilNuevo() {
        val p = json.decodeFromString<Perfil>("""{"id":"u1","genero":"mujer","diversidad":true,"avisos":{"planesCerca":false},"proximos":[{"id":"p1","titulo":"Truco"}]}""")
        assertTrue(p.genero == "mujer")
        assertTrue(!p.avisos.planesCerca && p.avisos.tuGente)
        assertEquals("Truco", p.proximos.firstOrNull()?.titulo)
        assertTrue(p.altaIncompleta)
    }

    @Test fun eventoEscribiendoPrivado() {
        val e = TiempoReal.leer("""{"tipo":"escribiendo","privado":"u9"}""")!!
        assertTrue(e.privadoDe == "u9" && e.privado == null)
    }

    @Test fun version() {
        val v = json.decodeFromString<Version>("""{"version":"abc","minAndroid":7}""")
        assertTrue(v.pideActualizar(6))
        assertFalse(v.pideActualizar(7))
        assertFalse(Version().pideActualizar(1))
    }

    @Test fun ubicacionRedondeada() {
        val u = Ubicacion(-38.00234, -57.55751)
        assertEquals(-38.0, u.lat, 0.0)
        assertEquals(-57.56, u.lng, 0.0)
        assertEquals("""{"lat":-38.0,"lng":-57.56}""", json.encodeToString(Ubicacion.serializer(), u))
    }

    @Test fun genero() {
        assertTrue(Generos.puedeSumarse("mujeres", "mujer", false))
        assertFalse(Generos.puedeSumarse("mujeres", "varon", true))
        // «Solo diversidades» sólo con la confirmación del pedido: no se deduce del género.
        assertFalse(Generos.puedeSumarse("diversidades", "no_binario", false))
        assertTrue(Generos.puedeSumarse("diversidades", "varon", true))
        assertTrue(Generos.puedeSumarse("cualquiera", "", false))
    }
}

class OtrosTest {
    @Test fun fechas() {
        val ahora = Fecha.parse("2026-10-31T20:00:00-03:00")!!
        assertEquals("Hoy 21:30", Fecha.cuando("2026-10-31T21:30:00-03:00", ahora))
        assertEquals("Hoy 21:30", Fecha.cuando("2026-11-01T00:30:00Z", ahora))
        assertEquals("Mañana 10:00", Fecha.cuando("2026-11-01T10:00:00-03:00", ahora))
        assertEquals("Sáb 10:00", Fecha.cuando("2026-11-07T10:00:00-03:00", ahora))
        assertEquals("Ahora · en 2 h", Fecha.enCuanto("2026-10-31T22:00:00-03:00", ahora))
        assertEquals("Ahora · en 40 min", Fecha.enCuanto("2026-10-31T20:40:00-03:00", ahora))
        assertNull(Fecha.enCuanto("2026-11-01T02:00:00-03:00", ahora))
        assertEquals("Hoy, 20:30 a 21:30", Fecha.rango("2026-10-31T20:30:00-03:00", 60, ahora))
        assertEquals("hace 20 min", Fecha.hace("2026-10-31T19:40:00-03:00", ahora))
        assertEquals("ayer", Fecha.hace("2026-10-30T19:40:00-03:00", ahora))
        assertEquals("octubre", Fecha.mes(ahora))
    }

    @Test fun distancias() {
        assertEquals("80 m", distancia(80))
        assertEquals("900 m", distancia(860))
        assertEquals("1,2 km", distancia(1240))
        assertEquals("12 km", distancia(12400))
        assertEquals("", distancia(null))
    }

    @Test fun categorias() {
        assertEquals("Música en vivo", Categorias.nombre("musica"))
        assertEquals("Nueva", Categorias.nombre("nueva"))
        assertEquals(8, Categorias.paraCrear.size)
    }

    @Test fun clerk() {
        // pk_test_ + base64("clerk.ejemplo.com$")
        val k = "pk_test_" + java.util.Base64.getEncoder().encodeToString("clerk.ejemplo.com$".toByteArray()).replace("=", "")
        assertEquals("https://clerk.ejemplo.com", Clerk.fapiDe(k))
        assertNull(Clerk.fapiDe("hola"))
        assertEquals(
            "redirect_url=ar.com.caigo.app%3A%2F%2Fcallback&strategy=oauth_apple",
            Clerk.formulario(mapOf("strategy" to "oauth_apple", "redirect_url" to "ar.com.caigo.app://callback")),
        )
        assertEquals("abc", Clerk.nonce("ar.com.caigo.app://callback?rotating_token_nonce=abc"))
        assertEquals("Se canceló el ingreso.", mensajeDe(Clerk.ErrorClerk("cancelado")))
    }

    @Test fun tiempoReal() {
        assertEquals("wss://api.caigo.com.ar/v1/tiempo-real?token=cs_1", TiempoReal.url("https://api.caigo.com.ar", "cs_1"))
        assertTrue(TiempoReal.espera(0) == 1000L && TiempoReal.espera(3) == 8000L && TiempoReal.espera(9) == 30_000L)
    }

    @Test fun api() {
        val a = Api("https://api.caigo.com.ar", OkHttpClient(), { null })
        assertEquals(
            "https://api.caigo.com.ar/v1/planes/ahora?lat=-38.1",
            a.url("/v1/planes/ahora", "lat" to "-38.1", "categoria" to null, "x" to "").toString(),
        )
        assertEquals(listOf("1", "2"), FiltrosFinos(verificados = true, faltan = 2).query().mapNotNull { it.second })
        assertEquals("-38.002300", Api.num(-38.0023))
    }

    @Test fun busqueda() {
        assertNull(Api.busqueda(" t "))
        assertEquals("tr", Api.busqueda(" tr "))
        assertNull(Api.busqueda(null))
    }

    @Test fun iconos() {
        // Todos los paths del handoff se leen (el iPhone además mide la caja con TrazoSVG).
        I.todos.forEach { d -> assertTrue(d, PathParser().parsePathString(d).toNodes().isNotEmpty()) }
    }
}
