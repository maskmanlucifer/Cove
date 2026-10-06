package app.cove.companion.feature.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText

/**
 * Sheet to type a number (weight, reps, body weight) instead of stepping to it. The keyboard lifts the sheet, so
 * Done stays visible; a value that is not a usable number keeps Done inert and says why.
 *
 * @param decimal true for weights, false for whole numbers such as reps.
 */
@Composable
fun NumberEntrySheet(
    visible: Boolean,
    title: String,
    initial: String,
    unit: String,
    decimal: Boolean,
    onDismiss: () -> Unit,
    onDone: (Double) -> Unit,
) {
    val c = Cove.colors
    var field by remember(visible, initial) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val requester = remember { FocusRequester() }
    val parsed = field.text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it < 1000 && (decimal || it % 1.0 == 0.0) }
    LaunchedEffect(visible) { if (visible) runCatching { requester.requestFocus() } }
    CoveSheet(visible, onDismiss, Modifier.imePadding()) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoveText(title, style = CoveType.Meta, color = c.muted)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BasicTextField(
                    field, { field = it },
                    Modifier.fillMaxWidth().focusRequester(requester),
                    singleLine = true,
                    textStyle = TypeInStyle.copy(color = c.ink, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(c.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { parsed?.let(onDone) }),
                )
            }
            CoveText(
                if (field.text.isNotBlank() && parsed == null) (if (decimal) "Enter a number like 62.5" else "Enter a whole number") else unit,
                style = CoveType.Meta, color = if (field.text.isNotBlank() && parsed == null) c.alert else c.muted,
            )
            PrimaryButton("Done", { parsed?.let(onDone) }, Modifier.fillMaxWidth(), enabled = parsed != null)
        }
    }
}
