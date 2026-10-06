package app.cove.companion.feature.today

import app.cove.companion.data.local.entity.TodoEntity
import org.junit.Test
import org.junit.Assert.assertEquals

class TodayRowsTest {
    private fun todo(id: String, done: Boolean = false, due: Long? = null) = TodoEntity(id, null, id, due, done = done)

    @Test
    fun countsEveryOpenTodoNotJustTheRowsShown() {
        val r = todayRows(listOf(todo("a"), todo("b"), todo("c"), todo("d"), todo("e")), emptySet(), emptyMap(), false, 0, 100)
        assertEquals(listOf("a", "b", "c"), r.rows.map { it.id })
        assertEquals(5, r.openCount)
        assertEquals(2, r.more)
    }

    @Test
    fun justTickedRowStaysInItsPlaceUntilUnpinned() {
        val list = listOf(todo("b"), todo("c"), todo("d"), todo("a", done = true))
        val r = todayRows(list, emptySet(), mapOf("a" to 0), false, 0, 100)
        assertEquals(listOf("a", "b", "c"), r.rows.map { it.id })
        assertEquals(3, r.openCount)
        assertEquals(listOf("b", "c", "d"), todayRows(list, emptySet(), emptyMap(), false, 0, 100).rows.map { it.id })
    }

    @Test
    fun doneRowsShowOnlyInTheEveningRecap() {
        val list = listOf(todo("a", done = true))
        assertEquals(0, todayRows(list, emptySet(), emptyMap(), false, 0, 100).rows.size)
        assertEquals(1, todayRows(list, emptySet(), emptyMap(), true, 0, 100).rows.size)
    }

    @Test
    fun todosDueOnOtherDaysAreLeftOut() {
        val r = todayRows(listOf(todo("a", due = 500), todo("b", due = 50)), emptySet(), emptyMap(), false, 0, 100)
        assertEquals(listOf("b"), r.rows.map { it.id })
        assertEquals(1, r.openCount)
    }
}
