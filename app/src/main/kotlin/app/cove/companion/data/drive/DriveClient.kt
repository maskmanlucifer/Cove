package app.cove.companion.data.drive

import java.io.File

/** The visible "Cove" folder in the user's Drive and its subfolders. */
data class DriveFolders(val root: String, val photos: String, val voice: String, val backups: String)

/** A Drive file as the app needs to see it; [createdTime] is epoch millis. */
data class DriveFile(val id: String, val name: String, val createdTime: Long)

/** Why a Drive call failed, already sorted into what the caller should do next. */
sealed class DriveException(message: String) : Exception(message) {
    /** No usable token: the user has to grant Drive access (or sign in) before anything works. */
    class NeedsConsent : DriveException("Drive access needs consent")

    /** Network trouble, 429 or 5xx that outlasted the in-call retries: try again later. */
    class Transient(message: String) : DriveException(message)

    /** The request itself is wrong or the item is gone (4xx other than auth): retrying will not help. */
    class Permanent(val status: Int, message: String) : DriveException(message)
}

/** Google Drive REST v3 operations Cove needs, scoped to `drive.file`. */
interface DriveClient {
    /** Finds or creates `Cove` with its `Photos`, `Voice` and `Backups` subfolders. */
    suspend fun folders(): DriveFolders

    /** Uploads [content] as [name] into [folderId] and returns the new file id. */
    suspend fun upload(folderId: String, name: String, mime: String, content: ByteArray): String

    /** Downloads file [fileId] into [dest] (written atomically). */
    suspend fun download(fileId: String, dest: File)

    /** Deletes [fileId]; a file that is already gone counts as deleted. */
    suspend fun delete(fileId: String)

    /** Lists the files directly inside [folderId], newest first. */
    suspend fun list(folderId: String): List<DriveFile>
}
