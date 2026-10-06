package app.cove.companion.data.repo

import androidx.room.withTransaction
import app.cove.companion.core.Clock
import app.cove.companion.core.newId
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.BodyWeightEntity
import app.cove.companion.data.local.entity.DayOverrideEntity
import app.cove.companion.data.local.entity.ExerciseLogEntity
import app.cove.companion.data.local.entity.PlanExerciseEntity
import app.cove.companion.data.local.entity.TrainingSettingsEntity
import app.cove.companion.feature.training.engine.WeekPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Everything training reads in one value, so screens recompute from a single flow. */
data class TrainingTables(
    val settings: TrainingSettingsEntity?,
    val plan: List<PlanExerciseEntity>,
    val overrides: List<DayOverrideEntity>,
    val logs: List<ExerciseLogEntity>,
    val bodyWeights: List<BodyWeightEntity>,
)

/**
 * Training data: the weekday plan, per-date weight changes, what was done each day, and body weight.
 * Every write goes through [ChangeLog.mark] so sync sees it.
 */
class TrainingRepository(private val db: CoveDatabase, private val clock: Clock, private val log: ChangeLog) {
    private val dao get() = db.training()

    /** Serialises writes that read before they write (numbering a new exercise). */
    private val writeLock = Mutex()

    /** All training tables as one flow; emits again when any of them changes. */
    val tables: Flow<TrainingTables> = combine(
        dao.observeSettings(), dao.observePlan(), dao.observeOverrides(0), dao.observeLogs(), dao.observeBodyWeights(),
    ) { settings, plan, overrides, logs, weights -> TrainingTables(settings, plan, overrides, logs, weights) }

    /** Settings, or the defaults while nothing is stored. */
    val settings: Flow<TrainingSettingsEntity> = dao.observeSettings().map { it ?: TrainingSettingsEntity() }

    suspend fun settingsNow(): TrainingSettingsEntity = dao.settings() ?: TrainingSettingsEntity()

    /** Sets the display unit (`kg` or `lb`). */
    suspend fun saveUnit(unit: String) {
        val saved = settingsNow().copy(unit = unit, updatedAt = clock.now())
        dao.upsertSettings(saved)
        log.mark("training_settings", saved.id)
    }

    // ---- weekday plan ---------------------------------------------------------------------------

    suspend fun plan(): List<PlanExerciseEntity> = dao.plan()

    suspend fun planExercise(id: String): PlanExerciseEntity? = dao.planExercise(id)?.takeIf { it.deletedAt == null }

    private suspend fun write(row: PlanExerciseEntity): PlanExerciseEntity {
        val saved = row.copy(updatedAt = clock.now())
        dao.upsertPlanExercise(saved)
        log.mark("plan_exercises", saved.id)
        return saved
    }

    /** Adds an exercise at the end of [weekday] (ISO 1..7). */
    suspend fun addExercise(weekday: Int, name: String, weightKg: Double, sets: Int, reps: Int, incrementKg: Double = WeekPlan.defaultIncrement(name)): PlanExerciseEntity =
        writeLock.withLock {
            val sort = (dao.plan().filter { it.weekday == weekday }.maxOfOrNull { it.sort } ?: -1) + 1
            write(PlanExerciseEntity(newId(), weekday, name.trim(), weightKg, sets, reps, incrementKg, sort))
        }

    /** Saves changes to an exercise of the weekday template. */
    suspend fun updateExercise(row: PlanExerciseEntity): PlanExerciseEntity = write(row)

    /** Removes an exercise from its weekday (soft delete); [restoreExercise] brings it back. */
    suspend fun deleteExercise(id: String) {
        dao.planExercise(id)?.let { write(it.copy(deletedAt = clock.now())) }
    }

    suspend fun restoreExercise(id: String) {
        dao.planExercise(id)?.let { write(it.copy(deletedAt = null)) }
    }

    /** Moves an exercise one place up ([dir] -1) or down (+1) within its weekday. */
    suspend fun move(id: String, dir: Int) = writeLock.withLock {
        val row = dao.planExercise(id) ?: return@withLock
        val day = dao.plan().filter { it.weekday == row.weekday }.sortedWith(compareBy({ it.sort }, { it.name }))
        val i = day.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j !in day.indices) return@withLock
        val reordered = day.toMutableList().also { it.add(j, it.removeAt(i)) }
        db.withTransaction { reordered.forEachIndexed { k, r -> if (r.sort != k) write(r.copy(sort = k)) } }
    }

    /**
     * Copies the exercises of [from] onto each of [to] (a lift already there by name is left alone).
     * @return the ids of the new rows, to pass to [deleteExercises] for Undo.
     */
    suspend fun copyDay(from: Int, to: Set<Int>): List<String> = writeLock.withLock {
        val all = dao.plan()
        val source = all.filter { it.weekday == from }.sortedBy { it.sort }
        val created = ArrayList<String>()
        db.withTransaction {
            for (day in to.filter { it != from }) {
                val have = all.filter { it.weekday == day }
                var sort = (have.maxOfOrNull { it.sort } ?: -1) + 1
                for (s in source) {
                    if (have.any { WeekPlan.nameKey(it.name) == WeekPlan.nameKey(s.name) }) continue
                    created += write(s.copy(id = newId(), weekday = day, sort = sort++, deletedAt = null)).id
                }
            }
        }
        created
    }

    /** Soft-deletes [ids] (Undo of an add or copy). */
    suspend fun deleteExercises(ids: List<String>) = ids.forEach { deleteExercise(it) }

    /**
     * Plans [name] on [weekday]: changes the existing lift of that name, or adds it.
     * @return the row and what it was before (null when new), for Undo.
     */
    suspend fun planExercise(weekday: Int, name: String, weightKg: Double?, sets: Int?, reps: Int?): Pair<PlanExerciseEntity, PlanExerciseEntity?> {
        val existing = dao.plan().firstOrNull { it.weekday == weekday && WeekPlan.nameKey(it.name) == WeekPlan.nameKey(name) }
        if (existing != null) {
            val saved = write(existing.copy(weightKg = weightKg ?: existing.weightKg, sets = sets ?: existing.sets, reps = reps ?: existing.reps))
            return saved to existing
        }
        return addExercise(weekday, name, weightKg ?: lastWeight(name) ?: 0.0, sets ?: 3, reps ?: 8) to null
    }

    /** The weight last logged for [name], if any. */
    private suspend fun lastWeight(name: String): Double? =
        dao.logs().lastOrNull { WeekPlan.nameKey(it.name) == WeekPlan.nameKey(name) }?.weightKg?.takeIf { it > 0 }

    /** Every exercise name used so far, plan and history, for the name suggestions. */
    suspend fun knownNames(): List<String> =
        (dao.plan().map { it.name } + dao.logs().map { it.name }).distinctBy { WeekPlan.nameKey(it) }

    // ---- date overrides -------------------------------------------------------------------------

    suspend fun override(day: Long, planExerciseId: String): DayOverrideEntity? =
        dao.override(WeekPlan.overrideId(day, planExerciseId))?.takeIf { it.deletedAt == null }

    private suspend fun writeOverride(row: DayOverrideEntity) {
        val saved = row.copy(updatedAt = clock.now())
        dao.upsertOverride(saved)
        log.mark("day_overrides", saved.id)
    }

    /** Changes the planned weight of [planExerciseId] for [day] only. @return the previous override, for Undo. */
    suspend fun setDayWeight(day: Long, planExerciseId: String, kg: Double): DayOverrideEntity? {
        val before = override(day, planExerciseId)
        writeOverride((before ?: DayOverrideEntity(WeekPlan.overrideId(day, planExerciseId), day, planExerciseId)).copy(weightKg = kg, deletedAt = null))
        return before
    }

    /** Hides the weight suggestion of [planExerciseId] for [day]. */
    suspend fun dismissAdvice(day: Long, planExerciseId: String) {
        val before = override(day, planExerciseId)
        writeOverride((before ?: DayOverrideEntity(WeekPlan.overrideId(day, planExerciseId), day, planExerciseId)).copy(dismissed = true, deletedAt = null))
    }

    /** Puts an override back as it was ([before] null removes it). */
    suspend fun restoreOverride(day: Long, planExerciseId: String, before: DayOverrideEntity?) {
        val id = WeekPlan.overrideId(day, planExerciseId)
        if (before != null) writeOverride(before) else dao.override(id)?.let { writeOverride(it.copy(deletedAt = clock.now())) }
    }

    // ---- logs -----------------------------------------------------------------------------------

    suspend fun logOf(day: Long, name: String): ExerciseLogEntity? = dao.log(WeekPlan.logId(day, name))?.takeIf { it.deletedAt == null }

    /** Records what was done for [name] on [day] (one record per lift and day). @return the previous record, for Undo. */
    suspend fun logExercise(day: Long, name: String, weightKg: Double, targetSets: Int, targetReps: Int, reps: List<Int>): ExerciseLogEntity? {
        val before = logOf(day, name)
        val saved = ExerciseLogEntity(WeekPlan.logId(day, name), day, name.trim(), weightKg, targetSets, targetReps, reps.joinToString(","), clock.now())
        dao.upsertLog(saved)
        log.mark("exercise_logs", saved.id)
        return before
    }

    /** Puts a log back as it was ([before] null removes it). */
    suspend fun restoreLog(day: Long, name: String, before: ExerciseLogEntity?) {
        val id = WeekPlan.logId(day, name)
        val saved = (before ?: dao.log(id)?.copy(deletedAt = clock.now()) ?: return).copy(updatedAt = clock.now())
        dao.upsertLog(saved)
        log.mark("exercise_logs", id)
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
