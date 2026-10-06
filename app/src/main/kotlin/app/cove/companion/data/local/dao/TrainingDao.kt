package app.cove.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.cove.companion.data.local.entity.BodyWeightEntity
import app.cove.companion.data.local.entity.DayOverrideEntity
import app.cove.companion.data.local.entity.ExerciseLogEntity
import app.cove.companion.data.local.entity.PlanExerciseEntity
import app.cove.companion.data.local.entity.TrainingSettingsEntity
import kotlinx.coroutines.flow.Flow

/** Queries for the training tables; soft-deleted rows are filtered out of every list. */
@Dao
interface TrainingDao {
    @Query("SELECT * FROM training_settings WHERE id = 'me'")
    fun observeSettings(): Flow<TrainingSettingsEntity?>

    @Query("SELECT * FROM training_settings WHERE id = 'me'")
    suspend fun settings(): TrainingSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: TrainingSettingsEntity)

    @Query("SELECT * FROM plan_exercises WHERE deletedAt IS NULL ORDER BY weekday, sort, name")
    fun observePlan(): Flow<List<PlanExerciseEntity>>

    @Query("SELECT * FROM plan_exercises WHERE deletedAt IS NULL ORDER BY weekday, sort, name")
    suspend fun plan(): List<PlanExerciseEntity>

    @Query("SELECT * FROM plan_exercises WHERE id = :id")
    suspend fun planExercise(id: String): PlanExerciseEntity?

    @Upsert
    suspend fun upsertPlanExercise(row: PlanExerciseEntity)

    @Query("SELECT * FROM day_overrides WHERE deletedAt IS NULL AND day >= :from")
    fun observeOverrides(from: Long): Flow<List<DayOverrideEntity>>

    @Query("SELECT * FROM day_overrides WHERE id = :id")
    suspend fun override(id: String): DayOverrideEntity?

    @Upsert
    suspend fun upsertOverride(row: DayOverrideEntity)

    @Query("SELECT * FROM exercise_logs WHERE deletedAt IS NULL ORDER BY day")
    fun observeLogs(): Flow<List<ExerciseLogEntity>>

    @Query("SELECT * FROM exercise_logs WHERE deletedAt IS NULL ORDER BY day")
    suspend fun logs(): List<ExerciseLogEntity>

    @Query("SELECT * FROM exercise_logs WHERE id = :id")
    suspend fun log(id: String): ExerciseLogEntity?

    @Upsert
    suspend fun upsertLog(row: ExerciseLogEntity)

    @Query("SELECT * FROM body_weights WHERE deletedAt IS NULL ORDER BY day")
    fun observeBodyWeights(): Flow<List<BodyWeightEntity>>

    @Query("SELECT * FROM body_weights WHERE day = :day")
    suspend fun bodyWeight(day: Long): BodyWeightEntity?

    @Upsert
    suspend fun upsertBodyWeight(weight: BodyWeightEntity)
}
