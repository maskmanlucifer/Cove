package app.cove.companion.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.local.entity.JournalMediaEntity
import java.io.File

/**
 * Debug only (`--ei journalPhotos N`): an entry with [N] generated photos in turn wide, square, tall, very tall,
 * EXIF-rotated, 24 MP and corrupt, plus a voice-note row, to check the editor's layout and the viewer.
 */
object DebugPhotos {
    /** Entry id the editor route opens: `journal/debug-photos` (seed first, open afterwards). */
    const val ENTRY_ID = "debug-photos"

    private val shapes = listOf(
        Triple("wide", 2400, 1350), Triple("square", 1600, 1600), Triple("tall", 1500, 2000), Triple("verytall", 1000, 3000),
        Triple("rotated", 1600, 1200), Triple("huge", 6000, 4000), Triple("corrupt", 0, 0),
    )

    suspend fun seed(c: AppContainer, count: Int, cacheDir: File) {
        val base = c.journal.newEntry(c.clock.now().toLocalDate())
        val entry = base.copy(id = ENTRY_ID, title = "Photo test", body = "Photos of several shapes.")
        c.journal.save(entry)
        repeat(count) { i ->
            val (name, w, h) = shapes[i % shapes.size]
            val file = File(cacheDir, "debug-$i-$name.jpg")
            if (name == "corrupt") {
                addCorrupt(c, entry.id)
                return@repeat
            }
            draw(file, w, h, "$name ${i + 1}", exifRotate = name == "rotated")
            c.journalMedia.addPhoto(entry.id, Uri.fromFile(file))
            file.delete()
        }
        val voiceId = newId()
        val voice = c.journalFiles.voice(voiceId).also { it.writeBytes(ByteArray(64)) }
        c.journal.saveMedia(JournalMediaEntity(voiceId, entry.id, "voice", voice.path, durationMs = 42_000, bytes = 64, updatedAt = c.clock.now()))
    }

    private fun addCorrupt(c: AppContainer, entryId: String) {
        val id = newId()
        val file = File(c.journalFiles.photoBase(id).path + ".webp").also { it.writeBytes(ByteArray(2000) { b -> (b * 7).toByte() }) }
        kotlinx.coroutines.runBlocking {
            c.journal.saveMedia(JournalMediaEntity(id, entryId, "photo", file.path, bytes = file.length(), updatedAt = c.clock.now()))
        }
    }

    private fun draw(file: File, w: Int, h: Int, label: String, exifRotate: Boolean) {
        val config = if (w * h > 8_000_000) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(w, h, config)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val hue = (label.hashCode() and 0xFF) / 255f * 360f
        for (y in 0 until 8) {
            paint.color = Color.HSVToColor(floatArrayOf(hue, 0.35f, 0.55f + y * 0.05f))
            canvas.drawRect(0f, h * y / 8f, w.toFloat(), h * (y + 1) / 8f, paint)
        }
        paint.color = Color.WHITE
        paint.textSize = minOf(w, h) / 8f
        canvas.drawText(if (exifRotate) "UP $label" else label, w / 12f, h / 5f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = minOf(w, h) / 60f
        canvas.drawRect(paint.strokeWidth, paint.strokeWidth, w - paint.strokeWidth, h - paint.strokeWidth, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        if (exifRotate) {
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
        }
    }
}
