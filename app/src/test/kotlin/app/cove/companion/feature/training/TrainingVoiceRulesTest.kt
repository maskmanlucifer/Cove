package app.cove.companion.feature.training

import app.cove.companion.ai.model.SpokenSet
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.model.workoutDayLabel
import app.cove.companion.ai.provider.rules.RuleParser
import app.cove.companion.ai.provider.rules.WorkoutDays
import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingVoiceRulesTest {
    /** Tuesday 6 October 2026. */
    private val today = LocalDate.of(2026, 10, 6)
    private val parser = RuleParser(Clock { LocalDateTime.of(2026, 10, 6, 10, 35).toEpochMillis() })
    private val names = listOf("Bench press", "Overhead press", "Squat", "Barbell row")
    private fun parse(s: String) = parser.parse(s, exercises = names)
    private fun plan(date: LocalDate, name: String, w: Double? = null, sets: Int? = null, reps: Int? = null, unit: String? = null) =
        VoiceIntent.PlanExercise(date, name, w, sets, reps, unit)

    @Test fun planTomorrowWithTwoLifts() = assertEquals(
        listOf(plan(today.plusDays(1), "Bench press", 60.0, 3, 8, "kg"), plan(today.plusDays(1), "Overhead press", 40.0, null, 8, "kg")),
        parse("plan tomorrow bench press sixty kilos three sets of eight and overhead press forty for eight"),
    )

    @Test fun addToAWeekday() = assertEquals(
        listOf(plan(LocalDate.of(2026, 10, 8), "Squat", 80.0, 5, 5, "kg")),
        parse("add squat eighty kg five by five to Thursday"),
    )

    @Test fun todayPushDayList() = assertEquals(
        listOf(plan(today, "Bench press", 60.0, null, 8), plan(today, "Barbell row", 50.0, null, 10)),
        parse("today push day: bench 60 for 8, row 50 for 10"),
    )

    @Test fun changeWeightToday() = assertEquals(listOf(VoiceIntent.ChangeWeight("Bench press", 62.5)), parse("change my bench to sixty two and a half"))

    @Test fun changeWeightWithUnit() = assertEquals(listOf(VoiceIntent.ChangeWeight("Squat", 85.0, "kg")), parse("set squat to 85 kilos"))

    @Test fun loggingSetsStaysLogging() = assertEquals(
        listOf(VoiceIntent.LogSets("Bench press", listOf(SpokenSet(62.5, 8), SpokenSet(62.5, 8), SpokenSet(62.5, 6)))),
        parse("I did bench sixty-two and a half for eight, eight and six"),
    )

    @Test fun bareSetsWithoutADayLogToday() = assertEquals(
        listOf(VoiceIntent.LogSets("Bench press", listOf(SpokenSet(60.0, 8)))), parse("bench 60 for 8"),
    )

    @Test fun trailingTodayStillLogs() = assertEquals(
        listOf(VoiceIntent.LogSets("Bench press", listOf(SpokenSet(60.0, 8)))), parse("bench 60 for 8 today"),
    )

    @Test fun bodyWeightPhrases() {
        assertEquals(listOf(VoiceIntent.LogBodyWeight(68.4)), parse("my weight is sixty eight point four"))
        assertEquals(listOf(VoiceIntent.LogBodyWeight(68.4)), parse("log weight 68.4"))
        assertEquals(listOf(VoiceIntent.LogBodyWeight(150.0, "lb")), parse("I weigh 150 pounds"))
    }

    @Test fun planningADayTemplateByName() {
        val r = parse("plan tomorrow push day").map { it as VoiceIntent.PlanExercise }
        assertEquals(listOf("Bench press", "Overhead press", "Incline dumbbell press", "Triceps pushdown"), r.map { it.exercise })
        assertEquals(today.plusDays(1), r[0].date)
    }

    @Test fun planWithoutNumbersKeepsWhatIsThere() = assertEquals(listOf(plan(today.plusDays(1), "Squat")), parse("plan squat tomorrow"))

    @Test fun nonWorkoutAddsAreUntouched() {
        assertEquals(1, parse("add milk to my shopping list").size)
        assertEquals(VoiceIntent.AddTodos::class, parse("add milk to my shopping list").single()::class)
    }

    @Test fun askingForTheWorkout() = assertEquals(listOf(VoiceIntent.QueryNextWorkout), parse("what's my workout today"))

    @Test fun dayWords() {
        assertEquals(today, WorkoutDays.find("today push", today)!!.date)
        assertEquals(today.plusDays(1), WorkoutDays.find("tomorrow", today)!!.date)
        assertEquals(today, WorkoutDays.find("this tuesday", today)!!.date)
        assertEquals(today.plusDays(7), WorkoutDays.find("next tuesday", today)!!.date)
        assertEquals(LocalDate.of(2026, 10, 9), WorkoutDays.find("squat this Friday", today)!!.date)
        assertEquals(LocalDate.of(2026, 10, 8), WorkoutDays.find("on thu", today)!!.date)
        assertEquals(LocalDate.of(2026, 10, 11), WorkoutDays.find("sunday", today)!!.date)
        assertEquals(today.plusDays(2), WorkoutDays.find("day after tomorrow", today)!!.date)
        assertNull(WorkoutDays.find("every monday", today))
        assertNull(WorkoutDays.find("bench 60", today))
    }

    @Test fun dayLabels() {
        assertEquals("Today", workoutDayLabel(today, today))
        assertEquals("Tomorrow", workoutDayLabel(today.plusDays(1), today))
        assertEquals("Thursday", workoutDayLabel(today.plusDays(2), today))
    }
}
