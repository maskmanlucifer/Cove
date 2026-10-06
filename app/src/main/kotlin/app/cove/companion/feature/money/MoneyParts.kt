package app.cove.companion.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveDock
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.Tab
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** Text sizes the Money frames use between the design scale's steps. */
internal object MoneyType {
    val Row = CoveType.Body.copy(fontSize = 16.sp, lineHeight = 21.6.sp)
    val Sub = CoveType.Button.copy(fontWeight = FontWeight.Normal)
    val Small = CoveType.Meta.copy(fontSize = 13.sp, lineHeight = 17.55.sp)
    val Big = CoveType.Figure.copy(fontSize = 64.sp, lineHeight = 66.sp)
    val Quote = CoveType.Button.copy(fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val Note = CoveType.Meta.copy(lineHeight = 21.sp)
}

/** White rounded block holding rows separated by thin dividers. */
@Composable
internal fun RowsCard(
    modifier: Modifier = Modifier,
    horizontal: Dp = 20.dp,
    vertical: Dp = 6.dp,
    radius: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Cove.colors.card, RoundedCornerShape(radius))
            .padding(horizontal = horizontal, vertical = vertical),
    ) { content() }
}

/** Divider between rows inside a [RowsCard]. */
@Composable
internal fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Cove.colors.well))
}

/** Label on the left, a tappable value with a chevron on the right (When, Paid with, Monthly budget). */
@Composable
internal fun PickRow(label: String, value: String, onClick: () -> Unit, divider: Boolean = true) {
    if (divider) RowDivider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CoveText(label, Modifier.padding(end = 12.dp), style = MoneyType.Row, color = Cove.colors.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CoveText(value, style = MoneyType.Row)
            CoveIcon(CoveIcons.ChevronRight, Cove.colors.tail, size = 14.dp)
        }
    }
}

/**
 * 44dp white circle with an icon, used for close and add. The tap area is 48dp and [label] is what screen readers
 * announce, since the icon has no text.
 */
@Composable
internal fun RoundIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    filled: Boolean = true,
) {
    Box(
        modifier
            .size(44.dp)
            .requiredSize(48.dp)
            .pressable(onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(44.dp).background(if (filled) Cove.colors.card else Color.Transparent, CoveShapes.Circle),
            contentAlignment = Alignment.Center,
        ) { CoveIcon(icon, if (filled) Cove.colors.ink else Cove.colors.muted, size = iconSize) }
    }
}

/** Back button, centred label and a trailing text action: the bar of the pushed Money screens. */
@Composable
internal fun MoneyTopBar(label: String, action: String, onBack: () -> Unit, onAction: () -> Unit, actionStrong: Boolean) {
    val c = Cove.colors
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RoundIconButton(CoveIcons.ChevronLeft, onBack, "Back", iconSize = 24.dp, filled = false)
        CoveText(label, style = CoveType.Meta, color = c.muted)
        Box(Modifier.height(44.dp).requiredHeight(48.dp).pressable(onAction).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            CoveText(
                action,
                style = MoneyType.Row.copy(fontWeight = if (actionStrong) FontWeight.Medium else FontWeight.Normal),
                color = if (actionStrong) c.ink else c.muted,
            )
        }
    }
}

/** Thin progress track; the fill turns terracotta once the budget is passed. */
@Composable
internal fun BudgetBar(fraction: Float, over: Boolean, modifier: Modifier = Modifier) {
    val c = Cove.colors
    Box(modifier.fillMaxWidth().height(4.dp).background(c.hairline, RoundedCornerShape(2.dp))) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .background(if (over) c.alert else c.ink, RoundedCornerShape(2.dp)),
        )
    }
}

/** The floating dock on pushed Money screens: Money stays selected, other tabs go home. */
@Composable
internal fun MoneyDock(nav: Nav, modifier: Modifier = Modifier) {
    CoveDock(
        Tab.Money,
        onSelect = { if (it == Tab.Money) nav.back() else nav.home() },
        onVoice = { nav.go(Routes.Voice) },
        modifier = modifier,
    )
}

/** Two-way toggle on a tinted track (Spent / Received, Spending / Income). [fill] stretches the options evenly. */
@Composable
internal fun KindToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, fill: Boolean = false) {
    val c = Cove.colors
    Row(modifier.height(40.dp).background(c.well, CoveShapes.Pill).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .then(if (on) Modifier.shadow(1.dp, CoveShapes.Pill, ambientColor = Color(0x0F141420), spotColor = Color(0x0F141420)) else Modifier)
                    .background(if (on) c.card else Color.Transparent, CoveShapes.Pill)
                    .pressable({ onSelect(i) })
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CoveText(
                    label,
                    style = CoveType.Meta.copy(fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                    color = if (on) c.ink else c.muted,
                )
            }
        }
    }
}
