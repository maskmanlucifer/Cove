package app.cove.companion.ai.model

/**
 * Two records that may be one payment: a payment found in a message and an expense the user already has. Only names
 * and local times are sent ("Swiggy", "Tue 1:20 pm"); the amounts already matched on the phone and never leave it.
 */
data class SamePair(val payment: String, val paymentTime: String, val existing: String, val existingTime: String)

/** One batch of [pairs] to judge; answers refer to pairs by position. */
data class SameRequest(val pairs: List<SamePair>) {
    companion object {
        /** Most pairs in one request; callers split bigger lists. */
        const val MAX_BATCH = 20
    }
}
