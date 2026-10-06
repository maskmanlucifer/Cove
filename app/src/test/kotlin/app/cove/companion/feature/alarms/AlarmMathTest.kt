package app.cove.companion.feature.alarms

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmMathTest {
    private val utc = ZoneId.of("UTC")
    private val ny = ZoneId.of("America/New_York")

    private fun ms(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    // 2026-10-06 is a Tuesday.
    @Test
    fun onceLaterTodayFiresToday() {
        val now = ms(utc, 2026, 10, 6, 5, 0)
        assertEquals(ms(utc, 2026, 10, 6, 6, 30), nextFireMillis(390, 0, now, utc))
    }

    @Test
    fun onceAlreadyPastFiresTomorrow() {
        val now = ms(utc, 2026, 10, 6, 7, 0)
        assertEquals(ms(utc, 2026, 10, 7, 6, 30), nextFireMillis(390, 0, now, utc))
    }

    @Test
    fun exactlyNowIsNotNext() {
        val now = ms(utc, 2026, 10, 6, 6, 30)
        assertEquals(ms(utc, 2026, 10, 7, 6, 30), nextFireMillis(390, 0, now, utc))
    }

    @Test
    fun weekdaysSkipWeekend() {
        val fridayLate = ms(utc, 2026, 10, 9, 7, 0)
        assertEquals(ms(utc, 2026, 10, 12, 6, 30), nextFireMillis(390, AlarmDays.WEEKDAYS, fridayLate, utc))
    }

    @Test
    fun sundayBitAndSingleDay() {
        val now = ms(utc, 2026, 10, 6, 12, 0)
        assertEquals(ms(utc, 2026, 10, 11, 8, 0), nextFireMillis(480, 1 shl 6, now, utc))
        assertEquals(ms(utc, 2026, 10, 13, 6, 30), nextFireMillis(390, 1 shl 1, now, utc))
    }

    @Test
    fun springForwardGapShiftsForward() {
        val now = ms(ny, 2026, 3, 7, 12, 0)
        val fire = nextFireMillis(2 * 60 + 30, AlarmDays.ALL, now, ny)
        assertEquals(ms(ny, 2026, 3, 8, 3, 30), fire)
    }

    @Test
    fun fallBackOverlapPicksFirstOccurrence() {
        val now = ms(ny, 2026, 11, 1, 0, 0)
        val fire = nextFireMillis(90, 0, now, ny)
        assertEquals(ms(ny, 2026, 11, 1, 1, 30), fire)
        assertEquals(90 * 60_000L, fire - now)
    }

    @Test
    fun describeAndSubtitle() {
        assertEquals("once", AlarmDays.describe(0))
        assertEquals("every day", AlarmDays.describe(127))
        assertEquals("weekdays", AlarmDays.describe(AlarmDays.WEEKDAYS))
        assertEquals("Mon, Wed", AlarmDays.describe(0b101))
        assertEquals("Wake up · weekdays", alarmSubtitle("Wake up", AlarmDays.WEEKDAYS))
        assertEquals("Weekends", alarmSubtitle("Weekends", AlarmDays.WEEKEND))
        assertEquals("Nap · once", alarmSubtitle("Nap", 0))
        assertEquals("Weekdays", alarmSubtitle("", AlarmDays.WEEKDAYS))
    }

    @Test
    fun flipAndHas() {
        val m = AlarmDays.flip(AlarmDays.WEEKDAYS, 5)
        assertTrue(AlarmDays.has(m, java.time.DayOfWeek.SATURDAY))
        assertFalse(AlarmDays.has(AlarmDays.flip(m, 5), java.time.DayOfWeek.SATURDAY))
    }

    @Test
    fun untilTextFormats() {
        assertEquals("8 h 49 min", untilText((8 * 60 + 49) * 60_000L))
        assertEquals("49 min", untilText(49 * 60_000L))
        assertEquals("3 h", untilText(180 * 60_000L))
        assertEquals("1 d 2 h", untilText((26 * 60) * 60_000L))
        assertEquals("1 min", untilText(1_000))
    }
}
