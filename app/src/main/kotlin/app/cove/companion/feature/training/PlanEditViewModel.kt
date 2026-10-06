package app.cove.companion.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.Undo
import app.cove.companion.core.newId
import app.cove.companion.data.local.entity.ExerciseEntity
import app.cove.companion.data.local.entity.PlanDayEntity
import app.cove.companion.data.repo.idList
import app.cove.companion.feature.training.engine.Schedule
import app.cove.companion.feature.training.engine.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek

/** Edits the programme: reorder, add, remove and tune lifts, plus unit, rest time and weekdays. */
class PlanEditViewModel(private val c: AppContainer) : ViewModel() {
    val snap: StateFlow<TrainingSnapshot?> = c.trainingSnapshots().map<TrainingSnapshot, TrainingSnapshot?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun day(id: String) = c.training.planDays().firstOrNull { it.id == id }

    private suspend fun setIds(d: PlanDayEntity, ids: List<String>) = c.training.savePlanDay(d.copy(exerciseIds = ids.joinToString(",")))

    suspend fun move(dayId: String, exerciseId: String, dir: Int) {
        val d = day(dayId) ?: return
        val ids = d.exerciseIds.idList().toMutableList()
        val i = ids.indexOf(exerciseId)
        val j = i + dir
        if (i < 0 || j !in ids.indices) return
        ids[i] = ids[j].also { ids[j] = ids[i] }
        setIds(d, ids)
    }

    suspend fun remove(dayId: String, exerciseId: String, name: String) {
        val d = day(dayId) ?: return
        val before = d.exerciseIds
        setIds(d, d.exerciseIds.idList() - exerciseId)
        Undo.center.post("training", "Removed $name") { day(dayId)?.let { c.training.savePlanDay(it.copy(exerciseIds = before)) } }
    }

    /** Names offered by the add picker for [dayId]: library lifts not in the day, then built-in ones not yet in the library. */
    fun candidates(snap: TrainingSnapshot, dayId: String): List<String> {
        val inDay = snap.tables.planDays.firstOrNull { it.id == dayId }?.exerciseIds?.idList().orEmpty().mapNotNull { snap.exercise(it)?.name?.lowercase() }.toSet()
        val library = snap.exercises.map { it.name }
        val builtin = (DefaultProgramme.all + DefaultProgramme.extras).map { it.name }.filter { n -> library.none { it.equals(n, true) } }
        return (library + builtin).filter { it.lowercase() !in inDay }.distinct()
    }

    suspend fun add(dayId: String, name: String) {
        val snap = c.trainingSnapshots().first()
        val d = day(dayId) ?: return
        val e = snap.exercises.firstOrNull { it.name.equals(name, true) } ?: c.training.saveExercise(
            DefaultProgramme.find(name)?.let { DefaultProgramme.exerciseRows(listOf(it), snap.unit, { newId() }, snap.exercises.size).first() }
                ?: ExerciseEntity(newId(), name.trim(), sort = snap.exercises.size),
        )
        val before = d.exerciseIds
        setIds(d, d.exerciseIds.idList() + e.id)
        Undo.center.post("training", "Added ${e.name}") { day(dayId)?.let { c.training.savePlanDay(it.copy(exerciseIds = before)) } }
    }

    suspend fun tune(e: ExerciseEntity, sets: Int, repMin: Int, repMax: Int, incrementKg: Double) {
        c.training.saveExercise(e.copy(sets = sets, repMin = repMin, repMax = maxOf(repMin, repMax), incrementKg = incrementKg))
    }

    suspend fun setUnit(u: WeightUnit) = c.training.saveSettings { it.copy(unit = u.key) }

    suspend fun restDelta(seconds: Int) = c.training.saveSettings { it.copy(restSeconds = (it.restSeconds + seconds).coerceIn(30, 300)) }

    suspend fun toggleDay(d: DayOfWeek) = c.training.saveSettings { s ->
        val cur = Schedule.parseWeekdays(s.weekdays)
        val next = if (d in cur) cur - d else cur + d
        if (next.size in 2..4) s.copy(weekdays = Schedule.formatWeekdays(next), daysPerWeek = next.size) else s
    }
}
