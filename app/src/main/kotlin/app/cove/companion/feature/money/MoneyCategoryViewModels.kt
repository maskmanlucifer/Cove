package app.cove.companion.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.Undo
import app.cove.companion.core.newId
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.categorize.CategoryTokens
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

private fun monthName(date: java.time.LocalDate) = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

data class CategoriesState(
    val month: String = "",
    val items: List<CategoryMonth> = emptyList(),
    val budgetTotal: Long = 0,
)

/** Categories with this month's progress, and reordering. */
class CategoriesViewModel(private val c: AppContainer) : ViewModel() {
    private val today = c.clock.now().toLocalDate()
    private val range = ledgerRange(today)

    val state: StateFlow<CategoriesState> = combine(
        c.money.categories, c.money.expenses(range.first, range.last),
    ) { categories, all ->
        val items = categoryMonths(categories, all, today)
        CategoriesState(monthName(today), items, items.filter { it.category.kind == "spending" }.sumOf { it.budget })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoriesState())

    fun reorder(ids: List<String>) {
        viewModelScope.launch { c.money.reorderCategories(ids) }
    }
}

data class CategoryDetailState(
    val loaded: Boolean = false,
    val category: ExpenseCategoryEntity? = null,
    val month: String = "",
    val spent: Long = 0,
    val budget: Long = 0,
    val daysToGo: Int = 0,
    val groups: List<DayGroup> = emptyList(),
) {
    val left: Long get() = budget - spent
}

/** One category's month: progress against budget and its transactions grouped by day. */
class CategoryDetailViewModel(c: AppContainer, id: String) : ViewModel() {
    private val today = c.clock.now().toLocalDate()
    private val range = ledgerRange(today)

    val state: StateFlow<CategoryDetailState> = combine(
        c.money.categories, c.money.expenses(range.first, range.last),
    ) { categories, all ->
        val cat = categories.firstOrNull { it.id == id }
        if (cat == null) CategoryDetailState(loaded = true) else {
            val month = categoryMonths(listOf(cat), all, today).single()
            CategoryDetailState(
                loaded = true,
                category = cat,
                month = monthName(today),
                spent = month.spent,
                budget = month.budget,
                daysToGo = MoneyMath.daysToGo(today),
                groups = MoneyMath.groupByDay(thisMonth(all, today).filter { it.categoryId == id }, today),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryDetailState())
}

/** Outcome of checking a category name; null from [validate] means it is fine. */
object CategoryNames {
    const val BLANK = "Add a name to continue"
    const val TAKEN = "That name is already used"

    /** Why [name] cannot be used, or null. [others] are the names of the other live categories. */
    fun validate(name: String, others: List<String>): String? = when {
        name.isBlank() -> BLANK
        others.any { it.trim().equals(name.trim(), ignoreCase = true) } -> TAKEN
        else -> null
    }
}

data class CategoryFormState(
    val isNew: Boolean = true,
    val name: String = "",
    val income: Boolean = false,
    /** Whole rupees typed by the user. */
    val budget: String = "",
    val carryOver: Boolean = false,
    val alertAt80: Boolean = true,
    /** Comma-separated words that file here; see [ExpenseCategoryEntity.keywords]. */
    val keywords: String = "",
    /** Inline guidance under the name: a duplicate name right away, a blank one after a try at saving. */
    val nameError: String? = null,
    /** How many expenses are filed here, so Delete can say what moves to Other. */
    val expenseCount: Int = 0,
)

/** Create or edit a category; [id] is `new` or an existing category id. */
class CategoryEditViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    private var existing: ExpenseCategoryEntity? = null

    /** Id a new category is saved under; fixed for this screen so saving twice rewrites one row. */
    private val newCategoryId = newId()
    private val form = MutableStateFlow(CategoryFormState(isNew = id == "new"))
    private val triedSave = MutableStateFlow(false)
    private val otherNames = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<CategoryFormState> = combine(form, triedSave, otherNames) { f, tried, others ->
        val error = CategoryNames.validate(f.name, others)
        f.copy(nameError = error?.takeIf { error == CategoryNames.TAKEN || tried })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, form.value)

    init {
        viewModelScope.launch {
            c.money.categories.collect { all -> otherNames.value = all.filter { it.id != id }.map { it.name } }
        }
        if (id != "new") {
            viewModelScope.launch {
                c.money.category(id)?.let { e ->
                    existing = e
                    form.value = CategoryFormState(
                        isNew = false, name = e.name, income = e.kind == "income",
                        budget = if (e.budgetPaise > 0) (e.budgetPaise / 100).toString() else "",
                        carryOver = e.carryOver, alertAt80 = e.alertAt80, keywords = e.keywords,
                        expenseCount = c.money.expenseCount(id),
                    )
                }
            }
        }
    }

    fun setName(name: String) = form.update { it.copy(name = name) }
    fun setIncome(income: Boolean) = form.update { it.copy(income = income) }
    fun setBudget(text: String) = form.update { it.copy(budget = text.filter(Char::isDigit).take(8)) }
    fun setCarryOver(on: Boolean) = form.update { it.copy(carryOver = on) }
    fun setAlert(on: Boolean) = form.update { it.copy(alertAt80 = on) }
    fun setKeywords(text: String) = form.update { it.copy(keywords = text.take(200)) }

    /** Saves the category; false when the name is blank or already used (the screen then shows why). */
    suspend fun save(): Boolean {
        triedSave.value = true
        val f = form.value
        val name = f.name.trim()
        if (CategoryNames.validate(name, otherNames.value) != null) return false
        val sort = existing?.sort ?: ((c.money.categories.first().maxOfOrNull { it.sort } ?: -1) + 1)
        c.money.saveCategory(
            (existing ?: ExpenseCategoryEntity(newCategoryId, name, sort = sort)).copy(
                name = name,
                kind = if (f.income) "income" else "spending",
                budgetPaise = (f.budget.toLongOrNull() ?: 0L) * 100,
                carryOver = f.carryOver,
                alertAt80 = f.alertAt80,
                keywords = CategoryTokens.cleanKeywords(f.keywords),
            ),
        )
        return true
    }

    /** Deletes the category and offers an Undo that brings it and its expenses back. */
    suspend fun delete() {
        val cat = existing ?: return
        val moved = c.money.deleteCategory(id)
        val message = if (moved.isEmpty()) "Category deleted"
        else "Category deleted · ${moved.size} expense${if (moved.size == 1) "" else "s"} moved to Other"
        Undo.center.post(MONEY_UNDO, message) { c.money.restoreCategory(cat.id, moved) }
    }
}
