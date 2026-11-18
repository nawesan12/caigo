package ar.com.caigo.app.pantallas.planes

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ar.com.caigo.app.app.Ir
import ar.com.caigo.app.app.LocalApp
import ar.com.caigo.app.app.PlanFestejado
import ar.com.caigo.app.nucleo.Cupo
import ar.com.caigo.app.nucleo.Fecha
import ar.com.caigo.app.nucleo.Plan
import ar.com.caigo.app.pantallas.mios.miosNombres
import ar.com.caigo.app.pantallas.mios.miosPrimer
import ar.com.caigo.app.piezas.Avatar
import ar.com.caigo.app.piezas.BotonIcono
import ar.com.caigo.app.piezas.Cta
import ar.com.caigo.app.piezas.Sistema
import ar.com.caigo.app.tema.C
import ar.com.caigo.app.tema.Gels
import ar.com.caigo.app.tema.I
import ar.com.caigo.app.tema.T
import ar.com.caigo.app.tema.gel
import ar.com.caigo.app.tema.px
import kotlinx.coroutines.delay

/**
 * «¡Estás adentro!»: el festejo corto al entrar a un plan (sumarse quedó `aceptada`, desde el detalle, la
 * tarjeta, el gesto o «Decidí rápido»). Gel lima a pantalla completa, las caras de quienes van con la tuya
 * entrando al hueco «+ Vos», «Somos N de M» y tres salidas: el chat, la agenda y compartir. Se cierra con la X o
 * tocando afuera de los botones. Las caras entran en menos de 0,6 s; con «reducir movimiento», sin animación.
 */
@Composable
fun Festejo(ir: Ir, f: PlanFestejado, cerrar: () -> Unit) {
    val c = LocalApp.current
    val ctx = LocalContext.current
    val yo by c.yo.collectAsState()
    val sinMovimiento = reducirMovimiento()
    // 0: nada; 1: entran las caras; 2: entra la tuya al hueco.
    var fase by remember { mutableIntStateOf(if (sinMovimiento) 2 else 0) }
    LaunchedEffect(Unit) {
        if (sinMovimiento) return@LaunchedEffect
        fase = 1
        delay(220)
        fase = 2
    }
    BackHandler(onBack = cerrar)
    val plan = f.plan
    val somos = minOf(plan.total, plan.somos + f.cantidad)

    // Una capa encima de todo: TalkBack la anuncia como un panel nuevo (y la navegación de abajo queda sin
    // semántica mientras está, en Navegacion.kt).
    Box(
        Modifier.fillMaxSize()
            .semantics { paneTitle = "¡Estás adentro!" }
            .gel(0.dp, Gels.lima.fondo, grano = true)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = cerrar),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f).padding(top = 20.dp))
            Column(
                Modifier.padding(horizontal = 28.dp).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Caras(plan, yo?.nombre, yo?.fotos?.firstOrNull(), c.sesion.usuarioId, fase, sinMovimiento)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("¡Estás adentro!", 36f, 800, C.carbon, alto = 1f, espaciado = px(-1.2f, 36f), alinear = TextAlign.Center)
                    T(plan.titulo, 16f, 600, C.carbon, alto = 1.3f, alinear = TextAlign.Center, maxRenglones = 2)
                }
                T(Cupo.somos(somos, plan.total), 17f, 800, C.limaTexto, unRenglon = true)
            }
            Spacer(Modifier.weight(1f).padding(bottom = 20.dp))
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Cta(
                    "Saludar en el chat", { cerrar(); ir.chat(plan.id) }, Modifier.fillMaxWidth(), gel = Gels.violeta, alto = 58.dp, tam = 17f,
                    color = C.blanco, icono = I.enviar, iconoTrazo = 2.4f, iconoTam = 18.dp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Cta(
                        "Agendar", { agendar(ctx, plan, planesLink(plan)) }, Modifier.weight(1f), gel = Gels.blanco, alto = 52.dp, tam = 14f,
                        color = C.carbon, icono = I.calendarioSimple, iconoTrazo = 2.4f, iconoTam = 16.dp,
                    )
                    Cta(
                        "Avisarle a alguien",
                        { Sistema.compartir(ctx, "${plan.titulo} · ${Cupo.badge(maxOf(0, plan.faltan - f.cantidad))}. Sumate en Caigo: ${planesLink(plan)}") },
                        Modifier.weight(1f), gel = Gels.blanco, alto = 52.dp, tam = 14f, color = C.carbon,
                    )
                }
            }
        }
        BotonIcono(
            I.cruz, cerrar,
            Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(top = 8.dp, end = 16.dp).semantics { contentDescription = "Cerrar" },
            iconoTam = 18.dp, trazo = 2.8f,
        )
    }
}

/** Hasta 5 caras de los que van (Avatar, como en la tarjeta) y el hueco «+ Vos» que se llena con la tuya. */
@Composable
private fun Caras(plan: Plan, miNombre: String?, miFoto: String?, yo: String, fase: Int, sinMovimiento: Boolean) {
    val van = plan.van.filter { it.id != yo }.take(5)
    val alfa by animateFloatAsState(if (fase >= 1) 1f else 0f, if (sinMovimiento) snap() else tween(250), label = "caras")
    val mia by animateFloatAsState(if (fase >= 2) 1f else 0f, if (sinMovimiento) snap() else spring(dampingRatio = .72f, stiffness = 380f), label = "mia")
    val bajada = with(LocalDensity.current) { 14.dp.toPx() }
    val etiqueta = if (van.isEmpty()) "Vas vos" else "Van ${miosNombres(van.map { miosPrimer(it.nombre) })} y vos"
    Row(
        Modifier.clearAndSetSemantics { contentDescription = etiqueta },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-12).dp),
    ) {
        van.forEachIndexed { i, p ->
            Avatar(
                p.nombre, p.foto, 56.dp,
                Modifier.zIndex((van.size - i).toFloat()).graphicsLayer { alpha = alfa; translationY = (1f - alfa) * bajada },
                borde = 3.dp, colorBorde = C.lima1,
            )
        }
        Box(Modifier.padding(start = if (van.isEmpty()) 0.dp else 20.dp).size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(64.dp)) {
                val ancho = 2.5.dp.toPx()
                drawCircle(
                    C.carbon.copy(alpha = .35f), radius = size.minDimension / 2 - ancho / 2,
                    style = Stroke(ancho, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
            T("+ Vos", 13f, 800, C.carbon, Modifier.graphicsLayer { alpha = 1f - mia }, unRenglon = true)
            Avatar(
                miNombre ?: "Vos", miFoto, 64.dp,
                Modifier.graphicsLayer { alpha = mia.coerceIn(0f, 1f); scaleX = .3f + .7f * mia; scaleY = .3f + .7f * mia },
                borde = 3.dp, colorBorde = C.carbon,
            )
        }
    }
}

/** «Agendar»: la pantalla del calendario para agregar el plan (Intent ACTION_INSERT de CalendarContract). */
private fun agendar(ctx: Context, p: Plan, link: String) {
    val inicio = Fecha.parse(p.empiezaEn)?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
    val fin = inicio + maxOf(p.duracionMin, 30) * 60_000L
    val lugar = p.lugar?.nombre ?: p.lugarNombre
    val dir = p.direccion?.texto.orEmpty()
    val donde = listOf(lugar, dir.ifEmpty { p.zona.ifEmpty { p.ciudad } }).filter { it.isNotEmpty() }.joinToString(", ")
    val i = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, p.titulo)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, inicio)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, fin)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, donde)
        .putExtra(CalendarContract.Events.DESCRIPTION, link)
    runCatching { ctx.startActivity(i) }
}
