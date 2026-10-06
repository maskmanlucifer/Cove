package app.cove.companion.ai.provider.rules

/** Names the expense category for a spoken or typed note; lets the rule parser use the user's real categories without depending on features. */
fun interface CategoryResolver {
    /** The user's category name for [note], or null when nothing matches (the caller then uses "Other"). */
    fun categoryFor(note: String): String?

    companion object {
        /** Resolver that never matches. */
        val None = CategoryResolver { null }
    }
}
