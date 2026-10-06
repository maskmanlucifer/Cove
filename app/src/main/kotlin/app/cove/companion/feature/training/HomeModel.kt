package app.cove.companion.feature.training

import app.cove.companion.data.repo.idList
import app.cove.companion.feature.training.engine.DoneSession
import app.cove.companion.feature.training.engine.Schedule
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat

/** One lift of the session card: name left, "62.5 kg · 3×8" right. */
data class LiftRow(val id: String, val name: String, val detail: String)

/** Everything the Training home draws once a programme exists. */
data class HomeState(
    val dayType: String,
    /** "Today · 7 pm", "Thursday · 7 pm" or "In progress". */
    val whenLabel: String,
    val lifts: List<LiftRow>,
    /** "4 exercises · about 45 min". */
    val meta: String,
    val running: Boolean,
    /** Name of the suggested accessory behind "+ Dips", or null when none is left. */
    val accessory: String?,
    val nextLine: String,
    val progressLine: String,
    val exerciseIds: List<String>,
)

/** Pure construction of [HomeState] from the tables (unit tested). */
object HomeModel {
    private val groupOf = mapOf("push" to "Dips", "pull" to "Pull-ups", "legs" to "Lunges")

    fun build(snap: TrainingSnapshot): HomeState {
        val active = snap.active
        val planned = snap.plannedToday
        val slots = snap.upcoming(2)
        val slot = slots.firstOrNull()
        val dayType = active?.dayType ?: planned?.dayType ?: slot?.dayType ?: snap.dayTypes.firstOrNull().orEmpty()
        val ids = when {
            active != null -> active.exerciseIds.idList()
            planned != null -> planned.exerciseIds.idList()
            else -> snap.dayExercises(dayType).map { it.id }
        }.filter { snap.exercise(it) != null }
        val lifts = ids.mapNotNull { snap.exercise(it) }.map { e ->
            val target = snap.suggestion(e, active?.id).target
            LiftRow(e.id, e.name, TrainingText.targetLine(snap.spec(e), target, snap.unit))
        }
        val at = TrainingText.timeLabel(snap.startMinutes)
        val today = snap.today
        val whenLabel = when {
            active != null -> "In progress"
            planned != null -> "Today · $at"
            slot == null -> ""
            slot.date == today -> "Today · $at"
            Schedule.missedRecently(today, snap.weekdays, snap.doneSessions) && slot.date != today -> "Catch up today, or ${Schedule.dayLabel(today, slot.date)} · $at"
            else -> Schedule.dayLabel(today, slot.date) + " · " + at
        }
        val showsToday = active != null || planned != null || slot?.date == today
        val after = if (showsToday) {
            Schedule.upcoming(today, snap.weekdays, snap.dayTypes, snap.doneSessions + DoneSession(dayType, today), 1).firstOrNull()
        } else slots.getOrNull(1)
        val next = Schedule.nextLine(today, after, restFirst = showsToday)
        val group = snap.exercise(ids.firstOrNull().orEmpty())?.muscleGroup ?: dayType.lowercase()
        val accessory = groupOf[group]?.takeIf { name -> ids.none { snap.exercise(it)?.name.equals(name, true) } }
        val minutes = snap.estimateMinutes(ids)
        return HomeState(
            dayType, whenLabel, lifts,
            "${lifts.size} exercise${if (lifts.size == 1) "" else "s"} · about $minutes min",
            active != null, accessory.takeIf { active == null }, next, progressLine(snap), ids,
        )
    }

    /** "Bench up 7.5 kg": the first main lift with at least two sessions, or a calm empty line. */
    fun progressLine(snap: TrainingSnapshot): String {
        for (e in snap.exercises.sortedBy { it.sort }) {
            val points = snap.points(e.id)
            if (points.size < 2) continue
            val d = points.last().topKg - points.first().topKg
            val name = shortName(e.name)
            return when {
                snap.spec(e).isBodyweight -> {
                    val reps = points.last().topReps - points.first().topReps
                    if (reps > 0) "$name up $reps ${if (reps == 1) "rep" else "reps"}" else "$name holding"
                }
                d > 0.001 -> "$name up ${WeightFormat.withUnit(d, snap.unit)}"
                d < -0.001 -> "$name eased ${WeightFormat.withUnit(-d, snap.unit)}"
                else -> "$name holding steady"
            }
        }
        return "Your first sessions will show here"
    }

    /** "Bench press" reads as "Bench" in running text. */
    fun shortName(name: String) = if (name.equals("Bench press", true)) "Bench" else name
}
