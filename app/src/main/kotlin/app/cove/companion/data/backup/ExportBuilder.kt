package app.cove.companion.data.backup

import app.cove.companion.data.sync.RowJson
import app.cove.companion.feature.journal.blocks.JournalBodyCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** A parsed backup: its schema [version], when it was made and the rows of each synced table (server-shaped JSON). */
data class Backup(val version: Int, val createdAt: Long, val tables: Map<String, List<JsonObject>>)

/** Thrown when a file is not a Cove backup this version can read. */
class BackupFormatException(message: String) : Exception(message)

/**
 * Pure export of the synced tables to the `cove-YYYY-MM.json.gz` format and of journal entries to Markdown.
 * Schema: `{"version":1,"app":"cove","createdAt":<ms>,"tables":{"<table>":[<row>...]}}` with rows in the same
 * server-shaped JSON sync uses. Local-only tables (search index, outbox, sync state, conflicts) are never included.
 */
object ExportBuilder {
    /** Bump when the layout above changes incompatibly. */
    const val VERSION = 1

    /** Backup file name for [month]. */
    fun snapshotName(month: YearMonth) = "cove-$month.json.gz"

    /** Markdown file name for [month]. */
    fun journalName(month: YearMonth) = "journal-$month.md"

    /** Snapshot JSON for [tables], keeping table order and sorting each table's rows by [keyOf] for a stable result. */
    fun snapshotJson(createdAt: Long, tables: Map<String, List<JsonObject>>, keyOf: (String) -> String): String {
        val body = JsonObject(
            tables.mapValues { (name, rows) ->
                JsonArray(rows.sortedBy { RowJson.string(it, keyOf(name)).orEmpty() })
            },
        )
        return JsonObject(
            mapOf("version" to JsonPrimitive(VERSION), "app" to JsonPrimitive("cove"), "createdAt" to JsonPrimitive(createdAt), "tables" to body),
        ).toString()
    }

    fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()

    /** Reads a snapshot (gzipped or plain JSON) and checks its version. */
    fun parse(bytes: ByteArray): Backup {
        val text = try {
            if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { String(it.readBytes()) }
            } else String(bytes)
        } catch (e: java.io.IOException) {
            throw BackupFormatException("Not a Cove backup")
        }
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw BackupFormatException("Not a Cove backup")
        }
        val version = runCatching { root.getValue("version").jsonPrimitive.int }.getOrNull() ?: throw BackupFormatException("Missing version")
        if (version > VERSION) throw BackupFormatException("This backup is from a newer Cove (version $version)")
        val tables = root["tables"]?.jsonObject ?: throw BackupFormatException("Missing tables")
        return Backup(
            version,
            runCatching { root.getValue("createdAt").jsonPrimitive.long }.getOrDefault(0),
            tables.mapValues { (_, rows) -> rows.jsonArray.map { it.jsonObject } },
        )
    }

    /**
     * One Markdown document per month that has entries, keyed by [journalName]. Deleted entries are left out. Photos and
     * voice notes of [media] (the `journal_media` rows) appear where the body places them; photos link to the file
     * Drive keeps in the sibling `Photos` folder (`../Photos/<id>.<ext>`).
     */
    fun journalMarkdown(entries: List<JsonObject>, media: List<JsonObject> = emptyList()): Map<YearMonth, String> =
        entries.filter { RowJson.string(it, "deleted_at") == null }
            .groupBy { YearMonth.from(LocalDate.ofEpochDay(RowJson.long(it, "day"))) }
            .toSortedMap()
            .mapValues { (month, list) -> monthDocument(month, list, mediaByEntry(media)) }

    private fun mediaByEntry(rows: List<JsonObject>): Map<String, Map<String, JournalBodyCodec.MarkdownMedia>> =
        rows.filter { RowJson.string(it, "deleted_at") == null }.groupBy { RowJson.string(it, "entry_id").orEmpty() }.mapValues { (_, list) ->
            list.associate { r ->
                val id = RowJson.string(r, "id").orEmpty()
                val ext = RowJson.string(r, "local_path").orEmpty().substringAfterLast('.', "webp")
                id to JournalBodyCodec.MarkdownMedia(RowJson.string(r, "kind").orEmpty(), "../Photos/$id.$ext", RowJson.long(r, "duration_ms"))
            }
        }

    private fun monthDocument(month: YearMonth, entries: List<JsonObject>, media: Map<String, Map<String, JournalBodyCodec.MarkdownMedia>>): String = buildString {
        append("# Journal, ${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}\n")
        entries.sortedWith(compareBy({ RowJson.long(it, "day") }, { RowJson.long(it, "created_at") })).forEach { e ->
            val day = LocalDate.ofEpochDay(RowJson.long(e, "day")).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val title = RowJson.string(e, "title").orEmpty().trim()
            append("\n## ").append(day)
            if (title.isNotEmpty()) append(" · ").append(title)
            append('\n')
            RowJson.string(e, "mood")?.let { append("\nMood: ").append(it).append('\n') }
            val body = JournalBodyCodec.toMarkdown(RowJson.string(e, "body").orEmpty(), media[RowJson.string(e, "id")].orEmpty())
            if (body.isNotEmpty()) append('\n').append(body).append('\n')
        }
    }
}
