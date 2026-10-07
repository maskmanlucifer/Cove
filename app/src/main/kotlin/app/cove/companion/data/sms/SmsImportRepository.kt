package app.cove.companion.data.sms

import androidx.room.withTransaction
import app.cove.companion.core.Clock
import app.cove.companion.core.newId
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.SmsImportLogEntity
import app.cove.companion.data.categorize.PayeeLearning
import app.cove.companion.data.repo.MoneyRepository
import app.cove.companion.data.repo.PayeeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** One transaction waiting for the user's decision, with the existing expense it may repeat. */
data class ReviewItem(val candidate: Candidate, val match: ExistingMatch?) {
    val id: String get() = candidate.id
}

/** Result of a scan: items to review plus counts for the empty and summary texts. */
data class ScanResult(
    val items: List<ReviewItem>,
    val scanned: Int,
    /** Messages seen before (imported, skipped or judged not a transaction), not parsed again. */
    val alreadyHandled: Int,
    /** Payments dropped because they repeat an earlier decision or another message of this scan. */
    val duplicatesDropped: Int,
)

/** Live numbers while scanning. */
data class ScanProgress(val scanned: Int, val total: Int?, val found: Int)

/** What the user chose for one [ReviewItem]. */
data class ImportDecision(
    val item: ReviewItem,
    val include: Boolean,
    /** `spent` or `received`. */
    val kind: String,
    val categoryId: String?,
    /** Category the user picked by hand: taught to the memory and forgotten again by undo. */
    val taughtFrom: String? = null,
    val picked: Boolean = false,
    /** Note to save (the user's label or the remembered one); null saves the generated merchant name. */
    val note: String? = null,
    /** The user typed or changed the label in this review: an explicit action that teaches the payee. */
    val labelEdited: Boolean = false,
) {
    /** Only explicit edits teach: a category pick or a typed label on a spent row, never an unreviewed bulk import. */
    val teaches: Boolean get() = include && kind == "spent" && categoryId != null && (picked || labelEdited)
}

/**
 * What an import did, kept for the summary and for Undo. [payeeSnapshots] put payee memory back; [payeePicks] (payee key to
 * category and label) feed the retro-tag offer for earlier payments.
 */
data class ImportSummary(
    val batchId: String,
    val expenseIds: List<String>,
    val added: Int,
    val skipped: Int,
    val taught: List<Pair<String, String>>,
    val payeeSnapshots: List<PayeeSnapshot> = emptyList(),
    val payeePicks: Map<String, Pair<String, String?>> = emptyMap(),
)

/**
 * Orchestrates "Import from messages": scanning a [SmsSource] into reviewable transactions, writing the chosen ones
 * as expenses with a log of every decision, and undoing a batch. Message text is never stored; see `docs/SMS_IMPORT.md`.
 */
class SmsImportRepository(
    private val db: CoveDatabase,
    private val clock: Clock,
    private val money: MoneyRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val dao get() = db.smsImport()

    /** Start of [range] given what has been decided before. */
    suspend fun rangeStart(range: ImportRange): Long = ImportRange.start(range, clock.now(), dao.lastDecidedAt(), zone)

    /** Whether "Since last import" has anything to build on. */
    suspend fun hasHistory(): Boolean = dao.lastDecidedAt() != null

    /** Adds log rows for imported expenses the log does not know (fresh install, backup restore, after "Forget"). */
    suspend fun rebuildLog() {
        val now = clock.now()
        val rows = dao.importedExpenses().mapNotNull { e ->
            val ref = e.externalRef ?: return@mapNotNull null
            SmsImportLogEntity(
                key = if (ref.startsWith("msg:")) ref else SmsDedupe.refKey(ref), outcome = SmsImportOutcome.IMPORTED,
                amountPaise = e.amountPaise, kind = e.kind, merchant = e.note.ifBlank { null },
                externalRef = if (ref.startsWith("msg:")) null else ref, expenseId = e.id, messageAt = e.spentAt, loggedAt = now,
                parserVersion = SmsTransactionParser.VERSION,
            )
        }
        if (rows.isNotEmpty()) dao.insertMissing(rows)
    }

    /**
     * Reads [source] from [since], parses on the default dispatcher in pages, logs ignored messages, collapses and
     * dedupes what is left. Cancellable; [onProgress] is called after every page.
     */
    suspend fun scan(source: SmsSource, since: Long, onProgress: (ScanProgress) -> Unit = {}): ScanResult = withContext(Dispatchers.Default) {
        rebuildLog()
        val total = source.count(since)
        val seen = HashSet<String>()
        val found = ArrayList<Candidate>()
        var scanned = 0
        var handled = 0
        source.read(since) { page ->
            currentCoroutineContext().ensureActive()
            val keys = page.map { SmsDedupe.messageKey(it.sender, it.body) }
            val rows = keys.chunked(400).flatMap { dao.rowsFor(it) }.associateBy { it.key }
            val ids = page.mapNotNull { it.providerId }
            val decidedIds = ids.chunked(400).flatMap { dao.decidedProviderIds(it) }.toHashSet()
            val now = clock.now()
            val ignored = ArrayList<SmsImportLogEntity>()
            page.forEachIndexed { i, m ->
                scanned++
                val key = keys[i]
                val row = rows[key]
                if (!seen.add(key) || (row != null && (row.outcome != SmsImportOutcome.IGNORED || row.parserVersion >= SmsTransactionParser.VERSION)) || (m.providerId != null && m.providerId in decidedIds)) {
                    handled++
                    return@forEachIndexed
                }
                when (val r = SmsTransactionParser.parse(m.sender, m.body, m.receivedAt, zone)) {
                    is ParseResult.Accepted -> found += Candidate(r.tx, listOf(MessageId(key, m.providerId)))
                    is ParseResult.Rejected -> ignored += SmsImportLogEntity(
                        key = key, providerId = m.providerId, outcome = SmsImportOutcome.IGNORED, messageAt = m.receivedAt,
                        loggedAt = now, parserVersion = SmsTransactionParser.VERSION,
                    )
                }
            }
            if (ignored.isNotEmpty()) dao.upsertAll(ignored)
            onProgress(ScanProgress(scanned, total, found.size))
        }
        finish(found, scanned, handled)
    }

    private suspend fun finish(found: List<Candidate>, scanned: Int, handled: Int): ScanResult {
        val collapsed = SmsDedupe.collapse(found)
        var dropped = found.sumOf { it.messages.size } - collapsed.size
        if (collapsed.isEmpty()) return ScanResult(emptyList(), scanned, handled, dropped.coerceAtLeast(0))
        val lo = collapsed.minOf { it.tx.at }
        val hi = collapsed.maxOf { it.tx.at }
        val refs = collapsed.mapNotNull { it.tx.ref }.distinct()
        val logRows = (refs.chunked(400).flatMap { dao.byRefs(it) } + dao.decidedBetween(lo - SmsDedupe.SAME_PAYMENT_WINDOW_MS, hi + SmsDedupe.SAME_PAYMENT_WINDOW_MS)).distinctBy { it.key }
        val logged = logRows.map { LoggedTx(TxSig(it.amountPaise, if (it.kind == "received") Direction.Credit else Direction.Debit, it.last4, it.merchant, it.externalRef, it.messageAt), it.outcome) }
        val covered = SmsDedupe.coveredByLog(collapsed, logged)
        val now = clock.now()
        if (covered.isNotEmpty()) {
            dao.upsertAll(collapsed.filter { it.id in covered }.flatMap { c -> logRows(c, SmsImportOutcome.DUPLICATE, null, null, now) })
            dropped += covered.size
        }
        val open = collapsed.filter { it.id !in covered }
        if (open.isEmpty()) return ScanResult(emptyList(), scanned, handled, dropped.coerceAtLeast(0))
        val existing = (dao.expensesBetween(open.minOf { it.tx.at } - SmsDedupe.EXISTING_WINDOW_MS, open.maxOf { it.tx.at } + SmsDedupe.EXISTING_WINDOW_MS) +
            open.mapNotNull { it.tx.ref }.distinct().chunked(400).flatMap { dao.expensesByRefs(it) }).distinctBy { it.id }
            .map { ExistingExpense(it.id, it.amountPaise, it.kind, it.spentAt, it.note, it.externalRef) }
        val matches = SmsDedupe.matchExisting(open, existing, zone)
        return ScanResult(open.map { ReviewItem(it, matches[it.id]) }, scanned, handled, dropped.coerceAtLeast(0))
    }

    private fun logRows(c: Candidate, outcome: String, expenseId: String?, batchId: String?, now: Long) = c.messages.mapIndexed { i, m ->
        SmsImportLogEntity(
            key = m.key, providerId = m.providerId,
            outcome = if (i == 0) outcome else if (outcome == SmsImportOutcome.IMPORTED) SmsImportOutcome.DUPLICATE else outcome,
            amountPaise = c.tx.amountPaise, kind = if (c.tx.direction == Direction.Credit) "received" else "spent", merchant = c.tx.merchant,
            last4 = c.tx.last4, externalRef = c.tx.ref, expenseId = expenseId, batchId = batchId, messageAt = c.tx.at, loggedAt = now,
            parserVersion = SmsTransactionParser.VERSION,
        )
    }

    /**
     * Writes the included decisions as expenses and logs every decision in one transaction: included messages as
     * imported, excluded ones as duplicate (when they matched something) or skipped.
     */
    suspend fun import(decisions: List<ImportDecision>): ImportSummary = withContext(Dispatchers.IO) {
        val batch = newId()
        val now = clock.now()
        val ids = ArrayList<String>()
        val taught = ArrayList<Pair<String, String>>()
        val snapshots = ArrayList<PayeeSnapshot>()
        val picks = LinkedHashMap<String, Pair<String, String?>>()
        var skipped = 0
        db.withTransaction {
            val logs = ArrayList<SmsImportLogEntity>()
            for (d in decisions) {
                val c = d.item.candidate
                if (d.include) {
                    val generated = noteFor(c.tx, d.kind)
                    val note = d.note?.trim()?.takeIf { it.isNotEmpty() } ?: generated
                    val e = ExpenseEntity(
                        id = newId(), amountPaise = c.tx.amountPaise, kind = d.kind, categoryId = d.categoryId.takeIf { d.kind == "spent" },
                        note = note, paidWith = c.tx.paidWith, spentAt = c.tx.at, source = "sms", externalRef = c.tx.ref ?: c.messages.first().key,
                        payeeKey = c.tx.payeeKey,
                    )
                    money.save(e)
                    ids += e.id
                    if (d.teaches && d.categoryId != null) {
                        money.teach(note, d.categoryId, d.taughtFrom)
                        taught += note to d.categoryId
                        c.tx.payeeKey?.let { key ->
                            val label = PayeeLearning.labelFor(note, generated)
                            snapshots += money.teachPayee(key, d.categoryId, label, generated)
                            picks[key] = d.categoryId to label
                        }
                    }
                    logs += logRows(c, SmsImportOutcome.IMPORTED, e.id, batch, now)
                } else {
                    skipped++
                    val outcome = if (d.item.match != null) SmsImportOutcome.DUPLICATE else SmsImportOutcome.SKIPPED
                    logs += logRows(c, outcome, d.item.match?.expenseId, batch, now).map { it.copy(outcome = outcome) }
                }
            }
            dao.upsertAll(logs)
        }
        ImportSummary(batch, ids, ids.size, skipped, taught, snapshots, picks)
    }

    /** Reverses [summary]: soft-deletes its expenses, removes its log rows (so a later scan finds them again) and forgets what was taught. */
    suspend fun undo(summary: ImportSummary) = withContext(Dispatchers.IO) {
        db.withTransaction {
            summary.expenseIds.forEach { money.delete(it) }
            dao.deleteBatch(summary.batchId)
            summary.taught.forEach { (note, cat) -> money.forget(note, cat) }
            summary.payeeSnapshots.asReversed().forEach { money.restorePayee(it) }
        }
    }

    /** Clears the import log. Expenses stay; the log is rebuilt from them on the next scan. */
    suspend fun forgetHistory() = withContext(Dispatchers.IO) { dao.clear() }

    /** Note text of an imported expense: the merchant or payee, the wallet for a wallet payment, or a plain label. */
    fun noteFor(tx: ParsedSms, kind: String): String = tx.noteFor(kind)
}
