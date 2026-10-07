package app.cove.companion.feature.permissions

/** Things the user can switch off after the fact that make a feature quietly stop working. */
enum class PermissionIssue { Notifications, ExactAlarms, FullScreenIntent, Microphone, Calendar, UsageStats, Messages }

/** Current state of each permission. */
data class PermissionSnapshot(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreenIntent: Boolean,
    val microphone: Boolean,
    val calendar: Boolean,
    val usageStats: Boolean,
    /** Both "Receive text messages" and "Read text messages" are allowed. */
    val messages: Boolean = true,
)

/** Which features are in use, so only relevant gaps are reported. */
data class PermissionNeeds(
    val alarms: Boolean = false,
    val reminders: Boolean = false,
    val brief: Boolean = false,
    val voice: Boolean = false,
    val briefCalendar: Boolean = false,
    val suggestions: Boolean = false,
    /** "Payments from messages" is on. */
    val messages: Boolean = false,
)

/** Which settings page fixes an issue. */
enum class FixTarget { NotificationSettings, ExactAlarmSettings, FullScreenSettings, AppSettings, UsageAccessSettings }

/** Plain-language guide for one [PermissionIssue]: what is off, what it means, and the one button. */
data class PermissionGuideText(val title: String, val body: String, val action: String, val target: FixTarget)

/** Pure rules behind the permission banners. */
object PermissionHealth {
    /** Issues that matter given [needs], most important first. */
    fun missing(s: PermissionSnapshot, needs: PermissionNeeds): List<PermissionIssue> = buildList {
        if (!s.notifications && (needs.alarms || needs.reminders || needs.brief)) add(PermissionIssue.Notifications)
        if (!s.exactAlarms && needs.alarms) add(PermissionIssue.ExactAlarms)
        if (!s.fullScreenIntent && needs.alarms && s.notifications) add(PermissionIssue.FullScreenIntent)
        if (!s.microphone && needs.voice) add(PermissionIssue.Microphone)
        if (!s.calendar && needs.briefCalendar) add(PermissionIssue.Calendar)
        if (!s.usageStats && needs.suggestions) add(PermissionIssue.UsageStats)
        if (!s.messages && needs.messages) add(PermissionIssue.Messages)
    }

    /** Copy and fix for [issue]. [alarmsInUse] tailors the notification line when alarms exist. */
    fun guide(issue: PermissionIssue, alarmsInUse: Boolean = true): PermissionGuideText = when (issue) {
        PermissionIssue.Notifications -> PermissionGuideText(
            "Notifications are off",
            if (alarmsInUse) "Alarms still ring, but Cove can’t show its screen or Stop and Snooze buttons, and reminders won’t appear."
            else "Reminders and your morning brief can’t appear until notifications are on.",
            "Turn on notifications", FixTarget.NotificationSettings,
        )
        PermissionIssue.ExactAlarms -> PermissionGuideText(
            "Alarms may ring late",
            "Android needs “Alarms & reminders” allowed so alarms ring on the minute.",
            "Allow alarms", FixTarget.ExactAlarmSettings,
        )
        PermissionIssue.FullScreenIntent -> PermissionGuideText(
            "Alarm screen is blocked",
            "Allow full-screen alerts so a ringing alarm can light up your lock screen.",
            "Allow full-screen alerts", FixTarget.FullScreenSettings,
        )
        PermissionIssue.Microphone -> PermissionGuideText(
            "Microphone is off",
            "You can still type to Cove. To talk to it, allow the microphone.",
            "Allow microphone", FixTarget.AppSettings,
        )
        PermissionIssue.Calendar -> PermissionGuideText(
            "Calendar is off",
            "Your brief won’t mention events from your calendar until you allow it.",
            "Allow calendar", FixTarget.AppSettings,
        )
        PermissionIssue.UsageStats -> PermissionGuideText(
            "Usage access is off",
            "Suggestions based on how you use your phone are paused. Nothing leaves your phone either way.",
            "Open usage access", FixTarget.UsageAccessSettings,
        )
        PermissionIssue.Messages -> PermissionGuideText(
            "Payments from messages are paused",
            "Allow “Receive text messages” and “Read text messages” for Cove. You can still paste a message to add a payment.",
            "Open settings", FixTarget.AppSettings,
        )
    }
}
