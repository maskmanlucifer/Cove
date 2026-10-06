package app.cove.companion.feature.training.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.Segmented
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.engine.ChartMath
import app.cove.companion.feature.training.engine.ChartRange
import app.cove.companion.feature.training.engine.ExerciseProgress
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.ui.BackChevron
import app.cove.companion.feature.training.ui.LineChart
import app.cove.companion.feature.training.ui.chartLabels
import app.cove.companion.navigation.Nav

/** Weight progress in one calm screen: the body weight line and every exercise's change since it was first logged. */
@Composable
fun ProgressScreen(nav: Nav) {
    val vm = appViewModel { ProgressViewModel(it) }
    val ui by vm.state.collectAsState()
    var range by remember { mutableStateOf(ChartRange.ThreeMonths) }
    var open by remember { mutableStateOf<String?>(null) }
    val c = Cove.colors
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { BackChevron(nav.back) }
            if (ui.loaded) Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = DockClearance + 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CoveText("Progress", style = CoveType.Title)
                Segmented(ChartRange.entries.map { it.label }, range.ordinal, { range = ChartRange.entries[it] }, height = 44.dp)
                BodyCard(ui, range)
                CoveText("Exercises", style = CoveType.Meta, color = c.muted)
                if (ui.exercises.isEmpty()) {
                    CoveText("Nothing logged yet. Tap an exercise on the Training page when you have done it.", style = CoveType.Meta, color = c.muted)
                } else Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(horizontal = 20.dp)) {
                    ui.exercises.forEachIndexed { i, p ->
                        if (i > 0) Hairline()
                        ExerciseLine(p, ui, expanded = open == p.name) { open = if (open == p.name) null else p.name }
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyCard(ui: ProgressUi, range: ChartRange) {
    val c = Cove.colors
    val points = range.filter(ui.body, ui.today)
    Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CoveText("Body weight", style = CoveType.Meta, color = c.muted)
        if (points.isEmpty()) {
            CoveText("No weigh-ins in this range. Add one on the Training page.", style = CoveType.Body, color = c.muted)
        } else {
            CoveText(TrainingStats.bodyChange(points, ui.today, ui.unit), style = CoveType.Heading)
            val values = points.map { ui.unit.fromKg(it.second) }
            LineChart(
                values, ChartMath.axis(values, if (ui.unit == WeightUnit.Kg) 0.5 else 1.0), chartLabels(points.map { it.first }, ui.today),
                "Body weight, " + points.joinToString { "${TrainingStats.dateLabel(it.first, ui.today)} ${WeightFormat.withUnit(it.second, ui.unit)}" },
            )
        }
    }
}

@Composable
private fun ExerciseLine(p: ExerciseProgress, ui: ProgressUi, expanded: Boolean, onToggle: () -> Unit) {
    val c = Cove.colors
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable(onToggle, onClickLabel = if (expanded) "Hide chart" else "Show chart", role = Role.Button)
                .padding(vertical = 10.dp).semantics { stateDescription = if (expanded) "Chart shown" else "Chart hidden" },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CoveText(p.name, style = CoveType.Body)
            CoveText(
                if (p.latestKg <= 0.0) "Bodyweight" else "${WeightFormat.withUnit(p.latestKg, ui.unit)}, ${TrainingText.change(p.deltaKg, ui.unit)}",
                style = CoveType.Meta, color = c.muted,
            )
        }
        if (expanded) {
            Column(Modifier.padding(bottom = 12.dp)) {
                if (p.points.size < 2) CoveText("One session so far.", style = CoveType.Meta, color = c.muted)
                else {
                    val values = p.points.map { ui.unit.fromKg(it.second) }
                    LineChart(
                        values, ChartMath.axis(values, if (ui.unit == WeightUnit.Kg) 2.5 else 5.0), chartLabels(p.points.map { it.first }, ui.today),
                        "${p.name}, " + p.points.joinToString { "${TrainingStats.dateLabel(it.first, ui.today)} ${WeightFormat.withUnit(it.second, ui.unit)}" },
                    )
                }
            }
        }
    }
}
