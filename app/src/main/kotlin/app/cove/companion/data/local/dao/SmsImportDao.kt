package app.cove.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.SmsImportLogEntity

/** Access to the local `sms_import_log` and the expense lookups the import needs. */
@Dao
interface SmsImportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<SmsImportLogEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(rows: List<SmsImportLogEntity>)

    @Query("SELECT `key`, outcome, parserVersion FROM sms_import_log WHERE `key` IN (:keys)")
    suspend fun rowsFor(keys: List<String>): List<LoggedKey>

    @Query("SELECT providerId FROM sms_import_log WHERE providerId IN (:ids) AND outcome != 'ignored'")
    suspend fun decidedProviderIds(ids: List<Long>): List<Long>

    @Query("SELECT * FROM sms_import_log WHERE outcome != 'ignored' AND externalRef IN (:refs)")
    suspend fun byRefs(refs: List<String>): List<SmsImportLogEntity>

    @Query("SELECT * FROM sms_import_log WHERE outcome IN ('imported', 'duplicate', 'skipped') AND messageAt BETWEEN :from AND :to")
    suspend fun decidedBetween(from: Long, to: Long): List<SmsImportLogEntity>

    @Query("SELECT MAX(messageAt) FROM sms_import_log WHERE outcome IN ('imported', 'duplicate', 'skipped')")
    suspend fun lastDecidedAt(): Long?

    @Query("SELECT COUNT(*) FROM sms_import_log")
    suspend fun count(): Int

    @Query("DELETE FROM sms_import_log")
    suspend fun clear()

    @Query("DELETE FROM sms_import_log WHERE batchId = :batchId")
    suspend fun deleteBatch(batchId: String)

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND source = 'sms' AND externalRef IS NOT NULL")
    suspend fun importedExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND spentAt BETWEEN :from AND :to")
    suspend fun expensesBetween(from: Long, to: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND externalRef IN (:refs)")
    suspend fun expensesByRefs(refs: List<String>): List<ExpenseEntity>
}

/** A logged message's key with its outcome and the parser version that judged it. */
data class LoggedKey(val key: String, val outcome: String, val parserVersion: Int)
