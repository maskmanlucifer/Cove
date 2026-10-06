package app.cove.companion.feature.suggest

import app.cove.companion.AppContainer
import app.cove.companion.core.Clock
import app.cove.companion.core.newId
import app.cove.companion.core.startOfDayMillis
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.local.entity.DecisionEntity
import app.cove.companion.feature.voice.exec.AlarmRestore
import app.cove.companion.feature.voice.exec.UndoPayload
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneId

/**
 * Turns rules into at most one active suggestion card, expires ignored ones at noon and applies the user's choice.
 * Rules decide; no LLM is involved.
 */
class DecisionEngine(private val c: AppContainer, private val usage: UsageSignals, private val clock: Clock) {
    /** Expires old cards and, when none is active and the user allows it, creates today's suggestion. */
    suspend fun refresh() {
        c.assistant.shownDecisions().filter { DecisionRules.expired(it.createdAt, clock.now()) }
            .forEach { c.assistant.saveDecision(it.copy(status = "dismissed")) }
        if (!c.settings.settings.first().suggestionsOn) return
        if (c.assistant.shownDecisions().isNotEmpty()) return
        val now = clock.now()
        val today = now.toLocalDate()
        val dayStart = today.startOfDayMillis()
        if (c.assistant.decisionsSince(DecisionRules.LATE_NIGHT, dayStart) > 0 || c.assistant.isMuted(DecisionRules.LATE_NIGHT)) return

        val lastUse = (SuggestDebug.lastUse?.let { FakeUsageSignals(it) } ?: usage).lastScreenUse(dayStart, now)
        val events = c.plan.eventsOn(today).first().filter { it.repeat == "none" }.map { it.startAt.toLocalDateTime().let { t -> t.hour * 60 + t.minute } }.sorted()
        val ctx = SuggestionContext(
            now.toLocalDateTime(), lastUse?.toLocalDateTime(), events, c.plan.alarms.first(),
            c.assistant.recentConfirmed(DecisionRules.LATE_NIGHT, 2).size,
        )
        val found = DecisionRules.lateNight(ctx) ?: return
        c.assistant.saveDecision(
            DecisionEntity(newId(), found.kind, found.title, found.body, found.detail.encode(), "shown", now),
        )
    }

    /** "Do it": performs the moves, keeps the previous times for Undo and shows the confirmation chip. */
    suspend fun accept(d: DecisionEntity) {
        val detail = DecisionDetail.decode(d.reasons)
        val restores = mutableListOf<AlarmRestore>()
        detail.moves.forEach { m ->
            c.database.alarms().get(m.alarmId)?.takeIf { it.deletedAt == null }?.let {
                restores += AlarmRestore(it.id, it.minutes, it.enabled)
                c.plan.saveAlarm(it.copy(minutes = m.to))
            }
        }
        c.assistant.saveDecision(d.copy(status = "confirmed"))
        val command = c.assistant.recordCommand("suggestion", "suggest_shift", Json.encodeToString(UndoPayload(alarmRestore = restores)))
        c.voice.feedback.show("Moved. Sleep in till 8", if (restores.isEmpty()) null else command.id)
    }

    /** "Keep as is": the card goes away and is not shown again today. */
    suspend fun keep(d: DecisionEntity) = c.assistant.saveDecision(d.copy(status = "dismissed"))

    /** "Stop suggesting this": mutes the kind for good. */
    suspend fun mute(d: DecisionEntity) {
        c.assistant.mute(d.kind)
        c.assistant.saveDecision(d.copy(status = "muted"))
    }

    /** Whether [d] should still be on screen. */
    fun visible(d: DecisionEntity?): Boolean = d != null && !DecisionRules.expired(d.createdAt, clock.now(), ZoneId.systemDefault())
}
