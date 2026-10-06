package app.cove.companion.feature.training

import app.cove.companion.feature.training.engine.Axis
import app.cove.companion.feature.training.engine.ChartMath
import app.cove.companion.feature.training.engine.ChartRange
import app.cove.companion.feature.training.engine.DoneSession
import app.cove.companion.feature.training.engine.LiftChange
import app.cove.companion.feature.training.engine.LoggedSet
import app.cove.companion.feature.training.engine.ProgressRange
import app.cove.companion.feature.training.engine.Schedule
import app.cove.companion.feature.training.engine.Slot
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.engine.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleStatsTest {
    private val tue = LocalDate.of(2026, 10, 6)
    private val order = listOf("Push", "Legs", "Pull")
    private val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)

    @Test fun rotationAndWeekdays() {
        assertEquals("Legs", Schedule.nextType(order, "Push"))
        assertEquals("Push", Schedule.nextType(order, "Pull"))
        assertEquals("Push", Schedule.nextType(order, null))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), Schedule.parseWeekdays("1,3,5"))
        assertEquals("2,4,6", Schedule.formatWeekdays(days))
        assertEquals(3, Schedule.parseWeekdays("nonsense").size)
    }

    @Test fun todayCountsUntilASessionIsDone() {
        val done = listOf(DoneSession("Pull", tue.minusDays(3)))
        val up = Schedule.upcoming(tue, days, order, done, 3)
        assertEquals(listOf(Slot(tue, "Push"), Slot(tue.plusDays(2), "Legs"), Slot(tue.plusDays(4), "Pull")), up)
        val after = Schedule.upcoming(tue, days, order, done + DoneSession("Push", tue), 1)
        assertEquals(Slot(tue.plusDays(2), "Legs"), after.single())
    }

    @Test fun aSkippedDayLeavesTheSameTypeNext() {
        val done = listOf(DoneSession("Push", tue.minusDays(7)))
        assertEquals("Legs", Schedule.upcoming(tue, days, order, done, 1).single().dayType)
    }

    @Test fun unknownDayTypesDoNotBreakTheRotation() {
        val done = listOf(DoneSession("Push", tue.minusDays(3)), DoneSession("Extra", tue.minusDays(1)))
        assertEquals("Legs", Schedule.upcoming(tue, days, order, done, 1).single().dayType)
    }

    @Test fun missedRecentlyOnlyOffTrainingDays() {
        val mon = tue.minusDays(1)
        val twiceWeekly = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        assertTrue(Schedule.missedRecently(tue, twiceWeekly, listOf(DoneSession("Push", mon.minusDays(7)))))
        assertEquals(false, Schedule.missedRecently(tue, days, listOf(DoneSession("Push", mon.minusDays(7)))))
    }

    @Test fun nextLineWording() {
        assertEquals("Rest, then legs on Thursday", Schedule.nextLine(tue, Slot(tue.plusDays(2), "Legs"), true))
        assertEquals("Legs tomorrow", Schedule.nextLine(tue, Slot(tue.plusDays(1), "Legs"), true))
        assertEquals("Pull on Saturday", Schedule.nextLine(tue, Slot(tue.plusDays(4), "Pull"), false))
        assertEquals("Nothing planned", Schedule.nextLine(tue, null, true))
        assertEquals("Tomorrow", Schedule.dayLabel(tue, tue.plusDays(1)))
        assertEquals("Thu 15", Schedule.dayLabel(tue, tue.plusDays(9)))
    }

    @Test fun axisMatchesTheFrames() {
        val bench = ChartMath.axis(listOf(55.0, 55.0, 57.5, 57.5, 60.0, 60.0, 62.5), 2.5)
        assertEquals(listOf(52.5, 57.5, 62.5), bench.ticks)
        val body = ChartMath.axis(listOf(69.0, 69.1, 68.8, 68.9, 68.7, 68.6, 68.6, 68.4), 0.5)
        assertEquals(listOf(68.0, 68.5, 69.0, 69.5), body.ticks)
        assertEquals(0.5, Axis(0.0, 10.0, listOf(0.0, 10.0)).fraction(5.0), 0.001)
        assertEquals(listOf(0, 3, 7), ChartMath.labelIndexes(8))
        assertEquals("68.5", ChartMath.tickLabel(68.5))
        assertEquals("62.5", ChartMath.tickLabel(62.5))
    }

    private fun pts(vararg w: Pair<Int, Double>) = TrainingStats.points(
        w.mapIndexed { i, (d, kg) -> Triple("s$i", tue.minusDays(d.toLong()), listOf(LoggedSet(kg, 8), LoggedSet(kg, 8))) },
    )

    @Test fun liftDeltaAndBest() {
        val p = pts(28 to 55.0, 7 to 60.0, 0 to 62.5)
        assertEquals("Up 7.5 kg in four weeks.", TrainingStats.liftDelta(p, WeightUnit.Kg))
        assertEquals(62.5, TrainingStats.best(p)!!.weightKg, 0.001)
        assertEquals("One session so far.", TrainingStats.liftDelta(p.takeLast(1), WeightUnit.Kg))
        assertEquals("Holding at 60 kg.", TrainingStats.liftDelta(pts(14 to 60.0, 0 to 60.0), WeightUnit.Kg))
        assertEquals("Down 2.5 kg in two weeks.", TrainingStats.liftDelta(pts(14 to 60.0, 0 to 57.5), WeightUnit.Kg))
        val old = pts(60 to 50.0, 7 to 60.0, 0 to 62.5)
        assertEquals(2, ChartRange.Month.filter(old, tue).size)
        assertEquals(3, ChartRange.ThreeMonths.filter(old, tue).size)
        assertEquals(3, ChartRange.All.filter(p, tue).size)
    }

    @Test fun headlineSentences() {
        fun lift(up: Boolean) = LiftChange("Bench", 0, false, 55.0, if (up) 62.5 else 55.0, 8, 8, 3)
        assertEquals("11 sessions in four weeks. Every lift went up.", TrainingStats.headline(ProgressRange.Month, 11, listOf(lift(true), lift(true))))
        assertEquals("Three sessions this week. Weights held steady.", TrainingStats.headline(ProgressRange.Week, 3, listOf(lift(false))))
        assertEquals("No sessions yet. Start one when you are ready.", TrainingStats.headline(ProgressRange.Year, 0, emptyList()))
        assertEquals("One session this year. Every one counts.", TrainingStats.headline(ProgressRange.Year, 1, emptyList()))
    }

    @Test fun bucketsAndBodyDelta() {
        val dates = listOf(0L, 1L, 8L, 20L).map { tue.minusDays(it) }
        assertEquals(listOf(0, 1, 1, 2), TrainingStats.perWindow(dates, tue, 4, 7))
        assertEquals(4, TrainingStats.perMonth(dates, tue, 3).sum())
        val entries = listOf(tue.minusDays(29) to 69.0, tue.minusDays(10) to 68.8, tue to 68.4)
        assertEquals("Down 0.6 kg this month", TrainingStats.bodyDelta(entries, tue, WeightUnit.Kg))
        assertEquals("Up 0.4 kg this month", TrainingStats.bodyDelta(listOf(tue.minusDays(5) to 68.0, tue to 68.4), tue, WeightUnit.Kg))
        assertEquals("Your first weigh-in.", TrainingStats.bodyDelta(emptyList(), tue, WeightUnit.Kg))
        assertEquals("Steady this month", TrainingStats.bodyDelta(listOf(tue.minusDays(5) to 68.0, tue to 68.0), tue, WeightUnit.Kg))
    }
}
