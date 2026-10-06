package app.cove.companion.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.AccentButton
import app.cove.companion.design.components.CoveCard
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.Segmented
import app.cove.companion.design.components.UndoHost
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.plan.AddExerciseHost
import app.cove.companion.feature.training.ui.LabelRow
import app.cove.companion.feature.training.ui.LogSheet
import app.cove.companion.feature.training.ui.PrimaryButton
import app.cove.companion.feature.training.ui.Sparkline
import app.cove.companion.feature.training.ui.TrainingType
import app.cove.companion.feature.training.ui.WeightSheet
import app.cove.companion.feature.training.ui.WorkoutRowView
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** The Training page: today's workout, my weight, the weekly plan and a way to the progress. One calm scroll, no setup. */
@Composable
fun TrainingScreen(nav: Nav) {
    val vm = appViewModel { TrainingViewModel(it) }
    val ui by vm.state.collectAsState()
    var logging by remember { mutableStateOf<String?>(null) }
    var weighing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val c = Cove.colors
    CoveScreen {
        if (ui.loaded) Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = DockClearance + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CoveText("Training", style = CoveType.Title)
            TodayBlock(ui, vm, onOpen = { logging = it }, onAdd = { adding = true }, onPlan = { nav.go(Routes.trainingPlanDay(ui.today.dayOfWeek.value)) })
            WeightBlock(ui) { weighing = true }
            Column {
                CoveText("Plan your week", Modifier.padding(bottom = 8.dp), style = CoveType.Meta, color = c.muted)
                Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(horizontal = 20.dp)) {
                    for (d in 1..7) {
                        val n = ui.counts[d] ?: 0
                        LabelRow(
                            TrainingText.weekdayName(d), TrainingText.exerciseCount(n), first = d == 1,
                            muted = c.ink, valueColor = c.muted, chevron = true, onClick = { nav.go(Routes.trainingPlanDay(d)) },
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(horizontal = 20.dp)) {
                LabelRow("See progress", "", first = true, muted = c.ink, chevron = true, onClick = { nav.go(Routes.TrainingProgress) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CoveText("Weights shown in", style = CoveType.Meta, color = c.muted)
                Segmented(listOf("kg", "lb"), ui.unit.ordinal, { vm.setUnit(WeightUnit.entries[it]) }, height = 44.dp)
            }
        }
        val row = ui.rows.firstOrNull { it.exercise.id == logging }
        LogSheet(
            logging != null && row != null, row, ui.unit, { logging = null },
            onSave = { kg, reps -> row?.let { vm.log(it, kg, reps) }; logging = null },
            onWeightOnly = { kg -> row?.let { vm.changeTodayWeight(it, kg) }; logging = null },
            onRemove = { row?.let { vm.removeLog(it) }; logging = null },
        )
        WeightSheet(weighing, ui.unit, ui.weighedKg ?: ui.lastKg, { weighing = false }) { kg -> vm.saveWeight(kg); weighing = false }
        AddExerciseHost(adding, ui.today.dayOfWeek.value, ui.unit, { adding = false })
        UndoHost("training", Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = DockClearance))
    }
}

@Composable
private fun TodayBlock(ui: TrainingUi, vm: TrainingViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit, onPlan: () -> Unit) {
    val c = Cove.colors
    CoveCard(padding = 20) {
        CoveText("Today's workout", style = CoveType.Meta, color = c.muted)
        if (ui.rows.isEmpty()) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CoveText("Nothing planned for today.", style = CoveType.Heading)
                PrimaryButton("Plan today", onPlan, Modifier.fillMaxWidth())
                CoveText("You can also say it with the mic: “plan today, bench 60 for 8”.", style = CoveType.Meta, color = c.muted)
            }
        } else {
            Column(Modifier.padding(top = 4.dp)) {
                ui.rows.forEachIndexed { i, row ->
                    if (i > 0) Hairline()
                    WorkoutRowView(
                        row, ui.unit, { onOpen(row.exercise.id) }, { vm.useAdvice(row) }, { vm.dismissAdvice(row) },
                        ui.canAsk, ui.opinions[row.exercise.id], { vm.ask(row) },
                    )
                }
            }
            AccentButton("Add exercise", onAdd, Modifier.padding(top = 8.dp).heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun WeightBlock(ui: TrainingUi, onAdd: () -> Unit) {
    val c = Cove.colors
    CoveCard(padding = 20) {
        CoveText("Weight today", style = CoveType.Meta, color = c.muted)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val today = ui.weighedKg
            if (today != null) {
                CoveText(WeightFormat.number(today, ui.unit), " ${ui.unit.label}", style = CoveType.Hero)
            } else if (ui.lastKg != null && ui.lastDay != null) {
                CoveText("Last: ${WeightFormat.withUnit(ui.lastKg, ui.unit)} · ${TrainingStats.dateLabel(ui.lastDay, ui.today)}", style = CoveType.Heading)
            } else {
                CoveText("No weight yet.", style = CoveType.Heading)
            }
            if (ui.spark.size >= 2) Sparkline(
                ui.spark.map { it.second }, "Last 30 days of weight, " + ui.spark.joinToString { WeightFormat.withUnit(it.second, ui.unit) },
            )
            if (today != null) AccentButton("Edit", onAdd, Modifier.heightIn(min = 48.dp))
            else PrimaryButton("Add weight", onAdd, Modifier.fillMaxWidth())
        }
    }
}
