package app.cove.companion.navigation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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

/** The five dock destinations. Each tab screen draws its own content and leaves room for the dock. */
@Composable
fun MainScreen(nav: Nav) {
    val container = LocalContext.current.container
    var tab by rememberSaveable { mutableStateOf(Tab.entries.firstOrNull { it.name.equals(DebugLaunch.tab, true) } ?: Tab.Today) }
    val oneThing by remember(container) { container.settings.settings.map { it.oneThingMode } }.collectAsState(false)
    CoveScreen {
        Crossfade(tab, animationSpec = tween(150), label = "tab") { current ->
            when (current) {
                Tab.Today -> TodayScreen(nav)
                Tab.Plan -> PlanScreen(nav)
                Tab.Money -> MoneyScreen(nav)
                Tab.Journal -> JournalScreen(nav)
                Tab.Me -> MeScreen(nav)
            }
        }
        // Keeps scrolled content from running under the clock; invisible at rest because it matches the canvas.
        val topInset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(maxOf(topInset, 48.dp))
                .background(Brush.verticalGradient(listOf(Cove.colors.canvas, Cove.colors.canvas, Color.Transparent))),
        )
        if (!(oneThing && tab == Tab.Today)) Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            CoveDock(tab, onSelect = { tab = it }, onVoice = { nav.go(Routes.Voice) })
        }
        UndoToastHost(Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }
}
