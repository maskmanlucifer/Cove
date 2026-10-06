package app.cove.companion.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Conventions: client-generated UUID `id`, `updatedAt` epoch millis (sync cursor),
 * `deletedAt` set instead of deleting so removals can sync. Money is in paise.
 */

/** Single settings row (id = [ID]). */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: String = ID,
    val displayName: String = "",
    val wakeMinutes: Int = 6 * 60 + 30,
    val theme: String = "system",
    val textScale: Float = 1f,
    val spokenReplies: Boolean = true,
    val reduceMotion: String = "system",
    val nudgeMode: String = "bundled",
    val oneThingMode: Boolean = false,
    /** Epoch millis when One-thing mode switches itself off again (wind-down runs until the wake alarm); 0 = until switched off. */
    val oneThingUntil: Long = 0,
    val briefOn: Boolean = true,
    val suggestionsOn: Boolean = true,
    val biometricLock: Boolean = false,
    val onboarded: Boolean = false,
    /** `balanced` (WebP 80) or `high` (WebP 90) for new journal photos. */
    val photoQuality: String = "balanced",
    /** When true, media uploads wait for an unmetered network. */
    val uploadOnWifiOnly: Boolean = true,
    /** `immediately`, `1min` or `5min`: how long the app may stay in the background before the lock returns. */
    val lockAfter: String = "1min",
    /** With the app lock on, hide the app's content in recents and screenshots even while unlocked. */
    val hideInRecents: Boolean = true,
    val updatedAt: Long = 0,
) {
    companion object {
        const val ID = "me"
    }
}

/** Alarm; `daysMask` bit 0 = Monday … bit 6 = Sunday, 0 = once. */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String,
    val label: String,
    val minutes: Int,
    val daysMask: Int = 0,
    val kind: String = "wake",
    val sound: String = "Soft rise",
    val gentleRise: Boolean = true,
    val snoozeMinutes: Int = 9,
    val enabled: Boolean = true,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

@Entity(tableName = "todo_categories")
data class TodoCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sort: Int = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

@Entity(tableName = "todos", indices = [Index("categoryId")])
data class TodoEntity(
    @PrimaryKey val id: String,
    val categoryId: String?,
    val title: String,
    val dueAt: Long? = null,
    val remind: Boolean = false,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val sort: Int = 0,
    val source: String = "manual",
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** Calendar-style item shown on the schedule. `repeat` is none|daily|weekly|monthly. */
@Entity(tableName = "events", indices = [Index("startAt")])
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startAt: Long,
    val endAt: Long?,
    val place: String? = null,
    val notes: String? = null,
    val repeat: String = "none",
    val remindBeforeMin: Int? = 30,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** Habit; `cadence` is daily|days|weekly, `daysMask` as in [AlarmEntity]. */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val cadence: String = "daily",
    val daysMask: Int = 127,
    val remindMinutes: Int? = null,
    val afterWakeUp: Boolean = false,
    val showOnToday: Boolean = true,
    val sort: Int = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

@Entity(tableName = "habit_logs", indices = [Index(value = ["habitId", "day"], unique = true)])
data class HabitLogEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val day: Long,
    val count: Int = 1,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** `kind` is spending|income. */
@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String = "spending",
    val budgetPaise: Long = 0,
    val carryOver: Boolean = false,
    val alertAt80: Boolean = true,
    val sort: Int = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** `kind` is spent|received; `source` is manual|voice|sms. */
@Entity(tableName = "expenses", indices = [Index("spentAt"), Index("categoryId")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amountPaise: Long,
    val kind: String = "spent",
    val categoryId: String?,
    val note: String = "",
    val paidWith: String = "UPI",
    val spentAt: Long,
    val source: String = "manual",
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** Journal entry; `mood` is the user's own pick (calm|good|tired|low|…). */
@Entity(tableName = "journal_entries", indices = [Index("day")])
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val day: Long,
    val title: String = "",
    val body: String = "",
    val mood: String? = null,
    val createdAt: Long,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** Photo or voice note; `uploadState` is pending|done|failed (Drive upload). */
@Entity(tableName = "journal_media", indices = [Index("entryId")])
data class JournalMediaEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val kind: String,
    val localPath: String,
    val thumbPath: String? = null,
    val durationMs: Long? = null,
    val bytes: Long = 0,
    val uploadState: String = "pending",
    val driveFileId: String? = null,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** Suggestion shown on Today; `reasons` is a JSON string array. */
@Entity(tableName = "decisions")
data class DecisionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val reasons: String,
    val status: String = "shown",
    val createdAt: Long,
    val updatedAt: Long = 0,
)

/** Kinds the user muted with "Stop suggesting this". */
@Entity(tableName = "suggestion_prefs")
data class SuggestionPrefEntity(
    @PrimaryKey val kind: String,
    val muted: Boolean,
    val updatedAt: Long = 0,
)

/** Executed voice command with data needed to undo it. */
@Entity(tableName = "voice_commands")
data class VoiceCommandEntity(
    @PrimaryKey val id: String,
    val transcript: String,
    val intent: String,
    val undoPayload: String?,
    val undone: Boolean = false,
    val createdAt: Long,
)

/** Cached morning brief for a day; `segments` is a JSON array of {title, text}. */
@Entity(tableName = "briefs")
data class BriefEntity(
    @PrimaryKey val day: Long,
    val segments: String,
    val generatedAt: Long,
    val durationSec: Int = 0,
)

/** Local-only on-device journal insights; never synced. */
@Entity(tableName = "search_index")
data class SearchIndexEntity(
    @PrimaryKey val entryId: String,
    val summary: String = "",
    val aiMood: String? = null,
    val transcript: String = "",
    val caption: String = "",
    val tags: String = "",
    val embedding: ByteArray? = null,
    val updatedAt: Long = 0,
) {
    override fun equals(other: Any?) = other is SearchIndexEntity && entryId == other.entryId && updatedAt == other.updatedAt
    override fun hashCode() = entryId.hashCode()
}

/** Pending change for the sync worker (local only). */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val tableName: String,
    val rowId: String,
    val op: String,
    val queuedAt: Long,
)

/** Per-table sync cursor (local only). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val tableName: String,
    val lastPullAt: Long = 0,
)
