package app.cove.companion.feature.training

import app.cove.companion.feature.training.engine.AdviceKind
import app.cove.companion.feature.training.engine.LogPoint
import app.cove.companion.feature.training.engine.WeightAdvice
import app.cove.companion.feature.training.engine.WeightAdvisor
import app.cove.companion.feature.training.engine.WeightUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightAdvisorTest {
    private val today = LocalDate.of(2026, 10, 6)

    private fun log(daysAgo: Int, kg: Double, vararg reps: Int, sets: Int = 3, target: Int = 8) =
        LogPoint(today.minusDays(daysAgo.toLong()), kg, sets, target, reps.toList())

    private fun advise(history: List<LogPoint>, current: Double, inc: Double = 2.5, unit: WeightUnit = WeightUnit.Kg) =
        WeightAdvisor.advise(history, current, inc, today, unit)

    @Test fun noHistoryMeansNoAdvice() = assertNull(advise(emptyList(), 60.0))

    @Test fun bodyweightLiftsGetNoAdvice() = assertNull(advise(listOf(log(7, 0.0, 8, 8, 8)), 0.0))

    @Test fun allSetsHitAddsOneIncrementWithReason() {
        val a = advise(listOf(log(7, 60.0, 8, 8, 8)), 60.0)!!
        assertEquals(AdviceKind.Increase, a.kind)
        assertEquals(62.5, a.weightKg, 0.001)
        assertEquals("Last time all sets done at 60 kg. Try 62.5 kg.", a.reason)
        assertTrue(a.actionable)
    }

    @Test fun dumbbellIncrementIsOneKilo() = assertEquals(17.0, advise(listOf(log(7, 16.0, 10, 10, 10, target = 10)), 16.0, 1.0)!!.weightKg, 0.001)

    @Test fun moreRepsThanPlannedStillCountsAsHit() = assertEquals(AdviceKind.Increase, advise(listOf(log(7, 60.0, 9, 9, 8)), 60.0)!!.kind)

    @Test fun nothingIsSuggestedWhenTodayAlreadyUsesIt() = assertNull(advise(listOf(log(7, 60.0, 8, 8, 8)), 62.5))

    @Test fun lastSetShortHolds() {
        val a = advise(listOf(log(7, 60.0, 8, 8, 6)), 60.0)!!
        assertEquals(AdviceKind.Hold, a.kind)
        assertEquals("Last set was 6 reps. Stay at 60 kg.", a.reason)
        assertTrue(!a.actionable)
    }

    @Test fun middleSetShortHolds() = assertEquals("A set came up at 7 reps. Stay at 60 kg.", advise(listOf(log(7, 60.0, 8, 7, 8)), 60.0)!!.reason)

    @Test fun fewerSetsHolds() = assertEquals("Only 2 of 3 sets last time. Stay at 60 kg.", advise(listOf(log(7, 60.0, 8, 8)), 60.0)!!.reason)

    @Test fun oneRepWordIsSingular() = assertEquals("Last set was 1 rep. Stay at 60 kg.", advise(listOf(log(7, 60.0, 8, 8, 1)), 60.0)!!.reason)

    @Test fun twoWeakSessionsAtTheSameWeightSuggestADeload() {
        val a = advise(listOf(log(14, 60.0, 8, 7, 6), log(7, 60.0, 8, 8, 6)), 60.0)!!
        assertEquals(AdviceKind.Deload, a.kind)
        assertEquals(55.0, a.weightKg, 0.001)
        assertEquals("Two tough sessions at 60 kg. Try a lighter 55 kg and build back up.", a.reason)
    }

    @Test fun weakSessionsAtDifferentWeightsJustHold() =
        assertEquals(AdviceKind.Hold, advise(listOf(log(14, 57.5, 8, 7, 6), log(7, 60.0, 8, 8, 6)), 60.0)!!.kind)

    @Test fun deloadIsSkippedWhenTodayIsAlreadyLighter() = assertNull(advise(listOf(log(14, 60.0, 8, 7, 6), log(7, 60.0, 8, 8, 6)), 55.0))

    @Test fun overThreeWeeksSuggestsALighterRestart() {
        val a = advise(listOf(log(25, 60.0, 8, 8, 8)), 60.0)!!
        assertEquals(AdviceKind.Restart, a.kind)
        assertEquals(55.0, a.weightKg, 0.001)
        assertEquals("It has been 3 weeks since your last session. Ease back in at 55 kg.", a.reason)
    }

    @Test fun exactlyThreeWeeksIsNotAGap() = assertEquals(AdviceKind.Increase, advise(listOf(log(21, 60.0, 8, 8, 8)), 60.0)!!.kind)

    @Test fun restartBeatsIncrease() = assertEquals(AdviceKind.Restart, advise(listOf(log(30, 80.0, 8, 8, 8)), 80.0)!!.kind)

    @Test fun poundsAreShownInPounds() {
        val a = advise(listOf(log(7, 60.0, 8, 8, 8)), 60.0, unit = WeightUnit.Lb)!!
        assertEquals("Last time all sets done at 132.3 lb. Try 137.8 lb.", a.reason)
    }

    @Test fun deloadAndRoundingHelpers() {
        assertEquals(55.0, WeightAdvisor.deloadWeight(60.0, 2.5), 0.001)
        assertEquals(14.0, WeightAdvisor.deloadWeight(16.0, 1.0), 0.001)
        assertEquals(17.5, WeightAdvisor.roundToIncrement(17.4, 2.5), 0.001)
        assertEquals(WeightAdvice(AdviceKind.Hold, 1.0, "x").actionable, false)
    }
}
