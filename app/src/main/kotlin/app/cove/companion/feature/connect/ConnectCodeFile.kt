package app.cove.companion.feature.connect

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.cove.companion.data.config.SetupCodeExport
import app.cove.companion.data.config.SetupCodeResult
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.feature.me.SheetCaption
import app.cove.companion.feature.me.SheetHeading
import app.cove.companion.feature.plan.PlanSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Largest setup-code file Cove reads; a real one is under 1 KB. */
private const val MAX_CODE_FILE = 64 * 1024

/** Reads at most [limit] bytes (`InputStream.readNBytes` needs Android 13). */
private fun readUpTo(input: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (out.size() < limit) {
        val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** Reads a small text file the user picked; null when it cannot be read or is too large. */
suspend fun readSetupFile(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = readUpTo(input, MAX_CODE_FILE + 1)
            if (bytes.size > MAX_CODE_FILE) null else String(bytes)
        }
    }.getOrNull()
}

/** "Import setup code from a file": picks a saved code file and applies it like a pasted code. */
@Composable
fun ImportCodeFromFileAction(vm: ConnectViewModel, onResult: (SetupCodeResult?) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = readSetupFile(context, uri)
            if (text == null) onResult(null) else {
                val result = vm.applySetupCode(text)
                onResult(result)
                if (result is SetupCodeResult.Parsed) onDone()
            }
        }
    }
    SheetAction("Import setup code from a file", { pick.launch(arrayOf("text/*", "application/octet-stream")) })
}

/** "Copy my setup code" and "Save setup code to a file", behind a plain warning about what the code contains. */
@Composable
fun ShareCodeSheet(vm: ConnectViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val code = remember { vm.setupCode() }
    var message by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null || code == null) return@rememberLauncherForActivityResult
        message = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(SetupCodeExport.fileText(code).toByteArray()) }
        }.fold({ "Saved. Keep that file somewhere private." }, { "Couldn’t save the file. Choose another place, such as Downloads." })
    }
    PlanSheet(onDismiss, gap = 16) {
        SheetHeading("Your setup code")
        SheetCaption(
            "After a reinstall, a new phone or Clear all data, paste this code once to bring back all your connections. " +
                "It contains your keys: anyone who has it can use your Supabase, Google and Gemini accounts. Share it with no one.",
        )
        if (code == null) {
            SheetCaption("Nothing is saved yet. Add a connection first.")
            return@PlanSheet
        }
        SheetAction("Copy my setup code", {
            SetupCodeShare.copy(context, code)
            message = "Copied. Cove clears it from the clipboard after a minute."
        }, primary = true)
        SheetAction("Save setup code to a file", { save.launch("cove-setup-code.txt") })
        message?.let { CoveText(it, style = CoveType.Meta, color = Cove.colors.muted) }
    }
}
