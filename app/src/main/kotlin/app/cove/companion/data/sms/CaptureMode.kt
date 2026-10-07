package app.cove.companion.data.sms

/**
 * What Cove does with a payment found in a new message ("Payments from messages", Me and Money > Import from messages).
 * Stored per device in plain preferences by [SmsCapturePrefs].
 *
 * @property key stable value written to preferences.
 * @property blurb one plain sentence explaining the choice.
 */
enum class CaptureMode(val key: String, val label: String, val blurb: String) {
    Off("off", "Off", "Cove does not look at new messages. You can still import them yourself."),
    Ask("ask", "Ask me", "Cove spots payments in new messages and asks before adding each one."),
    Auto("auto", "Add automatically", "Cove adds payments as they arrive and lets you undo. Possible repeats still ask first.");

    companion object {
        /** The mode saved under [key]; [Off] when nothing (or something unknown) was saved. */
        fun of(key: String?): CaptureMode = entries.firstOrNull { it.key == key } ?: Off
    }
}

/** What to do with one message. */
enum class CaptureAction { Ignore, Ask, Add }

/** The decision table for live capture. Pure; tested for every combination in `CaptureDecisionTest`. */
object CaptureDecision {
    /**
     * @param permission the app may read or receive messages right now.
     * @param found the message is a new payment (not rejected by the parser, not already decided).
     * @param duplicate the payment seems to repeat an expense the user already has: never added without asking.
     */
    fun decide(mode: CaptureMode, permission: Boolean, found: Boolean, duplicate: Boolean): CaptureAction = when {
        mode == CaptureMode.Off || !permission || !found -> CaptureAction.Ignore
        duplicate || mode == CaptureMode.Ask -> CaptureAction.Ask
        else -> CaptureAction.Add
    }
}

/** How often the "since last import" catch-up scan may run. */
object CatchUpThrottle {
    /** At most one scan per this long. */
    const val INTERVAL_MS = 10 * 60_000L

    /** True when a scan may run now: never ran, enough time passed, or the clock went backwards. */
    fun due(lastAt: Long, now: Long, intervalMs: Long = INTERVAL_MS): Boolean = lastAt <= 0L || now < lastAt || now - lastAt >= intervalMs
}
