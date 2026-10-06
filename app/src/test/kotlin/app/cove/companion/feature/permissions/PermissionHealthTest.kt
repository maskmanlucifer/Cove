package app.cove.companion.feature.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionHealthTest {
    private val allOn = PermissionSnapshot(true, true, true, true, true, true)
    private val allOff = PermissionSnapshot(false, false, false, false, false, false)

    @Test fun nothingMissingWhenAllGranted() {
        assertTrue(PermissionHealth.missing(allOn, PermissionNeeds(true, true, true, true, true, true)).isEmpty())
    }

    @Test fun onlyRelevantGapsAreReported() {
        assertTrue(PermissionHealth.missing(allOff, PermissionNeeds()).isEmpty())
        assertEquals(
            listOf(PermissionIssue.Notifications, PermissionIssue.ExactAlarms),
            PermissionHealth.missing(allOff, PermissionNeeds(alarms = true)),
        )
        assertEquals(listOf(PermissionIssue.Notifications), PermissionHealth.missing(allOff, PermissionNeeds(reminders = true)))
    }

    @Test fun fullScreenIsReportedOnlyOnceNotificationsAreOn() {
        val s = allOn.copy(fullScreenIntent = false)
        assertEquals(listOf(PermissionIssue.FullScreenIntent), PermissionHealth.missing(s, PermissionNeeds(alarms = true)))
        assertEquals(listOf(PermissionIssue.Notifications, PermissionIssue.ExactAlarms), PermissionHealth.missing(allOff, PermissionNeeds(alarms = true)))
    }

    @Test fun revocationAfterTheFactIsDetectedByRereadingTheSnapshot() {
        val needs = PermissionNeeds(alarms = true)
        assertTrue(PermissionHealth.missing(allOn, needs).isEmpty())
        assertEquals(listOf(PermissionIssue.Notifications), PermissionHealth.missing(allOn.copy(notifications = false), needs))
    }

    @Test fun everyIssueHasOneActionAndCalmCopy() {
        PermissionIssue.entries.forEach {
            val g = PermissionHealth.guide(it)
            assertTrue(g.title.isNotBlank() && g.body.isNotBlank() && g.action.isNotBlank())
        }
        assertEquals(FixTarget.NotificationSettings, PermissionHealth.guide(PermissionIssue.Notifications).target)
        assertEquals(FixTarget.ExactAlarmSettings, PermissionHealth.guide(PermissionIssue.ExactAlarms).target)
    }
}
