package app.cove.companion.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One pending "Undo" offer. [area] says which screens may show it ("money", "journal"); [onUndo] reverses the
 * change and [onExpire] makes it final (for example deleting files) once the window passes.
 */
class UndoItem(
    val area: String,
    val message: String,
    internal val onUndo: suspend () -> Unit,
    internal val onExpire: suspend () -> Unit,
)

/**
 * Holds the single Undo offer that outlives the screen which made it, so "Entry deleted · Undo" can appear on the
 * list the user returns to. A newer offer makes the older one final at once.
 */
class UndoCenter(private val scope: CoroutineScope) {
    private val _current = MutableStateFlow<UndoItem?>(null)
    val current: StateFlow<UndoItem?> = _current.asStateFlow()
    private var timer: Job? = null

    /** Shows [message] with an Undo action for [windowMs]; returns the offer. */
    fun post(
        area: String,
        message: String,
        windowMs: Long = WINDOW_MS,
        onExpire: suspend () -> Unit = {},
        onUndo: suspend () -> Unit,
    ): UndoItem {
        val item = UndoItem(area, message, onUndo, onExpire)
        _current.value?.let { old -> scope.launch { old.onExpire() } }
        timer?.cancel()
        _current.value = item
        timer = scope.launch {
            delay(windowMs)
            expire(item)
        }
        return item
    }

    /** Reverses the current offer. */
    fun undo(item: UndoItem) {
        if (_current.value !== item) return
        timer?.cancel()
        _current.value = null
        scope.launch { item.onUndo() }
    }

    /** Makes [item] final when it is still the current offer. */
    fun expire(item: UndoItem) {
        if (_current.value !== item) return
        timer?.cancel()
        _current.value = null
        scope.launch { item.onExpire() }
    }

    companion object {
        /** Long enough for a slow reader or a TalkBack user to find the button. */
        const val WINDOW_MS = 8000L
    }
}

/** App-wide Undo offers; screens show the ones for their area with `UndoHost`. */
object Undo {
    val center = UndoCenter(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
}
