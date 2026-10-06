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
data class SyncUi(val label: String, val signedIn: Boolean, val canSignIn: Boolean, val conflicts: Int, val problem: SyncProblem? = null)

/** Next step offered under a sync problem. */
enum class SyncAction(val label: String) { Retry("Try again"), OpenConnect("Open Connect services") }

/** A sync failure in plain words with the one thing the user can do about it. */
data class SyncProblem(val title: String, val body: String, val action: SyncAction)

/** Turns the raw sync failure [message] into a [SyncProblem]; the raw text itself is never shown. */
fun syncProblem(message: String): SyncProblem {
    val m = message.lowercase()
    fun has(vararg parts: String) = parts.any { it in m }
    return when {
        has("signed out", "401", "jwt", "expired") ->
            SyncProblem("Sync is paused", "You were signed out. Sign in again to keep your devices in step.", SyncAction.OpenConnect)
        has("403", "api key", "apikey", "rejected", "invalid key") ->
            SyncProblem("Sync is paused", "Your database did not accept the saved key. Check it in Connect services.", SyncAction.OpenConnect)
        has("404", "pgrst", "relation", "does not exist", "schema") ->
            SyncProblem("Sync is paused", "Your database is missing Cove’s tables. Run the setup in Connect services.", SyncAction.OpenConnect)
        has("resolve", "unknownhost", "timeout", "timed out", "connect", "network", "unreachable", "offline") ->
            SyncProblem("Sync is paused", "Cove can’t reach your database right now. It keeps trying; you can also try now.", SyncAction.Retry)
        else -> SyncProblem("Sync is paused", "Something stopped the last sync. Try again in a moment.", SyncAction.Retry)
    }
}

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

/** Row text for the sync state: Not set up / Not signed in / Syncing / Up to date · 2 min ago / Paused. */
fun syncUi(auth: AuthState, status: SyncStatus, conflicts: Int, now: Long): SyncUi {
    val label = when {
        auth is AuthState.Disabled -> "Not set up"
        auth is AuthState.SignedOut -> "Not signed in"
        conflicts > 0 -> if (conflicts == 1) "1 to review" else "$conflicts to review"
        status is SyncStatus.Syncing -> "Syncing"
        status is SyncStatus.Failed -> "Paused"
        status is SyncStatus.UpToDate -> "Up to date · ${agoText(now - status.at)}"
        else -> "Up to date"
    }
    val problem = (status as? SyncStatus.Failed)?.takeIf { auth is AuthState.SignedIn }?.let { syncProblem(it.message) }
    return SyncUi(label, auth is AuthState.SignedIn, auth is AuthState.SignedOut, conflicts, problem)
}

/** Row text for "Back up now": "Not set up" without Drive, "Never", or "2 d ago". */
fun backupLabel(driveEnabled: Boolean, lastAt: Long, now: Long): String = when {
    !driveEnabled -> "Not set up"
    lastAt <= 0 -> "Never"
    else -> agoText(now - lastAt)
}

/** Message of the backup sheet when Drive is not set up or signed in. */
const val BackupNotSignedIn = "Sign in with Google in Connect services to use Drive backups."

/** User-facing line for the outcome of a backup or restore; never shows raw error text. */
fun backupMessage(result: BackupResult, restoring: Boolean): String = when (result) {
    is BackupResult.Done -> if (restoring) "Restored ${result.detail}." else "Backed up."
    BackupResult.NeedsConsent -> "Allow Cove to use your Google Drive, then try again."
    BackupResult.Offline -> "Could not reach Google Drive. Try again when you are online."
    BackupResult.NothingToRestore -> "No backup found in your Drive yet."
    BackupResult.NotEmpty -> "Restore only works before you add your own entries. Your alarms, to-dos and journal are already here."
    is BackupResult.Failed -> if (restoring) "The restore did not finish. Nothing was lost; try again in a moment." else "The backup did not finish. Try again in a moment."
}

/** Whether the backup sheet should offer "Open Connect services" for [result] instead of only a message. */
fun backupNeedsConnect(result: BackupResult) = result == BackupResult.NeedsConsent

/** Helper line under the Photo quality control. */
fun photoQualityHelp(value: String) = when (value) {
    PhotoQuality.HIGH -> "Keeps more detail. Photos take about twice the space in your Drive."
    else -> "Small files that still look sharp on your phone. Applies to photos you add from now on."
}

/** "On" or "Off", the value shown on toggle rows. */
fun onOff(on: Boolean) = if (on) "On" else "Off"
