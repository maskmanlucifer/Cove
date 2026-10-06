package app.cove.companion.ai.prompt

import app.cove.companion.ai.model.AdviceRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Prompt for a one-sentence second opinion on a lift's weight. Reply: `{"s":"one sentence"}`. */
object AdvicePrompt {
    const val SYSTEM = "You are a calm strength coach. The input is one lift, today's planned weight in kilograms, the plan " +
        "(sets x reps), the last sessions (daysAgo, weightKg, reps per set) and what the app's rules suggest. " +
        "Give one short, plain sentence (under 25 words) on whether to raise, keep or lower the weight and why. " +
        "No medical advice. Reply with JSON only: {\"s\":\"...\"}."

    /** The user turn as JSON. */
    fun user(r: AdviceRequest): String = buildJsonObject {
        put("lift", JsonPrimitive(r.exercise.trim().take(60)))
        put("plannedKg", JsonPrimitive(r.currentKg))
        put("plan", JsonPrimitive("${r.targetSets}x${r.targetReps}"))
        put("rulesSay", JsonPrimitive(r.rulesSay.take(160)))
        put("sessions", JsonArray(r.sessions.takeLast(AdviceRequest.MAX_SESSIONS).map { s ->
            buildJsonObject {
                put("daysAgo", JsonPrimitive(s.daysAgo))
                put("weightKg", JsonPrimitive(s.weightKg))
                put("reps", JsonArray(s.reps.map { JsonPrimitive(it) }))
            }
        }))
    }.toString()

    /** One prompt string for Nano, or null when it would not fit. */
    fun nano(r: AdviceRequest): String? = "$SYSTEM\n\n${user(r)}".takeIf { it.length <= PromptLimits.NANO_MAX_PROMPT_CHARS }
}
