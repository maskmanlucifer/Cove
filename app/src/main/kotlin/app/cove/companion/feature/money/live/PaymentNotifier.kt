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
import app.cove.companion.data.sms.PendingPayment

/**
 * Posts the quiet payment notifications: "₹8 spent · Pluxee wallet" with Add and Skip, "Added ₹8 · Food" with Undo, and one
 * summary when several are showing. They use the low-importance Payments channel (no sound, no pop-up), hide the amount on
 * the lock screen, and are never posted while one-thing mode is on (the Money row still shows what is waiting).
 */
object PaymentNotifier {
    private const val GROUP = "cove_payments"
    private const val ASK = "ask:"
    private const val ADDED = "added:"

    /** Posts [report]'s payments unless [quiet] (one-thing mode); both lists may be empty. */
    fun post(context: Context, report: CaptureReport, quiet: Boolean) {
        if (quiet || report.isEmpty) return
        val nm = context.getSystemService(NotificationManager::class.java)
        Notifications.createChannels(context)
        report.pending.forEach { nm.notify(ASK + it.key, idOf(it.key), ask(context, it)) }
        report.added.forEach { nm.notify(ADDED + it.expenseId, idOf(it.expenseId), added(context, it)) }
        refreshSummary(context)
    }

    /** Takes down the notification of pending payment [key] (after Add or Skip). */
    fun dismissAsk(context: Context, key: String) {
        context.getSystemService(NotificationManager::class.java).cancel(ASK + key, idOf(key))
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
        val pending = mine.count { it.tag?.startsWith(ASK) == true }
        val text = PaymentTexts.summary(pending, mine.size - pending)
        val n = base(context)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setGroupSummary(true)
            .setContentIntent(if (pending > 0) reviewIntent(context, "summary") else openIntent(context))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden(context))
            .build()
        nm.notify(Notifications.ID_PAYMENTS_SUMMARY, n)
    }

    private fun ask(context: Context, p: PendingPayment): Notification {
        val text = PaymentTexts.ask(p)
        return base(context)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.text))
            .setContentIntent(reviewIntent(context, p.key))
            .addAction(0, "Add", action(context, PaymentActionReceiver.ACTION_ADD, p.key))
            .addAction(0, "Skip", action(context, PaymentActionReceiver.ACTION_SKIP, p.key))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden(context))
            .build()
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

    /** Opens the app on the review of payments waiting for the user. */
    private fun reviewIntent(context: Context, key: String): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_PAYMENTS).setData(Uri.parse("cove-pay://review/$key"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String, key: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, PaymentActionReceiver::class.java).setAction(action).setData(Uri.parse("cove-pay://$action/$key")).putExtra(PaymentActionReceiver.EXTRA_KEY, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
