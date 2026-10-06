package app.cove.companion.feature.onboarding

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.cove.companion.core.Permissions
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.OrbColors
import app.cove.companion.design.components.BalancedText
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.Hairline
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.navigation.Nav
import kotlin.math.hypot

/** Step 0: promise, "Get started" and a way in for people who already use Cove. */
@Composable
fun WelcomeScreen(nav: Nav) {
    val context = LocalContext.current
    val next = { nav.go(OnboardingStep.Welcome.next!!.route) }
    CoveScreen {
        WelcomeBlob()
        Column(Modifier.fillMaxSize().coveTopInset().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 40.dp)) {
            Box(Modifier.weight(1f))
            Column(Modifier.padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BalancedText(
                    "Say it once.", " Cove remembers the rest.",
                    CoveType.Title.copy(fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1.4).sp),
                )
                CoveText(
                    "Alarms, to-dos, money and a journal, all by voice. Everything stays in your own space.",
                    style = Body16.copy(lineHeight = 24.sp),
                    color = Cove.colors.muted,
                )
            }
            BigButton("Get started", next)
            Box(Modifier.padding(top = 4.dp)) {
                TextAction("I already use Cove") { nav.go(Routes.Connect) }
            }
        }
    }
}

/** The soft peach, lilac and sky glow behind the welcome headline. */
@Composable
private fun WelcomeBlob() {
    val dark = Cove.colors.isDark
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 130.dp)
                .size(320.dp)
                .blur(14.dp),
        ) {
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) }
            fun blob(color: Color, cx: Float, cy: Float, stop: Float) {
                val center = Offset(size.width * cx, size.height * cy)
                val reach = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y))
                val tint = if (dark) color.copy(alpha = 0.55f) else color
                drawRect(Brush.radialGradient(0f to tint, stop to tint.copy(alpha = 0f), center = center, radius = reach))
            }
            clipPath(circle) {
                blob(OrbColors.Sky, 0.50f, 0.65f, 0.50f)
                blob(OrbColors.Lilac, 0.65f, 0.45f, 0.48f)
                blob(OrbColors.Peach, 0.40f, 0.40f, 0.45f)
            }
        }
    }
}

/** Step 1: wake-up time on a drum; saved to settings and the "Wake up" alarm. */
@Composable
fun WakeTimeScreen(nav: Nav) {
    val vm = appViewModel { OnboardingViewModel(it) }
    val wake by vm.wake.collectAsState()
    val next = { nav.go(OnboardingStep.WakeTime.next!!.route) }
    StepFrame(OnboardingStep.WakeTime.position, "When do you usually", " wake up?", onSkip = next) {
        WakeWheel(wake, vm::setWake, Modifier.weight(1f).fillMaxWidth())
        HelperText("Your brief will be ready by then. Change it any time.")
        BigButton("Continue", { vm.saveWake(next) })
    }
}

/** Step 2: explains the microphone, then asks for RECORD_AUDIO; denial just moves on to typing. */
@Composable
fun MicPermissionScreen(nav: Nav) {
    val context = LocalContext.current
    val next = { nav.go(OnboardingStep.Mic.next!!.route) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next() }
    StepFrame(OnboardingStep.Mic.position, "Can Cove hear you", " when you ask?", onSkip = next) {
        InfoCard {
            listOf(
                "Only while you tap or hold the mic",
                "Speech becomes text on this phone first",
                "Typing works everywhere too",
            ).forEachIndexed { i, line ->
                if (i > 0) Hairline()
                Row(
                    Modifier.heightIn(min = 60.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(6.dp).background(Cove.colors.ink, RoundedCornerShape(3.dp)))
                    CoveText(line, style = Body16)
                }
            }
        }
        Box(Modifier.weight(1f))
        HelperText("Android will ask next. Choose “While using the app”.")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BigButton("Allow microphone", {
                if (Permissions.micGranted(context)) next() else ask.launch(Manifest.permission.RECORD_AUDIO)
            })
            TextAction("I’ll type for now", next)
        }
    }
}

/** Step 3: notifications and exact alarms; Finish unlocks once alarms are allowed. */
@Composable
fun AlarmPermissionScreen(nav: Nav) {
    val vm = appViewModel { OnboardingViewModel(it) }
    val context = LocalContext.current
    val finish = { vm.finish { nav.home() } }
    var tick by remember { mutableIntStateOf(0) }
    var notifAsked by rememberSaveable { mutableStateOf(false) }
    var openedSettings by rememberSaveable { mutableStateOf(false) }
    val notifAllowed = tick >= 0 && Permissions.notificationsAllowed(context)
    val exactAllowed = tick >= 0 && Permissions.exactAlarmsAllowed(context)

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifAsked = true
        tick++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(exactAllowed, openedSettings) { if (exactAllowed && openedSettings) finish() }

    StepFrame(OnboardingStep.Alarms.position, "So alarms ring", " on the minute", onSkip = finish) {
        InfoCard {
            PermissionRow(
                title = "Notifications",
                subtitle = "Bundled, three times a day",
                allowed = notifAllowed,
                action = if (notifAsked) "Settings" else "Allow",
            ) {
                if (notifAsked) context.startActivity(Permissions.notificationSettings(context))
                else if (Permissions.needsNotificationRequest) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Permissions.notificationSettings(context))
            }
            Hairline()
            PermissionRow("Alarms and reminders", "Opens a system page", exactAllowed, "Allow") {
                openedSettings = true
                context.startActivity(Permissions.exactAlarmSettings(context))
            }
        }
        HelperText("Turn on “Allow setting alarms”, then press back. Cove will notice and move on.", center = false)
        Box(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BigButton("Finish", finish, enabled = exactAllowed)
            TextAction("Use reminders only", finish)
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Cove.colors.card, RoundedCornerShape(28.dp)).padding(horizontal = 20.dp, vertical = 6.dp),
    ) { content() }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, allowed: Boolean, action: String, onAction: () -> Unit) {
    val c = Cove.colors
    Row(Modifier.heightIn(min = 76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CoveText(title, style = Body16)
            CoveText(subtitle, style = CoveType.Meta, color = c.muted)
        }
        if (allowed) CoveText("Allowed", style = CoveType.MetaMedium, color = c.saved)
        else PillButton(action, onAction, height = 44.dp, horizontalPadding = 16.dp, textStyle = CoveType.MetaMedium)
    }
}
