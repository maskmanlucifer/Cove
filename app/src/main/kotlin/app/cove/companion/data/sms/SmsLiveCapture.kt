package app.cove.companion.data.sms

import androidx.room.withTransaction
import app.cove.companion.ai.model.SamePair
import app.cove.companion.ai.model.SameRequest
import app.cove.companion.data.categorize.ExpenseCategorizer
import app.cove.companion.data.categorize.Reason
import app.cove.companion.data.categorize.Suggestion
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.CategoryMemoryEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.PayeeMemoryEntity
import app.cove.companion.data.repo.MoneyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** An expense live capture wrote on its own ("Add automatically", or Add on a notification). */
data class AddedPayment(val expenseId: String, val amountPaise: Long, val kind: String, val note: String, val categoryName: String?)

/** What live capture did with one message or one catch-up scan. */
data class CaptureReport(val added: List<AddedPayment> = emptyList()) {
    val isEmpty: Boolean get() = added.isEmpty()
}

/**
 * Asks whether payments found in messages repeat the expenses they are paired with (`AiService.samePayments`):
 * [Answer.Same] per pair, [Answer.NoAi] when no cloud model is set up, [Answer.NoAnswer] when it could not answer now.
 */
fun interface SameChecker {
    suspend fun check(pairs: List<SamePair>): Answer

    sealed interface Answer {
        data class Same(val same: List<Boolean>) : Answer
        data object NoAi : Answer
        data object NoAnswer : Answer
    }
}

/** Category and remembered label to use for a captured payment. */
data class Suggested(val categoryId: String?, val label: String?)

/** The same suggestion the import review shows: payee memory, then learned words, then built-ins, then "Other". Pure. */
object CaptureSuggestion {
    private val mealWallets = setOf("Pluxee", "Sodexo", "Meal card")

    /**
     * The categorizer's guess for a spent [tx]. A meal wallet (Pluxee, Sodexo, meal card) is food unless the merchant or
     * what Cove learned says otherwise: the merchant is tried first, and only when it says nothing the wallet itself is.
     */
    fun categorize(tx: ParsedSms, categories: List<ExpenseCategoryEntity>, memory: Map<String, CategoryMemoryEntity>, payee: PayeeMemoryEntity?): Suggestion {
        val first = ExpenseCategorizer.suggest(tx.noteFor("spent"), categories, memory, payee)
        if (first.categoryId != null || tx.paidWith != "Wallet" || tx.bank !in mealWallets) return first
        return ExpenseCategorizer.suggest("${tx.bank} wallet", categories, memory)
    }

    fun of(
        tx: ParsedSms,
        categories: List<ExpenseCategoryEntity>,
        memory: Map<String, CategoryMemoryEntity>,
        payees: Map<String, PayeeMemoryEntity>,
    ): Suggested {
        if (tx.direction == Direction.Credit) return Suggested(null, null)
        val payee = tx.payeeKey?.let(payees::get)
        val s = categorize(tx, categories, memory, payee)
        return Suggested(s.categoryId ?: ExpenseCategorizer.fallback(categories)?.id, payee?.label?.takeIf { s.reason == Reason.Payee })
    }
}

/**
 * Handles payments found in new messages, with no one asked: the live receiver's one message, the app-open catch-up
 * scan, and Undo on an "Added" notification. It reuses [SmsImportRepository] for parsing, dedupe and the log, then
 * settles each payment with [CaptureDecision]: new ones are added, repeats are logged as duplicates, and doubts go to
 * [same] (names and times only). A doubt Gemini cannot answer now is left unlogged, and the catch-up cursor
 * ([CaptureCursor]) stays before it, so the next scan asks again. Message text is parsed in memory and never kept.
 * Decisions that write run one at a time.
 */
class SmsLiveCapture(
    private val db: CoveDatabase,
    private val money: MoneyRepository,
    private val imports: SmsImportRepository,
    private val prefs: SmsCapturePrefs,
    private val same: SameChecker,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val gate = Mutex()

    /** Handles one message from the receiver. A doubt that cannot be settled now is read again by the catch-up scan. */
    suspend fun onMessage(sender: String?, body: String, receivedAt: Long): CaptureReport = gate.withLock {
        if (prefs.mode.value == CaptureMode.Off) return@withLock CaptureReport()
        val result = imports.scan(OneMessageSource(SmsMessage(null, sender, body, receivedAt)), 0L)
        settle(result.items).report
    }

    /**
     * Scans [source] from the cursor (at first: since the last decision, never before capture was turned on), settles
     * what it finds and what the old "Ask me" mode left waiting, then moves the cursor up to the newest message read,
     * but not past one still waiting for an answer.
     */
    suspend fun catchUp(source: SmsSource): CaptureReport = gate.withLock {
        if (prefs.mode.value == CaptureMode.Off) return@withLock CaptureReport()
        val since = prefs.checkedUntil.takeIf { it > 0 } ?: maxOf(imports.rangeStart(ImportRange.SinceLast), prefs.enabledAt)
        val result = imports.scan(source, since)
        val outcome = settle(result.items + imports.pendingItems())
        prefs.checkedUntil = CaptureCursor.next(since, result.newestAt, outcome.oldestWaiting)
        outcome.report
    }

    private class Outcome(val report: CaptureReport, val oldestWaiting: Long?)

    private suspend fun settle(items: List<ReviewItem>): Outcome {
        if (items.isEmpty()) return Outcome(CaptureReport(), null)
        val actions = items.associate { it.id to CaptureDecision.decide(it.match) }.toMutableMap()
        val doubts = items.filter { actions[it.id] == CaptureAction.Check }
        doubts.chunked(SameRequest.MAX_BATCH).forEach { batch ->
            when (val answer = same.check(batch.map { pairOf(it) })) {
                is SameChecker.Answer.Same -> batch.forEachIndexed { i, item -> actions[item.id] = if (answer.same[i]) CaptureAction.Skip else CaptureAction.Add }
                SameChecker.Answer.NoAi -> batch.forEach { actions[it.id] = CaptureDecision.withoutAi(it.candidate.tx.at, it.match!!, zone) }
                SameChecker.Answer.NoAnswer -> Unit
            }
        }
        val adds = items.filter { actions[it.id] == CaptureAction.Add }
        val skips = items.filter { actions[it.id] == CaptureAction.Skip }
        val added = if (adds.isEmpty()) emptyList() else write(adds, context(adds))
        if (skips.isNotEmpty()) imports.import(skips.map { ImportDecision(it, false, kindOf(it), null) })
        val oldestWaiting = items.filter { actions[it.id] == CaptureAction.Check }
            .flatMap { item -> item.candidate.messages.map { it.receivedAt } }.filter { it > 0 }.minOrNull()
        return Outcome(CaptureReport(added), oldestWaiting)
    }

    /** What may leave the phone about a doubt: the two names and the two local times, never amounts or message text. */
    private fun pairOf(item: ReviewItem): SamePair {
        val tx = item.candidate.tx
        val m = item.match!!
        return SamePair(tx.noteFor(kindOf(item)), timeLabel(tx.at), m.note.ifBlank { "Expense" }, timeLabel(m.at))
    }

    private fun timeLabel(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(TIME)

    /** Undoes an automatic add: soft-deletes the expense and logs its message as skipped so it is not added again. No-op when done. */
    suspend fun undo(expenseId: String) {
        gate.withLock {
            db.withTransaction {
                val e = db.expenses().get(expenseId)?.takeIf { it.deletedAt == null } ?: return@withTransaction
                money.delete(e.id)
                db.smsImport().skipImportedFor(e.id)
            }
        }
    }

    private class Ctx(val cats: List<ExpenseCategoryEntity>, val memory: Map<String, CategoryMemoryEntity>, val payees: Map<String, PayeeMemoryEntity>)

    private suspend fun context(items: List<ReviewItem>) = Ctx(
        money.categories.first().filter { it.kind == "spending" && it.deletedAt == null },
        money.memory.first(),
        money.payees(items.mapNotNull { it.candidate.tx.payeeKey }),
    )

    private suspend fun write(items: List<ReviewItem>, ctx: Ctx): List<AddedPayment> {
        val suggestions = items.map { CaptureSuggestion.of(it.candidate.tx, ctx.cats, ctx.memory, ctx.payees) }
        val decisions = items.mapIndexed { i, item ->
            ImportDecision(item, true, kindOf(item), suggestions[i].categoryId, note = suggestions[i].label)
        }
        val summary = imports.import(decisions)
        return items.mapIndexed { i, item ->
            val tx = item.candidate.tx
            val kind = kindOf(item)
            AddedPayment(
                summary.expenseIds[i], tx.amountPaise, kind, suggestions[i].label ?: tx.noteFor(kind),
                suggestions[i].categoryId?.let { id -> ctx.cats.firstOrNull { it.id == id }?.name },
            )
        }
    }

    private fun kindOf(item: ReviewItem) = if (item.candidate.tx.direction == Direction.Credit) "received" else "spent"

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)
    }
}
