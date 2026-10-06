package app.cove.companion.feature.suggest

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** Phone-usage facts that stay on the device and feed [DecisionEngine]. */
interface UsageSignals {
    /** Epoch millis of the last time the screen was in use within [from]..[to], or null when unknown or unused. */
    suspend fun lastScreenUse(from: Long, to: Long): Long?
}

/** Real implementation over `UsageStatsManager`; needs the user-granted Usage Access and returns null without it. */
class UsageStatsSignals(private val context: Context) : UsageSignals {
    /** True when the user has granted Usage Access to Cove. */
    val granted: Boolean
        get() {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }

    override suspend fun lastScreenUse(from: Long, to: Long): Long? {
        if (!granted) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(from, to)
        val e = UsageEvents.Event()
        var last: Long? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) last = e.timeStamp
        }
        return last
    }
}

/** Fixed answer for tests and the debug trigger (`--es suggest late-night`). */
class FakeUsageSignals(private val lastUse: Long?) : UsageSignals {
    override suspend fun lastScreenUse(from: Long, to: Long): Long? = lastUse
}

/** Debug-only override set from `MainActivity`'s intent. */
object SuggestDebug {
    /** Epoch millis the fake phone was last used, or null for the real signals. */
    @Volatile
    var lastUse: Long? = null
}
