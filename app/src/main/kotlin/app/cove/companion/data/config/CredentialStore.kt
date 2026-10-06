package app.cove.companion.data.config

import app.cove.companion.security.Sealer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The user's [Credentials], kept as one [sealer]-encrypted file (use the Keystore `SecretBox` and a path under
 * `no_backup`, so it never leaves this phone). [credentials] is what the app uses: stored values win, and
 * [devDefaults] (Gradle properties in debug builds) only fill fields the user left blank.
 */
class CredentialStore(
    private val file: File,
    private val sealer: Sealer,
    private val devDefaults: Credentials = Credentials(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var stored: Credentials = read()
    private val _credentials = MutableStateFlow(stored.filledBy(devDefaults))

    /** Effective credentials; emits on every change. */
    val credentials: StateFlow<Credentials> = _credentials.asStateFlow()

    /** What the user has typed in, without dev defaults (for editing screens). */
    val saved: Credentials @Synchronized get() = stored

    /** Applies [patch] (normalised) on top of what is stored and persists the result. */
    @Synchronized
    fun update(patch: Map<CredentialField, String>) {
        if (patch.isEmpty()) return
        write(stored.patched(patch))
    }

    /** Forgets everything stored on this phone. */
    @Synchronized
    fun clear() = write(Credentials())

    private fun write(next: Credentials) {
        file.parentFile?.mkdirs()
        if (next == Credentials()) {
            file.delete()
        } else {
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(sealer.encrypt(json.encodeToString(next).toByteArray()))
            check(tmp.renameTo(file)) { "Could not save the credentials" }
        }
        stored = next
        _credentials.value = next.filledBy(devDefaults)
    }

    private fun read(): Credentials {
        if (!file.exists()) return Credentials()
        return runCatching { json.decodeFromString<Credentials>(String(sealer.decrypt(file.readBytes()))) }
            .getOrElse {
                file.delete()
                Credentials()
            }
    }
}
