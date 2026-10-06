package app.cove.companion.data.sync

import app.cove.companion.data.auth.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** [SyncRemote] speaking PostgREST to Supabase with the signed-in user's token. */
class SupabaseRemote(
    private val baseUrl: String,
    private val anonKey: String,
    private val auth: AuthRepository,
    private val client: HttpClient,
) : SyncRemote {
    override suspend fun upsert(table: SyncTable, rows: List<JsonObject>) {
        if (rows.isEmpty()) return
        call { token ->
            client.post("${baseUrl.trimEnd('/')}/rest/v1/${table.name}") {
                parameter("on_conflict", "user_id,${table.key}")
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                contentType(ContentType.Application.Json)
                setBody(JsonArray(rows).toString())
            }
        }
    }

    override suspend fun fetch(table: SyncTable, since: Long, limit: Int): List<JsonObject> {
        val text = call { token ->
            client.get("${baseUrl.trimEnd('/')}/rest/v1/${table.name}") {
                parameter("select", "*")
                parameter("updated_at", "gte.$since")
                parameter("order", "updated_at.asc,${table.key}.asc")
                parameter("limit", limit)
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
        }
        return Json.parseToJsonElement(text).jsonArray.map { it.jsonObject }
    }

    /** Sends with a fresh token; on 401 refreshes once and retries before giving up with [SyncAuthException]. */
    private suspend fun call(send: suspend (String) -> HttpResponse): String {
        repeat(2) { attempt ->
            val token = auth.accessToken() ?: throw SyncAuthException()
            val response = send(token)
            if (response.status.value == 401) {
                if (attempt == 0) auth.invalidateAccessToken()
            } else if (!response.status.isSuccess()) {
                throw java.io.IOException("HTTP ${response.status.value}")
            } else {
                return response.bodyAsText()
            }
        }
        throw SyncAuthException()
    }
}
