package app.cove.companion.core

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** What the UI should offer next for a runtime permission. */
enum class PermissionStep { Granted, Ask, OpenSettings }

/**
 * Picks the next [PermissionStep]: once the system has refused a request and will no longer show its dialog
 * ([canAskAgain] false), the only way forward is app settings.
 *
 * @param deniedBefore the user already answered "don't allow" at least once.
 * @param canAskAgain `shouldShowRequestPermissionRationale` for the permission, or true if never asked.
 */
fun permissionStep(granted: Boolean, deniedBefore: Boolean, canAskAgain: Boolean): PermissionStep = when {
    granted -> PermissionStep.Granted
    deniedBefore && !canAskAgain -> PermissionStep.OpenSettings
    else -> PermissionStep.Ask
}

/** Runtime permission checks and settings intents for everything Cove asks the user to allow. */
object Permissions {
    /** Whether [permission] is currently granted. */
    fun granted(c: Context, permission: String) =
        ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED

    fun micGranted(c: Context) = granted(c, Manifest.permission.RECORD_AUDIO)

    /** Whether the morning brief may read the device calendar. */
    fun calendarGranted(c: Context) = granted(c, Manifest.permission.READ_CALENDAR)

    /** True on API 32 and below unless the user switched notifications off in system settings. */
    fun notificationsAllowed(c: Context) = NotificationManagerCompat.from(c).areNotificationsEnabled()

    /** Whether the POST_NOTIFICATIONS runtime request exists on this Android version. */
    val needsNotificationRequest get() = Build.VERSION.SDK_INT >= 33

    /** Whether exact alarms may be scheduled (always true where `USE_EXACT_ALARM` is granted). */
    fun exactAlarmsAllowed(c: Context): Boolean =
        c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Whether the ring screen may take over the display from a notification (always allowed before Android 14). */
    fun fullScreenIntentAllowed(c: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Opens the system "Alarms & reminders" page for this app. */
    fun exactAlarmSettings(c: Context) =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}"))

    /** System screen where the user allows full-screen alerts (Android 14+). */
    @RequiresApi(34)
    fun fullScreenIntentSettings(c: Context) =
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${c.packageName}"))

    /** Opens this app's notification settings, for when the runtime request was refused for good. */
    fun notificationSettings(c: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)

    /** Opens this app's system settings page, where any refused permission can be switched on. */
    fun appSettings(c: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
}
