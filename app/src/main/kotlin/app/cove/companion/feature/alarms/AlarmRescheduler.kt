package app.cove.companion.feature.alarms

import android.content.Context
import app.cove.companion.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Keeps AlarmManager in step with the alarms table, whoever changes it (UI, voice, sync). */
class AlarmRescheduler(context: Context, private val container: AppContainer) {
    private val scheduler = AlarmScheduler(context.applicationContext, container.clock)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Starts observing; call once from the Application. */
    fun start() {
        scope.launch { container.plan.alarms.collect { scheduler.sync(it) } }
    }
}
