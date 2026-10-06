package app.cove.companion.feature.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.engine.TrainingText

/** Round 48dp step button used by the sheets. */
@Composable
fun MiniStep(glyph: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).background(Cove.colors.well, CoveShapes.Circle).pressable(onClick, role = Role.Button).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { CoveText(glyph, style = CoveType.Section.copy(fontWeight = FontWeight.Normal)) }
}

/** Label on the left, then − value + on the right; [value] reads aloud as "<label> <value>". */
@Composable
fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoveText(label, Modifier.weight(1f), style = TrainingType.Row, color = Cove.colors.muted)
        MiniStep("−", "Decrease $label", onMinus)
        CoveText(value, Modifier.padding(horizontal = 4.dp).semantics { contentDescription = "$label $value" }, style = TrainingType.Row, textAlign = TextAlign.Center)
        MiniStep("+", "Increase $label", onPlus)
    }
}

/** Single-line text field in the well style of the app's other sheets. */
@Composable
fun SimpleField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, decimal: Boolean = false, label: String = hint) {
    val c = Cove.colors
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp).background(c.well, CoveShapes.Card).padding(horizontal = 16.dp).semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) CoveText(hint, style = TrainingType.Row, color = c.placeholder)
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = TrainingType.Row.copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text),
        )
    }
}

/** Mon..Sun chips that toggle; [selected] holds ISO weekdays and [disabled] one that cannot be picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdayChips(selected: Set<Int>, onToggle: (Int) -> Unit, disabled: Int? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (d in 1..7) {
            if (d == disabled) continue
            WellChip(TrainingText.weekdayShort(d), { onToggle(d) }, selected = d in selected)
        }
    }
}

/** 48dp pill that shows up on a white sheet: well grey, ink when [selected]. */
@Composable
fun WellChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    val c = Cove.colors
    Box(
        modifier.heightIn(min = 48.dp).background(if (selected) c.accent else c.well, CoveShapes.Pill)
            .pressable(onClick, role = Role.Button).semantics { this.selected = selected }.padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { CoveText(text, style = CoveType.Meta, color = if (selected) c.onAccent else c.ink, maxLines = 1) }
}
