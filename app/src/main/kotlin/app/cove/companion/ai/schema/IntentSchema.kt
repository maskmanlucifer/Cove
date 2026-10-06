package app.cove.companion.ai.schema

import app.cove.companion.ai.model.SpokenSet
import app.cove.companion.ai.model.TodoDraft
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.core.toEpochMillis
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * The strict JSON contract between the app and any language model (Gemini Nano or the cloud). The matching prompt
 * is `IntentPrompt`. Anything that does not validate is rejected whole so the router can fall to the next provider.
 */
object IntentSchema {
    private val json = Json { isLenient = false }
    private const val MAX_INTENTS = 5
    private const val MAX_ITEMS = 12
    private const val MAX_TEXT = 200
    private val dayNames = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    /** Parses and validates a model reply; null when it is not valid JSON of the schema above. */
    fun parse(raw: String): List<VoiceIntent>? {
        val root = runCatching { json.parseToJsonElement(Unfence.of(raw)) }.getOrNull() as? JsonObject ?: return null
        val list = (root["intents"] as? JsonArray) ?: return null
        if (list.size > MAX_INTENTS) return null
        val out = list.map { intent(it as? JsonObject ?: return null) ?: return null }
        return out
    }


    /**
     * Pre-check of a cloud reply, same rules as the Edge Function: at most five known intents, and unclear or
     * low-confidence answers are dropped. Returns the normalised JSON text, or null.
     */
    fun validateCloud(raw: String): String? {
        val obj = runCatching { json.parseToJsonElement(Unfence.of(raw)) as? JsonObject }.getOrNull() ?: return null
        val intents = obj["intents"] as? JsonArray ?: return null
        if (intents.isEmpty() || intents.size > MAX_INTENTS) return null
        if (intents.any { i -> ((i as? JsonObject)?.get("type") as? JsonPrimitive)?.content !in TYPES }) return null
        val confidence = ((obj["confidence"] as? JsonPrimitive)?.doubleOrNull ?: 0.8).coerceIn(0.0, 1.0)
        if (confidence < 0.5) return null
        return buildJsonObject {
            put("intents", intents)
            put("confidence", confidence)
        }.toString()
    }

    private val TYPES = setOf(
        "set_alarm", "change_alarm", "add_todo", "add_reminder", "log_expense",
        "log_habit", "journal_note", "query_next", "undo_last",
        "log_sets", "plan_exercise", "change_weight", "log_body_weight", "next_workout",
    )

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
        "log_sets" -> logSets(o)
        "plan_exercise" -> planExercise(o)
        "change_weight" -> {
            val name = o.str("exercise")?.trim()?.takeIf { it.isNotEmpty() && it.length <= 60 }
            val w = (o["weight"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it in 0.0..1000.0 }
            if (o.optStr("unit") != null && unit(o) == null) return null
            if (name == null || w == null) null else VoiceIntent.ChangeWeight(name, w, unit(o))
        }
        "log_body_weight" -> {
            val w = (o["weight"] as? JsonPrimitive)?.doubleOrNull
            val unit = unit(o)
            if (o.optStr("unit") != null && unit == null) return null
            val kg = if (unit == "lb") w?.div(2.2046) else w
            if (w == null || kg == null || kg < 20 || kg > 400) null else VoiceIntent.LogBodyWeight(w, unit)
        }
        "next_workout" -> VoiceIntent.QueryNextWorkout
        "query_next" -> VoiceIntent.QueryNext
        "undo_last" -> VoiceIntent.UndoLast
        else -> null
    }

    private const val MAX_SETS = 20

    /** `{"type":"plan_exercise","day":"2026-10-07","exercise":"Bench press","weight":60,"sets":3,"reps":8}`; weight, sets, reps and unit are optional. */
    private fun planExercise(o: JsonObject): VoiceIntent? {
        val exercise = o.str("exercise")?.trim()?.takeIf { it.isNotEmpty() && it.length <= 60 } ?: return null
        val date = o.optStr("day")?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return null
        fun int(key: String, max: Int): Int? = when (val v = o[key]) {
            null, JsonNull -> null
            is JsonPrimitive -> v.doubleOrNull?.takeIf { it % 1.0 == 0.0 && it >= 1 && it <= max }?.toInt() ?: throw IllegalArgumentException()
            else -> throw IllegalArgumentException()
        }
        val weight = when (val v = o["weight"]) {
            null, JsonNull -> null
            is JsonPrimitive -> v.doubleOrNull?.takeIf { it in 0.0..1000.0 } ?: return null
            else -> return null
        }
        if (o.optStr("unit") != null && unit(o) == null) return null
        return try {
            VoiceIntent.PlanExercise(date, exercise, weight, int("sets", MAX_SETS), int("reps", 100), unit(o))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** `{"type":"log_sets","exercise":"Bench press","unit":"kg","sets":[{"weight":62.5,"reps":8}]}`; weight may be null. */
    private fun logSets(o: JsonObject): VoiceIntent? {
        val exercise = o.str("exercise")?.trim()?.takeIf { it.isNotEmpty() && it.length <= 60 } ?: return null
        val unit = unit(o)
        val sets = (o["sets"] as? JsonArray)?.takeIf { it.size in 1..MAX_SETS }?.map { e ->
            val item = e as? JsonObject ?: return null
            val reps = (item["reps"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it % 1.0 == 0.0 && it in 1.0..100.0 }?.toInt() ?: return null
            val weightRaw = item["weight"]
            val weight = when (weightRaw) {
                null, JsonNull -> null
                is JsonPrimitive -> weightRaw.doubleOrNull?.takeIf { it in 0.0..1000.0 } ?: return null
                else -> return null
            }
            SpokenSet(weight, reps)
        } ?: return null
        return VoiceIntent.LogSets(exercise, sets, unit)
    }

    /** `kg` or `lb` when present and valid; null when absent or unrecognised. */
    private fun unit(o: JsonObject): String? = o.optStr("unit")?.lowercase()?.takeIf { it == "kg" || it == "lb" }

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
