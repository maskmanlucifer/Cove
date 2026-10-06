package app.cove.companion.feature.plan

import app.cove.companion.data.local.entity.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanLogicTest {
    private fun todo(id: String, cat: String?, sort: Int, done: Boolean = false) =
        TodoEntity(id, cat, id, sort = sort, done = done)

    private fun item(kind: ItemKind, min: Int, end: Int? = null, title: String = "x") =
        ScheduleItem(kind, min, end, title)

    @Test
    fun nowLineSitsBeforeFirstFutureItemAndNextEventIsCard() {
        val rows = buildTimeline(
            listOf(item(ItemKind.Alarm, 390), item(ItemKind.Event, 660, 705, "Coffee"), item(ItemKind.Event, 840, 960, "Deep")),
            nowMinutes = 635,
        )
        assertTrue(rows[1] is TimelineRow.Now)
        val coffee = rows[2] as TimelineRow.Entry
        assertTrue(coffee.card)
        assertEquals("45 min", coffee.detail)
        val deep = rows[3] as TimelineRow.Entry
        assertEquals("until 4", deep.detail)
        assertTrue((rows[0] as TimelineRow.Entry).past)
    }

    @Test
    fun durationText() {
        assertEquals("1 h 30 min", durationText(90))
        assertEquals("2 h", durationText(120))
    }

    @Test
    fun moveWithinCategoryRenumbers() {
        val all = listOf(todo("a", "c", 0), todo("b", "c", 1), todo("c", "c", 2))
        val out = moveTodo(all, "c", "c", 0).associateBy { it.id }
        assertEquals(0, out.getValue("c").sort)
        assertEquals(1, out.getValue("a").sort)
        assertEquals(2, out.getValue("b").sort)
    }

    @Test
    fun moveAcrossCategoriesChangesCategoryAndClosesGap() {
        val all = listOf(todo("a", "x", 0), todo("b", "x", 1), todo("c", "y", 0))
        val out = moveTodo(all, "a", "y", Int.MAX_VALUE).associateBy { it.id }
        assertEquals("y", out.getValue("a").categoryId)
        assertEquals(1, out.getValue("a").sort)
        assertEquals(0, out.getValue("b").sort)
    }

    @Test
    fun dropIndexCountsCentersAbove() {
        assertEquals(2, dropIndex(listOf(10f, 20f, 30f), 25f))
    }

    @Test
    fun movedList() {
        assertEquals(listOf(2, 1, 3), listOf(1, 2, 3).moved(1, 0))
    }
}
