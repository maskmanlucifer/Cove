package app.cove.companion.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var tab by rememberSaveable { mutableStateOf(Tab.entries.firstOrNull { it.name.equals(DebugLaunch.tab, true) } ?: Tab.Today) }
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
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            CoveDock(tab, onSelect = { tab = it }, onVoice = { nav.go(Routes.Voice) })
        }
        UndoToastHost(Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
    }
}
