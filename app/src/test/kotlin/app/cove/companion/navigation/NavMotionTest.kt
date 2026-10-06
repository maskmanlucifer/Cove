package app.cove.companion.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class NavMotionTest {
    @Test
    fun transitionsAreShortAndLeaveFasterThanTheyEnter() {
        assertTrue(NavMotion.ENTER_MS in 150..300)
        assertTrue(NavMotion.EXIT_MS < NavMotion.ENTER_MS)
        assertTrue(NavMotion.TAB_MS <= NavMotion.ENTER_MS)
    }

    @Test
    fun slideIsSubtle() {
        assertTrue(NavMotion.SLIDE_DP in 8..24)
        assertTrue(NavMotion.RISE_DP in 8..32)
    }
}
