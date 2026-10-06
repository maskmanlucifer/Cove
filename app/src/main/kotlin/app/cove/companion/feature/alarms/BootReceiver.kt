package app.cove.companion.feature.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cove.companion.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-registers every alarm after reboot, app update, clock or time-zone change, or when the
 * exact-alarm permission is granted.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = app.container
                AlarmScheduler(app, container.clock).sync(container.plan.alarms.first())
            } finally {
                pending.finish()
            }
        }
    }
}
