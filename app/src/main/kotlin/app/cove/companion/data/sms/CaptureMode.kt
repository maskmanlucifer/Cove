package app.cove.companion.data.sms

import java.time.Instant
import java.time.ZoneId

/**
 * Whether Cove picks up payments from new messages by itself ("Payments from messages", Me and Money > Import from
 * messages). Stored per device in plain preferences by [SmsCapturePrefs].
 *
 * @property key stable value written to preferences.
 * @property blurb one plain sentence explaining the choice.
 */
enum class CaptureMode(val key: String, val label: String, val blurb: String) {
    Off("off", "Off", "Cove does not look at new messages. You can still import them yourself."),
    Auto("auto", "On", "Cove adds payments from new messages by itself and skips ones you already have. You can undo any of them.");

    companion object {
        /**
         * The mode saved under [key]; [Off] when nothing (or something unknown) was saved. The old "Ask me" mode is
         * now [Auto]: repeats are settled without asking.
         */
        fun of(key: String?): CaptureMode = if (key == "ask") Auto else entries.firstOrNull { it.key == key } ?: Off
    }
}

/** What happens to one payment found in a message. */
enum class CaptureAction {
    /** A new payment: written as an expense. */
    Add,

    /** It repeats an expense the user already has: logged as a duplicate, nothing written. */
    Skip,

    /** It may repeat an expense: Gemini is asked, and without an answer the message is left for the next scan. */
    Check,
}

/** How live capture settles a payment without involving the user. Pure; tested in `CaptureDecisionTest`. */
object CaptureDecision {
    /**
     * The phone's own call. No similar expense means [CaptureAction.Add]; an exact match (same bank reference, or the
     * same amount on the same day with a similar name) means [CaptureAction.Skip]; the same amount within a day and a
     * half under another name ("Swiggy" against "Lunch") is a doubt, [CaptureAction.Check].
     */
    fun decide(match: ExistingMatch?): CaptureAction = when {
        match == null -> CaptureAction.Add
        match.exact -> CaptureAction.Skip
        else -> CaptureAction.Check
    }

    /**
     * The phone's answer to a doubt when no cloud model is set up at all (so waiting would never end): the same local
     * day counts as the same payment, another day as a new one.
     */
    fun withoutAi(paymentAt: Long, match: ExistingMatch, zone: ZoneId = ZoneId.systemDefault()): CaptureAction {
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return if (day(paymentAt) == day(match.at)) CaptureAction.Skip else CaptureAction.Add
    }
}

/**
 * The "checked up to" point of the catch-up scan: the arrival time of the newest message fully settled. Every scan
 * reads from it, and it never moves past a message still waiting for Gemini, so that message is read again next time.
 * Pure; tested in `CaptureDecisionTest`.
 */
object CaptureCursor {
    /**
     * The cursor after a scan that read from [current]. [newestRead] is the arrival time of the newest message read
     * (null when none); [oldestWaiting] that of the oldest message left for the next try (null when none).
     */
    fun next(current: Long, newestRead: Long?, oldestWaiting: Long?): Long = when {
        oldestWaiting != null -> maxOf(current, oldestWaiting - 1)
        newestRead != null -> maxOf(current, newestRead)
        else -> current
    }
}

/** How often the catch-up scan may run. */
object CatchUpThrottle {
    /** At most one scan per this long. */
    const val INTERVAL_MS = 10 * 60_000L

    /** True when a scan may run now: never ran, enough time passed, or the clock went backwards. */
    fun due(lastAt: Long, now: Long, intervalMs: Long = INTERVAL_MS): Boolean = lastAt <= 0L || now < lastAt || now - lastAt >= intervalMs
}
