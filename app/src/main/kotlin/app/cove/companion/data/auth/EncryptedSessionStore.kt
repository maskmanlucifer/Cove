package app.cove.companion.data.auth

import android.content.Context
import app.cove.companion.security.SecretBox
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** [SessionStore] keeping the session as Keystore-encrypted JSON in private preferences. */
class EncryptedSessionStore(context: Context, private val box: SecretBox = SecretBox("cove_session")) : SessionStore {
    private val prefs by lazy { context.getSharedPreferences("cove_session", Context.MODE_PRIVATE) }

    override fun load(): Session? =
        prefs.getString(KEY, null)?.let(box::decryptString)?.let { runCatching { Json.decodeFromString<Session>(it) }.getOrNull() }

    override fun save(session: Session?) {
        prefs.edit().apply {
            if (session == null) remove(KEY) else putString(KEY, box.encryptString(Json.encodeToString(session)))
        }.apply()
    }

    private companion object {
        const val KEY = "session"
    }
}
