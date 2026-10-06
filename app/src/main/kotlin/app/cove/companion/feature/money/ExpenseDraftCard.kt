package app.cove.companion.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cove.companion.core.rupees
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.pressable

/**
 * What the app understood from "Spent 340 on lunch at Café Ivy", ready to confirm.
 *
 * @property quote what the user said, shown above the amount; null when typed.
 * @property soFar budget line such as "Food so far: ₹7,340 of ₹9,000.", see [MoneyMath.soFarLine].
 */
data class ExpenseDraft(
    val amountPaise: Long,
    val title: String,
    val category: String,
    val whenText: String,
    val paidWith: String,
    val quote: String? = null,
    val soFar: String? = null,
)

/**
 * Result card shown after logging an expense by voice: amount, category chip, time, payment method,
 * where the category stands, and Save / Change. Reusable by the Voice screen.
 *
 * @param modifier supplies size and padding; with [fillHeight] it must give the column a bounded height.
 * @param fillHeight pushes the buttons to the bottom edge; pass false when the parent has unbounded height.
 * @param onCategory opens the category picker from the chip.
 */
@Composable
fun ExpenseDraftCard(
    draft: ExpenseDraft,
    onSave: () -> Unit,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = true,
    onCategory: () -> Unit = onChange,
) {
    val c = Cove.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        draft.quote?.let { CoveText("“$it”", style = MoneyType.Quote, color = c.muted) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val figure = rupees(draft.amountPaise, decimals = true)
            CoveText(figure.substringBefore('.'), "." + figure.substringAfter('.'), style = MoneyType.Big)
            CoveText(draft.title, style = CoveType.Body)
        }
        RowsCard {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CoveText("Category", style = MoneyType.Row, color = c.muted)
                Row(
                    Modifier
                        .height(36.dp)
                        .background(c.canvas, CoveShapes.Pill)
                        .pressable(onCategory)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CoveText(draft.category, style = CoveType.Meta)
                    CoveIcon(CoveIcons.ChevronDown, c.ink, size = 12.dp)
                }
            }
            FactRow("When", draft.whenText)
            FactRow("Paid with", draft.paidWith)
        }
        draft.soFar?.let { CoveText(it, style = MoneyType.Note, color = c.muted) }
        if (fillHeight) Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Save", onSave, Modifier.weight(1f), height = 56.dp, textStyle = MoneyType.Row.copy(fontWeight = FontWeight.Medium))
            PillButton(
                "Change", onChange, Modifier.width(120.dp), kind = ButtonKind.Secondary, height = 56.dp,
                container = c.card, textStyle = MoneyType.Row,
            )
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    RowDivider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CoveText(label, style = MoneyType.Row, color = Cove.colors.muted)
        CoveText(value, style = MoneyType.Row)
    }
}
