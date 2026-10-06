package app.cove.companion.feature.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cove.companion.container
import app.cove.companion.resilience.CrashHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Re-registers every alarm after reboot, app update, clock or time-zone change, or when the
 * exact-alarm permission is granted.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO + CrashHandler.coroutineHandler("boot")).launch {
            try {
                val container = app.container
                try {
                    val alarms = withTimeout(DB_TIMEOUT_MS) { container.plan.alarms.first() }
                    AlarmScheduler(app, container.clock).sync(alarms)
                } catch (e: Exception) {
                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                    // Database unreadable: keep alarms alive from the mirror (a read-only copy of what was last synced).
                    CrashHandler.report("boot-db", e)
                    val saved = AlarmMirror.forContext(app).read().map { it.toEntity() }
                    AlarmScheduler(app, container.clock).sync(saved)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** A broken database can leave a Room flow waiting forever; fall back to the mirror instead of hanging the receiver. */
        const val DB_TIMEOUT_MS = 5_000L
    }
}
