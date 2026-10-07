package app.cove.companion.ai

import app.cove.companion.ai.model.DetailDrafts
import app.cove.companion.ai.model.DetailKind
import app.cove.companion.ai.model.FoundDetail
import app.cove.companion.ai.model.VoiceIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailDraftsTest {
    private val now = 1_000_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun found(text: String, phrase: String, kind: DetailKind, millis: Long? = null, amount: Double? = null, label: String? = null): FoundDetail {
        val start = text.indexOf(phrase)
        return FoundDetail(kind, start, start + phrase.length, phrase, millis, amount, if (amount != null) "Rs" else null, label)
    }

    @Test fun aFutureTimeBecomesAReminderWithoutTheTimeWords() {
        val text = "Ping the plumber 98765 43210 around 5 tomorrow"
        val r = DetailDrafts.draft(text, listOf(
            found(text, "98765 43210", DetailKind.Phone),
            found(text, "5 tomorrow", DetailKind.DateTime, millis = now + day),
        ), now).single() as VoiceIntent.AddReminder
        assertEquals(now + day, r.at)
        assertEquals("Ping the plumber · 98765 43210", r.title)
    }

    @Test fun aDayWithoutAnHourGetsNineInTheMorningNotMidnight() {
        val zone = java.time.ZoneId.systemDefault()
        val midnight = java.time.LocalDate.of(2026, 10, 9).atStartOfDay(zone).toInstant().toEpochMilli()
        val nowAtNoon = java.time.LocalDate.of(2026, 10, 8).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val text = "Call the plumber on Friday morning"
        val start = text.indexOf("Friday morning")
        val date = FoundDetail(DetailKind.DateTime, start, start + "Friday morning".length, "Friday morning", epochMillis = midnight, hasTime = false)
        val r = DetailDrafts.draft(text, listOf(date), nowAtNoon).single() as VoiceIntent.AddReminder
        assertEquals(java.time.LocalDate.of(2026, 10, 9).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), r.at)
        assertEquals("Call the plumber", r.title)
    }

    @Test fun aFlightWithADateIsAReminderNamedAfterTheFlight() {
        val text = "Flight AI 302 on Friday"
        val r = DetailDrafts.draft(text, listOf(
            found(text, "AI 302", DetailKind.Flight, label = "flight AI 302"),
            found(text, "Friday", DetailKind.DateTime, millis = now + 2 * day),
        ), now).single() as VoiceIntent.AddReminder
        assertEquals("Catch flight AI 302", r.title)
    }

    @Test fun anAmountWithNoFutureDateIsAnExpense() {
        val text = "Rent receipt paid Rs 18,500 on the 3rd"
        val e = DetailDrafts.draft(text, listOf(
            found(text, "Rs 18,500", DetailKind.Money, amount = 18500.0),
            found(text, "the 3rd", DetailKind.DateTime, millis = now - 5 * day),
        ), now).single() as VoiceIntent.LogExpense
        assertEquals(1_850_000L, e.amountPaise)
        assertEquals(now - 5 * day, e.at)
        assertEquals("Rent receipt paid", e.note)
        assertEquals(false, e.received)
    }

    @Test fun receivedMoneyIsIncome() {
        val text = "Got Rs 2,000 from Rahul"
        val e = DetailDrafts.draft(text, listOf(found(text, "Rs 2,000", DetailKind.Money, amount = 2000.0)), now).single() as VoiceIntent.LogExpense
        assertTrue(e.received)
    }

    @Test fun anAmountWithAFutureDateIsAReminderThatKeepsTheAmount() {
        val text = "Pay Rs 2,500 rent on Friday"
        val r = DetailDrafts.draft(text, listOf(
            found(text, "Rs 2,500", DetailKind.Money, amount = 2500.0),
            found(text, "Friday", DetailKind.DateTime, millis = now + 2 * day),
        ), now).single() as VoiceIntent.AddReminder
        assertEquals("Pay Rs 2,500 rent", r.title)
    }

    @Test fun aFlightWithNoTimeIsKeptAsANote() {
        val text = "Flight AI 302 is my one on Friday"
        val m = DetailDrafts.draft(text, listOf(found(text, "AI 302", DetailKind.Flight, label = "flight AI 302")), now).single() as VoiceIntent.Remember
        assertEquals("flight ai 302", m.subject)
        assertEquals("AI 302", m.detail)
    }

    @Test fun aPhoneNumberAloneIsKeptAsANote() {
        val text = "The landlord's number is 98123 45678"
        val m = DetailDrafts.draft(text, listOf(found(text, "98123 45678", DetailKind.Phone)), now).single() as VoiceIntent.Remember
        assertEquals("phone number", m.subject)
    }

    @Test fun aPastTimeAloneOrNothingFoundDraftsNothing() {
        val text = "It rained yesterday"
        assertTrue(DetailDrafts.draft(text, listOf(found(text, "yesterday", DetailKind.DateTime, millis = now - day)), now).isEmpty())
        assertTrue(DetailDrafts.draft(text, emptyList(), now).isEmpty())
    }
}
