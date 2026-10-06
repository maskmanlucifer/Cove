package app.cove.companion.data.drive

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Pure builders for Drive REST requests, kept apart from the HTTP client so they can be unit tested. */
object DriveRequests {
    const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
    const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    const val FOLDER_MIME = "application/vnd.google-apps.folder"

    /** Files above this size use the resumable protocol; smaller ones go in a single multipart request. */
    const val RESUMABLE_ABOVE_BYTES = 5 * 1024 * 1024

    /** Escapes [s] for use inside a single-quoted Drive query string. */
    fun escape(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'")

    /** Query for folders called [name], optionally inside [parentId]. */
    fun folderQuery(name: String, parentId: String?): String = buildString {
        append("name = '${escape(name)}' and mimeType = '$FOLDER_MIME' and trashed = false")
        if (parentId != null) append(" and '${escape(parentId)}' in parents")
    }

    /** Query for the non-trashed children of [folderId]. */
    fun childrenQuery(folderId: String): String = "'${escape(folderId)}' in parents and trashed = false"

    /** File metadata JSON for a create call; [mime] and [parentId] are omitted when null. */
    fun metadata(name: String, mime: String?, parentId: String?): String = buildJsonObject {
        put("name", name)
        if (mime != null) put("mimeType", mime)
        if (parentId != null) put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parentId)) })
    }.toString()

    /** `Content-Type` header value for a multipart/related upload using [boundary]. */
    fun multipartContentType(boundary: String) = "multipart/related; boundary=$boundary"

    /** Body of a multipart/related upload: a JSON metadata part followed by the media part. */
    fun multipartBody(boundary: String, metadataJson: String, mime: String, content: ByteArray): ByteArray {
        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadataJson\r\n" +
            "--$boundary\r\nContent-Type: $mime\r\n\r\n"
        return head.toByteArray() + content + "\r\n--$boundary--".toByteArray()
    }

    /** True when an upload of [size] bytes should use the resumable protocol. */
    fun useResumable(size: Int) = size > RESUMABLE_ABOVE_BYTES
}

/** Retry timing for 429 and 5xx answers. */
object Backoff {
    /** Calls tried in total before a transient failure is surfaced. */
    const val MAX_ATTEMPTS = 4

    /** Delay before retry number [attempt] (1 = first retry): 500 ms doubling, capped at 30 s. */
    fun delayMs(attempt: Int, baseMs: Long = 500, capMs: Long = 30_000): Long =
        minOf(capMs, baseMs shl (attempt - 1).coerceIn(0, 20))

    /** True for statuses worth retrying. */
    fun retryable(status: Int) = status == 429 || status in 500..599
}
