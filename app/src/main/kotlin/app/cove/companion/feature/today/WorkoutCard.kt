package app.cove.companion.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.AccentButton
import app.cove.companion.design.components.CheckCircle
import app.cove.companion.design.components.CoveCard
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.WorkoutActions
import app.cove.companion.feature.training.WorkoutToday
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WorkoutRow
import app.cove.companion.feature.training.ui.spokenRow
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Feeds [WorkoutCard]: today's planned exercises, nothing while none are planned. */
class WorkoutCardViewModel(c: AppContainer) : ViewModel() {
    private val actions = WorkoutActions(c)
    val state: StateFlow<WorkoutToday?> = actions.today.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Use 62.5 kg": confirms the suggestion for today. */
    fun use(row: WorkoutRow) = viewModelScope.launch { actions.useAdvice(row) }
}

/**
 * Today's workout: up to three exercises with weight x sets x reps, the first weight suggestion with its Use button,
 * and a tap into Training. Draws nothing while nothing is planned.
 */
@Composable
fun WorkoutCard(nav: Nav, modifier: Modifier = Modifier) {
    val vm = appViewModel { WorkoutCardViewModel(it) }
    val w by vm.state.collectAsState()
    val state = w ?: return
    if (state.rows.isEmpty()) return
    val c = Cove.colors
    val shown = state.rows.take(MAX_ROWS)
    val suggestion = state.rows.firstOrNull { it.advice?.actionable == true }
    CoveCard(modifier, padding = 20) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable({ nav.go(Routes.Training) }, onClickLabel = "Open Training", role = Role.Button),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CoveText("Today's workout", style = CoveType.Meta, color = c.muted)
                CoveText("Open", style = CoveType.Meta, color = c.accent)
            }
            shown.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spokenRow(row, state.unit) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CheckCircle(row.done != null, onToggle = null, size = 22)
                    Column(Modifier.weight(1f)) {
                        CoveText(row.exercise.name, style = CoveType.Body)
                        CoveText(TrainingText.detail(row.exercise.weightKg, row.exercise.sets, row.exercise.reps, state.unit), style = CoveType.Meta, color = c.muted)
                    }
                }
            }
            if (state.rows.size > MAX_ROWS) CoveText("+ ${state.rows.size - MAX_ROWS} more", style = CoveType.Meta, color = c.muted)
            suggestion?.advice?.let { a ->
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoveText("${suggestion.exercise.name}: ${a.reason}", style = CoveType.Meta, color = c.muted)
                    AccentButton("Use ${WeightFormat.withUnit(a.weightKg, state.unit)}", { vm.use(suggestion) }, Modifier.heightIn(min = 48.dp))
                }
            }
        }
    }
}

private const val MAX_ROWS = 3
