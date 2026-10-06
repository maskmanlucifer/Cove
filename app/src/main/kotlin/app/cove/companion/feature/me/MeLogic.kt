package app.cove.companion.feature.me

import app.cove.companion.core.clockText
import app.cove.companion.design.TextScales

/** Stored `theme` values in the order of the Look segmented control. */
val ThemeModes = listOf("system", "light", "dark")

/** Stored `nudgeMode` values in the order of the Nudges segmented control. */
val NudgeModes = listOf("as_they_come", "bundled", "brief_only")

/** Stored `reduceMotion` values in the order of the segmented control. */
val MotionModes = listOf("system", "on", "off")

/** Helper line shown under the Nudges control for each mode. */
fun nudgeHelp(mode: String) = when (mode) {
    "as_they_come" -> "Each nudge arrives when it is due."
    "brief_only" -> "Nothing until your morning brief."
    else -> "Three gentle check-ins a day: 9 am, 1 pm and 6 pm."
}

/** Short label for a stored nudge mode. */
fun nudgeLabel(mode: String) = when (mode) {
    "as_they_come" -> "As they come"
    "brief_only" -> "Brief only"
    else -> "Bundled"
}

/** "6:30 am" for minutes since midnight. */
fun clockLabel(minutes: Int): String = clockText(minutes).let { it.digits + it.suffix }

/** "6:30 am" or "6:30 am · 2 more" for the alarms row; "None" when there are no alarms. */
fun alarmSummary(minutes: List<Int>): String = when {
    minutes.isEmpty() -> "None"
    else -> clockLabel(minutes.first()) + if (minutes.size > 1) " · ${minutes.size - 1} more" else ""
}

/** "System · Default": theme and text size of the Look row. */
fun lookSummary(theme: String, textScale: Float) =
    theme.replaceFirstChar { it.uppercase() } + " · " + TextScales.label(textScale)
