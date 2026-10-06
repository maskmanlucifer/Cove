package app.cove.companion.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Notification channels and ids shared across features. Additive: add a constant and a channel
 * in [createChannels]; never rename an existing channel id.
 */
object Notifications {
    /** Ringing alarms: high importance, silent channel because the ring service plays the sound itself. */
    const val CHANNEL_ALARMS = "alarms"

    /** Reminders and the "alarm missed" note. */
    const val CHANNEL_REMINDERS = "reminders"

    /** Gentle suggestions such as "Wind down". */
    const val CHANNEL_NUDGES = "nudges"

    const val ID_ALARM_RING = 4101
    const val ID_WIND_DOWN = 4102
    const val ID_ALARM_MISSED = 4103

    /** Creates every channel; safe to call repeatedly. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Wake-up alarms while they ring"
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(false)
                },
                NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders you asked for"
                },
                NotificationChannel(CHANNEL_NUDGES, "Nudges", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Quiet suggestions, like winding down for bed"
                },
            ),
        )
    }
}
