package app.cove.companion.feature.training.voice

import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.feature.training.engine.WeightUnit
import kotlinx.serialization.Serializable

/** A weigh-in a voice command replaced, so undo can put it back ([previousKg] null means there was none). */
@Serializable
data class BodyWeightUndo(val day: Long, val previousKg: Double? = null)

/** What undoing a training voice command has to reverse. */
@Serializable
data class TrainingUndo(
    val sets: List<String> = emptyList(),
    val sessions: List<String> = emptyList(),
    val bodyWeights: List<BodyWeightUndo> = emptyList(),
) {
    val isEmpty get() = sets.isEmpty() && sessions.isEmpty() && bodyWeights.isEmpty()
}

/** Outcome of one training intent: [summary] on success, [error] otherwise, plus what undo needs. */
data class TrainingOutcome(
    val summary: String = "",
    val error: String? = null,
    val undo: TrainingUndo = TrainingUndo(),
    val label: String = "",
    /** Screen to open after saving (the workout just started). */
    val route: String? = null,
)

/** One set of a draft as it will be saved: [weightKg] is resolved (planned weight when none was said). */
data class PreviewSet(val weightKg: Double, val reps: Int)

/** The draft of frame 44: the lift's name, the sets, and what they mean for next time. */
data class SetsPreview(val exercise: String, val unit: WeightUnit, val sets: List<PreviewSet>, val note: String, val bodyweight: Boolean)

/** Training side of voice commands, so [app.cove.companion.feature.voice.exec.IntentExecutor] stays testable without a database. */
interface TrainingVoice {
    /** Names of the user's lifts for the intent parser (empty before training is set up). */
    suspend fun exerciseNames(): List<String>

    /** Resolves a [LogSets][VoiceIntent.LogSets] draft without saving anything. */
    suspend fun preview(intent: VoiceIntent.LogSets): SetsPreview?

    suspend fun logSets(intent: VoiceIntent.LogSets): TrainingOutcome

    suspend fun startWorkout(intent: VoiceIntent.StartWorkout): TrainingOutcome

    suspend fun logBodyWeight(intent: VoiceIntent.LogBodyWeight): TrainingOutcome

    /** Spoken answer for "what is my next workout". */
    suspend fun nextWorkout(): String

    /** Reverses a command's training changes. */
    suspend fun undo(undo: TrainingUndo)
}
