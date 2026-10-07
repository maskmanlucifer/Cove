package app.cove.companion.data.sms

import app.cove.companion.data.local.entity.SmsPendingEntity

/** The pending row for a payment waiting for the user: parsed fields only, never message text. */
internal fun ReviewItem.toPending(now: Long): SmsPendingEntity {
    val c = candidate
    val t = c.tx
    return SmsPendingEntity(
        key = c.id, messageKeys = c.messages.joinToString(",") { it.key }, amountPaise = t.amountPaise,
        direction = if (t.direction == Direction.Credit) "credit" else "debit", merchant = t.merchant, at = t.at, dateFromText = t.dateFromText,
        last4 = t.last4, paidWith = t.paidWith, ref = t.ref, bank = t.bank, confidence = t.confidence, payeeKey = t.payeeKey,
        matchExpenseId = match?.expenseId, matchNote = match?.note, matchAmountPaise = match?.amountPaise, createdAt = now,
    )
}

/** The candidate a pending row stands for, ready for the import review. */
internal fun SmsPendingEntity.toCandidate(): Candidate = Candidate(
    ParsedSms(
        amountPaise, if (direction == "credit") Direction.Credit else Direction.Debit, merchant, at, dateFromText, last4, paidWith, ref, bank, confidence, payeeKey,
    ),
    messageKeys.split(',').filter { it.isNotEmpty() }.map { MessageId(it, null) },
)
