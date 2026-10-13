package app.cove.companion.design.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.ReducedMotionMillis
import kotlin.math.floor

/** Top-level destinations, in swipe order: Me sits left of Today, and the pages used most sit closest to Today. */
enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Me("Me", CoveIcons.Me, CoveIcons.MeBold),
    Today("Today", CoveIcons.Today, CoveIcons.TodayBold),
    Plan("Plan", CoveIcons.Plan, CoveIcons.PlanBold),
    Money("Money", CoveIcons.Money, CoveIcons.MoneyBold),
    Journal("Journal", CoveIcons.Journal, CoveIcons.JournalBold),
}

/** The pages you swipe between, in order. The app opens on [Tab.Today]. */
val SwipePages: List<Tab> = Tab.entries

/** Pure pieces of the dock, kept apart from the composable so they can be unit tested. */
object DockSwitcher {
    /** How long the page list stays up after a tap before it settles away on its own. */
    const val AUTO_COLLAPSE_MS = 4000L

    val pageCount: Int get() = SwipePages.size

    /** Which page dot is lit for [tab]. */
    fun pageIndex(tab: Tab): Int = SwipePages.indexOf(tab)

    /** Spoken description of a row in the page list, e.g. "Plan, page 3 of 5". */
    fun tabDescription(tab: Tab): String = "${tab.label}, page ${pageIndex(tab) + 1} of $pageCount"

    /** Open the list unless it is already open; the same tap closes it. */
    fun toggle(expanded: Boolean): Boolean = !expanded

    /** Spoken description of the page dots, e.g. "Page 3 of 5, Plan. Tap to choose a page". */
    fun pillDescription(tab: Tab): String = "Page ${pageIndex(tab) + 1} of $pageCount, ${tab.label}. Tap to choose a page"

    /**
     * The row under a finger [y] px below the top of the list's first row, rows [rowHeight] px tall. Above the list
     * counts as the first row, so a quick flick upwards still lands; below the list (back over the dots) is no row.
     */
    fun rowAt(y: Float, rowHeight: Float, count: Int = pageCount): Int? = when {
        y < 0f -> 0
        y >= rowHeight * count -> null
        else -> floor(y / rowHeight).toInt()
    }
}

/** The page dots, stacked: the current page is a tall green bar, the others small muted dots. */
@Composable
private fun PageDots(active: Int) {
    val c = Cove.colors
    val reduce = LocalReduceMotion.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(DockSwitcher.pageCount) { i ->
            val on = i == active
            val height by animateDpAsState(if (on) 14.dp else 6.dp, if (reduce) snap() else tween(180), label = "dotHeight")
            Box(Modifier.size(6.dp, height).background(if (on) c.accent else c.dockInactive.copy(alpha = 0.5f), CoveShapes.Pill))
        }
    }
}

/**
 * Floating dock at the bottom right: the page dots above the voice orb. Tapping the dots opens the page list and a tap
 * on a row goes there. Pressing the dots and sliding onto the list moves through the pages as the finger passes each
 * row, with a light tick, and letting go keeps the page. A tap elsewhere, Back, or four seconds of nothing closes the
 * list. Place it in a full-size box.
 *
 * @param onPreview called as a drag passes over each row, so the page behind can follow the finger. Leave it null
 * where switching pages means leaving the screen; [onSelect] then fires once, on release.
 */
@Composable
fun CoveDock(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
    onPreview: ((Tab) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf<Tab?>(null) }
    val timeout = LocalAccessibilityManager.current
    LaunchedEffect(expanded, hovered) {
        if (!expanded || hovered != null) return@LaunchedEffect
        val ms = timeout?.calculateRecommendedTimeoutMillis(DockSwitcher.AUTO_COLLAPSE_MS, containsIcons = false, containsText = true, containsControls = true)
            ?: DockSwitcher.AUTO_COLLAPSE_MS
        kotlinx.coroutines.delay(ms)
        expanded = false
    }
    BackHandler(enabled = expanded) { expanded = false }
    val reduce = LocalReduceMotion.current
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val preview by rememberUpdatedState(onPreview)
    val coords = remember { arrayOfNulls<LayoutCoordinates>(2) }
    val rowPx = with(LocalDensity.current) { DockRowHeight.toPx() }
    val listPadPx = with(LocalDensity.current) { DockListPadding.toPx() }
    val pick: (Tab) -> Unit = { tab -> expanded = false; if (tab != current) select(tab) }
    val c = Cove.colors
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(DOCK_MAX_FONT_SCALE))) {
        Box(modifier.fillMaxSize()) {
            val fade = if (reduce) ReducedMotionMillis else 160
            AnimatedVisibility(expanded, Modifier.fillMaxSize(), enter = fadeIn(tween(fade)), exit = fadeOut(tween(fade))) {
                Box(Modifier.fillMaxSize()) {
                    // Light enough that the page behind stays readable while a drag moves through the pages.
                    Box(
                        Modifier.fillMaxSize().background(c.canvas.copy(alpha = 0.6f))
                            .clickable(remember { MutableInteractionSource() }, indication = null) { expanded = false },
                    )
                    PageList(
                        highlighted = hovered ?: selected,
                        onPick = pick,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(end = DockEnd, bottom = DockBarHeight + 12.dp)
                            .onGloballyPositioned { coords[1] = it },
                    )
                }
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = DockEnd, bottom = DockBottom)
                    .shadow(4.dp, CoveShapes.Pill, ambientColor = shadowColor(), spotColor = shadowColor())
                    .background(c.card, CoveShapes.Pill).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(48.dp, 64.dp)
                        .onGloballyPositioned { coords[0] = it }
                        .semantics(mergeDescendants = true) {
                            contentDescription = DockSwitcher.pillDescription(selected)
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                            role = Role.Button
                            onClick("Choose a page") { expanded = DockSwitcher.toggle(expanded); true }
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val wasOpen = expanded
                                expanded = true
                                var dragging = false
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                                    if (!dragging) continue
                                    change.consume()
                                    val dots = coords[0] ?: continue
                                    val list = coords[1]?.takeIf { it.isAttached } ?: continue
                                    val y = list.localPositionOf(dots, change.position).y - listPadPx
                                    val tab = DockSwitcher.rowAt(y, rowPx)?.let { SwipePages[it] }
                                    if (tab != null && tab != hovered) {
                                        hovered = tab
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        preview?.invoke(tab)
                                    }
                                }
                                val landed = hovered
                                hovered = null
                                when {
                                    dragging && landed != null -> pick(landed)
                                    dragging -> expanded = false
                                    wasOpen -> expanded = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { PageDots(DockSwitcher.pageIndex(selected)) }
                VoiceOrb(Modifier.semantics { contentDescription = "Speak"; role = Role.Button }, size = 48.dp, onClick = onVoice)
            }
        }
    }
}

/** The list of pages above the dock; [highlighted] is the current page, or the row under a dragging finger. */
@Composable
private fun PageList(highlighted: Tab, onPick: (Tab) -> Unit, modifier: Modifier) {
    val c = Cove.colors
    Column(
        modifier.width(220.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = shadowColor(), spotColor = shadowColor())
            .background(c.card, RoundedCornerShape(20.dp)).padding(DockListPadding),
    ) {
        SwipePages.forEach { tab ->
            val on = tab == highlighted
            Row(
                Modifier.fillMaxWidth().height(DockRowHeight)
                    .background(if (on) c.ink else Color.Transparent, RoundedCornerShape(14.dp))
                    .semantics(mergeDescendants = true) {
                        role = Role.Tab
                        selected = on
                        contentDescription = DockSwitcher.tabDescription(tab)
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onPick(tab) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoveText(
                    tab.label, Modifier.weight(1f),
                    style = CoveType.Body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) c.onInk else c.ink, maxLines = 1,
                )
                if (tab == Tab.Me) CoveText("Settings", style = CoveType.Meta, color = if (on) c.onInk else c.muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun shadowColor(): Color = Cove.colors.shadow.copy(alpha = if (Cove.colors.isDark) 0.3f else 0.1f)

private val DockEnd = 20.dp
private val DockRowHeight = 48.dp
private val DockListPadding = 6.dp
private const val DOCK_MAX_FONT_SCALE = 1.3f
