package app.cove.companion.feature.recovery

import app.cove.companion.resilience.RecoveryReason

/** What the Recovery screen can offer. */
enum class RecoveryAction { TryAgain, SaveCopy, Restore, StartFresh, OpenStorage }

/** Plain-language explanation and next steps for one [RecoveryReason]. */
data class RecoveryCopy(
    val title: String,
    val body: String,
    val actions: List<RecoveryAction>,
    /** Extra line under "Save a copy" when the copy is not directly readable. */
    val copyNote: String? = null,
)

/** Copy per failure class. No exception names or codes: those live under "Technical details". */
fun recoveryCopy(reason: RecoveryReason): RecoveryCopy = when (reason) {
    RecoveryReason.KeyMissing -> RecoveryCopy(
        "Cove can’t unlock your data",
        "The key that opens your data on this phone is gone. This can happen after a phone transfer or a security reset. Nothing was deleted. Anything you backed up to Google Drive or synced is safe.",
        listOf(RecoveryAction.Restore, RecoveryAction.SaveCopy, RecoveryAction.StartFresh),
        "The copy stays locked: it can’t be opened without the missing key, but nothing is lost by keeping it.",
    )
    RecoveryReason.KeyInvalid -> RecoveryCopy(
        "Cove can’t unlock your data",
        "The key file on this phone is damaged, so your data can’t be opened. Nothing was deleted. Anything you backed up to Google Drive or synced is safe.",
        listOf(RecoveryAction.TryAgain, RecoveryAction.Restore, RecoveryAction.SaveCopy, RecoveryAction.StartFresh),
        "The copy stays locked: it can’t be opened without the key, but nothing is lost by keeping it.",
    )
    RecoveryReason.Corrupt -> RecoveryCopy(
        "Cove couldn’t open your data",
        "The file on this phone looks damaged. Nothing was deleted yet. Try again first; if that doesn’t help, save a copy before doing anything else.",
        listOf(RecoveryAction.TryAgain, RecoveryAction.SaveCopy, RecoveryAction.Restore, RecoveryAction.StartFresh),
        "The copy is encrypted. Keep it in case it can be repaired later.",
    )
    RecoveryReason.MigrationFailed -> RecoveryCopy(
        "Cove couldn’t finish updating",
        "Your data is untouched. Try again. If it keeps happening, save a copy of your data and use a backup.",
        listOf(RecoveryAction.TryAgain, RecoveryAction.SaveCopy, RecoveryAction.Restore, RecoveryAction.StartFresh),
        "The copy is encrypted. Keep it in case it can be repaired later.",
    )
    RecoveryReason.StorageFull -> RecoveryCopy(
        "Your phone is out of space",
        "Cove needs a little room to open. Free up some storage, then try again. Nothing was deleted.",
        listOf(RecoveryAction.OpenStorage, RecoveryAction.TryAgain, RecoveryAction.SaveCopy),
    )
    RecoveryReason.Unknown -> RecoveryCopy(
        "Something stopped Cove from opening",
        "Your data hasn’t been touched. Try again. If it keeps happening, save a copy and share the technical details.",
        listOf(RecoveryAction.TryAgain, RecoveryAction.SaveCopy, RecoveryAction.Restore, RecoveryAction.StartFresh),
        "The copy is encrypted. Keep it in case it can be repaired later.",
    )
    RecoveryReason.CrashLoop -> RecoveryCopy(
        "Cove had trouble starting",
        "It closed unexpectedly a few times in a row, so Cove paused its background work. Your data is safe. Try again; if it keeps happening, save a copy and share the technical details.",
        listOf(RecoveryAction.TryAgain, RecoveryAction.SaveCopy, RecoveryAction.StartFresh),
        "The copy is encrypted. Keep it in case it can be repaired later.",
    )
}

/**
 * Whether "Start fresh" may be confirmed: [typed] must read exactly [WORD] (ignoring case and spaces).
 */
object StartFreshRule {
    const val WORD = "DELETE"

    /** True when [typed] matches [WORD]. */
    fun confirmed(typed: String): Boolean = typed.trim().equals(WORD, ignoreCase = true)
}
