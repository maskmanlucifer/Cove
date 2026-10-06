package app.cove.companion.feature.onboarding

import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.design.TextScales
import app.cove.companion.design.resolveReduceMotion
import app.cove.companion.feature.me.alarmSummary
import app.cove.companion.feature.today.pickOneThing
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class OnboardingLogicTest {
    @Test fun wakeStepsWrapAroundMidnight() {
        assertEquals(6 * 60 + 45, stepWake(6 * 60 + 30, 1))
        assertEquals(23 * 60 + 45, stepWake(0, -1))
        assertEquals(15, stepWake(23 * 60 + 45, 2))
    }

    @Test fun snapRoundsToQuarterHours() {
        assertEquals(390, snapWake(386))
        assertEquals(405, snapWake(398))
        assertEquals(0, snapWake(24 * 60 - 5))
    }

    @Test fun stepsAreOrdered() {
        assertEquals(OnboardingStep.Mic, OnboardingStep.WakeTime.next)
        assertNull(OnboardingStep.Alarms.next)
        assertEquals(3, OnboardingStep.indicatorCount)
        assertEquals(3, OnboardingStep.Alarms.position)
    }

    @Test fun textScaleMapsToNearestOption() {
        assertEquals("Default", TextScales.label(1f))
        assertEquals("Large", TextScales.label(1.2f))
        assertEquals("Small", TextScales.label(0.5f))
    }

    @Test fun reduceMotionResolution() {
        assertEquals(true, resolveReduceMotion("on", false))
        assertEquals(false, resolveReduceMotion("off", true))
        assertEquals(true, resolveReduceMotion("system", true))
    }

    @Test fun alarmSummaryText() {
        assertEquals("None", alarmSummary(emptyList()))
        assertEquals("6:30 am · 2 more", alarmSummary(listOf(390, 700, 800)))
    }

    @Test fun oneThingPicksSoonestAndSkips() {
        val a = TodoEntity("a", null, "A", dueAt = 20)
        val b = TodoEntity("b", null, "B", dueAt = 10)
        val c = TodoEntity("c", null, "C", dueAt = null)
        assertEquals("b", pickOneThing(listOf(a, b, c), emptyList())?.id)
        assertEquals("a", pickOneThing(listOf(a, b, c), listOf("b"))?.id)
        assertEquals("b", pickOneThing(listOf(a, b, c), listOf("a", "b", "c"))?.id)
        assertNull(pickOneThing(listOf(a.copy(done = true)), emptyList()))
    }
}
