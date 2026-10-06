package app.cove.companion.feature.me

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.design.components.PillButton
import app.cove.companion.feature.plan.PlanSheet

/** Confirmation sheet for "Back up now" ([restore] false) and "Restore from backup" ([restore] true). */
@Composable
fun BackupSheet(restore: Boolean, vm: MeViewModel, onDismiss: () -> Unit) {
    val ui by vm.backup.collectAsState()
    PlanSheet(onDismiss, gap = 16) {
        SheetHeading(if (restore) "Restore from backup" else "Back up now")
        SheetCaption(
            if (restore) "Brings back everything from your latest backup in Google Drive. Works on a fresh Cove with no entries yet."
            else "Saves a copy of your data in the Cove folder on your Google Drive, plus your journal as readable text. Cove keeps the last 12 months.",
        )
        ui.message?.let { SheetCaption(it) }
        PillButton(
            when {
                ui.busy -> if (restore) "Restoring…" else "Backing up…"
                restore -> "Restore"
                else -> "Back up now"
            },
            { if (restore) vm.restore() else vm.backUp() },
            Modifier.fillMaxWidth(), height = 52.dp,
        )
    }
}
