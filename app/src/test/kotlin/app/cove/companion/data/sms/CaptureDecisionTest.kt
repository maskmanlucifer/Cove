package app.cove.companion.data.sms

import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.PayeeMemoryEntity
import java.io.File
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureDecisionTest {
    private fun d(mode: CaptureMode, permission: Boolean = true, found: Boolean = true, duplicate: Boolean = false) =
        CaptureDecision.decide(mode, permission, found, duplicate)

    @Test fun decisionTableCoversEveryCombination() {
        val expected = { mode: CaptureMode, permission: Boolean, found: Boolean, duplicate: Boolean ->
            when {
                mode == CaptureMode.Off || !permission || !found -> CaptureAction.Ignore
                duplicate -> CaptureAction.Ask
                mode == CaptureMode.Ask -> CaptureAction.Ask
                else -> CaptureAction.Add
            }
        }
        var rows = 0
        for (mode in CaptureMode.entries) for (permission in listOf(true, false)) for (found in listOf(true, false)) for (duplicate in listOf(true, false)) {
            assertEquals("$mode permission=$permission found=$found duplicate=$duplicate", expected(mode, permission, found, duplicate), d(mode, permission, found, duplicate))
            rows++
        }
        assertEquals(24, rows)
    }

    @Test fun keyRows() {
        assertEquals(CaptureAction.Ignore, d(CaptureMode.Off))
        assertEquals(CaptureAction.Ask, d(CaptureMode.Ask))
        assertEquals(CaptureAction.Add, d(CaptureMode.Auto))
        assertEquals("a possible duplicate is never added on its own", CaptureAction.Ask, d(CaptureMode.Auto, duplicate = true))
        assertEquals("without permission nothing happens", CaptureAction.Ignore, d(CaptureMode.Auto, permission = false))
        assertEquals("a rejected or already decided message is left alone", CaptureAction.Ignore, d(CaptureMode.Auto, found = false))
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

    @Test fun pendingRowKeepsParsedFieldsAndNothingElse() {
        val tx = parsed("VM-PLUXEE", "Rs. 8.00 spent from your Pluxee wallet at CAFE on 05-10-2026. Bal Rs. 1,250")
        val c = Candidate(tx, listOf(MessageId("msg:aa", null), MessageId("msg:bb", 7)))
        val match = ExistingMatch("e1", "Lunch", 800, tx.at, false)
        val row = ReviewItem(c, match).toPending(42L)
        assertEquals("msg:aa", row.key)
        assertEquals("msg:aa,msg:bb", row.messageKeys)
        assertEquals("Lunch", row.matchNote)
        assertEquals(42L, row.createdAt)
        val back = row.toCandidate()
        assertEquals(tx, back.tx)
        assertEquals(listOf("msg:aa", "msg:bb"), back.messages.map { it.key })
        assertEquals("msg:aa", back.id)
    }

    @Test fun creditsRoundTripAsCredits() {
        val tx = parsed("VM-PLUXEE", "Refund of Rs 45.00 credited to your Pluxee wallet from BIG BAZAAR")
        assertEquals(Direction.Credit, ReviewItem(Candidate(tx, listOf(MessageId("msg:cc", null))), null).toPending(1L).toCandidate().tx.direction)
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
