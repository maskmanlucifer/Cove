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

    /** Payments found in new messages: quiet (no sound, no pop-up), see `docs/SMS_IMPORT.md`. */
    const val CHANNEL_PAYMENTS = "payments"

    const val ID_ALARM_RING = 4101
    const val ID_WIND_DOWN = 4102
    const val ID_ALARM_MISSED = 4103

    /** Bundled "N for this afternoon" summary; replaced, never stacked. */
    const val ID_NUDGE_BUNDLE = 4201
    const val ID_BRIEF_READY = 4202

    /** Summary that groups the payment notifications; replaced, never stacked. */
    const val ID_PAYMENTS_SUMMARY = 4301

    /** Individual payment notifications use ids from here, one per payment. */
    const val ID_PAYMENT_BASE = 50_000

    /** Individual reminders use ids from here, one per to-do or event. */
    const val ID_REMINDER_BASE = 10_000

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
                NotificationChannel(CHANNEL_PAYMENTS, "Payments", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Payments found in your messages, with Add and Skip. Always quiet."
                    setShowBadge(false)
                },
            ),
        )
    }
}
