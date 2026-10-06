package app.cove.companion.feature.training.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** One logged day of one lift, as the advisor reads it. */
data class LogPoint(val day: LocalDate, val weightKg: Double, val targetSets: Int, val targetReps: Int, val reps: List<Int>) {
    /** Every planned set was done and reached the planned reps. */
    val allHit get() = reps.size >= targetSets && reps.all { it >= targetReps }
}

/** What the advisor suggests. Only [Increase], [Deload] and [Restart] carry a button; [Hold] is just a sentence. */
enum class AdviceKind { Increase, Deload, Restart, Hold }

/** A suggested weight with the plain-words [reason]. [actionable] advice offers "Use X". */
data class WeightAdvice(val kind: AdviceKind, val weightKg: Double, val reason: String) {
    val actionable get() = kind != AdviceKind.Hold
}

/**
 * Rules for the weight suggestion on today's workout (see `docs/TRAINING.md`). First match wins:
 * 1. More than [GAP_DAYS] days since the last session: restart about 10% lighter ("Ease back in").
 * 2. Two weak sessions in a row at the same weight: deload about 10%.
 * 3. Every set reached the planned reps last time: one increment more.
 * 4. Otherwise hold, saying which set came up short.
 * Nothing is suggested without history, for body weight lifts, or when the suggestion would not change today's weight.
 */
object WeightAdvisor {
    /** Days without a session after which Cove suggests a lighter restart. */
    const val GAP_DAYS = 21

    private const val EPS = 0.01

    /**
     * @param history logged days of this lift before [today], oldest first.
     * @param currentKg the weight planned for today.
     * @param incrementKg the lift's step.
     */
    fun advise(history: List<LogPoint>, currentKg: Double, incrementKg: Double, today: LocalDate, unit: WeightUnit): WeightAdvice? {
        val last = history.lastOrNull() ?: return null
        if (last.weightKg <= 0.0) return null
        fun w(kg: Double) = WeightFormat.withUnit(kg, unit)
        val days = ChronoUnit.DAYS.between(last.day, today)
        if (days > GAP_DAYS) {
            val kg = deloadWeight(last.weightKg, incrementKg)
            if (kg >= currentKg - EPS) return null
            val weeks = (days / 7).toInt()
            return WeightAdvice(AdviceKind.Restart, kg, "It has been $weeks weeks since your last session. Ease back in at ${w(kg)}.")
        }
        val prev = history.getOrNull(history.size - 2)
        if (!last.allHit && prev != null && !prev.allHit && abs(prev.weightKg - last.weightKg) < EPS) {
            val kg = deloadWeight(last.weightKg, incrementKg)
            if (kg >= currentKg - EPS) return null
            return WeightAdvice(AdviceKind.Deload, kg, "Two tough sessions at ${w(last.weightKg)}. Try a lighter ${w(kg)} and build back up.")
        }
        if (last.allHit) {
            val kg = roundToIncrement(last.weightKg + incrementKg, incrementKg)
            if (kg <= currentKg + EPS) return null
            return WeightAdvice(AdviceKind.Increase, kg, "Last time all sets done at ${w(last.weightKg)}. Try ${w(kg)}.")
        }
        return WeightAdvice(AdviceKind.Hold, currentKg, holdReason(last) + " Stay at ${w(currentKg)}.")
    }

    private fun holdReason(last: LogPoint): String {
        val short = last.reps.filter { it < last.targetReps }
        return when {
            last.reps.size < last.targetSets -> "Only ${last.reps.size} of ${last.targetSets} sets last time."
            last.reps.last() < last.targetReps -> "Last set was ${last.reps.last()} ${repWord(last.reps.last())}."
            else -> "A set came up at ${short.min()} ${repWord(short.min())}."
        }
    }

    private fun repWord(n: Int) = if (n == 1) "rep" else "reps"

    /** [kg] rounded to the nearest multiple of [increment]; halves go up. */
    fun roundToIncrement(kg: Double, increment: Double): Double =
        if (increment <= 0) kg else floor(kg / increment + 0.5 + 1e-9) * increment

    /** About 10 percent lighter than [kg] on the increment grid, always lighter than [kg] (halves go lighter). */
    fun deloadWeight(kg: Double, increment: Double): Double {
        val raw = kg * 0.9
        val rounded = if (increment <= 0) raw else ceil(raw / increment - 0.5 - 1e-9) * increment
        return maxOf(0.0, if (rounded >= kg - EPS && increment > 0) kg - increment else rounded)
    }
}
