package app.cove.companion.feature.suggest

import app.cove.companion.core.clockText
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.AlarmEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** One reason shown on the card ([card], null = card hides it) and in "Why I suggested this" with its [source]. */
@Serializable
data class Reason(val why: String, val source: String, val card: String? = null)

/** An alarm shift that "Do it" performs. */
@Serializable
data class AlarmMove(val alarmId: String, val to: Int)

/** Stored in `DecisionEntity.reasons`: the reasons plus what "Do it" will change. */
@Serializable
data class DecisionDetail(val reasons: List<Reason>, val moves: List<AlarmMove> = emptyList()) {
    fun encode(): String = Json.encodeToString(this)

    companion object {
        /** Reads the stored JSON; a plain string array (older rows) becomes reasons without sources. */
        fun decode(text: String): DecisionDetail = runCatching { Json.decodeFromString<DecisionDetail>(text) }.getOrNull()
            ?: runCatching { Json.decodeFromString<List<String>>(text).map { Reason(it, "", it) } }.getOrDefault(emptyList()).let(::DecisionDetail)
    }
}

/** A suggestion produced by a rule, before it is stored. */
data class Candidate(val kind: String, val title: String, val body: String, val detail: DecisionDetail)

/** What the rules look at. Times are local. */
data class SuggestionContext(
    val now: LocalDateTime,
    val lastPhoneUse: LocalDateTime?,
    /** Start minutes of today's events, ascending. */
    val eventMinutes: List<Int>,
    val alarms: List<AlarmEntity>,
    /** How many earlier suggestions of the same kind the user accepted. */
    val confirmedBefore: Int,
)

/** Pure rules of the decision engine. */
object DecisionRules {
    const val LATE_NIGHT = "late_night_shift"
    private const val SLEEP_IN_TO = 8 * 60
    private const val EVENING = 18 * 60

    /** Phone used after 1 am, nothing planned before 11, a wake alarm earlier than 8: suggest sleeping in. */
    fun lateNight(ctx: SuggestionContext): Candidate? {
        val use = ctx.lastPhoneUse ?: return null
        val useTime = use.toLocalTime()
        if (useTime < LocalTime.of(1, 0) || useTime >= LocalTime.of(7, 0) || use.toLocalDate() != ctx.now.toLocalDate()) return null
        if (ctx.now.hour >= 12) return null
        val first = ctx.eventMinutes.firstOrNull()
        if (first != null && first < 11 * 60) return null
        val today = ctx.alarms.filter { it.enabled && it.deletedAt == null && appliesOn(it, ctx.now.toLocalDate()) }
        val wake = today.filter { it.kind == "wake" && it.minutes < SLEEP_IN_TO }.minByOrNull { it.minutes } ?: return null
        val run = today.filter { it.kind != "wake" && it.kind != "bedtime" && it.minutes < SLEEP_IN_TO }.minByOrNull { it.minutes }

        val body = if (run != null) {
            "Move your ${digits(run.minutes)} ${run.label.lowercase().ifBlank { "alarm" }} to ${spoken(EVENING)} and sleep in till 8?"
        } else "Sleep in till 8? Your ${digits(wake.minutes)} alarm can move to 8:00."
        val useText = clockText(use.hour * 60 + use.minute).let { it.digits + it.suffix }
        val planText = first?.let { spoken(it) }
        val reasons = buildList {
            add(Reason("Phone use until $useText", "From screen time, on this phone", "Phone was in use until $useText"))
            add(
                if (planText != null) Reason("First plan at $planText", "From your calendar", "Nothing planned before ${planText.removeSuffix(" am").removeSuffix(" pm")}")
                else Reason("Nothing planned today", "From your calendar", "Nothing planned this morning"),
            )
            if (ctx.confirmedBefore > 0) {
                val what = run?.label?.lowercase()?.takeIf { it.isNotBlank() }?.let { "${it}s" } ?: "your morning"
                val times = if (ctx.confirmedBefore >= 2) "twice" else "once"
                add(Reason("You moved $what $times before", if (ctx.confirmedBefore >= 2) "Last two late nights" else "Last late night"))
            }
        }
        val moves = listOfNotNull(AlarmMove(wake.id, SLEEP_IN_TO), run?.let { AlarmMove(it.id, EVENING) })
        return Candidate(LATE_NIGHT, "Late night?", body, DecisionDetail(reasons, moves))
    }

    /** Ignored cards disappear at the first noon after they were created. */
    fun expired(createdAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val created = createdAt.toLocalDateTime()
        var noon = LocalDateTime.of(created.toLocalDate(), LocalTime.NOON)
        if (!noon.isAfter(created)) noon = noon.plusDays(1)
        return !noon.atZone(zone).toInstant().isAfter(java.time.Instant.ofEpochMilli(now))
    }

    private fun appliesOn(a: AlarmEntity, day: LocalDate) = a.daysMask == 0 || (a.daysMask shr (day.dayOfWeek.value - 1)) and 1 == 1

    private fun digits(minutes: Int) = clockText(minutes).digits

    /** "6 pm" or "10:30 am". */
    fun spoken(minutes: Int): String {
        val c = clockText(minutes)
        return (if (minutes % 60 == 0) c.digits.substringBefore(':') else c.digits) + c.suffix
    }
}
