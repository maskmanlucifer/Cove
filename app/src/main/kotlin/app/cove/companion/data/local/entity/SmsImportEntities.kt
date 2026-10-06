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
