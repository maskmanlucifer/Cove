package app.cove.companion.design

import app.cove.companion.design.components.DockBarHeight
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.DockFloatBottom
import app.cove.companion.design.components.DockSwitcher
import app.cove.companion.design.components.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockSwitcherTest {
    @Test
    fun tabsAnnouncePosition() {
        assertEquals("Today, tab 1 of 5", DockSwitcher.tabDescription(Tab.Today))
        assertEquals("Plan, tab 2 of 5", DockSwitcher.tabDescription(Tab.Plan))
        assertEquals("Me, tab 5 of 5", DockSwitcher.tabDescription(Tab.Me))
    }

    @Test
    fun toggleFlips() {
        assertTrue(DockSwitcher.toggle(false))
        assertFalse(DockSwitcher.toggle(true))
    }

    @Test
    fun contentClearsTheBar() {
        assertTrue(DockClearance >= DockBarHeight)
        assertTrue(DockFloatBottom < DockBarHeight)
    }
}
