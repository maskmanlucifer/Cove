package app.cove.companion.data.sms

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsDedupeTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun t(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private val min = 60_000L

    private fun sig(paise: Long = 45000, dir: Direction = Direction.Debit, last4: String? = null, merchant: String? = null, ref: String? = null, at: Long = t(5, 10, 0)) =
        TxSig(paise, dir, last4, merchant, ref, at)

    private fun tx(sig: TxSig, conf: Float = 0.7f, dateFromText: Boolean = false) =
        ParsedSms(sig.amountPaise, sig.direction, sig.merchant, sig.at, dateFromText, sig.last4, "UPI", sig.ref, null, conf)

    private fun cand(key: String, sig: TxSig, conf: Float = 0.7f) = Candidate(tx(sig, conf), listOf(MessageId(key, null)))

    @Test fun messageKeyIsStableAndNormalised() {
        val a = SmsDedupe.messageKey("AX-HDFCBK", "Rs.450 debited  from A/c XX1234")
        assertEquals(a, SmsDedupe.messageKey(" ax-hdfcbk", "rs.450 DEBITED from a/c xx1234\n"))
        assertNotEquals(a, SmsDedupe.messageKey("AX-HDFCBK", "Rs.451 debited from A/c XX1234"))
        assertNotEquals(a, SmsDedupe.messageKey("VM-ICICIB", "Rs.450 debited from A/c XX1234"))
        assertTrue(a.startsWith("msg:") && a.length == 4 + 64)
        assertFalse("hash must not leak text", a.contains("debited"))
    }

    @Test fun sameTransactionTable() {
        val base = sig(ref = "R1", last4 = "1234", merchant = "Zomato")
        val cases = listOf(
            "same ref any time" to (base to base.copy(at = base.at + 5 * 60 * 60_000L, merchant = null, last4 = null) to true),
            "different ref same everything else" to (base to base.copy(ref = "R2") to false),
            "different amount" to (base to base.copy(amountPaise = 45100) to false),
            "different direction" to (base to base.copy(direction = Direction.Credit) to false),
            "no refs, last4 and time" to (sig(last4 = "1234") to sig(last4 = "1234", at = t(5, 10, 8)) to true),
            "no refs, merchant fuzzy" to (sig(merchant = "Amazon Pay") to sig(merchant = "AMAZON", at = t(5, 10, 3)) to true),
            "no refs, just over 10 min" to (sig(last4 = "1234") to sig(last4 = "1234", at = t(5, 10, 11)) to false),
            "no refs, exactly 10 min" to (sig(last4 = "1234") to sig(last4 = "1234", at = t(5, 10, 10)) to true),
            "different last4" to (sig(last4 = "1234", merchant = "Zomato") to sig(last4 = "9999", merchant = "Zomato") to false),
            "different merchants no digits" to (sig(merchant = "Zomato") to sig(merchant = "Swiggy") to false),
            "digits agree merchants differ" to (sig(last4 = "1234", merchant = "Zomato Ltd") to sig(last4 = "1234", merchant = "Rahul") to true),
            "no evidence at all" to (sig() to sig() to false),
            "one side lacks fields, other agrees on merchant" to (sig(merchant = "Zomato", last4 = "1234") to sig(merchant = "Zomato") to true),
            "ref on one side only, merchants agree" to (sig(ref = "R1", merchant = "Zomato") to sig(merchant = "Zomato", at = t(5, 10, 2)) to true),
        )
        for ((name, v) in cases) {
            val (pair, want) = v
            assertEquals(name, want, SmsDedupe.sameTransaction(pair.first, pair.second))
            assertEquals("$name (symmetric)", want, SmsDedupe.sameTransaction(pair.second, pair.first))
        }
    }

    @Test fun collapseKeepsRicherAndFillsGaps() {
        val bank = cand("bank", sig(ref = "628374650192", last4 = "1234", merchant = null, at = t(5, 10, 0)), conf = 0.75f)
        val app = cand("app", sig(ref = "628374650192", last4 = null, merchant = "Zomato", at = t(5, 10, 1)), conf = 0.6f)
        val out = SmsDedupe.collapse(listOf(bank, app))
        assertEquals(1, out.size)
        val c = out.single()
        assertEquals("Zomato", c.tx.merchant)
        assertEquals("1234", c.tx.last4)
        assertEquals(setOf("bank", "app"), c.messages.map { it.key }.toSet())
    }

    @Test fun collapseKeepsSeparatePaymentsAndIsOrderIndependent() {
        val a = cand("a", sig(ref = "R1", merchant = "Zomato", at = t(5, 10, 0)))
        val b = cand("b", sig(ref = "R2", merchant = "Zomato", at = t(5, 10, 3)))
        val c = cand("c", sig(paise = 100, merchant = "Zomato", at = t(5, 10, 3)))
        val one = SmsDedupe.collapse(listOf(a, b, c))
        val two = SmsDedupe.collapse(listOf(c, b, a))
        assertEquals(3, one.size)
        assertEquals(one.map { it.id }.toSet(), two.map { it.id }.toSet())
    }

    @Test fun collapseHandlesThreeMessagesOfOnePayment() {
        val x = cand("x", sig(merchant = "Zomato", at = t(5, 10, 0)))
        val y = cand("y", sig(merchant = "Zomato", last4 = "1234", at = t(5, 10, 4)), conf = 0.8f)
        val z = cand("z", sig(ref = "R9", merchant = "Zomato", at = t(5, 10, 8)))
        val out = SmsDedupe.collapse(listOf(x, y, z))
        assertEquals(1, out.size)
        assertEquals(3, out.single().messages.size)
        assertEquals("R9", out.single().tx.ref)
    }

    @Test fun collapseIsIdempotent() {
        val list = listOf(cand("a", sig(ref = "R1")), cand("b", sig(ref = "R1")), cand("c", sig(paise = 5000, ref = "R3")))
        val once = SmsDedupe.collapse(list)
        val twice = SmsDedupe.collapse(once)
        assertEquals(once.map { it.id to it.messages.size }, twice.map { it.id to it.messages.size })
    }

    @Test fun logCoversByRefOrByPayment() {
        val logged = listOf(
            LoggedTx(sig(ref = "R1", at = t(1, 9, 0)), SmsImportOutcome.IMPORTED),
            LoggedTx(sig(paise = 9900, last4 = "1234", at = t(5, 9, 58)), SmsImportOutcome.SKIPPED),
        )
        val byRef = cand("r", sig(ref = "R1", at = t(5, 10, 0)))
        val byPayment = cand("p", sig(paise = 9900, last4 = "1234", at = t(5, 10, 0)))
        val fresh = cand("f", sig(paise = 9900, last4 = "5555", at = t(5, 10, 0)))
        assertEquals(setOf("r", "p"), SmsDedupe.coveredByLog(listOf(byRef, byPayment, fresh), logged))
    }

    private fun existing(id: String, paise: Long, at: Long, note: String = "", kind: String = "spent", ref: String? = null) =
        ExistingExpense(id, paise, kind, at, note, ref)

    @Test fun existingExpenseTable() {
        data class Row(val name: String, val cand: TxSig, val existing: List<ExistingExpense>, val match: String?, val exact: Boolean = false)
        val c = sig(paise = 34000, merchant = "Zomato", at = t(5, 13, 0))
        val rows = listOf(
            Row("manual same day similar merchant is exact", c, listOf(existing("e1", 34000, t(5, 12, 50), "Zomato lunch")), "e1", true),
            Row("manual same amount different note is possible only", c, listOf(existing("e1", 34000, t(5, 12, 50), "Lunch")), "e1", false),
            Row("no note is possible", c, listOf(existing("e1", 34000, t(5, 9, 0), "")), "e1", false),
            Row("35 hours earlier different day is possible", c, listOf(existing("e1", 34000, t(5, 13, 0) - 35 * 60 * min, "Zomato")), "e1", false),
            Row("37 hours away is not a match", c, listOf(existing("e1", 34000, t(5, 13, 0) - 37 * 60 * min, "Zomato")), null),
            Row("different amount", c, listOf(existing("e1", 34100, t(5, 13, 0), "Zomato")), null),
            Row("received vs debit", c, listOf(existing("e1", 34000, t(5, 13, 0), "Zomato", kind = "received")), null),
            Row("same ref is exact at any distance", c.copy(ref = "R1"), listOf(existing("e1", 34000, t(1, 13, 0), "x", ref = "R1")), "e1", true),
            Row("closest of two wins", c, listOf(existing("far", 34000, t(4, 13, 0), "Lunch"), existing("near", 34000, t(5, 11, 0), "Lunch")), "near", false),
            Row("exact beats closer", c, listOf(existing("close", 34000, t(5, 12, 59), "Lunch"), existing("exact", 34000, t(5, 8, 0), "Zomato")), "exact", true),
        )
        for (r in rows) {
            val m = SmsDedupe.matchExisting(listOf(Candidate(tx(r.cand), listOf(MessageId("k", null)))), r.existing, zone)["k"]
            assertEquals(r.name, r.match, m?.expenseId)
            if (m != null) assertEquals(r.name + " exact", r.exact, m.exact)
        }
    }

    @Test fun oneExistingExpenseIsUsedOnce() {
        val a = Candidate(tx(sig(paise = 34000, merchant = "Zomato", at = t(5, 12, 0))), listOf(MessageId("a", null)))
        val b = Candidate(tx(sig(paise = 34000, merchant = "Swiggy", at = t(5, 13, 0))), listOf(MessageId("b", null)))
        val out = SmsDedupe.matchExisting(listOf(a, b), listOf(existing("e1", 34000, t(5, 12, 5), "Lunch")), zone)
        assertEquals(1, out.size)
        assertEquals("a", out.keys.single())
    }

    @Test fun similarTextRules() {
        assertTrue(SmsDedupe.similarText("Amazon Pay", "amazon"))
        assertTrue(SmsDedupe.similarText("Zomato lunch", "Zomato"))
        assertTrue(SmsDedupe.similarText("Gifts", "Gift shop"))
        assertFalse(SmsDedupe.similarText("Lunch", "Zomato"))
        assertFalse(SmsDedupe.similarText(null, "Zomato"))
        assertFalse(SmsDedupe.similarText("", ""))
    }
}
