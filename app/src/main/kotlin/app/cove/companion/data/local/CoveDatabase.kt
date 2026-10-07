package app.cove.companion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import app.cove.companion.resilience.CrashHandler
import kotlinx.coroutines.Dispatchers
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.cove.companion.data.local.dao.AlarmDao
import app.cove.companion.data.local.dao.AssistantDao
import app.cove.companion.data.local.dao.EventDao
import app.cove.companion.data.local.dao.ExpenseDao
import app.cove.companion.data.local.dao.HabitDao
import app.cove.companion.data.local.dao.JournalDao
import app.cove.companion.data.local.dao.SettingsDao
import app.cove.companion.data.local.dao.SmsImportDao
import app.cove.companion.data.local.dao.SyncDao
import app.cove.companion.data.local.dao.TodoDao
import app.cove.companion.data.local.dao.TrainingDao
import app.cove.companion.data.local.entity.BodyWeightEntity
import app.cove.companion.data.local.entity.DayOverrideEntity
import app.cove.companion.data.local.entity.ExerciseLogEntity
import app.cove.companion.data.local.entity.PlanExerciseEntity
import app.cove.companion.data.local.entity.TrainingSettingsEntity
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.BriefEntity
import app.cove.companion.data.local.entity.CategoryMemoryEntity
import app.cove.companion.data.local.entity.PayeeMemoryEntity
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
import app.cove.companion.data.local.entity.SmsImportLogEntity
import app.cove.companion.data.local.entity.SmsPendingEntity
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
        CategoryMemoryEntity::class,
        PlanExerciseEntity::class, DayOverrideEntity::class, ExerciseLogEntity::class,
        BodyWeightEntity::class, TrainingSettingsEntity::class,
        SmsImportLogEntity::class, PayeeMemoryEntity::class, SmsPendingEntity::class,
    ],
    version = 11,
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
    abstract fun training(): TrainingDao
    abstract fun smsImport(): SmsImportDao

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

        /** Adds the `oneThingUntil` setting. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `oneThingUntil` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds category `keywords` and the learned `category_memory` table. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense_categories` ADD COLUMN `keywords` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_memory` (`token` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                        "`count` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`token`))",
                )
            }
        }

        /** The first training tables (programme, sessions, sets); replaced by [MIGRATION_7_8]. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                OldTrainingMigration.STATEMENTS.forEach { db.execSQL(it) }
            }
        }

        /** Replaces the programme/session training tables with the weekday plan and day logs (see `docs/TRAINING.md`). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                TrainingMigration.ALL.forEach { db.execSQL(it) }
            }
        }

        /** Adds `expenses.externalRef` and the local-only `sms_import_log` (see `docs/SMS_IMPORT.md`). */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `externalRef` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sms_import_log` (`key` TEXT NOT NULL, `providerId` INTEGER, `outcome` TEXT NOT NULL, " +
                        "`amountPaise` INTEGER NOT NULL, `kind` TEXT NOT NULL, `merchant` TEXT, `last4` TEXT, `externalRef` TEXT, " +
                        "`expenseId` TEXT, `batchId` TEXT, `messageAt` INTEGER NOT NULL, `loggedAt` INTEGER NOT NULL, " +
                        "`parserVersion` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                )
                listOf("providerId", "externalRef", "messageAt", "batchId", "expenseId").forEach {
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_import_log_$it` ON `sms_import_log` (`$it`)")
                }
            }
        }

        /** Adds `expenses.payeeKey` (indexed) and the synced `payee_memory` table (see `docs/CATEGORIZATION.md`). */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `payeeKey` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_payeeKey` ON `expenses` (`payeeKey`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `payee_memory` (`payeeKey` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `label` TEXT, " +
                        "`displayName` TEXT NOT NULL, `count` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`payeeKey`))",
                )
            }
        }

        /** Adds the local-only `sms_pending` table for payments found in incoming messages (see `docs/SMS_IMPORT.md`). */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sms_pending` (`key` TEXT NOT NULL, `messageKeys` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, " +
                        "`direction` TEXT NOT NULL, `merchant` TEXT, `at` INTEGER NOT NULL, `dateFromText` INTEGER NOT NULL, `last4` TEXT, " +
                        "`paidWith` TEXT NOT NULL, `ref` TEXT, `bank` TEXT, `confidence` REAL NOT NULL, `payeeKey` TEXT, " +
                        "`matchExpenseId` TEXT, `matchNote` TEXT, `matchAmountPaise` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_pending_at` ON `sms_pending` (`at`)")
            }
        }

        /** File name of the database in the app's databases directory. */
        const val NAME = "cove.db"

        fun create(context: Context, factory: SupportSQLiteOpenHelper.Factory? = null): CoveDatabase =
            Room.databaseBuilder(context, CoveDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                // Room's own background coroutines (invalidation tracking) would crash the process on a failing database; report instead.
                .setQueryCoroutineContext(Dispatchers.IO + CrashHandler.coroutineHandler("room"))
                .apply { if (factory != null) openHelperFactory(factory) }
                .build()
    }
}
