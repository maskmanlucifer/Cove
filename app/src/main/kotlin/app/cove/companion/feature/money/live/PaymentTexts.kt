package app.cove.companion.feature.money.live

import app.cove.companion.core.rupees
import app.cove.companion.data.sms.AddedPayment
import app.cove.companion.data.sms.PendingPayment

/** Title and text of a notification. */
data class NoteText(val title: String, val text: String)

/** Wording of the payment notifications and the Money row. Pure, so it is unit-tested; calm and short, no jargon. */
object PaymentTexts {
    /** "₹8 spent · Pluxee wallet" or "₹150 received · Refund Co", for a payment that waits for Add or Skip. */
    fun ask(p: PendingPayment): NoteText = NoteText(
        "${rupees(p.amountPaise)} ${if (p.kind == "received") "received" else "spent"} · ${p.label}",
        p.duplicateLine ?: "Add it to Money?",
    )

    /** "Added ₹8 · Food" (or "Added ₹150 received"), for a payment Cove added; the action is Undo. */
    fun added(a: AddedPayment): NoteText {
        val title = if (a.kind == "received") "Added ${rupees(a.amountPaise)} received" else "Added ${rupees(a.amountPaise)}" + (a.categoryName?.let { " · $it" } ?: "")
        return NoteText(title, a.note)
    }

    /** Privacy statement shown wherever the mode is chosen. Accurate: see `docs/SMS_IMPORT.md`. */
    const val PRIVACY = "Messages are read on this phone only. Cove keeps just the amount, merchant and category of payments you accept. " +
        "The message text is never stored, uploaded, synced or sent to AI."

    /** The permission guide: needs both permissions, and sideloaded apps may need \"Allow restricted settings\" first. */
    const val GUIDE_TITLE = "Messages are off for Cove"
    const val GUIDE_BODY = "Allow “Receive text messages” and “Read text messages” for Cove in Settings. On Android 13 and newer, " +
        "if they look greyed out, open the three dots in App info and choose “Allow restricted settings” first."

    /** What the lock screen shows instead of the amount. */
    const val HIDDEN = "A payment was found in your messages"

    /** The group summary: "3 new payments" with "Added 1 · 2 to look at". */
    fun summary(pending: Int, added: Int): NoteText {
        val total = pending + added
        val parts = buildList {
            if (added > 0) add("Added $added")
            if (pending > 0) add("$pending to look at")
        }
        return NoteText(if (total == 1) "1 new payment" else "$total new payments", parts.joinToString(" · "))
    }

    /** The quiet row on Money: "2 new payments found in your messages · Review". */
    fun moneyRow(pending: Int): String =
        (if (pending == 1) "1 new payment" else "$pending new payments") + " found in your messages · Review"
}
