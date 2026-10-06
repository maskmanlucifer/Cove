package app.cove.companion.feature.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.AccentButton
import app.cove.companion.design.components.CheckCircle
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.engine.WorkoutRow

/** "Bench press, 60 kilograms, 3 sets of 8, done" for screen readers. */
fun spokenRow(row: WorkoutRow, unit: WeightUnit): String {
    val e = row.exercise
    val kg = row.done?.weightKg ?: e.weightKg
    return TrainingText.spoken(e.name, kg, e.sets, e.reps, unit) + if (row.done != null) ", done: " + TrainingText.repsList(row.reps) else ""
}

/** One exercise of today: check, name, "60 kg · 3 x 8" (or what was done), and the suggestion below it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutRowView(
    row: WorkoutRow,
    unit: WeightUnit,
    onOpen: () -> Unit,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
    canAsk: Boolean,
    opinion: String?,
    onAsk: () -> Unit,
) {
    val c = Cove.colors
    val e = row.exercise
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .pressable(onOpen, onClickLabel = "Log ${e.name}", role = Role.Button)
                .semantics(mergeDescendants = true) { contentDescription = spokenRow(row, unit); stateDescription = if (row.done != null) "Done" else "Not done" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CheckCircle(row.done != null, onToggle = null, size = 24)
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CoveText(e.name, style = CoveType.Body)
                val line = row.done?.let { d -> (if (d.weightKg > 0) WeightFormat.withUnit(d.weightKg, unit) + " · " else "") + TrainingText.repsList(row.reps) + " reps" }
                    ?: TrainingText.detail(e.weightKg, e.sets, e.reps, unit)
                CoveText(line, style = CoveType.Meta, color = c.muted)
            }
        }
        row.advice?.let { a ->
            Column(Modifier.padding(start = 38.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CoveText(a.reason, style = CoveType.Meta, color = c.muted)
                if (a.actionable) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AccentButton("Use ${WeightFormat.withUnit(a.weightKg, unit)}", onUse, Modifier.heightIn(min = 48.dp))
                        Box(
                            Modifier.size(48.dp).pressable(onDismiss, onClickLabel = "Hide suggestion", role = Role.Button).semantics { contentDescription = "Hide suggestion for ${e.name}" },
                            contentAlignment = Alignment.Center,
                        ) { CoveIcon(CoveIcons.Close, c.muted, size = 16.dp) }
                    }
                    if (canAsk && opinion == null) {
                        Box(Modifier.heightIn(min = 48.dp).pressable(onAsk, role = Role.Button), contentAlignment = Alignment.CenterStart) {
                            CoveText("Ask Cove", style = CoveType.Meta, color = c.accent)
                        }
                    }
                }
                opinion?.let { CoveText(it, style = CoveType.Meta, color = c.ink) }
            }
        }
    }
}

/** Tiny line of recent [values] with the last point filled; decorative, so [summary] speaks for it. */
@Composable
fun Sparkline(values: List<Double>, summary: String, modifier: Modifier = Modifier) {
    val c = Cove.colors
    Canvas(modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = summary }) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val hi = values.max()
        val span = if (hi - lo < 0.001) 1.0 else hi - lo
        val pad = 4.dp.toPx()
        fun x(i: Int) = pad + i * (size.width - 2 * pad) / (values.size - 1)
        fun y(v: Double) = (pad + (1 - (v - lo) / span) * (size.height - 2 * pad)).toFloat()
        val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        drawPath(path, c.ink, style = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(c.ink, 3.5.dp.toPx(), Offset(x(values.lastIndex), y(values.last())))
    }
}
