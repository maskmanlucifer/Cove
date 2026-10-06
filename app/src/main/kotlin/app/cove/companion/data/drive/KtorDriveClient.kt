package app.cove.companion.data.drive

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * [DriveClient] over Ktor. Retries 429/5xx and network errors with [Backoff], refreshes the token once on 401
 * and maps persistent auth failures to [DriveException.NeedsConsent].
 */
class KtorDriveClient(
    private val http: HttpClient,
    private val auth: DriveAuth,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val boundary: () -> String = { "cove-" + java.util.UUID.randomUUID() },
) : DriveClient {
    private val folderMutex = Mutex()
    private var cached: DriveFolders? = null

    override suspend fun folders(): DriveFolders = folderMutex.withLock {
        cached ?: run {
            val root = findOrCreateFolder("Cove", null)
            DriveFolders(
                root, findOrCreateFolder("Photos", root), findOrCreateFolder("Voice", root), findOrCreateFolder("Backups", root),
            ).also { cached = it }
        }
    }

    /** Returns the id of the folder [name] under [parentId], creating it when no such folder exists. */
    suspend fun findOrCreateFolder(name: String, parentId: String?): String {
        val found = send {
            method = HttpMethod.Get
            url(DriveRequests.FILES_URL)
            parameter("q", DriveRequests.folderQuery(name, parentId))
            parameter("fields", "files(id,name)")
            parameter("orderBy", "createdTime")
            parameter("pageSize", 1)
        }.json().getValue("files").jsonArray.firstOrNull()
        if (found != null) return found.jsonObject.getValue("id").jsonPrimitive.content
        val created = send {
            method = HttpMethod.Post
            url(DriveRequests.FILES_URL)
            parameter("fields", "id")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(DriveRequests.metadata(name, DriveRequests.FOLDER_MIME, parentId))
        }
        return created.json().getValue("id").jsonPrimitive.content
    }

    override suspend fun upload(folderId: String, name: String, mime: String, content: ByteArray): String {
        val metadata = DriveRequests.metadata(name, null, folderId)
        val response = if (DriveRequests.useResumable(content.size)) {
            val session = send {
                method = HttpMethod.Post
                url(DriveRequests.UPLOAD_URL)
                parameter("uploadType", "resumable")
                parameter("fields", "id")
                header("X-Upload-Content-Type", mime)
                header("X-Upload-Content-Length", content.size.toString())
                header(HttpHeaders.ContentType, "application/json; charset=UTF-8")
                setBody(metadata)
            }.headers[HttpHeaders.Location] ?: throw DriveException.Permanent(0, "No upload session")
            send {
                method = HttpMethod.Put
                url(session)
                header(HttpHeaders.ContentType, mime)
                setBody(content)
            }
        } else {
            val b = boundary()
            send {
                method = HttpMethod.Post
                url(DriveRequests.UPLOAD_URL)
                parameter("uploadType", "multipart")
                parameter("fields", "id")
                header(HttpHeaders.ContentType, DriveRequests.multipartContentType(b))
                setBody(DriveRequests.multipartBody(b, metadata, mime, content))
            }
        }
        return response.json().getValue("id").jsonPrimitive.content
    }

    override suspend fun download(fileId: String, dest: File) {
        val bytes = send {
            method = HttpMethod.Get
            url("${DriveRequests.FILES_URL}/$fileId")
            parameter("alt", "media")
        }.bodyAsBytes()
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            throw IOException("Could not save ${dest.name}")
        }
    }

    override suspend fun delete(fileId: String) {
        try {
            send {
                method = HttpMethod.Delete
                url("${DriveRequests.FILES_URL}/$fileId")
            }
        } catch (e: DriveException.Permanent) {
            if (e.status != 404) throw e
        }
    }

    override suspend fun list(folderId: String): List<DriveFile> =
        send {
            method = HttpMethod.Get
            url(DriveRequests.FILES_URL)
            parameter("q", DriveRequests.childrenQuery(folderId))
            parameter("fields", "files(id,name,createdTime)")
            parameter("orderBy", "createdTime desc")
            parameter("pageSize", 1000)
        }.json().getValue("files").jsonArray.map {
            val o = it.jsonObject
            DriveFile(
                o.getValue("id").jsonPrimitive.content,
                o.getValue("name").jsonPrimitive.content,
                o["createdTime"]?.jsonPrimitive?.contentOrNull?.let { t -> runCatching { java.time.Instant.parse(t).toEpochMilli() }.getOrNull() } ?: 0,
            )
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    /** Sends one logical request with auth, a single token refresh on 401 and backoff on transient failures. */
    private suspend fun send(configure: HttpRequestBuilder.() -> Unit): HttpResponse {
        var attempt = 0
        var refreshed = false
        while (true) {
            val token = (auth.token() as? DriveToken.Granted)?.accessToken ?: throw DriveException.NeedsConsent()
            val response = try {
                http.request {
                    configure()
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (++attempt >= Backoff.MAX_ATTEMPTS) throw DriveException.Transient(e.message ?: "Network error")
                sleep(Backoff.delayMs(attempt))
                continue
            }
            val status = response.status.value
            if (response.status.isSuccess()) return response
            val body = response.bodyAsText()
            when {
                status == 401 -> {
                    if (refreshed) throw DriveException.NeedsConsent()
                    refreshed = true
                    auth.invalidate()
                }
                status == 403 && !isRateLimit(body) -> {
                    if (body.contains("storageQuotaExceeded")) throw DriveException.Permanent(status, "Drive is full")
                    throw DriveException.NeedsConsent()
                }
                Backoff.retryable(status) || status == 403 -> {
                    if (++attempt >= Backoff.MAX_ATTEMPTS) throw DriveException.Transient("Drive $status")
                    sleep(Backoff.delayMs(attempt))
                }
                else -> throw DriveException.Permanent(status, "Drive $status")
            }
        }
    }

    private fun isRateLimit(body: String) = body.contains("rateLimitExceeded") || body.contains("userRateLimitExceeded")
}
