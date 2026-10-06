package app.cove.companion.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType

/** Stand-in for screens that are not built yet. */
@Composable
fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CoveText(name, style = CoveType.Title, color = Cove.colors.tail)
    }
}
