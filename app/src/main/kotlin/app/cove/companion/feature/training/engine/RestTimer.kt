package app.cove.companion.feature.training.engine

/**
 * A rest in progress, kept as an absolute end time so it survives the screen turning off and the process dying.
 *
 * @param endsAt epoch millis when the rest is over.
 * @param nextLabel what comes next ("set 3, 37.5 kg × 8"), shown under the countdown and in the notification.
 */
data class RestState(val endsAt: Long, val nextLabel: String, val header: String = "") {
    /** Whole seconds left at [now] (rounded up so the display reaches 0:00 exactly when the rest ends). */
    fun remainingSeconds(now: Long): Int = ((endsAt - now + 999) / 1000).toInt().coerceAtLeast(0)

    /** True when the rest is over at [now]. */
    fun isOver(now: Long): Boolean = now >= endsAt

    /** The same rest made [seconds] longer. */
    fun extended(seconds: Int): RestState = copy(endsAt = endsAt + seconds * 1000L)
}
