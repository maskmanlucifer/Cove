package app.cove.companion.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.clockText
import app.cove.companion.core.toEpochMillis
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.AccentButton
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveSwitch
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.Segmented
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val ButtonText = CoveType.Button.copy(fontSize = 16.sp)

/**
 * Sheet sub-page that picks one of [categories]. With none yet it explains what categories are and lets the person make
 * the first one right here ([onCreate]); otherwise "New category" opens the same field under the list.
 */
@Composable
fun CategoryPage(
    categories: List<TodoCategoryEntity>,
    selected: String?,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val c = Cove.colors
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    SubPageHeader("Category", onBack)
    val create = { if (name.isNotBlank()) { onCreate(name.trim()); name = "" } }
    if (categories.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CoveText("No categories yet", style = CoveType.Heading)
            CoveText(
                "Categories keep your to-dos tidy, like Home or Shopping. Make your first one, or skip it and the to-do goes to Inbox.",
                style = CoveType.Meta, color = c.muted,
            )
        }
    } else {
        OptionList(categories.map { it.id to it.name }, selected) { onPick(it) }
    }
    if (creating || categories.isEmpty()) {
        TitleField(name, { name = it }, "Category name", onDone = create, autofocus = categories.isNotEmpty())
        PillButton("Create category", create, Modifier.fillMaxWidth(), height = 52.dp, textStyle = ButtonText)
    } else {
        AccentButton("New category", { creating = true }, Modifier.padding(top = 8.dp))
    }
}

/** Sheet sub-page that picks a due day and optionally a time; "Clear" removes the date. */
@Composable
fun WhenPage(initial: Long?, now: Long, onSet: (Long?) -> Unit, onBack: () -> Unit) {
    val start = initial?.toLocalDateTime()
    var date by remember { mutableStateOf(start?.toLocalDate() ?: now.toLocalDateTime().toLocalDate()) }
    var hasTime by remember { mutableStateOf(start != null && (start.hour != 0 || start.minute != 0)) }
    var minutes by remember { mutableIntStateOf(start?.let { it.hour * 60 + it.minute }?.takeIf { it > 0 } ?: 9 * 60) }
    SubPageHeader("When", onBack)
    DatePanel(date) { date = it }
    SheetControlRow("Add a time") { CoveSwitch(hasTime, { hasTime = it }) }
    if (hasTime) TimePanel(minutes) { minutes = it }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(
            "Set", { onSet(LocalDateTime.of(date, if (hasTime) LocalTime.of(minutes / 60, minutes % 60) else LocalTime.MIDNIGHT).toEpochMillis()) },
            Modifier.weight(1f), height = 56.dp, textStyle = ButtonText,
        )
        PillButton("Clear", { onSet(null) }, Modifier.height(56.dp), kind = ButtonKind.Text, height = 56.dp, horizontalPadding = 32.dp, textStyle = ButtonText)
    }
}

/** Sheet sub-page that picks a day. */
@Composable
fun DayPage(selected: LocalDate, onPick: (LocalDate) -> Unit, onBack: () -> Unit) {
    SubPageHeader("Day", onBack)
    DatePanel(selected, onPick)
}

/** Sheet sub-page that edits an event's start and end time. */
@Composable
fun TimeRangePage(start: Int, end: Int, onChange: (Int, Int) -> Unit, onBack: () -> Unit) {
    var editingEnd by remember { mutableStateOf(false) }
    SubPageHeader("Time", onBack)
    Segmented(
        listOf("Starts ${clockText(start).digits}${clockText(start).suffix}", "Ends ${clockText(end).digits}${clockText(end).suffix}"),
        if (editingEnd) 1 else 0, { editingEnd = it == 1 },
        Modifier.fillMaxWidth(), height = 40.dp, fillWidth = true,
    )
    if (editingEnd) {
        TimePanel(end) { onChange(start, if (it <= start) start + 5 else it) }
    } else {
        TimePanel(start) { newStart ->
            val length = (end - start).coerceAtLeast(5)
            onChange(newStart, (newStart + length).coerceAtMost(24 * 60 - 1))
        }
    }
}

/** Remind-before choices: minutes before the start, or null for none. */
val RemindOptions: List<Pair<Int?, String>> = listOf(
    null to "None", 0 to "At time of event", 5 to "5 min before", 10 to "10 min before",
    15 to "15 min before", 30 to "30 min before", 60 to "1 hour before", 1440 to "1 day before",
)

/** Label for a remind-before value. */
fun remindText(minutes: Int?): String = RemindOptions.firstOrNull { it.first == minutes }?.second ?: "$minutes min before"

/** Repeat choices stored on events. */
val RepeatOptions: List<Pair<String, String>> = listOf("none" to "Never", "daily" to "Every day", "weekly" to "Every week", "monthly" to "Every month")

/** Day and time of [dueAt] such as "Sat, 10:00" plus the muted " am"; null when there is no date. */
fun whenParts(dueAt: Long?, now: Long): Pair<String, String?>? {
    if (dueAt == null) return null
    val d = dueAt.toLocalDateTime()
    val day = relativeDay(d.toLocalDate(), now.toLocalDateTime().toLocalDate())
    if (d.hour == 0 && d.minute == 0) return day to null
    val t = clockText(d.hour * 60 + d.minute)
    return "$day, ${t.digits}" to t.suffix
}
