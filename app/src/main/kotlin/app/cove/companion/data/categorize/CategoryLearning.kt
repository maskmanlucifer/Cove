package app.cove.companion.data.categorize

import app.cove.companion.data.local.entity.CategoryMemoryEntity

/**
 * Pure update rules for [CategoryMemoryEntity]. A token holds one category; agreeing picks raise its count,
 * disagreeing picks lower it, and when it reaches zero the new category takes over.
 */
object CategoryLearning {
    /** Highest count kept, so an old habit can still be unlearned in a few corrections. */
    const val MAX_COUNT = 20

    /** The row after the user filed [token] under [categoryId]. */
    fun learn(existing: CategoryMemoryEntity?, token: String, categoryId: String, now: Long): CategoryMemoryEntity {
        val live = existing?.takeIf { it.deletedAt == null && it.count > 0 }
        return when {
            live == null -> CategoryMemoryEntity(token, categoryId, 1, now)
            live.categoryId == categoryId -> live.copy(count = minOf(live.count + 1, MAX_COUNT), updatedAt = now)
            live.count <= 1 -> CategoryMemoryEntity(token, categoryId, 1, now)
            else -> live.copy(count = live.count - 1, updatedAt = now)
        }
    }

    /** The row after the user took [token] out of [categoryId]; null when there is nothing to change. */
    fun unlearn(existing: CategoryMemoryEntity?, categoryId: String, now: Long): CategoryMemoryEntity? {
        if (existing == null || existing.deletedAt != null || existing.categoryId != categoryId) return null
        return if (existing.count <= 1) existing.copy(count = 0, deletedAt = now, updatedAt = now)
        else existing.copy(count = existing.count - 1, updatedAt = now)
    }
}
