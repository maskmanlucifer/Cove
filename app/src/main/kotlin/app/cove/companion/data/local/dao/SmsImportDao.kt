package app.cove.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.SmsImportLogEntity
import app.cove.companion.data.local.entity.SmsPendingEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM sms_import_log WHERE outcome IN ('imported', 'duplicate', 'skipped', 'pending') AND messageAt BETWEEN :from AND :to")
    suspend fun decidedBetween(from: Long, to: Long): List<SmsImportLogEntity>

    @Query("SELECT MAX(messageAt) FROM sms_import_log WHERE outcome IN ('imported', 'duplicate', 'skipped')")
    suspend fun lastDecidedAt(): Long?

    @Query("SELECT COUNT(*) FROM sms_import_log")
    suspend fun count(): Int

    /** Clears decisions and ignored messages; payments still waiting for the user keep their marker so they are not found twice. */
    @Query("DELETE FROM sms_import_log WHERE outcome != 'pending'")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPending(rows: List<SmsPendingEntity>)

    @Query("SELECT * FROM sms_pending ORDER BY `at` DESC")
    suspend fun pending(): List<SmsPendingEntity>

    @Query("SELECT * FROM sms_pending WHERE `key` = :key")
    suspend fun pendingByKey(key: String): SmsPendingEntity?

    @Query("SELECT COUNT(*) FROM sms_pending")
    fun observePendingCount(): Flow<Int>

    /** Returns how many rows went, so a repeated Add or Skip finds nothing left to do. */
    @Query("DELETE FROM sms_pending WHERE `key` IN (:keys)")
    suspend fun deletePending(keys: List<String>): Int

    /** Turns the imported log rows of an expense into skipped ones, so an undone automatic payment is not added again. */
    @Query("UPDATE sms_import_log SET outcome = 'skipped' WHERE expenseId = :expenseId AND outcome = 'imported'")
    suspend fun skipImportedFor(expenseId: String)

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
