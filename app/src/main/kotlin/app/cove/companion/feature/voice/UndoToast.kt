package app.cove.companion.feature.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.cove.companion.container
import app.cove.companion.design.components.TopUndoBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VISIBLE_MS = 9000L

/** Frame 20: the dark "Saved 3 to-dos / Undo" chip, now at the top via [TopUndoBar]; Undo really reverts the command. */
@Composable
fun UndoToastHost(modifier: Modifier = Modifier) {
    val kit = LocalContext.current.container.voice
    val toast by kit.feedback.toast.collectAsState()
    val scope = rememberCoroutineScope()
    val shown = toast
    LaunchedEffect(shown) {
        if (shown != null) {
            delay(VISIBLE_MS)
            kit.feedback.dismiss(shown)
        }
    }
    val last = rememberLast(shown)
    if (last != null) {
        TopUndoBar(
            last.text,
            last.commandId?.let { id ->
                {
                    scope.launch {
                        kit.executor.undo(id)
                        kit.feedback.dismiss(last)
                    }
                }
            },
            modifier, belowHeader = true, visible = shown != null,
        )
    }
}

/** Keeps showing the last toast while the exit animation plays after the state became null. */
@Composable
private fun rememberLast(current: UndoToast?): UndoToast? {
    val held = remember { mutableStateOf<UndoToast?>(null) }
    if (current != null) held.value = current
    return held.value
}
