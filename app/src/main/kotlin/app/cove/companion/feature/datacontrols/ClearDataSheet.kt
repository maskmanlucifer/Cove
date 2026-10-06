package app.cove.companion.feature.datacontrols

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.data.wipe.CloudReport
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CheckCircle
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.illustrations.Illustration
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.connect.ShareCodeSheet
import app.cove.companion.feature.me.SheetCaption
import app.cove.companion.feature.me.SheetHeading
import app.cove.companion.feature.plan.PlanSheet
import app.cove.companion.feature.recovery.StartFreshRule

/** Me > "Clear all data": preview, optional backup, optional cloud deletion, then a fresh-install reset. */
@Composable
fun ClearDataSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { ClearDataViewModel(it, context) }
    val ui by vm.ui.collectAsState()
    var showCode by remember { mutableStateOf(false) }
    val busy = ui.phase is ClearPhase.CloudRunning || ui.phase == ClearPhase.Clearing
    PlanSheet({ if (!busy) onDismiss() }, gap = 16) { close ->
        SheetHeading("Clear all data")
        when (val phase = ui.phase) {
            ClearPhase.Confirm -> ConfirmContent(ui, vm, close, onShowCode = { showCode = true })
            is ClearPhase.CloudRunning -> {
                SheetCaption("Deleting your cloud copies. Keep Cove open until this finishes.")
                CoveText(phase.step + "…", style = CoveType.BodyMedium)
                PillButton("Stop", vm::stopCloud, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
            }
            is ClearPhase.CloudDone -> CloudDoneContent(phase.report, vm, close)
            ClearPhase.Clearing -> {
                SheetCaption("Clearing this phone. Cove restarts in a moment.")
                CoveText("Clearing…", style = CoveType.BodyMedium)
            }
            is ClearPhase.Failed -> {
                CoveText(phase.message, style = CoveType.Meta, color = Cove.colors.alert)
                PillButton("Try again", vm::reset, Modifier.fillMaxWidth(), height = 52.dp)
                PillButton("Cancel", close, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
            }
        }
    }
    if (showCode) ShareCodeSheet(app.cove.companion.core.appViewModel { app.cove.companion.feature.connect.ConnectViewModel(it) }) { showCode = false }
}

@Composable
private fun ConfirmContent(ui: ClearUi, vm: ClearDataViewModel, close: () -> Unit, onShowCode: () -> Unit) {
    val context = LocalContext.current
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri -> uri?.let(vm::backUpToFile) }
    SheetCaption("This returns Cove to a fresh install on this phone. It cannot be undone.")
    Column(Modifier.fillMaxWidth().background(Cove.colors.card, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CoveText("What will be deleted", style = CoveType.BodyMedium)
        val preview = ui.preview
        if (preview == null) CoveText("Counting…", style = CoveType.Meta, color = Cove.colors.muted)
        else (preview.sentences() + "Encrypted database (${preview.databaseSize()})").forEach { Bullet(it) }
        Bullet("Settings, alarms, reminders, widgets and background jobs")
        Bullet("Your saved connections, Supabase sign-in and Google Drive permission")
    }
    CoveText("Backup", style = CoveType.BodyMedium)
    PillButton(
        if (ui.backupBusy) "Saving…" else "Save a backup first",
        { if (ui.driveReady) vm.backUpToDrive() else saveFile.launch(vm.backupFileName()) },
        Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp,
    )
    SheetCaption(
        (if (ui.driveReady) "Saves a copy in your Google Drive." else "Saves a readable copy of your entries (not photos) to a file.") + " You can skip this.",
    )
    ui.backupMessage?.let { CoveText(it, style = CoveType.Meta) }
    if (ui.hasConnections) {
        PillButton("Save my setup code first", onShowCode, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
        SheetCaption("Clearing also removes your saved connections. A setup code brings them all back in one paste.")
    }
    if (ui.signedIn) CloudOption(ui, vm) else SheetCaption("Copies in your own Supabase or Google Drive are not touched.")
    HoldToConfirm("Hold to clear all data", ui.canConfirm, vm::confirm)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    PillButton("Cancel", close, Modifier.fillMaxWidth().focusRequester(focus), height = 52.dp)
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoveText("•", style = CoveType.Meta, color = Cove.colors.muted)
        CoveText(text, Modifier.weight(1f), style = CoveType.Meta)
    }
}

@Composable
private fun CloudOption(ui: ClearUi, vm: ClearDataViewModel) {
    val c = Cove.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .pressable({ vm.setAlsoCloud(!ui.alsoCloud) }, role = Role.Checkbox)
            .semantics(mergeDescendants = true) { stateDescription = if (ui.alsoCloud) "Checked" else "Not checked" },
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(ui.alsoCloud, null)
        CoveText("Also delete my cloud copies", Modifier.weight(1f), style = CoveType.BodyMedium)
    }
    if (!ui.alsoCloud) {
        SheetCaption("Unchecked, your copies in Supabase and Google Drive stay as they are.")
        return
    }
    CoveText(
        "This permanently deletes your rows in Supabase, your thumbnails, and the files Cove created in your Google Drive. " +
            "It happens first, so you can see what was deleted before this phone is cleared.",
        style = CoveType.Meta.copy(lineHeight = androidx.compose.ui.unit.TextUnit(21f, androidx.compose.ui.unit.TextUnitType.Sp)), color = c.alert,
    )
    CoveText("Type ${StartFreshRule.WORD} to confirm", style = CoveType.Meta, color = c.muted)
    BasicTextField(
        ui.typed, vm::type, singleLine = true,
        textStyle = CoveType.Body.copy(color = c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(c.well, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { inner() }
        },
    )
}

@Composable
private fun CloudDoneContent(report: CloudReport, vm: ClearDataViewModel, close: () -> Unit) {
    if (report.ok) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Illustration(Scene.Cleared, Modifier.height(110.dp)) }
    SheetCaption(if (report.ok) "Your cloud copies are deleted. This is exactly what was removed:" else "Some cloud copies are not deleted. This is exactly what happened:")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        report.lines().forEach { CoveText(it, style = CoveType.Meta) }
    }
    if (report.ok) {
        PillButton("Continue and clear this phone", vm::continueToPhone, Modifier.fillMaxWidth(), height = 52.dp)
    } else {
        PillButton("Try again", vm::retryCloud, Modifier.fillMaxWidth(), height = 52.dp)
        PillButton("Clear this phone anyway", vm::continueToPhone, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
    }
    PillButton("Cancel", close, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
}
