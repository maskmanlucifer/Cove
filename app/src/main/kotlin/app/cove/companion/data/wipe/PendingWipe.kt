package app.cove.companion.data.wipe

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Content of the `pending_wipe` marker. [notice] is shown once after the wipe; it never holds user data. */
@Serializable
data class PendingWipe(val createdAt: Long, val notice: String = DEFAULT_NOTICE) {
    companion object {
        /** What the owner sees on the first launch after a wipe. */
        const val DEFAULT_NOTICE = "Your data on this phone was cleared."

        private val json = Json { ignoreUnknownKeys = true }

        /** Writes the marker atomically, so a kill cannot leave half a file that is mistaken for no marker. */
        fun write(file: File, marker: PendingWipe) {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(marker))
            check(tmp.renameTo(file)) { "Could not record the clear request" }
        }

        /** The marker in [file], or null when it is unreadable (the wipe still runs, with the default notice). */
        fun read(file: File): PendingWipe? = runCatching { json.decodeFromString<PendingWipe>(file.readText()) }.getOrNull()
    }
}
