package app.cove.companion.data.sync

import app.cove.companion.core.newId
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.SyncConflictEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.data.repo.PlanRepository
import app.cove.companion.data.repo.TodoRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** How the user settled a conflict. */
enum class Resolution { KeepThisPhone, KeepOther, KeepBoth }

/** Applies a [Resolution] through the repositories (so the outbox sees it) and asks the engine to sync. */
class ConflictResolver(
    private val db: CoveDatabase,
    private val plan: PlanRepository,
    private val todos: TodoRepository,
    private val requestSync: () -> Unit,
) {
    /** Settles [conflict]; the local row stays pending in the outbox and uploads on the next sync. */
    suspend fun resolve(conflict: SyncConflictEntity, how: Resolution) {
        val remote = Json.parseToJsonElement(conflict.remoteJson).jsonObject
        when (how) {
            Resolution.KeepThisPhone -> Unit
            Resolution.KeepOther -> save(conflict.tableName, remote)
            Resolution.KeepBoth -> save(conflict.tableName, JsonObject(remote + ("id" to JsonPrimitive(newId()))))
        }
        db.sync().deleteConflict(conflict.tableName, conflict.rowId)
        requestSync()
    }

    private suspend fun save(table: String, row: JsonObject) {
        when (table) {
            "alarms" -> plan.saveAlarm(alarm(row))
            "events" -> plan.saveEvent(event(row))
            "todos" -> todos.save(todo(row))
        }
    }

    private fun str(r: JsonObject, k: String) = RowJson.string(r, k)
    private fun long(r: JsonObject, k: String) = (r[k] as? JsonPrimitive)?.longOrNull
    private fun bool(r: JsonObject, k: String) = (r[k] as? JsonPrimitive)?.booleanOrNull

    private fun alarm(r: JsonObject) = AlarmEntity(
        id = str(r, "id").orEmpty(), label = str(r, "label").orEmpty(), minutes = long(r, "minutes")?.toInt() ?: 0,
        daysMask = long(r, "days_mask")?.toInt() ?: 0, kind = str(r, "kind") ?: "wake", sound = str(r, "sound") ?: "Soft rise",
        gentleRise = bool(r, "gentle_rise") ?: true, snoozeMinutes = long(r, "snooze_minutes")?.toInt() ?: 9,
        enabled = bool(r, "enabled") ?: true, deletedAt = long(r, "deleted_at"),
    )

    private fun event(r: JsonObject) = EventEntity(
        id = str(r, "id").orEmpty(), title = str(r, "title").orEmpty(), startAt = long(r, "start_at") ?: 0, endAt = long(r, "end_at"),
        place = str(r, "place"), notes = str(r, "notes"), repeat = str(r, "repeat") ?: "none",
        remindBeforeMin = long(r, "remind_before_min")?.toInt(), deletedAt = long(r, "deleted_at"),
    )

    private fun todo(r: JsonObject) = TodoEntity(
        id = str(r, "id").orEmpty(), categoryId = str(r, "category_id"), title = str(r, "title").orEmpty(), dueAt = long(r, "due_at"),
        remind = bool(r, "remind") ?: false, done = bool(r, "done") ?: false, doneAt = long(r, "done_at"),
        sort = long(r, "sort")?.toInt() ?: 0, source = str(r, "source") ?: "manual", deletedAt = long(r, "deleted_at"),
    )
}
