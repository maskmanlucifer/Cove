package app.cove.companion.feature.training.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveCard
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.Segmented
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.feature.training.trainingSnapshots
import app.cove.companion.feature.training.TrainingSnapshot
import app.cove.companion.feature.training.engine.ChartMath
import app.cove.companion.feature.training.engine.ChartRange
import app.cove.companion.feature.training.engine.LoggedSet
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.ui.BackChevron
import app.cove.companion.feature.training.ui.LabelRow
import app.cove.companion.feature.training.ui.LineChart
import app.cove.companion.feature.training.ui.TrainingTopBar
import app.cove.companion.feature.training.ui.TrainingType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import app.cove.companion.navigation.Nav

/** Holds the lift page's snapshot. */
class LiftViewModel(c: AppContainer) : ViewModel() {
    val snap: StateFlow<TrainingSnapshot?> = c.trainingSnapshots().map<TrainingSnapshot, TrainingSnapshot?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Frame 46: one lift's progress, best set, a chart of the top set weight and its sessions. */
@Composable
fun LiftHistoryScreen(exerciseId: String, nav: Nav) {
    val vm = appViewModel { LiftViewModel(it) }
    val snap by vm.snap.collectAsState()
    var range by remember { mutableStateOf(ChartRange.Month) }
    val c = Cove.colors
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            TrainingTopBar("Training", { BackChevron(nav.back) })
            val s = snap
            val e = s?.exercise(exerciseId)
            if (s != null && e != null) {
                val spec = s.spec(e)
                val all = s.points(exerciseId)
                val shown = range.filter(all, s.today).ifEmpty { all.takeLast(1) }
                val unit = s.unit
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CoveText(e.name, style = CoveType.Title)
                        CoveText(TrainingStats.liftDelta(all, unit, spec.isBodyweight), style = TrainingType.Sub, color = c.muted)
                    }
                    if (all.isEmpty()) {
                        CoveText("No sessions yet. Start one when you are ready.", style = TrainingType.Sub, color = c.muted)
                    } else {
                        TrainingStats.best(all)?.let { best ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                CoveText("Best set", style = CoveType.Meta, color = c.muted)
                                if (spec.isBodyweight) CoveText(best.reps.toString(), " reps", style = CoveType.Hero)
                                else CoveText(WeightFormat.number(best.weightKg, unit), " ${unit.label} × ${best.reps}", style = CoveType.Hero)
                            }
                        }
                        CoveCard(padding = 20) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    CoveText(if (spec.isBodyweight) "Top set reps" else "Top set weight", style = CoveType.Meta, color = c.muted)
                                    Segmented(ChartRange.entries.map { it.label }, range.ordinal, { range = ChartRange.entries[it] }, height = 32.dp)
                                }
                                val values = shown.map { if (spec.isBodyweight) it.topReps.toDouble() else unit.fromKg(it.topKg) }
                                val axis = ChartMath.axis(values, if (spec.isBodyweight) 1.0 else unit.stepperStep)
                                val summary = "${e.name}, top set, " + shown.joinToString { if (spec.isBodyweight) "${it.topReps} reps" else WeightFormat.withUnit(it.topKg, unit) }
                                LineChart(values, axis, chartLabels(shown.map { it.date }, s.today), summary)
                            }
                        }
                        Column {
                            all.asReversed().take(12).forEachIndexed { i, p ->
                                LabelRow(dayText(s.today, p.date), setsText(p.sets.map { LoggedSet(it.weightKg, it.reps) }, unit, spec.isBodyweight), first = i == 0)
                            }
                        }
                    }
                }
            }
        }
    }
}
