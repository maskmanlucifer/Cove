package app.cove.companion.design.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.roundToInt
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
 * Compact bottom bar: a pill showing the current tab and the voice orb. Tapping the pill morphs it into the row of
 * the five destinations; the orb never moves and the current tab's icon keeps its x (see [DockMotion]). Choosing a
 * tab, tapping elsewhere, Back, or four seconds of nothing closes it again. Place it at the bottom of a full-size box.
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
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(expanded, reduce) {
        val spec = if (reduce) tween<Float>(ReducedMotionMillis, easing = LinearEasing) else tween(DockMotion.DURATION_MS, easing = DockMotion.Ease)
        progress.animateTo(if (expanded) 1f else 0f, spec)
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(DOCK_MAX_FONT_SCALE))) {
        Box(modifier.fillMaxSize()) {
            if (expanded) {
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { expanded = false })
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(DockBarHeight).padding(bottom = 22.dp)) {
                DockMorph(
                    selected, expanded, progress, reduce,
                    onToggle = { expanded = DockSwitcher.toggle(expanded) },
                    onPick = { expanded = false; if (it != selected) onSelect(it) },
                    Modifier.align(Alignment.BottomStart),
                )
                Box(Modifier.align(Alignment.BottomEnd).padding(end = 28.dp)) { Orb(onVoice) }
            }
        }
    }
}

/**
 * The pill and the five tab slots in one layout of fixed size, so nothing re-measures or reflows while it opens:
 * the card background grows around the current icon, the other icons slide out from it, and the label fades.
 */
@Composable
private fun DockMorph(
    selected: Tab,
    expanded: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    reduce: Boolean,
    onToggle: () -> Unit,
    onPick: (Tab) -> Unit,
    modifier: Modifier,
) {
    val c = Cove.colors
    Layout(
        content = {
            Box(Modifier.layoutId("bg").shadow(0.5.dp, CoveShapes.Pill, ambientColor = shadowColor(), spotColor = shadowColor()).background(c.card, CoveShapes.Pill))
            Row(
                Modifier.layoutId("label").graphicsLayer { alpha = DockMotion.frame(progress.value, reduce).labelAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selected.ordinal >= DockMotion.LEFT_EXTENDING_FROM) CoveIcon(CoveIcons.ChevronUp, c.muted, size = 14.dp)
                CoveText(selected.label, style = CoveType.Button, color = c.ink, maxLines = 1)
                if (selected.ordinal < DockMotion.LEFT_EXTENDING_FROM) CoveIcon(CoveIcons.ChevronUp, c.muted, size = 14.dp)
            }
            Tab.entries.forEach { tab ->
                val on = tab == selected
                Box(
                    Modifier
                        .layoutId(tab)
                        .then(
                            if (expanded) Modifier.semantics(mergeDescendants = true) {
                                role = Role.Tab
                                this.selected = on
                                contentDescription = DockSwitcher.tabDescription(tab)
                            }.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { if (on) onToggle() else onPick(tab) }
                            else Modifier.clearAndSetSemantics { },
                        ),
                ) {
                    // Active state is colour, not a marker or a shift: a soft blue disc fades in behind the current icon
                    // and the icon turns blue, so nothing moves vertically while the bar opens or closes.
                    if (on) {
                        Box(
                            Modifier.align(Alignment.Center).size(36.dp)
                                .graphicsLayer { alpha = DockMotion.frame(progress.value, reduce).geom }
                                .background(c.accentSoft, CircleShape),
                        )
                    }
                    CoveIcon(tab.icon, if (on) c.accent else c.dockInactive, size = 20.dp, modifier = Modifier.align(Alignment.Center))
                }
            }
            if (!expanded) {
                Box(
                    Modifier
                        .layoutId("hit")
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Switch section"
                            stateDescription = "${selected.label}, collapsed"
                            role = Role.Button
                        }
                        .clickable(onClickLabel = "Show menu", role = Role.Button, onClick = onToggle),
                )
            }
        },
        modifier = modifier.fillMaxWidth().height(44.dp),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = 44.dp.roundToPx()
        val slot = 44.dp.roundToPx()
        fun px(dp: Float) = (dp * density).roundToInt()
        val label = measurables.first { it.layoutId == "label" }.measure(Constraints())
        val geo = DockMotion.geometry(w / density, selected.ordinal, label.width / density)
        val f = DockMotion.frame(progress.value, reduce)
        val bgW = px(DockMotion.lerp(geo.collapsedRight - geo.collapsedLeft, geo.expandedRight - geo.expandedLeft, f.geom))
        val bgX = px(DockMotion.lerp(geo.collapsedLeft, geo.expandedLeft, f.geom))
        val labelX = if (selected.ordinal < DockMotion.LEFT_EXTENDING_FROM) geo.iconCenter + 18f else geo.iconCenter - 18f - label.width / density
        val placed = measurables.filter { it.layoutId != "label" }.map { m ->
            val id = m.layoutId
            val size = when (id) {
                "bg" -> Constraints.fixed(bgW, h)
                "hit" -> Constraints.fixed(px(geo.collapsedRight - geo.collapsedLeft), h)
                else -> Constraints.fixed(slot, slot)
            }
            id to m.measure(size)
        }
        layout(w, h) {
            placed.forEach { (id, pl) ->
                when (id) {
                    "bg" -> pl.place(bgX, 0)
                    "hit" -> pl.place(px(geo.collapsedLeft), 0)
                    is Tab -> {
                        val cx = geo.slotCenter(id.ordinal)
                        if (id == selected) pl.place(px(cx) - slot / 2, 0)
                        else pl.placeWithLayer(px(cx) - slot / 2, 0) {
                            alpha = f.otherAlpha
                            translationX = px((geo.slotCenter(selected.ordinal) - cx) * (1f - f.geom)).toFloat()
                        }
                    }
                }
                if (id == "bg") label.place(px(labelX), (h - label.height) / 2)
            }
        }
    }
}

@Composable
private fun Orb(onVoice: () -> Unit) {
    VoiceOrb(Modifier.semantics { contentDescription = "Speak"; role = Role.Button }, size = 44.dp, onClick = onVoice)
}

@Composable
private fun shadowColor(): Color = Cove.colors.shadow.copy(alpha = if (Cove.colors.isDark) 0.3f else 0.06f)

private const val DOCK_MAX_FONT_SCALE = 1.3f
