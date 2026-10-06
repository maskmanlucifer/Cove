package app.cove.companion.feature.today

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodPromptTest {
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)

    @Test
    fun hiddenInTheDaytime() {
        assertFalse(MoodPrompt.visible(at(6, 10), null))
        assertFalse(MoodPrompt.visible(at(6, 19, 59), null))
        assertFalse(MoodPrompt.visible(at(7, 4), null))
    }

    @Test
    fun shownFromEightPmUntilFourAm() {
        assertTrue(MoodPrompt.visible(at(6, 20), null))
        assertTrue(MoodPrompt.visible(at(6, 23, 59), null))
        assertTrue(MoodPrompt.visible(at(7, 3, 59), null))
    }

    @Test
    fun afterMidnightBelongsToTheSameDay() {
        assertEquals(LocalDate.of(2026, 10, 6), MoodPrompt.dayKey(at(7, 2)))
        assertFalse(MoodPrompt.visible(at(7, 2), LocalDate.of(2026, 10, 6)))
    }

    @Test
    fun answeringHidesItForTheRestOfThatDayOnly() {
        val answered = LocalDate.of(2026, 10, 6)
        assertFalse(MoodPrompt.visible(at(6, 21), answered))
        assertTrue(MoodPrompt.visible(at(7, 21), answered))
    }
}
