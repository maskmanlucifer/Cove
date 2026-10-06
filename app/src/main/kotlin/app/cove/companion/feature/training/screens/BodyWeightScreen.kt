package app.cove.companion.feature.training.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.cove.companion.container
import app.cove.companion.core.OneShot
import app.cove.companion.core.appViewModel
import app.cove.companion.core.toLocalDate
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.feature.money.AmountKeypad
import app.cove.companion.feature.training.engine.BodyWeightInput
import app.cove.companion.feature.training.engine.ChartMath
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.trainingSnapshots
import app.cove.companion.feature.training.ui.CloseCircle
import app.cove.companion.feature.training.ui.LineChart
import app.cove.companion.feature.training.ui.PrimaryButton
import app.cove.companion.feature.training.ui.TrainingTopBar
import app.cove.companion.feature.training.ui.TrainingType
import app.cove.companion.navigation.Nav
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Frame 47: today's weigh-in on a keypad, the trend sentence and a small chart. */
@Composable
fun BodyWeightScreen(nav: Nav) {
    val vm = appViewModel { LiftViewModel(it) }
    val snap by vm.snap.collectAsState()
    val s = snap
    val scope = rememberCoroutineScope()
    val guard = remember { OneShot() }
    var text by remember { mutableStateOf<String?>(null) }
    val container = androidx.compose.ui.platform.LocalContext.current.container
    val c = Cove.colors
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            TrainingTopBar("Morning · before breakfast", { CloseCircle(nav.back) })
            if (s != null) {
                val unit = s.unit
                val today = s.today
                val todayKey = today.toEpochDay()
                val entries = s.tables.bodyWeights.filter { it.deletedAt == null }
                val initial = (entries.firstOrNull { it.day == todayKey } ?: entries.lastOrNull())?.let { WeightFormat.number(it.kg, unit) }.orEmpty()
                val shownText = text ?: initial
                val value = BodyWeightInput.value(shownText, unit)
                val history = entries.filter { it.day != todayKey }.map { LocalDate.ofEpochDay(it.day) to it.kg } + (value?.let { listOf(today to unit.toKg(it)) } ?: emptyList())
                val ordered = history.sortedBy { it.first }.takeLast(8)
                val values = ordered.map { unit.fromKg(it.second) }
                Column(Modifier.weight(1f).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CoveText(shownText.ifEmpty { "0" }, " ${unit.label}", style = TrainingType.Big, modifier = Modifier.semantics { contentDescription = "Weight ${shownText.ifEmpty { "none yet" }} ${unit.label}" })
                        CoveText(TrainingStats.bodyDelta(history, today, unit), style = CoveType.Body, color = c.muted, textAlign = TextAlign.Center)
                    }
                    if (values.isNotEmpty()) Box(Modifier.padding(horizontal = 4.dp)) {
                        LineChart(
                            values, ChartMath.axis(values, if (unit.key == "kg") 0.5 else 1.0), chartLabels(ordered.map { it.first }, today),
                            "Body weight, " + ordered.joinToString { WeightFormat.withUnit(it.second, unit) },
                        )
                    }
                    Box(Modifier.weight(1f))
                    PrimaryButton(
                        if (value != null) "Save ${WeightFormat.trim(value)} ${unit.label}" else "Save",
                        { value?.let { v -> guard.launch(scope) { container.training.saveBodyWeight(todayKey, unit.toKg(v)); nav.back(); true } } },
                        Modifier.fillMaxWidth(), enabled = value != null && !guard.busy,
                    )
                    AmountKeypad(
                        { k -> text = BodyWeightInput.push(if (text == null) "" else text!!, k) },
                        { text = BodyWeightInput.back(text ?: initial) },
                        { text = "" },
                        Modifier,
                    )
                }
            }
        }
    }
}
