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
    /** A day with several entries that the user tapped; its entries are listed in place of Recent. */
    val selected: LocalDate? = null,
    val selectedEntries: List<RecentEntry> = emptyList(),
)

private val dayMeta = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)
private val dayMetaMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "Sun 27 · calm"; the month is added ("Sun 27 Sep") for an entry outside [today]'s month. */
internal fun recentMeta(day: LocalDate, mood: String?, today: LocalDate): String {
    val label = day.format(if (YearMonth.from(day) == YearMonth.from(today)) dayMeta else dayMetaMonth)
    return if (mood.isNullOrBlank()) label else "$label · $mood"
}

/** Month calendar with entry days marked, the entry count and the two latest entries (all of a day with several). */
class JournalViewModel(private val c: AppContainer) : ViewModel() {
    private val today = c.clock.now().toLocalDate()
    private val month = MutableStateFlow(YearMonth.from(today))
    private var idsByDay: Map<Long, List<String>> = emptyMap()
    private val selected = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<JournalMonthState> = combine(c.journal.entries, month, selected) { entries, m, pick ->
        idsByDay = entries.groupBy { it.day }.mapValues { (_, list) -> list.map { it.id } }
        val inMonth = entries.filter { YearMonth.from(LocalDate.ofEpochDay(it.day)) == m }
        JournalMonthState(
            today = today,
            grid = buildMonthGrid(m, inMonth.map { it.day }.toSet()),
            monthLabel = m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (m.year != today.year) " ${m.year}" else "",
            entryCount = inMonth.size,
            recent = entries.take(2).map { it.toRecent() },
            empty = entries.isEmpty(),
            selected = pick,
            selectedEntries = pick?.let { d -> entries.filter { it.day == d.toEpochDay() }.map { it.toRecent() } }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalMonthState(today))

    /** Moves the calendar by [delta] months. */
    fun shift(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    /**
     * What a tap on [date] does: opens its only entry, starts a new one for an empty day, or (with several entries)
     * lists them below the calendar and returns null. Tapping the listed day again hides the list.
     */
    fun tapDay(date: LocalDate): String? {
        val ids = idsByDay[date.toEpochDay()].orEmpty()
        selected.value = null
        return when {
            ids.size == 1 -> Routes.journalEdit(ids.single())
            ids.size > 1 -> {
                if (state.value.selected != date) selected.value = date
                null
            }
            date <= today -> journalNewRoute(date)
            else -> null
        }
    }

    private fun JournalEntryEntity.toRecent(): RecentEntry {
        return RecentEntry(id, recentMeta(LocalDate.ofEpochDay(day), mood, today), displayTitle())
    }
}

/** Title shown in lists: the title, else the first line of the body, else "Untitled". */
fun JournalEntryEntity.displayTitle(): String =
    title.trim().ifEmpty { body.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }.orEmpty() }
        .ifEmpty { mood?.takeIf { it.isNotBlank() }?.let { "Feeling $it" }.orEmpty() }.ifEmpty { "Untitled" }
