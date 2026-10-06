package app.cove.companion.feature.training

import app.cove.companion.AppContainer
import app.cove.companion.ai.model.AdviceRequest
import app.cove.companion.ai.model.AdviceSession
import app.cove.companion.ai.model.AiResult
import app.cove.companion.core.Undo
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.local.entity.ExerciseLogEntity
import app.cove.companion.data.repo.TrainingTables
import app.cove.companion.feature.training.engine.TodayWorkout
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.engine.WorkoutRow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Today's workout as the Training page and the Today card both read it. */
data class WorkoutToday(val unit: WeightUnit, val today: LocalDate, val rows: List<WorkoutRow>, val tables: TrainingTables)

/** What users do to today's workout (shared by Training and the Today card); every change that can be taken back offers Undo. */
class WorkoutActions(private val c: AppContainer) {
    private val repo get() = c.training

    /** Today's workout, recomputed whenever the training tables or the day change. */
    val today: Flow<WorkoutToday> = repo.tables.map { t ->
        val day = c.clock.now().toLocalDate()
        val unit = WeightUnit.of(t.settings?.unit)
        WorkoutToday(unit, day, TodayWorkout.rows(t, day, unit), t)
    }

    private fun day() = c.clock.now().toLocalDate().toEpochDay()

    /** Records [reps] of [row] at [kg] for today. */
    suspend fun log(row: WorkoutRow, kg: Double, reps: List<Int>) {
        val d = day()
        val before = repo.logExercise(d, row.exercise.name, kg, row.exercise.sets, row.exercise.reps, reps)
        Undo.center.post("training", "Logged ${row.exercise.name}") { restoreLog(d, row.exercise.name, before) }
    }

    /** Removes today's log of [row]. */
    suspend fun removeLog(row: WorkoutRow) {
        val d = day()
        val before = row.done ?: return
        repo.restoreLog(d, row.exercise.name, null)
        Undo.center.post("training", "Removed ${row.exercise.name}") { restoreLog(d, row.exercise.name, before) }
    }

    private suspend fun restoreLog(d: Long, name: String, before: ExerciseLogEntity?) = repo.restoreLog(d, name, before)

    /** Changes today's planned weight only; later days keep their own plan. */
    suspend fun changeTodayWeight(row: WorkoutRow, kg: Double) {
        val d = day()
        val before = repo.setDayWeight(d, row.exercise.id, kg)
        Undo.center.post("training", "${row.exercise.name} set for today") { repo.restoreOverride(d, row.exercise.id, before) }
    }

    /** "Use 62.5 kg": confirms the suggestion for today. */
    suspend fun useAdvice(row: WorkoutRow) {
        val advice = row.advice?.takeIf { it.actionable } ?: return
        changeTodayWeight(row, advice.weightKg)
    }

    /** "x" on a suggestion: hides it for today. */
    suspend fun dismissAdvice(row: WorkoutRow) {
        val d = day()
        val before = repo.override(d, row.exercise.id)
        repo.dismissAdvice(d, row.exercise.id)
        Undo.center.post("training", "Suggestion hidden for today") { repo.restoreOverride(d, row.exercise.id, before) }
    }

    /** One-sentence second opinion from [app.cove.companion.ai.AiService], or null when none is available. Never changes a weight. */
    suspend fun ask(state: WorkoutToday, row: WorkoutRow): String? {
        val history = TrainingStats.history(state.tables.logs, row.exercise.name, state.today)
        val request = AdviceRequest(
            row.exercise.name, row.exercise.weightKg, row.exercise.sets, row.exercise.reps,
            history.takeLast(AdviceRequest.MAX_SESSIONS).map { AdviceSession(ChronoUnit.DAYS.between(it.day, state.today).toInt(), it.weightKg, it.reps) },
            row.advice?.reason.orEmpty(),
        )
        return (c.ai.adviseWorkout(request) as? AiResult.Ok)?.value
    }

    /** True when a Gemini key or Nano can answer "Ask Cove". */
    suspend fun canAsk(): Boolean = c.ai.status().let { it.onDeviceReady || it.cloudReady }
}
