package app.cove.companion.data.sms

import app.cove.companion.data.local.entity.SmsPendingEntity

/** The candidate a row left by the old "Ask me" mode stands for, so live capture can settle it. */
internal fun SmsPendingEntity.toCandidate(): Candidate = Candidate(
    ParsedSms(
        amountPaise, if (direction == "credit") Direction.Credit else Direction.Debit, merchant, at, dateFromText, last4, paidWith, ref, bank, confidence, payeeKey,
    ),
    messageKeys.split(',').filter { it.isNotEmpty() }.map { MessageId(it, null) },
)
