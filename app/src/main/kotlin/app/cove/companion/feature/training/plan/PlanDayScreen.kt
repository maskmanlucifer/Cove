package app.cove.companion.feature.training.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cove.companion.core.OneShot
import app.cove.companion.core.appViewModel
import app.cove.companion.data.local.entity.PlanExerciseEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.AccentButton
import app.cove.companion.feature.training.ui.WellChip
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.UndoHost
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.engine.StarterTemplate
import app.cove.companion.feature.training.engine.TodayWorkout
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.ui.BackChevron
import app.cove.companion.feature.training.ui.PrimaryButton
import app.cove.companion.feature.training.ui.TextAction
import app.cove.companion.feature.training.ui.WeekdayChips
import app.cove.companion.navigation.Nav

private enum class Sheet { None, Add, Copy, Template }

/** Editor of one weekday's exercises: add, edit, delete with Undo, reorder, copy to other days, start from a template. */
@Composable
fun PlanDayScreen(weekday: Int, nav: Nav) {
    val vm = appViewModel(key = "plan-$weekday") { PlanDayViewModel(it, weekday) }
    val ui by vm.state.collectAsState()
    var editing by remember { mutableStateOf<PlanExerciseEntity?>(null) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    val c = Cove.colors
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { BackChevron(nav.back) }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = DockClearance + 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoveText(TrainingText.weekdayName(weekday), style = CoveType.Title)
                    CoveText("These show on Today every ${TrainingText.weekdayName(weekday)}.", style = CoveType.Meta, color = c.muted)
                }
                if (ui.loaded) {
                    if (ui.rows.isEmpty()) {
                        Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoveText("Nothing here yet.", style = CoveType.Heading)
                            CoveText("Add an exercise, or start from Push, Pull or Legs.", style = CoveType.Meta, color = c.muted)
                        }
                    } else Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(start = 20.dp, end = 8.dp)) {
                        ui.rows.forEachIndexed { i, row ->
                            if (i > 0) Hairline()
                            PlanRow(row, ui.unit, i > 0, i < ui.rows.lastIndex, { editing = row }, { vm.move(row, -1) }, { vm.move(row, 1) })
                        }
                    }
                    AccentButton("Add exercise", { sheet = Sheet.Add }, Modifier.heightIn(min = 48.dp))
                    Column {
                        if (ui.rows.isNotEmpty()) TextAction("Copy to other days", { sheet = Sheet.Copy }, strong = false)
                        TextAction("Start from Push, Pull or Legs", { sheet = Sheet.Template }, strong = false)
                    }
                }
            }
        }
        ExerciseSheet(
            sheet == Sheet.Add, "Add to ${TrainingText.weekdayName(weekday)}", null, ui.names, ui.unit, { sheet = Sheet.None },
            onSave = { n, kg, s, r, inc -> vm.add(n, kg, s, r, inc); sheet = Sheet.None },
        )
        val e = editing
        ExerciseSheet(
            e != null, "Edit exercise", e, ui.names, ui.unit, { editing = null },
            onSave = { n, kg, s, r, inc -> e?.let { vm.update(it.copy(name = n, weightKg = kg, sets = s, reps = r, incrementKg = inc)) }; editing = null },
            onDelete = { e?.let { vm.delete(it) }; editing = null },
        )
        CopySheet(sheet == Sheet.Copy, weekday, { sheet = Sheet.None }) { days -> vm.copyTo(days); sheet = Sheet.None }
        TemplateSheet(sheet == Sheet.Template, weekday, { sheet = Sheet.None }) { t, days -> vm.applyTemplate(t, days); sheet = Sheet.None }
        UndoHost("training", Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = DockClearance))
    }
}

@Composable
private fun PlanRow(row: PlanExerciseEntity, unit: WeightUnit, canUp: Boolean, canDown: Boolean, onEdit: () -> Unit, onUp: () -> Unit, onDown: () -> Unit) {
    val c = Cove.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).pressable(onEdit, onClickLabel = "Edit ${row.name}", role = Role.Button).padding(vertical = 10.dp)
                .semantics(mergeDescendants = true) { contentDescription = TrainingText.spoken(row.name, row.weightKg, row.sets, row.reps, unit) },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CoveText(row.name, style = CoveType.Body)
            CoveText(TrainingText.detail(row.weightKg, row.sets, row.reps, unit), style = CoveType.Meta, color = c.muted)
        }
        MoveButton(CoveIcons.ChevronUp, "Move ${row.name} up", canUp, onUp)
        MoveButton(CoveIcons.ChevronDown, "Move ${row.name} down", canDown, onDown)
    }
}

@Composable
private fun MoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).pressable(onClick, enabled = enabled, role = Role.Button).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { CoveIcon(icon, if (enabled) Cove.colors.ink else Cove.colors.tail.copy(alpha = 0.4f), size = 18.dp) }
}

/** Pick the weekdays to copy this day to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CopySheet(visible: Boolean, from: Int, onDismiss: () -> Unit, onCopy: (Set<Int>) -> Unit) {
    var days by remember(visible) { mutableStateOf(emptySet<Int>()) }
    val guard = remember(visible) { OneShot() }
    val scope = rememberCoroutineScope()
    CoveSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoveText("Copy ${TrainingText.weekdayName(from)} to", style = CoveType.Heading)
            WeekdayChips(days, { days = if (it in days) days - it else days + it }, disabled = from)
            PrimaryButton("Copy", { guard.launch(scope) { onCopy(days); true } }, Modifier.fillMaxWidth(), enabled = days.isNotEmpty() && !guard.busy)
        }
    }
}

/** Pick Push, Pull or Legs and the weekdays to add it to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateSheet(visible: Boolean, weekday: Int, onDismiss: () -> Unit, onApply: (StarterTemplate, Set<Int>) -> Unit) {
    var pick by remember(visible) { mutableStateOf(0) }
    var days by remember(visible) { mutableStateOf(setOf(weekday)) }
    val guard = remember(visible) { OneShot() }
    val scope = rememberCoroutineScope()
    val t = TodayWorkout.starters[pick]
    CoveSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoveText("Start from", style = CoveType.Heading)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayWorkout.starters.forEachIndexed { i, s -> WellChip(s.name, { pick = i }, selected = i == pick) }
            }
            CoveText(t.lifts.joinToString(", ") { it.first }, style = CoveType.Meta, color = Cove.colors.muted)
            CoveText("On", style = CoveType.Meta, color = Cove.colors.muted)
            WeekdayChips(days, { days = if (it in days) days - it else days + it })
            PrimaryButton("Add ${t.name}", { guard.launch(scope) { onApply(t, days); true } }, Modifier.fillMaxWidth(), enabled = days.isNotEmpty() && !guard.busy)
        }
    }
}
