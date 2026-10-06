package app.cove.companion.feature.training.engine

import app.cove.companion.data.local.entity.DayOverrideEntity
import app.cove.companion.data.local.entity.PlanExerciseEntity
import java.time.LocalDate

/**
 * One exercise of a particular date: [weightKg] is what is planned that day (the weekday template, or that date's
 * own change), [templateKg] the weekday's weight.
 */
data class PlannedExercise(
    val id: String,
    val name: String,
    val weightKg: Double,
    val templateKg: Double,
    val sets: Int,
    val reps: Int,
    val incrementKg: Double,
    val sort: Int,
    /** True once the user dismissed this date's weight suggestion. */
    val dismissed: Boolean = false,
) {
    val bodyweight get() = weightKg <= 0.0 && templateKg <= 0.0
}

/** Weekday template and per-date overrides, resolved into what is planned for a date (pure, unit tested). */
object WeekPlan {
    /** Key that identifies a lift across days and weeks: case and spacing do not matter. */
    fun nameKey(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Id of the override row for [planExerciseId] on [day] (epoch day). */
    fun overrideId(day: Long, planExerciseId: String) = "$day|$planExerciseId"

    /** Id of the log row for [name] on [day] (epoch day). */
    fun logId(day: Long, name: String) = "$day|${nameKey(name)}"

    /** The exercises planned for [date], in order: the weekday's template with that date's overrides applied. */
    fun forDay(date: LocalDate, plan: List<PlanExerciseEntity>, overrides: List<DayOverrideEntity>): List<PlannedExercise> {
        val day = date.toEpochDay()
        val byPlan = overrides.filter { it.day == day && it.deletedAt == null }.associateBy { it.planExerciseId }
        return plan.filter { it.weekday == date.dayOfWeek.value && it.deletedAt == null }
            .sortedWith(compareBy({ it.sort }, { it.name }))
            .map { e ->
                val o = byPlan[e.id]
                PlannedExercise(e.id, e.name, o?.weightKg ?: e.weightKg, e.weightKg, e.sets, e.reps, e.incrementKg, e.sort, o?.dismissed == true)
            }
    }

    /** Number of exercises per ISO weekday 1..7. */
    fun counts(plan: List<PlanExerciseEntity>): Map<Int, Int> =
        (1..7).associateWith { d -> plan.count { it.weekday == d && it.deletedAt == null } }

    /** The next date from [from] (inclusive) with at least one exercise, within a week, or null. */
    fun nextPlannedDay(from: LocalDate, plan: List<PlanExerciseEntity>): LocalDate? {
        val c = counts(plan)
        return (0L..6L).map { from.plusDays(it) }.firstOrNull { (c[it.dayOfWeek.value] ?: 0) > 0 }
    }

    /** Default step for a lift by name: dumbbell, curl and raise style lifts move in small steps, barbells in 2.5 kg. */
    fun defaultIncrement(name: String): Double {
        val n = nameKey(name)
        return if (listOf("dumbbell", "db ", "curl", "raise", "fly", "flye", "pushdown", "extension", "lunge").any { it in n }) 1.0 else 2.5
    }
}
