package app.cove.companion.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType

/** Round tick box used by to-dos and habits; filled ink when [checked]. */
@Composable
fun CheckCircle(checked: Boolean, onToggle: (() -> Unit)?, modifier: Modifier = Modifier, size: Int = 22) {
    val c = Cove.colors
    val bg by animateColorAsState(if (checked) c.ink else Color.Transparent, tween(200), label = "check")
    Box(
        modifier
            .size(size.dp)
            .clip(CoveShapes.Circle)
            .background(bg)
            .let { if (checked) it else it.border(1.5.dp, if (c.isDark) c.tail else Color(0xFFCFD1D5), CoveShapes.Circle) }
            .let { if (onToggle != null) it.pressable(onToggle) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) CoveIcon(CoveIcons.Check, c.onInk, size = (size * 0.7f).dp)
    }
}

/** 46x28 on/off switch from the design (ink track, white thumb). */
@Composable
fun CoveSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    val x by animateDpAsState(if (checked) 21.dp else 3.dp, tween(200), label = "thumb")
    val track by animateColorAsState(if (checked) c.ink else c.wellStrong, tween(200), label = "track")
    Box(
        modifier
            .size(46.dp, 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(track)
            .pressable({ onChange(!checked) }),
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

/** Two-to-four way segmented control on a tinted track. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    Row(
        modifier
            .height(44.dp)
            .background(c.wellStrong, CoveShapes.Pill)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .height(36.dp)
                    .clip(CoveShapes.Pill)
                    .background(if (on) c.card else Color.Transparent)
                    .pressable({ onSelect(i) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                CoveText(
                    label,
                    style = CoveType.Button.copy(fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                    color = if (on) c.ink else c.muted,
                )
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
            .let { if (onClick != null) it.pressable(onClick) else it }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CoveText(text, style = CoveType.Meta, color = if (selected) c.onInk else c.ink)
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
            .height(56.dp)
            .let { if (onClick != null) it.pressable(onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CoveText(label, style = CoveType.Body.copy(fontSize = TextUnit(16f, TextUnitType.Sp)), color = c.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
