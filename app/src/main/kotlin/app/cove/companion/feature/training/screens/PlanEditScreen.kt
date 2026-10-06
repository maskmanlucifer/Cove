package app.cove.companion.feature.training.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.data.local.entity.ExerciseEntity
import app.cove.companion.data.repo.idList
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.Segmented
import app.cove.companion.design.components.UndoHost
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.DayChip
import app.cove.companion.feature.training.MiniStep
import app.cove.companion.feature.training.PlanEditViewModel
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.ui.BackChevron
import app.cove.companion.feature.training.ui.PrimaryButton
import app.cove.companion.feature.training.ui.SecondaryButton
import app.cove.companion.feature.training.ui.StepRow
import app.cove.companion.feature.training.ui.TrainingTopBar
import app.cove.companion.feature.training.ui.TrainingType
import app.cove.companion.navigation.Nav
import kotlinx.coroutines.launch
import java.time.DayOfWeek

private data class Editing(val dayId: String, val exercise: ExerciseEntity)

/** Plan editor: lifts per day (reorder, remove, add, tune sets and reps), units, rest time and training days. */
@Composable
fun PlanEditScreen(nav: Nav) {
    val vm = appViewModel { PlanEditViewModel(it) }
    val snap by vm.snap.collectAsState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    val c = Cove.colors
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            TrainingTopBar("Plan", { BackChevron(nav.back) })
            val s = snap
            if (s != null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                CoveText("Your plan", style = CoveType.Title)
                s.tables.planDays.sortedBy { it.sort }.forEach { day ->
                    Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(horizontal = 20.dp, vertical = 8.dp)) {
                        CoveText(day.dayType, Modifier.padding(top = 10.dp, bottom = 4.dp), style = CoveType.Meta, color = c.muted)
                        val ids = day.exerciseIds.idList()
                        ids.mapNotNull { s.exercise(it) }.forEachIndexed { i, e ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.well))
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).pressable({ editing = Editing(day.id, e) }, onClickLabel = "Edit ${e.name}", role = Role.Button), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    CoveText(e.name, style = TrainingType.Row)
                                    CoveText(repText(e), style = CoveType.Meta, color = c.muted)
                                }
                                MiniStep("↑", "Move ${e.name} up") { scope.launch { vm.move(day.id, e.id, -1) } }
                                MiniStep("↓", "Move ${e.name} down") { scope.launch { vm.move(day.id, e.id, 1) } }
                            }
                        }
                        Box(Modifier.heightIn(min = 48.dp).pressable({ adding = day.id }, role = Role.Button), contentAlignment = Alignment.CenterStart) {
                            CoveText("+ Add exercise", style = TrainingType.Sub, color = c.muted)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CoveText("Units", style = CoveType.Meta, color = c.muted)
                    Segmented(listOf("kg", "lb"), if (s.unit == WeightUnit.Kg) 0 else 1, { scope.launch { vm.setUnit(if (it == 0) WeightUnit.Kg else WeightUnit.Lb) } }, Modifier.fillMaxWidth(), fillWidth = true)
                    CoveText("Rest between sets", style = CoveType.Meta, color = c.muted)
                    StepRow("Rest", TrainingText.clock(s.restSeconds), { scope.launch { vm.restDelta(-15) } }, { scope.launch { vm.restDelta(15) } })
                    CoveText("Training days", style = CoveType.Meta, color = c.muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { d -> DayChip(d, d in s.weekdays, { scope.launch { vm.toggleDay(d) } }, Modifier.weight(1f)) }
                    }
                }
            }
        }
        UndoHost("training", Modifier.align(Alignment.TopCenter))
        val s = snap
        val dayId = adding
        CoveSheet(dayId != null && s != null, { adding = null }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                CoveText("Add exercise", Modifier.padding(vertical = 8.dp), style = CoveType.Meta, color = c.muted)
                if (s != null && dayId != null) vm.candidates(s, dayId).forEach { name ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable({ adding = null; scope.launch { vm.add(dayId, name) } }, role = Role.Button), contentAlignment = Alignment.CenterStart) {
                        CoveText(name, style = TrainingType.Row)
                    }
                }
            }
        }
        val ed = editing
        var sets by remember(ed?.exercise?.id) { mutableIntStateOf(ed?.exercise?.sets ?: 3) }
        var lo by remember(ed?.exercise?.id) { mutableIntStateOf(ed?.exercise?.repMin ?: 8) }
        var hi by remember(ed?.exercise?.id) { mutableIntStateOf(ed?.exercise?.repMax ?: 8) }
        CoveSheet(ed != null, { editing = null }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CoveText(ed?.exercise?.name.orEmpty(), style = CoveType.Meta, color = c.muted)
                StepRow("Sets", sets.toString(), { sets = maxOf(1, sets - 1) }, { sets = minOf(10, sets + 1) })
                StepRow("Fewest reps", lo.toString(), { lo = maxOf(1, lo - 1); hi = maxOf(hi, lo) }, { lo = minOf(50, lo + 1); hi = maxOf(hi, lo) })
                StepRow("Most reps", hi.toString(), { hi = maxOf(lo, hi - 1) }, { hi = minOf(50, hi + 1) })
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Done", { ed?.let { scope.launch { vm.tune(it.exercise, sets, lo, hi, it.exercise.incrementKg) } }; editing = null }, Modifier.weight(1f))
                    SecondaryButton("Remove", { ed?.let { scope.launch { vm.remove(it.dayId, it.exercise.id, it.exercise.name) } }; editing = null })
                }
            }
        }
    }
}

private fun repText(e: ExerciseEntity) = "${e.sets}×" + if (e.repMax > e.repMin) "${e.repMin}-${e.repMax}" else e.repMin.toString()
