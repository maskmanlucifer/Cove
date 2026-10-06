package app.cove.companion.feature.training.engine

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Wording shared by the training screens and the Today card (all copy lives here so it can be unit tested). */
object TrainingText {
    /** "Bench press · 60 kg · 3 x 8"; body weight lifts leave the weight out. */
    fun rowLine(name: String, kg: Double, sets: Int, reps: Int, unit: WeightUnit): String =
        if (kg <= 0.0) "$name · $sets x $reps" else "$name · ${WeightFormat.withUnit(kg, unit)} · $sets x $reps"

    /** "60 kg · 3 x 8", the detail half of [rowLine]. */
    fun detail(kg: Double, sets: Int, reps: Int, unit: WeightUnit): String =
        if (kg <= 0.0) "$sets x $reps" else "${WeightFormat.withUnit(kg, unit)} · $sets x $reps"

    /** "Bench press, 60 kilograms, 3 sets of 8" for screen readers. */
    fun spoken(name: String, kg: Double, sets: Int, reps: Int, unit: WeightUnit): String {
        val weight = if (kg <= 0.0) "" else ", " + WeightFormat.number(kg, unit) + if (unit == WeightUnit.Kg) " kilograms" else " pounds"
        return "$name$weight, $sets sets of $reps"
    }

    /** "No exercises", "1 exercise", "3 exercises". */
    fun exerciseCount(n: Int): String = when (n) {
        0 -> "Rest"
        1 -> "1 exercise"
        else -> "$n exercises"
    }

    /** "Monday" for an ISO weekday 1..7. */
    fun weekdayName(isoDay: Int): String = DayOfWeek.of(isoDay).getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /** "Mon" for an ISO weekday 1..7. */
    fun weekdayShort(isoDay: Int): String = DayOfWeek.of(isoDay).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** "62.5 kg" or "20 lb" gap as words: "up 5 kg", "down 2.5 kg", "no change". */
    fun change(deltaKg: Double, unit: WeightUnit): String {
        val shown = Math.round(unit.fromKg(deltaKg) * 10) / 10.0
        return when {
            shown > 0 -> "up ${WeightFormat.trim(shown)} ${unit.label}"
            shown < 0 -> "down ${WeightFormat.trim(-shown)} ${unit.label}"
            else -> "no change"
        }
    }

    /** "8, 8, 6" for the reps of the sets. */
    fun repsList(reps: List<Int>): String = reps.joinToString(", ")
}
