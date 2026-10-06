package app.cove.companion.feature.training.engine

/** Where a session stands: the lift to do now, its set number, and whether everything is done. */
data class SessionPosition(val index: Int, val exerciseId: String?, val setNo: Int, val finished: Boolean, val total: Int)

/** Pure position logic of a session, derived from what is logged so a killed app resumes in the right place. */
object SessionFlow {
    /**
     * The first lift in [order] that is not skipped and has fewer logged sets than planned.
     *
     * @param planned sets planned per lift id (missing counts as 3).
     * @param logged sets logged per lift id.
     */
    fun position(order: List<String>, skipped: Set<String>, planned: Map<String, Int>, logged: Map<String, Int>): SessionPosition {
        val i = order.indexOfFirst { it !in skipped && (logged[it] ?: 0) < (planned[it] ?: 3) }
        return if (i < 0) SessionPosition(order.size.coerceAtLeast(1) - 1, null, 0, true, order.size)
        else SessionPosition(i, order[i], (logged[order[i]] ?: 0) + 1, false, order.size)
    }
}
