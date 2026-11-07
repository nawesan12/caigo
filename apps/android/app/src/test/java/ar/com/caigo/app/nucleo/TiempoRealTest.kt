package ar.com.caigo.app.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TiempoRealTest {
    @Test fun escribiendoEnUnPrivadoTraeElIdComoTexto() {
        val e = TiempoReal.leer("""{"tipo":"escribiendo","privado":"u-123"}""")!!
        assertEquals("escribiendo", e.tipo)
        assertEquals("u-123", e.escribePrivado)
        assertNull(e.privado)
    }

    @Test fun unPrivadoNuevoSigueSiendoMensaje() {
        val e = TiempoReal.leer("""{"tipo":"privado","privado":{"id":7,"deId":"a","aId":"b","texto":"hola"}}""")!!
        assertEquals(7L, e.privado?.id)
        assertEquals("", e.escribePrivado)
    }

    @Test fun busquedaDesdeDosLetras() {
        assertNull(Api.busqueda(" a "))
        assertEquals("ab", Api.busqueda(" ab "))
        assertEquals(-38.0, Api.redondear(-38.0023), 0.0)
    }
}
