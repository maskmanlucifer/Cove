package app.cove.companion.feature.training

import app.cove.companion.AppContainer
import app.cove.companion.feature.me.TrainingSummaries
import app.cove.companion.feature.me.TrainingSummary
import app.cove.companion.feature.training.engine.Schedule
import app.cove.companion.feature.training.engine.TrainingText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** The Me > Body > Training row: "Push · 7 pm" for today's session, "Legs · Thursday" for a later one. */
class LiveTrainingSummary(private val c: AppContainer) : TrainingSummary {
    private val _value = MutableStateFlow(TrainingSummary.NOT_SET_UP)
    override val value: StateFlow<String> = _value

    private fun start() {
        c.trainingSnapshots().map { text(it) }.catch { }.onEach { _value.value = it }.launchIn(c.appScope)
    }

    companion object {
        /** Makes Me read the summary of the next session; call once at start-up. */
        fun install(c: AppContainer) {
            val live = LiveTrainingSummary(c)
            live.start()
            TrainingSummaries.current = live
        }

        /** The row text for [snap]. */
        fun text(snap: TrainingSnapshot): String {
            if (!snap.hasPlan) return TrainingSummary.NOT_SET_UP
            snap.active?.let { return "${it.dayType} · in progress" }
            val slot = snap.upcoming(1).firstOrNull() ?: return TrainingSummary.NOT_SET_UP
            val label = Schedule.dayLabel(snap.today, slot.date)
            return if (label == "Today") "${slot.dayType} · ${TrainingText.timeLabel(snap.startMinutes)}" else "${slot.dayType} · $label"
        }
    }
}
