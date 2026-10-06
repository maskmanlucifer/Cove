package app.cove.companion.feature.money

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.coveTopInset

/** Debug-only host that shows [ExpenseDraftCard] with frame 25's sample, since Voice embeds it later. */
@Composable
fun MoneyLoggedDebugScreen() {
    CoveScreen {
        ExpenseDraftCard(
            ExpenseDraft(
                amountPaise = 34_000,
                title = "Lunch · Café Ivy",
                category = "Food",
                whenText = "Today, 1:12 pm",
                paidWith = "UPI",
                quote = "Spent 340 on lunch at Café Ivy",
                soFar = MoneyMath.soFarLine("Food", 734_000, 900_000),
            ),
            onSave = {},
            onChange = {},
            modifier = Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 40.dp),
        )
    }
}
