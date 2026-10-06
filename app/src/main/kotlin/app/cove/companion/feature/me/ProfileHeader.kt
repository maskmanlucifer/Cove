package app.cove.companion.feature.me

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.cove.companion.container
import app.cove.companion.core.Undo
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.UndoHost
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.plan.OptionList
import app.cove.companion.feature.plan.PlanSheet
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Undo area of the profile photo's "Photo removed" offer. */
private const val PROFILE_UNDO = "profile"

private enum class PhotoAction { Gallery, Camera, Remove }

/**
 * Me's header: the avatar (tap for the profile photo sheet), the name (tap to edit via [onEditName]) and a
 * caption. The photo comes from the picker or the camera, is cropped in-app, and stays on this phone.
 */
@Composable
fun ProfileHeader(name: String, onEditName: () -> Unit) {
    val context = LocalContext.current
    val store = context.container.profilePhoto
    val scope = rememberCoroutineScope()
    val version by store.version.collectAsState()
    val photo by produceState<ImageBitmap?>(null, version) {
        value = withContext(Dispatchers.IO) { store.file()?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() } }
    }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { uri -> cropping = uri } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) cropping = cameraUri(context, file) else file?.delete()
    }

    val c = Cove.colors
    Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(name, photo) { sheet = true }
            Column(Modifier.weight(1f).heightIn(min = 48.dp).pressable(onEditName, role = Role.Button, onClickLabel = "Edit name"), verticalArrangement = Arrangement.Center) {
                if (name.isBlank()) CoveText("Add your name", style = CoveType.Section.copy(lineHeight = 32.sp), color = c.placeholder)
                else CoveText(name, style = CoveType.Section.copy(lineHeight = 32.sp), maxLines = 2)
                CoveText("Your data lives in your own space", style = CoveType.Meta, color = c.muted)
            }
        }
        UndoHost(PROFILE_UNDO)
    }

    if (sheet) {
        PlanSheet({ sheet = false }, gap = 12) { close ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetHeading("Profile photo")
                val options = buildList {
                    add(PhotoAction.Gallery to "Choose from gallery")
                    add(PhotoAction.Camera to "Take a photo")
                    if (photo != null) add(PhotoAction.Remove to "Remove photo")
                }
                OptionList(options, selected = null) { action ->
                    close()
                    when (action) {
                        PhotoAction.Gallery -> picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        PhotoAction.Camera -> {
                            val target = context.container.journalFiles.newCameraTarget()
                            cameraFile = target.first
                            try {
                                camera.launch(target.second)
                            } catch (e: ActivityNotFoundException) {
                                target.first.delete()
                                Toast.makeText(context, "No camera app found. Try choosing from your gallery.", Toast.LENGTH_LONG).show()
                            }
                        }
                        PhotoAction.Remove -> scope.launch {
                            store.remove()
                            Undo.center.post(PROFILE_UNDO, "Profile photo removed", onExpire = { store.discardRemoved() }) { store.restore() }
                        }
                    }
                }
            }
        }
    }
    cropping?.let { source ->
        ProfileCropper(
            source,
            onClose = {
                cropping = null
                cameraFile?.delete()
                cameraFile = null
            },
        )
    }
}

private fun cameraUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

/** The 56 dp circle: the photo, else the name's initial, else a person icon. */
@Composable
private fun Avatar(name: String, photo: ImageBitmap?, onClick: () -> Unit) {
    val c = Cove.colors
    val initial = avatarInitial(name)
    Box(
        Modifier.size(56.dp).clip(CoveShapes.Circle).background(if (c.isDark) c.wellStrong else Color(0xFFE4E1DB))
            .pressable(onClick, role = Role.Button).semantics { contentDescription = "Profile photo, double tap to change" },
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(photo, null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
            initial != null -> CoveText(initial, style = CoveType.Value.copy(fontSize = 20.sp, lineHeight = 27.sp), color = c.muted)
            else -> CoveIcon(CoveIcons.Me, c.muted, size = 24.dp)
        }
    }
}
