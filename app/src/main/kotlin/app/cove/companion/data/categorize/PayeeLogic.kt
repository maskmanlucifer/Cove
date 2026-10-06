package app.cove.companion.data.categorize

import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity

/** One earlier payment the retro-tag offer would change, with what it was so Undo can put it back. */
data class RetroChange(
    val expenseId: String,
    val fromCategoryId: String?,
    val fromNote: String,
    val toCategoryId: String,
    val toNote: String,
)

/** A payee Cove could remember from how the user already filed its payments. */
data class PastProposal(val payeeKey: String, val categoryId: String, val displayName: String, val count: Int)

/** Pure rules for retro-tagging earlier payments and for "Learn from my past payments". */
object PayeeLogic {
    /** Payees must appear at least this often under one category before they are proposed. */
    const val MIN_CONSISTENT = 2

    /**
     * Earlier spent expenses of the same payee ([others], already filtered to that key) that differ from what the user
     * just chose: another category, or (when a [label] was chosen) another note. [excludeIds] are the ones just saved.
     */
    fun retroChanges(others: List<ExpenseEntity>, excludeIds: Set<String>, categoryId: String, label: String?): List<RetroChange> =
        others.filter { it.deletedAt == null && it.kind == "spent" && it.id !in excludeIds }
            .filter { it.categoryId != categoryId || (label != null && it.note != label) }
            .sortedWith(compareByDescending<ExpenseEntity> { it.spentAt }.thenBy { it.id })
            .map { RetroChange(it.id, it.categoryId, it.note, categoryId, label ?: it.note) }

    /** "Tag 3 earlier payments to this payee too?" (or "these payees" when [payees] is more than one). */
    fun offerText(count: Int, payees: Int): String =
        "Tag $count earlier payment${if (count == 1) "" else "s"} to ${if (payees == 1) "this payee" else "these payees"} too?"

    /**
     * Payees with no memory yet whose spent [expenses] are filed consistently: one real category holds at least
     * [MIN_CONSISTENT] of them and strictly more than any other. Biggest first.
     */
    fun pastProposals(
        expenses: List<ExpenseEntity>,
        rememberedKeys: Set<String>,
        categories: List<ExpenseCategoryEntity>,
    ): List<PastProposal> {
        val usable = categories.filter { it.deletedAt == null && it.kind == "spending" && !it.name.trim().equals("Other", true) }.map { it.id }.toSet()
        return expenses.filter { it.deletedAt == null && it.kind == "spent" && it.payeeKey != null && it.payeeKey !in rememberedKeys }
            .groupBy { it.payeeKey!! }
            .mapNotNull { (key, list) ->
                val counts = list.mapNotNull { it.categoryId }.filter { it in usable }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                val top = counts.firstOrNull() ?: return@mapNotNull null
                if (top.value < MIN_CONSISTENT || (counts.size > 1 && counts[1].value >= top.value)) return@mapNotNull null
                val name = list.filter { it.categoryId == top.key }.groupingBy { it.note }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
                PastProposal(key, top.key, name, top.value)
            }
            .sortedWith(compareByDescending<PastProposal> { it.count }.thenBy { it.payeeKey })
    }
}
