package app.cove.companion.feature.training

import app.cove.companion.feature.training.engine.LiftChange
import app.cove.companion.feature.training.engine.ProgressRange
import app.cove.companion.feature.training.engine.TrainingStats
import app.cove.companion.feature.training.ui.Bar
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** A row of the top sets table; [fraction] is the bar (current relative to the heaviest listed). */
data class TopSetRow(val exerciseId: String, val name: String, val start: String?, val now: String, val fraction: Float)

/** Everything the Progress screen draws for one range (all from the tables). */
data class ProgressState(
    val range: ProgressRange,
    val headline: String,
    val sessions: Int,
    val bodyPoints: List<Pair<LocalDate, Double>>,
    val bars: List<Bar>,
    val barsTitle: String,
    val goalText: String,
    val goal: Int,
    val topSets: List<TopSetRow>,
)

/** Pure construction of [ProgressState] (unit tested). */
object ProgressModel {
    private val dm = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun build(snap: TrainingSnapshot, range: ProgressRange): ProgressState {
        val today = snap.today
        val from = today.minusDays(TrainingStats.rangeDays(range) - 1L)
        val inRange = snap.finished.filter { snap.sessionDate(it) in from..today }
        val dates = inRange.map { snap.sessionDate(it) }
        val changes = snap.exercises.sortedBy { it.sort }.mapNotNull { e ->
            val pts = snap.points(e.id).filter { it.date in from..today }
            if (pts.isEmpty()) return@mapNotNull null
            val bw = snap.spec(e).isBodyweight
            LiftChange(HomeModel.shortName(e.name), e.sort, bw, pts.first().topKg, pts.last().topKg, pts.first().topReps, pts.last().topReps, pts.size) to e
        }
        val body = snap.tables.bodyWeights.filter { it.deletedAt == null }.map { LocalDate.ofEpochDay(it.day) to it.kg }
            .filter { it.first in from..today }.sortedBy { it.first }
        val perWeek = snap.daysPerWeek
        val (bars, title, goalText, goal) = when (range) {
            ProgressRange.Week -> {
                val days = (0..6).map { from.plusDays(it.toLong()) }
                Quad(days.mapIndexed { i, d -> Bar(dates.count { it == d }, if (i == 6) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) }, "Sessions this week", "goal $perWeek", 1)
            }
            ProgressRange.Month -> {
                val counts = TrainingStats.perWindow(dates, today, 4, 7)
                Quad(counts.mapIndexed { i, n -> Bar(n, if (i == 3) "Now" else today.minusDays((3 - i) * 7L + 6).format(dm)) }, "Sessions a week", "goal $perWeek", perWeek)
            }
            ProgressRange.Year -> {
                val counts = TrainingStats.perMonth(dates, today, 12)
                val perMonth = Math.round(perWeek * 52 / 12.0).toInt()
                Quad(counts.mapIndexed { i, n -> Bar(n, if (i == 11) "Now" else YearMonth.from(today).minusMonths(11L - i).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) }, "Sessions a month", "goal $perMonth", perMonth)
            }
        }
        val maxNow = changes.maxOfOrNull { (ch, _) -> if (ch.bodyweight) ch.nowReps.toDouble() else ch.nowKg } ?: 1.0
        val top = changes.take(5).map { (ch, e) ->
            val u = snap.unit
            val fmt = { kg: Double, reps: Int -> if (ch.bodyweight) reps.toString() else app.cove.companion.feature.training.engine.WeightFormat.number(kg, u) }
            val start = fmt(ch.startKg, ch.startReps)
            val now = fmt(ch.nowKg, ch.nowReps)
            TopSetRow(e.id, e.name, start.takeIf { it != now }, now, ((if (ch.bodyweight) ch.nowReps.toDouble() else ch.nowKg) / maxNow).toFloat().coerceIn(0.05f, 1f))
        }
        return ProgressState(range, TrainingStats.headline(range, inRange.size, changes.map { it.first }), inRange.size, body, bars, title, goalText, goal, top)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
