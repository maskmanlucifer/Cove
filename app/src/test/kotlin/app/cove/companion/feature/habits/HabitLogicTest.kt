package app.cove.companion.feature.habits

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitLogicTest {
    private val today = LocalDate.of(2026, 9, 27)

    @Test fun lastSevenIsOldestFirstEndingToday() {
        val logged = setOf(0L, 1, 3, 6).map { today.toEpochDay() - 6 + it }.toSet()
        val days = lastSevenDays(today, logged)
        assertEquals(listOf(true, true, false, true, false, false, true), days)
        assertEquals("4 of 7 days", countText(days))
    }

    @Test fun oldLogsDoNotCountAndNothingResets() {
        val days = lastSevenDays(today, setOf(today.toEpochDay() - 7, today.toEpochDay() - 30))
        assertEquals("0 of 7 days", countText(days))
        assertEquals(7, days.size)
    }

    @Test fun dueDays() {
        val mwf = 0b0010101
        assertTrue(isDue("days", mwf, LocalDate.of(2026, 9, 28)))
        assertFalse(isDue("days", mwf, today))
        assertTrue(isDue("daily", 0, today))
    }
}
