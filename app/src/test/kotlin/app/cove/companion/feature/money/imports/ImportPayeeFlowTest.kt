package app.cove.companion.feature.money.imports

import app.cove.companion.data.categorize.PayeeLearning
import app.cove.companion.data.categorize.PayeeLogic
import app.cove.companion.data.categorize.Reason
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.PayeeMemoryEntity
import app.cove.companion.data.sms.Candidate
import app.cove.companion.data.sms.ImportDecision
import app.cove.companion.data.sms.MessageId
import app.cove.companion.data.sms.ParseResult
import app.cove.companion.data.sms.ReviewItem
import app.cove.companion.data.sms.SmsDedupe
import app.cove.companion.data.sms.SmsMessage
import app.cove.companion.data.sms.SmsSource
import app.cove.companion.data.sms.SmsTransactionParser
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's story end to end on the pure pieces the screens use: the same QR handle in two months, tagged by hand the
 * first time and pre-tagged the second. A fake inbox feeds the real parser; teaching uses the same rules as the repository.
 */
class ImportPayeeFlowTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun ms(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private class FakeInbox(private val all: List<SmsMessage>) : SmsSource {
        override suspend fun count(since: Long): Int = all.count { it.receivedAt >= since }
        override suspend fun read(since: Long, pageSize: Int, onPage: suspend (List<SmsMessage>) -> Unit) = onPage(all.filter { it.receivedAt >= since })
    }

    private val cats = listOf(
        ExpenseCategoryEntity("id-food", "Food", kind = "spending", sort = 0),
        ExpenseCategoryEntity("id-health", "Health", kind = "spending", sort = 1),
        ExpenseCategoryEntity("id-other", "Other", kind = "spending", sort = 2),
    )

    private fun sms(m: Int, d: Int, amount: String, vpa: String, ref: String) = SmsMessage(
        null, "AX-HDFCBK", "Rs.$amount debited from A/c XX1234 on %02d-%02d-26 to VPA $vpa. UPI Ref No $ref".format(d, m), ms(m, d),
    )

    private val inbox = FakeInbox(
        listOf(
            sms(9, 12, "300.00", "paytmqr2810050501abcd@paytm", "100000000001"),
            sms(9, 14, "45.00", "chaiwala@ybl", "100000000002"),
            sms(10, 3, "300.00", "paytmqr2810050501abcd@paytm", "100000000003"),
            sms(10, 4, "320.00", "paytmqr2810050501wxyz@paytm", "100000000004"),
            sms(10, 6, "45.00", "chaiwala@ybl", "100000000005"),
        ),
    )

    private fun scan(from: Long, to: Long): List<ReviewItem> = runBlocking {
        val out = ArrayList<ReviewItem>()
        inbox.read(from) { page ->
            page.filter { it.receivedAt < to }.forEach { m ->
                val tx = (SmsTransactionParser.parse(m.sender, m.body, m.receivedAt, zone) as ParseResult.Accepted).tx
                out += ReviewItem(Candidate(tx, listOf(MessageId(SmsDedupe.messageKey(m.sender, m.body), null))), null)
            }
        }
        out
    }

    /** What the repository does for one included decision. */
    private fun teach(memory: MutableMap<String, PayeeMemoryEntity>, row: ImportRow, now: Long) {
        val d = ImportDecision(row.item, true, row.kind, row.categoryId, row.suggestedId, row.picked, row.label, row.labelEdited)
        val key = row.item.candidate.tx.payeeKey
        if (!d.teaches || key == null) return
        val label = PayeeLearning.labelFor(row.note, row.generated)
        memory[key] = PayeeLearning.learn(memory[key], key, d.categoryId!!, label, row.generated, now)
    }

    @Test fun sameQrHandleInTwoMonthsIsPreTaggedTheSecondTime() {
        val memory = HashMap<String, PayeeMemoryEntity>()

        val sept = scan(ms(9, 1), ms(10, 1)).map { rowFor(it, cats, emptyMap(), memory) }
        val qr = sept.first { it.item.candidate.tx.payeeKey == "vpa:paytmqr2810050501@paytm" }
        assertEquals("Payment", qr.note)
        assertEquals("id-other", qr.categoryId)
        assertFalse(qr.recalled)
        // The user tags category and a custom label by hand.
        val tagged = qr.copy(categoryId = "id-health", picked = true, label = "Gym", labelEdited = true)
        teach(memory, tagged, 1)
        // The other payee in the same batch was left as it was: nothing is taught.
        teach(memory, sept.first { it !== qr }, 1)
        assertEquals(setOf("vpa:paytmqr2810050501@paytm"), memory.keys)
        assertEquals("Gym", memory.getValue("vpa:paytmqr2810050501@paytm").label)

        val oct = scan(ms(10, 1), ms(11, 1)).map { rowFor(it, cats, emptyMap(), memory) }
        val again = oct.first { it.item.candidate.tx.amountPaise == 30000L }
        assertEquals("id-health", again.categoryId)
        assertEquals("Gym", again.note)
        assertTrue(again.recalled)
        assertEquals(Reason.Payee.label, again.reason)
        assertEquals("Learned from your earlier payment", again.reason)
        // A different printed QR of the same shop is the same payee.
        val sibling = oct.first { it.item.candidate.tx.amountPaise == 32000L }
        assertEquals("id-health", sibling.categoryId)
        assertEquals("Gym", sibling.note)
        // An unknown payee still gets the generic note and Other.
        val chai = oct.first { it.item.candidate.tx.payeeKey == "vpa:chaiwala@ybl" }
        assertEquals("id-other", chai.categoryId)
        assertFalse(chai.recalled)
    }

    @Test fun recalledRowStaysEditable() {
        val memory = mapOf("vpa:paytmqr2810050501@paytm" to PayeeMemoryEntity("vpa:paytmqr2810050501@paytm", "id-health", "Gym", "", 1, 1))
        val row = scan(ms(10, 1), ms(10, 4)).map { rowFor(it, cats, emptyMap(), memory) }.single()
        assertEquals("id-health", row.categoryId)
        val changed = row.copy(categoryId = "id-food", picked = true, label = "Lunch", labelEdited = true)
        val map = HashMap(memory)
        teach(map, changed, 5)
        // One earlier confirmation, one change of mind: the mapping is replaced.
        assertEquals("id-food", map.getValue("vpa:paytmqr2810050501@paytm").categoryId)
        assertEquals("Lunch", map.getValue("vpa:paytmqr2810050501@paytm").label)
    }

    @Test fun unreviewedBulkImportTeachesNothing() {
        val memory = HashMap<String, PayeeMemoryEntity>()
        scan(ms(9, 1), ms(11, 1)).map { rowFor(it, cats, emptyMap(), memory) }.forEach { teach(memory, it, 1) }
        assertTrue(memory.isEmpty())
    }

    @Test fun unchangedGeneratedNameStoresNoLabelSoMerchantNameKeepsBeingUsed() {
        val memory = HashMap<String, PayeeMemoryEntity>()
        val tx = scan(ms(9, 14), ms(9, 15)).map { rowFor(it, cats, emptyMap(), memory) }.single()
        assertEquals("Chaiwala", tx.note)
        teach(memory, tx.copy(categoryId = "id-food", picked = true), 1)
        assertNull(memory.getValue("vpa:chaiwala@ybl").label)
        val next = rowFor(scan(ms(10, 6), ms(10, 7)).single(), cats, emptyMap(), memory)
        assertEquals("Chaiwala", next.note)
        assertEquals("id-food", next.categoryId)
    }

    @Test fun retroTagOffersEarlierPaymentsOfTheSamePayeeOnly() {
        val key = "vpa:paytmqr2810050501@paytm"
        val earlier = listOf(
            ExpenseEntity("e1", 30000, "spent", "id-other", "Payment", "UPI", ms(8, 1), payeeKey = key),
            ExpenseEntity("e2", 30000, "spent", "id-other", "Payment", "UPI", ms(8, 8), payeeKey = key),
            ExpenseEntity("e3", 30000, "spent", "id-health", "Gym", "UPI", ms(8, 15), payeeKey = key),
        )
        val changes = PayeeLogic.retroChanges(earlier, setOf("justImported"), "id-health", "Gym")
        assertEquals(listOf("e2", "e1"), changes.map { it.expenseId })
        assertEquals("Tag 2 earlier payments to this payee too?", PayeeLogic.offerText(changes.size, 1))
    }
}
