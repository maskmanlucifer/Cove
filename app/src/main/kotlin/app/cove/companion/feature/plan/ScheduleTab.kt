package app.cove.companion.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.clockText
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable

private val TimeColumn = 56.dp
private val Detail = CoveType.Meta.copy(fontSize = 13.sp, lineHeight = 17.55.sp)
private val CardTitle = CoveType.Body.copy(fontSize = 18.sp, lineHeight = 24.3.sp, fontWeight = FontWeight.Medium)
private val NowLabel = CoveType.Label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)

/** The "Schedule" segment: today's events, alarms and dated to-dos around the now line. */
@Composable
fun ScheduleTab(rows: List<TimelineRow>, onOpenTodo: (String) -> Unit, onOpenEvent: (String) -> Unit, onOpenAlarm: (String) -> Unit = {}) {
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            when (row) {
                is TimelineRow.Now -> NowLine(row.minutes)
                is TimelineRow.Entry -> if (row.card) EventCard(row, onOpenEvent) else EntryRow(row, onOpenTodo, onOpenEvent, onOpenAlarm)
            }
        }
    }
}

@Composable
private fun TimeLabel(minutes: Int, modifier: Modifier = Modifier, strong: Boolean = false) {
    val c = Cove.colors
    CoveText(
        clockText(minutes).digits,
        modifier.semantics { contentDescription = clockText(minutes).let { it.digits + " " + it.suffix.trim() } },
        style = if (strong) CoveType.MetaMedium else CoveType.Meta,
        color = if (strong) c.ink else c.muted,
        maxLines = 1,
    )
}

@Composable
private fun EntryRow(row: TimelineRow.Entry, onOpenTodo: (String) -> Unit, onOpenEvent: (String) -> Unit, onOpenAlarm: (String) -> Unit) {
    val c = Cove.colors
    val item = row.item
    val open = item.todoId ?: item.eventId ?: item.alarmId
    val onOpen = if (item.eventId != null) onOpenEvent else if (item.alarmId != null) onOpenAlarm else onOpenTodo
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (open != null) it.pressable({ onOpen(open) }, role = Role.Button) else it }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeLabel(item.minutes, Modifier.widthIn(min = TimeColumn))
        Column(Modifier.weight(1f)) {
            val style: TextStyle = if (row.past) CoveType.Body.copy(textDecoration = TextDecoration.LineThrough) else CoveType.Body
            CoveText(item.title, style = style, color = if (row.past) c.tail else c.ink)
            row.detail?.let { CoveText(it, style = Detail, color = c.tail) }
        }
    }
}

@Composable
private fun NowLine(minutes: Int) {
    val c = Cove.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        CoveText(clockText(minutes).digits, Modifier.widthIn(min = TimeColumn), style = NowLabel)
        Box(Modifier.weight(1f).height(1.5.dp).background(c.ink, RoundedCornerShape(1.dp)))
    }
}

@Composable
private fun EventCard(row: TimelineRow.Entry, onOpen: (String) -> Unit) {
    val c = Cove.colors
    val id = row.item.eventId
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).let { if (id != null) it.pressable({ onOpen(id) }, role = Role.Button).semantics(mergeDescendants = true) {} else it }) {
        TimeLabel(row.item.minutes, Modifier.widthIn(min = TimeColumn).padding(top = 20.dp, end = 8.dp), strong = true)
        Column(
            Modifier
                .weight(1f)
                .background(c.card, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CoveText(row.item.title, style = CardTitle)
            row.detail?.let { CoveText(it, style = CoveType.Meta, color = c.muted) }
        }
    }
}
