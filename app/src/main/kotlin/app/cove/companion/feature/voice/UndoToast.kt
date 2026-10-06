package app.cove.companion.feature.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.container
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VISIBLE_MS = 7000L

/** Frame 20: the dark "Saved 3 to-dos / Undo" chip floating above the dock; Undo really reverts the command. */
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
    Box(modifier.padding(horizontal = 16.dp)) {
        AnimatedVisibility(
            shown != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            val c = Cove.colors
            val last = rememberLast(shown)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(32.dp, CoveShapes.Pill, ambientColor = Color(0x33141420), spotColor = Color(0x33141420))
                    .background(c.ink, CoveShapes.Pill)
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoveText(last?.text.orEmpty(), Modifier.weight(1f), style = CoveType.Body.copy(fontSize = 15.sp, lineHeight = 20.25.sp), color = c.onInk)
                if (last?.commandId != null) {
                    Box(
                        Modifier
                            .height(40.dp)
                            .background(c.onInk.copy(alpha = 0.14f), CoveShapes.Pill)
                            .pressable({
                                scope.launch {
                                    kit.executor.undo(last.commandId)
                                    kit.feedback.dismiss(last)
                                }
                            })
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { CoveText("Undo", style = CoveType.Button, color = c.onInk) }
                }
            }
        }
    }
}

/** Keeps showing the last toast while the exit animation plays after the state became null. */
@Composable
private fun rememberLast(current: UndoToast?): UndoToast? {
    val held = remember { mutableStateOf<UndoToast?>(null) }
    if (current != null) held.value = current
    return held.value
}
