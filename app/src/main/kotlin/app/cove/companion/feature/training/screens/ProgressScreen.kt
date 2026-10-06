package app.cove.companion.feature.training.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.Segmented
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.ProgressModel
import app.cove.companion.feature.training.engine.ChartMath
import app.cove.companion.feature.training.engine.ProgressRange
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.ui.BackChevron
import app.cove.companion.feature.training.ui.BarChart
import app.cove.companion.feature.training.ui.LineChart
import app.cove.companion.feature.training.ui.TrainingType
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** Frame 48: the headline, body weight, sessions against the goal, and the top sets table. */
@Composable
fun ProgressScreen(nav: Nav) {
    val vm = appViewModel { LiftViewModel(it) }
    val snap by vm.snap.collectAsState()
    var range by remember { mutableStateOf(ProgressRange.Month) }
    val c = Cove.colors
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                BackChevron(nav.back)
                Segmented(ProgressRange.entries.map { it.label }, range.ordinal, { range = ProgressRange.entries[it] }, height = 36.dp)
                Box(Modifier.width(44.dp))
            }
            val s = snap
            if (s != null) {
                val p = ProgressModel.build(s, range)
                val unit = s.unit
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CoveText("Progress", style = CoveType.Title)
                        CoveText(p.headline, style = TrainingType.Sub, color = c.muted)
                    }
                    if (p.bodyPoints.isNotEmpty()) Card(Modifier.pressable({ nav.go(Routes.TrainingWeight) }, onClickLabel = "Open body weight", role = Role.Button)) {
                        val values = p.bodyPoints.map { unit.fromKg(it.second) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            CoveText("Body weight", style = CoveType.Meta, color = c.muted)
                            CoveText(WeightFormat.number(p.bodyPoints.last().second, unit), " ${unit.label}", style = CoveType.BodyMedium)
                        }
                        LineChart(
                            values, ChartMath.axis(values, if (unit.key == "kg") 0.5 else 1.0), chartLabels(p.bodyPoints.map { it.first }, s.today),
                            "Body weight, " + p.bodyPoints.joinToString { WeightFormat.withUnit(it.second, unit) },
                        )
                    } else Card(Modifier.pressable({ nav.go(Routes.TrainingWeight) }, role = Role.Button)) {
                        CoveText("Body weight", style = CoveType.Meta, color = c.muted)
                        CoveText("No weigh-ins in this range. Tap to add today's.", style = TrainingType.Sub, color = c.muted)
                    }
                    Card {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            CoveText(p.barsTitle, style = CoveType.Meta, color = c.muted)
                            CoveText(p.goalText, style = CoveType.Meta, color = c.muted)
                        }
                        BarChart(p.bars, p.goal, p.barsTitle + ": " + p.bars.joinToString { "${it.label} ${it.value}" } + ", " + p.goalText)
                    }
                    if (p.topSets.isNotEmpty()) Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(horizontal = 20.dp, vertical = 4.dp)) {
                        CoveText("Top sets · ${unit.label}", Modifier.padding(top = 14.dp), style = CoveType.Meta, color = c.muted)
                        p.topSets.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.well))
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable({ nav.go(Routes.trainingLift(r.exerciseId)) }, role = Role.Button),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CoveText(r.name, Modifier.weight(1f), style = TrainingType.Row, maxLines = 2)
                                Box(Modifier.width(96.dp).height(4.dp).background(c.ink.copy(alpha = 0.08f), CoveShapes.Pill)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(r.fraction).background(c.ink, CoveShapes.Pill))
                                }
                                Box(Modifier.width(84.dp), contentAlignment = Alignment.CenterEnd) {
                                    CoveText(if (r.start != null) r.start + "→" else "", r.now, style = TrainingType.Sub.copy(color = c.ink), color = c.tail, secondaryColor = c.ink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().background(Cove.colors.card, CoveShapes.Card).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}
