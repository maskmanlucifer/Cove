package app.cove.companion.data.sms

import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.PayeeMemoryEntity
import app.cove.companion.data.local.entity.SmsPendingEntity
import java.io.File
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureDecisionTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val t = 1_790_000_000_000L
    private fun match(exact: Boolean, at: Long = t) = ExistingMatch("e1", "Lunch", 34_000, at, exact)

    @Test fun newPaymentsAreAddedRepeatsSkippedAndDoubtsChecked() {
        assertEquals(CaptureAction.Add, CaptureDecision.decide(null))
        assertEquals(CaptureAction.Skip, CaptureDecision.decide(match(exact = true)))
        assertEquals(CaptureAction.Check, CaptureDecision.decide(match(exact = false)))
    }

    @Test fun withoutAnyCloudModelTheSameDayCountsAsTheSamePayment() {
        assertEquals(CaptureAction.Skip, CaptureDecision.withoutAi(t + 20 * 60_000, match(false), zone))
        assertEquals(CaptureAction.Add, CaptureDecision.withoutAi(t + 30 * 3_600_000L, match(false), zone))
    }

    @Test fun cursorFollowsTheNewestMessageButStopsBeforeOneStillWaiting() {
        assertEquals("nothing read keeps it", 100L, CaptureCursor.next(100, null, null))
        assertEquals(500L, CaptureCursor.next(100, 500, null))
        assertEquals("never moves back", 100L, CaptureCursor.next(100, 50, null))
        assertEquals("stays before the oldest doubt", 299L, CaptureCursor.next(100, 500, 300))
        assertEquals(100L, CaptureCursor.next(100, 500, 100))
    }

    @Test fun theOldAskModeBecomesOn() {
        assertEquals(CaptureMode.Auto, CaptureMode.of("ask"))
        assertEquals(listOf(CaptureMode.Off, CaptureMode.Auto), CaptureMode.entries)
    }

    @Test fun modeKeysRoundTripAndDefaultToOff() {
        CaptureMode.entries.forEach { assertEquals(it, CaptureMode.of(it.key)) }
        assertEquals(CaptureMode.Off, CaptureMode.of(null))
        assertEquals(CaptureMode.Off, CaptureMode.of("loud"))
        assertTrue(CaptureMode.entries.all { it.blurb.endsWith(".") })
    }

    @Test fun catchUpRunsAtMostOnceEveryTenMinutes() {
        val t = 1_000_000_000_000L
        assertTrue("never ran", CatchUpThrottle.due(0, t))
        assertFalse(CatchUpThrottle.due(t, t + 9 * 60_000))
        assertFalse(CatchUpThrottle.due(t, t))
        assertTrue(CatchUpThrottle.due(t, t + 10 * 60_000))
        assertTrue("clock moved back", CatchUpThrottle.due(t, t - 1))
        assertEquals(10 * 60_000L, CatchUpThrottle.INTERVAL_MS)
    }

    private fun parsed(sender: String?, body: String): ParsedSms =
        (SmsTransactionParser.parse(sender, body, 1_790_000_000_000L, ZoneId.of("Asia/Kolkata")) as ParseResult.Accepted).tx

    private val cats = listOf(
        ExpenseCategoryEntity("food", "Food", kind = "spending", sort = 0),
        ExpenseCategoryEntity("fun", "Fun", kind = "spending", sort = 1),
        ExpenseCategoryEntity("other", "Other", kind = "spending", sort = 2),
    )

    @Test fun pluxeePaymentsAreSuggestedAsFood() {
        val tx = parsed("VM-PLUXEE", "Rs 8 spent from Pluxee wallet")
        assertEquals("food", CaptureSuggestion.of(tx, cats, emptyMap(), emptyMap()).categoryId)
    }

    @Test fun payeeMemoryBeatsTheBuiltInAndBringsItsLabel() {
        val tx = parsed("VM-PLUXEE", "Rs 8 spent from Pluxee wallet")
        val memory = mapOf(tx.payeeKey!! to PayeeMemoryEntity(tx.payeeKey!!, "fun", "Games", "Pluxee wallet", 2, 1L))
        val s = CaptureSuggestion.of(tx, cats, emptyMap(), memory)
        assertEquals("fun", s.categoryId)
        assertEquals("Games", s.label)
    }

    @Test fun moneyReceivedHasNoCategory() {
        val tx = parsed("VM-PLUXEE", "Refund of Rs 45.00 credited to your Pluxee wallet from BIG BAZAAR")
        assertNull(CaptureSuggestion.of(tx, cats, emptyMap(), emptyMap()).categoryId)
    }

    @Test fun unknownMerchantFallsBackToOther() {
        val tx = parsed("VM-PLUXEE", "Rs. 90 spent from your Pluxee wallet at ZXQV")
        assertEquals("food", CaptureSuggestion.of(tx, cats, emptyMap(), emptyMap()).categoryId)
        val unknown = parsed("AX-HDFCBK", "Rs.450.00 debited from A/c XX1234 on 05-10-26 to VPA zxqvk@okaxis. UPI Ref No 123456789012")
        assertEquals("other", CaptureSuggestion.of(unknown, cats, emptyMap(), emptyMap()).categoryId)
    }

    @Test fun aRowLeftByTheOldAskModeTurnsBackIntoItsPayment() {
        val tx = parsed("VM-PLUXEE", "Refund of Rs 45.00 credited to your Pluxee wallet from BIG BAZAAR")
        val row = SmsPendingEntity(
            key = "msg:aa", messageKeys = "msg:aa,msg:bb", amountPaise = tx.amountPaise, direction = "credit", merchant = tx.merchant,
            at = tx.at, dateFromText = tx.dateFromText, last4 = tx.last4, paidWith = tx.paidWith, ref = tx.ref, bank = tx.bank,
            confidence = tx.confidence, payeeKey = tx.payeeKey, matchExpenseId = null, matchNote = null, matchAmountPaise = null, createdAt = 1L,
        )
        val back = row.toCandidate()
        assertEquals(tx, back.tx)
        assertEquals(listOf("msg:aa", "msg:bb"), back.messages.map { it.key })
        assertEquals("msg:aa", back.id)
    }

    @Test fun manifestReceiverIsGuardedBySystemOnlyPermission() {
        val m = File("src/main/AndroidManifest.xml").takeIf { it.exists() }?.readText() ?: File("app/src/main/AndroidManifest.xml").readText()
        assertTrue(m.contains("android.permission.RECEIVE_SMS"))
        val receiver = Regex("""<receiver[^>]*SmsReceiver[^>]*>""").find(m)?.value.orEmpty()
        assertTrue(receiver.contains("android:permission=\"android.permission.BROADCAST_SMS\""))
        assertTrue(receiver.contains("android:exported=\"true\""))
        assertTrue(m.contains("android.provider.Telephony.SMS_RECEIVED"))
        assertTrue(Regex("""<receiver[^>]*PaymentActionReceiver[^>]*exported="false"""").containsMatchIn(m))
    }
}
