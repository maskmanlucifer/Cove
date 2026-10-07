package app.cove.companion.feature.money.imports

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.cove.companion.core.PermissionStep
import app.cove.companion.core.Permissions
import app.cove.companion.core.appViewModel
import app.cove.companion.core.permissionStep
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.data.sms.CaptureMode
import app.cove.companion.feature.money.MoneyTopBar
import app.cove.companion.feature.money.live.PaymentsPermissionGuide
import app.cove.companion.feature.money.live.paymentPermissions
import app.cove.companion.feature.money.live.rememberPaymentsAccess
import app.cove.companion.feature.money.MoneyUndoBar
import app.cove.companion.feature.money.RetroTagBar
import app.cove.companion.feature.permissions.openFix
import app.cove.companion.feature.permissions.FixTarget
import app.cove.companion.navigation.Nav

private const val READ_SMS = Manifest.permission.READ_SMS

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Import from messages (Money): permission and rationale, range choice, scan, review, import and Undo, with a
 * "Paste a message" path that needs no permission. See `docs/SMS_IMPORT.md`.
 */
@Composable
fun MoneyImportScreen(nav: Nav, pendingOnly: Boolean = false) {
    val vm = appViewModel(key = if (pendingOnly) "pending" else null) { ImportViewModel(it, pendingOnly) }
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.granted(context, READ_SMS)) }
    var deniedBefore by rememberSaveable { mutableStateOf(false) }
    var canAskAgain by rememberSaveable { mutableStateOf(true) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val ok = Permissions.granted(context, READ_SMS)
        granted = ok
        if (ok) vm.permissionGranted() else {
            deniedBefore = true
            canAskAgain = context.activity()?.shouldShowRequestPermissionRationale(READ_SMS) ?: false
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = Permissions.granted(context, READ_SMS)
        if (granted && !now) vm.permissionLost()
        granted = now
        if (now) vm.permissionGranted()
    }
    val step = permissionStep(granted, deniedBefore, canAskAgain)
    val access = rememberPaymentsAccess()
    var offerMode by rememberSaveable { mutableStateOf(CaptureMode.Ask) }

    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            MoneyTopBar("Import", "Close", nav.back, nav.back, actionStrong = false)
            when (s.stage) {
                ImportStage.Offer -> OfferStage(offerMode, { offerMode = it }) {
                    vm.finishOffer(offerMode, granted)
                    if (offerMode != CaptureMode.Off && !Permissions.messagesGranted(context)) launcher.launch(paymentPermissions(context, offerMode))
                }
                ImportStage.Intro -> IntroStage(s.message, step, deniedBefore, { launcher.launch(paymentPermissions(context, s.mode)) }, { openFix(context, FixTarget.AppSettings) }, vm::openPaste)
                ImportStage.Range -> RangeStage(s, vm::setRange, vm::scanInbox, vm::openPaste) {
                    PaymentsSection(s.mode, { m -> vm.setMode(m); if (m != CaptureMode.Off && !access.granted) access.askPermissions() }) {
                        PaymentsPermissionGuide(s.mode, access)
                    }
                }
                ImportStage.Scanning -> ScanningStage(s, vm::cancelScan)
                ImportStage.Paste -> PasteStage(s.message, granted, vm::scanPasted) { vm.backToStart(granted) }
                ImportStage.Review, ImportStage.Importing -> ReviewStage(s, vm, importing = s.stage == ImportStage.Importing)
                ImportStage.Done -> DoneStage(s, nav.back)
            }
        }
        RetroTagBar(Modifier.align(Alignment.TopCenter), belowHeader = true)
        MoneyUndoBar(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 24.dp))
    }
}
