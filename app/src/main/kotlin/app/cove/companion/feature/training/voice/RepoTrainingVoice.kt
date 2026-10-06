package app.cove.companion.feature.training.voice

import app.cove.companion.AppContainer
import app.cove.companion.ai.model.ExerciseNames
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.model.workoutDayLabel
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.local.entity.DayOverrideEntity
import app.cove.companion.data.local.entity.ExerciseLogEntity
import app.cove.companion.feature.training.engine.TodayWorkout
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeekPlan
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import kotlinx.coroutines.flow.first

/** [TrainingVoice] over the training repository: every write goes through it, so sync and undo see it. */
class RepoTrainingVoice(private val c: AppContainer) : TrainingVoice {
    private suspend fun unit() = WeightUnit.of(c.training.settingsNow().unit)
    private fun today() = c.clock.now().toLocalDate()

    override suspend fun exerciseNames(): List<String> = c.training.knownNames()

    /** The user's own name for [spoken] when they have one, else the built-in name or the words as said. */
    private suspend fun resolve(spoken: String): String =
        ExerciseNames.resolve(spoken, c.training.knownNames()) ?: spoken.trim().replaceFirstChar { it.uppercase() }

    override suspend fun planExercise(intent: VoiceIntent.PlanExercise): TrainingOutcome {
        val u = intent.unit?.let { WeightUnit.of(it) } ?: unit()
        val name = resolve(intent.exercise)
        val weekday = intent.date.dayOfWeek.value
        val (row, before) = c.training.planExercise(weekday, name, intent.weight?.let(u::toKg), intent.sets, intent.reps)
        val day = TrainingText.weekdayName(weekday)
        return TrainingOutcome(
            summary = "Planned ${row.name} for ${workoutDayLabel(intent.date, today())}",
            undo = if (before == null) TrainingUndo(created = listOf(row.id)) else TrainingUndo(changed = listOf(PlanSnap(before.id, before.weightKg, before.sets, before.reps))),
            label = "changed $day's plan",
        )
    }

    override suspend fun logSets(intent: VoiceIntent.LogSets): TrainingOutcome {
        val t = c.training.tables.first()
        val u = intent.unit?.let { WeightUnit.of(it) } ?: unit()
        val name = resolve(intent.exercise)
        val day = today()
        val planned = TodayWorkout.rows(t, day, u).firstOrNull { WeekPlan.nameKey(it.exercise.name) == WeekPlan.nameKey(name) }?.exercise
        val plannedKg = planned?.weightKg ?: 0.0
        val kg = intent.sets.maxOf { it.weight?.let(u::toKg) ?: plannedKg }
        val reps = intent.sets.map { it.reps }
        val before = c.training.logExercise(day.toEpochDay(), planned?.name ?: name, kg, planned?.sets ?: reps.size, planned?.reps ?: reps.first(), reps)
        return TrainingOutcome(
            summary = "Logged ${planned?.name ?: name}",
            undo = TrainingUndo(logs = listOf(LogUndo(day.toEpochDay(), planned?.name ?: name, before?.let { LogSnap(it.weightKg, it.targetSets, it.targetReps, it.reps) }))),
            label = "removed ${planned?.name ?: name} from today",
        )
    }

    override suspend fun changeWeight(intent: VoiceIntent.ChangeWeight): TrainingOutcome {
        val t = c.training.tables.first()
        val u = intent.unit?.let { WeightUnit.of(it) } ?: unit()
        val name = resolve(intent.exercise)
        val row = TodayWorkout.rows(t, today(), u).firstOrNull { WeekPlan.nameKey(it.exercise.name) == WeekPlan.nameKey(name) }
            ?: return TrainingOutcome(error = "$name isn't planned today")
        val day = today().toEpochDay()
        val before = c.training.setDayWeight(day, row.exercise.id, u.toKg(intent.weight))
        return TrainingOutcome(
            summary = "${row.exercise.name} is ${WeightFormat.withUnit(u.toKg(intent.weight), u)} today",
            undo = TrainingUndo(overrides = listOf(OverrideUndo(day, row.exercise.id, before != null, before?.weightKg, before?.dismissed ?: false))),
            label = "${row.exercise.name} weight changed back",
        )
    }

    override suspend fun logBodyWeight(intent: VoiceIntent.LogBodyWeight): TrainingOutcome {
        val u = intent.unit?.let { WeightUnit.of(it) } ?: unit()
        val shown = unit()
        val kg = u.toKg(intent.weight)
        val day = today().toEpochDay()
        val previous = c.training.bodyWeight(day)
        c.training.saveBodyWeight(day, kg, previous?.note.orEmpty())
        return TrainingOutcome(
            summary = "Logged ${WeightFormat.withUnit(kg, shown)}",
            undo = TrainingUndo(bodyWeights = listOf(BodyWeightUndo(day, previous?.kg))),
            label = "removed today's weigh-in",
        )
    }

    override suspend fun todaySummary(): String {
        val u = unit()
        val rows = TodayWorkout.rows(c.training.tables.first(), today(), u)
        if (rows.isEmpty()) return "Nothing planned for today"
        return "Today: " + rows.joinToString(", ") { TrainingText.rowLine(it.exercise.name, it.exercise.weightKg, it.exercise.sets, it.exercise.reps, u) }
    }

    override suspend fun undo(undo: TrainingUndo) {
        c.training.deleteExercises(undo.created)
        undo.changed.forEach { s -> c.training.planExercise(s.id)?.let { c.training.updateExercise(it.copy(weightKg = s.weightKg, sets = s.sets, reps = s.reps)) } }
        undo.overrides.forEach { o ->
            c.training.restoreOverride(o.day, o.planId, if (o.had) DayOverrideEntity(WeekPlan.overrideId(o.day, o.planId), o.day, o.planId, o.weightKg, o.dismissed) else null)
        }
        undo.logs.forEach { l ->
            c.training.restoreLog(l.day, l.name, l.previous?.let { ExerciseLogEntity(WeekPlan.logId(l.day, l.name), l.day, l.name, it.weightKg, it.targetSets, it.targetReps, it.reps) })
        }
        undo.bodyWeights.forEach { b -> if (b.previousKg == null) c.training.deleteBodyWeight(b.day) else c.training.saveBodyWeight(b.day, b.previousKg) }
    }
}
