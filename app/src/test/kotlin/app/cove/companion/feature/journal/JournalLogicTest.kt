package app.cove.companion.feature.journal

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalLogicTest {
    @Test fun septemberStartsOnTuesday() {
        val grid = buildMonthGrid(YearMonth.of(2026, 9), setOf(LocalDate.of(2026, 9, 4).toEpochDay()))
        assertEquals(1, grid.leading)
        assertEquals(30, grid.cells.size)
        assertEquals(1, grid.entryDays)
        assertEquals(true, grid.cells[3].hasEntry)
    }

    @Test fun mondayStartHasNoLeading() = assertEquals(0, buildMonthGrid(YearMonth.of(2026, 6), emptySet()).leading)

    @Test fun entriesText() {
        assertEquals("No entries", entriesText(0))
        assertEquals("1 entry", entriesText(1))
        assertEquals("14 entries", entriesText(14))
    }

    @Test fun durationAndRoutes() {
        assertEquals("0:42", formatDuration(42_900))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals(LocalDate.of(2026, 9, 1), newEntryDay("new-" + LocalDate.of(2026, 9, 1).toEpochDay()))
        assertNull(newEntryDay("new"))
        assertNull(newEntryDay("3f2a-uuid"))
    }
}
