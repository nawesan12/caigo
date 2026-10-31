package ar.com.caigo.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ar.com.caigo.app.R
import ar.com.caigo.app.ui.pantallas.mios.MisPlanes
import ar.com.caigo.app.ui.pantallas.mios.Perfil
import ar.com.caigo.app.ui.pantallas.planes.Ahora
import ar.com.caigo.app.ui.pantallas.planes.Explorar
import ar.com.caigo.app.ui.piezas.fondoCaigo
import ar.com.caigo.app.ui.piezas.tocable
import ar.com.caigo.app.ui.piezas.toque
import ar.com.caigo.app.ui.tema.Gels
import ar.com.caigo.app.ui.tema.I
import ar.com.caigo.app.ui.tema.Icono
import ar.com.caigo.app.ui.tema.LocalPaleta
import ar.com.caigo.app.ui.tema.T
import ar.com.caigo.app.ui.tema.externa
import ar.com.caigo.app.ui.tema.gel
import ar.com.caigo.app.ui.tema.rgba

/** Cuánto lugar deja abajo el contenido de una pestaña para no quedar tapado por la tab bar. */
@Composable
fun altoTabBar(): Dp = 24.dp + 70.dp + 14.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Mientras una hoja está abierta adentro de una pestaña, la tab bar se esconde (si no, la tapa). */
val LocalTabBarOculta = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<Boolean>?> { null }

/** Esconde la tab bar mientras [oculta] sea true. */
@Composable
fun OcultarTabBar(oculta: Boolean) {
    val o = LocalTabBarOculta.current
    androidx.compose.runtime.DisposableEffect(oculta) {
        o?.value = oculta
        onDispose { o?.value = false }
    }
}

/** Las cuatro pestañas con la tab bar flotante encima. */
@Composable
fun Principal(ir: Ir, inicial: Pestana, modo: String?) {
    var tab by rememberSaveable { mutableStateOf(inicial) }
    val p = LocalPaleta.current
    val c = ar.com.caigo.app.LocalApp.current
    // Avisos (Android 13+): se piden una vez, al llegar a la app después del alta (como iOS).
    val permisoAvisos = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { ok -> if (ok) c.alcance.launch { ar.com.caigo.app.push.Push.registrar(c) } }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!c.sesion.avisosPedidos) {
            c.sesion.marcarAvisosPedidos()
            if (ar.com.caigo.app.push.Push.necesitaPermiso(c.ctx)) permisoAvisos.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val oculta = androidx.compose.runtime.remember { mutableStateOf(false) }
    androidx.compose.runtime.CompositionLocalProvider(LocalTabBarOculta provides oculta) {
        Box(Modifier.fillMaxSize().fondoCaigo(p.oscuro)) {
            when (tab) {
                Pestana.Ahora -> Ahora(ir, modo)
                Pestana.Explorar -> Explorar(ir)
                Pestana.Mis -> MisPlanes(ir)
                Pestana.Perfil -> Perfil(ir)
            }
            androidx.compose.animation.AnimatedVisibility(
                !oculta.value, Modifier.align(Alignment.BottomCenter),
                enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut(),
            ) { TabBar(tab, { tab = it; oculta.value = false }, { ir.crear() }) }
        }
    }
}

/**
 * Tab bar flotante (README · Navegación): 18 px a los costados, 24 abajo, 70 de alto, radio 35.
 * En el medio, el isotipo de Caigo de 68 px que sobresale (Crear).
 */
@Composable
fun TabBar(activa: Pestana, onTab: (Pestana) -> Unit, onCrear: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPaleta.current
    val abajo = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 24.dp + abajo)
            .height(70.dp)
            .gel(35.dp, if (p.oscuro) Gels.tabBarOscura else Gels.tabBar),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            val items = listOf(
                Triple("Ahora", I.reloj, Pestana.Ahora),
                Triple("Explorar", I.lupa, Pestana.Explorar),
                null,
                Triple("Mis planes", I.calendario, Pestana.Mis),
                Triple("Perfil", I.usuario, Pestana.Perfil),
            )
            items.forEach { it ->
                if (it == null) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        Box(
                            Modifier.offset(y = -22.dp).size(68.dp).toque(onClick = onCrear),
                            contentAlignment = Alignment.Center,
                        ) {
                            // drop-shadow(0 10px 12px rgba(116,86,241,.45)) del isotipo
                            Box(Modifier.size(54.dp).gel(17.dp, null, listOf(externa(10f, 12f, rgba(116, 86, 241, .45f)))))
                            Image(painterResource(R.drawable.caigo_icon), "Crear", Modifier.size(68.dp))
                        }
                    }
                } else {
                    val (l, d, t) = it
                    val on = t == activa
                    val color = if (on) p.tabActivo else p.tabInactivo
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .padding(horizontal = 3.dp, vertical = 8.dp)
                            .then(if (on) Modifier.gel(28.dp, if (p.oscuro) Gels.tabPillOscura else Gels.tabPill) else Modifier)
                            .tocable { onTab(t) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        Icono(d, 24.dp, color, 2.2f)
                        T(l, 10.5f, if (on) 800 else 500, color, unRenglon = true)
                    }
                }
            }
        }
    }
}
