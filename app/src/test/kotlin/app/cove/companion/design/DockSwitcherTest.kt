package app.cove.companion.design

import app.cove.companion.design.components.DockBarHeight
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.DockFloatBottom
import app.cove.companion.design.components.DockStackBottom
import app.cove.companion.design.components.DockSwitcher
import app.cove.companion.design.components.SwipePages
import app.cove.companion.design.components.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockSwitcherTest {
    @Test
    fun meSitsLeftOfTodayAndTheRestFollowInOrder() {
        assertEquals(listOf(Tab.Me, Tab.Today, Tab.Plan, Tab.Money, Tab.Journal), SwipePages)
    }

    @Test
    fun rowsAnnouncePosition() {
        assertEquals("Me, page 1 of 5", DockSwitcher.tabDescription(Tab.Me))
        assertEquals("Plan, page 3 of 5", DockSwitcher.tabDescription(Tab.Plan))
    }

    @Test
    fun toggleFlips() {
        assertTrue(DockSwitcher.toggle(false))
        assertFalse(DockSwitcher.toggle(true))
    }

    @Test
    fun contentClearsTheDock() {
        assertTrue(DockClearance >= DockBarHeight)
        assertTrue(DockFloatBottom < DockBarHeight)
        assertTrue(DockStackBottom > DockBarHeight)
    }

    @Test
    fun pageDotsCountEveryPage() {
        assertEquals(5, DockSwitcher.pageCount)
        assertEquals(0, DockSwitcher.pageIndex(Tab.Me))
        assertEquals(1, DockSwitcher.pageIndex(Tab.Today))
        assertEquals(4, DockSwitcher.pageIndex(Tab.Journal))
    }

    @Test
    fun dotsSayWhichPageAndHowToChoose() {
        assertEquals("Page 3 of 5, Plan. Tap to choose a page", DockSwitcher.pillDescription(Tab.Plan))
    }

    @Test
    fun dragPicksTheRowUnderTheFinger() {
        assertEquals(0, DockSwitcher.rowAt(-40f, 48f))
        assertEquals(0, DockSwitcher.rowAt(10f, 48f))
        assertEquals(2, DockSwitcher.rowAt(48f * 2 + 1, 48f))
        assertEquals(4, DockSwitcher.rowAt(48f * 5 - 1, 48f))
        assertNull(DockSwitcher.rowAt(48f * 5, 48f))
    }
}
