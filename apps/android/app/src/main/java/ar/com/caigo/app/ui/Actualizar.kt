package ar.com.caigo.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.R
import ar.com.caigo.app.ui.piezas.Cta
import ar.com.caigo.app.ui.piezas.Pantalla
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.px
import ar.com.caigo.app.ui.tema.rgba

/**
 * «Actualizá la app» (la API pide una versión más nueva en `/v1/version`). No está diseñada: se arma
 * con el vacío 13a (isotipo inclinado, título de 30 px, bajada y CTA lima). Tapa todo y no se cierra.
 */
@Composable
fun Actualizar() {
    val ctx = LocalContext.current
    val p = LocalPaleta.current
    Pantalla(Modifier.zIndex(50f).tocable { }) { _, abajo ->
        Column(
            Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = abajo),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(130.dp).rotate(-10f).gel(36.dp, null, listOf(externa(22f, 24f, rgba(116, 86, 241, .35f)))))
                Image(painterResource(R.drawable.caigo_icon), null, Modifier.size(170.dp).rotate(-10f))
            }
            T("Hay una versión nueva de Caigo", 30f, 700, p.texto, alto = 1.08f, espaciado = px(-.6f, 30f), alinear = TextAlign.Center)
            T("Actualizala para seguir sumándote a planes. Es un minuto.", 15f, 400, p.texto2, alto = 1.5f, alinear = TextAlign.Center)
            Cta("Actualizar", {
                val id = ctx.packageName
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
            }, Modifier.fillMaxWidth().padding(top = 6.dp), alto = 58.dp, tam = 16f)
        }
    }
}
