package app.cove.companion.feature.training

import app.cove.companion.data.local.entity.DayOverrideEntity
import app.cove.companion.data.local.entity.ExerciseLogEntity
import app.cove.companion.data.local.entity.PlanExerciseEntity
import app.cove.companion.data.repo.TrainingTables
import app.cove.companion.feature.training.engine.TodayWorkout
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeekPlan
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPlanTest {
    /** Tuesday. */
    private val today = LocalDate.of(2026, 10, 6)
    private fun ex(id: String, weekday: Int, name: String, kg: Double, sort: Int = 0, deleted: Boolean = false) =
        PlanExerciseEntity(id, weekday, name, kg, 3, 8, 2.5, sort, 0, if (deleted) 1L else null)

    private val plan = listOf(
        ex("b", 2, "Bench press", 60.0, 0), ex("o", 2, "Overhead press", 35.0, 1), ex("s", 4, "Squat", 80.0),
        ex("gone", 2, "Dips", 0.0, 2, deleted = true),
    )

    @Test fun templateOfTheWeekdayInOrderWithoutDeleted() {
        val rows = WeekPlan.forDay(today, plan, emptyList())
        assertEquals(listOf("Bench press", "Overhead press"), rows.map { it.name })
        assertEquals(60.0, rows[0].weightKg, 0.0)
    }

    @Test fun otherWeekdaysAreNotMixedIn() = assertEquals(listOf("Squat"), WeekPlan.forDay(today.plusDays(2), plan, emptyList()).map { it.name })

    @Test fun overrideChangesOnlyThatDate() {
        val o = DayOverrideEntity(WeekPlan.overrideId(today.toEpochDay(), "b"), today.toEpochDay(), "b", 62.5)
        assertEquals(62.5, WeekPlan.forDay(today, plan, listOf(o))[0].weightKg, 0.0)
        assertEquals(60.0, WeekPlan.forDay(today, plan, listOf(o))[0].templateKg, 0.0)
        assertEquals(60.0, WeekPlan.forDay(today.plusDays(7), plan, listOf(o))[0].weightKg, 0.0)
    }

    @Test fun deletedOverrideIsIgnoredAndDismissIsKept() {
        val d = today.toEpochDay()
        val gone = DayOverrideEntity("$d|b", d, "b", 70.0, deletedAt = 5)
        assertEquals(60.0, WeekPlan.forDay(today, plan, listOf(gone))[0].weightKg, 0.0)
        val dismissed = DayOverrideEntity("$d|o", d, "o", null, dismissed = true)
        val rows = WeekPlan.forDay(today, plan, listOf(dismissed))
        assertEquals(35.0, rows[1].weightKg, 0.0)
        assertTrue(rows[1].dismissed)
    }

    @Test fun countsAndNextPlannedDay() {
        assertEquals(mapOf(1 to 0, 2 to 2, 3 to 0, 4 to 1, 5 to 0, 6 to 0, 7 to 0), WeekPlan.counts(plan))
        assertEquals(today, WeekPlan.nextPlannedDay(today, plan))
        assertEquals(today.plusDays(2), WeekPlan.nextPlannedDay(today.plusDays(1), plan))
        assertNull(WeekPlan.nextPlannedDay(today, emptyList()))
    }

    @Test fun idsAndKeysIgnoreCaseAndSpacing() {
        assertEquals(WeekPlan.nameKey("Bench  Press "), WeekPlan.nameKey("bench press"))
        assertEquals("20000|bench press", WeekPlan.logId(20000, " Bench press"))
    }

    @Test fun defaultIncrementIsSmallForDumbbellsAndCurls() {
        assertEquals(2.5, WeekPlan.defaultIncrement("Bench press"), 0.0)
        assertEquals(1.0, WeekPlan.defaultIncrement("Incline dumbbell press"), 0.0)
        assertEquals(1.0, WeekPlan.defaultIncrement("Biceps curl"), 0.0)
    }

    private fun log(day: LocalDate, name: String, kg: Double, reps: String) = ExerciseLogEntity(WeekPlan.logId(day.toEpochDay(), name), day.toEpochDay(), name, kg, 3, 8, reps)

    private fun tables(logs: List<ExerciseLogEntity>, overrides: List<DayOverrideEntity> = emptyList(), p: List<PlanExerciseEntity> = plan) =
        TrainingTables(null, p, overrides, logs, emptyList())

    @Test fun todayRowsCarryAdviceDoneAndDismissed() {
        val week = today.minusDays(7)
        val t = tables(listOf(log(week, "Bench press", 60.0, "8,8,8"), log(week, "Overhead press", 35.0, "8,8,7")))
        val rows = TodayWorkout.rows(t, today, WeightUnit.Kg)
        assertEquals(62.5, rows[0].advice!!.weightKg, 0.0)
        assertEquals("Last set was 7 reps. Stay at 35 kg.", rows[1].advice!!.reason)

        val done = TodayWorkout.rows(tables(t.logs + log(today, "Bench press", 62.5, "8,8,8")), today, WeightUnit.Kg)
        assertNotNull(done[0].done)
        assertNull(done[0].advice)
        assertEquals(listOf(8, 8, 8), done[0].reps)

        val d = today.toEpochDay()
        val hidden = TodayWorkout.rows(tables(t.logs, listOf(DayOverrideEntity("$d|b", d, "b", null, true))), today, WeightUnit.Kg)
        assertNull(hidden[0].advice)
    }

    @Test fun zeroWeightFallsBackToTheLastLoggedWeight() {
        val p = listOf(ex("r", 2, "Barbell row", 0.0))
        val rows = TodayWorkout.rows(tables(listOf(log(today.minusDays(7), "Barbell row", 45.0, "8,8,8")), p = p), today, WeightUnit.Kg)
        assertEquals(45.0, rows[0].exercise.weightKg, 0.0)
        assertEquals(47.5, rows[0].advice!!.weightKg, 0.0)
    }

    @Test fun summaryNamesTheDay() {
        val names = listOf("Bench press", "Overhead press", "Incline dumbbell press")
        assertEquals("Push", TodayWorkout.dayLabel(names))
        assertNull(TodayWorkout.dayLabel(listOf("Zercher squat")))
        val rows = WeekPlan.forDay(today, plan, emptyList())
        assertEquals("Today: Push · 2 exercises", TodayWorkout.summary(rows))
        assertEquals("Not planned", TodayWorkout.summary(emptyList()))
        assertEquals("Today: Legs · 1 exercise", TodayWorkout.summary(WeekPlan.forDay(today.plusDays(2), plan, emptyList())))
    }

    @Test fun historyOrdersOldestFirstAndStopsBeforeToday() {
        val logs = listOf(log(today, "Squat", 90.0, "8"), log(today.minusDays(7), "squat", 80.0, "8,8,8"), log(today.minusDays(14), "Squat", 77.5, "8,8,8"))
        val h = TrainingStats.history(logs, "Squat", today)
        assertEquals(listOf(77.5, 80.0), h.map { it.weightKg })
        assertEquals(listOf(8, 8, 8), h.last().reps)
    }

    @Test fun exerciseProgressIsFirstToLatest() {
        val p = TrainingStats.exercises(listOf(log(today.minusDays(28), "Bench press", 55.0, "8"), log(today.minusDays(7), "Bench press", 60.0, "8"), log(today, "Squat", 80.0, "8")))
        assertEquals("Squat", p[0].name)
        val bench = p.first { it.name == "Bench press" }
        assertEquals("Bench press 60 kg, up 5 kg", TrainingStats.exerciseLine(bench, WeightUnit.Kg))
        assertEquals("Squat 80 kg, no change", TrainingStats.exerciseLine(p[0], WeightUnit.Kg))
    }

    @Test fun bodyChangeInWords() {
        val d = { n: Int -> today.minusDays(n.toLong()) }
        assertEquals("No weigh-ins yet.", TrainingStats.bodyChange(emptyList(), today, WeightUnit.Kg))
        assertEquals("One weigh-in so far.", TrainingStats.bodyChange(listOf(d(3) to 68.0), today, WeightUnit.Kg))
        assertEquals("Down 0.6 kg since 3 Oct", TrainingStats.bodyChange(listOf(d(3) to 69.0, today to 68.4), today, WeightUnit.Kg))
        assertEquals("Up 1 kg since 3 Oct", TrainingStats.bodyChange(listOf(d(3) to 68.0, today to 69.0), today, WeightUnit.Kg))
        assertEquals("Steady since 3 Oct", TrainingStats.bodyChange(listOf(d(3) to 68.0, today to 68.0), today, WeightUnit.Kg))
        assertEquals(1, TrainingStats.recent(listOf(d(40) to 70.0, d(2) to 68.0).let { it }, today).size)
    }

    @Test fun wording() {
        assertEquals("Bench press · 60 kg · 3 x 8", TrainingText.rowLine("Bench press", 60.0, 3, 8, WeightUnit.Kg))
        assertEquals("Pull-ups · 3 x 8", TrainingText.rowLine("Pull-ups", 0.0, 3, 8, WeightUnit.Kg))
        assertEquals("Bench press, 60 kilograms, 3 sets of 8", TrainingText.spoken("Bench press", 60.0, 3, 8, WeightUnit.Kg))
        assertEquals("Bench press, 132.3 pounds, 3 sets of 8", TrainingText.spoken("Bench press", 60.0, 3, 8, WeightUnit.Lb))
        assertEquals("Rest", TrainingText.exerciseCount(0))
        assertEquals("1 exercise", TrainingText.exerciseCount(1))
        assertEquals("Monday", TrainingText.weekdayName(1))
        assertEquals("up 2.5 kg", TrainingText.change(2.5, WeightUnit.Kg))
        assertEquals("down 5 kg", TrainingText.change(-5.0, WeightUnit.Kg))
        assertEquals("no change", TrainingText.change(0.01, WeightUnit.Kg))
        assertEquals("62.5", WeightFormat.number(62.5))
        assertFalse(WeightFormat.parse("abc") != null)
    }
}
