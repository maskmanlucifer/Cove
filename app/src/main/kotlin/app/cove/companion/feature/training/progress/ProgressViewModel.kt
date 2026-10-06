package app.cove.companion.feature.training.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.toLocalDate
import app.cove.companion.feature.training.engine.ExerciseProgress
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.engine.WeightUnit
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the progress screen draws, from real data only. */
data class ProgressUi(
    val loaded: Boolean = false,
    val unit: WeightUnit = WeightUnit.Kg,
    val today: LocalDate = LocalDate.now(),
    /** All weigh-ins, oldest first. */
    val body: List<Pair<LocalDate, Double>> = emptyList(),
    val exercises: List<ExerciseProgress> = emptyList(),
)

/** Reads weigh-ins and exercise logs for the progress screen. */
class ProgressViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<ProgressUi> = c.training.tables.map { t ->
        ProgressUi(
            true, WeightUnit.of(t.settings?.unit), c.clock.now().toLocalDate(),
            t.bodyWeights.map { LocalDate.ofEpochDay(it.day) to it.kg }, TrainingStats.exercises(t.logs),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUi())
}
