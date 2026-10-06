package app.cove.companion.feature.money

import app.cove.companion.core.startOfDayMillis
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import java.time.LocalDate
import java.time.YearMonth

/** A category's standing in the current month; [budget] already includes carried-over money. */
data class CategoryMonth(val category: ExpenseCategoryEntity, val spent: Long, val budget: Long) {
    val over: Boolean get() = MoneyMath.isOver(spent, budget)
}

/** Millis range covering last month and this month, which is all the Money screens query. */
fun ledgerRange(today: LocalDate): LongRange {
    val start = YearMonth.from(today).minusMonths(1).atDay(1).startOfDayMillis()
    val end = YearMonth.from(today).plusMonths(1).atDay(1).startOfDayMillis() - 1
    return start..end
}

/** Expenses that fall in [today]'s month. */
fun thisMonth(expenses: List<ExpenseEntity>, today: LocalDate): List<ExpenseEntity> =
    expenses.filter { YearMonth.from(it.spentAt.toLocalDate()) == YearMonth.from(today) }

/** Per-category spend and budget for [today]'s month, in the user's category order. */
fun categoryMonths(
    categories: List<ExpenseCategoryEntity>,
    expenses: List<ExpenseEntity>,
    today: LocalDate,
): List<CategoryMonth> {
    val current = thisMonth(expenses, today).filter { it.kind == "spent" }
    val prevMonth = YearMonth.from(today).minusMonths(1)
    val previous = expenses.filter { it.kind == "spent" && YearMonth.from(it.spentAt.toLocalDate()) == prevMonth }
    return categories.map { cat ->
        val carried = MoneyMath.carriedOver(cat.budgetPaise, previous.filter { it.categoryId == cat.id }.sumOf { it.amountPaise }, cat.carryOver)
        CategoryMonth(cat, current.filter { it.categoryId == cat.id }.sumOf { it.amountPaise }, cat.budgetPaise + carried)
    }
}
