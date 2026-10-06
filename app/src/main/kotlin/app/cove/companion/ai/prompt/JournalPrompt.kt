package app.cove.companion.ai.prompt

import app.cove.companion.ai.model.Summary

/** Prompts for on-device understanding of journal text and photos. They only ever run on Nano. */
object JournalPrompt {
    private fun cut(text: String) = text.take(PromptLimits.JOURNAL_TEXT_CHARS)

    fun summary(text: String) =
        "Summarise this diary entry in one calm sentence of at most 20 words. Reply with the sentence only.\n\n${cut(text)}"

    fun tags(text: String) =
        "Give 3 to 5 one-word lowercase topic tags for this diary entry. Reply with the tags separated by commas only.\n\n${cut(text)}"

    fun mood(text: String) =
        "Which mood does this diary entry read as: ${Summary.MOODS.joinToString(", ")}? Reply with one word.\n\n${cut(text)}"

    const val CAPTION = "Describe this photo in one short sentence."
}
