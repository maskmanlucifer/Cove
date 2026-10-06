package app.cove.companion.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import app.cove.companion.core.toLocalDate
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.BalancedText
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.ai.model.VoiceIntent

/** Frame 03: the drafts for what was heard, with an auto-picked category per to-do; nothing is saved until Save all. */
@Composable
fun ResultView(s: VoiceState, nowMillis: Long, vm: VoiceViewModel) {
    val today = nowMillis.toLocalDate()
    val single = s.drafts.singleOrNull()
    if (single is VoiceIntent.LogExpense) {
        ExpenseDraft(s, single, nowMillis, vm)
        return
    }
    val (head, tail) = resultHeadline(s.drafts, today)
    var todoIndex = 0
    val rows = s.drafts.flatMap { intent ->
        if (intent is VoiceIntent.AddTodos) {
            intent.items.map { item ->
                val index = todoIndex++
                @Composable { CardRow(item.title) { CategoryChip(item.category ?: "Choose", s.categories) { vm.setTodoCategory(index, it) } } }
            }
        } else listOf(@Composable { CardRow(describe(intent, today)) {} })
    }
    ResultFrame(
        s,
        headline = { BalancedText(head, tail, CoveType.Title) },
        body = { RowsCard(rows) },
        actions = { ActionPair("Save all", vm::save, "Edit", 104, vm::typeInstead) },
    )
}

/** The shared layout of result-like frames: quote, headline, body, spacer, actions. */
@Composable
fun ResultFrame(s: VoiceState, headline: @Composable () -> Unit, body: @Composable () -> Unit, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (s.transcript.isNotBlank()) Quote(s.transcript)
        headline()
        body()
        Box(Modifier.weight(1f))
        actions()
    }
}

/** Answer to a question such as "what's next". */
@Composable
fun AnswerView(s: VoiceState, onDone: () -> Unit) {
    ResultFrame(
        s,
        headline = { BalancedText(s.answer, "", CoveType.Title) },
        body = {},
        actions = { ActionPair("Done", onDone, "Ask again", 120, onDone) },
    )
}

/** Rounded category chip; tapping opens a small list to pick another category. */
@Composable
fun CategoryChip(name: String, options: List<String>, height: Int = 34, onPick: (String) -> Unit) {
    val c = Cove.colors
    var open by remember { mutableStateOf(false) }
    val below = with(LocalDensity.current) { (height + 6).dp.roundToPx() }
    Box {
        Row(
            Modifier.height(height.dp).background(c.canvas, CoveShapes.Pill).pressable({ open = true }).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CoveText(name, style = CoveType.Meta, color = if (height == 34) c.muted else c.ink)
            CoveIcon(CoveIcons.ChevronDown, if (height == 34) c.muted else c.ink, size = 12.dp)
        }
        if (open && options.isNotEmpty()) {
            Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, below), onDismissRequest = { open = false }) {
                Column(
                    Modifier
                        .shadow(24.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x33141420), spotColor = Color(0x33141420))
                        .background(c.card, RoundedCornerShape(20.dp))
                        .padding(vertical = 6.dp)
                        .width(168.dp),
                ) {
                    options.forEach { option ->
                        Box(
                            Modifier.fillMaxWidth().height(44.dp).pressable({ onPick(option); open = false }).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { CoveText(option, style = CoveType.Meta.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)), color = if (option == name) c.ink else c.muted) }
                    }
                }
            }
        }
    }
}
