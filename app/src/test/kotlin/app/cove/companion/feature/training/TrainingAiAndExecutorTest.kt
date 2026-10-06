package app.cove.companion.feature.training

import app.cove.companion.ai.model.AdviceRequest
import app.cove.companion.ai.model.AdviceSession
import app.cove.companion.ai.model.SpokenSet
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.prompt.AdvicePrompt
import app.cove.companion.ai.prompt.PromptLimits
import app.cove.companion.ai.schema.AdviceSchema
import app.cove.companion.ai.schema.IntentSchema
import app.cove.companion.feature.training.voice.BodyWeightUndo
import app.cove.companion.feature.training.voice.LogSnap
import app.cove.companion.feature.training.voice.LogUndo
import app.cove.companion.feature.training.voice.PlanSnap
import app.cove.companion.feature.training.voice.TrainingUndo
import app.cove.companion.feature.voice.describe
import app.cove.companion.feature.voice.resultHeadline
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingAiAndExecutorTest {
    private val today = LocalDate.of(2026, 10, 6)

    @Test fun planExerciseSchema() {
        val r = IntentSchema.parse("""{"intents":[{"type":"plan_exercise","day":"2026-10-07","exercise":"Bench press","weight":60,"sets":3,"reps":8,"unit":null},
            {"type":"plan_exercise","day":"2026-10-07","exercise":"Squat"}]}""")!!
        assertEquals(VoiceIntent.PlanExercise(LocalDate.of(2026, 10, 7), "Bench press", 60.0, 3, 8), r[0])
        assertEquals(VoiceIntent.PlanExercise(LocalDate.of(2026, 10, 7), "Squat"), r[1])
    }

    @Test fun planExerciseSchemaRejectsBadDaysAndCounts() {
        assertNull(IntentSchema.parse("""{"intents":[{"type":"plan_exercise","day":"tomorrow","exercise":"Squat"}]}"""))
        assertNull(IntentSchema.parse("""{"intents":[{"type":"plan_exercise","day":"2026-10-07","exercise":"Squat","sets":0}]}"""))
        assertNull(IntentSchema.parse("""{"intents":[{"type":"plan_exercise","day":"2026-10-07","exercise":"Squat","weight":-5}]}"""))
        assertNull(IntentSchema.parse("""{"intents":[{"type":"plan_exercise","day":"2026-10-07"}]}"""))
    }

    @Test fun changeWeightSchema() {
        assertEquals(listOf(VoiceIntent.ChangeWeight("Bench press", 62.5, "kg")), IntentSchema.parse("""{"intents":[{"type":"change_weight","exercise":"Bench press","weight":62.5,"unit":"kg"}]}"""))
        assertNull(IntentSchema.parse("""{"intents":[{"type":"change_weight","exercise":"Bench press","weight":"heavy"}]}"""))
    }

    @Test fun retiredStartWorkoutIsRejected() = assertNull(IntentSchema.parse("""{"intents":[{"type":"start_workout","day":null}]}"""))

    @Test fun adviceSchemaAcceptsOneSentenceOnly() {
        assertEquals("Go up a little.", AdviceSchema.parse("""{"s":"Go up a little."}"""))
        assertEquals("Fenced.", AdviceSchema.parse("```json\n{\"s\":\"Fenced.\"}\n```"))
        assertNull(AdviceSchema.parse("""{"s":""}"""))
        assertNull(AdviceSchema.parse("""{"s":"${"x".repeat(201)}"}"""))
        assertNull(AdviceSchema.parse("""{"t":"no"}"""))
        assertNull(AdviceSchema.parse("plain text"))
    }

    @Test fun advicePromptCarriesOnlyWorkoutFactsAndFitsNano() {
        val r = AdviceRequest("Bench press", 60.0, 3, 8, List(10) { AdviceSession(it * 7, 60.0, listOf(8, 8, 8)) }, "Try 62.5 kg.")
        val user = AdvicePrompt.user(r)
        assertEquals(AdviceRequest.MAX_SESSIONS, Regex("daysAgo").findAll(user).count())
        assertTrue(AdvicePrompt.nano(r)!!.length <= PromptLimits.NANO_MAX_PROMPT_CHARS)
        assertTrue(AdvicePrompt.SYSTEM.contains("{\"s\""))
    }

    @Test fun draftsShowTheDayClearly() {
        val tomorrow = today.plusDays(1)
        val a = VoiceIntent.PlanExercise(tomorrow, "Bench press", 60.0, 3, 8, "kg")
        val b = VoiceIntent.PlanExercise(tomorrow, "Overhead press", 40.0, null, 8, "kg")
        assertEquals("Tomorrow: Bench press · 60 kg · 3 x 8", describe(a, today))
        assertEquals("Tomorrow: Overhead press · 40 kg · 3 x 8", describe(b, today))
        assertEquals("Thursday: Squat", describe(VoiceIntent.PlanExercise(today.plusDays(2), "Squat"), today))
        assertEquals("Tomorrow." to " Two exercises. Check the day.", resultHeadline(listOf(a, b), today))
        assertEquals("Your plan." to " Two exercises. Check the days.", resultHeadline(listOf(a, b.copy(date = today)), today))
        assertEquals("Today: Bench press to 62.5 kg", describe(VoiceIntent.ChangeWeight("Bench press", 62.5), today))
        assertEquals("Today: Squat · 80 kg × 5, 85 kg × 3", describe(VoiceIntent.LogSets("Squat", listOf(SpokenSet(80.0, 5), SpokenSet(85.0, 3))), today))
        assertEquals("Today: Bench press · 62.5 kg · 8, 6 reps", describe(VoiceIntent.LogSets("Bench press", listOf(SpokenSet(62.5, 8), SpokenSet(62.5, 6))), today))
    }

    @Test fun undoPayloadRoundTripsAndOldOnesStillDecode() {
        val u = TrainingUndo(
            created = listOf("a"), changed = listOf(PlanSnap("b", 60.0, 3, 8)),
            logs = listOf(LogUndo(20000, "Bench press", LogSnap(60.0, 3, 8, "8,8,8"))), bodyWeights = listOf(BodyWeightUndo(20000, null)),
        )
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(u, json.decodeFromString<TrainingUndo>(json.encodeToString(u)))
        assertEquals(TrainingUndo(), json.decodeFromString<TrainingUndo>("""{"sets":["x"],"sessions":["y"]}"""))
        assertTrue(TrainingUndo().isEmpty)
        assertEquals(TrainingUndo(created = listOf("a", "c")), TrainingUndo(created = listOf("a")) + TrainingUndo(created = listOf("c")))
    }
}
