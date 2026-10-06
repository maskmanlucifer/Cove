package app.cove.companion.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.util.LruCache
import app.cove.companion.data.local.entity.JournalMediaEntity
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** What loading a journal photo produced. */
sealed interface PhotoResult {
    /** Decoded at the requested size. */
    class Ready(val bitmap: Bitmap) : PhotoResult

    /** No copy on this phone and none could be fetched (offline, Drive off, never uploaded). */
    data object Missing : PhotoResult

    /** The file exists but cannot be decoded, or decoding ran out of memory. */
    data object Broken : PhotoResult
}

/**
 * Decodes journal photos for display: off the main thread, at the size they are drawn (so a 2048 px WebP never
 * loads in full for a 360 dp column), with EXIF orientation applied, behind a small in-memory LRU cache sized by
 * [memoryClassMb] (see [photoCacheBytes]). At most two decodes run at once so scrolling stays smooth.
 * Bitmaps are never recycled by hand: Compose may still be drawing an evicted one, so the GC frees them.
 *
 * @param fetcher the current [MediaFetcher]; looked up on every call because Drive can be switched on and off.
 */
class PhotoLoader(memoryClassMb: Int, private val fetcher: () -> MediaFetcher) {
    private val cache = object : LruCache<String, Bitmap>(photoCacheBytes(memoryClassMb)) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val sizes = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val gate = Semaphore(2)

    /** The cached bitmap for [media] at [widthPx] wide, or null; cheap enough to call while composing. */
    fun peek(media: JournalMediaEntity, widthPx: Int): Bitmap? = cache.get(key(media.id, widthPx))

    /** The cached thumbnail of [media], or null. */
    fun peekThumb(media: JournalMediaEntity): Bitmap? = cache.get(key(media.id, THUMB_EDGE))

    /** The size [size] already found for the photo [id], or null. */
    fun knownSize(id: String): Pair<Int, Int>? = sizes[id]

    /** Oriented pixel size of [media], read from the file header and remembered; null when no file is on this phone yet. */
    suspend fun size(media: JournalMediaEntity): Pair<Int, Int>? {
        sizes[media.id]?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(media.localPath).takeIf { it.isFile } ?: media.thumbPath?.let(::File)?.takeIf { it.isFile }
            file?.let { headerSize(it) }?.also { sizes[media.id] = it }
        }
    }

    /** The photo at [widthPx] wide (full width of its box), fetching it first when needed. */
    suspend fun load(media: JournalMediaEntity, widthPx: Int): PhotoResult {
        peek(media, widthPx)?.let { return PhotoResult.Ready(it) }
        val file = safely { fetcher().file(media) } ?: return PhotoResult.Missing
        val bitmap = decode(file, key(media.id, widthPx)) { w, h -> decodeSizeForWidth(w, h, widthPx) } ?: return PhotoResult.Broken
        return PhotoResult.Ready(bitmap)
    }

    /** The thumbnail of [media] for the blur-up placeholder; null when it is not on this phone. */
    suspend fun thumb(media: JournalMediaEntity): Bitmap? {
        peekThumb(media)?.let { return it }
        val file = safely { fetcher().thumb(media) } ?: return null
        return decode(file, key(media.id, THUMB_EDGE)) { w, h -> fitLongEdge(w, h, THUMB_EDGE) }
    }

    private suspend fun decode(file: File, key: String, target: (Int, Int) -> Pair<Int, Int>): Bitmap? = gate.withPermit {
        withContext(Dispatchers.IO) {
            try {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val (w, h) = target(info.size.width, info.size.height)
                    decoder.setTargetSize(w, h)
                }
                cache.put(key, bitmap)
                bitmap
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                cache.evictAll()
                null
            }
        }
    }

    private suspend fun <T> safely(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun key(id: String, width: Int) = "$id@$width"

    private fun headerSize(file: File): Pair<Int, Int>? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val orientation = ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            orientedSize(bounds.outWidth, bounds.outHeight, orientation)
        }
    } catch (e: Exception) {
        null
    }
}
