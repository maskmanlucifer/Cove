package app.cove.companion.feature.training.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One finished session of one lift: the heaviest set ([topKg] x [topReps]) and every set. */
data class LiftPoint(val sessionId: String, val date: LocalDate, val topKg: Double, val topReps: Int, val sets: List<LoggedSet>)

/** Window of the lift chart. */
enum class ChartRange(val label: String, val days: Int?) {
    Month("1M", 30), ThreeMonths("3M", 91), All("All", null);

    /** The points of [all] (oldest first) that fall in this window ending [today]. */
    fun filter(all: List<LiftPoint>, today: LocalDate): List<LiftPoint> =
        if (days == null) all else all.filter { !it.date.isBefore(today.minusDays(days.toLong())) }
}

/** Range of the Progress screen. */
enum class ProgressRange(val label: String) { Week("Week"), Month("Month"), Year("Year") }

/** A lift's first and latest top weight in a range, for the summary and the top sets table. */
data class LiftChange(val name: String, val sort: Int, val bodyweight: Boolean, val startKg: Double, val nowKg: Double, val startReps: Int, val nowReps: Int, val sessions: Int) {
    val up get() = if (bodyweight) nowReps > startReps else nowKg > startKg + 0.001
}

/** Statistics shown on the lift history, body weight and progress screens (pure, unit tested). */
object TrainingStats {
    /** Per-session top sets of one lift from raw rows, oldest first. [rows] are (sessionId, date, sets of the lift in that session). */
    fun points(rows: List<Triple<String, LocalDate, List<LoggedSet>>>): List<LiftPoint> =
        rows.filter { it.third.isNotEmpty() }.sortedBy { it.second }.map { (id, date, sets) ->
            val top = sets.maxOf { it.weightKg }
            val reps = sets.filter { abs(it.weightKg - top) < 0.01 }.maxOf { it.reps }
            LiftPoint(id, date, top, reps, sets)
        }

    /** The heaviest set ever, preferring more reps at equal weight. */
    fun best(points: List<LiftPoint>): LoggedSet? =
        points.map { LoggedSet(it.topKg, it.topReps) }.maxWithOrNull(compareBy<LoggedSet> { it.weightKg }.thenBy { it.reps })

    /** "Up 7.5 kg in four weeks." under the lift's name. */
    fun liftDelta(points: List<LiftPoint>, unit: WeightUnit, bodyweight: Boolean = false): String {
        if (points.isEmpty()) return "No sets yet."
        if (points.size == 1) return "One session so far."
        val first = points.first()
        val last = points.last()
        val span = spanText(first.date, last.date)
        if (bodyweight) {
            val d = last.topReps - first.topReps
            return when {
                d > 0 -> "Up $d ${if (d == 1) "rep" else "reps"} $span."
                d < 0 -> "Down ${-d} ${if (d == -1) "rep" else "reps"} $span."
                else -> "Holding steady $span."
            }
        }
        val d = last.topKg - first.topKg
        val amount = WeightFormat.withUnit(abs(d), unit)
        return when {
            d > 0.001 -> "Up $amount $span."
            d < -0.001 -> "Down $amount $span."
            else -> "Holding at ${WeightFormat.withUnit(last.topKg, unit)}."
        }
    }

    /** "in four weeks", "in 3 days", "in five months" between two dates. */
    fun spanText(from: LocalDate, to: LocalDate): String {
        val days = ChronoUnit.DAYS.between(from, to).toInt().coerceAtLeast(1)
        return when {
            days < 7 -> "in ${TrainingText.countWord(days)} ${if (days == 1) "day" else "days"}"
            days < 60 -> {
                val w = Math.round(days / 7.0).toInt().coerceAtLeast(1)
                "in ${TrainingText.countWord(w)} ${if (w == 1) "week" else "weeks"}"
            }
            days < 700 -> {
                val m = Math.round(days / 30.4).toInt()
                "in ${TrainingText.countWord(m)} months"
            }
            else -> "in ${TrainingText.countWord(Math.round(days / 365.0).toInt())} years"
        }
    }

    /** Sessions per bucket, oldest first: [buckets] windows of [windowDays] days ending on [today]. */
    fun perWindow(dates: List<LocalDate>, today: LocalDate, buckets: Int, windowDays: Int): List<Int> =
        (0 until buckets).map { i ->
            val to = today.minusDays((buckets - 1 - i).toLong() * windowDays)
            val from = to.minusDays(windowDays - 1L)
            dates.count { !it.isBefore(from) && !it.isAfter(to) }
        }

    /** Sessions per calendar month for the last [months] months ending with [today]'s month, oldest first. */
    fun perMonth(dates: List<LocalDate>, today: LocalDate, months: Int): List<Int> =
        (0 until months).map { i ->
            val m = java.time.YearMonth.from(today).minusMonths((months - 1 - i).toLong())
            dates.count { java.time.YearMonth.from(it) == m }
        }

    /** Days in a [range] window. */
    fun rangeDays(range: ProgressRange): Int = when (range) {
        ProgressRange.Week -> 7
        ProgressRange.Month -> 28
        ProgressRange.Year -> 365
    }

    /**
     * The headline of the Progress screen: "11 sessions in four weeks. Every lift went up."
     *
     * @param sessions number of finished sessions in the range.
     */
    fun headline(range: ProgressRange, sessions: Int, lifts: List<LiftChange>): String {
        if (sessions == 0) return "No sessions yet. Start one when you are ready."
        val count = if (sessions <= 10) TrainingText.countWord(sessions).replaceFirstChar { it.uppercase() } else sessions.toString()
        val noun = if (sessions == 1) "session" else "sessions"
        val period = when (range) {
            ProgressRange.Week -> "this week"
            ProgressRange.Month -> "in four weeks"
            ProgressRange.Year -> "this year"
        }
        val compared = lifts.filter { it.sessions >= 2 }
        val ups = compared.filter { it.up }
        val second = when {
            compared.isEmpty() -> "Every one counts."
            ups.size == compared.size -> if (compared.size == 1) "${compared[0].name} went up." else "Every lift went up."
            ups.isEmpty() -> "Weights held steady."
            ups.size == 1 -> "${ups[0].name} went up."
            ups.size == 2 -> "${ups[0].name} and ${ups[1].name.replaceFirstChar { it.lowercase() }} went up."
            else -> "${TrainingText.countWord(ups.size).replaceFirstChar { it.uppercase() }} lifts went up."
        }
        return "$count $noun $period. $second"
    }

    /** "Down 0.6 kg this month" for [entries] (day, kg) up to [today]; compares the first entry of the last 30 days with the latest. */
    fun bodyDelta(entries: List<Pair<LocalDate, Double>>, today: LocalDate, unit: WeightUnit): String {
        val recent = entries.filter { !it.first.isBefore(today.minusDays(30)) && !it.first.isAfter(today) }.sortedBy { it.first }
        if (recent.size < 2) return if (entries.isEmpty()) "Your first weigh-in." else "Weigh in again to see the trend."
        val d = recent.last().second - recent.first().second
        val amount = WeightFormat.withUnit(abs(unit.fromKg(d).let { unit.toKg(Math.round(it * 10) / 10.0) }), unit)
        return when {
            abs(unit.fromKg(d)) < 0.05 -> "Steady this month"
            d < 0 -> "Down $amount this month"
            else -> "Up $amount this month"
        }
    }
}
