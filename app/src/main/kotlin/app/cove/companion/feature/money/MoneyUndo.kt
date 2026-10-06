package app.cove.companion.feature.money

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cove.companion.container
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The expense just deleted, so whichever Money screen is on top can offer Undo. */
object MoneyUndo {
    /** Id of the last deleted expense, or null when there is nothing to undo. */
    val deleted = MutableStateFlow<String?>(null)
}

/** Dark "Deleted · Undo" bar shown for a few seconds after an expense is deleted. */
@Composable
fun MoneyUndoBar(modifier: Modifier = Modifier) {
    val id by MoneyUndo.deleted.collectAsState()
    val money = LocalContext.current.container.money
    val scope = rememberCoroutineScope()
    val c = Cove.colors
    LaunchedEffect(id) {
        if (id != null) {
            delay(5000)
            MoneyUndo.deleted.value = null
        }
    }
    val shown = id ?: return
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(c.ink, CoveShapes.Pill)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoveText("Deleted", Modifier.weight(1f), style = MoneyType.Sub, color = c.onInk)
        Box(
            Modifier
                .height(40.dp)
                .background(c.onInk.copy(alpha = 0.12f), CoveShapes.Pill)
                .pressable({
                    MoneyUndo.deleted.value = null
                    scope.launch { money.restore(shown) }
                })
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { CoveText("Undo", style = CoveType.Button.copy(fontWeight = FontWeight.Medium), color = c.onInk) }
    }
}
