package app.cove.companion.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CheckCircle
import app.cove.companion.design.components.CoveText

/** Frame 30: to-dos in one card while offline; rows with changes that have not synced say "will sync". */
@Composable
fun OfflineTodos(rows: List<TodoRow>, pending: Set<String>, onToggle: (String, Boolean) -> Unit) {
    val c = Cove.colors
    Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(horizontal = 20.dp, vertical = 6.dp)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.well))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(row.done, role = Role.Checkbox, onValueChange = { onToggle(row.id, it) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CheckCircle(row.done, onToggle = null)
                CoveText(row.title, Modifier.weight(1f), color = if (row.done) c.tail else c.ink)
                if (row.id in pending) CoveText("will sync", style = CoveType.Meta, color = c.muted)
            }
        }
    }
}
