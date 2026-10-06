package app.cove.companion.data.drive

import java.io.File

/**
 * In-memory-free [DriveClient] that keeps "Drive" as plain files under [root] (`root/<Folder>/<name>`), with the
 * relative path as the file id. Used by the debug fake-Drive flag and by tests; set [failWith] to simulate errors.
 */
class FakeDriveClient(private val root: File, private val now: () -> Long = System::currentTimeMillis) : DriveClient {
    /** When set, every upload throws this. */
    var failWith: DriveException? = null

    private val names = listOf("Cove" to "Cove", "Photos" to "Cove/Photos", "Voice" to "Cove/Voice", "Backups" to "Cove/Backups")

    override suspend fun folders(): DriveFolders {
        names.forEach { File(root, it.second).mkdirs() }
        return DriveFolders("Cove", "Cove/Photos", "Cove/Voice", "Cove/Backups")
    }

    override suspend fun upload(folderId: String, name: String, mime: String, content: ByteArray): String {
        failWith?.let { throw it }
        val file = File(root, "$folderId/$name")
        file.parentFile?.mkdirs()
        file.writeBytes(content)
        file.setLastModified(now())
        return "$folderId/$name"
    }

    override suspend fun download(fileId: String, dest: File) {
        val src = File(root, fileId)
        if (!src.exists()) throw DriveException.Permanent(404, "Not found")
        dest.parentFile?.mkdirs()
        src.copyTo(dest, overwrite = true)
    }

    override suspend fun delete(fileId: String) {
        File(root, fileId).delete()
    }

    override suspend fun list(folderId: String): List<DriveFile> =
        (File(root, folderId).listFiles() ?: return emptyList())
            .filter { it.isFile }
            .map { DriveFile("$folderId/${it.name}", it.name, it.lastModified()) }
            .sortedByDescending { it.createdTime }
}
