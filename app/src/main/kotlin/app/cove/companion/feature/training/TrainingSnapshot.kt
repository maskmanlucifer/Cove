package app.cove.companion.feature.training

import app.cove.companion.core.toLocalDate
import app.cove.companion.data.local.entity.ExerciseEntity
import app.cove.companion.data.local.entity.SetLogEntity
import app.cove.companion.data.local.entity.WorkoutSessionEntity
import app.cove.companion.data.repo.TrainingTables
import app.cove.companion.data.repo.idList
import app.cove.companion.feature.training.engine.DoneSession
import app.cove.companion.feature.training.engine.ExerciseKind
import app.cove.companion.feature.training.engine.ExerciseSpec
import app.cove.companion.feature.training.engine.LiftPoint
import app.cove.companion.feature.training.engine.LoggedSet
import app.cove.companion.feature.training.engine.ProgressionEngine
import app.cove.companion.feature.training.engine.Schedule
import app.cove.companion.feature.training.engine.Slot
import app.cove.companion.feature.training.engine.Suggestion
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightUnit
import java.time.DayOfWeek
import java.time.LocalDate

/** The training tables seen from one day: targets, history and the week's schedule are computed from here. */
class TrainingSnapshot(val tables: TrainingTables, val today: LocalDate) {
    private val settingsRow = tables.settings
    val hasPlan: Boolean get() = tables.planDays.isNotEmpty() && settingsRow != null
    val unit: WeightUnit = WeightUnit.of(settingsRow?.unit)
    val restSeconds: Int = settingsRow?.restSeconds ?: 90
    val daysPerWeek: Int = settingsRow?.daysPerWeek ?: 3
    val weekdays: Set<DayOfWeek> = Schedule.parseWeekdays(settingsRow?.weekdays.orEmpty(), Schedule.defaultWeekdays(daysPerWeek))
    val startMinutes: Int = settingsRow?.startMinutes ?: (19 * 60)
    val exercises: List<ExerciseEntity> = tables.exercises
    private val byId = exercises.associateBy { it.id }
    private val startWeights: Map<String, Double> = settingsRow?.startWeights.orEmpty().split(';').mapNotNull {
        val (name, kg) = it.split('=').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
        kg.toDoubleOrNull()?.let { v -> name.trim().lowercase() to v }
    }.toMap()

    fun exercise(id: String): ExerciseEntity? = byId[id]

    fun spec(e: ExerciseEntity) = ExerciseSpec(ExerciseKind.of(e.kind), e.incrementKg, e.repMin, e.repMax, e.sets)

    /** Day types in programme order. */
    val dayTypes: List<String> get() = tables.planDays.sortedBy { it.sort }.map { it.dayType }

    /** The lifts of [dayType] in the programme's order. */
    fun dayExercises(dayType: String): List<ExerciseEntity> =
        tables.planDays.firstOrNull { it.dayType.equals(dayType, true) }?.exerciseIds?.idList().orEmpty().mapNotNull { byId[it] }

    fun sessionDate(s: WorkoutSessionEntity): LocalDate = (s.startedAt ?: s.plannedAt).toLocalDate()

    /** Finished sessions, oldest first. */
    val finished: List<WorkoutSessionEntity> = tables.sessions.filter { it.endedAt != null }.sortedBy { it.startedAt ?: it.plannedAt }

    /** A session planned for today (for example by "+ Dips") that has not started yet. */
    val plannedToday: WorkoutSessionEntity? = tables.sessions.firstOrNull { it.startedAt == null && it.endedAt == null && it.plannedAt.toLocalDate() == today }

    val active: WorkoutSessionEntity? = tables.sessions.firstOrNull { it.startedAt != null && it.endedAt == null }

    private val setsBySession: Map<String, List<SetLogEntity>> = tables.sets.groupBy { it.sessionId }

    fun setsOf(sessionId: String): List<SetLogEntity> = setsBySession[sessionId].orEmpty().sortedWith(compareBy({ it.loggedAt }, { it.setNo }))

    /** Sets of one lift in one session, in set order. */
    fun liftSets(sessionId: String, exerciseId: String): List<SetLogEntity> = setsOf(sessionId).filter { it.exerciseId == exerciseId }.sortedBy { it.setNo }

    /** What past finished sessions logged for [exerciseId], oldest first, leaving out [exclude]. */
    fun history(exerciseId: String, exclude: String? = null): List<List<LoggedSet>> =
        finished.filter { it.id != exclude }.map { s -> liftSets(s.id, exerciseId).map { LoggedSet(it.weightKg, it.reps) } }.filter { it.isNotEmpty() }

    /** The weight to start [e] at: the user's own, else the default programme's, else a light bar. */
    fun startKg(e: ExerciseEntity): Double =
        startWeights[e.name.lowercase()] ?: DefaultProgramme.find(e.name)?.startKg ?: if (e.kind == ExerciseKind.Bodyweight.key) 0.0 else 20.0

    /** The next target for [e]; [exclude] is a session to leave out of the history (the one in progress). */
    fun suggestion(e: ExerciseEntity, exclude: String? = null): Suggestion = ProgressionEngine.next(spec(e), history(e.id, exclude), startKg(e))

    /** What the last finished session did on [e], for "last time". */
    fun lastTime(e: ExerciseEntity, exclude: String? = null): List<LoggedSet> = history(e.id, exclude).lastOrNull().orEmpty()

    val doneSessions: List<DoneSession> = finished.map { DoneSession(it.dayType, sessionDate(it)) }

    /** The next [count] planned sessions from today. */
    fun upcoming(count: Int): List<Slot> = Schedule.upcoming(today, weekdays, dayTypes, doneSessions, count)

    /** Session minutes estimate for [ids]. */
    fun estimateMinutes(ids: List<String>): Int {
        val lifts = ids.mapNotNull { byId[it] }
        return TrainingText.estimateMinutes(lifts.sumOf { it.sets }, lifts.size, restSeconds)
    }

    /** Per-session top sets of [exerciseId] for the history chart, oldest first. */
    fun points(exerciseId: String): List<LiftPoint> = TrainingStats.points(
        finished.map { s -> Triple(s.id, sessionDate(s), liftSets(s.id, exerciseId).map { LoggedSet(it.weightKg, it.reps) }) },
    )
}
