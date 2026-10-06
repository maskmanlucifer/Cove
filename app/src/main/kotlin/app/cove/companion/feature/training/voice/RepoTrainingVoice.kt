package app.cove.companion.feature.training.voice

import app.cove.companion.AppContainer
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.model.ExerciseNames
import app.cove.companion.core.newId
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.local.entity.ExerciseEntity
import app.cove.companion.data.repo.idList
import app.cove.companion.feature.training.DefaultProgramme
import app.cove.companion.feature.training.TrainingSnapshot
import app.cove.companion.feature.training.engine.ExerciseKind
import app.cove.companion.feature.training.engine.LoggedSet
import app.cove.companion.feature.training.engine.ProgressionEngine
import app.cove.companion.feature.training.engine.Schedule
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/** [TrainingVoice] over the training repository: every write goes through it, so sync and undo see it. */
class RepoTrainingVoice(private val c: AppContainer) : TrainingVoice {
    private suspend fun snapshot() = TrainingSnapshot(c.training.tables.first(), c.clock.now().toLocalDate())

    override suspend fun exerciseNames(): List<String> = c.training.exercises().map { it.name }

    /** The user's lift for [spoken], or an unsaved one built from the default programme (saved only when logging). */
    private fun resolve(snap: TrainingSnapshot, spoken: String): ExerciseEntity {
        val names = snap.exercises.map { it.name }
        val name = ExerciseNames.resolve(spoken, names) ?: spoken.trim().replaceFirstChar { it.uppercase() }
        snap.exercises.firstOrNull { it.name.equals(name, true) }?.let { return it }
        val d = DefaultProgramme.find(name)
        return ExerciseEntity(
            id = "", name = d?.name ?: name, muscleGroup = d?.group ?: "other", kind = (d?.kind ?: ExerciseKind.Weighted).key,
            incrementKg = d?.let { DefaultProgramme.incrementFor(it.incrementKg, snap.unit) } ?: 2.5,
            repMin = d?.repMin ?: 8, repMax = d?.repMax ?: 8, sets = d?.sets ?: 3, sort = snap.exercises.size,
        )
    }

    private fun sets(snap: TrainingSnapshot, e: ExerciseEntity, intent: VoiceIntent.LogSets, exclude: String?): List<PreviewSet> {
        val unit = intent.unit?.let { WeightUnit.of(it) } ?: snap.unit
        val bodyweight = e.kind == ExerciseKind.Bodyweight.key
        val planned = snap.suggestion(e, exclude).target.weightKg
        return intent.sets.map { PreviewSet(if (bodyweight) 0.0 else it.weight?.let(unit::toKg) ?: planned, it.reps) }
    }

    override suspend fun preview(intent: VoiceIntent.LogSets): SetsPreview? {
        val snap = snapshot()
        if (!snap.hasPlan) return null
        val e = resolve(snap, intent.exercise)
        val exclude = snap.active?.id
        val drafted = sets(snap, e, intent, exclude)
        val earlier = snap.active?.let { s -> snap.liftSets(s.id, e.id).map { LoggedSet(it.weightKg, it.reps) } }.orEmpty()
        val all = earlier + drafted.map { LoggedSet(it.weightKg, it.reps) }
        val history = if (e.id.isEmpty()) emptyList() else snap.history(e.id, exclude)
        val spec = snap.spec(e)
        val goal = ProgressionEngine.next(spec, history, snap.startKg(e)).target.reps
        val next = ProgressionEngine.next(spec, history + listOf(all), snap.startKg(e))
        val top = if (spec.isBodyweight) 0.0 else all.maxOf { it.weightKg }
        val working = if (spec.isBodyweight) all else all.filter { abs(it.weightKg - top) < 0.01 }
        return SetsPreview(e.name, snap.unit, drafted, TrainingText.draftNote(next, working, goal, snap.unit), spec.isBodyweight)
    }

    override suspend fun logSets(intent: VoiceIntent.LogSets): TrainingOutcome {
        val snap = snapshot()
        if (!snap.hasPlan) return TrainingOutcome(error = "Set up Training first, then I can log your sets")
        var e = resolve(snap, intent.exercise)
        if (e.id.isEmpty()) e = c.training.saveExercise(e.copy(id = newId()))
        val before = snap.active
        val session = before ?: c.training.startSession(dayTypeFor(snap, e), listOf(e.id))
        val startedHere = before == null
        if (!startedHere && e.id !in session.exerciseIds.idList()) c.training.addToSession(session.id, e.id)
        val rows = sets(snap, e, intent, session.id)
        val ids = rows.map { c.training.logSet(session.id, e.id, it.weightKg, it.reps, "voice").id }
        if (startedHere) c.training.finishSession(session.id)
        val n = ids.size
        return TrainingOutcome(
            summary = "Logged ${if (n == 1) "a set" else "$n sets"} of ${e.name}",
            undo = TrainingUndo(sets = ids, sessions = if (startedHere) listOf(session.id) else emptyList()),
            label = "removed $n ${if (n == 1) "set" else "sets"} of ${e.name}",
        )
    }

    private fun dayTypeFor(snap: TrainingSnapshot, e: ExerciseEntity): String =
        snap.tables.planDays.firstOrNull { e.id in it.exerciseIds.idList() }?.dayType ?: "Extra"

    override suspend fun startWorkout(intent: VoiceIntent.StartWorkout): TrainingOutcome {
        val snap = snapshot()
        if (!snap.hasPlan) return TrainingOutcome(error = "Set up Training first, then I can start a workout")
        snap.active?.let { return TrainingOutcome(summary = "Your ${it.dayType} workout is already going", route = SESSION_ROUTE) }
        val day = snap.dayTypes.firstOrNull { it.equals(intent.day, true) } ?: snap.upcoming(1).firstOrNull()?.dayType ?: snap.dayTypes.firstOrNull()
            ?: return TrainingOutcome(error = "I couldn't find a workout to start")
        val ids = snap.dayExercises(day).map { it.id }
        if (ids.isEmpty()) return TrainingOutcome(error = "The $day day has no exercises yet")
        val session = c.training.startSession(day, ids)
        return TrainingOutcome(
            summary = "Started $day day", undo = TrainingUndo(sessions = listOf(session.id)), label = "discarded the $day workout", route = SESSION_ROUTE,
        )
    }

    override suspend fun logBodyWeight(intent: VoiceIntent.LogBodyWeight): TrainingOutcome {
        val snap = snapshot()
        val unit = intent.unit?.let { WeightUnit.of(it) } ?: snap.unit
        val kg = unit.toKg(intent.weight)
        val day = c.clock.now().toLocalDate().toEpochDay()
        val previous = c.training.bodyWeight(day)
        c.training.saveBodyWeight(day, kg, previous?.note.orEmpty())
        return TrainingOutcome(
            summary = "Logged ${WeightFormat.withUnit(kg, snap.unit)}",
            undo = TrainingUndo(bodyWeights = listOf(BodyWeightUndo(day, previous?.kg))),
            label = "removed today's weigh-in",
        )
    }

    override suspend fun nextWorkout(): String {
        val snap = snapshot()
        if (!snap.hasPlan) return "Training isn't set up yet. Open Training from Me to start"
        snap.active?.let { return "Your ${it.dayType} workout is running" }
        val slot = snap.upcoming(1).firstOrNull() ?: return "Nothing is planned yet"
        val at = TrainingText.timeLabel(snap.startMinutes)
        return when (val label = Schedule.dayLabel(snap.today, slot.date)) {
            "Today" -> "Next is ${slot.dayType} today at $at"
            "Tomorrow" -> "Next is ${slot.dayType} tomorrow at $at"
            else -> "Next is ${slot.dayType} on $label at $at"
        }
    }

    override suspend fun undo(undo: TrainingUndo) {
        undo.sets.forEach { c.training.deleteSet(it) }
        undo.sessions.forEach { c.training.discardSession(it) }
        undo.bodyWeights.forEach { b -> if (b.previousKg == null) c.training.deleteBodyWeight(b.day) else c.training.saveBodyWeight(b.day, b.previousKg) }
    }

    companion object {
        /** The route the voice screen opens after "start workout". */
        const val SESSION_ROUTE = "training/session"
    }
}
