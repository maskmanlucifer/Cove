package app.cove.companion.feature.money

import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyLedgerTest {
    private val today = LocalDate.of(2026, 10, 5)
    private fun at(d: LocalDate) = LocalDateTime.of(d, LocalTime.NOON).toEpochMillis()
    private fun spend(cat: String, paise: Long, d: LocalDate) =
        ExpenseEntity("${cat}$paise$d", paise, categoryId = cat, spentAt = at(d))

    @Test fun carryOverAddsLastMonthsLeftover() {
        val food = ExpenseCategoryEntity("food", "Food", budgetPaise = 900_000, carryOver = true)
        val fun_ = ExpenseCategoryEntity("fun", "Fun", budgetPaise = 200_000)
        val expenses = listOf(
            spend("food", 700_000, LocalDate.of(2026, 9, 12)),
            spend("fun", 100_000, LocalDate.of(2026, 9, 12)),
            spend("food", 100_000, LocalDate.of(2026, 10, 2)),
            spend("fun", 250_000, LocalDate.of(2026, 10, 3)),
        )
        val (f, u) = categoryMonths(listOf(food, fun_), expenses, today)
        assertEquals(1_100_000L, f.budget)
        assertEquals(100_000L, f.spent)
        assertEquals(200_000L, u.budget)
        assertEquals(true, u.over)
    }

    @Test fun incomeAndOtherMonthsAreIgnored() {
        val food = ExpenseCategoryEntity("food", "Food", budgetPaise = 900_000)
        val expenses = listOf(
            spend("food", 100_000, today).copy(kind = "received"),
            spend("food", 100_000, LocalDate.of(2026, 8, 30)),
        )
        assertEquals(0L, categoryMonths(listOf(food), expenses, today).single().spent)
    }
}
