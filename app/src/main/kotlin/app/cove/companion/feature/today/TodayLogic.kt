package app.cove.companion.feature.today

import app.cove.companion.core.startOfDayMillis
import app.cove.companion.data.local.entity.SettingsEntity
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
