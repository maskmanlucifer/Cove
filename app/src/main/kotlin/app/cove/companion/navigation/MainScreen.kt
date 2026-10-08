package app.cove.companion.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.ReducedMotionMillis
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
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
import app.cove.companion.design.components.SwipePages
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
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
 * The four swipeable pages. Me is not one of them: it opens over the pages from the dock or the profile bubble on Today,
 * so the dots in the dock always count these four.
 */
private val Pages = SwipePages

/**
 * Swipe sideways between Today, Plan, Money and Journal; the dock's pill and dots follow the page. Each page keeps its
 * scroll position while it is off screen, and Me fades in over the pages without disturbing them.
 */
@Composable
private fun PageHost(state: PagerState, onMe: Boolean, nav: Nav, openMe: () -> Unit) {
    val reduce = LocalReduceMotion.current
    HorizontalPager(state, Modifier.fillMaxSize().then(if (onMe) Modifier.clearAndSetSemantics { } else Modifier), key = { Pages[it].name }) { index ->
        Box(Modifier.fillMaxSize()) {
            when (Pages[index]) {
                Tab.Today -> TodayScreen(nav, onOpenMe = openMe)
                Tab.Plan -> PlanScreen(nav)
                Tab.Money -> MoneyScreen(nav)
                Tab.Journal -> JournalScreen(nav)
                Tab.Me -> Unit
            }
        }
    }
    AnimatedVisibility(
        onMe,
        enter = fadeIn(tween(if (reduce) ReducedMotionMillis else NavMotion.TAB_MS, easing = NavMotion.Ease)),
        exit = fadeOut(tween(if (reduce) ReducedMotionMillis else NavMotion.TAB_MS, easing = NavMotion.Ease)),
    ) {
        // Opaque and touch-absorbing, so nothing underneath can be tapped while Me is open.
        Box(Modifier.fillMaxSize().background(Cove.colors.canvas).pointerInput(Unit) {}) { MeScreen(nav) }
    }
}

/** The dock destinations. Each screen draws its own content and leaves room for the dock. */
@Composable
fun MainScreen(nav: Nav) {
    val container = LocalContext.current.container
    val start = Tab.entries.firstOrNull { it.name.equals(DebugLaunch.tab, true) } ?: Tab.Today
    val pager = rememberPagerState(initialPage = Pages.indexOf(start).coerceAtLeast(0)) { Pages.size }
    var onMe by rememberSaveable { mutableStateOf(start == Tab.Me) }
    val scope = rememberCoroutineScope()
    val tab = if (onMe) Tab.Me else Pages[pager.currentPage]
    BackHandler(enabled = onMe) { onMe = false }
    val oneThing by remember(container) { container.settings.settings.map { it.oneThingMode } }.collectAsState(false)
    val select: (Tab) -> Unit = { target ->
        if (target == Tab.Me) onMe = true
        else {
            onMe = false
            scope.launch { pager.animateScrollToPage(Pages.indexOf(target)) }
        }
    }
    CoveScreen {
        PageHost(pager, onMe, nav, openMe = { onMe = true })
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
        if (!(oneThing && tab == Tab.Today)) CoveDock(tab, onSelect = select, onVoice = { nav.go(Routes.Voice) })
        UndoToastHost(Modifier.align(Alignment.TopCenter))
    }
}
