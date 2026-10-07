package app.cove.companion.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One decision about one message in "Import from messages". Local-only: never synced and never in backups
 * (`SyncTables` does not list it). It stores parsed fields and a hash of the message, never its text.
 *
 * @property key `msg:` + hash of (sender, normalized body), or `ext:` + reference for rows rebuilt from expenses.
 * @property outcome imported | duplicate | ignored | skipped (see `SmsImportOutcome`).
 */
@Entity(
    tableName = "sms_import_log",
    indices = [Index("providerId"), Index("externalRef"), Index("messageAt"), Index("batchId"), Index("expenseId")],
)
data class SmsImportLogEntity(
    @PrimaryKey val key: String,
    val providerId: Long? = null,
    val outcome: String,
    val amountPaise: Long = 0,
    val kind: String = "",
    val merchant: String? = null,
    val last4: String? = null,
    val externalRef: String? = null,
    val expenseId: String? = null,
    val batchId: String? = null,
    val messageAt: Long,
    val loggedAt: Long,
    val parserVersion: Int = 0,
)

/**
 * A payment found in an incoming message that waits for the user ("Ask me", or a possible duplicate in any mode).
 * Local-only like [SmsImportLogEntity] and never in `SyncTables` or backups. Parsed fields only: the message text
 * is never stored. Cleared when the payment is added, skipped or decided in the import review.
 *
 * @property key key of the first message ([app.cove.companion.data.sms.Candidate.id]).
 * @property messageKeys keys of every message that described this payment, comma separated.
 * @property direction `debit` or `credit`.
 * @property categoryId suggested spending category at the time it was found; null for money received.
 * @property matchExpenseId existing expense this payment may repeat, with [matchNote] and [matchAmountPaise] for the explanation.
 */
@Entity(tableName = "sms_pending", indices = [Index("at")])
data class SmsPendingEntity(
    @PrimaryKey val key: String,
    val messageKeys: String,
    val amountPaise: Long,
    val direction: String,
    val merchant: String?,
    val at: Long,
    val dateFromText: Boolean,
    val last4: String?,
    val paidWith: String,
    val ref: String?,
    val bank: String?,
    val confidence: Float,
    val payeeKey: String?,
    val categoryId: String?,
    val matchExpenseId: String?,
    val matchNote: String?,
    val matchAmountPaise: Long?,
    val createdAt: Long,
)
