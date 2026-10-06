package app.cove.companion.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.Undo
import app.cove.companion.core.newId
import app.cove.companion.data.local.entity.WorkoutSessionEntity
import app.cove.companion.data.repo.idList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the Training home shows: nothing while loading, setup without a programme, otherwise [home]. */
data class TrainingUi(val loaded: Boolean = false, val snap: TrainingSnapshot? = null, val home: HomeState? = null)

/** Home of Training: today's session card, starting it, and adding an accessory. */
class TrainingHomeViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<TrainingUi> = c.trainingSnapshots()
        .map { TrainingUi(true, it, if (it.hasPlan) HomeModel.build(it) else null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingUi())

    /** Starts (or resumes) the session; calls [onReady] once it exists. */
    suspend fun start(onReady: () -> Unit) {
        val ui = state.value
        val home = ui.home ?: return
        val snap = ui.snap ?: return
        c.training.begin(home.dayType, home.exerciseIds, snap.plannedToday)
        onReady()
    }

    /** "+ Dips": adds the accessory to today's planned session, with Undo. */
    suspend fun addAccessory() {
        val ui = state.value
        val home = ui.home ?: return
        val snap = ui.snap ?: return
        val name = home.accessory ?: return
        val lift = snap.exercises.firstOrNull { it.name.equals(name, true) } ?: run {
            val d = DefaultProgramme.find(name) ?: return
            c.training.saveExercise(
                DefaultProgramme.exerciseRows(listOf(d), snap.unit, { newId() }, snap.exercises.size).first(),
            )
        }
        val before = snap.plannedToday
        val ids = home.exerciseIds + lift.id
        val saved = c.training.saveSession(
            before?.copy(exerciseIds = ids.joinToString(","))
                ?: WorkoutSessionEntity(newId(), home.dayType, c.clock.now(), exerciseIds = ids.joinToString(",")),
        )
        Undo.center.post("training", "Added $name") {
            if (before == null) c.training.saveSession(saved.copy(deletedAt = c.clock.now()))
            else c.training.saveSession(saved.copy(exerciseIds = before.exerciseIds))
        }
    }

    /** Ids of the lifts of [s] without the skipped ones. */
    fun lifts(s: WorkoutSessionEntity): List<String> = s.exerciseIds.idList()
}
