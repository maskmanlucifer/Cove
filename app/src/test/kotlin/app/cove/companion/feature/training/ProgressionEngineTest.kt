package app.cove.companion.feature.training

import app.cove.companion.feature.training.engine.ExerciseKind
import app.cove.companion.feature.training.engine.ExerciseSpec
import app.cove.companion.feature.training.engine.LoggedSet
import app.cove.companion.feature.training.engine.Move
import app.cove.companion.feature.training.engine.ProgressionEngine
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionEngineTest {
    private val bar = ExerciseSpec(ExerciseKind.Weighted, 2.5, 8, 8, 3)
    private val dumbbell = ExerciseSpec(ExerciseKind.Weighted, 2.0, 10, 12, 3)
    private val dips = ExerciseSpec(ExerciseKind.Bodyweight, 0.0, 10, 10, 3)

    private fun s(w: Double, vararg reps: Int) = reps.map { LoggedSet(w, it) }

    private fun next(spec: ExerciseSpec, vararg sessions: List<LoggedSet>, start: Double = 40.0) =
        ProgressionEngine.next(spec, sessions.toList(), start)

    @Test fun noHistoryStartsFromTheStartWeightRoundedToTheIncrement() {
        val r = next(bar, start = 41.0)
        assertEquals(Move.Start, r.move)
        assertEquals(40.0, r.target.weightKg, 0.001)
        assertEquals(8, r.target.reps)
        assertEquals(42.5, next(bar, start = 42.0).target.weightKg, 0.001)
    }

    @Test fun allSetsHitMovesUpOneIncrement() {
        val r = next(bar, s(37.5, 8, 8, 8))
        assertEquals(Move.Increase, r.move)
        assertEquals(40.0, r.target.weightKg, 0.001)
        assertEquals("All sets hit 8.", r.reason)
    }

    @Test fun lastSetShortHolds() {
        val r = next(bar, s(62.5, 8, 8, 6))
        assertEquals(Move.Hold, r.move)
        assertEquals(62.5, r.target.weightKg, 0.001)
        assertEquals("Hold. Last set was 6 reps.", r.reason)
    }

    @Test fun anotherSetShortHoldsWithItsOwnReason() {
        val r = next(bar, s(60.0, 8, 6, 8))
        assertEquals(Move.Hold, r.move)
        assertEquals("Hold. A set came up at 6 reps.", r.reason)
    }

    @Test fun repeatedMissAtTheSameWeightDeloadsAboutTenPercent() {
        val r = next(bar, s(60.0, 8, 7, 7), s(60.0, 8, 6, 6))
        assertEquals(Move.Deload, r.move)
        assertEquals(55.0, r.target.weightKg, 0.001)
        assertEquals(8, r.target.reps)
    }

    @Test fun aMissThenAHitDoesNotDeload() {
        val r = next(bar, s(60.0, 8, 7, 7), s(60.0, 8, 8, 8))
        assertEquals(Move.Increase, r.move)
        assertEquals(62.5, r.target.weightKg, 0.001)
    }

    @Test fun afterADeloadTheNextMissIsAFreshMiss() {
        val r = next(bar, s(60.0, 8, 7, 7), s(60.0, 8, 6, 6), s(55.0, 8, 7, 7))
        assertEquals(Move.Hold, r.move)
        assertEquals(55.0, r.target.weightKg, 0.001)
    }

    @Test fun rangeLiftsAddRepsBeforeWeight() {
        val first = next(dumbbell, s(16.0, 10, 10, 10))
        assertEquals(Move.Reps, first.move)
        assertEquals(16.0, first.target.weightKg, 0.001)
        assertEquals(12, first.target.reps)
        assertEquals("Aim for 12 reps first.", first.reason)
        val second = next(dumbbell, s(16.0, 10, 10, 10), s(16.0, 12, 12, 12))
        assertEquals(Move.Increase, second.move)
        assertEquals(18.0, second.target.weightKg, 0.001)
        assertEquals(10, second.target.reps)
        assertEquals("All sets hit 12.", second.reason)
    }

    @Test fun rangeLiftMissingTheRaisedGoalHoldsAtThatGoal() {
        val r = next(dumbbell, s(16.0, 10, 10, 10), s(16.0, 12, 11, 10))
        assertEquals(Move.Hold, r.move)
        assertEquals(12, r.target.reps)
    }

    @Test fun bodyweightAddsTwoReps() {
        val r = next(dips, s(0.0, 10, 10, 10))
        assertEquals(Move.Reps, r.move)
        assertEquals(12, r.target.reps)
        assertEquals("Two more reps each set.", r.reason)
        assertEquals("3×10 → 3×12", TrainingText.change(dips, next(dips).target, r.target, WeightUnit.Kg).let { it.first + it.second })
    }

    @Test fun bodyweightMissHoldsAndTwoMissesEaseBack() {
        assertEquals(Move.Hold, next(dips, s(0.0, 10, 9, 8)).move)
        val r = next(dips, s(0.0, 10, 9, 8), s(0.0, 10, 8, 7))
        assertEquals(Move.Deload, r.move)
        assertEquals(9, r.target.reps)
    }

    @Test fun fewerSetsThanPlannedHoldsWithoutCountingAsAMiss() {
        val r = next(bar, s(60.0, 8, 8))
        assertEquals(Move.Hold, r.move)
        assertEquals("Hold. 2 of 3 sets last time.", r.reason)
        assertEquals(Move.Increase, next(bar, s(60.0, 8, 8), s(60.0, 8, 8, 8)).move)
    }

    @Test fun warmUpSetsAreIgnored() {
        val r = next(bar, listOf(LoggedSet(20.0, 10), LoggedSet(60.0, 8), LoggedSet(60.0, 8), LoggedSet(60.0, 8)))
        assertEquals(62.5, r.target.weightKg, 0.001)
    }

    @Test fun liftingMoreThanTheTargetBuildsOnWhatWasLifted() {
        val r = next(bar, s(60.0, 8, 8, 8), s(70.0, 8, 8, 8))
        assertEquals(72.5, r.target.weightKg, 0.001)
    }

    @Test fun roundingAndDeloadHelpers() {
        assertEquals(57.5, ProgressionEngine.roundToIncrement(56.25, 2.5), 0.001)
        assertEquals(60.0, ProgressionEngine.roundToIncrement(60.9, 2.5), 0.001)
        assertEquals(55.0, ProgressionEngine.deloadWeight(62.5, 2.5), 0.001)
        assertEquals(17.0, ProgressionEngine.deloadWeight(18.0, 2.0), 0.001 + 1.0)
        assertEquals(2.5, ProgressionEngine.deloadWeight(2.5, 2.5) + 2.5, 0.001)
    }

    @Test fun poundsUseTheirOwnFormatting() {
        val kg = WeightUnit.Lb.toKg(135.0)
        assertEquals("135", WeightFormat.number(kg, WeightUnit.Lb))
        assertEquals("62.5 kg", WeightFormat.withUnit(62.5))
        assertEquals("40", WeightFormat.number(40.0))
        assertEquals("62.5 × 8", WeightFormat.set(62.5, 8, withUnit = false))
        assertEquals(62.5, WeightFormat.parse("62,5")!!, 0.001)
    }

    @Test fun poundSteppingRoundTripsThroughKilograms() {
        val unit = WeightUnit.Lb
        val kg = unit.toKg(unit.fromKg(unit.toKg(135.0)) + unit.stepperStep)
        assertEquals("140", WeightFormat.number(kg, unit))
    }
}
