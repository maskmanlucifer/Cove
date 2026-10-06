package app.cove.companion.feature.alarms

import android.content.Context
import android.content.Intent
import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.AlarmEntity

/** Debug-only intent hooks for alarms; call only inside `BuildConfig.DEBUG`. */
object DebugAlarms {
    /**
     * `--ei alarm_in_min N` adds a one-time "Test alarm" for N minutes from now (rings at that minute);
     * `--ez alarm_preview true [--ei minutes M]` opens the ring screen without starting the service.
     */
    suspend fun handle(context: Context, c: AppContainer, intent: Intent) {
        val inMin = intent.getIntExtra("alarm_in_min", -1)
        if (inMin >= 0) {
            val at = (c.clock.now() + inMin * 60_000L).toLocalDateTime()
            c.plan.saveAlarm(AlarmEntity(newId(), "Test alarm", at.hour * 60 + at.minute, 0))
        }
        if (intent.getBooleanExtra("alarm_preview", false)) {
            context.startActivity(
                Intent(context, AlarmRingActivity::class.java)
                    .putExtra("preview", true)
                    .putExtra("minutes", intent.getIntExtra("minutes", 6 * 60 + 30)),
            )
        }
    }
}
