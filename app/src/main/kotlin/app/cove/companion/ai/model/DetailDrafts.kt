package app.cove.companion.ai.model

import kotlin.math.roundToLong

/** What kind of detail was found in a sentence. Payment cards and IBANs are deliberately not listed: Cove never reads those. */
enum class DetailKind { DateTime, Money, Phone, Flight, Tracking, Address, Email, Url }

/**
 * One detail found in a sentence, with where it sits ([start] until [end], exclusive) so it can be cut out of the title.
 * [epochMillis] is set for [DetailKind.DateTime] ([hasTime] says whether it holds an hour); [amount] and [currency] for [DetailKind.Money]; [label] is a short
 * name for flights and parcels ("flight AI 302").
 */
data class FoundDetail(
    val kind: DetailKind,
    val start: Int,
    val end: Int,
    val text: String,
    val epochMillis: Long? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val label: String? = null,
    /** False when only a day was found ("tomorrow"), so [epochMillis] is midnight and says nothing about the hour. */
    val hasTime: Boolean = true,
)

/**
 * Turns the details found in a sentence into drafts, for text no rule understood. Pure and conservative: when the
 * details do not clearly mean one thing it returns nothing and the caller falls back to keeping a plain note.
 */
object DetailDrafts {
    private const val SOON_MS = 30 * 60_000L

    /** Hour used when someone names a day but no time: a sensible default they see in the draft and can change. */
    private const val DEFAULT_HOUR = 9
    private val dayParts = Regex("\\b(?:in the )?(?:morning|afternoon|evening|night|tonight)\\b", RegexOption.IGNORE_CASE)
    private val incomeWords = Regex("\\b(?:received|got|credited|refund(?:ed)?|salary|earned)\\b", RegexOption.IGNORE_CASE)
    private val trailingJoiners = Regex("(?:\\s+(?:at|on|by|around|about|for|in|of|to|the|a|an|is|was|rs|inr))+\\s*$", RegexOption.IGNORE_CASE)
    private val leadingJoiners = Regex("^(?:(?:at|on|by|around|about|for|in|of|to|the|and|then)\\s+)+", RegexOption.IGNORE_CASE)

    /** Drafts for [text] given what was [found] in it, with [nowMs] as the current time. */
    fun draft(text: String, found: List<FoundDetail>, nowMs: Long): List<VoiceIntent> {
        val money = found.firstOrNull { it.kind == DetailKind.Money && (it.amount ?: 0.0) > 0.0 }
        val date = found.firstOrNull { it.kind == DetailKind.DateTime && it.epochMillis != null }
        val phone = found.firstOrNull { it.kind == DetailKind.Phone }
        val at = date?.let(::moment)
        val future = at?.takeIf { it > nowMs + SOON_MS }
        val labelled = found.firstOrNull { it.kind == DetailKind.Flight || it.kind == DetailKind.Tracking }

        if (money != null) {
            val spans = listOfNotNull(money, date)
            if (future != null) return listOf(VoiceIntent.AddReminder(title(text, spans, keep = money.text), future))
            return listOf(
                VoiceIntent.LogExpense(
                    amountPaise = ((money.amount ?: 0.0) * 100).roundToLong(),
                    category = null,
                    paidWith = null,
                    note = title(text, spans),
                    received = incomeWords.containsMatchIn(text),
                    at = at?.takeIf { it <= nowMs },
                ),
            )
        }
        if (future != null) {
            if (labelled?.label != null) return listOf(VoiceIntent.AddReminder((if (labelled.kind == DetailKind.Flight) "Catch " else "Check ") + labelled.label, future))
            val extra = phone?.text?.let { " · $it" }.orEmpty()
            return listOf(VoiceIntent.AddReminder(title(text, listOfNotNull(date, phone)) + extra, future))
        }
        val keep = labelled
            ?: found.firstOrNull { it.kind in setOf(DetailKind.Phone, DetailKind.Address, DetailKind.Email, DetailKind.Url) }
            ?: return emptyList()
        val subject = keep.label ?: when (keep.kind) {
            DetailKind.Phone -> "phone number"
            DetailKind.Address -> "address"
            DetailKind.Email -> "email"
            else -> "link"
        }
        return listOf(VoiceIntent.Remember(tidy(text), subject.lowercase(), keep.text, "note"))
    }

    /** [text] without the [spans] (found details), tidied; a span whose text equals [keep] stays in. */
    private fun title(text: String, spans: List<FoundDetail>, keep: String? = null): String {
        val cut = spans.filter { it.text != keep }.sortedByDescending { it.start }
        var out = text
        for (s in cut) if (s.start in 0..out.length && s.end in s.start..out.length) out = out.removeRange(s.start, s.end)
        return tidy(out)
    }

    /** The moment a date detail means: its own time, or [DEFAULT_HOUR] on that day when only a day was found. */
    private fun moment(d: FoundDetail): Long {
        val millis = d.epochMillis ?: return 0L
        if (d.hasTime) return millis
        val zone = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atTime(DEFAULT_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    }

    private fun tidy(s: String): String =
        s.replace(dayParts, " ").replace(Regex("\\s+"), " ").trim().replace(trailingJoiners, "").replace(leadingJoiners, "").trim(' ', ',', '.', '-', '·')
            .replaceFirstChar { it.uppercase() }
}
