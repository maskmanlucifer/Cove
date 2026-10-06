package app.cove.companion.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Training tables (see `docs/TRAINING.md`). Same conventions as the rest of the schema: client-generated ids,
 * `updatedAt` for sync, `deletedAt` instead of deleting. Weights are always kilograms; the unit in
 * [TrainingSettingsEntity] only changes how they are shown and typed.
 */

/** One exercise of a weekday's template: [weekday] is ISO (1 = Monday); [weightKg] is the planned working weight. */
@Entity(tableName = "plan_exercises", indices = [Index("weekday")])
data class PlanExerciseEntity(
    @PrimaryKey val id: String,
    val weekday: Int,
    val name: String,
    val weightKg: Double = 0.0,
    val sets: Int = 3,
    val reps: Int = 8,
    /** Smallest step the weight moves by when the app suggests more (barbell 2.5, dumbbell 1 to 2). */
    val incrementKg: Double = 2.5,
    val sort: Int = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/**
 * A change that holds for one date only. [id] is `"<day>|<planExerciseId>"`. [weightKg] replaces the template weight
 * that day; [dismissed] hides that day's weight suggestion.
 */
@Entity(tableName = "day_overrides", indices = [Index("day")])
data class DayOverrideEntity(
    @PrimaryKey val id: String,
    /** Epoch day. */
    val day: Long,
    val planExerciseId: String,
    val weightKg: Double? = null,
    val dismissed: Boolean = false,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/**
 * What was done for one exercise on one day. [id] is `"<day>|<lowercase name>"`, so logging again replaces it.
 * [reps] holds the reps of each set, comma separated ("8,8,6"); [targetSets] x [targetReps] is what was planned.
 */
@Entity(tableName = "exercise_logs", indices = [Index("day")])
data class ExerciseLogEntity(
    @PrimaryKey val id: String,
    /** Epoch day. */
    val day: Long,
    val name: String,
    val weightKg: Double,
    val targetSets: Int,
    val targetReps: Int,
    val reps: String,
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

/** Single training settings row (id = [ID]). [unit] is `kg` or `lb`. */
@Entity(tableName = "training_settings")
data class TrainingSettingsEntity(
    @PrimaryKey val id: String = ID,
    val unit: String = "kg",
    val updatedAt: Long = 0,
) {
    companion object {
        const val ID = "me"
    }
}
