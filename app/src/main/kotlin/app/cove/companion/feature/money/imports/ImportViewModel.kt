package app.cove.companion.feature.money.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.Undo
import app.cove.companion.data.categorize.ExpenseCategorizer
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.sms.Direction
import app.cove.companion.data.sms.ImportDecision
import app.cove.companion.data.sms.ImportRange
import app.cove.companion.data.sms.ImportSummary
import app.cove.companion.data.sms.PastedSource
import app.cove.companion.data.sms.ReviewItem
import app.cove.companion.data.sms.ScanProgress
import app.cove.companion.data.sms.SmsReadException
import app.cove.companion.data.sms.SmsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Where the flow is. */
enum class ImportStage { Intro, Range, Scanning, Review, Importing, Done, Paste }

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
) {
    val id: String get() = item.id
    val isDuplicate: Boolean get() = item.match != null
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
) {
    val checkedCount: Int get() = rows.count { it.checked }
    val newRows: List<ImportRow> get() = rows.filterNot { it.isDuplicate }
    val dupRows: List<ImportRow> get() = rows.filter { it.isDuplicate }
}

/** Runs the scan, holds the user's choices and performs the one-shot import with its Undo. */
class ImportViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ImportState())
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
                val rows = result.items.map { item ->
                    val tx = item.candidate.tx
                    val kind = if (tx.direction == Direction.Credit) "received" else "spent"
                    val s = if (kind == "spent") ExpenseCategorizer.suggest(c.smsImport.noteFor(tx, kind), cats, memory) else null
                    val id = s?.categoryId ?: if (kind == "spent") ExpenseCategorizer.fallback(cats)?.id else null
                    ImportRow(item, checked = item.match == null, kind = kind, categoryId = id, suggestedId = id, reason = s?.reason?.label)
                }
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

    private fun updateRow(id: String, f: (ImportRow) -> ImportRow) = _state.update { s -> s.copy(rows = s.rows.map { if (it.id == id) f(it) else it }) }

    /** Imports the checked rows exactly once, then offers Undo for the whole batch. */
    fun import() {
        val rows = _state.value.rows
        if (_state.value.stage != ImportStage.Review || !importing.compareAndSet(false, true)) return
        _state.update { it.copy(stage = ImportStage.Importing) }
        viewModelScope.launch {
            try {
                val decisions = rows.map { ImportDecision(it.item, it.checked, it.kind, it.categoryId, it.suggestedId, it.picked) }
                val summary = c.smsImport.import(decisions)
                val dups = rows.count { !it.checked && it.isDuplicate }
                val left = rows.count { !it.checked && !it.isDuplicate }
                val text = summaryText(summary.added, dups, left)
                _state.update { it.copy(stage = ImportStage.Done, summaryText = text, added = summary.added, undone = false) }
                if (summary.added > 0) offerUndo(summary, text)
            } catch (e: CancellationException) {
                importing.set(false)
                throw e
            } catch (_: Exception) {
                importing.set(false)
                _state.update { it.copy(stage = ImportStage.Review, message = "Could not save these just now. Nothing was added. Please try again.") }
            }
        }
    }

    private fun offerUndo(summary: ImportSummary, text: String) {
        Undo.center.post("money", text) {
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
