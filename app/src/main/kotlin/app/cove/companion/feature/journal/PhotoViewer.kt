package app.cove.companion.feature.journal

import app.cove.companion.design.components.HoldToRemoveButton
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.MimeTypeMap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.cove.companion.container
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.media.MAX_LONG_EDGE
import app.cove.companion.data.media.PhotoResult
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.PhotoIcons
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.FullScreenDialog
import app.cove.companion.design.components.Zoom
import app.cove.companion.design.components.fitSize
import app.cove.companion.design.components.pressable
import app.cove.companion.design.components.zoomable
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 4f

/**
 * Full-screen viewer over a black scrim: swipe between [photos], pinch or double-tap to zoom, drag to pan.
 * Close, Share and Remove sit in a bar below the status bar; [onRemove] closes the viewer (the editor offers Undo).
 */
@Composable
fun PhotoViewer(photos: List<JournalMediaEntity>, start: Int, onClose: () -> Unit, onRemove: (JournalMediaEntity) -> Unit) {
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    FullScreenDialog(onClose) {
        val pager = rememberPagerState(start.coerceIn(0, photos.lastIndex)) { photos.size }
        var zoomed by remember { mutableStateOf(false) }
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { photos[it].id }) { page ->
            ViewerPage(photos[page], current = pager.currentPage == page) { zoomed = it }
        }
        val photo = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]
        ViewerBar(pager.currentPage + 1, photos.size, photo, onClose, onRemove)
    }
}

@Composable
private fun ViewerBar(index: Int, total: Int, photo: JournalMediaEntity, onClose: () -> Unit, onRemove: (JournalMediaEntity) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fetcher = context.container.driveKit.fetcher
    var removing by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(CoveIcons.Close, "Close photo", onClose)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (total > 1) CoveText("$index of $total", style = CoveType.Meta, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
        }
        BarButton(PhotoIcons.Share, "Share photo") {
            scope.launch { fetcher.file(photo)?.let { sharePhoto(context, it) } }
        }
        HoldToRemoveButton(
            "photo",
            { if (!removing) { removing = true; onRemove(photo) } },
            icon = PhotoIcons.Trash, iconSize = 20.dp, disc = 40.dp, idle = Color(0x40FFFFFF), iconColor = Color.White,
        )
    }
}

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).pressable(onClick, role = Role.Button).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).background(Color(0x40FFFFFF), CoveShapes.Circle), contentAlignment = Alignment.Center) {
            CoveIcon(icon, Color.White, size = 20.dp)
        }
    }
}

/** One page: the thumbnail first, then the stored image, with pinch, pan and double-tap zoom. */
@Composable
private fun ViewerPage(photo: JournalMediaEntity, current: Boolean, onZoomed: (Boolean) -> Unit) {
    val loader = LocalContext.current.container.photoLoader
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    var retry by remember { mutableIntStateOf(0) }
    val result by produceState<PhotoResult?>(loader.peek(photo, MAX_LONG_EDGE)?.let { PhotoResult.Ready(it) }, photo.id, retry) {
        if (value !is PhotoResult.Ready) value = null
        value = loader.load(photo, MAX_LONG_EDGE)
    }
    val thumb by produceState<Bitmap?>(loader.peekThumb(photo), photo.id) { if (value == null) value = loader.thumb(photo) }
    val ready = (result as? PhotoResult.Ready)?.bitmap
    val shown = ready ?: thumb
    var zoom by remember(photo.id) { mutableStateOf(Zoom()) }
    var job by remember { mutableStateOf<Job?>(null) }
    if (current) LaunchedEffect(zoom.zoomed) { onZoomed(zoom.zoomed) }
    LaunchedEffect(current) { if (!current) zoom = Zoom() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val view = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val content = shown?.let { fitSize(it.width.toFloat(), it.height.toFloat(), view.width, view.height) } ?: view
        Box(
            Modifier.fillMaxSize()
                .zoomable({ zoom }, { content }, MAX_ZOOM) { next, animate ->
                    job?.cancel()
                    if (animate && !reduce) {
                        val from = zoom
                        job = scope.launch {
                            Animatable(0f).animateTo(1f, tween(200)) {
                                zoom = Zoom(from.scale + (next.scale - from.scale) * value, from.x + (next.x - from.x) * value, from.y + (next.y - from.y) * value)
                            }
                        }
                    } else {
                        zoom = next
                    }
                }
                .graphicsLayer { scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.x; translationY = zoom.y }
                .semantics { contentDescription = "Photo. Pinch or double tap to zoom." },
            contentAlignment = Alignment.Center,
        ) {
            shown?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        when (result) {
            PhotoResult.Missing -> PhotoProblem("Photo not available offline", Color.White) { retry++ }
            PhotoResult.Broken -> PhotoProblem("Couldn’t open this photo", Color.White) { retry++ }
            else -> Unit
        }
    }
}

/** Hands the stored photo (GPS already stripped when it was added) to the system share sheet. */
private fun sharePhoto(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "image/*"
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, "Share photo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No app can take a picture: nothing to do.
    }
}
