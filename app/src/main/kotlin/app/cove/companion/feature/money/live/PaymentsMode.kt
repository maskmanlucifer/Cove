package app.cove.companion.feature.money.live

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.cove.companion.core.Permissions
import app.cove.companion.data.sms.CaptureMode
import app.cove.companion.design.Cove
import app.cove.companion.design.components.CheckCircle
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.GuideBanner
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.money.MoneyType
import app.cove.companion.feature.money.RowDivider
import app.cove.companion.feature.money.RowsCard
import app.cove.companion.feature.permissions.FixTarget
import app.cove.companion.feature.permissions.openFix

/**
 * Permissions to ask for in one go: reading messages always; receiving them and (where Android asks) notifications only
 * when [mode] is on, so someone who only imports by hand is not asked for more.
 */
fun paymentPermissions(context: android.content.Context, mode: CaptureMode): Array<String> = buildList {
    add(Manifest.permission.READ_SMS)
    if (mode != CaptureMode.Off) {
        add(Manifest.permission.RECEIVE_SMS)
        if (Permissions.needsNotificationRequest && !Permissions.notificationsAllowed(context)) add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

/**
 * What the "Payments from messages" controls need to work: the current permission state and a function that asks for
 * the missing permissions. Re-read on every resume, because the user may have just fixed it in Settings.
 */
class PaymentsAccess(val granted: Boolean, val askPermissions: () -> Unit)

/** Remembers [PaymentsAccess]; [onResult] runs after the system dialog closes. */
@Composable
fun rememberPaymentsAccess(onResult: (granted: Boolean) -> Unit = {}): PaymentsAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.messagesGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = Permissions.messagesGranted(context)
        onResult(granted)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = Permissions.messagesGranted(context) }
    return PaymentsAccess(granted) { launcher.launch(paymentPermissions(context, CaptureMode.Ask)) }
}

/** The three modes, each with one plain sentence; one is selected. Used on the import screen and in Me. */
@Composable
fun PaymentsModePicker(mode: CaptureMode, onMode: (CaptureMode) -> Unit, modifier: Modifier = Modifier) {
    RowsCard(modifier) {
        CaptureMode.entries.forEachIndexed { i, m ->
            if (i > 0) RowDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable({ onMode(m) }, role = Role.RadioButton)
                    .semantics(mergeDescendants = true) { selected = mode == m; contentDescription = "${m.label}. ${m.blurb}" }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CheckCircle(mode == m, null, size = 24)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CoveText(m.label, style = MoneyType.Row)
                    CoveText(m.blurb, style = MoneyType.Small, color = Cove.colors.muted)
                }
            }
        }
    }
}

/** The privacy promise, in the words the whole app uses. */
@Composable
fun PaymentsPrivacyNote(modifier: Modifier = Modifier) {
    CoveText(PaymentTexts.PRIVACY, modifier, style = MoneyType.Note, color = Cove.colors.muted)
}

/** The guide for a missing permission, shown only when capture is on and something is not allowed. */
@Composable
fun PaymentsPermissionGuide(mode: CaptureMode, access: PaymentsAccess, modifier: Modifier = Modifier) {
    if (mode == CaptureMode.Off || access.granted) return
    val context = LocalContext.current
    GuideBanner(
        PaymentTexts.GUIDE_TITLE, PaymentTexts.GUIDE_BODY, "Open settings", { openFix(context, FixTarget.AppSettings) }, modifier,
    )
}
