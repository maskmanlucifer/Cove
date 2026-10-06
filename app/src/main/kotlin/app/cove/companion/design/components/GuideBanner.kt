package app.cove.companion.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType

/**
 * Calm inline notice with one next step: a short [title], one line of [body] and a single [actionLabel] button.
 * Used wherever something is off and the user can fix it (permissions, sync, backups).
 */
@Composable
fun GuideBanner(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Cove.colors
    Column(
        modifier.fillMaxWidth().background(c.card, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(top = 6.dp).size(8.dp).background(c.alert, CoveShapes.Circle))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CoveText(title, style = CoveType.BodyMedium)
                CoveText(body, style = CoveType.Meta, color = c.muted)
            }
        }
        PillButton(actionLabel, onAction, kind = ButtonKind.Secondary, height = 44.dp)
    }
}
