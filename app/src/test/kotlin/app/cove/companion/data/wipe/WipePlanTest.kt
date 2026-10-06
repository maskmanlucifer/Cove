package app.cove.companion.data.wipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WipePlanTest {
    private val plan = WipePlan()

    @Test fun databaseIsRemovedFirstAndKeysLast() {
        assertEquals(WipeArea.Database, plan.areas.first())
        assertEquals(WipeArea.Keystore, plan.areas.last())
    }

    @Test fun everyAreaIsCovered() {
        assertEquals(WipeArea.entries, plan.areas)
    }

    @Test fun nothingUserFacingIsKept() {
        assertFalse(plan.keeps("credentials"))
        assertFalse(plan.keeps("session"))
        assertFalse(plan.keeps("drive"))
    }

    @Test fun databaseFileNames() {
        assertTrue(plan.isDatabaseFile("cove.db"))
        assertTrue(plan.isDatabaseFile("cove.db-wal"))
        assertTrue(plan.isDatabaseFile("cove.db-journal"))
        assertFalse(plan.isDatabaseFile("androidx.work.workdb"))
    }

    @Test fun onlyWorkManagerPrefsSurvive() {
        assertTrue(plan.removesPrefs("cove_session.xml"))
        assertTrue(plan.removesPrefs("alarm_scheduler.xml"))
        assertFalse(plan.removesPrefs("androidx.work.util.preferences.xml"))
    }
}
