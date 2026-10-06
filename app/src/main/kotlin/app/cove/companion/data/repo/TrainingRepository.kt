package app.cove.companion.data.repo

import androidx.room.withTransaction
import app.cove.companion.core.Clock
import app.cove.companion.core.newId
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.BodyWeightEntity
import app.cove.companion.data.local.entity.ExerciseEntity
import app.cove.companion.data.local.entity.PlanDayEntity
import app.cove.companion.data.local.entity.SetLogEntity
import app.cove.companion.data.local.entity.TrainingSettingsEntity
import app.cove.companion.data.local.entity.WorkoutPlanEntity
import app.cove.companion.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Everything training reads in one value, so screens recompute from a single flow. */
data class TrainingTables(
    val settings: TrainingSettingsEntity?,
    val exercises: List<ExerciseEntity>,
    val planDays: List<PlanDayEntity>,
    val sessions: List<WorkoutSessionEntity>,
    val sets: List<SetLogEntity>,
    val bodyWeights: List<BodyWeightEntity>,
)

/** Splits the comma-separated id lists stored on plan days and sessions. */
fun String.idList(): List<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Training data: programme, sessions, sets, body weight. Every write goes through [ChangeLog.mark] so sync sees it. */
class TrainingRepository(private val db: CoveDatabase, private val clock: Clock, private val log: ChangeLog) {
    private val dao get() = db.training()

    /** Serialises writes that read before they write (starting a session, numbering a set). */
    private val writeLock = Mutex()

    /** All training tables as one flow; emits again when any of them changes. */
    val tables: Flow<TrainingTables> = combine(
        combine(dao.observeSettings(), dao.observeExercises(), dao.observePlanDays()) { a, b, c -> Triple(a, b, c) },
        combine(dao.observeSessions(), dao.observeSets(), dao.observeBodyWeights()) { a, b, c -> Triple(a, b, c) },
    ) { (settings, exercises, days), (sessions, sets, weights) -> TrainingTables(settings, exercises, days, sessions, sets, weights) }

    /** Settings, or the defaults while nothing is stored. */
    val settings: Flow<TrainingSettingsEntity> = dao.observeSettings().map { it ?: TrainingSettingsEntity() }

    /** True once a programme exists. */
    suspend fun hasPlan(): Boolean = dao.plan() != null

    suspend fun settingsNow(): TrainingSettingsEntity = dao.settings() ?: TrainingSettingsEntity()

    suspend fun saveSettings(change: (TrainingSettingsEntity) -> TrainingSettingsEntity) {
        val saved = change(settingsNow()).copy(updatedAt = clock.now())
        dao.upsertSettings(saved)
        log.mark("training_settings", saved.id)
    }

    suspend fun exercises(): List<ExerciseEntity> = dao.exercises()

    suspend fun exercise(id: String): ExerciseEntity? = dao.exercise(id)?.takeIf { it.deletedAt == null }

    suspend fun saveExercise(exercise: ExerciseEntity): ExerciseEntity {
        val saved = exercise.copy(updatedAt = clock.now())
        dao.upsertExercise(saved)
        log.mark("exercises", saved.id)
        return saved
    }

    suspend fun planDays(): List<PlanDayEntity> = dao.planDays()

    suspend fun savePlanDay(day: PlanDayEntity) {
        dao.upsertPlanDay(day.copy(updatedAt = clock.now()))
        log.mark("plan_days", day.id)
    }

    /** Creates the programme: settings, the plan, its days and the lifts, in one transaction. */
    suspend fun createProgramme(settings: TrainingSettingsEntity, plan: WorkoutPlanEntity, days: List<PlanDayEntity>, exercises: List<ExerciseEntity>) {
        db.withTransaction {
            val now = clock.now()
            dao.upsertSettings(settings.copy(updatedAt = now))
            log.mark("training_settings", settings.id)
            dao.upsertPlan(plan.copy(updatedAt = now))
            log.mark("workout_plans", plan.id)
            exercises.forEach { dao.upsertExercise(it.copy(updatedAt = now)); log.mark("exercises", it.id) }
            days.forEach { dao.upsertPlanDay(it.copy(updatedAt = now)); log.mark("plan_days", it.id) }
        }
    }

    suspend fun plan(): WorkoutPlanEntity? = dao.plan()

    /** Soft-deletes a lift and takes it out of every plan day; returns the days it was in, to hand to [restoreExercise]. */
    suspend fun deleteExercise(id: String): List<PlanDayEntity> {
        val touched = dao.planDays().filter { id in it.exerciseIds.idList() }
        touched.forEach { savePlanDay(it.copy(exerciseIds = it.exerciseIds.idList().filter { e -> e != id }.joinToString(","))) }
        dao.exercise(id)?.let { saveExercise(it.copy(deletedAt = clock.now())) }
        return touched
    }

    /** Brings back a lift removed with [deleteExercise] and puts it back into [days] as they were. */
    suspend fun restoreExercise(id: String, days: List<PlanDayEntity>) {
        dao.exercise(id)?.let { saveExercise(it.copy(deletedAt = null)) }
        days.forEach { d -> dao.planDays().firstOrNull { it.id == d.id }?.let { savePlanDay(it.copy(exerciseIds = d.exerciseIds)) } }
    }

    // ---- sessions -------------------------------------------------------------------------------

    suspend fun activeSession(): WorkoutSessionEntity? = dao.activeSession()

    suspend fun session(id: String): WorkoutSessionEntity? = dao.session(id)?.takeIf { it.deletedAt == null }

    suspend fun saveSession(session: WorkoutSessionEntity): WorkoutSessionEntity {
        val saved = session.copy(updatedAt = clock.now())
        dao.upsertSession(saved)
        log.mark("workout_sessions", saved.id)
        return saved
    }

    /** Starts a session now for [dayType] with [exerciseIds]; an unfinished session is returned instead of starting a second. */
    suspend fun startSession(dayType: String, exerciseIds: List<String>, plannedAt: Long = clock.now()): WorkoutSessionEntity = writeLock.withLock {
        dao.activeSession()?.let { return@withLock it }
        val now = clock.now()
        saveSession(WorkoutSessionEntity(newId(), dayType, plannedAt, startedAt = now, exerciseIds = exerciseIds.joinToString(",")))
    }

    /** Starts the [planned] session (or a new one) now. Returns the running session. */
    suspend fun begin(dayType: String, exerciseIds: List<String>, planned: WorkoutSessionEntity?): WorkoutSessionEntity = writeLock.withLock {
        dao.activeSession()?.let { return@withLock it }
        val now = clock.now()
        if (planned != null && dao.session(planned.id)?.deletedAt == null) saveSession(planned.copy(startedAt = now))
        else saveSession(WorkoutSessionEntity(newId(), dayType, now, startedAt = now, exerciseIds = exerciseIds.joinToString(",")))
    }

    /** Adds [exerciseId] to the end of the session's lifts (an accessory such as Dips). */
    suspend fun addToSession(sessionId: String, exerciseId: String) {
        val s = dao.session(sessionId) ?: return
        val ids = s.exerciseIds.idList()
        if (exerciseId in ids) return
        saveSession(s.copy(exerciseIds = (ids + exerciseId).joinToString(","), skippedIds = s.skippedIds.idList().filter { it != exerciseId }.joinToString(",")))
    }

    /** Marks [exerciseId] as left out of the session. */
    suspend fun skipExercise(sessionId: String, exerciseId: String) {
        val s = dao.session(sessionId) ?: return
        saveSession(s.copy(skippedIds = (s.skippedIds.idList() + exerciseId).distinct().joinToString(",")))
    }

    /** Ends the session; one with no sets is thrown away instead. @return true when it was kept. */
    suspend fun finishSession(id: String): Boolean {
        val s = dao.session(id) ?: return false
        if (dao.setsOf(id).isEmpty()) {
            saveSession(s.copy(deletedAt = clock.now()))
            return false
        }
        if (s.endedAt == null) saveSession(s.copy(endedAt = clock.now()))
        return true
    }

    /** Throws the session and its sets away (soft delete). */
    suspend fun discardSession(id: String) {
        dao.setsOf(id).forEach { saveSet(it.copy(deletedAt = clock.now())) }
        dao.session(id)?.let { saveSession(it.copy(deletedAt = clock.now())) }
    }

    // ---- sets -----------------------------------------------------------------------------------

    suspend fun setsOf(sessionId: String): List<SetLogEntity> = dao.setsOf(sessionId)

    suspend fun saveSet(set: SetLogEntity): SetLogEntity {
        val saved = set.copy(updatedAt = clock.now())
        dao.upsertSet(saved)
        log.mark("set_logs", saved.id)
        return saved
    }

    /** Logs the next set of [exerciseId] in [sessionId]. */
    suspend fun logSet(sessionId: String, exerciseId: String, weightKg: Double, reps: Int, source: String = "manual"): SetLogEntity = writeLock.withLock {
        val n = dao.setsOf(sessionId).count { it.exerciseId == exerciseId }
        saveSet(SetLogEntity(newId(), sessionId, exerciseId, n + 1, weightKg, reps, clock.now(), source))
    }

    suspend fun set(id: String): SetLogEntity? = dao.set(id)

    /** Soft-deletes a set and renumbers the later sets of that lift. */
    suspend fun deleteSet(id: String) {
        val s = dao.set(id) ?: return
        saveSet(s.copy(deletedAt = clock.now()))
        renumber(s.sessionId, s.exerciseId)
    }

    /** Brings back a deleted set and renumbers by logging order. */
    suspend fun restoreSet(id: String) {
        val s = dao.set(id) ?: return
        saveSet(s.copy(deletedAt = null))
        renumber(s.sessionId, s.exerciseId)
    }

    private suspend fun renumber(sessionId: String, exerciseId: String) {
        dao.setsOf(sessionId).filter { it.exerciseId == exerciseId }.sortedBy { it.loggedAt }
            .forEachIndexed { i, set -> if (set.setNo != i + 1) saveSet(set.copy(setNo = i + 1)) }
    }

    // ---- body weight ----------------------------------------------------------------------------

    suspend fun bodyWeight(day: Long): BodyWeightEntity? = dao.bodyWeight(day)?.takeIf { it.deletedAt == null }

    /** Saves the weigh-in of [day] (one per day; a second replaces it). */
    suspend fun saveBodyWeight(day: Long, kg: Double, note: String = "") {
        dao.upsertBodyWeight(BodyWeightEntity(day, kg, note, clock.now()))
        log.mark("body_weights", day.toString())
    }

    /** Removes the weigh-in of [day]. */
    suspend fun deleteBodyWeight(day: Long) {
        dao.bodyWeight(day)?.let {
            dao.upsertBodyWeight(it.copy(deletedAt = clock.now(), updatedAt = clock.now()))
            log.mark("body_weights", day.toString())
        }
    }
}
