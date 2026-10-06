package app.cove.companion.feature.me

import app.cove.companion.core.clockText
import app.cove.companion.data.auth.AuthState
import app.cove.companion.data.backup.BackupResult
import app.cove.companion.data.media.PhotoQuality
import app.cove.companion.data.sync.SyncStatus
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

/** What the Me "Sync" row and sheet need to render. */
data class SyncUi(val label: String, val signedIn: Boolean, val canSignIn: Boolean, val conflicts: Int)

/** "just now", "2 min ago", "3 h ago", "2 d ago" for an age in milliseconds. */
fun agoText(ageMs: Long): String {
    val min = ageMs / 60_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 24 * 60 -> "${min / 60} h ago"
        else -> "${min / (24 * 60)} d ago"
    }
}

/** Row text for the sync state: Not set up / Not signed in / Syncing / Up to date · 2 min ago / Error. */
fun syncUi(auth: AuthState, status: SyncStatus, conflicts: Int, now: Long): SyncUi {
    val label = when {
        auth is AuthState.Disabled -> "Not set up"
        auth is AuthState.SignedOut -> "Not signed in"
        conflicts > 0 -> if (conflicts == 1) "1 to review" else "$conflicts to review"
        status is SyncStatus.Syncing -> "Syncing"
        status is SyncStatus.Failed -> "Error"
        status is SyncStatus.UpToDate -> "Up to date · ${agoText(now - status.at)}"
        else -> "Up to date"
    }
    return SyncUi(label, auth is AuthState.SignedIn, auth is AuthState.SignedOut, conflicts)
}

/** Row text for "Back up now": "Not set up" without Drive, "Never", or "2 d ago". */
fun backupLabel(driveEnabled: Boolean, lastAt: Long, now: Long): String = when {
    !driveEnabled -> "Not set up"
    lastAt <= 0 -> "Never"
    else -> agoText(now - lastAt)
}

/** User-facing line for the outcome of a backup or restore. */
fun backupMessage(result: BackupResult, restoring: Boolean): String = when (result) {
    is BackupResult.Done -> if (restoring) "Restored ${result.detail}." else "Backed up."
    BackupResult.NeedsConsent -> "Allow Cove to use your Google Drive, then try again."
    BackupResult.Offline -> "Could not reach Google Drive. Try again when you are online."
    BackupResult.NothingToRestore -> "No backup found in your Drive yet."
    BackupResult.NotEmpty -> "Restore only works on a fresh Cove with no entries yet."
    is BackupResult.Failed -> "Something went wrong: ${result.reason}"
}

/** Helper line under the Photo quality control. */
fun photoQualityHelp(value: String) = when (value) {
    PhotoQuality.HIGH -> "Keeps more detail. Photos take about twice the space in your Drive."
    else -> "Small files that still look sharp on your phone. Applies to photos you add from now on."
}
