package app.cove.companion.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable

/**
 * The payee of an imported expense (cleaned name, handle small) and what Cove remembers for it, with "Forget".
 * Shown on the edit screen only when the expense has a payee identity.
 */
@Composable
internal fun PayeeBlock(payee: PayeeInfo, onForget: () -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CoveText("Payee · ${payee.name}", style = MoneyType.Small.copy(fontWeight = FontWeight.Medium), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        payee.handle?.let { CoveText(it, style = MoneyType.Small, color = c.tail, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        payee.remembered?.let { text ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoveText("Remembered for this payee: $text", Modifier.weight(1f), style = MoneyType.Small, color = c.muted, maxLines = 2)
                CoveText("Forget", Modifier.heightIn(min = 44.dp).pressable(onForget).padding(horizontal = 4.dp), style = CoveType.Meta.copy(fontWeight = FontWeight.Medium), color = c.accent)
            }
        }
    }
}
