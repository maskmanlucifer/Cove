package app.cove.companion.feature.voice.intent

import app.cove.companion.core.toEpochMillis
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The strict JSON contract between the app and any language model (Gemini Nano or the cloud gateway).
 * Anything that does not validate is rejected whole so the caller can fall to the next layer.
 */
object IntentJson {
    private val json = Json { isLenient = false }
    private const val MAX_INTENTS = 5
    private const val MAX_ITEMS = 12
    private const val MAX_TEXT = 200
    private val dayNames = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    /** Instructions shared by the on-device prompt and the gateway; [now] is local ISO date-time. */
    fun systemPrompt(now: String, todoCategories: List<String>): String = """
        You turn one spoken command into JSON. Reply with JSON only, no prose, exactly this shape:
        {"intents":[ ... ]} where each intent is one of:
        {"type":"set_alarm","time":"HH:mm","label":"","days":[]}   days: any of mon,tue,wed,thu,fri,sat,sun; empty = once
        {"type":"change_alarm","time":"HH:mm","label":null}
        {"type":"add_todo","items":[{"title":"Milk","category":"Shopping"}]}   category: one of ${todoCategories.joinToString()} or null
        {"type":"add_reminder","title":"Call mum","at":"yyyy-MM-ddTHH:mm"}   at: local time or null
        {"type":"log_expense","amount":340,"category":"Food","paid_with":"UPI","note":"Lunch","received":false}   amount in rupees
        {"type":"log_habit","name":"Walk"}
        {"type":"journal_note","text":"..."}
        {"type":"query_next"}
        {"type":"undo_last"}
        The current local time is $now. Use 24-hour times. If the command is unclear, reply {"intents":[]}.
    """.trimIndent()

    /** Parses and validates a model reply; null when it is not valid JSON of the schema above. */
    fun parse(raw: String): List<VoiceIntent>? {
        val root = runCatching { json.parseToJsonElement(unfence(raw)) }.getOrNull() as? JsonObject ?: return null
        val list = (root["intents"] as? JsonArray) ?: return null
        if (list.size > MAX_INTENTS) return null
        val out = list.map { intent(it as? JsonObject ?: return null) ?: return null }
        return out
    }

    private fun unfence(s: String) = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    private fun intent(o: JsonObject): VoiceIntent? = when (o.str("type")) {
        "set_alarm" -> {
            val minutes = o.time("time")
            val days = o["days"]?.let { d -> (d as? JsonArray)?.let(::mask) ?: return null } ?: 0
            if (minutes == null) null else VoiceIntent.SetAlarm(minutes, o.optStr("label").orEmpty().ifBlank { "Alarm" }, days)
        }
        "change_alarm" -> o.time("time")?.let { VoiceIntent.ChangeAlarm(it, o.optStr("label")) }
        "add_todo" -> {
            val items = (o["items"] as? JsonArray)?.takeIf { it.size in 1..MAX_ITEMS }?.map { e ->
                val item = e as? JsonObject ?: return null
                val title = item.str("title")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT } ?: return null
                TodoDraft(title, item.optStr("category"))
            }
            items?.let { VoiceIntent.AddTodos(it) }
        }
        "add_reminder" -> {
            val title = o.str("title")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }
            val atRaw = o.optStr("at")
            val at = atRaw?.let { runCatching { LocalDateTime.parse(it).toEpochMillis() }.getOrNull() ?: return null }
            title?.let { VoiceIntent.AddReminder(it, at) }
        }
        "log_expense" -> {
            val amount = (o["amount"] as? JsonPrimitive)?.doubleOrNull
            if (amount == null || amount <= 0 || amount > 10_000_000) null
            else VoiceIntent.LogExpense(
                Math.round(amount * 100), o.optStr("category"), o.optStr("paid_with"),
                o.optStr("note").orEmpty(), (o["received"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        }
        "log_habit" -> o.str("name")?.trim()?.takeIf { it.isNotEmpty() }?.let { VoiceIntent.LogHabit(it) }
        "journal_note" -> o.str("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { VoiceIntent.JournalNote(it) }
        "query_next" -> VoiceIntent.QueryNext
        "undo_last" -> VoiceIntent.UndoLast
        else -> null
    }

    private fun mask(days: JsonArray): Int? {
        var m = 0
        for (d in days) {
            val i = dayNames.indexOf((d as? JsonPrimitive)?.takeIf { it.isString }?.content?.lowercase() ?: return null)
            if (i < 0) return null
            m = m or (1 shl i)
        }
        return m
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.optStr(key: String): String? = when (val v = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun JsonObject.time(key: String): Int? {
        val m = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(str(key) ?: return null) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }
}
