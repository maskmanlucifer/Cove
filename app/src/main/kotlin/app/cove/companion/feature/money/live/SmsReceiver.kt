package app.cove.companion.feature.money.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.cove.companion.container
import app.cove.companion.data.sms.CaptureMode
import app.cove.companion.data.sms.CaptureReport
import app.cove.companion.feature.today.oneThingActive
import app.cove.companion.resilience.CrashHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Longest the receiver may take in all (the system allows about ten seconds), including waiting for the database. */
private const val HANDLER_TIMEOUT_MS = 8_000L

/**
 * Looks at each incoming text message for a payment, with the app closed (see `docs/SMS_IMPORT.md`).
 * The text is read from the message parts, parsed in memory and dropped: it is never stored, logged or sent anywhere.
 *
 * Guarded like the other background components (`docs/RESILIENCE.md`): it does nothing at all when the mode is Off,
 * finishes within [HANDLER_TIMEOUT_MS], and if the database cannot be read it drops the message silently (the catch-up scan
 * reads the inbox again when the app opens). It never throws.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext
        val c = runCatching { app.container }.getOrNull() ?: return
        if (c.smsCapturePrefs.mode.value == CaptureMode.Off) return
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val sender = parts.first().originatingAddress
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val receivedAt = c.clock.now()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + CrashHandler.coroutineHandler("sms-receiver")).launch {
            try {
                CrashHandler.guarded("sms-receiver") {
                    withTimeout(HANDLER_TIMEOUT_MS) {
                        if (withTimeoutOrNull(HANDLER_TIMEOUT_MS - 2_000) { c.dbReady.first { it } } != true) return@withTimeout
                        val report = c.smsCapture.onMessage(sender, body, receivedAt)
                        val quiet = runCatching { oneThingActive(c.settings.settings.first(), c.clock.now()) }.getOrDefault(false)
                        PaymentNotifier.post(app, report, quiet)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** The Add, Skip and Undo buttons on payment notifications. Each is safe to run twice: the second finds nothing left to do. */
class PaymentActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + CrashHandler.coroutineHandler("payment-action")).launch {
            try {
                CrashHandler.guarded("payment-action") { withTimeout(HANDLER_TIMEOUT_MS) { handle(app, action, key) } }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: Context, action: String, key: String) {
        val c = app.container
        when (action) {
            ACTION_ADD -> {
                PaymentNotifier.dismissAsk(app, key)
                if (withTimeoutOrNull(HANDLER_TIMEOUT_MS - 2_000) { c.dbReady.first { it } } != true) return
                c.smsCapture.accept(key)?.let { PaymentNotifier.post(app, CaptureReport(added = listOf(it)), quiet = false) }
            }
            ACTION_SKIP -> {
                PaymentNotifier.dismissAsk(app, key)
                if (withTimeoutOrNull(HANDLER_TIMEOUT_MS - 2_000) { c.dbReady.first { it } } != true) return
                c.smsCapture.skip(key)
            }
            ACTION_UNDO -> {
                PaymentNotifier.dismissAdded(app, key)
                if (withTimeoutOrNull(HANDLER_TIMEOUT_MS - 2_000) { c.dbReady.first { it } } != true) return
                c.smsCapture.undo(key)
            }
        }
        PaymentNotifier.refreshSummary(app)
    }

    companion object {
        const val ACTION_ADD = "app.cove.companion.payment.ADD"
        const val ACTION_SKIP = "app.cove.companion.payment.SKIP"
        const val ACTION_UNDO = "app.cove.companion.payment.UNDO"
        const val EXTRA_KEY = "key"
    }
}
