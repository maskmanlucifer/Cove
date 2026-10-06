package app.cove.companion.feature.brief

import android.content.Context
import android.provider.CalendarContract
import app.cove.companion.core.Permissions
import app.cove.companion.core.toLocalDateTime
import java.time.LocalDate
import java.time.ZoneId

/** Reads today's entries of the device calendar; returns nothing when READ_CALENDAR is not granted. */
class CalendarSource(private val context: Context) {
    val granted: Boolean
        get() = Permissions.calendarGranted(context)

    /** Timed events of [day]; all-day entries are skipped. */
    fun eventsOn(day: LocalDate): List<DayItem> {
        if (!granted) return emptyList()
        val zone = ZoneId.systemDefault()
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
            android.content.ContentUris.appendId(it, from)
            android.content.ContentUris.appendId(it, to)
            it.build()
        }
        val out = mutableListOf<DayItem>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION),
                null, null, CalendarContract.Instances.BEGIN,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(2) == 1) continue
                    val t = c.getLong(1).toLocalDateTime()
                    out += DayItem(t.hour * 60 + t.minute, c.getString(0).orEmpty().ifBlank { "Event" }, c.getString(3)?.takeIf { it.isNotBlank() })
                }
            }
        }
        return out
    }
}
