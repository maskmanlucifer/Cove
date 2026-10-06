package app.cove.companion.data.categorize

import app.cove.companion.ai.provider.rules.CategoryResolver
import app.cove.companion.data.local.entity.CategoryMemoryEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.repo.MoneyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [CategoryResolver] backed by a live snapshot of the user's categories and memory, so parsing stays synchronous. */
class LiveCategoryResolver(money: MoneyRepository, scope: CoroutineScope) : CategoryResolver {
    @Volatile private var categories: List<ExpenseCategoryEntity> = emptyList()
    @Volatile private var memory: Map<String, CategoryMemoryEntity> = emptyMap()

    init {
        scope.launch { money.categories.collect { categories = it } }
        scope.launch { money.memory.collect { memory = it } }
    }

    override fun categoryFor(note: String): String? {
        val id = ExpenseCategorizer.suggest(note, categories, memory).categoryId ?: return null
        return categories.firstOrNull { it.id == id }?.name
    }
}
