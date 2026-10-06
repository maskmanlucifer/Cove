package app.cove.companion.feature.money

import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.local.entity.ExpenseEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyMathTest {
    private val today = LocalDate.of(2026, 10, 5)

    private fun expense(paise: Long, day: Int, kind: String = "spent", hour: Int = 9) = ExpenseEntity(
        id = "e$day$paise", amountPaise = paise, kind = kind, categoryId = null,
        spentAt = LocalDateTime.of(today.withDayOfMonth(day), LocalTime.of(hour, 0)).toEpochMillis(),
    )

    @Test fun wholeRupeesRoundsHalfUp() {
        assertEquals("₹11,580", MoneyMath.wholeRupees(1_157_950))
        assertEquals("₹210", MoneyMath.wholeRupees(21_000))
    }

    @Test fun daysToGoCountsAfterToday() {
        assertEquals(26, MoneyMath.daysToGo(today))
        assertEquals(0, MoneyMath.daysToGo(LocalDate.of(2026, 10, 31)))
    }

    @Test fun perDayMatchesDesign() {
        assertEquals(6_400L, MoneyMath.perDay(166_000, 26))
        assertNull(MoneyMath.perDay(-1, 26))
        assertEquals(10_000L, MoneyMath.perDay(10_000, 0))
    }

    @Test fun overBudgetCopyIsGentle() {
        assertEquals("₹210 over · no rush", MoneyMath.overNote(221_000, 200_000))
        assertEquals(" · ₹210 over, no rush", MoneyMath.overInline(221_000, 200_000))
        assertNull(MoneyMath.overNote(200_000, 200_000))
    }

    @Test fun alertFiresOnlyWhenCrossingEighty() {
        assertTrue(MoneyMath.crossedAlert(70_000, 82_000, 100_000, true))
        assertFalse(MoneyMath.crossedAlert(82_000, 90_000, 100_000, true))
        assertFalse(MoneyMath.crossedAlert(70_000, 82_000, 100_000, false))
        assertFalse(MoneyMath.crossedAlert(0, 10, 0, true))
    }

    @Test fun carryOverOnlyKeepsWhatWasLeft() {
        assertEquals(30_000L, MoneyMath.carriedOver(100_000, 70_000, true))
        assertEquals(0L, MoneyMath.carriedOver(100_000, 130_000, true))
        assertEquals(0L, MoneyMath.carriedOver(100_000, 70_000, false))
    }

    @Test fun barsCoverWholeMonth() {
        val bars = MoneyMath.dailyBars(
            listOf(expense(10_000, 1), expense(5_000, 2), expense(5_000, 2), expense(99, 3, kind = "received")),
            today,
        )
        assertEquals(31, bars.size)
        assertEquals(BarKind.Spent, bars[0].kind)
        assertEquals(1f, bars[0].fraction, 0.001f)
        assertEquals(1f, bars[1].fraction, 0.001f)
        assertEquals(BarKind.Quiet, bars[2].kind)
        assertEquals(BarKind.Quiet, bars[4].kind)
        assertEquals(BarKind.Future, bars[5].kind)
    }

    @Test fun groupsByDayNewestFirst() {
        val groups = MoneyMath.groupByDay(
            listOf(expense(1, 4, hour = 20), expense(2, 5, hour = 13), expense(3, 5, hour = 18), expense(4, 1)),
            today,
        )
        assertEquals(listOf("Today", "Yesterday", "Thu, 1 Oct"), groups.map { it.label })
        assertEquals(listOf(3L, 2L), groups[0].items.map { it.amountPaise })
    }

    @Test fun whenAndMethodLines() {
        val e = expense(1, 5, hour = 18).copy(paidWith = "UPI")
        assertEquals("Today, 6:00 pm", MoneyMath.whenText(e.spentAt, today))
        assertEquals("UPI · 6:00 pm", MoneyMath.methodLine(e))
        assertEquals("By voice · 6:00 pm", MoneyMath.methodLine(e.copy(source = "voice")))
    }

    @Test fun soFarLine() {
        assertEquals("Food so far: ₹7,340 of ₹9,000.", MoneyMath.soFarLine("Food", 734_000, 900_000))
    }
}
