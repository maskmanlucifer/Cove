package app.cove.companion.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.navigation.Routes
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A line in the recent list: "Sun 27 · calm" over the entry's title. */
data class RecentEntry(val id: String, val meta: String, val title: String)

data class JournalMonthState(
    val today: LocalDate = LocalDate.now(),
    val grid: MonthGrid = buildMonthGrid(YearMonth.now(), emptySet()),
    val monthLabel: String = "",
    val entryCount: Int = 0,
    val recent: List<RecentEntry> = emptyList(),
    val empty: Boolean = true,
)

private val dayMeta = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)

/** Month calendar with entry days marked, the entry count and the two latest entries. */
class JournalViewModel(private val c: AppContainer) : ViewModel() {
    private val today = c.clock.now().toLocalDate()
    private val month = MutableStateFlow(YearMonth.from(today))
    private var latestByDay: Map<Long, String> = emptyMap()

    val state: StateFlow<JournalMonthState> = combine(c.journal.entries, month) { entries, m ->
        latestByDay = entries.groupBy { it.day }.mapValues { it.value.first().id }
        val inMonth = entries.filter { YearMonth.from(LocalDate.ofEpochDay(it.day)) == m }
        JournalMonthState(
            today = today,
            grid = buildMonthGrid(m, inMonth.map { it.day }.toSet()),
            monthLabel = m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (m.year != today.year) " ${m.year}" else "",
            entryCount = inMonth.size,
            recent = entries.take(2).map { it.toRecent() },
            empty = entries.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalMonthState(today))

    /** Moves the calendar by [delta] months. */
    fun shift(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    /** Route for the entry behind a tapped [date]: its latest entry, or a new one for a day that has none yet. */
    fun routeFor(date: LocalDate): String? {
        val existing = latestByDay[date.toEpochDay()]
        return when {
            existing != null -> Routes.journalEdit(existing)
            date <= today -> journalNewRoute(date)
            else -> null
        }
    }

    private fun JournalEntryEntity.toRecent(): RecentEntry {
        val day = LocalDate.ofEpochDay(day).format(dayMeta)
        return RecentEntry(id, if (mood.isNullOrBlank()) day else "$day · $mood", displayTitle())
    }
}

/** Title shown in lists: the title, else the first line of the body, else "Untitled". */
fun JournalEntryEntity.displayTitle(): String =
    title.trim().ifEmpty { body.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }.orEmpty() }.ifEmpty { "Untitled" }
