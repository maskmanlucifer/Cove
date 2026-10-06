package app.cove.companion.ai.model

/**
 * One batch for category suggestions: [notes] are expense note text only (no amounts, dates or ids; the position
 * in the list is the opaque index) and [categories] are the user's category names.
 */
data class CategoryRequest(val notes: List<String>, val categories: List<String>) {
    companion object {
        /** Most notes in one request (one request per call; callers split bigger lists). */
        const val MAX_BATCH = 40

        /** Splits [items] into batches of at most [MAX_BATCH], in order. */
        fun <T> batches(items: List<T>): List<List<T>> = items.chunked(MAX_BATCH)
    }
}

/** The AI's pick for [index] (position in [CategoryRequest.notes]): [category] is one of the request's category names. */
data class CategorySuggestion(val index: Int, val category: String)
