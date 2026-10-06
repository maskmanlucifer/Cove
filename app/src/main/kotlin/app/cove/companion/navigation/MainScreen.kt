package app.cove.companion.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.ReducedMotionMillis
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import app.cove.companion.design.Cove
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cove.companion.container
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.design.components.CoveDock
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.Tab
import app.cove.companion.feature.journal.JournalScreen
import app.cove.companion.feature.me.MeScreen
import app.cove.companion.feature.money.MoneyScreen
import app.cove.companion.feature.plan.PlanScreen
import app.cove.companion.feature.today.TodayScreen
import app.cove.companion.feature.voice.UndoToastHost

private val DockFadeHeight = 112.dp

/**
 * Shows only the current tab (the others are not composed), keeps each tab's saved state (scroll, selections) in a
 * [androidx.compose.runtime.saveable.SaveableStateHolder] so coming back does not reset it, and fades the new one in.
 */
@Composable
private fun TabHost(tab: Tab, nav: Nav) {
    val holder = rememberSaveableStateHolder()
    val reduce = LocalReduceMotion.current
    val first = remember { booleanArrayOf(true) }
    key(tab) {
        val alpha = remember { Animatable(if (first[0]) 1f else 0f).also { first[0] = false } }
        LaunchedEffect(Unit) {
            alpha.animateTo(1f, tween(if (reduce) ReducedMotionMillis else NavMotion.TAB_MS, easing = NavMotion.Ease))
        }
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) {
        holder.SaveableStateProvider(tab.name) {
            when (tab) {
                Tab.Today -> TodayScreen(nav)
                Tab.Plan -> PlanScreen(nav)
                Tab.Money -> MoneyScreen(nav)
                Tab.Journal -> JournalScreen(nav)
                Tab.Me -> MeScreen(nav)
            }
        }
        }
    }
}

/** The five dock destinations. Each tab screen draws its own content and leaves room for the dock. */
@Composable
fun MainScreen(nav: Nav) {
    val container = LocalContext.current.container
    var tab by rememberSaveable { mutableStateOf(Tab.entries.firstOrNull { it.name.equals(DebugLaunch.tab, true) } ?: Tab.Today) }
    val oneThing by remember(container) { container.settings.settings.map { it.oneThingMode } }.collectAsState(false)
    CoveScreen {
        TabHost(tab, nav)
        // Keeps scrolled content from running under the clock; invisible at rest because it matches the canvas.
        val topInset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(maxOf(topInset, 48.dp))
                .background(Brush.verticalGradient(listOf(Cove.colors.canvas, Cove.colors.canvas, Color.Transparent))),
        )
        if (!(oneThing && tab == Tab.Today)) {
            // Soft fade so scrolled content never collides with the floating bar; matches the canvas, so invisible at rest.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(DockFadeHeight)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Cove.colors.canvas.copy(alpha = 0.92f), Cove.colors.canvas))),
            )
        }
        if (!(oneThing && tab == Tab.Today)) CoveDock(tab, onSelect = { tab = it }, onVoice = { nav.go(Routes.Voice) })
        UndoToastHost(Modifier.align(Alignment.TopCenter))
    }
}
