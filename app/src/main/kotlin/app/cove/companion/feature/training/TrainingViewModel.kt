package app.cove.companion.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.engine.WeekPlan
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.engine.WorkoutRow
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Training page shows. */
data class TrainingUi(
    val loaded: Boolean = false,
    val unit: WeightUnit = WeightUnit.Kg,
    val today: LocalDate = LocalDate.now(),
    val rows: List<WorkoutRow> = emptyList(),
    /** Exercises per ISO weekday 1..7. */
    val counts: Map<Int, Int> = emptyMap(),
    /** Today's weigh-in in kg, if any. */
    val weighedKg: Double? = null,
    /** The latest weigh-in and its date, if any. */
    val lastKg: Double? = null,
    val lastDay: LocalDate? = null,
    /** Last 30 days of weigh-ins, oldest first. */
    val spark: List<Pair<LocalDate, Double>> = emptyList(),
    val canAsk: Boolean = false,
    /** One-sentence opinions from "Ask Cove", by exercise id. */
    val opinions: Map<String, String> = emptyMap(),
)

/** The Training page: today's workout, my weight and the weekly plan. */
class TrainingViewModel(private val c: AppContainer) : ViewModel() {
    val actions = WorkoutActions(c)
    private val canAsk = MutableStateFlow(false)
    private val opinions = MutableStateFlow<Map<String, String>>(emptyMap())
    private var latest: WorkoutToday? = null

    val state: StateFlow<TrainingUi> = combine(actions.today, canAsk, opinions) { w, ask, ops ->
        latest = w
        val weights = w.tables.bodyWeights.map { LocalDate.ofEpochDay(it.day) to it.kg }
        val last = weights.lastOrNull()
        TrainingUi(
            true, w.unit, w.today, w.rows, WeekPlan.counts(w.tables.plan),
            weights.firstOrNull { it.first == w.today }?.second, last?.second, last?.first,
            TrainingStats.recent(weights, w.today), ask, ops,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingUi())

    init {
        viewModelScope.launch { canAsk.value = actions.canAsk() }
    }

    fun log(row: WorkoutRow, kg: Double, reps: List<Int>) = viewModelScope.launch { actions.log(row, kg, reps) }
    fun removeLog(row: WorkoutRow) = viewModelScope.launch { actions.removeLog(row) }
    fun changeTodayWeight(row: WorkoutRow, kg: Double) = viewModelScope.launch { actions.changeTodayWeight(row, kg) }
    fun useAdvice(row: WorkoutRow) = viewModelScope.launch { actions.useAdvice(row) }
    fun dismissAdvice(row: WorkoutRow) = viewModelScope.launch { actions.dismissAdvice(row) }

    /** Saves today's weigh-in ([kg] in kilograms). */
    fun saveWeight(kg: Double) = viewModelScope.launch { c.training.saveBodyWeight(state.value.today.toEpochDay(), kg) }

    fun setUnit(unit: WeightUnit) = viewModelScope.launch { c.training.saveUnit(unit.key) }

    /** "Ask Cove": fills [TrainingUi.opinions] for [row] when an answer comes. */
    fun ask(row: WorkoutRow) {
        val w = latest ?: return
        viewModelScope.launch {
            actions.ask(w, row)?.let { text -> opinions.update { it + (row.exercise.id to text) } }
                ?: opinions.update { it + (row.exercise.id to "No second opinion right now. The suggestion above still stands.") }
        }
    }
}
