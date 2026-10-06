package app.cove.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.cove.companion.data.local.entity.BodyWeightEntity
import app.cove.companion.data.local.entity.ExerciseEntity
import app.cove.companion.data.local.entity.PlanDayEntity
import app.cove.companion.data.local.entity.SetLogEntity
import app.cove.companion.data.local.entity.TrainingSettingsEntity
import app.cove.companion.data.local.entity.WorkoutPlanEntity
import app.cove.companion.data.local.entity.WorkoutSessionEntity
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

    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL ORDER BY sort, name")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE deletedAt IS NULL ORDER BY sort, name")
    suspend fun exercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun exercise(id: String): ExerciseEntity?

    @Upsert
    suspend fun upsertExercise(exercise: ExerciseEntity)

    @Query("SELECT * FROM workout_plans WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    fun observePlan(): Flow<WorkoutPlanEntity?>

    @Query("SELECT * FROM workout_plans WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun plan(): WorkoutPlanEntity?

    @Upsert
    suspend fun upsertPlan(plan: WorkoutPlanEntity)

    @Query("SELECT * FROM plan_days WHERE deletedAt IS NULL ORDER BY sort")
    fun observePlanDays(): Flow<List<PlanDayEntity>>

    @Query("SELECT * FROM plan_days WHERE deletedAt IS NULL ORDER BY sort")
    suspend fun planDays(): List<PlanDayEntity>

    @Upsert
    suspend fun upsertPlanDay(day: PlanDayEntity)

    @Query("SELECT * FROM workout_sessions WHERE deletedAt IS NULL ORDER BY plannedAt DESC")
    fun observeSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE deletedAt IS NULL ORDER BY plannedAt DESC")
    suspend fun sessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun session(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE deletedAt IS NULL AND startedAt IS NOT NULL AND endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE deletedAt IS NULL AND startedAt IS NOT NULL AND endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Upsert
    suspend fun upsertSession(session: WorkoutSessionEntity)

    @Query("SELECT * FROM set_logs WHERE deletedAt IS NULL ORDER BY loggedAt, setNo")
    fun observeSets(): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE deletedAt IS NULL ORDER BY loggedAt, setNo")
    suspend fun sets(): List<SetLogEntity>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY loggedAt, setNo")
    suspend fun setsOf(sessionId: String): List<SetLogEntity>

    @Query("SELECT * FROM set_logs WHERE id = :id")
    suspend fun set(id: String): SetLogEntity?

    @Upsert
    suspend fun upsertSet(set: SetLogEntity)

    @Query("SELECT * FROM body_weights WHERE deletedAt IS NULL ORDER BY day")
    fun observeBodyWeights(): Flow<List<BodyWeightEntity>>

    @Query("SELECT * FROM body_weights WHERE day = :day")
    suspend fun bodyWeight(day: Long): BodyWeightEntity?

    @Upsert
    suspend fun upsertBodyWeight(weight: BodyWeightEntity)
}
