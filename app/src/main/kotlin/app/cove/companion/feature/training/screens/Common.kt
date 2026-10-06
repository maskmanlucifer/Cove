package app.cove.companion.feature.training.screens

import app.cove.companion.feature.training.engine.ChartMath
import app.cove.companion.feature.training.engine.LoggedSet
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.ui.ChartLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val short = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val withDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "Today" or "Thu 1 Oct". */
fun dayText(today: LocalDate, date: LocalDate): String = if (date == today) "Today" else date.format(withDay)

/** First, middle and last captions under a chart: "21 Sep", "28 Sep", "Today". */
fun chartLabels(dates: List<LocalDate>, today: LocalDate): List<ChartLabel> =
    ChartMath.labelIndexes(dates.size).mapIndexed { k, i ->
        val last = i == dates.lastIndex
        ChartLabel(i, if (last && dates[i] == today) "Today" else dates[i].format(short))
    }

/** "62.5 × 8, 8, 6": sets grouped by weight in order, numbers in [unit]; body weight shows reps only. */
fun setsText(sets: List<LoggedSet>, unit: WeightUnit, bodyweight: Boolean = false): String {
    if (bodyweight) return sets.joinToString(", ") { it.reps.toString() }
    val groups = ArrayList<Pair<Double, MutableList<Int>>>()
    for (s in sets) {
        if (groups.lastOrNull()?.first?.let { Math.abs(it - s.weightKg) < 0.01 } == true) groups.last().second += s.reps
        else groups += s.weightKg to mutableListOf(s.reps)
    }
    return groups.joinToString(" · ") { (w, reps) -> WeightFormat.number(w, unit) + " × " + reps.joinToString(", ") }
}
