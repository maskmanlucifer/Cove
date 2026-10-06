package app.cove.companion.data.wipe

import app.cove.companion.data.drive.DriveClient
import app.cove.companion.data.drive.DriveException
import app.cove.companion.data.sync.SyncTable
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Outcome of one cloud step. Never reported as done unless it really finished. */
sealed interface StepResult {
    /** Finished; [count] items were deleted (0 means there was nothing to delete). */
    data class Done(val count: Int) : StepResult

    /** Not attempted, for a plain [reason] (for example Drive is not connected). */
    data class Skipped(val reason: String) : StepResult

    /** Stopped after deleting [count] items; [reason] says why in plain words. */
    data class Failed(val count: Int, val reason: String) : StepResult
}

/** What the cloud deletion did, in the three places Cove keeps copies. */
data class CloudReport(val supabase: StepResult, val drive: StepResult, val thumbs: StepResult) {
    /** True when no step failed (skipped steps are fine: there was nothing connected to delete from). */
    val ok: Boolean get() = listOf(supabase, drive, thumbs).none { it is StepResult.Failed }

    /** Plain lines such as "Supabase: 1,240 rows deleted." or "Drive: not deleted. Google needs your permission again." */
    fun lines(): List<String> = listOf(
        line("Supabase", supabase, "rows"), line("Google Drive", drive, "files"), line("Thumbnails", thumbs, "thumbnails"),
    )

    /** The three lines as one short sentence group, e.g. "Supabase: 1,240 rows. Drive: 38 files. Thumbnails: 38." */
    fun summary(): String = listOf(short("Supabase", supabase, "rows"), short("Drive", drive, "files"), short("Thumbnails", thumbs, null)).joinToString(" ")

    private fun line(name: String, r: StepResult, unit: String) = when (r) {
        is StepResult.Done -> if (r.count == 0) "$name: nothing to delete." else "$name: ${"%,d".format(r.count)} $unit deleted."
        is StepResult.Skipped -> "$name: skipped. ${r.reason}"
        is StepResult.Failed -> "$name: not finished (${"%,d".format(r.count)} $unit deleted). ${r.reason}"
    }

    private fun short(name: String, r: StepResult, unit: String?) = when (r) {
        is StepResult.Done -> "$name: ${"%,d".format(r.count)}${unit?.let { " $it" } ?: ""}."
        is StepResult.Skipped -> "$name: skipped."
        is StepResult.Failed -> "$name: not finished."
    }
}

/**
 * Deletes the signed-in user's cloud copies: every synced Supabase table (PostgREST `DELETE` under row-level
 * security, paged and retried), the thumbnails in the `thumbs` bucket and the files the app created in Drive.
 * Counts are exact; a step that fails says so and is never reported as done.
 */
class CloudEraser(
    private val baseUrl: String,
    private val anonKey: String,
    private val client: HttpClient,
    private val tables: List<SyncTable>,
    private val token: suspend () -> String?,
    private val invalidateToken: suspend () -> Unit,
    private val userId: String?,
    private val drive: DriveClient?,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val pageSize: Int = PAGE,
) {
    private class Fatal(val reason: String) : Exception(reason)

    /** Runs all three steps, telling [progress] in plain language what it is doing. A cancelled coroutine stops cleanly. */
    suspend fun eraseAll(progress: (String) -> Unit = {}): CloudReport {
        if (userId == null || baseUrl.isBlank()) {
            val none = StepResult.Skipped("You are not signed in.")
            return CloudReport(none, StepResult.Skipped("You are not signed in."), none)
        }
        val supabase = eraseTables(progress)
        val thumbs = eraseThumbs(progress)
        val driveResult = eraseDrive(progress)
        return CloudReport(supabase, driveResult, thumbs)
    }

    private suspend fun eraseTables(progress: (String) -> Unit): StepResult {
        val counter = IntArray(1)
        val failed = mutableListOf<String>()
        var reason: String? = null
        for (table in tables) {
            progress("Deleting ${table.name.replace('_', ' ')} from Supabase")
            try {
                deleteTable(table, counter)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Fatal) {
                failed += table.name.replace('_', ' ')
                reason = e.reason
            }
        }
        return if (failed.isEmpty()) StepResult.Done(counter[0])
        else StepResult.Failed(counter[0], "${reason ?: "Supabase did not answer."} Not cleared: ${failed.joinToString()}.")
    }

    /** Deletes every row of [table] in pages, adding each page's count to `counter[0]` as it goes. */
    private suspend fun deleteTable(table: SyncTable, counter: IntArray) {
        var limited = true
        repeat(MAX_PAGES) {
            val n = try {
                retrying { deletePage(table, limited) }
            } catch (e: BadRequest) {
                if (!limited) throw Fatal("Supabase refused the request (${e.status}).")
                limited = false
                return@repeat
            } catch (e: Missing) {
                return
            }
            counter[0] += n
            if (n == 0 || (limited && n < pageSize)) return
        }
        throw Fatal("There are more rows than Cove can clear in one go. Run it again.")
    }

    private class BadRequest(val status: Int) : Exception()
    private class Missing : Exception()

    private suspend fun deletePage(table: SyncTable, limited: Boolean): Int {
        val response = authorised { t ->
            client.delete("${baseUrl.trimEnd('/')}/rest/v1/${table.name}") {
                parameter(table.key, "not.is.null")
                parameter("select", table.key)
                if (limited) {
                    parameter("order", table.key)
                    parameter("limit", pageSize)
                }
                header("apikey", anonKey)
                header("Authorization", "Bearer $t")
                header("Prefer", "return=representation")
            }
        }
        return when {
            response.status.value == 404 -> throw Missing()
            response.status.value == 400 -> throw BadRequest(400)
            !response.status.isSuccess() -> throw IOException("HTTP ${response.status.value}")
            else -> Json.parseToJsonElement(response.bodyAsText()).jsonArray.size
        }
    }

    private suspend fun eraseThumbs(progress: (String) -> Unit): StepResult {
        progress("Deleting thumbnails")
        var total = 0
        return try {
            repeat(MAX_PAGES) {
                val names = retrying { listThumbs() }
                if (names.isEmpty()) return StepResult.Done(total)
                val gone = retrying { deleteThumbs(names) }
                if (gone == 0) return StepResult.Failed(total, "Supabase would not delete some thumbnails.")
                total += gone
            }
            StepResult.Failed(total, "There are more thumbnails than Cove can clear in one go. Run it again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StepResult.Failed(total, "Supabase did not answer.")
        }
    }

    private suspend fun listThumbs(): List<String> {
        val response = authorised { t ->
            client.post("${baseUrl.trimEnd('/')}/storage/v1/object/list/thumbs") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $t")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("prefix", userId); put("limit", pageSize); put("offset", 0) }.toString())
            }
        }
        if (response.status.value == 404) return emptyList()
        if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}")
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray
            .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }.filter { it.isNotBlank() }.map { "$userId/$it" }
    }

    private suspend fun deleteThumbs(paths: List<String>): Int {
        val response = authorised { t ->
            client.delete("${baseUrl.trimEnd('/')}/storage/v1/object/thumbs") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $t")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("prefixes", JsonArray(paths.map { kotlinx.serialization.json.JsonPrimitive(it) })) }.toString())
            }
        }
        if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}")
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray.size
    }

    private suspend fun eraseDrive(progress: (String) -> Unit): StepResult {
        val client = drive ?: return StepResult.Skipped("Google Drive is not connected.")
        progress("Deleting Cove files from Google Drive")
        var total = 0
        return try {
            val folders = retryingDrive { client.folders() }
            for (folder in listOf(folders.photos, folders.voice, folders.backups, folders.root)) {
                for (file in retryingDrive { client.list(folder) }) {
                    retryingDrive { client.delete(file.id) }
                    total++
                }
            }
            listOf(folders.photos, folders.voice, folders.backups, folders.root).forEach { retryingDrive { client.delete(it) } }
            StepResult.Done(total)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DriveException.NeedsConsent) {
            StepResult.Failed(total, "Google needs your permission again. Open Connect services, tap Drive, then try again.")
        } catch (e: DriveException) {
            StepResult.Failed(total, "Google Drive did not answer.")
        } catch (e: Exception) {
            StepResult.Failed(total, "Google Drive did not answer.")
        }
    }

    private suspend fun <T> retryingDrive(block: suspend () -> T): T {
        var wait = BACKOFF_MS
        repeat(ATTEMPTS - 1) {
            try {
                return block()
            } catch (e: DriveException.Transient) {
                pause(wait)
                wait *= 2
            }
        }
        return block()
    }

    /** Sends with a fresh token; a 401 refreshes once. */
    private suspend fun authorised(send: suspend (String) -> HttpResponse): HttpResponse {
        repeat(2) { attempt ->
            val t = token() ?: throw Fatal("Your Supabase sign-in has expired. Sign in again, then try once more.")
            val response = send(t)
            if (response.status.value != 401) return response
            if (attempt == 0) invalidateToken()
        }
        throw Fatal("Your Supabase sign-in has expired. Sign in again, then try once more.")
    }

    /** Retries network trouble, 429 and 5xx a few times with a growing pause; anything else passes through. */
    private suspend fun <T> retrying(block: suspend () -> T): T {
        var wait = BACKOFF_MS
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                if (!transient(e)) throw Fatal("Supabase refused the request (${e.message}).")
                if (attempt >= ATTEMPTS) throw Fatal("Supabase did not answer. Check your connection and try again.")
                pause(wait)
                wait *= 2
                attempt++
            }
        }
    }

    private fun transient(e: IOException): Boolean {
        val code = e.message?.removePrefix("HTTP ")?.toIntOrNull() ?: return true
        return code == 429 || code >= 500
    }

    private companion object {
        const val PAGE = 500
        const val MAX_PAGES = 400
        const val ATTEMPTS = 3
        const val BACKOFF_MS = 1_000L
    }
}
