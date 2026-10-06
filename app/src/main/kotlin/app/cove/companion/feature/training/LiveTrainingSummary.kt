package app.cove.companion.feature.training

import app.cove.companion.AppContainer
import app.cove.companion.core.toLocalDate
import app.cove.companion.feature.me.TrainingSummaries
import app.cove.companion.feature.me.TrainingSummary
import app.cove.companion.feature.training.engine.TodayWorkout
import app.cove.companion.feature.training.engine.WeekPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** The Me > Body > Training row: "Today: Push · 3 exercises", or "Not planned". */
class LiveTrainingSummary(private val c: AppContainer) : TrainingSummary {
    private val _value = MutableStateFlow(TodayWorkout.summary(emptyList()))
    override val value: StateFlow<String> = _value

    private fun start() {
        c.training.tables
            .map { t -> TodayWorkout.summary(WeekPlan.forDay(c.clock.now().toLocalDate(), t.plan, t.overrides)) }
            .catch { }.onEach { _value.value = it }.launchIn(c.appScope)
    }

    companion object {
        /** Makes Me read today's plan; call once at start-up. */
        fun install(c: AppContainer) {
            val live = LiveTrainingSummary(c)
            live.start()
            TrainingSummaries.current = live
        }
    }
}
