package app.cove.companion.feature.journal

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.cove.companion.core.appViewModel
import app.cove.companion.core.longLabel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav
import kotlinx.coroutines.launch

private val BodyStyle = CoveType.Body.copy(fontSize = 18.sp, lineHeight = 29.sp)

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
    val bodyFocus = remember { FocusRequester() }
    val leave: () -> Unit = { scope.launch { vm.finish(); nav.back() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::addPhoto) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = vm.cameraTarget
        if (ok && target != null) vm.addPhoto(target.second) else target?.first?.delete()
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.startRecording() }

    BackHandler(onBack = leave)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { scope.launch { vm.save() } }
    LaunchedEffect(s.loaded) {
        if (s.loaded && !s.persisted && vm.title.text.isEmpty() && vm.body.text.isEmpty()) titleFocus.requestFocus()
    }

    Box(Modifier.fillMaxSize().background(paper()).imePadding()) {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            TopBar(s.status, onBack = leave, onDone = leave)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 28.dp, end = 28.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CoveText(s.day.longLabel() + (s.mood?.let { " · $it" } ?: ""), style = CoveType.Meta, color = c.muted)
                EntryField(vm.title, "Title", CoveType.Title, titleFocus, Modifier.offset(y = (-2).dp), singleLine = true, onNext = { bodyFocus.requestFocus() })
                EntryField(vm.body, "Write whatever is on your mind.", BodyStyle, bodyFocus, Modifier.offset(y = (-3).dp).heightIn(min = 160.dp), bodyColor())
                val photos = s.media.filter { it.kind == "photo" }
                if (photos.isNotEmpty()) PhotoStrip(photos) { vm.removeMedia(it) }
                s.media.filter { it.kind == "voice" }.forEach { note ->
                    VoiceRow(note, s.playback, onToggle = { vm.togglePlayback(note) }, onRemove = { vm.removeMedia(note) })
                }
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
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = {
                sheet = null
                camera.launch(vm.newCameraUri())
            },
        )
        MoodSheet(
            sheet == "mood", s.mood, canDelete = s.persisted, onDismiss = { sheet = null },
            onPick = { vm.setMood(it); sheet = null },
            onDelete = { scope.launch { vm.delete(); nav.back() } },
        )
    }
}

/** Entry background: a touch lighter than the canvas, as in the design (#FBFBFA). */
@Composable
private fun paper(): Color = lerp(Cove.colors.canvas, Cove.colors.card, 0.6f)

/** Body ink, slightly softer than headlines (#2E3036). */
@Composable
private fun bodyColor(): Color = lerp(Cove.colors.ink, Cove.colors.muted, 0.28f)

@Composable
private fun TopBar(status: SaveStatus, onBack: () -> Unit, onDone: () -> Unit) {
    val c = Cove.colors
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).pressable(onBack), contentAlignment = Alignment.Center) {
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
        Box(Modifier.height(44.dp).pressable(onDone).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            CoveText("Done", style = CoveType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun EntryField(
    state: TextFieldState,
    placeholder: String,
    style: TextStyle,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
    color: Color = Cove.colors.ink,
    singleLine: Boolean = false,
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
