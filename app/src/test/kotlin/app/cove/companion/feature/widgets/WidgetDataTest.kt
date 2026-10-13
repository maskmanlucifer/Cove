package app.cove.companion.feature.widgets

import app.cove.companion.data.local.entity.EventEntity
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
    fun nextCardMatchesTheFrame() {
        val snap = WidgetData.build(
            ms(6, 10, 35),
            listOf(TodoEntity("a", null, "Reply to Priya", ms(6, 13)), TodoEntity("b", null, "Water the plants", null), TodoEntity("c", null, "Old", null, done = true, doneAt = ms(5, 9))),
            listOf(EventEntity("e", "Coffee with Jo", ms(6, 11), null)),
            emptyList(),
            utc,
        )
        assertEquals(NextCard("in 25 min", "11:00", " am", "Coffee with Jo"), snap.next)
    }

    @Test
    fun emptyDayHasNoNext() {
        val snap = WidgetData.build(ms(6, 10), emptyList(), emptyList(), emptyList(), utc)
        assertNull(snap.next)
    }
}
