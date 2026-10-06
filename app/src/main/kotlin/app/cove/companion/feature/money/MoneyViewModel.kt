package app.cove.companion.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.toLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.format.TextStyle
import java.util.Locale

/** A line in the Money tab's category card. [id] is null for spending with no category ("Other"). */
data class MoneyRow(val id: String?, val name: String, val spent: Long, val budget: Long)

data class MoneyState(
    val month: String = "",
    val spent: Long = 0,
    /** Total budget minus spend; null when no budgets are set. */
    val left: Long? = null,
    val daysToGo: Int = 0,
    val bars: List<DayBar> = emptyList(),
    val rows: List<MoneyRow> = emptyList(),
    val empty: Boolean = true,
)

/** Month overview for the Money tab: spend so far, daily strip and the four busiest categories. */
class MoneyViewModel(c: AppContainer) : ViewModel() {
    private val today = c.clock.now().toLocalDate()
    private val range = ledgerRange(today)

    val state: StateFlow<MoneyState> = combine(
        c.money.categories, c.money.expenses(range.first, range.last),
    ) { categories, all ->
        val month = thisMonth(all, today)
        val spending = categories.filter { it.kind == "spending" }
        val perCategory = categoryMonths(spending, all, today)
        val spent = MoneyMath.spentOf(month)
        val budget = perCategory.sumOf { it.budget }
        val loose = month.filter { it.kind == "spent" && it.categoryId == null }.sumOf { it.amountPaise }
        val rows = perCategory.filter { it.spent > 0 }
            .map { MoneyRow(it.category.id, it.category.name, it.spent, it.budget) }
            .let { if (loose > 0) it + MoneyRow(null, "Other", loose, 0) else it }
            .sortedByDescending { it.spent }
            .take(4)
        MoneyState(
            month = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            spent = spent,
            left = if (budget > 0) budget - spent else null,
            daysToGo = MoneyMath.daysToGo(today),
            bars = MoneyMath.dailyBars(month, today),
            rows = rows,
            empty = month.none { it.kind == "spent" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoneyState())
}
