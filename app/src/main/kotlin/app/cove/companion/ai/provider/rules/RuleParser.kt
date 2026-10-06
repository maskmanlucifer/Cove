package app.cove.companion.ai.provider.rules

import app.cove.companion.ai.model.TodoDraft
import app.cove.companion.ai.model.VoiceIntent
import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import app.cove.companion.core.toLocalDateTime
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Deterministic English parser: transcript to [VoiceIntent]s with regexes, spoken numbers and times.
 * Free, instant and the first layer of [IntentParser]; "now" always comes from [clock] and expense categories from [categories].
 */
class RuleParser(private val clock: Clock, private val categories: CategoryResolver = CategoryResolver.None) {
    private val opts = setOf(RegexOption.IGNORE_CASE)

    /**
     * Parses [raw] into intents (empty when nothing was understood).
     *
     * @param habits names of the user's habits, so "I did my walk" can tick the right one.
     */
    fun parse(raw: String, habits: List<String> = emptyList()): List<VoiceIntent> {
        val text = clean(SpokenNumbers.digitize(raw))
        if (text.isBlank()) return emptyList()
        val clauses = if (journalPrefix.containsMatchIn(text)) listOf(text) else splitClauses(text)
        val out = mutableListOf<VoiceIntent>()
        for (clause in clauses) {
            val intents = parseClause(clause.trim().trimEnd('.', ',', '!', '?', ' '), habits)
            for (i in intents) {
                val prev = out.lastOrNull()
                if (i is VoiceIntent.AddTodos && prev is VoiceIntent.AddTodos) {
                    out[out.lastIndex] = VoiceIntent.AddTodos(prev.items + i.items)
                } else out += i
            }
        }
        return out
    }

    /** Likely readings of a half-heard transcript that contains a time (frame 19); empty when none. */
    fun guesses(raw: String): List<VoiceIntent> {
        val text = SpokenNumbers.digitize(raw)
        val t = WhenParser.findTime(text) ?: return emptyList()
        val now = clock.now().toLocalDateTime()
        return listOf(
            VoiceIntent.AddReminder("Reminder", WhenParser.reminderAt(t, null, now, text)),
            VoiceIntent.SetAlarm(WhenParser.alarmMinutes(t, text)),
        )
    }

    private val fillers = Regex(
        "^(?:(?:hey|ok|okay|hi)\\s+(?:cove|google)[,\\s]*|(?:please|kindly)[,\\s]+|(?:can|could|would) you(?: please)?\\s+|i(?:'d| would) like (?:you )?to\\s+|i want (?:you )?to\\s+|i want to\\s+)+",
        opts,
    )

    private fun clean(s: String): String =
        s.trim().replace(fillers, "").replace(Regex("\\s+(?:please|thanks|thank you)\\W*$", opts), "").trim()

    private val splitter = Regex(
        "(?:[.;!?]\\s+|\\s*,?\\s*\\b(?:and then|and also|then|also|and)\\s+(?=(?:set|remind|add|log|spent|spend|paid|undo|wake|change|move|create|put|mark|tick|i\\s+(?:spent|paid|did|finished|completed))\\b))",
        opts,
    )

    private fun splitClauses(text: String) = text.split(splitter).filter { it.isNotBlank() }

    private fun parseClause(c: String, habits: List<String>): List<VoiceIntent> {
        if (c.isBlank()) return emptyList()
        undo.find(c)?.let { return listOf(VoiceIntent.UndoLast) }
        if (queryNext.containsMatchIn(c)) return listOf(VoiceIntent.QueryNext)
        journalPrefix.find(c)?.let { m ->
            val body = m.groupValues[1].trim()
            return if (body.isBlank()) emptyList() else listOf(VoiceIntent.JournalNote(body.replaceFirstChar { it.uppercase() }))
        }
        reminder.find(c)?.let { return listOf(reminder(it.groupValues[1], c)) }
        if (Regex("\\balarm\\b|\\bwake me\\b", opts).containsMatchIn(c)) return alarm(c)
        expense(c)?.let { return listOf(it) }
        habit(c, habits)?.let { return listOf(it) }
        todo(c)?.let { return it }
        return emptyList()
    }

    private val undo = Regex("^(?:undo(?: that| the last(?: one| thing)?| last(?: one)?| it)?|never ?mind(?: that)?|take that back|cancel that|scratch that)$", opts)
    private val queryNext = Regex("^(?:what(?:'s| is)(?: my)? next|what do i have(?: next| today| now)?|what(?:'s| is) (?:on|up)(?: next| today)?|next(?: thing| up)?|what(?:'s| is) coming up)\\??$", opts)
    private val journalPrefix = Regex(
        "^(?:(?:add|write|make|create|log|save|put)\\s+(?:an?\\s+|to\\s+|in\\s+)?(?:my\\s+|the\\s+)?(?:journal|diary)(?:\\s+(?:entry|note))?|write in my (?:journal|diary)|in my (?:journal|diary)|journal(?:\\s+(?:entry|note|this))?|dear diary|note to self)\\s*(?:that|:|,|-)?\\s*(.*)$",
        opts,
    )

    // ---- alarms -------------------------------------------------------------------------------

    private fun alarm(c: String): List<VoiceIntent> {
        val t = WhenParser.findTime(c) ?: return emptyList()
        val minutes = WhenParser.alarmMinutes(t, c)
        if (Regex("\\b(?:change|move|shift|push|update|reschedule|make)\\b", opts).containsMatchIn(c) &&
            !Regex("\\b(?:set|create)\\b", opts).containsMatchIn(c)
        ) {
            return listOf(VoiceIntent.ChangeAlarm(minutes, if (c.contains("bedtime", true)) "bedtime" else null))
        }
        val label = Regex("\\b(?:called|named|labell?ed)\\s+(.+)$", opts).find(c)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        return listOf(VoiceIntent.SetAlarm(minutes, label?.replaceFirstChar { it.uppercase() } ?: "Alarm", repeatMask(c)))
    }

    private fun repeatMask(c: String): Int {
        val lower = c.lowercase()
        val names = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        return when {
            Regex("every ?day|daily|each day").containsMatchIn(lower) -> 0b1111111
            Regex("week ?days").containsMatchIn(lower) -> 0b0011111
            Regex("week ?ends?").containsMatchIn(lower) -> 0b1100000
            else -> names.withIndex().filter { Regex("every ${it.value}s?").containsMatchIn(lower) }
                .fold(0) { acc, d -> acc or (1 shl d.index) }
        }
    }

    // ---- reminders ----------------------------------------------------------------------------

    private val reminder = Regex("^(?:remind me|set (?:an? )?reminder|add (?:an? )?reminder|create (?:an? )?reminder|reminder)\\s*(.*)$", opts)

    private fun reminder(rest: String, whole: String): VoiceIntent.AddReminder {
        val now = clock.now().toLocalDateTime()
        val rel = WhenParser.findRelative(rest, now)
        val day = WhenParser.findDay(rest, now.toLocalDate())
        val time = WhenParser.findTime(rest)
        val at = when {
            rel != null -> rel.first.toEpochMillis()
            time != null -> WhenParser.reminderAt(time, day?.date, now, whole)
            day != null -> LocalDateTime.of(day.date, LocalTime.of(9, 0)).toEpochMillis()
            else -> null
        }
        val title = strip(rest, listOfNotNull(rel?.second, day?.range, time?.range)).trim()
            .replace(Regex("^(?:to|about|that|of)\\s+", opts), "")
            .replace(Regex("\\b(?:in the (?:morning|evening|afternoon)|this (?:morning|evening|afternoon)|tonight|at night)\\b", opts), "")
            .replace(Regex("\\s+(?:at|on|by|for)$", opts), "")
        return VoiceIntent.AddReminder(tidyTitle(title).ifEmpty { "Reminder" }, at)
    }

    // ---- expenses -----------------------------------------------------------------------------

    private val amount = Regex("(?:₹|\\brs\\.?\\s*|\\brupees\\s+|\\binr\\s*)?(\\d[\\d,]*(?:\\.\\d{1,2})?)(\\s*k\\b|\\s*(?:rupees?|rs\\b|bucks))?", opts)
    private val spentLead = Regex("^(?:i\\s+)?(?:just\\s+)?(?:spent|spend|paid|pay|bought)\\b", opts)
    private val logLead = Regex("^(?:log|add|record|note|track|enter)\\s+(?:an?\\s+)?(?:expense|spend|spending|payment)?\\s*(?:of\\s+)?(?:₹|rs\\.?\\s*|rupees\\s+)?\\d", opts)
    private val logHead = Regex("^(?:log|add|record|note|track|enter)\\s+(?:an?\\s+)?(?:expense|spend|spending|payment)?\\s*(?:of\\s+)?", opts)
    private val receivedLead = Regex("^(?:i\\s+)?(?:received|earned|got paid|got)\\b", opts)
    private val payMethods = Regex(
        "\\b(?:using|via|with|by|through|on|in)\\s+(?:my\\s+)?(upi|cash|(?:credit |debit )?card|g ?pay|google pay|phone ?pe|paytm|net ?banking)\\b", opts,
    )

    private fun expense(c: String): VoiceIntent.LogExpense? {
        val received = receivedLead.containsMatchIn(c) && Regex("\\d").containsMatchIn(c)
        val isExpense = spentLead.containsMatchIn(c) || logLead.containsMatchIn(c) || received
        if (!isExpense || (received && !Regex("received|earned|got paid|salary|refund|income|cashback", opts).containsMatchIn(c))) return null
        val now = clock.now().toLocalDateTime()
        val time = WhenParser.findTime(c, allowBareHour = false)
        val noTime = if (time != null) c.removeRange(time.range) else c
        val m = amount.find(noTime) ?: return null
        var value = m.groupValues[1].replace(",", "").toDouble()
        if (m.groupValues[2].trim().equals("k", true)) value *= 1000
        val paise = Math.round(value * 100)
        if (paise <= 0) return null
        var rest = noTime.removeRange(m.range)
        val method = payMethods.find(rest)
        rest = if (method != null) rest.removeRange(method.range) else rest
        val paidWith = method?.groupValues?.get(1)?.let(::payName)
        rest = rest.replace(spentLead, "").replace(logHead, "")
            .replace(receivedLead, "")
            .replace(Regex("\\b(?:today|yesterday|just now|this morning|tonight)\\b", opts), "")
            .replace(Regex("^\\s*(?:on|for|to|at|towards|from)\\s+", opts), "")
            .replace(Regex("\\s+(?:on|for|to)\\s*$", opts), "")
            .replace(Regex("^\\s*(?:on|for)\\s+", opts), "")
        val parts = rest.split(Regex("\\s+(?:at|from|to)\\s+", opts), limit = 2).map { it.trim() }.filter { it.isNotEmpty() }
        val note = tidyTitle(parts.joinToString(" · "))
        val at = time?.let { pastToday(it, now) }
        return VoiceIntent.LogExpense(
            amountPaise = paise,
            category = if (received) null else categories.categoryFor(rest),
            paidWith = paidWith,
            note = note,
            received = received,
            at = at,
        )
    }

    /** Today at [t] for an expense said in the past; null when that would be in the future. */
    private fun pastToday(t: TimeMatch, now: LocalDateTime): Long? {
        var at = LocalDateTime.of(now.toLocalDate(), LocalTime.of(t.hour % 24, t.minute))
        if (t.meridiem == null && at.isAfter(now) && at.hour < 12) at = at.plusHours(12)
        return if (at.isAfter(now)) null else at.toEpochMillis()
    }

    private fun payName(raw: String) = when (raw.lowercase().replace(" ", "")) {
        "upi", "gpay", "googlepay", "phonepe", "paytm" -> "UPI"
        "cash" -> "Cash"
        "netbanking" -> "Net banking"
        else -> "Card"
    }

    // ---- habits -------------------------------------------------------------------------------

    private val habitVerb = Regex(
        "^(?:i\\s+)?(?:(?:just\\s+)?(?:did|finished|completed|done)|log|tick|check(?: off)?|mark|complete|finish)\\s+(?:my\\s+)?(?:habit\\s+)?(.+?)(?:\\s+habit)?(?:\\s+(?:as\\s+)?(?:done|complete|completed))?(?:\\s+today)?$",
        opts,
    )

    private fun habit(c: String, habits: List<String>): VoiceIntent.LogHabit? {
        val m = habitVerb.find(c) ?: return null
        val said = m.groupValues[1].trim()
        val known = habits.firstOrNull { h ->
            val name = h.lowercase()
            name in c.lowercase() || name.substringBefore(' ').let { it.length >= 4 && c.lowercase().contains(it) }
        }
        if (known != null) return VoiceIntent.LogHabit(known)
        return if (Regex("\\bhabit\\b", opts).containsMatchIn(c)) VoiceIntent.LogHabit(tidyTitle(said)) else null
    }

    // ---- to-dos -------------------------------------------------------------------------------

    private val todoLead = Regex(
        "^(?:(add|put|create|new|todo|to-do|to do|note down|note)\\s+(?:an?\\s+)?(?:new\\s+)?(?:to-?do\\s+)?|(buy|get|grab)\\s+|(need to|i need to|i have to|i should|don't forget to|do not forget to|remember to|i must)\\s+)",
        opts,
    )
    private val listTarget = Regex("\\s+(?:to|on|in|into|under)\\s+(?:my\\s+|the\\s+)?(?:([a-z]+)\\s+)?(?:to-?do\\s+)?(?:list|todos?|to-dos?|category)$", opts)
    private val itemSplit = Regex("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+|\\s*;\\s*|\\s+then\\s+|\\s+plus\\s+", opts)

    private fun todo(c: String): List<VoiceIntent>? {
        val lead = todoLead.find(c) ?: return null
        val shoppingVerb = lead.groups[2] != null
        val addLead = lead.groups[1] != null
        var rest = c.substring(lead.range.last + 1)
        var forced: String? = if (shoppingVerb) "Shopping" else null
        listTarget.find(rest)?.let {
            val word = it.groupValues[1].lowercase()
            if (word.isNotEmpty() && word !in setOf("my", "the", "to", "do", "todo")) forced = word.replaceFirstChar { ch -> ch.uppercase() }
            rest = rest.removeRange(it.range)
        }
        val now = clock.now().toLocalDateTime()
        val day = WhenParser.findDay(rest, now.toLocalDate())
        val time = WhenParser.findTime(rest)
        val due = when {
            time != null -> WhenParser.reminderAt(time, day?.date, now, rest)
            day != null -> LocalDateTime.of(day.date, LocalTime.of(9, 0)).toEpochMillis()
            else -> null
        }
        rest = strip(rest, listOfNotNull(day?.range, time?.range)).replace(Regex("\\s+(?:at|on|by|for)$", opts), "")
        val items = rest.split(itemSplit).map { tidyTitle(it.replace(Regex("^(?:also|to)\\s+", opts), "")) }.filter { it.isNotEmpty() }
        if (items.isEmpty()) return emptyList()
        return listOf(VoiceIntent.AddTodos(items.map { TodoDraft(it, forced ?: CategoryGuess.todo(it, afterAdd = addLead), due) }))
    }

    // ---- helpers ------------------------------------------------------------------------------

    private fun strip(s: String, ranges: List<IntRange>): String {
        val sb = StringBuilder(s)
        ranges.sortedByDescending { it.first }.forEach { if (it.last < sb.length) sb.delete(it.first, it.last + 1) }
        return sb.toString()
    }

    private fun tidyTitle(s: String): String =
        s.replace(Regex("\\s+"), " ").trim().trim(',', '.', '-', ' ').replaceFirstChar { it.uppercase() }
}
