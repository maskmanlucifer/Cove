package app.cove.companion.feature.me

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cove.companion.container
import app.cove.companion.core.OneShot
import app.cove.companion.data.media.MAX_LONG_EDGE
import app.cove.companion.data.media.fitLongEdge
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.FullScreenDialog
import app.cove.companion.design.components.Zoom
import app.cove.companion.design.components.coverSize
import app.cove.companion.design.components.pressable
import app.cove.companion.design.components.zoomable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CROP_MAX_ZOOM = 5f

/**
 * Lightweight crop step: drag and pinch the picture inside a circular mask, then Save stores a 512 px square WebP
 * through [app.cove.companion.data.media.ProfilePhotoStore]. [onClose] runs after Save, Cancel, Back or a failed read.
 */
@Composable
fun ProfileCropper(source: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val guard = remember { OneShot() }
    val bitmap by produceState<Bitmap?>(null, source) {
        value = withContext(Dispatchers.IO) { decodeForCrop(context, source) }
        if (value == null) {
            Toast.makeText(context, "Couldn’t use that photo", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    var zoom by remember { mutableStateOf(Zoom()) }
    var diameter by remember { mutableFloatStateOf(0f) }

    FullScreenDialog(onClose) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.heightIn(min = 48.dp).pressable(onClose, role = Role.Button).padding(end = 16.dp), contentAlignment = Alignment.CenterStart) {
                    CoveText("Cancel", style = CoveType.Button, color = Color.White, maxLines = 1)
                }
                CoveText("Move and zoom", Modifier.weight(1f), style = CoveType.Meta, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.End, maxLines = 1)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val side = minOf(maxWidth, maxHeight)
                val sidePx = with(density) { side.toPx() }
                bitmap?.let { bmp ->
                    val content = coverSize(bmp.width.toFloat(), bmp.height.toFloat(), sidePx, sidePx)
                    LaunchedEffect(bmp, sidePx) { zoom = Zoom(); diameter = sidePx }
                    Box(
                        Modifier.size(side).clip(CoveShapes.Circle).border(1.dp, Color.White.copy(alpha = 0.6f), CoveShapes.Circle)
                            .zoomable({ zoom }, { content }, CROP_MAX_ZOOM) { next, _ -> zoom = next }
                            .semantics { contentDescription = "Profile photo crop. Drag to move, pinch to zoom." },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            remember(bmp) { bmp.asImageBitmap() }, null,
                            Modifier.size(with(density) { content.width.toDp() }, with(density) { content.height.toDp() })
                                .graphicsLayer { scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.x; translationY = zoom.y },
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).background(if (bitmap == null || guard.busy) Color(0x40FFFFFF) else Color.White, CoveShapes.Pill)
                    .pressable({
                        val bmp = bitmap ?: return@pressable
                        val z = zoom
                        val d = diameter
                        guard.launch(scope) {
                            val saved = withContext(Dispatchers.Default) { cropToAvatar(bmp, d, z) }
                            val ok = saved?.let { container.profilePhoto.save(it) } ?: false
                            if (ok) onClose() else Toast.makeText(context, "Couldn’t save that photo", Toast.LENGTH_SHORT).show()
                            ok
                        }
                    }, enabled = bitmap != null && !guard.busy, role = Role.Button),
                contentAlignment = Alignment.Center,
            ) { CoveText("Save", style = CoveType.Button, color = Color.Black, maxLines = 1) }
            Box(Modifier.heightIn(min = 8.dp))
        }
    }
}

/** Reads [source] at no more than 2048 px on its long edge, EXIF rotation applied; null when it cannot be read. */
private fun decodeForCrop(context: Context, source: Uri): Bitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val (w, h) = fitLongEdge(info.size.width, info.size.height, MAX_LONG_EDGE)
        decoder.setTargetSize(w, h)
    }
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/** The square under the mask, scaled to [AVATAR_EDGE]; null on failure. */
private fun cropToAvatar(source: Bitmap, diameter: Float, zoom: Zoom): Bitmap? = try {
    val r = cropRect(source.width, source.height, diameter, zoom)
    val cropped = Bitmap.createBitmap(source, r.x, r.y, r.size, r.size)
    if (cropped.width == AVATAR_EDGE) cropped else Bitmap.createScaledBitmap(cropped, AVATAR_EDGE, AVATAR_EDGE, true).also { if (it !== cropped) cropped.recycle() }
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}
