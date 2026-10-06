package app.cove.companion.feature.connect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.ai.AiStatus
import app.cove.companion.data.config.TestResult
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.pressable

/** Text on the system clipboard, or null when empty. */
fun readClipboard(context: Context): String? =
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }

/** Puts [text] on the clipboard; [secret] hides the preview Android shows. */
fun copyToClipboard(context: Context, label: String, text: String, secret: Boolean = false) {
    val clip = ClipData.newPlainText(label, text)
    if (secret) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

/** Opens [url] in the browser; does nothing when no browser is installed. */
fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * A labelled input in a soft well with a Paste button; secret values are masked until "Show" is tapped.
 * [error] is a friendly message shown underneath.
 */
@Composable
fun CredentialInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    secret: Boolean = false,
    error: String? = null,
    multiline: Boolean = false,
) {
    val c = Cove.colors
    var reveal by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CoveText(label, style = CoveType.MetaMedium, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (secret && value.isNotEmpty()) SmallAction(if (reveal) "Hide" else "Show") { reveal = !reveal }
                SmallAction("Paste", onPaste)
            }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).background(c.well, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) CoveText(placeholder, style = INPUT_STYLE, color = c.placeholder, maxLines = 1)
            BasicTextField(
                value, onChange,
                Modifier.fillMaxWidth(),
                textStyle = INPUT_STYLE.copy(color = c.ink),
                singleLine = !multiline,
                minLines = if (multiline) 4 else 1,
                maxLines = if (multiline) 6 else 1,
                cursorBrush = SolidColor(c.ink),
                visualTransformation = if (secret && !reveal) PasswordVisualTransformation('•') else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri,
                ),
            )
        }
        if (error != null) CoveText(error, style = CoveType.Meta.copy(lineHeight = 19.sp), color = c.alert)
    }
}

private val INPUT_STYLE = CoveType.Meta.copy(fontSize = 15.sp, lineHeight = 20.sp)

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 32.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        CoveText(text, style = CoveType.MetaMedium, color = Cove.colors.ink)
    }
}

/** Short numbered how-to. */
@Composable
fun Steps(steps: List<String>) {
    val c = Cove.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, text ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(22.dp).clip(CoveShapes.Circle).background(c.well), contentAlignment = Alignment.Center) {
                    CoveText("${i + 1}", style = CoveType.Label, color = c.muted)
                }
                CoveText(text, Modifier.weight(1f), style = CoveType.Meta.copy(lineHeight = 20.sp))
            }
        }
    }
}

/** A pill button for opening a dashboard or copying text. */
@Composable
fun SheetAction(text: String, onClick: () -> Unit, primary: Boolean = false) {
    PillButton(
        text, onClick, Modifier.fillMaxWidth(), kind = if (primary) ButtonKind.Primary else ButtonKind.Secondary, height = 48.dp,
    )
}

/** Inline result of a test: green or orange dot, plain message. */
@Composable
fun ResultLine(result: TestResult?, busy: Boolean) {
    val c = Cove.colors
    when {
        busy -> CoveText("Checking...", style = CoveType.Meta, color = c.muted)
        result != null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CoveShapes.Circle).background(if (result.ok) c.saved else c.alert))
            CoveText(result.message, Modifier.weight(1f), style = CoveType.Meta.copy(lineHeight = 20.sp, fontWeight = FontWeight.Normal))
        }
    }
}

/** Where AI runs for this phone: "On this phone" (Gemini Nano) and "Cloud" (the key above), from [AiService.status]. */
@Composable
fun AiStatusLines(status: AiStatus) {
    AiStatusLine("On this phone", status.onDeviceReady, if (status.onDeviceReady) "AI available" else status.onDevice.replaceFirstChar(Char::uppercase))
    AiStatusLine("Cloud", status.cloudReady, if (status.cloudReady) "Key set" else status.cloud.replaceFirstChar(Char::uppercase))
}

@Composable
private fun AiStatusLine(label: String, ready: Boolean, value: String) {
    val c = Cove.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CoveShapes.Circle).background(if (ready) c.saved else c.tail))
        CoveText("$label: $value", Modifier.weight(1f), style = CoveType.Meta.copy(lineHeight = 20.sp, fontWeight = FontWeight.Normal))
    }
}
