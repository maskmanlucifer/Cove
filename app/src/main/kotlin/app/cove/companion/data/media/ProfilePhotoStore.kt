package app.cove.companion.data.media

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The profile photo: one 512 px WebP at `filesDir/profile/avatar.webp`, private to the app and never synced or
 * uploaded (it is device-local, so no server change is needed). Re-encoding a bitmap drops all EXIF, GPS included.
 * The file's existence is the setting; [version] changes whenever it does so screens reload it.
 */
class ProfilePhotoStore(context: Context) {
    private val dir = File(context.filesDir, "profile")
    private val current get() = File(dir, "avatar.webp")
    private val removed get() = File(dir, "avatar.removed.webp")
    private val _version = MutableStateFlow(current.lastModified())

    /** Changes whenever the photo is set, removed or restored. */
    val version: StateFlow<Long> = _version.asStateFlow()

    /** The photo file when one is set, else null. */
    fun file(): File? = current.takeIf { it.isFile }

    /** Encodes [bitmap] (already [app.cove.companion.feature.me.AVATAR_EDGE] square) as the profile photo. */
    suspend fun save(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val tmp = File(dir, "avatar.tmp")
            tmp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it)) }
            check(tmp.renameTo(current))
            removed.delete()
            _version.value = System.nanoTime()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Takes the photo off the profile but keeps its file so [restore] can bring it back; see [discardRemoved]. */
    suspend fun remove() = withContext(Dispatchers.IO) {
        removed.delete()
        if (current.isFile) current.renameTo(removed)
        _version.value = System.nanoTime()
    }

    /** Undoes [remove]. */
    suspend fun restore() = withContext(Dispatchers.IO) {
        if (removed.isFile) removed.renameTo(current)
        _version.value = System.nanoTime()
    }

    /** Makes a [remove] final. */
    suspend fun discardRemoved() = withContext(Dispatchers.IO) { removed.delete(); Unit }
}
