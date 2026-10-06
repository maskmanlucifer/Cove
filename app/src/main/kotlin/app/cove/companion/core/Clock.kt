package app.cove.companion.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Time source; replaceable in tests. */
fun interface Clock {
    fun now(): Long

    companion object {
        /** Debug builds may freeze time (epoch millis) so screens can be compared with the design. */
        @Volatile
        var frozenAt: Long? = null

        val System = Clock { frozenAt ?: java.lang.System.currentTimeMillis() }
    }
}

/** New client-side row id. */
fun newId(): String = UUID.randomUUID().toString()

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun LocalDateTime.toEpochMillis(): Long = atZone(zone).toInstant().toEpochMilli()

fun LocalDate.startOfDayMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

/** Epoch day used by habit logs and journal entries. */
fun Long.epochDay(): Long = toLocalDate().toEpochDay()
