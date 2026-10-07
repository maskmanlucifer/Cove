package app.cove.companion.data.sms

import app.cove.companion.core.Clock
import app.cove.companion.resilience.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The silent "since last import" scan that runs when the app comes to the front and when Money opens, so payments that
 * arrived while the receiver could not run (phone off, app force-stopped, permission allowed later) still show up.
 * Throttled by [CatchUpThrottle], never blocks the UI, never throws, and does nothing when capture is off or the
 * inbox may not be read. It posts no notifications: what it finds is added or shown on Money.
 */
class SmsCatchUp(
    private val scope: CoroutineScope,
    private val capture: SmsLiveCapture,
    private val prefs: SmsCapturePrefs,
    private val inbox: SmsSource,
    private val clock: Clock,
    private val dbReady: StateFlow<Boolean>,
    private val canRead: () -> Boolean,
) {
    private val running = AtomicBoolean(false)

    /** Starts a scan when one is due (or [force]d, for debugging); returns the job, or null when nothing was started. */
    fun runIfDue(force: Boolean = false): Job? {
        if (prefs.mode.value == CaptureMode.Off || !canRead()) return null
        val now = clock.now()
        if (!force && !CatchUpThrottle.due(prefs.lastCatchUpAt, now)) return null
        if (!running.compareAndSet(false, true)) return null
        prefs.lastCatchUpAt = now
        return scope.launch(Dispatchers.IO + CrashHandler.coroutineHandler("sms-catchup")) {
            try {
                CrashHandler.guarded("sms-catchup") {
                    if (withTimeoutOrNull(DB_WAIT_MS) { dbReady.first { it } } == true) withTimeout(SCAN_LIMIT_MS) { capture.catchUp(inbox) }
                }
            } finally {
                running.set(false)
            }
        }
    }

    private companion object {
        const val DB_WAIT_MS = 8_000L
        const val SCAN_LIMIT_MS = 60_000L
    }
}
