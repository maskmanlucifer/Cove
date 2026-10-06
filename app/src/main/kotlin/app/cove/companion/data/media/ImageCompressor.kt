package app.cove.companion.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/** A stored photo: [file] on disk (its extension reflects the format written). */
data class CompressedImage(val file: File, val width: Int, val height: Int, val skipped: Boolean) {
    val bytes: Long get() = file.length()
}

/**
 * Turns a picked or captured photo into the compact copy Cove keeps (PLAN 6c): decoded at the target size so a 50 MP
 * original never loads in full, EXIF rotation applied, long edge <= 2048 px, lossy WebP (lossless for screenshots).
 * GPS is stripped; the capture date survives as the file's modified time.
 */
class ImageCompressor(private val context: Context) {
    /** Compresses [source] into `destBase.<ext>` and returns the written file. Blocking: call off the main thread. */
    fun compress(source: Uri, destBase: File, quality: Int = QUALITY_BALANCED): CompressedImage {
        val resolver = context.contentResolver
        val bytes = resolver.openAssetFileDescriptor(source, "r")?.use { it.length } ?: -1L
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val capturedAt = captureTime(source)

        val first = planImage(bounds.outWidth, bounds.outHeight, bytes, screenshotLike = false, quality = quality)
        if (first.skip && bounds.outWidth > 0) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(bounds.outMimeType) ?: "jpg"
            val file = File(destBase.path + "." + ext)
            resolver.openInputStream(source)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            stripGps(file)
            capturedAt?.let { file.setLastModified(it) }
            return CompressedImage(file, bounds.outWidth, bounds.outHeight, skipped = true)
        }

        val bitmap = decode(source, MAX_LONG_EDGE)
        val lossless = isScreenshotLike(sample(bitmap))
        val file = File(destBase.path + ".webp")
        file.outputStream().use { out ->
            val format = if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
            bitmap.compress(format, if (lossless) 100 else quality, out)
        }
        capturedAt?.let { file.setLastModified(it) }
        return CompressedImage(file, bitmap.width, bitmap.height, skipped = false).also { bitmap.recycle() }
    }

    private fun decode(source: Uri, maxEdge: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (w, h) = fitLongEdge(info.size.width, info.size.height, maxEdge)
            decoder.setTargetSize(w, h)
        }

    private fun sample(bitmap: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(bitmap, 64, 64, false)
        return IntArray(64 * 64).also { small.getPixels(it, 0, 64, 0, 0, 64, 64); if (small !== bitmap) small.recycle() }
    }

    private fun captureTime(source: Uri): Long? = runCatching {
        context.contentResolver.openInputStream(source)?.use { ExifInterface(it).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) }
    }.getOrNull()?.let { exifDate ->
        runCatching {
            LocalDateTime.parse(exifDate, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun stripGps(file: File) {
        runCatching {
            val exif = ExifInterface(file)
            GPS_TAGS.forEach { exif.setAttribute(it, null) }
            exif.saveAttributes()
        }
    }

    private companion object {
        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_PROCESSING_METHOD,
        )
    }
}
