package app.cove.companion.feature.training.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.BalancedText
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.ui.PrimaryButton
import app.cove.companion.feature.training.ui.SecondaryButton
import app.cove.companion.feature.training.ui.StepRow
import app.cove.companion.feature.training.ui.TrainingType
import app.cove.companion.feature.voice.Quote
import app.cove.companion.feature.voice.VoiceState
import app.cove.companion.feature.voice.VoiceViewModel
import app.cove.companion.feature.voice.ActionPair
import app.cove.companion.design.components.coveTopInset

/** Frame 44: the sets heard, as drafts, what they mean for next time, and Save sets / Edit. */
@Composable
fun LogSetsDraft(s: VoiceState, vm: VoiceViewModel) {
    val p = s.setsPreview ?: return
    val c = Cove.colors
    var editing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (s.transcript.isNotBlank()) Quote(s.transcript)
                val (head, tail) = TrainingText.setsHeadline(p.sets.size) to " ${p.exercise}."
                BalancedText(head, tail, CoveType.Title)
                Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(horizontal = 20.dp)) {
                    p.sets.forEachIndexed { i, set ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.well))
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            CoveText("Set ${i + 1}", style = CoveType.Body)
                            CoveText(if (p.bodyweight) "${set.reps} reps" else WeightFormat.set(set.weightKg, set.reps, p.unit), style = CoveType.Body)
                        }
                    }
                }
                CoveText(p.note, style = CoveType.Meta.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp)), color = c.muted)
            }
            ActionPair("Save sets", vm::save, "Edit", 104) { editing = true }
        }
        EditSheet(editing, p, { editing = false }) { vm.editSets(it); editing = false }
    }
}

@Composable
private fun EditSheet(visible: Boolean, p: SetsPreview, onDismiss: () -> Unit, onDone: (List<PreviewSet>) -> Unit) {
    var rows by remember(p, visible) { mutableStateOf(p.sets) }
    CoveSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CoveText("Edit sets · ${p.exercise}", Modifier.padding(bottom = 4.dp), style = CoveType.Meta, color = Cove.colors.muted)
            rows.forEachIndexed { i, r ->
                if (!p.bodyweight) StepRow(
                    "Set ${i + 1} weight", WeightFormat.withUnit(r.weightKg, p.unit),
                    { rows = rows.toMutableList().also { it[i] = r.copy(weightKg = bump(r.weightKg, p, -1)) } },
                    { rows = rows.toMutableList().also { it[i] = r.copy(weightKg = bump(r.weightKg, p, 1)) } },
                )
                StepRow(
                    "Set ${i + 1} reps", r.reps.toString(),
                    { rows = rows.toMutableList().also { it[i] = r.copy(reps = maxOf(1, r.reps - 1)) } },
                    { rows = rows.toMutableList().also { it[i] = r.copy(reps = minOf(100, r.reps + 1)) } },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Done", { onDone(rows) }, Modifier.weight(1f))
                if (rows.size > 1) SecondaryButton("Remove last", { rows = rows.dropLast(1) })
                SecondaryButton("Add set", { rows = rows + rows.last() })
            }
            Box(Modifier.height(4.dp))
        }
    }
}

private fun bump(kg: Double, p: SetsPreview, dir: Int): Double =
    p.unit.toKg(maxOf(0.0, Math.round((p.unit.fromKg(kg) + dir * p.unit.stepperStep) * 10) / 10.0))
