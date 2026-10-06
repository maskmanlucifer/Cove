package app.cove.companion.security

import java.io.File
import java.security.SecureRandom

/**
 * Holds the SQLCipher passphrase: 256 random bits, generated once and stored in [file] sealed by [sealer]
 * (a non-exportable Keystore key that needs no user authentication, so alarms, widgets and workers can open the
 * database while the app is locked). The file is useless without the Keystore key on this device.
 */
class DatabasePassphrase(
    private val file: File,
    private val sealer: Sealer,
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * The passphrase as the ASCII hex of the random bytes, creating and storing it on first use.
     * Hex keeps it usable both as SQLCipher key bytes and as a quoted SQL string in `ATTACH ... KEY`.
     */
    @Synchronized
    fun get(): String {
        if (file.exists()) return String(sealer.decrypt(file.readBytes()), Charsets.US_ASCII)
        val hex = ByteArray(KEY_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(sealer.encrypt(hex.toByteArray(Charsets.US_ASCII)))
        check(tmp.renameTo(file)) { "Could not store the database key" }
        return hex
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}
