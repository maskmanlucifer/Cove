package app.cove.companion.feature.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Confirmation chip shown over Today after a voice command; [commandId] is what Undo reverses (null = no Undo). */
data class UndoToast(val text: String, val commandId: String?, val serial: Long)

/** Holds the confirmation chip so it survives the Voice screen closing. */
class VoiceFeedback {
    private val _toast = MutableStateFlow<UndoToast?>(null)
    val toast: StateFlow<UndoToast?> = _toast.asStateFlow()
    private var serial = 0L

    fun show(text: String, commandId: String?) {
        _toast.value = UndoToast(text, commandId, ++serial)
    }

    /** Hides the chip, but only the one that was shown as [shown] so a newer chip is never cleared by an old timer. */
    fun dismiss(shown: UndoToast) {
        _toast.update { if (it == shown) null else it }
    }
}

/** To-dos created by voice that Today flags "New" until it has been shown once. */
class NewTracker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("voice_new", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun add(newIds: Collection<String>) = set(_ids.value + newIds)

    /** Clears [seen] ids: the "New" tag fades once the user has looked at them. */
    fun markSeen(seen: Collection<String>) = set(_ids.value - seen.toSet())

    private fun set(value: Set<String>) {
        _ids.value = value
        prefs.edit().putStringSet(KEY, value).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}
