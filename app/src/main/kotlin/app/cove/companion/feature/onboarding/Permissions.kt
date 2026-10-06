package app.cove.companion.feature.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Runtime permission checks and settings intents used by the onboarding steps. */
object Permissions {
    fun micGranted(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** True on API 32 and below unless the user switched notifications off in system settings. */
    fun notificationsAllowed(c: Context) = NotificationManagerCompat.from(c).areNotificationsEnabled()

    /** Whether the POST_NOTIFICATIONS runtime request exists on this Android version. */
    val needsNotificationRequest get() = Build.VERSION.SDK_INT >= 33

    fun exactAlarmsAllowed(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Opens the system "Alarms & reminders" page for this app. */
    fun exactAlarmSettings(c: Context) = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}"))

    /** Opens this app's notification settings, for when the runtime request was refused for good. */
    fun notificationSettings(c: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
}
