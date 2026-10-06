package app.cove.companion.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.data.categorize.ReviewLogic
import app.cove.companion.data.categorize.ReviewState
import app.cove.companion.data.categorize.suggestInBatches
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.repo.CategoryChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Review screen is listing. */
enum class ReviewMode { Unfiled, CrossCheck }

/**
 * Review screen state. [message] is a plain-language note (errors, "AI agrees"); [provenance] says which AI
 * answered the last check ("On-device" or "Cloud"); [undoCount] is the size of the last Accept all.
 */
data class ReviewUiState(
    val loaded: Boolean = false,
    val mode: ReviewMode = ReviewMode.Unfiled,
    val review: ReviewState = ReviewState(),
    /** Spending categories the user can pick from. */
    val categories: List<ExpenseCategoryEntity> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val provenance: String? = null,
    val undoCount: Int = 0,
)

/** Lists unfiled expenses with offline suggestions, and runs the manual, bulk AI checks. Nothing changes until the user taps. */
class ReviewViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state

    init {
        viewModelScope.launch { loadUnfiled() }
    }

    private suspend fun loadUnfiled() {
        val cats = c.money.categories.first()
        val now = c.clock.now()
        val unfiled = ReviewLogic.unfiled(c.money.spentSince(now - ReviewLogic.UNFILED_DAYS * DAY_MS), cats, now)
        val rows = ReviewLogic.rows(unfiled, cats, c.money.memory.first())
        _state.update {
            it.copy(loaded = true, mode = ReviewMode.Unfiled, review = ReviewState(rows), categories = cats.filter { c -> c.kind == "spending" }, message = null, undoCount = 0)
        }
    }

    fun pick(expenseId: String, categoryId: String) = _state.update { it.copy(review = it.review.pick(expenseId, categoryId)) }

    fun skip(expenseId: String) = _state.update { it.copy(review = it.review.skip(expenseId)) }

    /** Files one row under its target category and teaches the memory. */
    fun accept(expenseId: String) {
        val change = _state.value.review.rows.firstOrNull { it.expense.id == expenseId }?.change ?: return
        viewModelScope.launch {
            c.money.applyCategories(listOf(change))
            _state.update { it.copy(review = it.review.filed(setOf(expenseId), it.review.undo)) }
        }
    }

    /** Files every open row that has a suggestion; [undoLast] reverses exactly this batch. */
    fun acceptAll() {
        val changes = _state.value.review.acceptAllChanges()
        if (changes.isEmpty()) return
        viewModelScope.launch {
            val applied: List<CategoryChange> = c.money.applyCategories(changes)
            _state.update { it.copy(review = it.review.filed(changes.map { ch -> ch.expenseId }.toSet(), applied), undoCount = applied.size) }
        }
    }

    fun undoLast() {
        val undo = _state.value.review.undo
        if (undo.isEmpty()) return
        viewModelScope.launch {
            c.money.undoCategories(undo)
            _state.update { it.copy(review = it.review.undone(), undoCount = 0) }
        }
    }

    fun dismissUndo() = _state.update { it.copy(undoCount = 0) }

    /** "Check with AI": asks about the rows that still have no suggestion, in batches; manual only. */
    fun checkWithAi() {
        val s = _state.value
        if (s.busy || s.mode != ReviewMode.Unfiled) return
        val asking = s.review.unresolved.filter { it.expense.note.isNotBlank() }
        if (asking.isEmpty()) {
            _state.update { it.copy(message = "Nothing left to check.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, provenance = null) }
            val names = ReviewLogic.aiCategoryNames(s.categories)
            val out = suggestInBatches(asking.map { it.expense.id to it.expense.note }, names) { notes, cats -> c.ai.suggestCategories(notes, cats) }
            val byName = s.categories.associateBy { it.name.trim().lowercase() }
            val picks = out.picks.mapNotNull { (id, name) -> byName[name.trim().lowercase()]?.let { id to it.id } }.toMap()
            _state.update {
                it.copy(
                    busy = false,
                    review = it.review.withAi(picks),
                    provenance = out.location?.let(ReviewLogic::provenance),
                    message = out.error?.let(ReviewLogic::errorText)
                        ?: if (picks.isEmpty()) "AI had no suggestions for these." else null,
                )
            }
        }
    }

    /** "Cross-check this month": asks AI about already-filed expenses and lists only where it disagrees. */
    fun crossCheck() {
        val s = _state.value
        if (s.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, provenance = null) }
            val cats = c.money.categories.first()
            val now = c.clock.now()
            val candidates = ReviewLogic.crossCheckCandidates(c.money.spentSince(now - ReviewLogic.CROSS_CHECK_DAYS * DAY_MS), cats, now)
            if (candidates.isEmpty()) {
                _state.update { it.copy(busy = false, message = "Nothing filed this month to check.") }
                return@launch
            }
            val out = suggestInBatches(candidates.map { it.id to it.note }, ReviewLogic.aiCategoryNames(cats)) { notes, names -> c.ai.suggestCategories(notes, names) }
            val rows = ReviewLogic.disagreements(candidates, out.picks, cats)
            if (out.error != null && rows.isEmpty()) {
                _state.update { it.copy(busy = false, message = ReviewLogic.errorText(out.error)) }
                return@launch
            }
            _state.update {
                it.copy(
                    busy = false,
                    mode = ReviewMode.CrossCheck,
                    review = ReviewState(rows),
                    categories = cats.filter { c -> c.kind == "spending" },
                    provenance = out.location?.let(ReviewLogic::provenance),
                    undoCount = 0,
                    message = out.error?.let(ReviewLogic::errorText)
                        ?: if (rows.isEmpty()) "AI agrees with how you filed all ${candidates.size}." else null,
                )
            }
        }
    }

    /** Back to the unfiled list. */
    fun showUnfiled() {
        viewModelScope.launch { loadUnfiled() }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
