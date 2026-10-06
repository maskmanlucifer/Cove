package app.cove.companion.feature.money

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText

private val KeyStyle = CoveType.Section.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
private val Rows = listOf("123", "456", "789")

/** Phone-style amount keypad: digits, a decimal point and backspace (long-press clears). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AmountKeypad(onKey: (Char) -> Unit, onBack: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (Rows + listOf(".0⌫")).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { ch ->
                    val source = remember { MutableInteractionSource() }
                    val pressed by source.collectIsPressedAsState()
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .semantics { if (ch == '⌫') contentDescription = "Backspace" else if (ch == '.') contentDescription = "Decimal point" }
                            .graphicsLayer { alpha = if (pressed) 0.4f else 1f; scaleX = if (pressed) 0.94f else 1f; scaleY = scaleX }
                            .combinedClickable(
                                interactionSource = source,
                                indication = null,
                                onLongClick = if (ch == '⌫') onClear else null,
                                onLongClickLabel = if (ch == '⌫') "Clear amount" else null,
                                onClick = { if (ch == '⌫') onBack() else onKey(ch) },
                            )
                            .semantics {
                                role = Role.Button
                                when (ch) {
                                    '⌫' -> contentDescription = "Delete digit"
                                    '.' -> contentDescription = "Decimal point"
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ch == '⌫') CoveIcon(CoveIcons.Backspace, Cove.colors.ink, size = 24.dp)
                        else CoveText(ch.toString(), style = KeyStyle)
                    }
                }
            }
        }
    }
}
