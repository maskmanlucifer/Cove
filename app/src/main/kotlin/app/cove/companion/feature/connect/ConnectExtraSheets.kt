package app.cove.companion.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.data.config.SetupCodeResult
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.plan.PlanSheet
import app.cove.companion.feature.me.SheetCaption
import app.cove.companion.feature.me.SheetHeading

/** "Paste setup code": one block of text fills every field it carries. */
@Composable
fun CodeSheet(vm: ConnectViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    PlanSheet(onDismiss, gap = 16) { close ->
        SheetHeading("Paste setup code")
        SheetCaption("A setup code starts with cove-setup:1: and fills in everything at once. Make one with tools/make-setup-code.py.")
        CredentialInput(
            "Setup code", text, { text = it; error = null },
            { readClipboard(context)?.let { text = it; error = null } },
            placeholder = "cove-setup:1:...", error = error, multiline = true,
        )
        ImportCodeFromFileAction(vm, onResult = { r -> error = (r as? SetupCodeResult.Invalid)?.message ?: if (r == null) "Couldn’t read that file." else null }, onDone = close)
        SheetAction("Fill everything in", {
            when (val r = vm.applySetupCode(text)) {
                is SetupCodeResult.Invalid -> error = r.message
                is SetupCodeResult.Parsed -> close()
            }
        }, primary = true)
    }
}

/** Drive: the identity Google needs for the Android OAuth client, and the consent button. */
@Composable
fun DriveSheet(ui: ConnectUi, vm: ConnectViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info = remember { readSigningInfo(context) }
    ScrollingSheetContent(
        "Google Drive", "Optional. Photos and voice notes go to a Cove folder in your own Drive, and monthly backups too.", onDismiss,
    ) {
        Steps(
            listOf(
                "Turn on the Drive API in Google Cloud (button below).",
                "Create an OAuth client of type Android with the package name and SHA-1 below.",
                "Come back and tap Connect Drive, then approve access.",
            ),
        )
        SheetAction("Open Drive API page", { openLink(context, ConnectLinks.DRIVE_API) })
        SheetAction("Open Google Cloud credentials", { openLink(context, ConnectLinks.GOOGLE_CREDENTIALS) })
        if (info != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CopyRow("Package name", info.packageName)
                CopyRow("SHA-1", info.sha1)
                CopyRow("SHA-256", info.sha256)
            }
            SheetCaption("These belong to the installed build. A debug and a release build have different SHA-1s, so add each one you use.")
        }
        if (!ui.signedIn) SheetCaption("Sign in with Google first. Drive uses the same sign-in.")
        SheetAction("Connect Drive", { vm.test(ServiceId.Drive, emptyMap(), context) }, primary = true)
        ResultLine(ui.tests[ServiceId.Drive], ui.busy == ServiceId.Drive)
        if (ui.driveConnected && ui.signedIn) CoveText("Drive is connected.", style = CoveType.Meta, color = Cove.colors.saved)
    }
}

@Composable
private fun CopyRow(label: String, value: String) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CoveText(label, style = CoveType.MetaMedium, color = Cove.colors.muted)
            Box(Modifier.heightIn(min = 32.dp).pressable({ copyToClipboard(context, label, value) }), contentAlignment = Alignment.Center) {
                CoveText("Copy", style = CoveType.MetaMedium)
            }
        }
        CoveText(value, style = CoveType.Meta.copy(fontSize = 13.sp, lineHeight = 18.sp))
    }
}
