package app.cove.companion.feature.training.engine

import app.cove.companion.data.local.entity.ExerciseLogEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Window of the progress charts. */
enum class ChartRange(val label: String, val days: Int?) {
    Month("1M", 30), ThreeMonths("3M", 91), All("All", null);

    /** The points of [all] (oldest first) that fall in this window ending [today]. */
    fun <T> filter(all: List<Pair<LocalDate, T>>, today: LocalDate): List<Pair<LocalDate, T>> =
        if (days == null) all else all.filter { !it.first.isBefore(today.minusDays(days.toLong())) }
}

/** A lift's journey: [firstKg] on its first logged day and [latestKg] now; [points] oldest first. */
data class ExerciseProgress(val name: String, val firstKg: Double, val latestKg: Double, val points: List<Pair<LocalDate, Double>>) {
    val deltaKg get() = latestKg - firstKg
}

/** Statistics for the progress screen (pure, unit tested). */
object TrainingStats {
    private val short = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** "12 Sep", or "Today". */
    fun dateLabel(date: LocalDate, today: LocalDate): String = if (date == today) "Today" else date.format(short)

    /** Log rows as advisor input for the lift called [name], before [before], oldest first. */
    fun history(logs: List<ExerciseLogEntity>, name: String, before: LocalDate): List<LogPoint> {
        val key = WeekPlan.nameKey(name)
        return logs.filter { it.deletedAt == null && WeekPlan.nameKey(it.name) == key && it.day < before.toEpochDay() }
            .sortedBy { it.day }
            .map { LogPoint(LocalDate.ofEpochDay(it.day), it.weightKg, it.targetSets, it.targetReps, parseReps(it.reps)) }
    }

    /** "8,8,6" as reps; blanks are dropped. */
    fun parseReps(text: String): List<Int> = text.split(',').mapNotNull { it.trim().toIntOrNull() }

    /** Every lift that has been logged, newest-logged first, with its first and latest weight. */
    fun exercises(logs: List<ExerciseLogEntity>): List<ExerciseProgress> =
        logs.filter { it.deletedAt == null }.groupBy { WeekPlan.nameKey(it.name) }.values.map { rows ->
            val sorted = rows.sortedBy { it.day }
            ExerciseProgress(sorted.last().name, sorted.first().weightKg, sorted.last().weightKg, sorted.map { LocalDate.ofEpochDay(it.day) to it.weightKg })
        }.sortedByDescending { it.points.last().first }

    /** "Bench press 60 kg, up 5 kg". */
    fun exerciseLine(p: ExerciseProgress, unit: WeightUnit): String =
        if (p.latestKg <= 0.0) p.name else "${p.name} ${WeightFormat.withUnit(p.latestKg, unit)}, ${TrainingText.change(p.deltaKg, unit)}"

    /** "Down 1.2 kg since 12 Sep" from the first to the latest of [entries] (oldest first); calm text when there is little data. */
    fun bodyChange(entries: List<Pair<LocalDate, Double>>, today: LocalDate, unit: WeightUnit): String {
        if (entries.isEmpty()) return "No weigh-ins yet."
        if (entries.size == 1) return "One weigh-in so far."
        val d = entries.last().second - entries.first().second
        val shown = Math.round(abs(unit.fromKg(d)) * 10) / 10.0
        val since = "since " + dateLabel(entries.first().first, today)
        return when {
            shown < 0.05 -> "Steady $since"
            d < 0 -> "Down ${WeightFormat.trim(shown)} ${unit.label} $since"
            else -> "Up ${WeightFormat.trim(shown)} ${unit.label} $since"
        }
    }

    /** The last [days] days of [entries] (oldest first) for the small sparkline. */
    fun recent(entries: List<Pair<LocalDate, Double>>, today: LocalDate, days: Int = 30): List<Pair<LocalDate, Double>> =
        entries.filter { !it.first.isBefore(today.minusDays(days.toLong())) && !it.first.isAfter(today) }
}
