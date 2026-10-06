package app.cove.companion.feature.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.app.ActivityCompat
import app.cove.companion.ai.model.SpeechFailure
import app.cove.companion.core.PermissionStep
import app.cove.companion.core.Permissions
import app.cove.companion.core.permissionStep
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.cove.companion.container
import app.cove.companion.core.appViewModel
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.navigation.Nav

/**
 * Voice assistant: listens as soon as it opens, then shows what it understood as drafts to confirm.
 * Typing runs the same pipeline, so the mic is optional.
 */
@Composable
fun VoiceScreen(nav: Nav) {
    val vm = appViewModel { VoiceViewModel(it) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val nowMillis = context.container.clock.now()
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var deniedBefore by rememberSaveable { mutableStateOf(false) }
    var refreshStep by remember { mutableIntStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.micGranted() else { deniedBefore = true; refreshStep++; vm.micDenied() }
    }
    fun micStep(): PermissionStep {
        val activity = context.findActivity()
        val canAsk = activity == null || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        return permissionStep(granted(), deniedBefore, canAsk)
    }
    LaunchedEffect(Unit) {
        if (granted() || VoiceDebug.hasPending) vm.begin() else request.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(state.done) { if (state.done) nav.home() }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> vm.pause()
                Lifecycle.Event.ON_RESUME -> vm.resume(granted())
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    CoveScreen {
        when (state.stage) {
            Stage.Listening -> ListeningView(state, nav.back, vm::typeInstead, vm::finish)
            Stage.Result -> ResultView(state, nowMillis, vm)
            Stage.Partial -> PartialView(state, nowMillis, vm, nav.back)
            Stage.Answer -> AnswerView(state) { nav.back() }
            Stage.Trouble -> TroubleView(
                state, speechGuidance(state.trouble ?: SpeechFailure.Other, remember(refreshStep, state.trouble) { micStep() }),
                onAction = { action ->
                    when (action) {
                        FixAction.AllowMic -> request.launch(Manifest.permission.RECORD_AUDIO)
                        FixAction.OpenSettings -> context.startActivity(Permissions.appSettings(context))
                        FixAction.DownloadOffline -> VoiceActions.openSpeechSettings(context)
                        FixAction.TryAgain -> vm.listen()
                        FixAction.TypeInstead -> vm.typeInstead()
                    }
                },
                onClose = nav.back,
            )
            Stage.Typing -> TypingView(
                state, nav.back, vm::onTyped, vm::submitTyped, vm::listen,
                onSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
