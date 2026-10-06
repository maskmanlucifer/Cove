package app.cove.companion.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cove.companion.core.Undo
import app.cove.companion.core.UndoItem
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType

/**
 * Dark pill with the current Undo offer of [area] ("Entry deleted" and an Undo button), announced politely to
 * screen readers. Place it near the bottom of a full-screen `Box`.
 */
@Composable
fun UndoHost(area: String, modifier: Modifier = Modifier) {
    val offer by Undo.center.current.collectAsState()
    val shown = offer?.takeIf { it.area == area }
    val held = remember { mutableStateOf<UndoItem?>(null) }
    if (shown != null) held.value = shown
    AnimatedVisibility(shown != null, modifier, enter = fadeIn(), exit = fadeOut()) {
        val item = held.value ?: return@AnimatedVisibility
        val c = Cove.colors
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(c.ink, CoveShapes.Pill)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoveText(item.message, Modifier.weight(1f).padding(vertical = 8.dp), style = CoveType.Button.copy(fontWeight = FontWeight.Normal), color = c.onInk)
            Box(
                Modifier
                    .height(48.dp)
                    .background(c.onInk.copy(alpha = 0.12f), CoveShapes.Pill)
                    .pressable({ Undo.center.undo(item) })
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { CoveText("Undo", style = CoveType.Button.copy(fontWeight = FontWeight.Medium), color = c.onInk) }
        }
    }
}
