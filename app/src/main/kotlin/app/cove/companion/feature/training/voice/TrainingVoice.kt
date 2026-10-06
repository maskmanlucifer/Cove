package app.cove.companion.feature.training.voice

import app.cove.companion.ai.model.VoiceIntent
import kotlinx.serialization.Serializable

/** A weigh-in a voice command replaced, so undo can put it back ([previousKg] null means there was none). */
@Serializable
data class BodyWeightUndo(val day: Long, val previousKg: Double? = null)

/** A plan exercise as it was before a command changed it. */
@Serializable
data class PlanSnap(val id: String, val weightKg: Double, val sets: Int, val reps: Int)

/** A per-date weight change as it was before a command ([had] false means there was none). */
@Serializable
data class OverrideUndo(val day: Long, val planId: String, val had: Boolean, val weightKg: Double? = null, val dismissed: Boolean = false)

/** An exercise log as it was before a command ([previous] null means there was none). */
@Serializable
data class LogUndo(val day: Long, val name: String, val previous: LogSnap? = null)

/** The values of an exercise log. */
@Serializable
data class LogSnap(val weightKg: Double, val targetSets: Int, val targetReps: Int, val reps: String)

/** What undoing a training voice command has to reverse. */
@Serializable
data class TrainingUndo(
    /** Plan exercises the command added. */
    val created: List<String> = emptyList(),
    val changed: List<PlanSnap> = emptyList(),
    val overrides: List<OverrideUndo> = emptyList(),
    val logs: List<LogUndo> = emptyList(),
    val bodyWeights: List<BodyWeightUndo> = emptyList(),
) {
    val isEmpty get() = created.isEmpty() && changed.isEmpty() && overrides.isEmpty() && logs.isEmpty() && bodyWeights.isEmpty()

    operator fun plus(o: TrainingUndo) = TrainingUndo(created + o.created, changed + o.changed, overrides + o.overrides, logs + o.logs, bodyWeights + o.bodyWeights)
}

/** Outcome of one training intent: [summary] on success, [error] otherwise, plus what undo needs. */
data class TrainingOutcome(
    val summary: String = "",
    val error: String? = null,
    val undo: TrainingUndo = TrainingUndo(),
    val label: String = "",
)

/** Training side of voice commands, so [app.cove.companion.feature.voice.exec.IntentExecutor] stays testable without a database. */
interface TrainingVoice {
    /** Names of the user's lifts for the intent parser. */
    suspend fun exerciseNames(): List<String>

    suspend fun planExercise(intent: VoiceIntent.PlanExercise): TrainingOutcome

    suspend fun logSets(intent: VoiceIntent.LogSets): TrainingOutcome

    suspend fun changeWeight(intent: VoiceIntent.ChangeWeight): TrainingOutcome

    suspend fun logBodyWeight(intent: VoiceIntent.LogBodyWeight): TrainingOutcome

    /** Spoken answer for "what is my workout today". */
    suspend fun todaySummary(): String

    /** Reverses a command's training changes. */
    suspend fun undo(undo: TrainingUndo)
}
