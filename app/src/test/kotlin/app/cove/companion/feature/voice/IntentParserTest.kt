package app.cove.companion.feature.voice

import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.ai.AiGateway
import app.cove.companion.data.ai.OnDeviceLlm
import app.cove.companion.feature.voice.intent.IntentJson
import app.cove.companion.feature.voice.intent.IntentParser
import app.cove.companion.feature.voice.intent.Layer
import app.cove.companion.feature.voice.intent.ParseContext
import app.cove.companion.feature.voice.intent.ParseOutcome
import app.cove.companion.feature.voice.intent.TodoDraft
import app.cove.companion.feature.voice.intent.VoiceIntent
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserTest {
    private val clock = Clock { LocalDateTime.of(2026, 10, 6, 10, 35).toEpochMillis() }

    private class Llm(val available: Boolean, val reply: String?) : OnDeviceLlm {
        var calls = 0
        override suspend fun isAvailable() = available
        override suspend fun generate(prompt: String): String? { calls++; return reply }
    }

    private class Gw(val reply: String?) : AiGateway {
        var calls = 0
        override val enabled = true
        override suspend fun parseIntent(transcript: String, now: String, zone: String, todoCategories: List<String>): String? { calls++; return reply }
    }

    private fun parse(p: IntentParser, text: String, ctx: ParseContext = ParseContext()) = runBlocking { p.parse(text, ctx) }

    private val todoJson = """{"intents":[{"type":"add_todo","items":[{"title":"Walk the dog","category":null}]}]}"""

    @Test
    fun rulesWinAndSkipModels() {
        val llm = Llm(true, todoJson)
        val gw = Gw(todoJson)
        val out = parse(IntentParser(clock, llm, gw), "remind me to call mum at 6") as ParseOutcome.Understood
        assertEquals(Layer.Rules, out.layer)
        assertEquals(0, llm.calls + gw.calls)
    }

    @Test
    fun nanoThenCloudThenPartial() {
        val unknown = "could you maybe sort the dog situation"
        val nano = parse(IntentParser(clock, Llm(true, todoJson), Gw(null)), unknown) as ParseOutcome.Understood
        assertEquals(Layer.OnDevice, nano.layer)
        val cloud = parse(IntentParser(clock, Llm(true, "garbage"), Gw(todoJson)), unknown) as ParseOutcome.Understood
        assertEquals(Layer.Cloud, cloud.layer)
        val background = parse(IntentParser(clock, Llm(true, todoJson), null), unknown, ParseContext(foreground = false))
        assertTrue(background is ParseOutcome.Partial)
    }

    @Test
    fun cloudNeverSeesJournalOrLongText() {
        val journal = """{"intents":[{"type":"journal_note","text":"x"}]}"""
        val gw = Gw(journal)
        assertTrue(parse(IntentParser(clock, null, gw), "what a strange day") is ParseOutcome.Partial)
        val long = "so " + "many words ".repeat(30)
        parse(IntentParser(clock, null, gw), long)
        assertEquals(1, gw.calls)
    }

    @Test
    fun partialOffersGuesses() {
        val out = parse(IntentParser(clock, null, null), "…mind me… the… at four…") as ParseOutcome.Partial
        assertEquals(2, out.guesses.size)
    }

    @Test
    fun jsonValidation() {
        assertEquals(listOf<VoiceIntent>(VoiceIntent.AddTodos(listOf(TodoDraft("Milk", "Shopping")))),
            IntentJson.parse("```json\n{\"intents\":[{\"type\":\"add_todo\",\"items\":[{\"title\":\"Milk\",\"category\":\"Shopping\"}]}]}\n```"))
        assertEquals(listOf<VoiceIntent>(VoiceIntent.SetAlarm(390, "Alarm", 0b0000011)),
            IntentJson.parse("""{"intents":[{"type":"set_alarm","time":"06:30","days":["mon","tue"]}]}"""))
        assertEquals(34000L, (IntentJson.parse("""{"intents":[{"type":"log_expense","amount":340}]}""")!!.single() as VoiceIntent.LogExpense).amountPaise)
        listOf(
            "not json", "{}", """{"intents":[{"type":"explode"}]}""", """{"intents":[{"type":"set_alarm","time":"25:00"}]}""",
            """{"intents":[{"type":"log_expense","amount":-1}]}""", """{"intents":[{"type":"add_todo","items":[]}]}""",
            """{"intents":[{"type":"set_alarm","time":"06:30","days":["funday"]}]}""", """{"intents":["x"]}""",
        ).forEach { assertNull(it, IntentJson.parse(it)) }
    }
}
