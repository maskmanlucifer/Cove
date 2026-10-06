package app.cove.companion.feature.training.engine

import app.cove.companion.data.local.entity.ExerciseLogEntity
import app.cove.companion.data.repo.TrainingTables
import java.time.LocalDate

/** One line of a day's workout: the plan, what was logged (null until then) and the weight advice (null when none applies). */
data class WorkoutRow(val exercise: PlannedExercise, val done: ExerciseLogEntity?, val advice: WeightAdvice?) {
    val reps get() = done?.let { TrainingStats.parseReps(it.reps) }.orEmpty()
}

/** Three optional starting points for a weekday: [name] and its exercises (name, sets, reps). */
data class StarterTemplate(val name: String, val lifts: List<Triple<String, Int, Int>>)

/** Builds today's workout and the small summaries other screens show. */
object TodayWorkout {
    /** Push, Pull and Legs, offered in the plan editor. They carry no weights: log once and Cove remembers. */
    val starters = listOf(
        StarterTemplate("Push", listOf(Triple("Bench press", 3, 8), Triple("Overhead press", 3, 8), Triple("Incline dumbbell press", 3, 10), Triple("Triceps pushdown", 3, 12))),
        StarterTemplate("Pull", listOf(Triple("Barbell row", 3, 8), Triple("Lat pulldown", 3, 10), Triple("Biceps curl", 3, 12))),
        StarterTemplate("Legs", listOf(Triple("Squat", 3, 8), Triple("Romanian deadlift", 3, 8), Triple("Leg press", 3, 10), Triple("Calf raise", 3, 12))),
    )

    /** The exercises of [today] with logs and advice. A zero weight falls back to the last logged weight of that lift. */
    fun rows(t: TrainingTables, today: LocalDate, unit: WeightUnit): List<WorkoutRow> {
        val planned = WeekPlan.forDay(today, t.plan, t.overrides)
        val day = today.toEpochDay()
        return planned.map { p ->
            val history = TrainingStats.history(t.logs, p.name, today)
            val kg = if (p.weightKg > 0 || history.isEmpty()) p.weightKg else history.last().weightKg
            val ex = p.copy(weightKg = kg)
            val done = t.logs.firstOrNull { it.deletedAt == null && it.id == WeekPlan.logId(day, p.name) }
            val advice = if (done != null || p.dismissed) null else WeightAdvisor.advise(history, kg, p.incrementKg, today, unit)
            WorkoutRow(ex, done, advice)
        }
    }

    /** "Push" when most of [names] belong to that starter template, else null. */
    fun dayLabel(names: List<String>): String? {
        if (names.isEmpty()) return null
        val keys = names.map { WeekPlan.nameKey(it) }
        return starters.firstOrNull { s ->
            val own = s.lifts.map { WeekPlan.nameKey(it.first) }
            keys.count { it in own } * 2 > keys.size
        }?.name
    }

    /** The Me row: "Today: Push · 3 exercises", "Today: 2 exercises", or "Not planned". */
    fun summary(plannedToday: List<PlannedExercise>): String {
        if (plannedToday.isEmpty()) return "Not planned"
        val label = dayLabel(plannedToday.map { it.name })
        return "Today: " + (label?.let { "$it · " } ?: "") + TrainingText.exerciseCount(plannedToday.size)
    }
}
