package app.cove.companion.data.media

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.File

/** Writes the 320 px WebP thumbnails (quality 70) used by the journal lists and calendar. */
object ThumbnailMaker {
    /** Decodes [source] at thumbnail size and writes it to [dest]. Blocking: call off the main thread. */
    fun make(source: File, dest: File) {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (w, h) = fitLongEdge(info.size.width, info.size.height, THUMB_EDGE)
            decoder.setTargetSize(w, h)
        }
        dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, THUMB_QUALITY, it) }
        bitmap.recycle()
    }
}
