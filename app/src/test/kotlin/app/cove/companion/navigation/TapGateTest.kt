package app.cove.companion.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapGateTest {
    @Test fun repeatedRouteWithinWindowIsDropped() {
        var now = 0L
        val gate = TapGate(500) { now }
        assertTrue(gate.allow("money/expense/new"))
        now = 100
        assertFalse(gate.allow("money/expense/new"))
        now = 200
        assertFalse(gate.allow("money/expense/new"))
    }

    @Test fun otherRoutesAndLaterTapsPass() {
        var now = 0L
        val gate = TapGate(500) { now }
        assertTrue(gate.allow("a"))
        now = 50
        assertTrue(gate.allow("b"))
        now = 700
        assertTrue(gate.allow("b"))
    }
}
