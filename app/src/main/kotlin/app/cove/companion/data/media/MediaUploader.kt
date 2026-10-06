package app.cove.companion.data.media

import app.cove.companion.data.drive.DriveClient
import app.cove.companion.data.drive.DriveException
import app.cove.companion.data.local.entity.JournalMediaEntity
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Journal media rows as the uploader sees them; backed by Room in the app and by a fake in tests. */
interface MediaRows {
    /** Live rows whose `uploadState` is not `done`. */
    suspend fun pending(): List<JournalMediaEntity>

    suspend fun save(media: JournalMediaEntity)
}

/** What one [MediaUploader.run] pass achieved; the worker turns it into success or retry. */
data class UploadRun(val done: Int = 0, val failed: Int = 0, val retryLater: Boolean = false, val needsConsent: Boolean = false)

/**
 * Uploads pending journal media to Drive and records the result on the row: `done` with `driveFileId`, `failed`
 * for items that cannot be uploaded, and untouched (still pending) when the problem is transient or needs consent.
 * Rows whose file is not on this device (pulled from another device) are skipped.
 *
 * @param thumbs where thumbnails go for other devices; null leaves them local. Thumbnail errors never fail an upload.
 */
class MediaUploader(
    private val rows: MediaRows,
    private val drive: DriveClient,
    private val thumbs: ThumbStore?,
) {
    /** Uploads every pending row once. */
    suspend fun run(): UploadRun {
        var done = 0
        var failed = 0
        for (row in rows.pending()) {
            val file = File(row.localPath)
            if (!file.isFile) continue
            try {
                val id = row.driveFileId ?: upload(row, file)
                uploadThumb(row)
                rows.save(row.copy(driveFileId = id, uploadState = STATE_DONE))
                done++
            } catch (e: CancellationException) {
                throw e
            } catch (e: DriveException.NeedsConsent) {
                return UploadRun(done, failed, needsConsent = true)
            } catch (e: DriveException.Transient) {
                return UploadRun(done, failed, retryLater = true)
            } catch (e: DriveException.Permanent) {
                rows.save(row.copy(uploadState = STATE_FAILED))
                failed++
            } catch (e: IOException) {
                rows.save(row.copy(uploadState = STATE_FAILED))
                failed++
            }
        }
        return UploadRun(done, failed, retryLater = failed > 0)
    }

    private suspend fun upload(row: JournalMediaEntity, file: File): String {
        val folders = drive.folders()
        val folder = if (row.kind == "voice") folders.voice else folders.photos
        return drive.upload(folder, "${row.id}.${file.extension.ifEmpty { "bin" }}", mimeFor(row.kind, file.extension), file.readBytes())
    }

    private suspend fun uploadThumb(row: JournalMediaEntity) {
        val thumb = row.thumbPath?.let(::File)?.takeIf { it.isFile } ?: return
        try {
            thumbs?.upload(row.id, thumb)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
    }

    companion object {
        const val STATE_DONE = "done"
        const val STATE_FAILED = "failed"

        /** MIME type for a stored file of [kind] with extension [ext]. */
        fun mimeFor(kind: String, ext: String): String = when {
            kind == "voice" -> "audio/ogg"
            ext.equals("webp", true) -> "image/webp"
            ext.equals("png", true) -> "image/png"
            ext.equals("heic", true) -> "image/heic"
            else -> "image/jpeg"
        }
    }
}
