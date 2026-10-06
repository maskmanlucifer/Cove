package app.cove.companion.feature.training.engine

import kotlin.math.roundToInt

/** Wording shared by the training screens (all copy lives here so it can be unit tested). */
object TrainingText {
    /** "62.5 kg · 3×8" for a weighted lift, "3×12" for body weight. */
    fun targetLine(spec: ExerciseSpec, target: Target, unit: WeightUnit): String =
        if (spec.isBodyweight) "${target.sets}×${target.reps}" else "${WeightFormat.withUnit(target.weightKg, unit)} · ${target.sets}×${target.reps}"

    /** "62.5 → 62.5 kg" parts for the Session done card: (before, after). */
    fun change(spec: ExerciseSpec, before: Target, after: Target, unit: WeightUnit): Pair<String, String> =
        if (spec.isBodyweight) "${before.sets}×${before.reps} → " to "${after.sets}×${after.reps}"
        else WeightFormat.number(before.weightKg, unit) + " → " to WeightFormat.withUnit(after.weightKg, unit)

    /**
     * The sentence under the sets of a voice draft (frame 44): what the logged sets mean for next time.
     *
     * @param next the suggestion computed with the drafted sets appended to the history.
     */
    fun draftNote(next: Suggestion, working: List<LoggedSet>, goal: Int, unit: WeightUnit): String {
        val kg = WeightFormat.withUnit(next.target.weightKg, unit)
        return when (next.move) {
            Move.Hold -> when {
                working.isEmpty() -> "Logged."
                working.last().reps < goal -> "The last set came up short, so next time stays at $kg."
                working.any { it.reps < goal } -> "A set came up short, so next time stays at $kg."
                else -> "That is fewer sets than planned, so next time stays at $kg."
            }
            Move.Increase -> "Every set landed, so next time goes up to $kg."
            Move.Reps -> "Every set landed. Next time aim for ${next.target.reps} reps."
            Move.Deload -> "Two tough sessions in a row, so next time eases back to $kg."
            Move.Start -> "Logged. Cove will plan from here."
        }
    }

    /** "3 sets" / "one set" style count for headings. */
    fun setsHeadline(n: Int): String = when (n) {
        1 -> "One set."
        else -> countWord(n).replaceFirstChar { it.uppercase() } + " sets."
    }

    private val words = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

    /** "three" for counts up to ten, digits above. */
    fun countWord(n: Int): String = words.getOrNull(n) ?: n.toString()

    /** "about 45 min" estimate for a session: [sets] sets with [restSeconds] between them plus a few minutes per lift, rounded to 5. */
    fun estimateMinutes(sets: Int, exercises: Int, restSeconds: Int): Int {
        if (sets <= 0) return 0
        val seconds = sets * (restSeconds + SET_WORK_SECONDS) + exercises * EXERCISE_SETUP_SECONDS
        return maxOf(5, (seconds / 60.0 / 5).roundToInt() * 5)
    }

    private const val SET_WORK_SECONDS = 45
    private const val EXERCISE_SETUP_SECONDS = 180

    private val small = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen",
        "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val tens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    /** English words for 0..999 ("thirty-seven", "one hundred and five"). */
    fun spokenNumber(n: Int): String = when {
        n < 20 -> small[n.coerceAtLeast(0)]
        n < 100 -> tens[n / 10] + if (n % 10 == 0) "" else "-" + small[n % 10]
        else -> small[(n / 100).coerceAtMost(9)] + " hundred" + if (n % 100 == 0) "" else " and " + spokenNumber(n % 100)
    }

    /** The hint under the steppers: `Or say “thirty-seven five for eight”` (kilograms or pounds as shown). */
    fun sayHint(display: Double, reps: Int, bodyweight: Boolean): String {
        if (bodyweight) return "Or say “${spokenNumber(reps)} reps”"
        val tenths = Math.round(display * 10).toInt()
        val whole = spokenNumber(tenths / 10)
        val frac = tenths % 10
        val w = if (frac == 0) whole else "$whole ${spokenNumber(frac)}"
        return "Or say “$w for ${spokenNumber(reps)}”"
    }

    /** "7 pm" or "7:30 pm" for [minutes] since midnight. */
    fun timeLabel(minutes: Int): String {
        val h24 = (minutes / 60) % 24
        val m = minutes % 60
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        return (if (m == 0) "$h12" else "$h12:${m.toString().padStart(2, '0')}") + if (h24 < 12) " am" else " pm"
    }

    /** "1:24" for [seconds] (never negative). */
    fun clock(seconds: Int): String {
        val s = maxOf(0, seconds)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** "1 minute 24 seconds" for screen readers. */
    fun spokenClock(seconds: Int): String {
        val s = maxOf(0, seconds)
        val m = s / 60
        val r = s % 60
        return buildList {
            if (m > 0) add("$m minute${if (m == 1) "" else "s"}")
            if (r > 0 || m == 0) add("$r second${if (r == 1) "" else "s"}")
        }.joinToString(" ")
    }
}
