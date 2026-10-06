package app.cove.companion.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Training tables (see `docs/TRAINING.md`). Same conventions as the rest of the schema: client-generated ids,
 * `updatedAt` for sync, `deletedAt` instead of deleting. Weights are always kilograms; the unit in
 * [TrainingSettingsEntity] only changes how they are shown and typed.
 */

/** A lift. [kind] is `weighted` or `bodyweight`; [repMin]..[repMax] is the rep range (equal for fixed reps like 3x8). */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** `push`, `pull`, `legs` or `other`. */
    val muscleGroup: String = "other",
    val kind: String = "weighted",
    /** Smallest step the weight moves by (barbell 2.5, dumbbell 1 to 2). */
    val incrementKg: Double = 2.5,
    val repMin: Int = 8,
    val repMax: Int = 8,
    val sets: Int = 3,
    val sort: Int = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** The programme: a name and how many days a week it runs. */
@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey val id: String,
    val name: String = "Push Pull Legs",
    val daysPerWeek: Int = 3,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** One day of the programme (Push, Pull, Legs); [exerciseIds] is the ordered, comma-separated list of exercises. */
@Entity(tableName = "plan_days", indices = [Index("planId")])
data class PlanDayEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val dayType: String,
    val exerciseIds: String = "",
    val sort: Int = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/**
 * One workout. Planned until [startedAt] is set, active until [endedAt] is set. [exerciseIds] is the ordered list
 * done this time (the plan day plus accessories added on the day); [skippedIds] are the ones left out.
 */
@Entity(tableName = "workout_sessions", indices = [Index("startedAt")])
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val dayType: String,
    val plannedAt: Long,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val note: String = "",
    val exerciseIds: String = "",
    val skippedIds: String = "",
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** One logged set. [source] is `manual` or `voice`. */
@Entity(tableName = "set_logs", indices = [Index("sessionId"), Index("exerciseId")])
data class SetLogEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val setNo: Int,
    val weightKg: Double,
    val reps: Int,
    val loggedAt: Long,
    val source: String = "manual",
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** A morning weigh-in; one per [day] (epoch day). */
@Entity(tableName = "body_weights")
data class BodyWeightEntity(
    @PrimaryKey val day: Long,
    val kg: Double,
    val note: String = "",
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/**
 * Single training settings row (id = [ID]). [unit] is `kg` or `lb`; [weekdays] are ISO day numbers (1 = Monday)
 * joined by commas; [startWeights] is `exerciseName=kg` pairs joined by `;` used until a lift has history.
 */
@Entity(tableName = "training_settings")
data class TrainingSettingsEntity(
    @PrimaryKey val id: String = ID,
    val unit: String = "kg",
    val daysPerWeek: Int = 3,
    val restSeconds: Int = 90,
    val weekdays: String = "1,3,5",
    /** Minutes since midnight a planned session starts, used for the plan entry and its reminder. */
    val startMinutes: Int = 19 * 60,
    val startWeights: String = "",
    val updatedAt: Long = 0,
) {
    companion object {
        const val ID = "me"
    }
}
