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
import androidx.compose.ui.text.font.FontWeight
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
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .let { if (tap != null) it.pressable(tap) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoveText(label, Modifier.weight(1f), style = Body16)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (value != null) CoveText(value, style = CoveType.Button.copy(fontWeight = FontWeight.Normal), color = c.muted)
            if (checked != null) CoveSwitch(checked, { onCheck?.invoke(it) })
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
