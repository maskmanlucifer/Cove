package app.cove.companion.feature.money

import app.cove.companion.design.components.fitScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyLayoutLogicTest {
    private fun row(name: String, paise: Long) = MoneyRow(name.lowercase(), name, paise, 0)

    @Test fun compactRupeesAbbreviatesFromACrore() {
        assertEquals("₹9,99,999", MoneyMath.compactRupees(99_999_900))
        assertEquals("₹1 Cr", MoneyMath.compactRupees(1_00_00_000L * 100))
        assertEquals("₹20 Cr", MoneyMath.compactRupees(20_00_07_000L * 100))
        assertEquals("₹10 Cr", MoneyMath.compactRupees(9_99_99_999_99L))
        assertEquals("₹1.5 Cr", MoneyMath.compactRupees(1_50_00_000L * 100))
    }

    @Test fun spokenRupeesUsesTheWord() {
        assertEquals("2,50,000 rupees", MoneyMath.spokenRupees(25_000_000))
    }

    @Test fun fewRowsAreKeptAndSortedBySpend() {
        val rows = MoneyMath.collapseRows(listOf(row("Fun", 100), row("Food", 700), row("Home", 400)))
        assertEquals(listOf("Food", "Home", "Fun"), rows.map { it.name })
    }

    @Test fun overflowRowsFoldIntoOtherSoTheRowsSumToTheTotal() {
        val all = listOf(row("A", 500), row("B", 400), row("C", 300), row("D", 200), row("E", 100), row("F", 50))
        val rows = MoneyMath.collapseRows(all)
        assertEquals(listOf("A", "B", "C", "D", "Other"), rows.map { it.name })
        assertEquals(all.sumOf { it.spent }, rows.sumOf { it.spent })
        assertNull(rows.last().id)
    }

    @Test fun aSingleLeftoverRowKeepsItsOwnName() {
        val all = listOf(row("A", 500), row("B", 400), row("C", 300), row("D", 200), row("Other", 100))
        val rows = MoneyMath.collapseRows(all)
        assertEquals(5, rows.size)
        assertEquals("other", rows.last().id)
    }

    @Test fun fitScaleShrinksUntilTheTextFits() {
        assertEquals(1f, fitScale(100, 0.4f) { 80 }, 0.001f)
        assertEquals(0.5f, fitScale(100, 0.4f) { s -> (200 * s).toInt() }, 0.051f)
        assertEquals(0.4f, fitScale(10, 0.4f) { 1000 }, 0.001f)
    }

    @Test fun categoryNamesNeedToBeUniqueAndNonBlank() {
        assertEquals(CategoryNames.BLANK, CategoryNames.validate("  ", listOf("Food")))
        assertEquals(CategoryNames.TAKEN, CategoryNames.validate(" food ", listOf("Food", "Home")))
        assertNull(CategoryNames.validate("Gifts", listOf("Food", "Home")))
    }
}
