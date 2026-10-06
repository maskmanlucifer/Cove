package app.cove.companion.data.media

import app.cove.companion.data.drive.DriveClient
import app.cove.companion.data.local.entity.JournalMediaEntity
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Returns a local file for a media row, downloading it from Drive (or its thumbnail from Supabase) into the
 * journal cache when this device does not have it, as on a fresh install. Null means "not available right now":
 * offline, Drive off, or never uploaded; callers show a placeholder.
 *
 * @param drive null while Drive is switched off.
 */
class MediaFetcher(
    private val drive: () -> DriveClient?,
    private val thumbs: ThumbStore?,
    private val cacheFile: (JournalMediaEntity) -> File,
    private val thumbFile: (String) -> File,
) {
    private val mutex = Mutex()

    /** The full photo or voice note: its own file if present, else the cached download. */
    suspend fun file(media: JournalMediaEntity): File? {
        File(media.localPath).takeIf { it.isFile }?.let { return it }
        val cache = cacheFile(media)
        if (cache.isFile) return cache
        val id = media.driveFileId ?: return null
        val client = drive() ?: return null
        return mutex.withLock {
            if (cache.isFile) return cache
            attempt { client.download(id, cache) }?.let { cache }
        }
    }

    /** The thumbnail for a photo row: local, cached, or fetched from the thumbnail bucket. */
    suspend fun thumb(media: JournalMediaEntity): File? {
        media.thumbPath?.let(::File)?.takeIf { it.isFile }?.let { return it }
        val cache = thumbFile(media.id)
        if (cache.isFile) return cache
        val store = thumbs ?: return null
        return attempt { if (store.download(media.id, cache)) Unit else null }?.let { cache }
    }

    private suspend fun <T : Any> attempt(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
