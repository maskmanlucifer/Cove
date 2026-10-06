package app.cove.companion.feature.nudges

import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.TodoEntity
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeLogicTest {
    private val utc = ZoneId.of("UTC")

    private fun ms(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m).atZone(utc).toInstant().toEpochMilli()
    private fun todo(title: String, due: Long? = null, remind: Boolean = false, done: Boolean = false) =
        TodoEntity(title, null, title, due, remind, done)

    @Test
    fun nextBundleWalksThroughTheDay() {
        assertEquals(ms(6, 9), NudgeMath.nextBundleAt(ms(6, 7), utc))
        assertEquals(ms(6, 13), NudgeMath.nextBundleAt(ms(6, 9), utc))
        assertEquals(ms(6, 18), NudgeMath.nextBundleAt(ms(6, 13, 1), utc))
        assertEquals(ms(7, 9), NudgeMath.nextBundleAt(ms(6, 18), utc))
    }

    @Test
    fun reminderFireTimeSubtractsLeadAndDropsPast() {
        assertEquals(ms(6, 10, 30), NudgeMath.reminderFireAt(ms(6, 11), 30, ms(6, 10)))
        assertNull(NudgeMath.reminderFireAt(ms(6, 11), 30, ms(6, 10, 45)))
        assertNull(NudgeMath.reminderFireAt(ms(6, 11), 0, ms(6, 11)))
        assertEquals(ms(6, 11), NudgeMath.reminderFireAt(ms(6, 11), -5, ms(6, 10)))
    }

    @Test
    fun repeatingEventPicksNextOccurrence() {
        val start = ms(1, 11)
        assertEquals(ms(7, 11), NudgeMath.nextOccurrence(start, "daily", 30, ms(6, 12), utc))
        assertEquals(ms(6, 11), NudgeMath.nextOccurrence(start, "daily", 30, ms(6, 10), utc))
        assertEquals(ms(8, 11), NudgeMath.nextOccurrence(start, "weekly", 30, ms(6, 10), utc))
        assertNull(NudgeMath.nextOccurrence(start, "none", 30, ms(6, 10), utc))
    }

    @Test
    fun quietHoursDeferAcrossMidnight() {
        val quiet = QuietHours(22 * 60, 7 * 60)
        assertEquals(ms(7, 7), NudgeMath.deferPastQuiet(ms(6, 23), quiet, utc))
        assertEquals(ms(6, 7), NudgeMath.deferPastQuiet(ms(6, 3), quiet, utc))
        assertEquals(ms(6, 12), NudgeMath.deferPastQuiet(ms(6, 12), quiet, utc))
        assertEquals(ms(6, 23), NudgeMath.deferPastQuiet(ms(6, 23), null, utc))
    }

    @Test
    fun plannerSkipsDoneUnflaggedAndPast() {
        val now = ms(6, 10)
        val plans = ReminderPlanner.plan(
            listOf(
                todo("a", ms(6, 13), remind = true), todo("b", ms(6, 13)), todo("c", ms(6, 13), true, done = true),
                todo("d", ms(6, 9), remind = true),
            ),
            listOf(EventEntity("e", "Coffee", ms(6, 11), null, remindBeforeMin = 30), EventEntity("f", "Quiet", ms(6, 12), null, remindBeforeMin = null)),
            now, zone = utc,
        )
        assertEquals(listOf("event:e" to ms(6, 10, 30), "todo:a" to ms(6, 13)), plans.map { it.key to it.fireAt })
    }

    @Test
    fun bundleWordingAndCounts() {
        val items = listOf(NudgeItem("Reply to Priya", ms(6, 13)), NudgeItem("Water the plants", null), NudgeItem("Call mum", ms(6, 18)))
        val b = NudgeContent.bundle(items, 13, false)!!
        assertEquals("3 for this afternoon", b.title)
        assertEquals("Reply to Priya, water the plants, call mum", b.body)
        val five = items + NudgeItem("Dish soap", null) + NudgeItem("Card", null)
        assertEquals("Reply to Priya, water the plants, call mum, +2 more", NudgeContent.bundle(five, 18, false)!!.body)
        assertEquals("5 for this evening", NudgeContent.bundle(five, 18, false)!!.title)
        assertEquals("One thing today", NudgeContent.bundle(items, 9, true)!!.title)
        assertNull(NudgeContent.bundle(emptyList(), 9, false))
    }

    @Test
    fun bundleItemsOnlyLaterTodayAndUndatedInMorning() {
        val now = ms(6, 13)
        val todos = listOf(todo("past", ms(6, 9)), todo("later", ms(6, 18)), todo("tomorrow", ms(7, 9)), todo("any"), todo("done", ms(6, 15), done = true))
        val events = listOf(EventEntity("e", "Dinner", ms(6, 19), null))
        assertEquals(listOf("later", "Dinner"), NudgeContent.items(todos, events, now, false, utc).map { it.title })
        assertTrue(NudgeContent.items(todos, events, now, true, utc).map { it.title }.contains("any"))
    }
}
