package app.cove.companion.design.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.ReducedMotionMillis

/** Top-level destinations reachable from the bottom bar. */
enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Today("Today", CoveIcons.Today, CoveIcons.TodayBold),
    Plan("Plan", CoveIcons.Plan, CoveIcons.PlanBold),
    Money("Money", CoveIcons.Money, CoveIcons.MoneyBold),
    Journal("Journal", CoveIcons.Journal, CoveIcons.JournalBold),
    Me("Me", CoveIcons.Me, CoveIcons.MeBold),
}

/** Pure pieces of the bottom bar, kept apart from the composable so they can be unit tested. */
object DockSwitcher {
    /** How long the expanded icon row stays up before it settles away on its own. */
    const val AUTO_COLLAPSE_MS = 4000L

    /** Spoken description of a destination in the expanded row, e.g. "Plan, tab 2 of 5". */
    fun tabDescription(tab: Tab): String = "${tab.label}, tab ${tab.ordinal + 1} of ${Tab.entries.size}"

    /** Open the switcher unless it is already open; the same tap closes it. */
    fun toggle(expanded: Boolean): Boolean = !expanded
}

/**
 * Compact bottom bar: a pill showing the current tab and the voice orb. Tapping the pill raises a row of the five
 * destinations (the design's expanded state); choosing one, tapping elsewhere, Back, or four seconds of nothing
 * settles it away again. Place it at the bottom of a full-size box (see [DockBarHeight]).
 */
@Composable
fun CoveDock(selected: Tab, onSelect: (Tab) -> Unit, onVoice: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val timeout = LocalAccessibilityManager.current
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        val ms = timeout?.calculateRecommendedTimeoutMillis(DockSwitcher.AUTO_COLLAPSE_MS, containsIcons = true, containsText = false, containsControls = true)
            ?: DockSwitcher.AUTO_COLLAPSE_MS
        kotlinx.coroutines.delay(ms)
        expanded = false
    }
    BackHandler(enabled = expanded) { expanded = false }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(DOCK_MAX_FONT_SCALE))) {
        Box(modifier.fillMaxSize()) {
            if (expanded) {
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { expanded = false })
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                Collapsed(selected, expanded, onToggle = { expanded = DockSwitcher.toggle(expanded) }, onVoice)
                Expanded(selected, expanded, onPick = { expanded = false; onSelect(it) }, onCollapse = { expanded = false }, onVoice)
            }
        }
    }
}

@Composable
private fun Collapsed(selected: Tab, expanded: Boolean, onToggle: () -> Unit, onVoice: () -> Unit) {
    val c = Cove.colors
    val reduce = LocalReduceMotion.current
    val ms = if (reduce) ReducedMotionMillis else MOTION_MS
    AnimatedVisibility(!expanded, enter = fadeIn(tween(ms)), exit = fadeOut(tween(ms))) {
        Row(
            Modifier.fillMaxWidth().height(DockBarHeight).padding(start = 28.dp, end = 28.dp, bottom = 22.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.size(44.dp))
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .shadow(0.5.dp, CoveShapes.Pill, ambientColor = shadowColor(), spotColor = shadowColor())
                    .background(c.card, CoveShapes.Pill)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Switch section"
                        stateDescription = "${selected.label}, collapsed"
                        role = Role.Button
                    }
                    .clickable(onClickLabel = "Show menu", role = Role.Button, onClick = onToggle)
                    .padding(start = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoveIcon(selected.icon, c.ink, size = 18.dp)
                CoveText(selected.label, style = CoveType.Button, color = c.ink)
                CoveIcon(CoveIcons.ChevronUp, c.muted, size = 14.dp)
            }
            Orb(onVoice)
        }
    }
}

@Composable
private fun Expanded(selected: Tab, expanded: Boolean, onPick: (Tab) -> Unit, onCollapse: () -> Unit, onVoice: () -> Unit) {
    val c = Cove.colors
    val reduce = LocalReduceMotion.current
    val ms = if (reduce) ReducedMotionMillis else MOTION_MS
    val ease = tween<Float>(ms, easing = FastOutSlowInEasing)
    AnimatedVisibility(
        expanded,
        enter = fadeIn(ease) + if (reduce) androidx.compose.animation.EnterTransition.None else slideInVertically(tween(ms, easing = FastOutSlowInEasing)) { it / 3 },
        exit = fadeOut(ease) + if (reduce) androidx.compose.animation.ExitTransition.None else slideOutVertically(tween(ms, easing = FastOutSlowInEasing)) { it / 3 },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(DockBarHeight + 8.dp)
                .background(Brush.verticalGradient(0f to c.canvas.copy(alpha = 0f), 0.45f to c.canvas, 1f to c.canvas))
                .padding(start = 28.dp, end = 28.dp, bottom = 22.dp)
                .semantics { contentDescription = "Sections" },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                val tint = if (on) c.ink else c.dockInactive
                Column(
                    Modifier
                        .size(44.dp)
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            this.selected = on
                            contentDescription = DockSwitcher.tabDescription(tab)
                        }
                        .clickable(onClick = { if (on) onCollapse() else onPick(tab) }, indication = null, interactionSource = remember { MutableInteractionSource() }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    CoveIcon(tab.icon, tint, size = 20.dp)
                    Box(Modifier.size(4.dp).background(if (on) c.ink else Color.Transparent, CircleShape))
                }
            }
            Orb(onVoice)
        }
    }
}

@Composable
private fun Orb(onVoice: () -> Unit) {
    VoiceOrb(Modifier.semantics { contentDescription = "Speak"; role = Role.Button }, size = 44.dp, onClick = onVoice)
}

@Composable
private fun shadowColor(): Color = if (Cove.colors.isDark) Color(0x4D000000) else Color(0x0F141420)

private const val DOCK_MAX_FONT_SCALE = 1.3f
private const val MOTION_MS = 200
