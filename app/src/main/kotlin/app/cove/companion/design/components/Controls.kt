package app.cove.companion.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType

/** Round tick box used by to-dos and habits; filled ink when [checked]. [label] names the item for screen readers. */
@Composable
fun CheckCircle(checked: Boolean, onToggle: (() -> Unit)?, modifier: Modifier = Modifier, size: Int = 22, label: String? = null) {
    val c = Cove.colors
    val bg by animateColorAsState(if (checked) c.ink else Color.Transparent, tween(if (LocalReduceMotion.current) 0 else 200), label = "check")
    Box(
        modifier
            .size(size.dp)
            .clip(CoveShapes.Circle)
            .background(bg)
            .let { if (checked) it else it.border(1.5.dp, if (c.isDark) c.tail else Color(0xFFCFD1D5), CoveShapes.Circle) }
            .let {
                if (onToggle != null) {
                    it.pressable(onToggle, onClickLabel = if (checked) "Mark not done" else "Mark done", role = Role.Checkbox)
                        .semantics {
                            stateDescription = if (checked) "Done" else "Not done"
                            if (label != null) contentDescription = label
                        }
                } else it
            },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) CoveIcon(CoveIcons.Check, c.onInk, size = (size * 0.7f).dp)
    }
}

/** 46x28 on/off switch from the design (ink track, white thumb); [label] names what it controls for screen readers. */
@Composable
fun CoveSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: String? = null, enabled: Boolean = true) {
    val c = Cove.colors
    val ms = if (LocalReduceMotion.current) 0 else 200
    val haptic = LocalHapticFeedback.current
    val x by animateDpAsState(if (checked) 21.dp else 3.dp, tween(ms), label = "thumb")
    val track by animateColorAsState(if (checked) c.ink else c.switchOff, tween(ms), label = "track")
    Box(
        modifier
            .size(46.dp, 28.dp)
            .graphicsLayerAlpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(track)
            .pressable({ haptic.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn); onChange(!checked) }, enabled = enabled, role = Role.Switch)
            .semantics {
                stateDescription = if (checked) "On" else "Off"
                if (label != null) contentDescription = label
            },
    ) {
        Box(
            Modifier
                .offset(x, 3.dp)
                .size(22.dp)
                .clip(CoveShapes.Circle)
                .background(if (checked) c.onInk else Color.White),
        )
    }
}

/**
 * Two-to-four way segmented control on a tinted track. When the labels cannot fit in one row
 * (large text sizes) the options stack as full-width rows instead of breaking words.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    fillWidth: Boolean = false,
) {
    val c = Cove.colors
    val base = if (height < 44.dp) CoveType.Meta else CoveType.Button
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val pad = with(LocalDensity.current) { 20.dp.toPx() }
        val trackPad = with(LocalDensity.current) { 8.dp.toPx() }
        val each = options.map { measurer.measure(it, base.copy(fontWeight = FontWeight.Medium)).size.width + pad.toInt() }
        val needed = each.sum() + trackPad
        val uneven = constraints.hasBoundedWidth && each.any { it > (constraints.maxWidth - trackPad) / options.size }
        val stacked = constraints.hasBoundedWidth && needed > constraints.maxWidth
        val shape = if (stacked) RoundedCornerShape(24.dp) else CoveShapes.Pill
        val track = Modifier.then(if (stacked || fillWidth) Modifier.fillMaxWidth() else Modifier)
        fun Modifier.tinted() = this.background(c.wellStrong, shape).padding(4.dp)
        val item: @Composable (Int, String, Modifier) -> Unit = { i, label, m ->
            val on = i == selected
            Box(
                m
                    .clip(CoveShapes.Pill)
                    .background(if (on) c.card else Color.Transparent)
                    .pressable({ onSelect(i) }, role = Role.RadioButton)
                    .semantics { this.selected = on }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                CoveText(
                    label,
                    style = base.copy(fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                    color = if (on) c.ink else c.muted,
                    maxLines = if (stacked) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Visible,
                )
            }
        }
        if (stacked) {
            Column(track.tinted(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEachIndexed { i, label -> item(i, label, Modifier.fillMaxWidth().heightIn(min = height)) }
            }
        } else {
            Row(track.height(height).tinted()) {
                options.forEachIndexed { i, label ->
                    item(i, label, Modifier.let { if (fillWidth) it.weight(if (uneven) each[i].toFloat() else 1f) else it }.fillMaxHeight())
                }
            }
        }
    }
}

/** Small tinted pill (mood, category, filters). */
@Composable
fun Chip(text: String, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, selected: Boolean = false, height: Int = 40) {
    val c = Cove.colors
    Box(
        modifier
            .height(height.dp)
            .background(if (selected) c.ink else c.card, CoveShapes.Pill)
            .let { if (onClick != null) it.pressable(onClick, role = Role.Button).semantics { this.selected = selected } else it }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CoveText(text, style = CoveType.Meta, color = if (selected) c.onInk else c.ink, maxLines = 1)
    }
}

/** White rounded surface used for grouped content. */
@Composable
fun CoveCard(modifier: Modifier = Modifier, padding: Int = 24, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Cove.colors.card, CoveShapes.Card)
            .padding(padding.dp),
    ) { content() }
}

/** 1dp hairline between rows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Cove.colors.hairline))
}

/** Settings-style row: muted label left, value and optional chevron right. */
@Composable
fun ValueRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueTail: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Cove.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.pressable(onClick, role = Role.Button) else it }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoveText(label, style = CoveType.Body.copy(fontSize = TextUnit(16f, TextUnitType.Sp)), color = c.muted)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            if (value != null) CoveText(value, valueTail ?: "", style = CoveType.Body.copy(fontSize = TextUnit(16f, TextUnitType.Sp)))
            trailing?.invoke()
            if (chevron) CoveIcon(CoveIcons.ChevronRight, c.tail, size = 18.dp)
        }
    }
}

/** Drag handle shown at the top of sheets. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp, 4.dp).background(Cove.colors.hairline, RoundedCornerShape(2.dp)))
}
