package app.cove.companion.feature.training.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.core.OneShot
import app.cove.companion.core.appViewModel
import app.cove.companion.data.local.entity.PlanExerciseEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.Chip
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeekPlan
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.ui.PrimaryButton
import app.cove.companion.feature.training.ui.SimpleField
import app.cove.companion.feature.training.ui.StepRow
import app.cove.companion.feature.training.ui.TextAction

/**
 * Add or edit one exercise of a weekday: name (with suggestions from earlier names), weight, sets, reps and the
 * jump used when Cove suggests more. [initial] null means adding. The guard makes a double tap on Save count once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseSheet(
    visible: Boolean,
    title: String,
    initial: PlanExerciseEntity?,
    names: List<String>,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (name: String, kg: Double, sets: Int, reps: Int, incrementKg: Double) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val key = initial?.id
    var name by remember(key, visible) { mutableStateOf(initial?.name.orEmpty()) }
    var weight by remember(key, visible) { mutableStateOf(initial?.weightKg?.takeIf { it > 0 }?.let { WeightFormat.number(it, unit) }.orEmpty()) }
    var sets by remember(key, visible) { mutableIntStateOf(initial?.sets ?: 3) }
    var reps by remember(key, visible) { mutableIntStateOf(initial?.reps ?: 8) }
    var jump by remember(key, visible) { mutableDoubleStateOf(initial?.incrementKg ?: 2.5) }
    var jumpTouched by remember(key, visible) { mutableStateOf(false) }
    val guard = remember(key, visible) { OneShot() }
    val scope = rememberCoroutineScope()
    val kg = if (weight.isBlank()) 0.0 else WeightFormat.parse(weight)?.let(unit::toKg)
    val typed = name.trim()
    val suggestions = names.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(5)
    CoveSheet(visible, onDismiss, Modifier.imePadding()) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CoveText(title, style = CoveType.Heading)
            SimpleField(name, { name = it }, "Exercise name")
            if (suggestions.isNotEmpty() && initial == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                suggestions.forEach { s ->
                    Chip(s, { name = s; if (!jumpTouched) jump = WeekPlan.defaultIncrement(s) }, height = 48)
                }
            }
            SimpleField(weight, { weight = it }, "Weight in ${unit.label} (blank for bodyweight)", decimal = true, label = "Weight in ${unit.label}")
            StepRow("Sets", sets.toString(), { sets = maxOf(1, sets - 1) }, { sets = minOf(20, sets + 1) })
            StepRow("Reps", reps.toString(), { reps = maxOf(1, reps - 1) }, { reps = minOf(100, reps + 1) })
            StepRow(
                "Jump", WeightFormat.withUnit(jump, unit),
                { jumpTouched = true; jump = stepJump(jump, -1, unit) }, { jumpTouched = true; jump = stepJump(jump, 1, unit) },
            )
            CoveText("Jump is how much heavier Cove suggests after a good session.", style = CoveType.Meta, color = Cove.colors.muted)
            PrimaryButton(
                if (initial == null) "Add" else "Save",
                { kg?.let { w -> guard.launch(scope) { onSave(typed, w, sets, reps, jump); true } } },
                Modifier.fillMaxWidth().padding(top = 4.dp), enabled = typed.isNotEmpty() && kg != null && !guard.busy,
            )
            if (onDelete != null) TextAction("Delete exercise", { guard.launch(scope) { onDelete(); true } }, strong = false)
        }
    }
}

private fun stepJump(kg: Double, dir: Int, unit: WeightUnit): Double {
    val shown = Math.round((unit.fromKg(kg) + dir * if (unit == WeightUnit.Kg) 0.5 else 1.0) * 10) / 10.0
    return unit.toKg(shown.coerceIn(if (unit == WeightUnit.Kg) 0.5 else 1.0, 25.0))
}

/** The add sheet for [weekday], usable from any screen (Training's "Add exercise"). */
@Composable
fun AddExerciseHost(visible: Boolean, weekday: Int, unit: WeightUnit, onDismiss: () -> Unit) {
    val vm = appViewModel(key = "add-$weekday") { PlanDayViewModel(it, weekday) }
    val ui by vm.state.collectAsState()
    ExerciseSheet(
        visible, "Add to ${TrainingText.weekdayName(weekday)}s", null, ui.names, unit, onDismiss,
        onSave = { n, kg, s, r, inc -> vm.add(n, kg, s, r, inc); onDismiss() },
    )
}
