package app.cove.companion.feature.brief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BriefLogicTest {
    private val facts = BriefFacts(
        name = "Maya",
        weather = WeatherFacts(24, 27, "clear", 10),
        items = listOf(DayItem(11 * 60, "Coffee with Jo", "Café Ivy")),
        todoTitles = listOf("Reply to Priya"),
        moneyLeftPaise = 1_157_950,
    )

    @Test
    fun scriptHasFourSegmentsWithChipTitles() {
        val s = BriefTemplates.build(facts)
        assertEquals(listOf("Weather · mild, 24°", "Your day", "Money · ₹11,580 left", "One thing to read"), s.map { it.title })
        assertTrue(s[1].text.startsWith("Coffee with Jo at 11 am at Café Ivy."))
    }

    @Test
    fun offlineBriefSkipsWeatherAndGreetsInDay() {
        val s = BriefTemplates.build(facts.copy(weather = null))
        assertEquals(listOf("Your day", "Money · ₹11,580 left", "One thing to read"), s.map { it.title })
        assertTrue(s[0].text.startsWith("Good morning, Maya."))
    }

    @Test
    fun generatedLinesReplaceTemplates() {
        val s = BriefTemplates.build(facts.copy(intro = "Rise and shine.", thought = "Be kind."))
        assertTrue(s[0].text.startsWith("Rise and shine."))
        assertEquals("Be kind.", s.last().text)
    }

    @Test
    fun overBudgetIsGentle() {
        val s = BriefTemplates.build(facts.copy(moneyLeftPaise = -21_000))
        assertEquals("Money · ₹210 over", s[2].title)
    }

    @Test
    fun chunksKeepOffsetsAndSplitOnSentences() {
        val text = "Coffee with Jo at eleven. Leave by 10:45, it’s a short walk."
        val c = BriefTiming.chunks(text)
        assertEquals(2, c.size)
        assertEquals(26, c[1].start)
        assertEquals(text, c.joinToString("") { it.text })
    }

    @Test
    fun longSentencesAreWrapped() {
        val text = "word ".repeat(100).trim() + "."
        val c = BriefTiming.chunks(text)
        assertTrue(c.size > 1 && c.all { it.text.length <= 221 })
        assertEquals(text, c.joinToString("") { it.text })
    }

    @Test
    fun durationsAndProgressMath() {
        val segs = listOf(BriefSegment("a", "one two three four five"), BriefSegment("b", "a b c d e f g h i j"))
        assertEquals(2, BriefTiming.segmentSeconds("hi"))
        assertEquals(2, BriefTiming.segmentSeconds("one two three four five"))
        assertEquals(4, BriefTiming.segmentSeconds(segs[1].text))
        assertEquals(6, BriefTiming.totalSeconds(segs))
        assertEquals(4f, BriefTiming.elapsedSeconds(segs, 1, 0.5f), 0.001f)
        val chunk = BriefTiming.chunks("Hello there. Bye now.")[1]
        assertEquals(13f / 21f, BriefTiming.segmentFraction("Hello there. Bye now.", chunk, 0), 0.001f)
        assertEquals("0:51", BriefTiming.clock(51))
        assertEquals("2:04", BriefTiming.clock(124))
        assertEquals("2 min", BriefTiming.minutesLabel(124))
        assertEquals("1 min", BriefTiming.minutesLabel(20))
    }

    @Test
    fun segmentsRoundTripThroughJson() {
        val s = BriefTemplates.build(facts)
        assertEquals(s, BriefCodec.decode(BriefCodec.encode(s)))
        assertTrue(BriefCodec.decode("garbage").isEmpty())
    }

    @Test
    fun parsesOpenMeteoReply() {
        val json = """{"current":{"temperature_2m":23.6,"weather_code":2},
            "daily":{"temperature_2m_max":[27.4],"precipitation_probability_max":[45]}}"""
        val w = WeatherParser.parse(json)
        assertNotNull(w)
        assertEquals(WeatherFacts(24, 27, "mostly clear", 45), w)
        assertEquals("mild", w!!.feel)
        assertNull(WeatherParser.parse("{}"))
        assertNull(WeatherParser.parse("not json"))
    }

    @Test
    fun workIsScheduledFifteenMinutesBeforeWake() {
        val zone = ZoneId.of("UTC")
        fun ms(h: Int, m: Int) = LocalDateTime.of(2026, 10, 7, h, m).atZone(zone).toInstant().toEpochMilli()
        assertEquals(15 * 60_000L, BriefScheduler.initialDelayMillis(ms(6, 0), 6 * 60 + 30, zone))
        assertEquals(24 * 60 * 60_000L, BriefScheduler.initialDelayMillis(ms(6, 15), 6 * 60 + 30, zone))
    }
}
