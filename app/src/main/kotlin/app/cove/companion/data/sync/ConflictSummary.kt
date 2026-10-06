package app.cove.companion.data.sync

import app.cove.companion.core.clockText
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.core.clock
import app.cove.companion.data.local.entity.SyncConflictEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** One side of a conflict as the screen shows it, e.g. "6:00 pm" over "This phone · 8:12 am". */
data class ConflictSide(val value: String, val meta: String)

/** Screen-ready description of a [SyncConflictEntity]. */
data class ConflictSummary(val title: String, val local: ConflictSide, val remote: ConflictSide, val sentence: String)

/** Builds the plain-language view of a conflict from the two row versions. */
object ConflictDescriber {
    /** Describes [c]; the value shown is the field that differs, falling back to the title. */
    fun describe(c: SyncConflictEntity): ConflictSummary {
        val local = Json.parseToJsonElement(c.localJson).jsonObject
        val remote = Json.parseToJsonElement(c.remoteJson).jsonObject
        val title = RowJson.string(remote, "title") ?: RowJson.string(local, "title") ?: RowJson.string(remote, "label") ?: "This item"
        val (a, b) = values(c.tableName, local, remote)
        return ConflictSummary(
            title = title,
            local = ConflictSide(a, "This phone · ${time(c.localAt)}"),
            remote = ConflictSide(b, "${c.remoteDevice} · ${time(c.remoteAt)}"),
            sentence = "“$title” was changed on your phone and ${deviceWord(c.remoteDevice)}.",
        )
    }

    private fun values(table: String, local: JsonObject, remote: JsonObject): Pair<String, String> {
        fun differs(key: String) = !RowJson.sameData(JsonObject(mapOf(key to (local[key] ?: kotlinx.serialization.json.JsonNull))), remote)
        fun pair(f: (JsonObject) -> String) = f(local) to f(remote)
        return when (table) {
            "alarms" -> when {
                differs("minutes") -> pair { clockText(RowJson.long(it, "minutes").toInt()).let { c -> c.digits + c.suffix } }
                differs("label") -> pair { RowJson.string(it, "label").orEmpty() }
                else -> pair { if (RowJson.string(it, "enabled") == "true") "On" else "Off" }
            }
            "events" -> when {
                differs("start_at") -> pair { RowJson.long(it, "start_at").toLocalDateTime().clock().let { c -> c.digits + c.suffix } }
                differs("title") -> pair { RowJson.string(it, "title").orEmpty() }
                differs("place") -> pair { RowJson.string(it, "place") ?: "No place" }
                else -> pair { RowJson.string(it, "notes") ?: "No notes" }
            }
            "todos" -> when {
                differs("title") -> pair { RowJson.string(it, "title").orEmpty() }
                differs("due_at") -> pair { j -> RowJson.string(j, "due_at")?.toLongOrNull()?.toLocalDateTime()?.clock()?.let { it.digits + it.suffix } ?: "No time" }
                differs("done") -> pair { if (RowJson.string(it, "done") == "true") "Done" else "Not done" }
                else -> pair { "Edited" }
            }
            else -> pair { "Edited" }
        }
    }

    private fun time(millis: Long) = millis.toLocalDateTime().clock().let { it.digits + it.suffix }.trim()

    /** "Tablet" -> "tablet"; device models keep their name. */
    fun deviceWord(name: String) = if (name.contains("tablet", ignoreCase = true)) "tablet" else name
}
