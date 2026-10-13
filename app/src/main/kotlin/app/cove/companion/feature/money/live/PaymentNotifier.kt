package app.cove.companion.feature.money.live

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import app.cove.companion.MainActivity
import app.cove.companion.R
import app.cove.companion.core.Notifications
import app.cove.companion.data.sms.AddedPayment
import app.cove.companion.data.sms.CaptureReport

/**
 * Posts the quiet payment notifications: "Added ₹8 · Food" with Undo, and one summary when several are showing. They use
 * the low-importance Payments channel (no sound, no pop-up), hide the amount on the lock screen, and are never posted
 * while one-thing mode is on.
 */
object PaymentNotifier {
    private const val GROUP = "cove_payments"
    private const val ADDED = "added:"

    /** Posts [report]'s payments unless [quiet] (one-thing mode). */
    fun post(context: Context, report: CaptureReport, quiet: Boolean) {
        if (quiet || report.isEmpty) return
        val nm = context.getSystemService(NotificationManager::class.java)
        Notifications.createChannels(context)
        report.added.forEach { nm.notify(ADDED + it.expenseId, idOf(it.expenseId), added(context, it)) }
        refreshSummary(context)
    }

    /** Takes down the "Added" notification of [expenseId] (after Undo). */
    fun dismissAdded(context: Context, expenseId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(ADDED + expenseId, idOf(expenseId))
    }

    /** Posts the summary when two or more payment notifications are showing, and takes it down otherwise. */
    fun refreshSummary(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val mine = nm.activeNotifications.filter { it.notification.group == GROUP && it.id != Notifications.ID_PAYMENTS_SUMMARY }
        if (mine.size < 2) {
            nm.cancel(Notifications.ID_PAYMENTS_SUMMARY)
            return
        }
        val text = PaymentTexts.summary(mine.size)
        val n = base(context)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setGroupSummary(true)
            .setContentIntent(openIntent(context))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden(context))
            .build()
        nm.notify(Notifications.ID_PAYMENTS_SUMMARY, n)
    }

    private fun added(context: Context, a: AddedPayment): Notification {
        val text = PaymentTexts.added(a)
        return base(context)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setContentIntent(openIntent(context))
            .addAction(0, "Undo", action(context, PaymentActionReceiver.ACTION_UNDO, a.expenseId))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden(context))
            .build()
    }

    private fun hidden(context: Context) = base(context).setContentTitle(PaymentTexts.HIDDEN).build()

    private fun base(context: Context) = NotificationCompat.Builder(context, Notifications.CHANNEL_PAYMENTS)
        .setSmallIcon(R.drawable.ic_notification)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setGroup(GROUP)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

    /** Stable id per payment, so a repeated post replaces instead of stacking. */
    private fun idOf(key: String) = Notifications.ID_PAYMENT_BASE + (key.hashCode() and 0xFFFF)

    private fun openIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun action(context: Context, action: String, key: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, PaymentActionReceiver::class.java).setAction(action).setData(Uri.parse("cove-pay://$action/$key")).putExtra(PaymentActionReceiver.EXTRA_KEY, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
