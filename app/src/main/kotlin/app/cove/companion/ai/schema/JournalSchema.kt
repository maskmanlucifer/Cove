package app.cove.companion.ai.schema

import app.cove.companion.ai.model.Summary

/** Cleans free-text Nano answers for journal insights; anything unusable becomes empty or null. */
object JournalSchema {
    private const val MAX_TAGS = 5

    /** `"calm, Work, #focus."` becomes `[calm, work, focus]`. */
    fun tags(raw: String): List<String> =
        raw.split(',', '\n').map { it.trim().lowercase().trim('#', '.') }.filter { it.isNotEmpty() }.take(MAX_TAGS)

    /** The first of [Summary.MOODS] mentioned in [raw], or null. */
    fun mood(raw: String): String? = raw.lowercase().let { a -> Summary.MOODS.firstOrNull { a.contains(it) } }
}
