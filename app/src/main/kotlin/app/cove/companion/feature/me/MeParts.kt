package app.cove.companion.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveSwitch
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.onboarding.Body16

/** A labelled group: small muted title above a white card holding [SettingsRow]s. */
@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CoveText(title, Modifier.padding(start = 4.dp), style = CoveType.Meta, color = Cove.colors.muted)
        Column(Modifier.fillMaxWidth().background(Cove.colors.card, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp)) {
            content()
        }
    }
}

/** Divider placed between two rows of a [SettingsGroup]. */
@Composable
fun RowDivider() = Hairline()

/**
 * One 52 dp row. Give [checked] and [onCheck] for a switch; otherwise the row shows [value] and a chevron
 * when [onClick] is set.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    checked: Boolean? = null,
    onCheck: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Cove.colors
    val tap = onClick ?: if (checked != null && onCheck != null) ({ onCheck(!checked) }) else null
    val stacked = value != null && LocalDensity.current.fontScale > 1.3f
    val valueStyle = CoveType.Button.copy(fontWeight = FontWeight.Normal)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .let { if (tap != null) it.pressable(tap, role = if (checked != null) Role.Switch else Role.Button) else it }
            .semantics(mergeDescendants = true) { if (checked != null) stateDescription = if (checked) "On" else "Off" }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (stacked) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CoveText(label, style = Body16)
                CoveText(value.orEmpty(), style = valueStyle, color = c.muted)
            }
        } else {
            CoveText(label, Modifier.weight(1f), style = Body16)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (value != null && !stacked) CoveText(value, style = valueStyle, color = c.muted)
            if (checked != null) CoveSwitch(checked, { onCheck?.invoke(it) }, label = label)
            else if (onClick != null) CoveIcon(CoveIcons.ChevronRight, c.tail, size = 14.dp)
        }
    }
}

/** Small muted caption used inside sheets. */
@Composable
fun SheetCaption(text: String) = CoveText(text, style = CoveType.Meta.copy(lineHeight = 21.sp), color = Cove.colors.muted)

/** Sheet title, matching the other sheets' title size. */
@Composable
fun SheetHeading(text: String) = CoveText(text, style = CoveType.Section.copy(lineHeight = 32.sp))

/** Gentle, non-blocking entry to the conflict screen. */
@Composable
fun ConflictBanner(title: String, onReview: () -> Unit) {
    val c = Cove.colors
    Row(
        Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(24.dp)).pressable(onReview, role = Role.Button).semantics(mergeDescendants = true) {}.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            CoveText("Two versions of “$title”", style = CoveType.BodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CoveText("Changed on another device.", style = CoveType.Meta, color = c.muted)
        }
        CoveText("Review", style = CoveType.Button, color = c.ink)
    }
}
