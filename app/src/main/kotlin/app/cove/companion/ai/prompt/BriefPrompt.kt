package app.cove.companion.ai.prompt

import app.cove.companion.ai.model.BriefRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Prompts for the one-line intro and "thought" of the morning brief. Facts never include journal content. */
object BriefPrompt {
    const val SYSTEM = "You write one short, calm line for a morning brief from the supplied facts. " +
        "kind intro: one warm sentence greeting the person. kind thought: one gentle thing to think about. " +
        "Never invent facts. Reply with JSON only: {\"text\":\"...\"}."

    /** The user turn: the line [BriefRequest.kind] and its facts as JSON. */
    fun user(request: BriefRequest): String = buildJsonObject {
        put("kind", request.kind)
        put("facts", buildJsonObject { request.facts.forEach { (k, v) -> put(k, v) } })
    }.toString()

    /** One prompt string for Nano, or null when it would not fit. */
    fun nano(request: BriefRequest): String? =
        "$SYSTEM\n\n${user(request)}".takeIf { it.length <= PromptLimits.NANO_MAX_PROMPT_CHARS }
}
