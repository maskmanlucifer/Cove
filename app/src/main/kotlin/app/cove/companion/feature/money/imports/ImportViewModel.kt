package app.cove.companion.feature.money.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.Undo
import app.cove.companion.data.categorize.ExpenseCategorizer
import app.cove.companion.data.categorize.Reason
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.sms.CaptureMode
import app.cove.companion.data.sms.Direction
import app.cove.companion.data.sms.ImportDecision
import app.cove.companion.data.sms.ImportRange
import app.cove.companion.data.sms.ImportSummary
import app.cove.companion.data.sms.PastedSource
import app.cove.companion.data.sms.ReviewItem
import app.cove.companion.data.sms.ScanProgress
import app.cove.companion.data.sms.noteFor
import app.cove.companion.data.sms.SmsReadException
import app.cove.companion.data.sms.SmsSource
import app.cove.companion.feature.money.RetroOffer
import app.cove.companion.feature.money.RetroTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Where the flow is. */
enum class ImportStage { Offer, Intro, Range, Scanning, Review, Importing, Done, Paste }

/** One review row: the found transaction and what the user has chosen for it so far. */
data class ImportRow(
    val item: ReviewItem,
    val checked: Boolean,
    /** `spent` or `received`. */
    val kind: String,
    val categoryId: String?,
    val suggestedId: String?,
    /** Plain reason for the suggestion ("learned", "built-in", ...). */
    val reason: String?,
    /** The user chose [categoryId] by hand. */
    val picked: Boolean = false,
    /** Note to save instead of the generated name: remembered for this payee or typed here; null keeps the generated name. */
    val label: String? = null,
    /** The user typed the [label] in this review. */
    val labelEdited: Boolean = false,
    /** [categoryId] (and maybe [label]) came from what the user taught about this payee. */
    val recalled: Boolean = false,
) {
    val id: String get() = item.id

    /** The cleaned merchant name, or a plain fallback when the message has none. */
    val generated: String get() = generatedNote(item.candidate.tx, kind)

    /** The note shown now and saved on import. */
    val note: String get() = label ?: generated
    val isDuplicate: Boolean get() = item.match != null
}

/** Merchant name of [tx], the wallet for a wallet payment, or the plain fallback for a payment of [kind] with no readable name. */
internal fun generatedNote(tx: app.cove.companion.data.sms.ParsedSms, kind: String): String = tx.noteFor(kind)

/**
 * The review row for [item]: category and label from the payee's memory when [payees] knows it (it wins over word
 * [memory] and built-ins), otherwise the categorizer's suggestion on the cleaned merchant name.
 */
internal fun rowFor(
    item: ReviewItem,
    cats: List<ExpenseCategoryEntity>,
    memory: Map<String, app.cove.companion.data.local.entity.CategoryMemoryEntity>,
    payees: Map<String, app.cove.companion.data.local.entity.PayeeMemoryEntity>,
): ImportRow {
    val tx = item.candidate.tx
    val kind = if (tx.direction == Direction.Credit) "received" else "spent"
    val payee = if (kind == "spent") tx.payeeKey?.let(payees::get) else null
    val s = if (kind == "spent") ExpenseCategorizer.suggest(generatedNote(tx, kind), cats, memory, payee) else null
    val id = s?.categoryId ?: if (kind == "spent") ExpenseCategorizer.fallback(cats)?.id else null
    val recalled = s?.reason == Reason.Payee
    return ImportRow(
        item, checked = item.match == null, kind = kind, categoryId = id, suggestedId = id, reason = s?.reason?.label,
        label = payee?.label?.takeIf { recalled }, recalled = recalled,
    )
}

/** Everything the Import screens draw. */
data class ImportState(
    val stage: ImportStage = ImportStage.Intro,
    val range: ImportRange = ImportRange.SinceLast,
    val hasHistory: Boolean = false,
    val estimate: Int? = null,
    val progress: ScanProgress? = null,
    val rows: List<ImportRow> = emptyList(),
    val categories: List<ExpenseCategoryEntity> = emptyList(),
    val scanned: Int = 0,
    val duplicatesDropped: Int = 0,
    val fromPaste: Boolean = false,
    /** Calm sentence about something that did not work; the screen always offers a next step. */
    val message: String? = null,
    val summaryText: String? = null,
    val undone: Boolean = false,
    val added: Int = 0,
    /** This device's "Payments from messages" mode. */
    val mode: CaptureMode = CaptureMode.Off,
) {
    val checkedCount: Int get() = rows.count { it.checked }
    val newRows: List<ImportRow> get() = rows.filterNot { it.isDuplicate }
    val dupRows: List<ImportRow> get() = rows.filter { it.isDuplicate }
}

/**
 * Runs the scan, holds the user's choices and performs the one-shot import with its Undo. With [pendingOnly] it skips the scan and
 * reviews the payments live capture found in new messages (`sms_pending`).
 */
class ImportViewModel(private val c: AppContainer, private val pendingOnly: Boolean = false) : ViewModel() {
    private val _state = MutableStateFlow(ImportState(mode = c.smsCapturePrefs.mode.value))
    val state: StateFlow<ImportState> = _state
    private var job: Job? = null
    private val importing = AtomicBoolean(false)
    private val inbox: SmsSource = c.smsInbox

    init {
        viewModelScope.launch {
            val cats = c.money.categories.first().filter { it.kind == "spending" && it.deletedAt == null }
            val has = c.smsImport.hasHistory()
            _state.update { it.copy(categories = cats, hasHistory = has, range = if (has) ImportRange.SinceLast else ImportRange.Last30) }
        }
        viewModelScope.launch { c.smsCapturePrefs.mode.collect { m -> _state.update { it.copy(mode = m) } } }
        if (pendingOnly) loadPending() else if (!c.smsCapturePrefs.offerShown) _state.update { it.copy(stage = ImportStage.Offer) }
    }

    /** Saves the mode chosen on the one-time offer or in the range step. */
    fun setMode(mode: CaptureMode) = c.smsCapturePrefs.setMode(mode, c.clock.now())

    /** Answers the one-time offer with [mode] and moves on to the permission step, or straight to the range when [readGranted]. */
    fun finishOffer(mode: CaptureMode, readGranted: Boolean) {
        setMode(mode)
        c.smsCapturePrefs.offerShown = true
        _state.update { it.copy(stage = if (readGranted) ImportStage.Range else ImportStage.Intro) }
        if (readGranted) loadEstimate()
    }

    /** Shows the payments found in new messages for review. */
    private fun loadPending() {
        _state.update { it.copy(stage = ImportStage.Scanning, progress = null) }
        job = viewModelScope.launch {
            try {
                val items = c.smsImport.pendingItems()
                val cats = c.money.categories.first().filter { it.kind == "spending" && it.deletedAt == null }
                val memory = c.money.memory.first()
                val payees = c.money.payees(items.mapNotNull { it.candidate.tx.payeeKey })
                val rows = items.map { rowFor(it, cats, memory, payees) }
                _state.update {
                    if (rows.isEmpty()) it.copy(stage = ImportStage.Done, summaryText = "Nothing is waiting", added = 0, categories = cats)
                    else it.copy(stage = ImportStage.Review, rows = rows, categories = cats, scanned = rows.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(stage = ImportStage.Done, summaryText = "Could not look just now", added = 0, message = null) }
            }
        }
    }

    /** Called by the screen once READ_SMS is granted (or already was). */
    fun permissionGranted() {
        if (_state.value.stage == ImportStage.Intro) {
            _state.update { it.copy(stage = ImportStage.Range, message = null) }
            loadEstimate()
        }
    }

    /** Called when the permission is lost, for example revoked in Settings while the screen was open. */
    fun permissionLost() {
        val s = _state.value.stage
        if (s == ImportStage.Range || s == ImportStage.Scanning) {
            job?.cancel()
            _state.update { it.copy(stage = ImportStage.Intro, message = "Messages are not allowed any more. You can allow them again, or paste a message instead.") }
        }
    }

    fun setRange(range: ImportRange) {
        _state.update { it.copy(range = range, estimate = null) }
        loadEstimate()
    }

    private var estimateJob: Job? = null

    private fun loadEstimate() {
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            val since = c.smsImport.rangeStart(_state.value.range)
            val n = try { inbox.count(since) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            _state.update { it.copy(estimate = n) }
        }
    }

    fun openPaste() = _state.update { it.copy(stage = ImportStage.Paste, message = null) }

    fun backToStart(granted: Boolean) = _state.update { it.copy(stage = if (granted) ImportStage.Range else ImportStage.Intro, message = null) }

    /** Scans the phone's inbox for the chosen range. */
    fun scanInbox() = startScan(inbox, null, fromPaste = false)

    /** Parses [text] the user pasted. */
    fun scanPasted(text: String) {
        if (text.isBlank()) return
        startScan(PastedSource(text, c.clock.now()), 0L, fromPaste = true)
    }

    private fun startScan(source: SmsSource, since: Long?, fromPaste: Boolean) {
        if (_state.value.stage == ImportStage.Scanning) return
        job?.cancel()
        _state.update { it.copy(stage = ImportStage.Scanning, progress = null, message = null, fromPaste = fromPaste) }
        job = viewModelScope.launch {
            try {
                val from = since ?: c.smsImport.rangeStart(_state.value.range)
                val result = c.smsImport.scan(source, from) { p -> _state.update { s -> s.copy(progress = p) } }
                val cats = c.money.categories.first().filter { it.kind == "spending" && it.deletedAt == null }
                val memory = c.money.memory.first()
                val payees = c.money.payees(result.items.mapNotNull { it.candidate.tx.payeeKey })
                val rows = result.items.map { rowFor(it, cats, memory, payees) }
                _state.update { it.copy(stage = ImportStage.Review, rows = rows, categories = cats, scanned = result.scanned, duplicatesDropped = result.duplicatesDropped) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SmsReadException) {
                _state.update {
                    if (e.kind == SmsReadException.Kind.PermissionRevoked) it.copy(stage = ImportStage.Intro, message = "Messages are not allowed any more. You can allow them again, or paste a message instead.")
                    else it.copy(stage = if (fromPaste) ImportStage.Paste else ImportStage.Range, message = "This phone would not share its messages just now. Try again, or paste a message instead.")
                }
            } catch (_: Exception) {
                _state.update { it.copy(stage = if (fromPaste) ImportStage.Paste else ImportStage.Range, message = "Something went wrong while looking. Nothing was changed. You can try again.") }
            }
        }
    }

    /** Stops a running scan and returns to the range choice. */
    fun cancelScan() {
        job?.cancel()
        _state.update { it.copy(stage = if (it.fromPaste) ImportStage.Paste else ImportStage.Range, progress = null) }
    }

    fun toggle(id: String) = updateRow(id) { it.copy(checked = !it.checked) }

    /** Checks every new row when some are unchecked, otherwise unchecks everything. */
    fun selectAllOrNone() = _state.update { s ->
        val anyNewOff = s.rows.any { !it.isDuplicate && !it.checked }
        s.copy(rows = s.rows.map { r -> if (anyNewOff) (if (r.isDuplicate) r else r.copy(checked = true)) else r.copy(checked = false) })
    }

    fun setKind(id: String, kind: String) = updateRow(id) { r ->
        if (r.kind == kind) r
        else if (kind == "received") r.copy(kind = kind, categoryId = null, picked = false)
        else r.copy(kind = kind, categoryId = r.suggestedId, picked = false)
    }

    fun pickCategory(id: String, categoryId: String) = updateRow(id) { it.copy(categoryId = categoryId, picked = true) }

    /** The user typed a label for the row; blank goes back to the generated name. Counts as an explicit edit. */
    fun setLabel(id: String, text: String) = updateRow(id) { it.copy(label = text.takeIf { t -> t.isNotBlank() }, labelEdited = true) }

    private fun updateRow(id: String, f: (ImportRow) -> ImportRow) = _state.update { s -> s.copy(rows = s.rows.map { if (it.id == id) f(it) else it }) }

    /** Imports the checked rows exactly once, then offers Undo for the whole batch. */
    fun import() {
        val rows = _state.value.rows
        if (_state.value.stage != ImportStage.Review || !importing.compareAndSet(false, true)) return
        _state.update { it.copy(stage = ImportStage.Importing) }
        viewModelScope.launch {
            try {
                val decisions = rows.map { ImportDecision(it.item, it.checked, it.kind, it.categoryId, it.suggestedId, it.picked, it.label, it.labelEdited) }
                val summary = c.smsImport.import(decisions)
                val dups = rows.count { !it.checked && it.isDuplicate }
                val left = rows.count { !it.checked && !it.isDuplicate }
                val text = summaryText(summary.added, dups, left)
                _state.update { it.copy(stage = ImportStage.Done, summaryText = text, added = summary.added, undone = false) }
                if (summary.added > 0) offerUndo(summary, text)
                offerRetroTag(summary)
            } catch (e: CancellationException) {
                importing.set(false)
                throw e
            } catch (_: Exception) {
                importing.set(false)
                _state.update { it.copy(stage = ImportStage.Review, message = "Could not save these just now. Nothing was added. Please try again.") }
            }
        }
    }

    /** After tagging payees, offers to tag the user's earlier payments to the same payees too (see [RetroTag]). */
    private suspend fun offerRetroTag(summary: ImportSummary) {
        val ids = summary.expenseIds.toSet()
        val per = summary.payeePicks.mapValues { (key, pick) -> c.money.retroChanges(mapOf(key to pick), ids) }.filterValues { it.isNotEmpty() }
        if (per.isNotEmpty()) RetroTag.post(RetroOffer(per.values.flatten(), per.size))
    }

    private fun offerUndo(summary: ImportSummary, text: String) {
        Undo.center.post("money", text) {
            RetroTag.dismiss()
            c.smsImport.undo(summary)
            _state.update { it.copy(undone = true, added = 0, summaryText = "Undone. Nothing was added.") }
        }
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        /** "Added 14 · skipped 3 duplicates · left out 2". */
        fun summaryText(added: Int, duplicates: Int, leftOut: Int): String {
            val parts = ArrayList<String>()
            parts += if (added == 0) "Nothing added" else "Added $added"
            if (duplicates > 0) parts += "skipped $duplicates duplicate" + if (duplicates == 1) "" else "s"
            if (leftOut > 0) parts += "left out $leftOut"
            return parts.joinToString(" · ")
        }
    }
}
