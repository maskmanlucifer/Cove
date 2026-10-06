package app.cove.companion.feature.training

import app.cove.companion.ai.model.SpokenSet
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.model.ExerciseNames
import app.cove.companion.ai.provider.rules.RuleParser
import app.cove.companion.ai.provider.rules.SpokenSets
import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SpokenSetsTest {
    private val parser = RuleParser(Clock { LocalDateTime.of(2026, 10, 6, 10, 35).toEpochMillis() })
    private val mine = listOf("Bench press", "Overhead press", "Incline dumbbell", "Barbell row", "Squat", "Romanian deadlift", "Dips")

    private fun sets(text: String, known: List<String> = emptyList()) = SpokenSets.parse(text, known)

    private fun same(weight: Double?, vararg reps: Int) = reps.map { SpokenSet(weight, it) }

    @Test fun frame44Phrase() {
        val p = sets("Bench, sixty-two and a half for eight, eight and six")!!
        assertEquals("Bench press", p.exercise)
        assertEquals(listOf(SpokenSet(62.5, 8), SpokenSet(62.5, 8), SpokenSet(62.5, 6)), p.sets)
    }

    @Test fun frame42Phrase() {
        val p = sets("overhead press thirty-seven five for eight")!!
        assertEquals("Overhead press", p.exercise)
        assertEquals(same(37.5, 8), p.sets)
    }

    @Test fun setsOfRepsAtWeight() {
        assertEquals(same(60.0, 8, 8, 8), sets("three sets of eight at sixty bench")!!.sets)
        assertEquals(same(60.0, 8, 8, 8), sets("squat 3 x 8 at 60")!!.sets)
        assertEquals(same(60.0, 8, 8, 8), sets("bench 60 for 8 for 3 sets")!!.sets)
        assertEquals(same(null, 10, 10), sets("dips two by ten")!!.sets)
    }

    @Test fun simpleForms() {
        assertEquals(same(60.0, 8), sets("bench 60 for 8")!!.sets)
        assertEquals(same(60.0, 8), sets("bench 60 x 8")!!.sets)
        assertEquals(same(60.0, 8, 8, 6), sets("bench 60 8 8 6")!!.sets)
        assertEquals(same(null, 12, 10, 8), sets("dips 12, 10 and 8")!!.sets)
        assertEquals(same(65.5, 5), sets("squat sixty five point five for five")!!.sets)
    }

    @Test fun changingWeightsInOneBreath() {
        assertEquals(listOf(SpokenSet(60.0, 8), SpokenSet(62.5, 6)), sets("bench 60 for 8 then 62.5 for 6")!!.sets)
    }

    @Test fun unitsAreRemembered() {
        assertEquals("lb", sets("bench 135 pounds for 8")!!.unit)
        assertEquals("kg", sets("bench 60 kilos for 8")!!.unit)
        assertNull(sets("bench 60 for 8")!!.unit)
    }

    @Test fun needsALiftAndSets() {
        assertNull(sets("spent 500 for lunch"))
        assertNull(sets("bench"))
        assertNull(sets("remind me at 6"))
    }

    @Test fun aliasesFindTheLift() {
        listOf("ohp" to "Overhead press", "press" to "Overhead press", "rdl" to "Romanian deadlift", "row" to "Barbell row", "squats" to "Squat",
            "bench" to "Bench press", "incline" to "Incline dumbbell", "dip" to "Dips", "pull ups" to "Pull-ups").forEach { (said, name) ->
            assertEquals(said, name, ExerciseNames.find("$said 60 for 8", emptyList())?.name)
        }
    }

    @Test fun usersOwnNamesWin() {
        assertEquals("Incline dumbbell", ExerciseNames.find("incline 20 for 10", mine)?.name)
        assertEquals("Bench press", ExerciseNames.find("flat bench", mine)?.name)
        assertEquals("Romanian deadlift", ExerciseNames.find("romanian", mine)?.name)
        assertEquals("Bench press", ExerciseNames.find("bench pres 60", mine)?.name)
        assertEquals("Pull-ups", ExerciseNames.find("chin ups", mine)?.name)
        assertEquals("Bench press", ExerciseNames.resolve("bench", mine))
    }

    @Test fun parserProducesTrainingIntents() {
        val log = parser.parse("Bench, sixty-two and a half for eight, eight and six", exercises = mine).single() as VoiceIntent.LogSets
        assertEquals("Bench press", log.exercise)
        assertEquals(3, log.sets.size)
        assertEquals(VoiceIntent.StartWorkout(null), parser.parse("start workout").single())
        assertEquals(VoiceIntent.StartWorkout("Legs"), parser.parse("let's start leg day").single())
        assertEquals(VoiceIntent.StartWorkout("Push"), parser.parse("start my push day").single())
        assertEquals(VoiceIntent.LogBodyWeight(68.4, null), parser.parse("weigh in sixty-eight point four").single())
        assertEquals(VoiceIntent.LogBodyWeight(150.0, "lb"), parser.parse("I weigh 150 pounds").single())
        assertEquals(VoiceIntent.QueryNextWorkout, parser.parse("what's my next workout").single())
        assertEquals(VoiceIntent.QueryNextWorkout, parser.parse("when is my next workout?").single())
    }

    @Test fun otherCommandsAreUnaffected() {
        assertEquals(VoiceIntent.QueryNext, parser.parse("what's next").single())
        assert(parser.parse("spent 500 on lunch").single() is VoiceIntent.LogExpense)
        assert(parser.parse("add milk to my shopping list").single() is VoiceIntent.AddTodos)
    }
}
