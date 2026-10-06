package app.cove.companion.feature.training.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.ai.model.ExerciseNames
import app.cove.companion.core.Undo
import app.cove.companion.data.local.entity.PlanExerciseEntity
import app.cove.companion.feature.training.engine.StarterTemplate
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeekPlan
import app.cove.companion.feature.training.engine.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the day editor shows: the weekday's exercises in order, the unit and names to suggest. */
data class PlanDayUi(
    val loaded: Boolean = false,
    val unit: WeightUnit = WeightUnit.Kg,
    val rows: List<PlanExerciseEntity> = emptyList(),
    val names: List<String> = emptyList(),
)

/** Edits one weekday's template: add, change, delete with Undo, reorder, copy to other days, start from a template. */
class PlanDayViewModel(private val c: AppContainer, val weekday: Int) : ViewModel() {
    private val repo get() = c.training

    val state: StateFlow<PlanDayUi> = repo.tables.map { t ->
        val known = repo.knownNames()
        PlanDayUi(
            true, WeightUnit.of(t.settings?.unit),
            t.plan.filter { it.weekday == weekday }.sortedWith(compareBy({ it.sort }, { it.name })),
            (known + ExerciseNames.defaults + COMMON).distinctBy { WeekPlan.nameKey(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanDayUi())

    private val dayName get() = TrainingText.weekdayName(weekday)

    fun add(name: String, kg: Double, sets: Int, reps: Int, incrementKg: Double) = viewModelScope.launch {
        val row = repo.addExercise(weekday, name, kg, sets, reps, incrementKg)
        Undo.center.post("training", "Added ${row.name} to $dayName") { repo.deleteExercise(row.id) }
    }

    fun update(row: PlanExerciseEntity) = viewModelScope.launch { repo.updateExercise(row) }

    fun delete(row: PlanExerciseEntity) = viewModelScope.launch {
        repo.deleteExercise(row.id)
        Undo.center.post("training", "Removed ${row.name}") { repo.restoreExercise(row.id) }
    }

    fun move(row: PlanExerciseEntity, dir: Int) = viewModelScope.launch { repo.move(row.id, dir) }

    /** Copies this day's exercises to [days]; a lift already there by name is left alone. */
    fun copyTo(days: Set<Int>) = viewModelScope.launch {
        val ids = repo.copyDay(weekday, days)
        if (ids.isNotEmpty()) Undo.center.post("training", "Copied to ${days.size} ${if (days.size == 1) "day" else "days"}") { repo.deleteExercises(ids) }
    }

    /** Adds the lifts of [template] to each of [days] (a lift already there by name is left alone). */
    fun applyTemplate(template: StarterTemplate, days: Set<Int>) = viewModelScope.launch {
        val created = ArrayList<String>()
        for (d in days) {
            val have = repo.plan().filter { it.weekday == d }.map { WeekPlan.nameKey(it.name) }.toSet()
            for ((name, sets, reps) in template.lifts) {
                if (WeekPlan.nameKey(name) in have) continue
                created += repo.planExercise(d, name, null, sets, reps).first.id
            }
        }
        if (created.isNotEmpty()) Undo.center.post("training", "Added ${template.name}") { repo.deleteExercises(created) }
    }

    private companion object {
        val COMMON = listOf("Bench press", "Squat", "Deadlift", "Overhead press", "Barbell row", "Pull-ups", "Lat pulldown", "Biceps curl", "Leg press")
    }
}
