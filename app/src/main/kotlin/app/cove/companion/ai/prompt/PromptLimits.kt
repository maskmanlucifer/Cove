package app.cove.companion.ai.prompt

/** Size budgets for prompts (PLAN 6a: Nano takes about 4,000 tokens in and 255 out). Characters, conservatively 3 per token. */
object PromptLimits {
    /** Longest prompt handed to Nano, template included. */
    const val NANO_MAX_PROMPT_CHARS = 9_000

    /** Longest journal text placed in a summary, tags or mood prompt; the rest is cut. */
    const val JOURNAL_TEXT_CHARS = 3_000

    /** Longest transcript sent to the cloud; longer ones stay on the phone. */
    const val CLOUD_TRANSCRIPT_CHARS = 160
}
