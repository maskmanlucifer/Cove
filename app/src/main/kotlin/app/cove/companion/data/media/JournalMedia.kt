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
    private val quality: suspend () -> Int = { QUALITY_BALANCED },
    private val onSaved: () -> Unit = {},
) {
    /** Compresses [source] (photo picker or camera URI) at the user's photo quality (80 Balanced, 90 High) and attaches it to [entryId]. */
    suspend fun addPhoto(entryId: String, source: Uri): JournalMediaEntity {
        val id = newId()
        val webpQuality = quality()
        val media = withContext(Dispatchers.IO) {
            val stored = compressor.compress(source, files.photoBase(id), webpQuality)
            val thumb = files.thumb(id)
            ThumbnailMaker.make(stored.file, thumb)
            JournalMediaEntity(id, entryId, "photo", stored.file.path, thumb.path, bytes = stored.bytes + thumb.length())
        }
        repo.saveMedia(media)
        onSaved()
        return media
    }

    /** Attaches a finished [recording] to [entryId]. */
    suspend fun addVoice(entryId: String, id: String, recording: VoiceRecording): JournalMediaEntity {
        val media = JournalMediaEntity(id, entryId, "voice", recording.file.path, durationMs = recording.durationMs, bytes = recording.file.length())
        repo.saveMedia(media)
        onSaved()
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
