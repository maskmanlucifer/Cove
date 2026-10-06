package app.cove.companion.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType

/**
 * Soft blue pill for secondary actions that must read as buttons (for example "Edit categories",
 * "Categories", "Import from messages"). Ink [PillButton]s stay reserved for the main action of a screen.
 */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    val c = Cove.colors
    Box(
        modifier
            .heightIn(min = 40.dp)
            .background(c.accentSoft, CoveShapes.Pill)
            .pressable(onClick, role = Role.Button)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            leading?.invoke()
            CoveText(text, style = CoveType.Button, color = c.accent, maxLines = 1)
        }
    }
}
