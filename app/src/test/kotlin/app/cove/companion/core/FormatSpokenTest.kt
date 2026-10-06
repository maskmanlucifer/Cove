package app.cove.companion.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSpokenTest {
    @Test fun wholeAmountsReadAsRupees() = assertEquals("840 rupees", rupeesSpoken(84_000))

    @Test fun groupsLargeAmountsTheIndianWay() = assertEquals("1,20,000 rupees", rupeesSpoken(12_000_000))

    @Test fun keepsPaise() = assertEquals("12 rupees 50 paise", rupeesSpoken(1_250))
}
