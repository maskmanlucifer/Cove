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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.OneShot
import app.cove.companion.core.appViewModel
import app.cove.companion.core.newId
import app.cove.companion.data.local.entity.TrainingSettingsEntity
import app.cove.companion.data.local.entity.WorkoutPlanEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveCard
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.Segmented
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.engine.Schedule
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.ui.NumberEntrySheet
import app.cove.companion.feature.training.ui.PrimaryButton
import app.cove.companion.feature.training.ui.TrainingType
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Choices of the first-run setup; weights are kilograms. */
data class SetupState(
    val weekdays: Set<DayOfWeek> = Schedule.defaultWeekdays(3),
    val unit: WeightUnit = WeightUnit.Kg,
    val starts: Map<String, Double> = DefaultProgramme.all.filter { it.main }.associate { it.name to DefaultProgramme.startIn(it.startKg, WeightUnit.Kg) },
)

/** Builds the default programme from the setup choices. */
class SetupViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state

    fun setDays(n: Int) = _state.update { it.copy(weekdays = Schedule.defaultWeekdays(n)) }

    fun toggleDay(d: DayOfWeek) = _state.update {
        val next = if (d in it.weekdays) it.weekdays - d else it.weekdays + d
        if (next.size in 2..4) it.copy(weekdays = next) else it
    }

    fun setUnit(u: WeightUnit) = _state.update { it.copy(unit = u) }

    fun setStart(name: String, kg: Double) = _state.update { it.copy(starts = it.starts + (name to kg.coerceIn(0.0, 500.0))) }

    /** Steps [name]'s starting weight by one stepper step in the chosen unit. */
    fun step(name: String, dir: Int) {
        val s = _state.value
        val v = s.unit.fromKg(s.starts[name] ?: 0.0) + dir * s.unit.stepperStep
        setStart(name, s.unit.toKg(maxOf(0.0, v)))
    }

    suspend fun create() {
        val s = _state.value
        DefaultProgramme.create(c.training, s.unit, s.weekdays, s.starts)
    }
}

/** First-run setup: days a week, unit, starting weights for the main lifts, then "Create my plan". */
@Composable
fun SetupContent() {
    val vm = appViewModel { SetupViewModel(it) }
    val s by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val guard = remember { OneShot() }
    var typing by remember { mutableStateOf<String?>(null) }
    val c = Cove.colors
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = DockClearance + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CoveText("Training", style = CoveType.Title)
                CoveText("A simple plan to begin with. Cove picks the weight; you lift it. Change anything later.", style = TrainingType.Sub, color = c.muted)
            }
            CoveCard(padding = 20) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CoveText("Days a week", style = CoveType.Meta, color = c.muted)
                    Segmented(listOf("2", "3", "4"), (s.weekdays.size - 2).coerceIn(0, 2), { vm.setDays(it + 2) }, Modifier.fillMaxWidth(), fillWidth = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { d -> DayChip(d, d in s.weekdays, { vm.toggleDay(d) }, Modifier.weight(1f)) }
                    }
                    CoveText("Units", style = CoveType.Meta, color = c.muted)
                    Segmented(listOf("kg", "lb"), if (s.unit == WeightUnit.Kg) 0 else 1, { vm.setUnit(if (it == 0) WeightUnit.Kg else WeightUnit.Lb) }, Modifier.fillMaxWidth(), fillWidth = true)
                }
            }
            CoveCard(padding = 20) {
                Column {
                    CoveText("Starting weights", style = CoveType.Meta, color = c.muted)
                    DefaultProgramme.all.filter { it.main }.forEach { l ->
                        val kg = s.starts[l.name] ?: 0.0
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoveText(l.name, Modifier.weight(1f), style = TrainingType.Row)
                            MiniStep("−", "Lower ${l.name}") { vm.step(l.name, -1) }
                            Box(Modifier.heightIn(min = 48.dp).pressable({ typing = l.name }, onClickLabel = "Type the weight", role = Role.Button).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                CoveText(WeightFormat.number(kg, s.unit) + " " + s.unit.label, style = TrainingType.Row)
                            }
                            MiniStep("+", "Raise ${l.name}") { vm.step(l.name, 1) }
                        }
                    }
                }
            }
            CoveText("Push, Pull and Legs, three or four lifts each. Weights for the other lifts start light and Cove adjusts them.", style = CoveType.Meta, color = c.muted)
            PrimaryButton("Create my plan", { guard.launch(scope) { vm.create(); true } }, Modifier.fillMaxWidth(), enabled = !guard.busy)
        }
        val name = typing
        NumberEntrySheet(
            name != null, name?.let { "$it, ${s.unit.label}" }.orEmpty(), name?.let { WeightFormat.number(s.starts[it] ?: 0.0, s.unit) }.orEmpty(), s.unit.label, true,
            { typing = null }, { v -> name?.let { vm.setStart(it, s.unit.toKg(v)) }; typing = null },
        )
    }
}

@Composable
internal fun DayChip(d: DayOfWeek, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = Cove.colors
    val name = d.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    Box(
        modifier.heightIn(min = 48.dp).background(if (on) c.ink else c.well, CoveShapes.Pill).pressable(onClick, role = Role.Checkbox)
            .semantics { selected = on; contentDescription = name },
        contentAlignment = Alignment.Center,
    ) { CoveText(d.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = CoveType.Meta, color = if (on) c.onInk else c.ink) }
}

/** 40dp round − / + button with a 48dp tap area, for rows that hold a number. */
@Composable
fun MiniStep(glyph: String, label: String, onClick: () -> Unit) {
    val c = Cove.colors
    Box(Modifier.size(48.dp).pressable(onClick, role = Role.Button).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp).background(c.well, CoveShapes.Circle), contentAlignment = Alignment.Center) { CoveText(glyph, style = CoveType.Section) }
    }
}
