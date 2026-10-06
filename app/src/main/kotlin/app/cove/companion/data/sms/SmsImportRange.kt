package app.cove.companion.data.sms

import java.time.Instant
import java.time.ZoneId

/** How far back "Import from messages" looks. */
enum class ImportRange(val label: String) {
    SinceLast("Since last import"),
    Last30("Last 30 days"),
    ThisMonth("This month"),
    All("All available");

    companion object {
        /**
         * Start of [range] in epoch millis. [SinceLast] begins just after the newest message ever decided, and falls
         * back to 30 days when nothing has been imported yet.
         */
        fun start(range: ImportRange, now: Long, lastDecidedAt: Long?, zone: ZoneId = ZoneId.systemDefault()): Long = when (range) {
            SinceLast -> lastDecidedAt?.plus(1) ?: start(Last30, now, null, zone)
            Last30 -> now - 30L * 24 * 3_600_000
            ThisMonth -> Instant.ofEpochMilli(now).atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            All -> 0L
        }
    }
}
