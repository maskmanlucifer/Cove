package app.cove.companion.feature.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.clockText
import app.cove.companion.core.rupees
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.ai.model.VoiceIntent
import java.util.Locale

private val Figure = CoveType.Figure.copy(fontSize = 64.sp, lineHeight = 66.sp)

/**
 * Frame 25: an expense draft. Private stand-in until the shared `ExpenseDraftCard` in `feature/money/` lands.
 */
@Composable
fun ExpenseDraft(s: VoiceState, e: VoiceIntent.LogExpense, nowMillis: Long, vm: VoiceViewModel) {
    val c = Cove.colors
    val whole = e.amountPaise / 100
    val cents = String.format(Locale.ENGLISH, ".%02d", e.amountPaise % 100)
    ResultFrame(
        s,
        headline = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CoveText(rupees(whole * 100), cents, style = Figure)
                if (e.note.isNotEmpty()) CoveText(e.note, style = CoveType.Body)
            }
        },
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                RowsCard(
                    buildList<@Composable () -> Unit> {
                        if (!e.received) add { DraftRow("Category") { CategoryChip(e.category ?: "Other", s.expenseCategories, height = 36) { vm.setExpenseCategory(it) } } }
                        add { DraftRow("When") { CoveText(whenText(e, nowMillis), style = DraftValue) } }
                        add { DraftRow("Paid with") { CoveText(e.paidWith ?: "UPI", style = DraftValue) } }
                    },
                )
                s.moneyHint?.let { CoveText(it, style = CoveType.Meta.copy(lineHeight = 21.sp), color = c.muted) }
            }
        },
        actions = { ActionPair("Save", vm::save, "Change", 120, vm::typeInstead) },
    )
}

private val DraftValue = CoveType.Body.copy(fontSize = 16.sp)

@Composable
private fun DraftRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        CoveText(label, style = DraftValue, color = Cove.colors.muted)
        value()
    }
}

private fun whenText(e: VoiceIntent.LogExpense, nowMillis: Long): String {
    val t = (e.at ?: nowMillis).toLocalDateTime()
    return "Today, " + clockText(t.hour * 60 + t.minute).let { it.digits + it.suffix }
}
