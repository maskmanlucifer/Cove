package app.cove.companion.navigation

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.Alignment
import app.cove.companion.design.components.SwipePages
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

/** The swipeable pages: Me, Today, Plan, Money, Journal. The app opens on Today, so Me is one swipe to the right. */
private val Pages = SwipePages

/** Swipe sideways between the pages; the dock's dots follow the page. Each page keeps its scroll position while it is off screen. */
@Composable
private fun PageHost(state: PagerState, nav: Nav, openMe: () -> Unit) {
    HorizontalPager(state, Modifier.fillMaxSize(), key = { Pages[it].name }) { index ->
        Box(Modifier.fillMaxSize()) {
            when (Pages[index]) {
                Tab.Me -> MeScreen(nav)
                Tab.Today -> TodayScreen(nav, onOpenMe = openMe)
                Tab.Plan -> PlanScreen(nav)
                Tab.Money -> MoneyScreen(nav)
                Tab.Journal -> JournalScreen(nav)
            }
        }
    }
}

/** The dock destinations. Each screen draws its own content and leaves room for the dock. */
@Composable
fun MainScreen(nav: Nav) {
    val container = LocalContext.current.container
    val start = Tab.entries.firstOrNull { it.name.equals(DebugLaunch.tab, true) } ?: Tab.Today
    val pager = rememberPagerState(initialPage = Pages.indexOf(start)) { Pages.size }
    val scope = rememberCoroutineScope()
    val tab = Pages[pager.currentPage]
    // Me sits left of Today, so Back there returns to Today rather than leaving the app.
    BackHandler(enabled = tab == Tab.Me) { scope.launch { pager.animateScrollToPage(Pages.indexOf(Tab.Today)) } }
    val oneThing by remember(container) { container.settings.settings.map { it.oneThingMode } }.collectAsState(false)
    val select: (Tab) -> Unit = { target -> scope.launch { pager.animateScrollToPage(Pages.indexOf(target)) } }
    CoveScreen {
        PageHost(pager, nav, openMe = { select(Tab.Me) })
        // Keeps scrolled content from running under the clock; invisible at rest because it matches the canvas.
        val topInset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(maxOf(topInset, 48.dp))
                .background(Brush.verticalGradient(listOf(Cove.colors.canvas, Cove.colors.canvas, Color.Transparent))),
        )
        if (!(oneThing && tab == Tab.Today)) {
            // Soft fade so scrolled content never collides with the floating controls; matches the canvas, so invisible at rest.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(DockFadeHeight)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Cove.colors.canvas.copy(alpha = 0.92f), Cove.colors.canvas))),
            )
        }
        if (!(oneThing && tab == Tab.Today)) CoveDock(tab, onSelect = select, onVoice = { nav.go(Routes.Voice) }, onPreview = select)
        UndoToastHost(Modifier.align(Alignment.TopCenter))
    }
}
