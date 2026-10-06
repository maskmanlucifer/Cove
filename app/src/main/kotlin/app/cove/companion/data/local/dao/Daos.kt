package app.cove.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.BriefEntity
import app.cove.companion.data.local.entity.DecisionEntity
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.HabitEntity
import app.cove.companion.data.local.entity.HabitLogEntity
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.local.entity.OutboxEntity
import app.cove.companion.data.local.entity.SearchIndexEntity
import app.cove.companion.data.local.entity.SettingsEntity
import app.cove.companion.data.local.entity.SuggestionPrefEntity
import app.cove.companion.data.local.entity.SyncConflictEntity
import app.cove.companion.data.local.entity.SyncStateEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.data.local.entity.VoiceCommandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 'me'")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 'me'")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(settings: SettingsEntity)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms WHERE deletedAt IS NULL ORDER BY minutes")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE deletedAt IS NULL AND enabled = 1")
    suspend fun enabled(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: String): AlarmEntity?

    @Upsert
    suspend fun upsert(alarm: AlarmEntity)
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_categories WHERE deletedAt IS NULL ORDER BY sort, name")
    fun observeCategories(): Flow<List<TodoCategoryEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL ORDER BY done, sort, updatedAt")
    fun observeTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun get(id: String): TodoEntity?

    @Upsert
    suspend fun upsert(todo: TodoEntity)

    @Upsert
    suspend fun upsertAll(todos: List<TodoEntity>)

    @Upsert
    suspend fun upsertCategory(category: TodoCategoryEntity)

    @Query("SELECT COUNT(*) FROM todo_categories")
    suspend fun categoryCount(): Int
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE deletedAt IS NULL AND startAt BETWEEN :from AND :to ORDER BY startAt")
    fun observeBetween(from: Long, to: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE deletedAt IS NULL AND startAt >= :from ORDER BY startAt LIMIT :limit")
    fun observeUpcoming(from: Long, limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE deletedAt IS NULL AND `repeat` != 'none' AND startAt <= :to")
    fun observeRepeating(to: Long): Flow<List<EventEntity>>

    @Upsert
    suspend fun upsert(event: EventEntity)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY sort, name")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_logs WHERE deletedAt IS NULL AND day BETWEEN :from AND :to")
    fun observeLogs(from: Long, to: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: String): HabitEntity?

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND day = :day")
    suspend fun log(habitId: String, day: Long): HabitLogEntity?

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Upsert
    suspend fun upsertLog(log: HabitLogEntity)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_categories WHERE deletedAt IS NULL ORDER BY sort, name")
    fun observeCategories(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND spentAt BETWEEN :from AND :to ORDER BY spentAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: String): ExpenseEntity?

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Upsert
    suspend fun upsertCategory(category: ExpenseCategoryEntity)

    @Query("SELECT COUNT(*) FROM expense_categories")
    suspend fun categoryCount(): Int

    @Query("SELECT * FROM expense_categories WHERE id = :id")
    suspend fun getCategory(id: String): ExpenseCategoryEntity?

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND categoryId = :categoryId")
    suspend fun inCategory(categoryId: String): List<ExpenseEntity>
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NULL ORDER BY day DESC, createdAt DESC")
    fun observeEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun get(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_media WHERE deletedAt IS NULL AND entryId = :entryId")
    fun observeMedia(entryId: String): Flow<List<JournalMediaEntity>>

    @Query("SELECT * FROM journal_media WHERE deletedAt IS NULL AND uploadState != 'done'")
    suspend fun pendingUploads(): List<JournalMediaEntity>

    @Upsert
    suspend fun upsert(entry: JournalEntryEntity)

    @Upsert
    suspend fun upsertMedia(media: JournalMediaEntity)

    @Upsert
    suspend fun upsertIndex(index: SearchIndexEntity)

    @Query("SELECT * FROM search_index")
    suspend fun allIndex(): List<SearchIndexEntity>

    @Query("SELECT * FROM search_index WHERE entryId = :entryId")
    suspend fun index(entryId: String): SearchIndexEntity?

    @Query("SELECT * FROM journal_media WHERE deletedAt IS NULL AND entryId = :entryId")
    suspend fun mediaOf(entryId: String): List<JournalMediaEntity>
}

@Dao
interface AssistantDao {
    @Query("SELECT * FROM decisions WHERE status = 'shown' ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveDecision(): Flow<DecisionEntity?>

    @Query("SELECT * FROM decisions WHERE kind = :kind AND status = 'confirmed' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentConfirmed(kind: String, limit: Int): List<DecisionEntity>

    @Upsert
    suspend fun upsert(decision: DecisionEntity)

    @Query("SELECT muted FROM suggestion_prefs WHERE kind = :kind")
    suspend fun isMuted(kind: String): Boolean?

    @Upsert
    suspend fun setPref(pref: SuggestionPrefEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: VoiceCommandEntity)

    @Query("SELECT * FROM voice_commands ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastCommand(): VoiceCommandEntity?

    @Query("SELECT * FROM voice_commands WHERE id = :id")
    suspend fun command(id: String): VoiceCommandEntity?

    @Query("UPDATE voice_commands SET undone = 1 WHERE id = :id")
    suspend fun markUndone(id: String)

    @Query("SELECT * FROM briefs WHERE day = :day")
    fun observeBrief(day: Long): Flow<BriefEntity?>

    @Upsert
    suspend fun upsertBrief(brief: BriefEntity)
}

/** Outbox row key. */
data class PendingKey(val tableName: String, val rowId: String)

@Dao
interface SyncDao {
    @Insert
    suspend fun enqueue(entry: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY seq LIMIT :limit")
    suspend fun pending(limit: Int): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE seq <= :seq")
    suspend fun clearUpTo(seq: Long)

    @Query("SELECT * FROM sync_state WHERE tableName = :table")
    suspend fun state(table: String): SyncStateEntity?

    @Upsert
    suspend fun setState(state: SyncStateEntity)

    @Query("SELECT COUNT(*) FROM outbox")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM outbox WHERE seq > :after ORDER BY seq LIMIT :limit")
    suspend fun pendingAfter(after: Long, limit: Int): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE seq IN (:seqs)")
    suspend fun clear(seqs: List<Long>)

    @Query("DELETE FROM outbox WHERE tableName = :table AND rowId = :id")
    suspend fun clearRow(table: String, id: String)

    @Query("SELECT DISTINCT tableName, rowId FROM outbox")
    suspend fun pendingKeys(): List<PendingKey>

    @Upsert
    suspend fun saveConflict(conflict: SyncConflictEntity)

    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt")
    suspend fun conflicts(): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt")
    fun observeConflicts(): Flow<List<SyncConflictEntity>>

    @Query("DELETE FROM sync_conflicts WHERE tableName = :table AND rowId = :id")
    suspend fun deleteConflict(table: String, id: String)
}
