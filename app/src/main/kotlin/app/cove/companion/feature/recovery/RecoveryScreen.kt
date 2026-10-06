package app.cove.companion.feature.recovery

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.cove.companion.data.backup.BackupFormatException
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveTheme
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.resilience.CrashHandler
import app.cove.companion.resilience.RecoveryReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Mode { Main, ConfirmFresh, ConfirmRestore }

/**
 * Shown instead of the app when it cannot start safely. Works with no database, no key and no network: it only
 * reads plain files and uses the system pickers, so the user always has a way forward.
 */
@Composable
fun RecoveryScreen(reason: RecoveryReason) {
    CoveTheme(isSystemInDarkTheme()) {
        RecoveryContent(reason)
    }
}

@Composable
private fun RecoveryContent(reason: RecoveryReason) {
    val context = LocalContext.current
    val actions = remember { RecoveryActions(context) }
    val scope = rememberCoroutineScope()
    val copy = recoveryCopy(reason)
    val c = Cove.colors

    var mode by rememberSaveable { mutableStateOf(Mode.Main) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var copySaved by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var backupBytes by remember { mutableStateOf<ByteArray?>(null) }
    var backupItems by rememberSaveable { mutableStateOf(0) }
    val details = remember(reason) { technicalDetails(context, reason) }

    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { actions.writeCopy(it) } }
                    .onFailure { CrashHandler.report("recovery-copy", it) }.isSuccess
            }
            copySaved = copySaved || ok
            message = if (ok) "Saved. Keep that file somewhere safe." else "Couldn’t save the copy. Choose another place, such as Downloads or Drive."
            busy = false
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    bytes to actions.readBackup(bytes.inputStream()).tables.values.sumOf { it.size }
                }
            }
            result.onSuccess { (bytes, items) ->
                backupBytes = bytes
                backupItems = items
                mode = Mode.ConfirmRestore
                message = null
            }.onFailure {
                message = if (it is BackupFormatException) "That file isn’t a Cove backup. Pick a file like cove-2026-07.json.gz." else "Couldn’t read that file."
            }
            busy = false
        }
    }

    fun act(action: RecoveryAction) {
        when (action) {
            RecoveryAction.TryAgain -> {
                CrashHandler.storeFor(context).clear()
                actions.restart()
            }
            RecoveryAction.SaveCopy -> saveCopy.launch("cove-data-copy.zip")
            RecoveryAction.Restore -> pickBackup.launch(arrayOf("*/*"))
            RecoveryAction.StartFresh -> { typed = ""; mode = Mode.ConfirmFresh }
            RecoveryAction.OpenStorage -> openStorageSettings(context)
        }
    }

    CoveScreen {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).coveTopInset()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (mode) {
                Mode.Main -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoveText(copy.title, style = CoveType.Title)
                        CoveText(copy.body, style = CoveType.Body, color = c.muted)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        copy.actions.filter { it != RecoveryAction.StartFresh }.forEachIndexed { i, a ->
                            PillButton(
                                actionLabel(a), { act(a) }, Modifier.fillMaxWidth(),
                                kind = if (i == 0) ButtonKind.Primary else ButtonKind.Secondary, height = 52.dp,
                            )
                            if (a == RecoveryAction.SaveCopy && copy.copyNote != null) {
                                CoveText(copy.copyNote, style = CoveType.Meta, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    }
                    message?.let { CoveText(it, style = CoveType.Meta, color = c.ink) }
                    if (busy) CoveText("Working…", style = CoveType.Meta, color = c.muted)
                    Details(details, showDetails, { showDetails = !showDetails }, context)
                    if (RecoveryAction.StartFresh in copy.actions) {
                        PillButton("Start fresh", { act(RecoveryAction.StartFresh) }, Modifier.fillMaxWidth(), kind = ButtonKind.Destructive, height = 48.dp)
                    }
                }
                Mode.ConfirmFresh -> StartFresh(
                    copySaved = copySaved, typed = typed, onTyped = { typed = it },
                    onSaveFirst = { saveCopy.launch("cove-data-copy.zip") },
                    onCancel = { mode = Mode.Main },
                    onConfirm = {
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) { actions.wipe() }
                            actions.restart()
                        }
                    },
                    busy = busy, message = message,
                )
                Mode.ConfirmRestore -> RestoreConfirm(
                    items = backupItems, busy = busy,
                    onCancel = { mode = Mode.Main; backupBytes = null },
                    onConfirm = {
                        val bytes = backupBytes ?: return@RestoreConfirm
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) { actions.stageRestore(bytes) }
                            actions.restart()
                        }
                    },
                )
            }
        }
    }
}

private fun actionLabel(a: RecoveryAction) = when (a) {
    RecoveryAction.TryAgain -> "Try again"
    RecoveryAction.SaveCopy -> "Save a copy of my data"
    RecoveryAction.Restore -> "Restore from a backup"
    RecoveryAction.StartFresh -> "Start fresh"
    RecoveryAction.OpenStorage -> "Open storage settings"
}

private fun technicalDetails(context: Context, reason: RecoveryReason): String {
    val note = CrashHandler.storeFor(context).last()
    return "Reason: $reason\n" + (note?.toReadableText() ?: "No error was recorded.")
}

private fun openStorageSettings(context: Context) {
    val tries = listOf(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, Settings.ACTION_SETTINGS)
    for (action in tries) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

@Composable
private fun Details(text: String, open: Boolean, onToggle: () -> Unit, context: Context) {
    val c = Cove.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable(onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CoveText("Technical details", style = CoveType.MetaMedium, color = c.muted)
            CoveIcon(if (open) CoveIcons.ChevronDown else CoveIcons.ChevronRight, c.muted, size = 14.dp)
        }
        if (open) {
            Box(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(20.dp)).padding(16.dp)) {
                CoveText(text, style = CoveType.Meta, color = c.muted)
            }
            PillButton("Copy details", {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Cove details", text))
            }, kind = ButtonKind.Secondary, height = 44.dp)
        }
    }
}

@Composable
private fun StartFresh(
    copySaved: Boolean, typed: String, onTyped: (String) -> Unit, onSaveFirst: () -> Unit,
    onCancel: () -> Unit, onConfirm: () -> Unit, busy: Boolean, message: String?,
) {
    val c = Cove.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CoveText("Start fresh?", style = CoveType.Title)
        CoveText(
            "This deletes everything Cove keeps on this phone: to-dos, alarms, money, journal and photos that are only here. " +
                "Anything backed up to Google Drive or synced to your own database is not touched.",
            style = CoveType.Body, color = c.muted,
        )
    }
    if (!copySaved) {
        Column(
            Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoveText("You haven’t saved a copy yet. Once deleted, this data can’t be brought back from this phone.", style = CoveType.Meta, color = c.muted)
            PillButton("Save a copy first", onSaveFirst, kind = ButtonKind.Secondary, height = 44.dp)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CoveText("Type ${StartFreshRule.WORD} to confirm", style = CoveType.Meta, color = c.muted)
        BasicTextField(
            typed, onTyped, singleLine = true,
            textStyle = CoveType.Body.copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(c.card, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { inner() }
            },
        )
    }
    message?.let { CoveText(it, style = CoveType.Meta) }
    val ok = StartFreshRule.confirmed(typed) && !busy
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(
            if (busy) "Deleting…" else "Delete everything", { if (ok) onConfirm() }, Modifier.fillMaxWidth(),
            kind = ButtonKind.Secondary, height = 52.dp, textStyle = if (ok) CoveType.Button.copy(color = c.alert) else CoveType.Button.copy(color = c.tail),
        )
        PillButton("Cancel", onCancel, Modifier.fillMaxWidth(), height = 52.dp)
    }
}

@Composable
private fun RestoreConfirm(items: Int, busy: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = Cove.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CoveText("Restore this backup?", style = CoveType.Title)
        CoveText(
            "This backup holds $items items. Cove will set the data it can’t open aside (it is not deleted), then load the backup into a fresh start.",
            style = CoveType.Body, color = c.muted,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(if (busy) "Restoring…" else "Restore", { if (!busy) onConfirm() }, Modifier.fillMaxWidth(), height = 52.dp)
        PillButton("Cancel", onCancel, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
    }
}
