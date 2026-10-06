package app.cove.companion.data.categorize

import app.cove.companion.data.local.entity.PayeeMemoryEntity

/**
 * Pure update rules for [PayeeMemoryEntity], the memory keyed by `PayeeKey` rather than by words. Like
 * [CategoryLearning]: agreeing confirmations raise the count, a different category lowers it, and at one the new
 * category takes over. The label always follows the latest explicit choice of the same category.
 */
object PayeeLearning {
    /** Highest count kept, so an old habit can still be changed in a few corrections. */
    const val MAX_COUNT = 20

    /**
     * The row after the user filed payee [key] under [categoryId].
     *
     * @param label the note the user wants shown, or null to keep showing the cleaned merchant name
     * @param displayName the cleaned merchant text, shown in lists and on the expense
     * @param keepLabel keep the stored label of an agreeing row (a category-only confirmation such as Review's Accept)
     */
    fun learn(
        existing: PayeeMemoryEntity?, key: String, categoryId: String, label: String?, displayName: String, now: Long, keepLabel: Boolean = false,
    ): PayeeMemoryEntity {
        val live = existing?.takeIf { it.deletedAt == null && it.count > 0 }
        val name = displayName.ifBlank { live?.displayName.orEmpty() }
        return when {
            live == null -> PayeeMemoryEntity(key, categoryId, label, name, 1, now)
            live.categoryId == categoryId ->
                live.copy(count = minOf(live.count + 1, MAX_COUNT), label = if (keepLabel) live.label else label, displayName = name, updatedAt = now)
            live.count <= 1 -> PayeeMemoryEntity(key, categoryId, label, name, 1, now)
            else -> live.copy(count = live.count - 1, updatedAt = now)
        }
    }

    /**
     * The label to store for a note the user ended with: null when it is blank or still the generated [generated]
     * name (so the cleaned merchant name keeps being used), otherwise the trimmed note as typed.
     */
    fun labelFor(note: String, generated: String): String? {
        val n = note.trim()
        return n.takeIf { it.isNotEmpty() && !it.equals(generated.trim(), ignoreCase = true) }
    }

    /** The row soft-deleted, as "Forget" leaves it so sync carries the deletion. */
    fun forgotten(row: PayeeMemoryEntity, now: Long): PayeeMemoryEntity = row.copy(count = 0, deletedAt = now, updatedAt = now)
}
