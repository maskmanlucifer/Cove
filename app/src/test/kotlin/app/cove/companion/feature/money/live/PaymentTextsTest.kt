package app.cove.companion.feature.money.live

import app.cove.companion.data.sms.AddedPayment
import app.cove.companion.feature.permissions.PermissionHealth
import app.cove.companion.feature.permissions.PermissionIssue
import app.cove.companion.feature.permissions.PermissionNeeds
import app.cove.companion.feature.permissions.PermissionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentTextsTest {
    @Test fun addedTitleShowsTheCategory() {
        assertEquals("Added ₹8 · Food", PaymentTexts.added(AddedPayment("e", 800, "spent", "Pluxee wallet", "Food")).title)
        assertEquals("Added ₹8", PaymentTexts.added(AddedPayment("e", 800, "spent", "Pluxee wallet", null)).title)
        assertEquals("Added ₹150 received", PaymentTexts.added(AddedPayment("e", 15000, "received", "Refund Co", null)).title)
        assertEquals("Pluxee wallet", PaymentTexts.added(AddedPayment("e", 800, "spent", "Pluxee wallet", "Food")).text)
    }

    @Test fun summaryCountsTheAddedPayments() {
        assertEquals("1 payment added", PaymentTexts.summary(1).title)
        assertEquals("3 payments added", PaymentTexts.summary(3).title)
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
