package app.cove.companion.feature.training

import androidx.compose.runtime.getValue
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.cove.companion.core.OneShot
import kotlinx.coroutines.launch
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveCard
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.UndoHost
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.ui.LabelRow
import app.cove.companion.feature.training.ui.TrainingType
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** Training home (frame 41), or the first-run setup while no programme exists. */
@Composable
fun TrainingScreen(nav: Nav) {
    val vm = appViewModel { TrainingHomeViewModel(it) }
    val ui by vm.state.collectAsState()
    CoveScreen {
        val home = ui.home
        when {
            !ui.loaded -> Unit
            home == null -> SetupContent()
            else -> HomeContent(ui.snap!!.daysPerWeek, home, vm, nav)
        }
        UndoHost("training", Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun HomeContent(daysPerWeek: Int, home: HomeState, vm: TrainingHomeViewModel, nav: Nav) {
    val scope = rememberCoroutineScope()
    val startGuard = remember { OneShot() }
    val c = Cove.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = DockClearance + 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CoveText("Training", style = CoveType.Title)
            CoveText(
                "${TrainingText.countWord(daysPerWeek).replaceFirstChar { it.uppercase() }} days a week. Cove picks the weight; you lift it.",
                style = TrainingType.Sub, color = c.muted,
            )
        }
        CoveCard(padding = 24) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CoveText(home.whenLabel, style = CoveType.Meta, color = c.muted)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CoveText(home.dayType, " day", style = CoveType.Hero)
                    CoveText(home.meta, style = TrainingType.Sub, color = c.muted)
                }
                Column {
                    home.lifts.forEachIndexed { i, l ->
                        if (i > 0) Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(c.well))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable({ nav.go(Routes.trainingLift(l.id)) }, role = Role.Button),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CoveText(l.name, Modifier.weight(1f), style = CoveType.Body)
                            CoveText(l.detail, style = TrainingType.Sub, color = c.muted)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillButton(
                        if (home.running) "Resume" else "Start",
                        { startGuard.launch(scope) { vm.start { nav.go(Routes.TrainingSession) }; false } },
                        height = 48.dp, horizontalPadding = 24.dp, textStyle = TrainingType.Button,
                    )
                    home.accessory?.let { name ->
                        Box(
                            Modifier.heightIn(min = 48.dp).pressable({ scope.launch { vm.addAccessory() } }, role = Role.Button).padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { CoveText("+ $name", style = TrainingType.Sub, color = c.muted) }
                    }
                }
            }
        }
        Column {
            LabelRow("Next", home.nextLine, first = true)
            LabelRow("Progress", home.progressLine, first = false, chevron = true, onClick = { nav.go(Routes.TrainingProgress) })
            LabelRow("Plan", "Edit", first = false, chevron = true, onClick = { nav.go(Routes.TrainingPlan) })
        }
    }
}
