package app.cove.companion.feature.training

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.Undo
import app.cove.companion.data.local.entity.ExerciseEntity
import app.cove.companion.data.local.entity.SetLogEntity
import app.cove.companion.data.local.entity.WorkoutSessionEntity
import app.cove.companion.data.repo.idList
import app.cove.companion.feature.training.engine.RestState
import app.cove.companion.feature.training.engine.SessionFlow
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.rest.RestAlarm
import app.cove.companion.feature.training.rest.RestStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The set being logged now (frame 42). */
data class SessionUi(
    val loaded: Boolean = false,
    val session: WorkoutSessionEntity? = null,
    val exercise: ExerciseEntity? = null,
    val header: String = "",
    val setNo: Int = 1,
    val plannedSets: Int = 3,
    val lastTime: String? = null,
    val previous: List<SetLogEntity> = emptyList(),
    val weightKg: Double = 0.0,
    val reps: Int = 8,
    val unit: WeightUnit = WeightUnit.Kg,
    val bodyweight: Boolean = false,
    val finished: Boolean = false,
    val hint: String = "",
    val allDone: Boolean = false,
)

/** Drives a running workout: the next set to log, its draft numbers, rest and finishing. */
class SessionViewModel(private val c: AppContainer, context: Context) : ViewModel() {
    private val app = context.applicationContext
    private val rest = RestStore(app)
    private val drafts = MutableStateFlow<Map<String, Pair<Double, Int>>>(emptyMap())

    val state: StateFlow<SessionUi> = combine(c.trainingSnapshots(), drafts) { snap, d -> build(snap, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUi())

    private fun order(snap: TrainingSnapshot, s: WorkoutSessionEntity) = s.exerciseIds.idList().filter { snap.exercise(it) != null }

    private fun position(snap: TrainingSnapshot, s: WorkoutSessionEntity): app.cove.companion.feature.training.engine.SessionPosition {
        val ids = order(snap, s)
        val logged = ids.associateWith { snap.liftSets(s.id, it).size }
        return SessionFlow.position(ids, s.skippedIds.idList().toSet(), ids.associateWith { snap.exercise(it)!!.sets }, logged)
    }

    private fun build(snap: TrainingSnapshot, d: Map<String, Pair<Double, Int>>): SessionUi {
        val s = snap.active ?: return SessionUi(loaded = true)
        val pos = position(snap, s)
        val e = pos.exerciseId?.let(snap::exercise)
        val header = "${s.dayType} · ${minOf(pos.index + 1, pos.total)} of ${pos.total}"
        if (e == null) return SessionUi(true, s, null, header, finished = true, allDone = true, unit = snap.unit)
        val spec = snap.spec(e)
        val prev = snap.liftSets(s.id, e.id)
        val target = snap.suggestion(e, s.id).target
        val base = prev.lastOrNull()?.let { it.weightKg to it.reps } ?: (target.weightKg to target.reps)
        val (w, r) = d["${e.id}#${pos.setNo}"] ?: base
        val last = snap.lastTime(e, s.id)
        val lt = last.getOrNull(pos.setNo - 1) ?: last.lastOrNull()
        val lastText = lt?.let { "last time " + if (spec.isBodyweight) "${it.reps} reps" else WeightFormat.set(it.weightKg, it.reps, snap.unit) }
        return SessionUi(
            true, s, e, header, pos.setNo, e.sets, lastText, prev, w, r, snap.unit, spec.isBodyweight, false,
            TrainingText.sayHint(snap.unit.fromKg(w), r, spec.isBodyweight),
        )
    }

    private fun key(u: SessionUi) = "${u.exercise?.id}#${u.setNo}"

    private fun edit(change: (Double, Int) -> Pair<Double, Int>) {
        val u = state.value
        if (u.exercise == null) return
        drafts.update { it + (key(u) to change(u.weightKg, u.reps)) }
    }

    fun stepWeight(dir: Int) = edit { w, r ->
        val unit = state.value.unit
        unit.toKg(maxOf(0.0, Math.round((unit.fromKg(w) + dir * unit.stepperStep) * 10) / 10.0)) to r
    }

    fun stepReps(dir: Int) = edit { w, r -> w to (r + dir).coerceIn(1, 100) }

    fun setWeight(kg: Double) = edit { _, r -> kg.coerceIn(0.0, 999.0) to r }

    fun setReps(n: Int) = edit { w, _ -> w to n.coerceIn(1, 100) }

    /** Logs the set on screen. Returns where to go next: Rest, Done (with the session id) or Stay. */
    suspend fun log(): Next {
        val u = state.value
        val s = u.session ?: return Next.Stay
        val e = u.exercise ?: return Next.Stay
        c.training.logSet(s.id, e.id, if (u.bodyweight) 0.0 else u.weightKg, u.reps)
        val snap = c.trainingSnapshots().first()
        val after = snap.active?.let { position(snap, it) } ?: return Next.Stay
        if (after.finished) {
            c.training.finishSession(s.id)
            return Next.Done(s.id)
        }
        val ne = snap.exercise(after.exerciseId!!)!!
        val prev = snap.liftSets(s.id, ne.id).lastOrNull()
        val t = snap.suggestion(ne, s.id).target
        val w = prev?.weightKg ?: t.weightKg
        val r = prev?.reps ?: t.reps
        val spec = snap.spec(ne)
        val setText = if (spec.isBodyweight) "set ${after.setNo}, $r reps" else "set ${after.setNo}, ${WeightFormat.set(w, r, snap.unit)}"
        val label = if (ne.id == e.id) setText else "${ne.name}, $setText"
        startRest(snap.restSeconds, label, "${s.dayType} · ${after.index + 1} of ${after.total}")
        return Next.Rest
    }

    private fun startRest(seconds: Int, label: String, header: String) {
        val st = RestState(c.clock.now() + seconds * 1000L, label, header)
        rest.set(st)
        RestAlarm.schedule(app, st)
    }

    /** Edits a logged set; the previous values come back with Undo. */
    suspend fun updateSet(set: SetLogEntity, kg: Double, reps: Int) {
        c.training.saveSet(set.copy(weightKg = kg, reps = reps))
        Undo.center.post("training", "Set updated") { c.training.saveSet(set) }
    }

    suspend fun deleteSet(set: SetLogEntity) {
        c.training.deleteSet(set.id)
        Undo.center.post("training", "Set removed") { c.training.restoreSet(set.id) }
    }

    /** Leaves out the lift on screen. Returns true when that was the last one. */
    suspend fun skipExercise(): Boolean {
        val u = state.value
        val s = u.session ?: return false
        val e = u.exercise ?: return false
        c.training.skipExercise(s.id, e.id)
        val snap = c.trainingSnapshots().first()
        return snap.active?.let { position(snap, it).finished } ?: true
    }

    /** Ends the workout now. Returns the session id when it was kept, null when nothing was logged. */
    suspend fun finish(): String? {
        val s = state.value.session ?: return null
        rest.clear()
        RestAlarm.cancel(app)
        return if (c.training.finishSession(s.id)) s.id else null
    }

    /** Leaves nothing running: used when a finished workout is left. */
    fun clearRest() {
        rest.clear()
        RestAlarm.cancel(app)
    }

    /** Where [log] sends the user. */
    sealed interface Next {
        data object Stay : Next
        data object Rest : Next
        data class Done(val sessionId: String) : Next
    }
}
