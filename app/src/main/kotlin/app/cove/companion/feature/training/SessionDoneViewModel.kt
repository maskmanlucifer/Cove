package app.cove.companion.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.Undo
import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.feature.training.engine.Move
import app.cove.companion.feature.training.engine.Schedule
import app.cove.companion.feature.training.engine.Slot
import app.cove.companion.feature.training.engine.TrainingText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import java.time.LocalTime

/** One lift on the Session done card: "62.5 → " before, "62.5 kg" after, and why. */
data class NextTargetRow(val name: String, val before: String, val after: String, val reason: String)

/** A row under the card: "Tomorrow  Rest" or "Thursday  Legs · 7 pm". */
data class UpcomingRow(val label: String, val value: String, val slot: Slot?)

/** Frame 45 data. */
data class SessionDoneUi(
    val loaded: Boolean = false,
    val kept: Boolean = false,
    val header: String = "",
    val rows: List<NextTargetRow> = emptyList(),
    val upcoming: List<UpcomingRow> = emptyList(),
    val slots: List<Slot> = emptyList(),
)

/** Builds the next targets for a finished session and writes the upcoming sessions into Plan. */
class SessionDoneViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    val state: StateFlow<SessionDoneUi> = c.trainingSnapshots().map { build(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDoneUi())

    private fun build(snap: TrainingSnapshot): SessionDoneUi {
        val s = (if (id == "last") snap.finished.lastOrNull() else snap.tables.sessions.firstOrNull { it.id == id }) ?: return SessionDoneUi(loaded = true)
        val mins = ((s.endedAt ?: s.startedAt ?: s.plannedAt) - (s.startedAt ?: s.plannedAt)).div(60_000).toInt().coerceAtLeast(1)
        val ids = s.exerciseIds.split(',').filter { it.isNotBlank() }
        val rows = ids.mapNotNull { snap.exercise(it) }.filter { snap.liftSets(s.id, it.id).isNotEmpty() }.map { e ->
            val before = snap.suggestion(e, s.id)
            val after = snap.suggestion(e)
            val (b, a) = TrainingText.change(snap.spec(e), before.target, after.target, snap.unit)
            NextTargetRow(e.name, b, a, if (after.move == Move.Start) "Starting weight." else after.reason)
        }
        val date = snap.sessionDate(s)
        val slots = Schedule.upcoming(date, snap.weekdays, snap.dayTypes, snap.doneSessions, 3).filter { it.date.isAfter(date) }
        val at = TrainingText.timeLabel(snap.startMinutes)
        val tomorrow = date.plusDays(1)
        val upcoming = buildList {
            val first = slots.firstOrNull()
            if (first?.date == tomorrow) add(UpcomingRow("Tomorrow", "${first.dayType} · $at", first)) else add(UpcomingRow("Tomorrow", "Rest", null))
            slots.firstOrNull { it.date.isAfter(tomorrow) }?.let { add(UpcomingRow(Schedule.dayLabel(date, it.date), "${it.dayType} · $at", it)) }
        }
        return SessionDoneUi(true, s.endedAt != null, "${s.dayType} · $mins min", rows, upcoming, slots)
    }

    /** Puts the upcoming sessions into the Plan schedule (one event per day, replaced if saved again); Undo removes them. */
    suspend fun savePlan() {
        val snap = c.trainingSnapshots().first()
        val ui = state.value
        val saved = ui.slots.map { slot ->
            val start = LocalDateTime.of(slot.date, LocalTime.of(snap.startMinutes / 60, snap.startMinutes % 60)).toEpochMillis()
            val minutes = snap.estimateMinutes(snap.dayExercises(slot.dayType).map { it.id }).coerceAtLeast(30)
            EventEntity("training-${slot.date}", slot.dayType, start, start + minutes * 60_000L, notes = "Training", remindBeforeMin = 30)
        }
        val before = saved.map { e -> c.database.events().get(e.id) }
        saved.forEach { c.plan.saveEvent(it) }
        if (saved.isNotEmpty()) {
            Undo.center.post("training", "Added to Plan") {
                saved.forEachIndexed { i, e ->
                    val old = before[i]
                    if (old == null || old.deletedAt != null) c.plan.saveEvent(e.copy(deletedAt = c.clock.now())) else c.plan.saveEvent(old)
                }
            }
        }
    }
}
