package app.cove.companion.feature.money

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {
    private fun type(vararg keys: Char) = keys.fold("") { acc, k -> AmountInput.push(acc, k) }

    @Test fun buildsWholeAmounts() {
        assertEquals("250", type('2', '5', '0'))
        assertEquals(25_000L, AmountInput.toPaise("250"))
    }

    @Test fun ignoresLeadingZeros() {
        assertEquals("5", type('0', '5'))
        assertEquals("0", type('0', '0'))
    }

    @Test fun decimalsStopAtTwoPlaces() {
        assertEquals("12.34", type('1', '2', '.', '3', '4', '5'))
        assertEquals(1_234L, AmountInput.toPaise("12.34"))
        assertEquals(1_250L, AmountInput.toPaise("12.5"))
    }

    @Test fun pointFirstGetsAZero() {
        assertEquals("0.5", type('.', '5'))
        assertEquals("1.5", type('1', '.', '.', '5'))
    }

    @Test fun backspaceAndEmpty() {
        assertEquals("2", AmountInput.back("25"))
        assertEquals("", AmountInput.back(""))
        assertEquals(0L, AmountInput.toPaise(""))
    }

    @Test fun displayUsesIndianGrouping() {
        assertEquals("₹0", AmountInput.display(""))
        assertEquals("₹2,50,000", AmountInput.display("250000"))
        assertEquals("₹12.", AmountInput.display("12."))
        assertEquals("₹12.5", AmountInput.display("12.5"))
    }

    @Test fun roundTripsExistingAmounts() {
        assertEquals("250", AmountInput.fromPaise(25_000))
        assertEquals("250.05", AmountInput.fromPaise(25_005))
    }

    @Test fun refusedNinthDigitIsReported() {
        val full = type(*"99999999".toCharArray())
        assertEquals(full, AmountInput.push(full, '9'))
        assertEquals(true, AmountInput.wouldOverflow(full, '9'))
        assertEquals(false, AmountInput.wouldOverflow(full, '.'))
        assertEquals(false, AmountInput.wouldOverflow("9999", '9'))
        assertEquals(false, AmountInput.wouldOverflow("99999999.5", '9'))
    }
}
