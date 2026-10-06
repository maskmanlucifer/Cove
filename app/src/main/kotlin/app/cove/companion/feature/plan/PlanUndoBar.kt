package app.cove.companion.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable

/** Dark pill above the dock with a message and an Undo button. */
@Composable
fun PlanUndoBar(message: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    val shadow = Color(0x33141420)
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(32.dp, CoveShapes.Pill, ambientColor = shadow, spotColor = shadow)
            .background(c.ink, CoveShapes.Pill)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoveText(message, Modifier.weight(1f), style = CoveType.Button.copy(fontWeight = FontWeight.Normal), color = c.onInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.height(40.dp).background(c.onInk.copy(alpha = 0.14f), CoveShapes.Pill).pressable(onUndo).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CoveText("Undo", style = CoveType.Button.copy(fontSize = 15.sp), color = c.onInk)
        }
    }
}
