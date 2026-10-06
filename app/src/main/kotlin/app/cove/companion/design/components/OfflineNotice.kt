package app.cove.companion.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cove.companion.container
import app.cove.companion.core.net.ConnectivityMonitor
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType

/** The grey "Offline · all still works" pill (frame 30). Reusable by any screen that wants to say so. */
@Composable
fun OfflineNotice(modifier: Modifier = Modifier, text: String = "Offline · all still works") {
    val c = Cove.colors
    Row(
        modifier.height(36.dp).background(c.wellStrong, CoveShapes.Pill).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(c.tail, CoveShapes.Circle))
        CoveText(text, style = CoveType.Meta)
    }
}

/** True while the device is offline (or debug forced it); recomposes on change. */
@Composable
fun rememberIsOffline(): Boolean {
    val online by LocalContext.current.container.connectivity.online.collectAsState()
    return !online || ConnectivityMonitor.forceOffline
}
