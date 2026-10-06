package app.cove.companion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.cove.companion.data.local.dao.AlarmDao
import app.cove.companion.data.local.dao.AssistantDao
import app.cove.companion.data.local.dao.EventDao
import app.cove.companion.data.local.dao.ExpenseDao
import app.cove.companion.data.local.dao.HabitDao
import app.cove.companion.data.local.dao.JournalDao
import app.cove.companion.data.local.dao.SettingsDao
import app.cove.companion.data.local.dao.SyncDao
import app.cove.companion.data.local.dao.TodoDao
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
import app.cove.companion.data.local.entity.SyncStateEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.data.local.entity.VoiceCommandEntity

/** Room database; the open-helper factory lets the security layer supply SQLCipher. */
@Database(
    entities = [
        SettingsEntity::class, AlarmEntity::class, TodoCategoryEntity::class, TodoEntity::class,
        EventEntity::class, HabitEntity::class, HabitLogEntity::class, ExpenseCategoryEntity::class,
        ExpenseEntity::class, JournalEntryEntity::class, JournalMediaEntity::class, DecisionEntity::class,
        SuggestionPrefEntity::class, VoiceCommandEntity::class, BriefEntity::class,
        SearchIndexEntity::class, OutboxEntity::class, SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CoveDatabase : RoomDatabase() {
    abstract fun settings(): SettingsDao
    abstract fun alarms(): AlarmDao
    abstract fun todos(): TodoDao
    abstract fun events(): EventDao
    abstract fun habits(): HabitDao
    abstract fun expenses(): ExpenseDao
    abstract fun journal(): JournalDao
    abstract fun assistant(): AssistantDao
    abstract fun sync(): SyncDao

    companion object {
        fun create(context: Context, factory: SupportSQLiteOpenHelper.Factory? = null): CoveDatabase =
            Room.databaseBuilder(context, CoveDatabase::class.java, "cove.db")
                .apply { if (factory != null) openHelperFactory(factory) }
                .build()
    }
}
