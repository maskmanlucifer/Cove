package app.cove.companion.feature.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.app.AppOpsManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.cove.companion.core.Permissions
import app.cove.companion.design.components.GuideBanner

/** Reads the live permission state of this phone. */
fun permissionSnapshot(c: Context) = PermissionSnapshot(
    notifications = Permissions.notificationsAllowed(c),
    exactAlarms = Permissions.exactAlarmsAllowed(c),
    fullScreenIntent = Permissions.fullScreenIntentAllowed(c),
    microphone = Permissions.micGranted(c),
    calendar = Permissions.calendarGranted(c),
    usageStats = usageAccessGranted(c),
    messages = Permissions.messagesGranted(c),
)

private fun usageAccessGranted(c: Context): Boolean {
    val ops = c.getSystemService(AppOpsManager::class.java)
    @Suppress("DEPRECATION")
    val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

/** Opens the settings page for [target]; falls back to this app's details page when the phone lacks that page. */
fun openFix(context: Context, target: FixTarget) {
    val intent = when (target) {
        FixTarget.NotificationSettings -> Permissions.notificationSettings(context)
        FixTarget.ExactAlarmSettings -> Permissions.exactAlarmSettings(context)
        FixTarget.FullScreenSettings ->
            if (Build.VERSION.SDK_INT >= 34) Permissions.fullScreenIntentSettings(context) else Permissions.appSettings(context)
        FixTarget.AppSettings -> Permissions.appSettings(context)
        FixTarget.UsageAccessSettings -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Permissions.appSettings(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Re-reads the permission state each time the screen comes back to the front (the user may have just fixed it in Settings). */
@Composable
fun rememberPermissionIssues(needs: PermissionNeeds): List<PermissionIssue> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    return remember(tick, needs) { PermissionHealth.missing(permissionSnapshot(context), needs) }
}

/** One banner for [issue] with the single button that opens the right Settings page. */
@Composable
fun PermissionGuide(issue: PermissionIssue, modifier: Modifier = Modifier, alarmsInUse: Boolean = true) {
    val context = LocalContext.current
    val guide = PermissionHealth.guide(issue, alarmsInUse)
    GuideBanner(guide.title, guide.body, guide.action, { openFix(context, guide.target) }, modifier)
}

/** Banners for every issue in [issues]. */
@Composable
fun PermissionGuides(issues: List<PermissionIssue>, modifier: Modifier = Modifier, alarmsInUse: Boolean = true) {
    issues.forEach { PermissionGuide(it, modifier, alarmsInUse) }
}
