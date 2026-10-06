package app.cove.companion.ai.model

/** One past session of a lift for the second opinion: [weightKg] and the reps of each set. */
data class AdviceSession(val daysAgo: Int, val weightKg: Double, val reps: List<Int>)

/**
 * A request for a one-sentence second opinion on today's weight for [exercise]. Ordinary data only: the lift's
 * name and the last few sessions; never journal content.
 */
data class AdviceRequest(
    val exercise: String,
    val currentKg: Double,
    val targetSets: Int,
    val targetReps: Int,
    val sessions: List<AdviceSession>,
    val rulesSay: String,
) {
    companion object {
        /** Sessions sent at most. */
        const val MAX_SESSIONS = 6
    }
}
