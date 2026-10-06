package app.cove.companion.data.media

import app.cove.companion.data.auth.AuthRepository
import app.cove.companion.data.auth.AuthState
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import java.io.File

/** Where thumbnails live so other devices can show them without touching Drive. */
interface ThumbStore {
    /** Uploads [file] as the thumbnail of media row [mediaId]; throws on failure. */
    suspend fun upload(mediaId: String, file: File)

    /** Downloads the thumbnail of [mediaId] into [dest]; false when there is none or it cannot be reached. */
    suspend fun download(mediaId: String, dest: File): Boolean
}

/**
 * [ThumbStore] over the private Supabase Storage bucket `thumbs`; objects are named `<user id>/<media id>.webp`,
 * which is what the bucket's per-user policies require.
 */
class SupabaseThumbStore(
    private val baseUrl: String,
    private val anonKey: String,
    private val auth: AuthRepository,
    private val client: HttpClient,
) : ThumbStore {
    private fun path(mediaId: String): String? =
        (auth.state.value as? AuthState.SignedIn)?.let { "thumbs/${it.userId}/$mediaId.webp" }

    override suspend fun upload(mediaId: String, file: File) {
        val path = path(mediaId) ?: error("Signed out")
        val token = auth.accessToken() ?: error("Signed out")
        val response = client.post("${baseUrl.trimEnd('/')}/storage/v1/object/$path") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $token")
            header("x-upsert", "true")
            header("Content-Type", "image/webp")
            setBody(file.readBytes())
        }
        if (!response.status.isSuccess()) throw java.io.IOException("thumb upload ${response.status.value}")
    }

    override suspend fun download(mediaId: String, dest: File): Boolean {
        val path = path(mediaId) ?: return false
        val token = auth.accessToken() ?: return false
        val response = client.get("${baseUrl.trimEnd('/')}/storage/v1/object/authenticated/$path") {
            header("apikey", anonKey)
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) return false
        dest.parentFile?.mkdirs()
        dest.writeBytes(response.bodyAsBytes())
        return true
    }
}
