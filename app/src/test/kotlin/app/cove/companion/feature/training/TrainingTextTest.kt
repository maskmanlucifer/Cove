package app.cove.companion.feature.training

import app.cove.companion.feature.training.engine.ExerciseKind
import app.cove.companion.feature.training.engine.ExerciseSpec
import app.cove.companion.feature.training.engine.LoggedSet
import app.cove.companion.feature.training.engine.Move
import app.cove.companion.feature.training.engine.ProgressionEngine
import app.cove.companion.feature.training.engine.RestState
import app.cove.companion.feature.training.engine.SessionFlow
import app.cove.companion.feature.training.engine.TrainingText
import app.cove.companion.feature.training.engine.WeightUnit
import app.cove.companion.feature.training.engine.BodyWeightInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTextTest {
    private val bar = ExerciseSpec(ExerciseKind.Weighted, 2.5, 8, 8, 3)

    @Test fun draftNoteMatchesFrame44() {
        val sets = listOf(LoggedSet(62.5, 8), LoggedSet(62.5, 8), LoggedSet(62.5, 6))
        val next = ProgressionEngine.next(bar, listOf(sets), 40.0)
        assertEquals("The last set came up short, so next time stays at 62.5 kg.", TrainingText.draftNote(next, sets, 8, WeightUnit.Kg))
    }

    @Test fun draftNoteForAGoodSessionAndForReps() {
        val good = listOf(LoggedSet(60.0, 8), LoggedSet(60.0, 8), LoggedSet(60.0, 8))
        val up = ProgressionEngine.next(bar, listOf(good), 40.0)
        assertEquals(Move.Increase, up.move)
        assertEquals("Every set landed, so next time goes up to 62.5 kg.", TrainingText.draftNote(up, good, 8, WeightUnit.Kg))
    }

    @Test fun estimateMinutesRoundsToFive() {
        assertEquals(40, TrainingText.estimateMinutes(12, 4, 90))
        assertEquals(35, TrainingText.estimateMinutes(9, 3, 120).let { if (it == 35) 35 else it })
        assertEquals(0, TrainingText.estimateMinutes(0, 0, 90))
    }

    @Test fun clockAndSpokenText() {
        assertEquals("1:24", TrainingText.clock(84))
        assertEquals("0:00", TrainingText.clock(-5))
        assertEquals("1 minute 24 seconds", TrainingText.spokenClock(84))
        assertEquals("30 seconds", TrainingText.spokenClock(30))
        assertEquals("7 pm", TrainingText.timeLabel(19 * 60))
        assertEquals("6:30 am", TrainingText.timeLabel(6 * 60 + 30))
    }

    @Test fun sayHintSpeaksTheNumbers() {
        assertEquals("Or say “thirty-seven five for eight”", TrainingText.sayHint(37.5, 8, false))
        assertEquals("Or say “forty for ten”", TrainingText.sayHint(40.0, 10, false))
        assertEquals("Or say “twelve reps”", TrainingText.sayHint(0.0, 12, true))
        assertEquals("one hundred and five", TrainingText.spokenNumber(105))
    }

    @Test fun restStateCountsDownFromAnAbsoluteEnd() {
        val r = RestState(100_000, "set 3")
        assertEquals(84, r.remainingSeconds(16_000))
        assertEquals(1, r.remainingSeconds(99_001))
        assertEquals(0, r.remainingSeconds(100_000))
        assertFalse(r.isOver(99_999))
        assertTrue(r.isOver(100_000))
        assertEquals(114, r.extended(30).remainingSeconds(16_000))
        assertEquals(0, r.remainingSeconds(500_000))
    }

    @Test fun sessionPositionFollowsLoggedSetsAndSkips() {
        val order = listOf("a", "b", "c")
        val planned = mapOf("a" to 3, "b" to 3, "c" to 3)
        assertEquals(1, SessionFlow.position(order, emptySet(), planned, emptyMap()).setNo)
        val mid = SessionFlow.position(order, emptySet(), planned, mapOf("a" to 3, "b" to 2))
        assertEquals("b", mid.exerciseId)
        assertEquals(3, mid.setNo)
        assertEquals(1, mid.index)
        assertEquals("c", SessionFlow.position(order, setOf("b"), planned, mapOf("a" to 3)).exerciseId)
        assertTrue(SessionFlow.position(order, setOf("c"), planned, mapOf("a" to 3, "b" to 3)).finished)
    }

    @Test fun bodyWeightKeypad() {
        assertEquals("68", BodyWeightInput.push(BodyWeightInput.push("", '6'), '8'))
        assertEquals("68.4", BodyWeightInput.push(BodyWeightInput.push("68", '.'), '4'))
        assertEquals("68.4", BodyWeightInput.push("68.4", '5'))
        assertEquals("0.", BodyWeightInput.push("", '.'))
        assertEquals("100", BodyWeightInput.push("100", '1'))
        assertEquals("6", BodyWeightInput.back("68"))
        assertEquals(68.4, BodyWeightInput.value("68.4", WeightUnit.Kg)!!, 0.001)
        assertNull(BodyWeightInput.value("5", WeightUnit.Kg))
        assertNull(BodyWeightInput.value("", WeightUnit.Kg))
    }
}
