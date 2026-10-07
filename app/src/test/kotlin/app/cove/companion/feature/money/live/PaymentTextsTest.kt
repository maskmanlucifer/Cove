package app.cove.companion.feature.money.live

import app.cove.companion.data.sms.AddedPayment
import app.cove.companion.data.sms.PendingPayment
import app.cove.companion.feature.permissions.PermissionHealth
import app.cove.companion.feature.permissions.PermissionIssue
import app.cove.companion.feature.permissions.PermissionNeeds
import app.cove.companion.feature.permissions.PermissionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentTextsTest {
    @Test fun askTitleNamesAmountDirectionAndWhere() {
        assertEquals("₹8 spent · Pluxee wallet", PaymentTexts.ask(PendingPayment("k", 800, "spent", "Pluxee wallet", null)).title)
        assertEquals("₹150 received · Refund Co", PaymentTexts.ask(PendingPayment("k", 15000, "received", "Refund Co", null)).title)
        assertEquals("₹1,250.50 spent · Cafe", PaymentTexts.ask(PendingPayment("k", 125050, "spent", "Cafe", null)).title)
    }

    @Test fun askTextExplainsPossibleDuplicates() {
        val p = PendingPayment("k", 34000, "spent", "Lunch", "Possible duplicate: you already added Lunch ₹340")
        assertEquals("Possible duplicate: you already added Lunch ₹340", PaymentTexts.ask(p).text)
        assertEquals("Add it to Money?", PaymentTexts.ask(p.copy(duplicateLine = null)).text)
    }

    @Test fun addedTitleShowsTheCategory() {
        assertEquals("Added ₹8 · Food", PaymentTexts.added(AddedPayment("e", 800, "spent", "Pluxee wallet", "Food")).title)
        assertEquals("Added ₹8", PaymentTexts.added(AddedPayment("e", 800, "spent", "Pluxee wallet", null)).title)
        assertEquals("Added ₹150 received", PaymentTexts.added(AddedPayment("e", 15000, "received", "Refund Co", null)).title)
        assertEquals("Pluxee wallet", PaymentTexts.added(AddedPayment("e", 800, "spent", "Pluxee wallet", "Food")).text)
    }

    @Test fun summaryGroupsPendingAndAdded() {
        assertEquals("2 new payments", PaymentTexts.summary(2, 0).title)
        assertEquals("2 to look at", PaymentTexts.summary(2, 0).text)
        assertEquals("3 new payments", PaymentTexts.summary(2, 1).title)
        assertEquals("Added 1 · 2 to look at", PaymentTexts.summary(2, 1).text)
        assertEquals("Added 3", PaymentTexts.summary(0, 3).text)
        assertEquals("1 new payment", PaymentTexts.summary(0, 1).title)
    }

    @Test fun moneyRowIsPluralAware() {
        assertEquals("2 new payments found in your messages · Review", PaymentTexts.moneyRow(2))
        assertEquals("1 new payment found in your messages · Review", PaymentTexts.moneyRow(1))
    }

    @Test fun privacyCopyMakesTheFourPromises() {
        val t = PaymentTexts.PRIVACY
        assertTrue(t.contains("on this phone only"))
        assertTrue(t.contains("amount, merchant and category"))
        for (word in listOf("stored", "uploaded", "synced", "sent to AI")) assertTrue(word, t.contains(word))
        assertTrue(PaymentTexts.GUIDE_BODY.contains("Allow restricted settings"))
        assertTrue(PaymentTexts.GUIDE_BODY.contains("Receive text messages") && PaymentTexts.GUIDE_BODY.contains("Read text messages"))
    }

    @Test fun lockScreenHidesTheAmount() {
        assertTrue(!PaymentTexts.HIDDEN.contains("₹"))
    }

    @Test fun messagesPermissionGapIsOnlyReportedWhenCaptureIsOn() {
        val off = PermissionSnapshot(true, true, true, true, true, true, messages = false)
        assertTrue(PermissionHealth.missing(off, PermissionNeeds()).isEmpty())
        assertEquals(listOf(PermissionIssue.Messages), PermissionHealth.missing(off, PermissionNeeds(messages = true)))
        assertTrue(PermissionHealth.missing(off.copy(messages = true), PermissionNeeds(messages = true)).isEmpty())
        val g = PermissionHealth.guide(PermissionIssue.Messages)
        assertEquals("Open settings", g.action)
    }
}
