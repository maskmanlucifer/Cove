package app.cove.companion.feature.widgets

import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.TodoEntity
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDataTest {
    private val utc = ZoneId.of("UTC")
    private fun ms(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(utc).toInstant().toEpochMilli()

    @Test
    fun nextCardAndMoneyMatchTheFrame() {
        val snap = WidgetData.build(
            ms(6, 10, 35),
            listOf(TodoEntity("a", null, "Reply to Priya", ms(6, 13)), TodoEntity("b", null, "Water the plants", null), TodoEntity("c", null, "Old", null, done = true, doneAt = ms(5, 9))),
            listOf(EventEntity("e", "Coffee with Jo", ms(6, 11), null)),
            emptyList(),
            listOf(ExpenseEntity("x", 84_000, "spent", null, spentAt = ms(6, 9)), ExpenseEntity("y", 100_000, "spent", null, spentAt = ms(2, 9))),
            listOf(ExpenseCategoryEntity("f", "Food", "spending", 1_284_000)),
            utc,
        )
        assertEquals(NextCard("in 25 min", "11:00", " am", "Coffee with Jo"), snap.next)
        assertEquals(2, snap.tasksLeft)
        assertEquals(84_000L, snap.spentTodayPaise)
        assertEquals(1_100_000L, snap.leftThisMonthPaise)
    }

    @Test
    fun emptyDayHasNoNextAndNoBudget() {
        val snap = WidgetData.build(ms(6, 10), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), utc)
        assertNull(snap.next)
        assertNull(snap.leftThisMonthPaise)
    }
}
