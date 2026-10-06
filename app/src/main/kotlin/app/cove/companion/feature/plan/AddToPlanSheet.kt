package app.cove.companion.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.clockText
import app.cove.companion.core.newId
import app.cove.companion.core.toEpochMillis
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveSwitch
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.Segmented
import app.cove.companion.design.components.ValueRow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private enum class AddPage { Main, Day, Time, Repeat, Remind, Category, When }

/** What the add sheet starts with; the debug launcher uses it to reproduce the design. */
data class AddDefaults(val event: Boolean, val title: String = "", val autofocus: Boolean = true)

/**
 * Sheet for adding a to-do or an event from the Plan tab. Events take a day, time range, repeat,
 * reminder and place; to-dos take a category, optional due time and reminder.
 */
@Composable
fun AddToPlanSheet(
    defaults: AddDefaults,
    categories: List<TodoCategoryEntity>,
    now: Long,
    onAddTodo: (title: String, categoryId: String?, dueAt: Long?, remind: Boolean) -> Unit,
    onAddEvent: (EventEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val nowTime = now.toLocalDateTime()
    val startDefault = ((nowTime.hour * 60 + nowTime.minute) / 60 + 1).coerceAtMost(22) * 60
    var isEvent by remember { mutableStateOf(defaults.event) }
    var title by remember { mutableStateOf(defaults.title) }
    var page by remember { mutableStateOf(AddPage.Main) }
    var day by remember { mutableStateOf(now.toLocalDate()) }
    var start by remember { mutableIntStateOf(startDefault) }
    var end by remember { mutableIntStateOf(startDefault + 45) }
    var repeat by remember { mutableStateOf("none") }
    var remindBefore by remember { mutableStateOf<Int?>(30) }
    var place by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var dueAt by remember { mutableStateOf<Long?>(null) }
    var remind by remember { mutableStateOf(false) }

    PlanSheet(onDismiss, gap = 20) { close ->
        val back = { page = AddPage.Main }
        when (page) {
            AddPage.Day -> DayPage(day, { day = it; back() }, back)
            AddPage.Time -> TimeRangePage(start, end, { s, e -> start = s; end = e }, back)
            AddPage.Repeat -> { SubPageHeader("Repeat", back); OptionList(RepeatOptions, repeat) { repeat = it; back() } }
            AddPage.Remind -> { SubPageHeader("Remind me", back); OptionList(RemindOptions, remindBefore) { remindBefore = it; back() } }
            AddPage.Category -> CategoryPage(categories, categoryId, { categoryId = it; back() }, back)
            AddPage.When -> WhenPage(dueAt, now, { dueAt = it; back() }, back)
            AddPage.Main -> {
                Segmented(
                    listOf("To-do", "Event"), if (isEvent) 1 else 0, { isEvent = it == 1 },
                    Modifier.fillMaxWidth(), height = 40.dp, fillWidth = true,
                )
                TitleField(
                    title, { title = it }, if (isEvent) "What’s happening?" else "What do you need to do?",
                    onDone = {}, autofocus = defaults.autofocus,
                )
                Column {
                    if (isEvent) {
                        SheetRow("Day", day.dayLabel(), { page = AddPage.Day })
                        SheetRow("Time", rangeText(start, end), { page = AddPage.Time })
                        SheetRow("Repeat", RepeatOptions.first { it.first == repeat }.second, { page = AddPage.Repeat })
                        SheetRow("Remind me", remindText(remindBefore), { page = AddPage.Remind })
                        PlaceRow(place) { place = it }
                    } else {
                        SheetRow("Category", categories.firstOrNull { it.id == categoryId }?.name ?: "None", { page = AddPage.Category })
                        val whenText = whenParts(dueAt, now)
                        SheetRow("When", whenText?.first ?: "Not set", { page = AddPage.When }, whenText?.second, placeholder = whenText == null)
                        Hairline()
                        ValueRow("Remind me", trailing = { CoveSwitch(remind, { remind = it }) })
                    }
                }
                val text = CoveType.Button.copy(fontSize = 16.sp)
                val ready = title.isNotBlank()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        "Add",
                        {
                            if (!ready) return@PillButton
                            if (isEvent) onAddEvent(buildEvent(title.trim(), day, start, end, repeat, remindBefore, place.trim())) else onAddTodo(title, categoryId, dueAt, remind)
                            close()
                        },
                        Modifier.weight(1f).alpha(if (ready) 1f else 0.4f), height = 56.dp, textStyle = text,
                    )
                    PillButton(
                        "Cancel", close, Modifier.width(104.dp), kind = ButtonKind.Secondary,
                        container = Cove.colors.canvas, height = 56.dp, textStyle = text.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}

private fun rangeText(start: Int, end: Int): String {
    val s = clockText(start)
    val e = clockText(end)
    return if (s.suffix == e.suffix) "${s.digits} – ${e.digits}${e.suffix}" else "${s.digits}${s.suffix} – ${e.digits}${e.suffix}"
}

private fun buildEvent(title: String, day: LocalDate, start: Int, end: Int, repeat: String, remind: Int?, place: String) =
    EventEntity(
        id = newId(),
        title = title,
        startAt = LocalDateTime.of(day, LocalTime.of(start / 60, start % 60)).toEpochMillis(),
        endAt = LocalDateTime.of(day, LocalTime.of(end / 60, end % 60)).toEpochMillis(),
        place = place.ifEmpty { null },
        repeat = repeat,
        remindBeforeMin = remind,
    )

@Composable
private fun PlaceRow(place: String, onChange: (String) -> Unit) {
    val c = Cove.colors
    val style = CoveType.Body.copy(fontSize = 16.sp, textAlign = TextAlign.End, color = c.ink)
    Hairline()
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CoveText("Where", style = CoveType.Body.copy(fontSize = 16.sp), color = c.muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (place.isEmpty()) CoveText("Add a place", style = style, color = c.placeholder)
            BasicTextField(
                place, onChange, Modifier.fillMaxWidth(),
                textStyle = style, singleLine = true, cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
        }
        CoveIcon(CoveIcons.ChevronRight, c.tail, size = 14.dp)
    }
}
