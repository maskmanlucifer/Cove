package app.cove.companion.feature.training.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Whether a lift is loaded with a weight or done with body weight (reps only). */
enum class ExerciseKind(val key: String) {
    Weighted("weighted"),
    Bodyweight("bodyweight");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Weighted
    }
}

/** What the engine needs to know about a lift. [repMin] == [repMax] means fixed reps such as 3x8. */
data class ExerciseSpec(
    val kind: ExerciseKind,
    val incrementKg: Double,
    val repMin: Int,
    val repMax: Int,
    val sets: Int,
) {
    val isBodyweight get() = kind == ExerciseKind.Bodyweight
    val isRange get() = repMax > repMin
}

/** One logged set, weight in kilograms (0 for body weight). */
data class LoggedSet(val weightKg: Double, val reps: Int)

/** What to lift next time: [reps] is the rep goal of every one of [sets] sets. */
data class Target(val weightKg: Double, val reps: Int, val sets: Int)

/** Why the target moved. */
enum class Move { Start, Increase, Reps, Hold, Deload }

/** The next target and the sentence that explains it (copy of frames 44 and 45). */
data class Suggestion(val target: Target, val move: Move, val reason: String)

/**
 * Double progression, as plain functions of past sessions (see `docs/TRAINING.md`).
 *
 * The history is replayed from the first session: the target starts at the start weight and the rep minimum, and each
 * session moves it. Sets lighter than the session's heaviest set (warm-ups) are ignored.
 * - Every working set reached the rep goal: for a rep range below its top, aim for two more reps ("Aim for 12 reps
 *   first."); otherwise add one increment and go back to the rep minimum ("All sets hit 8."). Body weight adds two reps.
 * - Any working set short: hold ("Hold. Last set was 6 reps.").
 * - Two sessions in a row short at the same weight: ease back about 10 percent, rounded to the increment.
 * - Fewer working sets than planned: hold without counting it as a miss.
 */
object ProgressionEngine {
    /** Weight drop of a deload. */
    const val DELOAD_FACTOR = 0.9

    /** Reps added for a bodyweight lift or a rep range after a clean session. */
    const val REP_STEP = 2

    private const val EPS = 0.01

    /** [kg] rounded to the nearest multiple of [increment]; halves go up. */
    fun roundToIncrement(kg: Double, increment: Double): Double {
        if (increment <= 0) return kg
        return floor(kg / increment + 0.5 + 1e-9) * increment
    }

    /** About 10 percent lighter than [kg] on the increment grid, always lighter than [kg] (halves go lighter). */
    fun deloadWeight(kg: Double, increment: Double): Double {
        val raw = kg * DELOAD_FACTOR
        val rounded = if (increment <= 0) raw else ceil(raw / increment - 0.5 - 1e-9) * increment
        return maxOf(0.0, if (rounded >= kg - EPS && increment > 0) kg - increment else rounded)
    }

    /**
     * The target for the next session.
     *
     * @param history the logged sets of each past session of this lift, oldest first; sessions with no sets are ignored.
     * @param startKg first weight when there is no history (rounded to the increment).
     */
    fun next(spec: ExerciseSpec, history: List<List<LoggedSet>>, startKg: Double): Suggestion {
        var target = Target(if (spec.isBodyweight) 0.0 else roundToIncrement(startKg, spec.incrementKg), spec.repMin, spec.sets)
        var suggestion = Suggestion(target, Move.Start, "Starting weight.")
        var missedAt: Double? = null
        for (session in history.filter { it.isNotEmpty() }) {
            val top = if (spec.isBodyweight) 0.0 else session.maxOf { it.weightKg }
            val goal = if (spec.isBodyweight || abs(top - target.weightKg) < EPS) target.reps else spec.repMin
            val working = if (spec.isBodyweight) session else session.filter { abs(it.weightKg - top) < EPS }
            suggestion = judge(spec, top, goal, working, missedAt)
            target = suggestion.target
            missedAt = if (suggestion.move == Move.Hold && working.any { it.reps < goal }) top else null
        }
        return suggestion
    }

    private fun judge(spec: ExerciseSpec, top: Double, goal: Int, working: List<LoggedSet>, missedAt: Double?): Suggestion {
        val short = working.filter { it.reps < goal }
        val keep = Target(top, goal, spec.sets)
        if (short.isNotEmpty()) {
            val repeated = missedAt != null && abs(missedAt - top) < EPS
            if (repeated) return deload(spec, top, goal)
            val n = if (working.last().reps < goal) working.last().reps else short.minOf { it.reps }
            val reason = if (working.last().reps < goal) "Hold. Last set was $n ${reps(n)}." else "Hold. A set came up at $n ${reps(n)}."
            return Suggestion(keep, Move.Hold, reason)
        }
        if (working.size < spec.sets) return Suggestion(keep, Move.Hold, "Hold. ${working.size} of ${spec.sets} sets last time.")
        if (spec.isBodyweight) {
            val to = goal + REP_STEP
            return Suggestion(keep.copy(reps = to), Move.Reps, "Two more reps each set.")
        }
        if (spec.isRange && goal < spec.repMax) {
            val to = minOf(spec.repMax, goal + REP_STEP)
            return Suggestion(keep.copy(reps = to), Move.Reps, "Aim for $to reps first.")
        }
        return Suggestion(Target(roundToIncrement(top + spec.incrementKg, spec.incrementKg), spec.repMin, spec.sets), Move.Increase, "All sets hit $goal.")
    }

    private fun deload(spec: ExerciseSpec, top: Double, goal: Int): Suggestion {
        val reason = "Two tough sessions in a row. Ease back about 10% and build up again."
        return if (spec.isBodyweight) {
            Suggestion(Target(0.0, maxOf(1, (goal * DELOAD_FACTOR + 0.49).roundToInt()).coerceAtMost(goal - 1).coerceAtLeast(1), spec.sets), Move.Deload, reason)
        } else {
            Suggestion(Target(deloadWeight(top, spec.incrementKg), spec.repMin, spec.sets), Move.Deload, reason)
        }
    }

    private fun reps(n: Int) = if (n == 1) "rep" else "reps"
}
