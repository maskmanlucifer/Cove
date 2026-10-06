package app.cove.companion.feature.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.graphicsLayerAlpha
import app.cove.companion.design.components.pressable

/** Type used across the training frames. */
object TrainingType {
    val Button = CoveType.Button.copy(fontSize = 16.sp, lineHeight = 21.6.sp)
    val Row = CoveType.Body.copy(fontSize = 16.sp, lineHeight = 21.6.sp)
    val Big = CoveType.Figure.copy(fontSize = 64.sp, lineHeight = 66.sp)
}

/** Bare chevron back button. */
@Composable
fun BackChevron(onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).pressable(onClick, role = Role.Button).semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) { CoveIcon(CoveIcons.ChevronLeft, Cove.colors.muted, size = 22.dp) }
}

/** Plain text action at the top right ("Finish"). */
@Composable
fun TextAction(text: String, onClick: () -> Unit, strong: Boolean = true) {
    Box(
        Modifier.heightIn(min = 48.dp).pressable(onClick, role = Role.Button).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        CoveText(text, style = TrainingType.Row.copy(fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal), color = if (strong) Cove.colors.ink else Cove.colors.muted)
    }
}

/** Full-width dark pill, 56dp, dimmed and inert while [enabled] is false. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PillButton(
        text, { if (enabled) onClick() }, modifier.graphicsLayerAlpha(if (enabled) 1f else 0.35f).semantics { if (!enabled) contentDescription = "$text, not available" },
        height = 56.dp, textStyle = TrainingType.Button,
    )
}

/** Row of 56dp with a hairline above (except the first), label left and value right. */
@Composable
fun LabelRow(label: String, value: String, first: Boolean, muted: Color = Cove.colors.muted, valueColor: Color = Cove.colors.ink, onClick: (() -> Unit)? = null, chevron: Boolean = false) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Cove.colors.well))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).let { if (onClick != null) it.pressable(onClick, role = Role.Button) else it }.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoveText(label, style = TrainingType.Row, color = muted)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            CoveText(value, style = TrainingType.Row, color = valueColor, textAlign = TextAlign.End, maxLines = 2)
            if (chevron) CoveIcon(CoveIcons.ChevronRight, Cove.colors.tail, size = 14.dp)
        }
    }
}
