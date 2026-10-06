package app.cove.companion.data.media

import android.net.Uri
import app.cove.companion.core.Clock
import app.cove.companion.core.newId
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.repo.JournalRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores journal photos and voice notes under `filesDir/journal/` and records them with `uploadState = pending`
 * for the Drive upload worker. Photos are compressed and thumbnailed first.
 */
class JournalMedia(
    private val files: JournalFiles,
    private val compressor: ImageCompressor,
    private val repo: JournalRepository,
    private val clock: Clock,
) {
    /** Compresses [source] (photo picker or camera URI) and attaches it to [entryId]. [quality] is 80 (Balanced) or 90 (High). */
    suspend fun addPhoto(entryId: String, source: Uri, quality: Int = QUALITY_BALANCED): JournalMediaEntity {
        val id = newId()
        val media = withContext(Dispatchers.IO) {
            val stored = compressor.compress(source, files.photoBase(id), quality)
            val thumb = files.thumb(id)
            ThumbnailMaker.make(stored.file, thumb)
            JournalMediaEntity(id, entryId, "photo", stored.file.path, thumb.path, bytes = stored.bytes + thumb.length())
        }
        repo.saveMedia(media)
        return media
    }

    /** Attaches a finished [recording] to [entryId]. */
    suspend fun addVoice(entryId: String, id: String, recording: VoiceRecording): JournalMediaEntity {
        val media = JournalMediaEntity(id, entryId, "voice", recording.file.path, durationMs = recording.durationMs, bytes = recording.file.length())
        repo.saveMedia(media)
        return media
    }

    /** Soft-deletes [media] and removes its files. */
    suspend fun remove(media: JournalMediaEntity) {
        repo.saveMedia(media.copy(deletedAt = clock.now()))
        withContext(Dispatchers.IO) {
            File(media.localPath).delete()
            media.thumbPath?.let { File(it).delete() }
        }
    }
}
