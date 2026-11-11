package ar.com.caigo.app

import android.net.Uri
import ar.com.caigo.app.app.Contenedor
import ar.com.caigo.app.app.Destino
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Cada link de `apps/pruebas/enlaces.json` que abre la app lleva a su pantalla (clon de EnlacesTests.swift), y el
 *  manifest declara exactamente los patrones del JSON (lo mismo que sirve Caddy en el assetlinks). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnlacesTest {
    private val lista = Json.parseToJsonElement(javaClass.getResource("/pruebas/enlaces.json")!!.readText()).jsonObject
    private val dominio = lista["dominio"]!!.jsonPrimitive.content

    private fun clave(d: Destino): String = when (d) {
        is Destino.Plan -> "plan:${d.id}"
        is Destino.PlanSlug -> "planSlug:${d.slug}"
        is Destino.Tap -> "tap:${d.placa}"
        is Destino.Pantalla -> "pantalla:${d.pantalla}"
        is Destino.VueltaClerk -> "vueltaClerk"
        is Destino.Seccion -> "seccion:" + d.pestana.name.lowercase()
        is Destino.Ruta -> "ruta:${d.pantalla}" + (d.id?.let { ":$it" } ?: "")
        is Destino.Web -> "web-en-app"
    }

    @Test fun cadaLinkAbreSuPantalla() {
        for ((camino, destino) in lista["ejemplos"]!!.jsonObject) {
            val d = destino.jsonPrimitive.content
            if (d == "web") continue
            assertEquals(camino, d, Contenedor.destino(Uri.parse("https://$dominio$camino"))?.let(::clave))
        }
    }

    @Test fun otroDominioNoEsDeLaApp() {
        assertNull(Contenedor.destino(Uri.parse("https://otro.com.ar/p/x")))
    }

    @Test fun elManifestDeclaraLoMismoQueElJson() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val android = "http://schemas.android.com/apk/res/android"
        val filtros = doc.getElementsByTagName("intent-filter")
        val filtro = (0 until filtros.length).map { filtros.item(it) as Element }.single { it.getAttributeNS(android, "autoVerify") == "true" }
        fun patron(d: Element): String? = d.getAttributeNS(android, "path").ifEmpty { null }
            ?: d.getAttributeNS(android, "pathPrefix").ifEmpty { null }?.let { "$it*" }
            ?: d.getAttributeNS(android, "pathPattern").ifEmpty { null }?.replace(".*", "*")
        val datos = filtro.getElementsByTagName("data")
        val app = mutableSetOf<String>(); val web = mutableSetOf<String>()
        for (i in 0 until datos.length) {
            val d = datos.item(i) as Element
            val p = patron(d) ?: continue
            if ((d.parentNode as Element).tagName == "uri-relative-filter-group") web += p else app += p
        }
        assertEquals(lista["app"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(), app)
        assertEquals(lista["web"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(), web)
    }
}
