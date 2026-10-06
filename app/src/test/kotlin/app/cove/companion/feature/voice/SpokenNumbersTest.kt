package app.cove.companion.feature.voice

import app.cove.companion.ai.provider.rules.SpokenNumbers
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenNumbersTest {
    private val cases = listOf(
        "six thirty" to "6 30",
        "set an alarm for six thirty tomorrow" to "set an alarm for 6 30 tomorrow",
        "seven forty five" to "7 45",
        "twenty five" to "25",
        "twenty-five rupees" to "25 rupees",
        "nineteen" to "19",
        "four" to "4",
        "three hundred forty" to "340",
        "three hundred and forty" to "340",
        "one hundred" to "100",
        "fifteen hundred" to "1500",
        "two thousand five hundred" to "2500",
        "six oh five" to "6 05",
        "Spent Three Hundred Forty on lunch" to "Spent 340 on lunch",
        "no one is here" to "no one is here",
        "remind me at four" to "remind me at 4",
        "call someone at noon" to "call someone at noon",
        "nothing to change" to "nothing to change",
    )

    @Test
    fun digitizes() = cases.forEach { (input, expected) -> assertEquals(input, expected, SpokenNumbers.digitize(input)) }
}
