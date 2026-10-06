package app.cove.companion.design.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.cove.companion.core.Undo
import app.cove.companion.core.UndoItem

/**
 * Shows the current Undo offer of [area] ("Entry deleted" and an Undo button) through [TopUndoBar]. Place it with
 * `Modifier.align(Alignment.TopCenter)` in a full-screen `Box`; set [belowHeader] on screens with a title or back row.
 */
@Composable
fun UndoHost(area: String, modifier: Modifier = Modifier, belowHeader: Boolean = true) {
    val offer by Undo.center.current.collectAsState()
    val shown = offer?.takeIf { it.area == area }
    val held = remember { mutableStateOf<UndoItem?>(null) }
    if (shown != null) held.value = shown
    val item = held.value ?: return
    TopUndoBar(item.message, { Undo.center.undo(item) }, modifier, belowHeader = belowHeader, visible = shown != null)
}
