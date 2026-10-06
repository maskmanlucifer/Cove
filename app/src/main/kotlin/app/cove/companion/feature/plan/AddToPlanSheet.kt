package app.cove.companion.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
 *
 * With [editing] set it becomes the event editor (see [EventEditSheet]): fields start from that event,
 * the To-do/Event switch is hidden and the buttons are Save and Delete.
 */
@Composable
fun AddToPlanSheet(
    defaults: AddDefaults,
    categories: List<TodoCategoryEntity>,
    now: Long,
    onAddTodo: (title: String, categoryId: String?, dueAt: Long?, remind: Boolean) -> Unit,
    onAddEvent: (EventEntity) -> Unit,
    onDismiss: () -> Unit,
    editing: EventEntity? = null,
    onDeleteEvent: (EventEntity) -> Unit = {},
) {
    val nowTime = now.toLocalDateTime()
    val startDefault = ((nowTime.hour * 60 + nowTime.minute) / 60 + 1).coerceAtMost(22) * 60
    val from = remember(editing?.id) { editing?.let(::EventForm) }
    var isEvent by rememberSaveable { mutableStateOf(defaults.event || editing != null) }
    var title by rememberSaveable { mutableStateOf(editing?.title ?: defaults.title) }
    var page by rememberSaveable { mutableStateOf(AddPage.Main) }
    var day by rememberSaveable { mutableStateOf(from?.day ?: now.toLocalDate()) }
    var start by rememberSaveable { mutableIntStateOf(from?.start ?: startDefault) }
    var end by rememberSaveable { mutableIntStateOf(from?.end ?: (startDefault + 45)) }
    var repeat by rememberSaveable { mutableStateOf(editing?.repeat ?: "none") }
    var remindBefore by rememberSaveable { mutableStateOf<Int?>(if (editing != null) editing.remindBeforeMin else 30) }
    var place by rememberSaveable { mutableStateOf(editing?.place.orEmpty()) }
    var categoryId by rememberSaveable { mutableStateOf(categories.firstOrNull()?.id) }
    var dueAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var remind by rememberSaveable { mutableStateOf(false) }

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
                if (editing == null) Segmented(
                    listOf("To-do", "Event"), if (isEvent) 1 else 0, { isEvent = it == 1 },
                    Modifier.fillMaxWidth(), height = 40.dp, fillWidth = true,
                )
                TitleField(
                    title, { title = it }, if (isEvent) "What’s happening?" else "What do you need to do?",
                    onDone = {}, autofocus = defaults.autofocus,
                )
                Column {
                    if (isEvent) {
                        SheetRow("Day", day.dayLabel(), { page = AddPage.Day }, if (day < now.toLocalDate()) " · past" else null)
                        SheetRow("Time", rangeText(start, end), { page = AddPage.Time })
                        SheetRow("Repeat", RepeatOptions.first { it.first == repeat }.second, { page = AddPage.Repeat })
                        SheetRow("Remind me", remindText(remindBefore), { page = AddPage.Remind })
                        PlaceRow(place) { place = it }
                    } else {
                        SheetRow("Category", categories.firstOrNull { it.id == categoryId }?.name ?: "None", { page = AddPage.Category })
                        val whenText = whenParts(dueAt, now)
                        SheetRow("When", whenText?.first ?: "Not set", { page = AddPage.When }, whenText?.second, placeholder = whenText == null)
                        Hairline()
                        ValueRow("Remind me", trailing = {
                            if (dueAt == null) CoveText("Set a time first", style = CoveType.Meta, color = Cove.colors.muted)
                            CoveSwitch(remind && dueAt != null, { remind = it }, label = "Remind me", enabled = dueAt != null)
                        })
                    }
                }
                val text = CoveType.Button.copy(fontSize = 16.sp)
                val ready = title.isNotBlank()
                val primary: @Composable (Modifier) -> Unit = { m ->
                    PillButton(
                        if (editing != null) "Save" else "Add",
                        {
                            if (!ready) return@PillButton
                            if (isEvent) onAddEvent(buildEvent(editing, title.trim(), day, start, end, repeat, remindBefore, place.trim())) else onAddTodo(title.trim(), categoryId, dueAt, remind && dueAt != null)
                            close()
                        },
                        m.alpha(if (ready) 1f else 0.4f), height = 56.dp, textStyle = text,
                    )
                }
                if (editing != null) {
                    primary(Modifier.fillMaxWidth())
                    PillButton(
                        "Delete event", { onDeleteEvent(editing); close() }, Modifier.fillMaxWidth(),
                        kind = ButtonKind.Destructive, height = 48.dp, textStyle = text,
                    )
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    primary(Modifier.weight(1f))
                    PillButton(
                        "Cancel", close, Modifier.widthIn(min = 104.dp), kind = ButtonKind.Secondary,
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

/** An event's day and start/end minutes, split out so the sheet can edit them separately. */
private class EventForm(e: EventEntity) {
    private val s = e.startAt.toLocalDateTime()
    val day: LocalDate = s.toLocalDate()
    val start: Int = s.hour * 60 + s.minute
    val end: Int = e.endAt?.toLocalDateTime()?.let { it.hour * 60 + it.minute } ?: (start + 45)
}

private fun buildEvent(base: EventEntity?, title: String, day: LocalDate, start: Int, end: Int, repeat: String, remind: Int?, place: String) =
    (base ?: EventEntity(newId(), title, 0, null)).copy(
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
