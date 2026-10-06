package app.cove.companion.data.categorize

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.CategoryRequest
import app.cove.companion.ai.model.CategorySuggestion
import app.cove.companion.ai.model.Location
import app.cove.companion.data.local.entity.CategoryMemoryEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.repo.CategoryChange

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Which expenses need a look, and the pure rules of the Review screen. */
object ReviewLogic {
    /** The Money tab shows its Review row only from this many unfiled expenses. */
    const val SHOW_MIN = 3

    /** How far back unfiled expenses count. */
    const val UNFILED_DAYS = 60

    /** How far back "Cross-check this month" looks. */
    const val CROSS_CHECK_DAYS = 30

    private fun isUnfiled(e: ExpenseEntity, usable: Map<String, ExpenseCategoryEntity>): Boolean {
        val cat = e.categoryId?.let { usable[it] }
        return cat == null || cat.name.trim().equals("Other", true)
    }

    private fun spendingById(categories: List<ExpenseCategoryEntity>) =
        categories.filter { it.deletedAt == null && it.kind == "spending" }.associateBy { it.id }

    /** Spent expenses of the last [UNFILED_DAYS] days with no category or in "Other", newest first. */
    fun unfiled(expenses: List<ExpenseEntity>, categories: List<ExpenseCategoryEntity>, now: Long): List<ExpenseEntity> {
        val usable = spendingById(categories)
        val from = now - UNFILED_DAYS * DAY_MS
        return expenses.filter { it.deletedAt == null && it.kind == "spent" && it.spentAt in from..now && isUnfiled(it, usable) }
            .sortedWith(compareByDescending<ExpenseEntity> { it.spentAt }.thenBy { it.id })
    }

    /** "5 in Other · Review", or null when fewer than [SHOW_MIN] are unfiled. */
    fun bannerText(count: Int): String? = if (count >= SHOW_MIN) "$count in Other · Review" else null

    /** Rows for [unfiled] expenses, each with the offline categorizer's suggestion (never "Other"). */
    fun rows(unfiled: List<ExpenseEntity>, categories: List<ExpenseCategoryEntity>, memory: Map<String, CategoryMemoryEntity>): List<ReviewRow> =
        unfiled.map { e ->
            val s = ExpenseCategorizer.suggest(e.note, categories, memory)
            ReviewRow(e, s)
        }

    /** Filed expenses of the last [CROSS_CHECK_DAYS] days with a note, in a real (non-Other) category, newest first. */
    fun crossCheckCandidates(expenses: List<ExpenseEntity>, categories: List<ExpenseCategoryEntity>, now: Long): List<ExpenseEntity> {
        val usable = spendingById(categories)
        val from = now - CROSS_CHECK_DAYS * DAY_MS
        return expenses.filter {
            it.deletedAt == null && it.kind == "spent" && it.spentAt in from..now && !isUnfiled(it, usable) && CategoryTokens.tokens(it.note).isNotEmpty()
        }.sortedWith(compareByDescending<ExpenseEntity> { it.spentAt }.thenBy { it.id })
    }

    /** Category names the AI may choose from: the user's spending categories except "Other". */
    fun aiCategoryNames(categories: List<ExpenseCategoryEntity>): List<String> =
        categories.filter { it.deletedAt == null && it.kind == "spending" && !it.name.trim().equals("Other", true) }.map { it.name }

    /** Rows only for the expenses whose AI category differs from the current one; the AI pick is the suggestion. */
    fun disagreements(
        candidates: List<ExpenseEntity>,
        aiByExpense: Map<String, String>,
        categories: List<ExpenseCategoryEntity>,
    ): List<ReviewRow> {
        val byName = categories.filter { it.deletedAt == null }.associateBy { it.name.trim().lowercase() }
        return candidates.mapNotNull { e ->
            val ai = aiByExpense[e.id]?.let { byName[it.trim().lowercase()] } ?: return@mapNotNull null
            if (ai.id == e.categoryId) null else ReviewRow(e, Suggestion(ai.id, 0.5f, Reason.Ai))
        }
    }

    /** Plain-language line for an AI failure; never mentions keys or content. */
    fun errorText(error: AiError): String = when (error) {
        is AiError.NeedsConfig -> "Add a Gemini key in Me, Connect services, or use a phone with on-device AI."
        AiError.Offline -> "You're offline. Checking with AI needs the internet on this phone."
        AiError.NeedsForeground -> "Keep Cove open and try again."
        AiError.Busy, AiError.RateLimited -> "AI is busy right now. Try again in a little while."
        AiError.Timeout -> "That took too long. Try again."
        AiError.InvalidOutput -> "The answer didn't make sense, so nothing was changed."
        AiError.PrivacyBlocked -> "This stays on your phone."
        is AiError.Unavailable -> "AI isn't available right now. Add a Gemini key in Me, Connect services, or use a phone with on-device AI."
    }

    /** "On-device" or "Cloud". */
    fun provenance(location: Location): String = if (location == Location.Cloud) "Cloud" else "On-device"
}

/** One expense in the review list. [picked] is the user's own chip choice, which beats the suggestion. */
data class ReviewRow(
    val expense: ExpenseEntity,
    val suggestion: Suggestion,
    val picked: String? = null,
    val skipped: Boolean = false,
    val done: Boolean = false,
) {
    /** Category this row would be filed under, or null when there is nothing to accept yet. */
    val targetId: String? get() = picked ?: suggestion.categoryId

    /** Reason label for the chip: the suggestion's, or "your pick". */
    val reasonLabel: String? get() = if (picked != null) "your pick" else suggestion.reason?.label

    /** The change accepting this row would make, or null. */
    val change: CategoryChange? get() = targetId?.let { CategoryChange(expense.id, expense.categoryId, it) }
}

/** The review list and the last accept-all, as plain state; the view model applies [CategoryChange]s to the database. */
data class ReviewState(val rows: List<ReviewRow> = emptyList(), val undo: List<CategoryChange> = emptyList()) {
    /** Rows still waiting for a decision. */
    val open: List<ReviewRow> get() = rows.filter { !it.done && !it.skipped }

    /** Open rows with nothing to accept; these are what "Check with AI" asks about. */
    val unresolved: List<ReviewRow> get() = open.filter { it.targetId == null }

    /** What "Accept all" would do: every open row that has a target. */
    fun acceptAllChanges(): List<CategoryChange> = open.mapNotNull { it.change }

    private fun edit(id: String, f: (ReviewRow) -> ReviewRow) = copy(rows = rows.map { if (it.expense.id == id) f(it) else it })

    fun pick(id: String, categoryId: String) = edit(id) { it.copy(picked = categoryId, skipped = false) }

    fun skip(id: String) = edit(id) { it.copy(skipped = true) }

    /** Marks [ids] filed; when [asUndo] is given it becomes the undoable batch. */
    fun filed(ids: Set<String>, asUndo: List<CategoryChange> = emptyList()) =
        copy(rows = rows.map { if (it.expense.id in ids) it.copy(done = true) else it }, undo = asUndo)

    /** Brings back the rows of [undo] and clears it. */
    fun undone() = copy(rows = rows.map { r -> if (undo.any { it.expenseId == r.expense.id }) r.copy(done = false) else r }, undo = emptyList())

    /** Gives AI picks (expense id to category id) to open rows that have no target yet. */
    fun withAi(picks: Map<String, String>) =
        copy(rows = rows.map { r -> if (!r.done && r.targetId == null) picks[r.expense.id]?.let { r.copy(suggestion = Suggestion(it, 0.5f, Reason.Ai)) } ?: r else r })
}

/** What a bulk AI pass produced: picks by expense id (category names), the provider's [location], and the error that stopped it. */
data class BulkOutcome(val picks: Map<String, String>, val location: Location?, val error: AiError?)

/** Runs [ask] over [items] (expense id, note) in batches of at most [CategoryRequest.MAX_BATCH], one call per batch, stopping at the first failure. */
suspend fun suggestInBatches(
    items: List<Pair<String, String>>,
    categories: List<String>,
    ask: suspend (notes: List<String>, categories: List<String>) -> AiResult<List<CategorySuggestion>>,
): BulkOutcome {
    val picks = LinkedHashMap<String, String>()
    var location: Location? = null
    for (batch in CategoryRequest.batches(items)) {
        when (val r = ask(batch.map { it.second }, categories)) {
            is AiResult.Ok -> {
                location = r.source.location
                r.value.forEach { s -> batch.getOrNull(s.index)?.let { picks[it.first] = s.category } }
            }
            is AiResult.Failed -> return BulkOutcome(picks, location, r.error)
        }
    }
    return BulkOutcome(picks, location, null)
}
