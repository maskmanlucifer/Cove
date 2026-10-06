package app.cove.companion.data.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Folders under `filesDir/journal/` for stored photos, thumbnails, voice notes and camera captures. */
class JournalFiles(private val context: Context) {
    private val root = File(context.filesDir, "journal")

    private fun dir(name: String) = File(root, name).also { it.mkdirs() }

    /** Where the compressed photo [id] goes (extension added by the compressor). */
    fun photoBase(id: String) = File(dir("photos"), id)

    fun thumb(id: String) = File(dir("thumbs"), "$id.webp")

    fun voice(id: String) = File(dir("voice"), "$id.ogg")

    /** Fresh file the camera app writes into, and the `content://` URI to hand it. */
    fun newCameraTarget(): Pair<File, Uri> {
        val file = File(dir("camera"), "${System.nanoTime()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
