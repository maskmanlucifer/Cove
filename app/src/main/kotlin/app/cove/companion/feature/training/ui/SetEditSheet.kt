package app.cove.companion.feature.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.MiniStep
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit

/**
 * Sheet to change one set: weight and reps with steppers, Done, and Delete (which the caller makes undoable).
 *
 * @param key identifies the set, so the numbers reset when another one is opened.
 */
@Composable
fun SetEditSheet(
    visible: Boolean,
    key: String,
    title: String,
    weightKg: Double,
    reps: Int,
    unit: WeightUnit,
    bodyweight: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double, Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = Cove.colors
    var w by remember(key, visible) { mutableDoubleStateOf(weightKg) }
    var r by remember(key, visible) { mutableIntStateOf(reps) }
    CoveSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CoveText(title, style = CoveType.Meta, color = c.muted)
            if (!bodyweight) {
                StepRow("Weight", WeightFormat.withUnit(w, unit), { w = maxOf(0.0, unit.toKg(Math.round((unit.fromKg(w) - unit.stepperStep) * 10) / 10.0)) }, { w = unit.toKg(Math.round((unit.fromKg(w) + unit.stepperStep) * 10) / 10.0) })
            }
            StepRow("Reps", r.toString(), { r = maxOf(1, r - 1) }, { r = minOf(100, r + 1) })
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Done", { onSave(if (bodyweight) 0.0 else w, r) }, Modifier.weight(1f))
                if (onDelete != null) {
                    SecondaryButton("Delete", onDelete)
                }
            }
        }
    }
}

@Composable
internal fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoveText(label, Modifier.weight(1f), style = TrainingType.Row, color = Cove.colors.muted)
        MiniStep("−", "Decrease ${label.lowercase()}", onMinus)
        CoveText(value, style = TrainingType.Row, modifier = Modifier.padding(horizontal = 4.dp))
        MiniStep("+", "Increase ${label.lowercase()}", onPlus)
    }
}
