package app.cove.companion.feature.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cove.companion.core.OneShot
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.Chip
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.feature.money.AmountKeypad
import app.cove.companion.feature.training.engine.BodyWeightInput
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.engine.WorkoutRow

/** [kg] moved by [steps] in [unit]'s own step (the lift's jump in kg, 5 lb in pounds), never below zero. */
private fun bump(kg: Double, steps: Int, unit: WeightUnit, incrementKg: Double): Double {
    val step = if (unit == WeightUnit.Kg) incrementKg else unit.stepperStep
    return unit.toKg(maxOf(0.0, Math.round((unit.fromKg(kg) + steps * step) * 10) / 10.0))
}

/**
 * Logs what was done for [row]: weight stepper, reps per set as chips, and one tap "Done as planned" while nothing was
 * changed. Saving again replaces today's log; "Remove log" takes it away. The guards make a double tap count once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogSheet(
    visible: Boolean,
    row: WorkoutRow?,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (kg: Double, reps: List<Int>) -> Unit,
    onWeightOnly: (kg: Double) -> Unit,
    onRemove: () -> Unit,
) {
    val ex = row?.exercise
    val startKg = row?.done?.weightKg ?: ex?.weightKg ?: 0.0
    val startReps = row?.reps?.takeIf { it.isNotEmpty() } ?: List(ex?.sets ?: 3) { ex?.reps ?: 8 }
    var kg by remember(ex?.id, visible) { mutableDoubleStateOf(startKg) }
    val reps = remember(ex?.id, visible) { mutableStateListOf<Int>().also { it.addAll(startReps) } }
    var sel by remember(ex?.id, visible) { mutableIntStateOf(0) }
    val guard = remember(ex?.id, visible) { OneShot() }
    val scope = rememberCoroutineScope()
    val c = Cove.colors
    val changed = kg != startKg || reps.toList() != startReps
    val done = row?.done != null
    CoveSheet(visible, onDismiss) {
        if (ex == null) return@CoveSheet
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CoveText(ex.name, style = CoveType.Heading)
            CoveText("Plan: " + TrainingText.detail(ex.weightKg, ex.sets, ex.reps, unit), style = CoveType.Meta, color = c.muted)
            StepRow(
                "Weight", if (kg <= 0.0) "Bodyweight" else WeightFormat.withUnit(kg, unit),
                { kg = bump(kg, -1, unit, ex.incrementKg) }, { kg = bump(kg, 1, unit, ex.incrementKg) },
            )
            CoveText("Reps per set", Modifier.padding(top = 4.dp), style = CoveType.Meta, color = c.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                reps.forEachIndexed { i, r ->
                    Chip("$r", { sel = i }, selected = i == sel.coerceAtMost(reps.lastIndex), height = 48, modifier = Modifier.semantics { contentDescription = "Set ${i + 1}, $r reps" })
                }
            }
            val s = sel.coerceIn(0, maxOf(0, reps.lastIndex))
            if (reps.isNotEmpty()) StepRow("Set ${s + 1} reps", reps[s].toString(), { reps[s] = maxOf(1, reps[s] - 1) }, { reps[s] = minOf(100, reps[s] + 1) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextAction("Add set", { reps.add(reps.lastOrNull() ?: ex.reps); sel = reps.lastIndex }, strong = false)
                if (reps.size > 1) TextAction("Remove last set", { reps.removeLast() }, strong = false)
            }
            PrimaryButton(
                if (changed || done) "Save" else "Done as planned",
                { guard.launch(scope) { onSave(kg, reps.toList()); true } },
                Modifier.fillMaxWidth().padding(top = 4.dp), enabled = reps.isNotEmpty() && !guard.busy,
            )
            if (!done && kg != startKg) TextAction("Just change today's weight", { guard.launch(scope) { onWeightOnly(kg); true } }, strong = false)
            if (done) TextAction("Remove today's log", { guard.launch(scope) { onRemove(); true } }, strong = false)
        }
    }
}

/** Today's weigh-in: big number, the numeric keypad and Save. Starts from today's value or the last one. */
@Composable
fun WeightSheet(visible: Boolean, unit: WeightUnit, initialKg: Double?, onDismiss: () -> Unit, onSave: (kg: Double) -> Unit) {
    val initial = initialKg?.let { WeightFormat.number(it, unit) }.orEmpty()
    var text by remember(visible, initial) { mutableStateOf<String?>(null) }
    val guard = remember(visible) { OneShot() }
    val scope = rememberCoroutineScope()
    val shown = text ?: initial
    val value = BodyWeightInput.value(shown, unit)
    CoveSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CoveText("Weight today", style = CoveType.Meta, color = Cove.colors.muted)
            CoveText(
                shown.ifEmpty { "0" }, " ${unit.label}", style = TrainingType.Big, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = "Weight ${shown.ifEmpty { "none yet" }} ${unit.label}" },
            )
            AmountKeypad(
                { k -> text = BodyWeightInput.push(text ?: "", k) },
                { text = BodyWeightInput.back(text ?: initial) },
                { text = "" },
            )
            PrimaryButton(
                if (value != null) "Save ${WeightFormat.trim(value)} ${unit.label}" else "Save",
                { value?.let { v -> guard.launch(scope) { onSave(unit.toKg(v)); true } } },
                Modifier.fillMaxWidth(), enabled = value != null && !guard.busy,
            )
        }
    }
}
