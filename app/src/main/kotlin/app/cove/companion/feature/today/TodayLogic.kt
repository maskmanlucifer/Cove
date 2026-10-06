package app.cove.companion.feature.today

import app.cove.companion.core.startOfDayMillis
import app.cove.companion.data.local.entity.SettingsEntity
import app.cove.companion.data.local.entity.TodoEntity
import java.time.Instant
import java.time.ZoneId

/** How long "Later" hides the Next card. */
const val CARD_SNOOZE_MINUTES = 30

/** Epoch millis [minutes] after [now]. */
fun snoozeUntil(now: Long, minutes: Int = CARD_SNOOZE_MINUTES): Long = now + minutes * 60_000L

/** Start of the day after [now]'s day: "Start now" hides the wind-down card until then ("for tonight"). */
fun tonightEnd(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).startOfDayMillis()

/** True while the Next card is hidden by "Later" or "Start now". */
fun cardHidden(now: Long, hiddenUntil: Long): Boolean = now < hiddenUntil

/** One-thing mode is on, and its timed end (if any) has not passed. */
fun oneThingActive(s: SettingsEntity, now: Long): Boolean = s.oneThingMode && (s.oneThingUntil == 0L || now < s.oneThingUntil)

/** The spoken line for "Start now". */
fun windDownLine(wakeText: String?): String =
    if (wakeText != null) "Winding down. I'll keep things quiet until your $wakeText alarm." else "Winding down. I'll keep things quiet tonight."

/** The to-dos Today lists: at most [limit] rows plus how many open ones did not fit. */
data class TodayRows(val rows: List<TodoEntity>, val openCount: Int) {
    /** Open to-dos beyond the rows shown. */
    val more: Int get() = (openCount - rows.count { !it.done }).coerceAtLeast(0)
}

/**
 * Picks Today's to-do rows. Done to-dos are listed only when [showDone] (evening recap) or while in
 * [pinned] (just ticked: id to the row index it had, so it stays put long enough to Undo).
 * [TodayRows.openCount] counts every open to-do for today, not only the rows shown.
 */
fun todayRows(todos: List<TodoEntity>, newIds: Set<String>, pinned: Map<String, Int>, showDone: Boolean, dayStart: Long, dayEnd: Long, limit: Int = 3): TodayRows {
    val inScope = todos.filter { it.dueAt == null || it.dueAt in dayStart..dayEnd || it.done }
    val rows = inScope
        .filter { it.id !in pinned && (!it.done || showDone) }
        .sortedBy { it.id !in newIds }
        .toMutableList()
    inScope.filter { it.done && it.id in pinned }.sortedBy { pinned.getValue(it.id) }
        .forEach { rows.add(pinned.getValue(it.id).coerceIn(0, rows.size), it) }
    return TodayRows(rows.take(limit), inScope.count { !it.done })
}
