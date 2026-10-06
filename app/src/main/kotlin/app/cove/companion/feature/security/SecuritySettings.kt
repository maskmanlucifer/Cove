package app.cove.companion.feature.security

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cove.companion.data.local.entity.SettingsEntity
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.Segmented
import app.cove.companion.feature.me.RowDivider
import app.cove.companion.feature.me.SettingsGroup
import app.cove.companion.feature.me.SettingsRow
import app.cove.companion.feature.me.SheetCaption
import app.cove.companion.feature.me.SheetHeading
import app.cove.companion.feature.plan.PlanSheet

/** Me rows for the app lock; the extra rows appear once the lock is on. */
@Composable
fun SecurityGroup(
    s: SettingsEntity,
    onLockToggle: (Boolean) -> Unit,
    onLockAfter: () -> Unit,
    onHideInRecents: (Boolean) -> Unit,
) {
    SettingsGroup("Security") {
        SettingsRow("App lock", checked = s.biometricLock, onCheck = onLockToggle)
        if (s.biometricLock) {
            RowDivider()
            SettingsRow("Lock after", value = LockAfter.fromKey(s.lockAfter).label, onClick = onLockAfter)
            RowDivider()
            SettingsRow("Hide in recents", checked = s.hideInRecents, onCheck = onHideInRecents)
        }
    }
}

/** Sheet choosing how long Cove may stay in the background before locking. */
@Composable
fun LockAfterSheet(current: String, onPick: (LockAfter) -> Unit, onDismiss: () -> Unit) {
    PlanSheet(onDismiss, gap = 16) {
        SheetHeading("Lock after")
        Segmented(
            LockAfter.entries.map { it.label },
            LockAfter.entries.indexOf(LockAfter.fromKey(current)),
            { onPick(LockAfter.entries[it]) },
            Modifier.fillMaxWidth(), fillWidth = true,
        )
        SheetCaption("How long Cove can be in the background before it asks you to unlock again. Alarms and reminders still work while it is locked.")
    }
}

/** Explains that the phone needs a screen lock before the app lock can be turned on. */
@Composable
fun LockUnavailableSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    PlanSheet(onDismiss, gap = 16) { close ->
        SheetHeading("Set a screen lock first")
        SheetCaption("App lock uses your fingerprint or your phone’s PIN, pattern or password. Add one in your phone’s security settings, then come back.")
        PillButton(
            "Open security settings",
            { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)); close() },
            Modifier.fillMaxWidth(), height = 52.dp,
        )
    }
}
