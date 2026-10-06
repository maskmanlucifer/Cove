package app.cove.companion.feature.training

import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.local.entity.BodyWeightEntity
import app.cove.companion.data.local.entity.ExerciseEntity
import app.cove.companion.data.local.entity.PlanDayEntity
import app.cove.companion.data.local.entity.SetLogEntity
import app.cove.companion.data.local.entity.TrainingSettingsEntity
import app.cove.companion.data.local.entity.WorkoutSessionEntity
import app.cove.companion.data.repo.TrainingTables
import app.cove.companion.feature.training.engine.ProgressRange
import app.cove.companion.feature.training.engine.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TrainingModelTest {
    private val today = LocalDate.of(2026, 10, 6)
    private fun at(offset: Int, h: Int = 19) = LocalDateTime.of(today.plusDays(offset.toLong()), LocalTime.of(h, 0)).toEpochMillis()

    private val bench = ExerciseEntity("bench", "Bench press", "push", "weighted", 2.5, 8, 8, 3, 0)
    private val ohp = ExerciseEntity("ohp", "Overhead press", "push", "weighted", 2.5, 8, 8, 3, 1)
    private val squat = ExerciseEntity("squat", "Squat", "legs", "weighted", 5.0, 8, 8, 3, 2)
    private val dips = ExerciseEntity("dips", "Dips", "push", "bodyweight", 0.0, 10, 10, 3, 3)
    private val days = listOf(
        PlanDayEntity("d1", "p", "Push", "bench,ohp", 0), PlanDayEntity("d2", "p", "Legs", "squat", 1), PlanDayEntity("d3", "p", "Pull", "", 2),
    )
    private val settings = TrainingSettingsEntity(weekdays = "2,4,6", startWeights = "Bench press=40;Overhead press=25")

    private fun session(id: String, offset: Int, type: String, ids: String, done: Boolean = true) =
        WorkoutSessionEntity(id, type, at(offset), at(offset), if (done) at(offset) + 48 * 60_000L else null, exerciseIds = ids)

    private fun sets(sid: String, ex: String, kg: Double, offset: Int, vararg reps: Int) =
        reps.mapIndexed { i, r -> SetLogEntity("$sid-$ex-$i", sid, ex, i + 1, kg, r, at(offset) + i * 1000L) }

    private fun snap(sessions: List<WorkoutSessionEntity>, sets: List<SetLogEntity>, weights: List<BodyWeightEntity> = emptyList(), withPlan: Boolean = true) =
        TrainingSnapshot(
            TrainingTables(if (withPlan) settings else null, listOf(bench, ohp, squat, dips), if (withPlan) days else emptyList(), sessions, sets, weights),
            today,
        )

    private val history = listOf(
        session("s1", -8, "Push", "bench,ohp"), session("s2", -5, "Push", "bench,ohp"), session("s3", -3, "Pull", "squat"),
    )
    private val historySets = sets("s1", "bench", 60.0, -8, 8, 7, 7) + sets("s2", "bench", 60.0, -5, 8, 8, 8) +
        sets("s1", "ohp", 32.5, -8, 8, 8, 8) + sets("s2", "ohp", 35.0, -5, 8, 8, 8) + sets("s3", "squat", 80.0, -3, 8, 8, 8)

    @Test fun noPlanMeansSetup() {
        assertFalse(snap(emptyList(), emptyList(), withPlan = false).hasPlan)
    }

    @Test fun homeShowsTodaysSessionWithEngineTargets() {
        val h = HomeModel.build(snap(history, historySets))
        assertEquals("Push", h.dayType)
        assertEquals("Today · 7 pm", h.whenLabel)
        assertEquals(listOf("62.5 kg · 3×8", "37.5 kg · 3×8"), h.lifts.map { it.detail })
        assertEquals("Dips", h.accessory)
        assertEquals("Rest, then legs on Thursday", h.nextLine)
        assertEquals("2 exercises · about 20 min", h.meta)
    }

    @Test fun firstRunUsesStartWeights() {
        val h = HomeModel.build(snap(emptyList(), emptyList()))
        assertEquals("40 kg · 3×8", h.lifts.first().detail)
        assertEquals("Your first sessions will show here", h.progressLine)
    }

    @Test fun plannedAccessoryJoinsTodaysList() {
        val planned = WorkoutSessionEntity("pl", "Push", at(0, 10), exerciseIds = "bench,ohp,dips")
        val h = HomeModel.build(snap(history + planned, historySets))
        assertEquals(listOf("Bench press", "Overhead press", "Dips"), h.lifts.map { it.name })
        assertEquals("3×10", h.lifts.last().detail)
        assertNull(h.accessory)
    }

    @Test fun anActiveSessionOffersResume() {
        val active = WorkoutSessionEntity("a", "Push", at(0), at(0), null, exerciseIds = "bench,ohp")
        val h = HomeModel.build(snap(history + active, historySets))
        assertTrue(h.running)
        assertEquals("In progress", h.whenLabel)
        assertNull(h.accessory)
    }

    @Test fun afterTodayIsDoneTheCardMovesOn() {
        val doneToday = session("s4", 0, "Push", "bench,ohp")
        val h = HomeModel.build(snap(history + doneToday, historySets + sets("s4", "bench", 62.5, 0, 8, 8, 6)))
        assertEquals("Legs", h.dayType)
        assertEquals("Thursday · 7 pm", h.whenLabel)
        assertEquals("Pull on Saturday", h.nextLine)
    }

    @Test fun progressLineReadsFromRealSets() {
        assertEquals("Bench up 5 kg", HomeModel.progressLine(snap(history + session("s0", -26, "Push", "bench"), historySets + sets("s0", "bench", 55.0, -26, 8, 8, 8))))
    }

    @Test fun historyIgnoresTheUnfinishedSessionAndEngineReadsTheRest() {
        val active = WorkoutSessionEntity("a", "Push", at(0), at(0), null, exerciseIds = "bench")
        val s = snap(history + active, historySets + sets("a", "bench", 62.5, 0, 8, 8))
        assertEquals(2, s.history("bench").size)
        assertEquals(62.5, s.suggestion(bench, "a").target.weightKg, 0.001)
        assertEquals(listOf(8, 8, 8), s.lastTime(bench, "a").map { it.reps })
    }

    @Test fun progressScreenNumbersComeFromTheData() {
        val bw = listOf(BodyWeightEntity(today.minusDays(29).toEpochDay(), 69.0), BodyWeightEntity(today.toEpochDay(), 68.4))
        val p = ProgressModel.build(snap(history, historySets, bw), ProgressRange.Month)
        assertEquals(3, p.sessions)
        assertEquals("Three sessions in four weeks. Overhead press went up.", p.headline)
        assertEquals(listOf(0, 0, 1, 2), p.bars.map { it.value })
        assertEquals(2, p.bodyPoints.size)
        assertEquals(listOf("Bench press", "Overhead press", "Squat"), p.topSets.map { it.name })
        assertEquals("60", p.topSets.first { it.name == "Bench press" }.now)
        val week = ProgressModel.build(snap(history, historySets), ProgressRange.Week)
        assertEquals(7, week.bars.size)
        assertEquals(1, ProgressModel.build(snap(history, historySets), ProgressRange.Year).bars.map { it.value }.last().coerceAtLeast(1))
    }

    @Test fun summaryRowsShowSessionDateAndUnit() {
        val s = snap(history, historySets)
        assertEquals(WeightUnit.Kg, s.unit)
        assertEquals(today.minusDays(5), s.sessionDate(history[1]))
        assertEquals(3, s.points("bench").let { it.size + 1 })
    }

    @Test fun defaultProgrammeCoversPushPullLegs() {
        assertEquals(listOf("Push", "Legs", "Pull"), DefaultProgramme.dayTypes)
        assertEquals(4, DefaultProgramme.push.size)
        assertTrue(DefaultProgramme.all.any { it.name == "Calf raise" })
        assertEquals(5.0, DefaultProgramme.incrementFor(2.5, WeightUnit.Lb).let { WeightUnit.Lb.fromKg(it) }, 0.1)
        assertEquals(135.0, DefaultProgramme.startIn(WeightUnit.Lb.toKg(137.0), WeightUnit.Lb), 0.001)
        assertEquals(60.0, DefaultProgramme.startIn(61.0, WeightUnit.Kg), 0.001)
    }
}
