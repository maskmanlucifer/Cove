package app.cove.companion.feature.training.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cove.companion.core.OneShot
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.BalancedText
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.feature.training.SessionDoneViewModel
import app.cove.companion.feature.training.ui.LabelRow
import app.cove.companion.feature.training.ui.PrimaryButton
import app.cove.companion.feature.training.ui.TrainingType
import app.cove.companion.navigation.Nav

/** Frame 45: what the session means for next time, what is coming up, and Save plan. */
@Composable
fun SessionDoneScreen(id: String, nav: Nav) {
    val vm = appViewModel(key = "done-$id") { SessionDoneViewModel(it, id) }
    val ui by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val guard = remember { OneShot() }
    val c = Cove.colors
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoveText(ui.header, style = CoveType.Meta, color = c.muted)
                    BalancedText("Done.", " Here’s next time.", CoveType.Title)
                }
                if (ui.rows.isEmpty() && ui.loaded) {
                    CoveText("No sets were logged, so there is nothing to plan from yet.", style = TrainingType.Sub, color = c.muted)
                }
                if (ui.rows.isNotEmpty()) Column(Modifier.fillMaxWidth().background(c.card, CoveShapes.Card).padding(horizontal = 20.dp)) {
                    ui.rows.forEachIndexed { i, r ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.well))
                        Column(Modifier.padding(vertical = 16.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                                CoveText(r.name, Modifier.weight(1f), style = CoveType.Body)
                                CoveText(r.before, r.after, style = CoveType.Body, color = c.tail, secondaryColor = c.ink)
                            }
                            CoveText(r.reason, style = CoveType.Meta, color = c.muted)
                        }
                    }
                }
                Column {
                    ui.upcoming.forEachIndexed { i, u -> LabelRow(u.label, u.value, first = i == 0, chevron = u.slot != null) }
                }
            }
            PrimaryButton("Save plan", { guard.launch(scope) { vm.savePlan(); nav.back(); true } }, Modifier.fillMaxWidth(), enabled = !guard.busy && ui.loaded)
        }
    }
}
