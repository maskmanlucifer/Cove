package app.cove.companion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import app.cove.companion.data.local.entity.SyncConflictEntity
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
        SearchIndexEntity::class, OutboxEntity::class, SyncStateEntity::class, SyncConflictEntity::class,
    ],
    version = 4,
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
        /** Adds the local-only `sync_conflicts` table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_conflicts` (`tableName` TEXT NOT NULL, `rowId` TEXT NOT NULL, " +
                        "`localJson` TEXT NOT NULL, `remoteJson` TEXT NOT NULL, `remoteDevice` TEXT NOT NULL, " +
                        "`localAt` INTEGER NOT NULL, `remoteAt` INTEGER NOT NULL, `detectedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`tableName`, `rowId`))",
                )
            }
        }

        /** Adds the `photoQuality` and `uploadOnWifiOnly` settings. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `photoQuality` TEXT NOT NULL DEFAULT 'balanced'")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `uploadOnWifiOnly` INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** Adds the app-lock `lockAfter` and `hideInRecents` settings. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `lockAfter` TEXT NOT NULL DEFAULT '1min'")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `hideInRecents` INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** File name of the database in the app's databases directory. */
        const val NAME = "cove.db"

        fun create(context: Context, factory: SupportSQLiteOpenHelper.Factory? = null): CoveDatabase =
            Room.databaseBuilder(context, CoveDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .apply { if (factory != null) openHelperFactory(factory) }
                .build()
    }
}
