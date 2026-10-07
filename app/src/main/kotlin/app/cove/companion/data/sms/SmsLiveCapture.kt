package app.cove.companion.data.sms

import androidx.room.withTransaction
import app.cove.companion.core.rupees
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

/** An expense live capture wrote on its own ("Add automatically", or Add on a notification). */
data class AddedPayment(val expenseId: String, val amountPaise: Long, val kind: String, val note: String, val categoryName: String?)

/** A payment waiting for the user. [duplicateLine] explains a possible repeat. */
data class PendingPayment(val key: String, val amountPaise: Long, val kind: String, val label: String, val duplicateLine: String?)

/** What live capture did with one message or one catch-up scan. */
data class CaptureReport(val added: List<AddedPayment> = emptyList(), val pending: List<PendingPayment> = emptyList()) {
    val isEmpty: Boolean get() = added.isEmpty() && pending.isEmpty()
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
 * Handles payments found in new messages: the live receiver's one message, the app-open catch-up scan, and the
 * Add / Skip / Undo actions on notifications. It reuses [SmsImportRepository] for parsing, dedupe and the log, then
 * applies the user's [CaptureMode] (see [CaptureDecision]). Message text is parsed in memory and never kept.
 * Decisions that write run one at a time, so a repeated notification tap finds nothing left to do.
 */
class SmsLiveCapture(
    private val db: CoveDatabase,
    private val money: MoneyRepository,
    private val imports: SmsImportRepository,
    private val prefs: SmsCapturePrefs,
) {
    private val gate = Mutex()

    /** Handles one message from the receiver ([permission]: the system delivered it, so receiving is allowed). */
    suspend fun onMessage(sender: String?, body: String, receivedAt: Long): CaptureReport = gate.withLock {
        val mode = prefs.mode.value
        if (mode == CaptureMode.Off) return@withLock CaptureReport()
        val result = imports.scan(OneMessageSource(SmsMessage(null, sender, body, receivedAt)), 0L)
        process(result.items, mode, permission = true)
    }

    /** Scans [source] for messages since the last decision (and not before capture was turned on) and handles what it finds. */
    suspend fun catchUp(source: SmsSource): CaptureReport = gate.withLock {
        val mode = prefs.mode.value
        if (mode == CaptureMode.Off) return@withLock CaptureReport()
        val since = maxOf(imports.rangeStart(ImportRange.SinceLast), prefs.enabledAt)
        val result = imports.scan(source, since)
        process(result.items, mode, permission = true)
    }

    private suspend fun process(items: List<ReviewItem>, mode: CaptureMode, permission: Boolean): CaptureReport {
        if (items.isEmpty()) return CaptureReport()
        val adds = ArrayList<ReviewItem>()
        val asks = ArrayList<ReviewItem>()
        for (item in items) when (CaptureDecision.decide(mode, permission, found = true, duplicate = item.match != null)) {
            CaptureAction.Add -> adds += item
            CaptureAction.Ask -> asks += item
            CaptureAction.Ignore -> Unit
        }
        val ctx = context(items)
        val added = if (adds.isEmpty()) emptyList() else write(adds, ctx)
        if (asks.isNotEmpty()) imports.savePending(asks)
        val pending = asks.map { it.toPayment() }
        return CaptureReport(added, pending)
    }

    /** Adds the pending payment [key] as an expense, as the Add action does. Null when it is already decided. */
    suspend fun accept(key: String): AddedPayment? = gate.withLock {
        val row = db.smsImport().pendingByKey(key) ?: return@withLock null
        val item = ReviewItem(row.toCandidate(), null)
        write(listOf(item), context(listOf(item))).firstOrNull()
    }

    /** Skips the pending payment [key] and remembers it as skipped. No-op when it is already decided. */
    suspend fun skip(key: String) {
        gate.withLock {
            val row = db.smsImport().pendingByKey(key) ?: return@withLock
            val item = ReviewItem(row.toCandidate(), null)
            imports.import(listOf(ImportDecision(item, false, kindOf(item), null)))
        }
    }

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

    private fun ReviewItem.toPayment(): PendingPayment {
        val tx = candidate.tx
        val line = match?.let { m -> "Possible duplicate: you already added ${m.note.ifBlank { "a payment" }} ${rupees(m.amountPaise)}" }
        return PendingPayment(id, tx.amountPaise, kindOf(this), tx.noteFor(kindOf(this)), line)
    }
}
