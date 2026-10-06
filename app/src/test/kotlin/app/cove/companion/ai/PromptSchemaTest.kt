package app.cove.companion.ai

import app.cove.companion.ai.model.BriefRequest
import app.cove.companion.ai.model.IntentContext
import app.cove.companion.ai.model.IntentRequest
import app.cove.companion.ai.model.Summary
import app.cove.companion.ai.model.TodoDraft
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.ai.prompt.BriefPrompt
import app.cove.companion.ai.prompt.IntentPrompt
import app.cove.companion.ai.prompt.JournalPrompt
import app.cove.companion.ai.prompt.PromptLimits
import app.cove.companion.ai.schema.BriefSchema
import app.cove.companion.ai.schema.IntentSchema
import app.cove.companion.ai.schema.JournalSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptSchemaTest {
    private val ctx = IntentContext(todoCategories = listOf("Home", "Shopping"))
    private val now = "2026-10-06T10:35"

    @Test fun intentSystemPromptHasNoUserContentAndFitsNano() {
        val system = IntentPrompt.system(now, ctx.todoCategories)
        assertTrue(system.length < 2_000)
        assertTrue(system.contains(now) && system.contains("Home, Shopping"))
        IntentSchema.parse("""{"intents":[{"type":"undo_last"}]}""")
        listOf("set_alarm", "change_alarm", "add_todo", "add_reminder", "log_expense", "log_habit", "journal_note", "query_next", "undo_last")
            .forEach { assertTrue(it, system.contains("\"$it\"")) }
    }

    @Test fun nanoIntentPromptContainsTheTranscriptOnceAndRespectsTheBudget() {
        val prompt = IntentPrompt.nano(IntentRequest("walk the dog", ctx, now, "UTC"))!!
        assertEquals(1, Regex("walk the dog").findAll(prompt).count())
        assertTrue(prompt.length <= PromptLimits.NANO_MAX_PROMPT_CHARS)
        assertNull(IntentPrompt.nano(IntentRequest("x".repeat(PromptLimits.NANO_MAX_PROMPT_CHARS), ctx, now, "UTC")))
    }

    @Test fun cloudSystemCarriesTheTimeZone() {
        assertTrue(IntentPrompt.cloudSystem(IntentRequest("x", ctx, now, "Asia/Kolkata")).endsWith("Time zone: Asia/Kolkata."))
    }

    @Test fun briefPromptsAreSmallAndStructured() {
        val request = BriefRequest("intro", mapOf("weather" to "sunny, 24 degrees"))
        assertTrue(BriefPrompt.SYSTEM.length < 500)
        assertTrue(BriefPrompt.user(request).contains("\"kind\":\"intro\""))
        assertTrue(BriefPrompt.nano(request)!!.length <= PromptLimits.NANO_MAX_PROMPT_CHARS)
        assertNull(BriefPrompt.nano(BriefRequest("intro", mapOf("k" to "v".repeat(PromptLimits.NANO_MAX_PROMPT_CHARS)))))
    }

    @Test fun journalPromptsCutLongTextBelowNanoInputLimit() {
        val huge = "a".repeat(50_000)
        listOf(JournalPrompt.summary(huge), JournalPrompt.tags(huge), JournalPrompt.mood(huge)).forEach {
            assertTrue(it.length < PromptLimits.JOURNAL_TEXT_CHARS + 400)
            assertTrue(it.length < PromptLimits.NANO_MAX_PROMPT_CHARS)
        }
        assertTrue(JournalPrompt.mood("x").contains(Summary.MOODS.joinToString(", ")))
        assertTrue(JournalPrompt.CAPTION.length < 100)
    }

    @Test fun intentJsonValidation() {
        assertEquals(listOf<VoiceIntent>(VoiceIntent.AddTodos(listOf(TodoDraft("Milk", "Shopping")))),
            IntentSchema.parse("```json\n{\"intents\":[{\"type\":\"add_todo\",\"items\":[{\"title\":\"Milk\",\"category\":\"Shopping\"}]}]}\n```"))
        assertEquals(listOf<VoiceIntent>(VoiceIntent.SetAlarm(390, "Alarm", 0b0000011)),
            IntentSchema.parse("""{"intents":[{"type":"set_alarm","time":"06:30","days":["mon","tue"]}]}"""))
        assertEquals(34000L, (IntentSchema.parse("""{"intents":[{"type":"log_expense","amount":340}]}""")!!.single() as VoiceIntent.LogExpense).amountPaise)
        listOf(
            "not json", "{}", """{"intents":[{"type":"explode"}]}""", """{"intents":[{"type":"set_alarm","time":"25:00"}]}""",
            """{"intents":[{"type":"log_expense","amount":-1}]}""", """{"intents":[{"type":"add_todo","items":[]}]}""",
            """{"intents":[{"type":"set_alarm","time":"06:30","days":["funday"]}]}""", """{"intents":["x"]}""",
        ).forEach { assertNull(it, IntentSchema.parse(it)) }
    }

    @Test fun cloudReplyValidation() {
        assertNotNull(IntentSchema.validateCloud("""{"intents":[{"type":"undo_last"}],"confidence":0.9}"""))
        assertNull(IntentSchema.validateCloud("""{"intents":[{"type":"launch"}]}"""))
        assertNull(IntentSchema.validateCloud("""{"intents":[{"type":"undo_last"}],"confidence":0.2}"""))
        assertNull(IntentSchema.validateCloud("""{"intents":[]}"""))
        assertNull(IntentSchema.validateCloud("nope"))
    }

    @Test fun briefLineValidation() {
        assertEquals("Hello.", BriefSchema.validateLine("""{"text":" Hello. "}"""))
        assertEquals("Hi", BriefSchema.validateLine("```json\n{\"text\":\"Hi\"}\n```"))
        assertNull(BriefSchema.validateLine("""{"text":""}"""))
        assertNull(BriefSchema.validateLine("""{"text":"${"a".repeat(BriefSchema.MAX_CHARS)}"}"""))
        assertNull(BriefSchema.validateLine("plain text"))
    }

    @Test fun journalAnswersAreCleaned() {
        assertEquals(listOf("calm", "work", "focus"), JournalSchema.tags("Calm, Work\n#focus."))
        assertEquals(5, JournalSchema.tags("a,b,c,d,e,f,g").size)
        assertEquals("tired", JournalSchema.mood("Feeling Tired today"))
        assertNull(JournalSchema.mood("happy"))
        assertFalse(Summary.MOODS.isEmpty())
    }
}
