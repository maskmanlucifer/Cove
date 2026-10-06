package app.cove.companion.feature.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import java.time.LocalDate

/**
 * Floating day switcher for the Schedule: previous and next day chevrons around the shown day.
 * Floats like the add button so the page layout stays as designed; tapping the label returns to today.
 */
@Composable
fun DayPill(day: LocalDate, isToday: Boolean, onShift: (Long) -> Unit, onToday: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    val shadow = c.shadow
    Row(
        modifier
            .height(44.dp)
            .shadow(8.dp, CoveShapes.Pill, ambientColor = shadow, spotColor = shadow)
            .background(c.card, CoveShapes.Pill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).pressable({ onShift(-1) }, role = Role.Button).semantics { contentDescription = "Previous day" }, contentAlignment = Alignment.Center) {
            CoveIcon(CoveIcons.ChevronLeft, c.muted, size = 18.dp)
        }
        Box(
            Modifier.widthIn(min = 56.dp).heightIn(min = 44.dp)
                .pressable({ onToday() }, enabled = !isToday, onClickLabel = "Back to today", role = Role.Button)
                .semantics { contentDescription = if (isToday) "Today" else day.dayLabel() + ", back to today" },
            contentAlignment = Alignment.Center,
        ) {
            CoveText(if (isToday) "Today" else day.dayLabel(), style = CoveType.Meta, color = if (isToday) c.muted else c.ink, maxLines = 1)
        }
        Box(Modifier.size(44.dp).pressable({ onShift(1) }, role = Role.Button).semantics { contentDescription = "Next day" }, contentAlignment = Alignment.Center) {
            CoveIcon(CoveIcons.ChevronRight, c.muted, size = 18.dp)
        }
    }
}
