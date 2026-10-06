package app.cove.companion.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * In-flight guard for Save / Done / Create actions: only the first tap runs, later taps are ignored until the work
 * ends without completing (for example a validation failure). Used on the main thread.
 */
class OneShot {
    /** True while an action runs or has completed; screens dim and disable their button on it. */
    var busy by mutableStateOf(false)
        private set

    /**
     * Runs [block] unless another run is in flight. [block] returns true when it finished the job (the guard then
     * stays closed, as the screen is about to close) and false to allow another try.
     *
     * @return false when the tap was ignored.
     */
    fun launch(scope: CoroutineScope, block: suspend () -> Boolean): Boolean {
        if (busy) return false
        busy = true
        scope.launch {
            val finished = try {
                block()
            } catch (t: Throwable) {
                busy = false
                throw t
            }
            if (!finished) busy = false
        }
        return true
    }
}
