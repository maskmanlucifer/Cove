package app.cove.companion.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.appViewModel
import app.cove.companion.core.longLabel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.UndoHost
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val WeekdayStyle = CoveType.Label.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal)

/** Journal tab: month calendar with entry days marked, the entry count and the latest entries. */
@Composable
fun JournalScreen(nav: Nav) {
    val vm = appViewModel { JournalViewModel(it) }
    val s by vm.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .coveTopInset()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = DockClearance),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CoveText("Journal", style = CoveType.Title)
                PillButton("Write", { nav.go(Routes.journalEdit()) })
            }
            MonthCard(s, onShift = vm::shift) { date -> vm.tapDay(date)?.let(nav.go) }
            if (s.empty) {
                CoveText("Nothing here yet. Write a few lines whenever you like.", style = CoveType.Meta, color = Cove.colors.muted)
            } else if (s.selected != null) {
                DayEntries(s.selected!!, s.selectedEntries, nav)
            } else {
                EntryList(s.recent, nav)
            }
        }
        UndoHost("journal", Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 112.dp))
    }
}

@Composable
private fun EntryList(entries: List<RecentEntry>, nav: Nav) {
    Column {
        entries.forEachIndexed { i, e ->
            if (i > 0) Hairline()
            Column(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).pressable({ nav.go(Routes.journalEdit(e.id)) }),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                CoveText(e.meta, style = CoveType.Meta, color = Cove.colors.muted)
                CoveText(e.title, style = CoveType.BodyMedium, maxLines = 1)
            }
        }
    }
}

/** Every entry of a day that has several, plus a way to add one more. */
@Composable
private fun DayEntries(day: LocalDate, entries: List<RecentEntry>, nav: Nav) {
    Column {
        CoveText(day.longLabel() + " · " + entriesText(entries.size), Modifier.padding(bottom = 4.dp), style = CoveType.Meta, color = Cove.colors.muted)
        EntryList(entries, nav)
        Hairline()
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable({ nav.go(journalNewRoute(day)) }), contentAlignment = Alignment.CenterStart) {
            CoveText("Write another", style = CoveType.BodyMedium, color = Cove.colors.muted)
        }
    }
}

@Composable
private fun MonthCard(s: JournalMonthState, onShift: (Long) -> Unit, onDay: (LocalDate) -> Unit) {
    val c = Cove.colors
    var drag = 0f
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card, CoveShapes.Card)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag > 80) onShift(-1) else if (drag < -80) onShift(1) },
                ) { _, amount -> drag += amount }
            }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CoveText(s.monthLabel, style = CoveType.BodyMedium)
            CoveText(entriesText(s.entryCount), style = CoveType.Meta, color = c.muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach {
                    Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                        CoveText(it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = WeekdayStyle, color = c.muted)
                    }
                }
            }
            val slots = s.grid.leading + s.grid.cells.size
            repeat((slots + 6) / 7) { week ->
                // Rows keep the design's 40 dp but grow with the font size so large text never touches the next row.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                    repeat(7) { col ->
                        val cell = s.grid.cells.getOrNull(week * 7 + col - s.grid.leading)
                        Box(Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 40.dp)) {
                            if (cell != null) DayCell(cell, cell.date == s.today || cell.date == s.selected) { onDay(cell.date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(cell: MonthCell, selected: Boolean, onClick: () -> Unit) {
    val c = Cove.colors
    val ink = if (selected) c.onInk else c.ink
    Column(
        Modifier
            .fillMaxSize()
            .background(if (selected) c.ink else Color.Transparent, RoundedCornerShape(12.dp))
            .pressable(onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = cell.date.longLabel() + if (cell.hasEntry) ", has an entry" else ""
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        CoveText(cell.day.toString(), style = CoveType.Meta, color = if (cell.hasEntry || selected) ink else c.placeholder)
        Box(
            Modifier.size(4.dp).background(if (cell.hasEntry) ink else Color.Transparent, CoveShapes.Circle),
        )
    }
}

/** "14 entries", "1 entry" or "No entries". */
internal fun entriesText(n: Int) = when (n) {
    0 -> "No entries"
    1 -> "1 entry"
    else -> "$n entries"
}
