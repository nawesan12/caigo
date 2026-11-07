package ar.com.caigo.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Cada pantalla de iOS (Caigo/Pantallas/<Carpeta>/<Nombre>.swift) tiene su clon en Android
 *  (pantallas/<carpeta>/<Nombre>.kt). La lista (paridad.txt) la escribe herramientas/paridad.sh; si falta una, el CI
 *  queda en rojo. */
class ParidadTest {
    @Test fun cadaPantallaDeIosTieneSuKt() {
        val lista = javaClass.getResource("/paridad.txt")!!.readText().lines().map { it.trim() }.filter { it.endsWith(".swift") }
        assertTrue("paridad.txt vacío", lista.isNotEmpty())
        val base = File("src/main/java/ar/com/caigo/app/pantallas")
        val faltan = lista.filterNot { s ->
            val carpeta = s.substringBeforeLast('/').lowercase()
            File(base, "$carpeta/${s.substringAfterLast('/').removeSuffix(".swift")}.kt").isFile
        }
        assertTrue("Faltan en Android: $faltan", faltan.isEmpty())
    }
}
