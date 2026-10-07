package app.cove.companion.feature.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.cove.companion.core.OneShot
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.UndoHost
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav
import kotlinx.coroutines.launch

internal val BodyStyle = CoveType.Body.copy(fontSize = 18.sp, lineHeight = 29.sp)

/** Journal entry editor: date and mood, title, body with autosave, and photo / voice note attachments. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun JournalEditScreen(id: String, nav: Nav) {
    val vm = appViewModel(key = "journal-edit-$id") { JournalEditViewModel(it, id) }
    val s by vm.state.collectAsState()
    val c = Cove.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val titleFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val drag = remember { BlockDrag() }
    val leaveGuard = remember { OneShot() }
    val deleteGuard = remember { OneShot() }
    var picking by remember { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }
    var micHelp by remember { mutableStateOf(false) }
    var micCanAsk by remember { mutableStateOf(true) }
    val leave: () -> Unit = {
        leaveGuard.launch(scope) {
            if (vm.finish()) Toast.makeText(context, "Voice note kept", Toast.LENGTH_SHORT).show()
            nav.back()
            true
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picking = false; it?.let(vm::addPhoto) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        picking = false
        val target = vm.cameraTarget
        if (ok && target != null) vm.addPhoto(target.second) else target?.first?.delete()
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micHelp = false
            vm.startRecording()
        } else {
            // Once Android stops showing its prompt, only the Settings page can grant the permission.
            val activity = context.findActivity()
            micCanAsk = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            micHelp = true
        }
    }

    BackHandler(onBack = leave)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { scope.launch { vm.save() } }
    LaunchedEffect(s.loaded) {
        if (s.loaded && !s.persisted && vm.title.text.isEmpty() && vm.doc.isEmpty) titleFocus.requestFocus()
    }

    Box(Modifier.fillMaxSize().background(paper()).imePadding()) {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            TopBar(s.status, canDelete = s.persisted, onBack = leave, onDone = leave, onDelete = { sheet = "delete" })
            DocumentList(
                vm, s, drag, listState, Modifier.weight(1f),
                titleFocus = titleFocus,
                onOpenPhoto = { viewing = it },
                scope = scope,
            )
            s.notice?.let {
                CoveText(
                    it, Modifier.fillMaxWidth().padding(horizontal = 28.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = CoveType.Meta, color = c.muted, textAlign = TextAlign.Center,
                )
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = if (WindowInsets.isImeVisible) 12.dp else 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                val recording = s.recordingMs
                if (recording != null) {
                    RecordingBar(recording, vm::stopRecording)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AttachChip("Photo") { sheet = "photo" }
                        AttachChip("Voice note") {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                vm.startRecording()
                            } else {
                                mic.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        AttachChip("Mood") { sheet = "mood" }
                    }
                }
            }
        }
        PhotoSheet(
            sheet == "photo", { sheet = null },
            onLibrary = {
                sheet = null
                if (picking) return@PhotoSheet
                picking = true
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = {
                sheet = null
                if (picking) return@PhotoSheet
                picking = true
                try {
                    camera.launch(vm.newCameraUri())
                } catch (e: ActivityNotFoundException) {
                    picking = false
                    vm.cameraUnavailable()
                }
            },
        )
        MoodSheet(sheet == "mood", s.mood, onDismiss = { sheet = null }, onPick = { vm.setMood(it); sheet = null })
        DeleteEntrySheet(
            sheet == "delete", onDismiss = { sheet = null },
            onDelete = {
                sheet = null
                deleteGuard.launch(scope) { vm.delete(); nav.back(); true }
            },
        )
        MicHelpSheet(
            micHelp, micCanAsk, onDismiss = { micHelp = false },
            onAllow = { mic.launch(Manifest.permission.RECORD_AUDIO) },
            onSettings = {
                micHelp = false
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
        )
        viewing?.let { start ->
            PhotoViewer(
                vm.doc.photoRows(s.media), start, onClose = { viewing = null },
                onRemove = { viewing = null; vm.removeBlock(it.id) },
            )
        }
        UndoHost("journal", Modifier.align(Alignment.TopCenter))
    }
}

/**
 * Makes a single blank line (a paragraph break, exactly two newlines) 16 dp tall instead of a full text line, as in frame 08.
 * The "\n\n" run becomes its own paragraph of three empty lines, so each is a third of 16 dp. Longer runs stay plain.
 */
private val BlankLineGap = OutputTransformation {
    val text = asCharSequence()
    var i = 0
    while (i < text.length) {
        if (text[i] != '\n') { i++; continue }
        var end = i
        while (end < text.length && text[end] == '\n') end++
        if (end - i == 2 && i > 0 && end < text.length) {
            addStyle(SpanStyle(fontSize = 4.sp), i, end)
            addStyle(ParagraphStyle(lineHeight = (16f / 3f).sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)), i, end)
        }
        i = end
    }
}

/** Entry background: a touch lighter than the canvas, as in the design (#FBFBFA). */
@Composable
private fun paper(): Color = lerp(Cove.colors.canvas, Cove.colors.card, 0.6f)

/** Body ink, slightly softer than headlines (#2E3036). */
@Composable
internal fun bodyColor(): Color = lerp(Cove.colors.ink, Cove.colors.muted, 0.28f)

@Composable
private fun TopBar(status: SaveStatus, canDelete: Boolean, onBack: () -> Unit, onDone: () -> Unit, onDelete: () -> Unit) {
    val c = Cove.colors
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).pressable(onBack, role = Role.Button).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
            CoveIcon(CoveIcons.ChevronLeft, c.muted, size = 22.dp)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            CoveText(
                when (status) {
                    SaveStatus.Idle -> ""
                    SaveStatus.Saving -> "Saving…"
                    SaveStatus.Saved -> "Saved"
                },
                style = CoveType.Meta, color = c.muted,
            )
        }
        if (canDelete) {
            Box(Modifier.height(48.dp).pressable(onDelete).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                CoveText("Delete", style = CoveType.Meta, color = c.alert)
            }
        }
        Box(Modifier.height(48.dp).pressable(onDone).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            CoveText("Done", style = CoveType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
internal fun EntryField(
    state: TextFieldState,
    placeholder: String,
    style: TextStyle,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
    color: Color = Cove.colors.ink,
    singleLine: Boolean = false,
    shortBlankLines: Boolean = false,
    onNext: () -> Unit = {},
) {
    BasicTextField(
        state,
        modifier.fillMaxWidth().focusRequester(focus),
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(Cove.colors.ink),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        onKeyboardAction = { onNext() },
        outputTransformation = if (shortBlankLines) BlankLineGap else null,
        inputTransformation = if (singleLine) InputTransformation { if (asCharSequence().contains('\n')) revertAllChanges() } else null,
        decorator = { inner ->
            Box {
                if (state.text.isEmpty()) CoveText(placeholder, style = style, color = Cove.colors.placeholder)
                inner()
            }
        },
    )
}

@Composable
private fun AttachChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).background(Cove.colors.well, CoveShapes.Pill).pressable(onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { CoveText(label, style = CoveType.Meta, color = Cove.colors.muted) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
