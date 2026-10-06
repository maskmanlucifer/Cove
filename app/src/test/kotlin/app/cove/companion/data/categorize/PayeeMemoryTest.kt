package app.cove.companion.data.categorize

import app.cove.companion.data.local.entity.CategoryMemoryEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.PayeeMemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayeeMemoryTest {
    private fun cat(name: String, sort: Int = 0) = ExpenseCategoryEntity(id = "id-${name.lowercase()}", name = name, kind = "spending", sort = sort)
    private val cats = listOf(cat("Food", 0), cat("Health", 1), cat("Home", 2), cat("Other", 3))
    private val key = "vpa:paytmqr2810050501@paytm"

    private fun payee(category: String, label: String? = null, count: Int = 1, name: String = "Gym Shop") =
        PayeeMemoryEntity(key, "id-$category", label, name, count, 1)

    private fun exp(id: String, category: String?, note: String = "Payment", at: Long = 1, kind: String = "spent", payeeKey: String? = key) =
        ExpenseEntity(id, 10000, kind, category?.let { "id-$it" }, note, "UPI", at, payeeKey = payeeKey)

    // --- recall priority: payee > word > built-in -------------------------------------------------------------

    @Test fun payeeBeatsWordMemoryKeywordsAndBuiltIns() {
        val words = mapOf("zomato" to CategoryMemoryEntity("zomato", "id-home", 5))
        val food = cats.map { if (it.id == "id-food") it.copy(keywords = "zomato") else it }
        val s = ExpenseCategorizer.suggest("Zomato", food, words, payee("health"))
        assertEquals("id-health", s.categoryId)
        assertEquals(Reason.Payee, s.reason)
    }

    @Test fun withoutPayeeWordMemoryBeatsBuiltIn() {
        val words = mapOf("zomato" to CategoryMemoryEntity("zomato", "id-home", 1))
        assertEquals(Reason.Learned, ExpenseCategorizer.suggest("Zomato", cats, words).reason)
        assertEquals("id-home", ExpenseCategorizer.suggest("Zomato", cats, words).categoryId)
        assertEquals(Reason.BuiltIn, ExpenseCategorizer.suggest("Zomato", cats).reason)
    }

    @Test fun payeeConfidenceGrowsWithCount() {
        assertEquals(0.87f, ExpenseCategorizer.suggest("Payment", cats, payee = payee("health", count = 1)).confidence, 0.001f)
        assertEquals(0.95f, ExpenseCategorizer.suggest("Payment", cats, payee = payee("health", count = 9)).confidence, 0.001f)
    }

    @Test fun staleOrUnusablePayeeMemoryIsIgnored() {
        assertEquals(Suggestion.NONE, ExpenseCategorizer.suggest("Payment", cats, payee = payee("health").copy(deletedAt = 5)))
        assertEquals(Suggestion.NONE, ExpenseCategorizer.suggest("Payment", cats, payee = payee("health", count = 0)))
        assertEquals(Suggestion.NONE, ExpenseCategorizer.suggest("Payment", cats, payee = payee("gone")))
    }

    @Test fun genericFallbackNeverMatchesWordsEvenWhenPolluted() {
        val polluted = mapOf("payment" to CategoryMemoryEntity("payment", "id-home", 9))
        assertEquals(Suggestion.NONE, ExpenseCategorizer.suggest("Payment", cats, polluted))
        assertEquals(Suggestion.NONE, ExpenseCategorizer.suggest("Money received", cats, polluted))
        // "Payment to Raju" still has a real word, but the generic word itself no longer counts.
        assertEquals(Suggestion.NONE, ExpenseCategorizer.suggest("Payment to Raju", cats, polluted))
        val real = mapOf("raju" to CategoryMemoryEntity("raju", "id-food", 1)) + polluted
        assertEquals("id-food", ExpenseCategorizer.suggest("Payment to Raju", cats, real).categoryId)
    }

    // --- teaching rules --------------------------------------------------------------------------------------------

    @Test fun firstTeachStoresCategoryAndLabel() {
        val m = PayeeLearning.learn(null, key, "id-health", "Gym", "Paytmqr", 100)
        assertEquals(PayeeMemoryEntity(key, "id-health", "Gym", "Paytmqr", 1, 100), m)
    }

    @Test fun agreeingConfirmationsRaiseCountAndLabelFollowsLatest() {
        var m = PayeeLearning.learn(null, key, "id-health", "Gym", "Gym Shop", 1)
        m = PayeeLearning.learn(m, key, "id-health", "Gym", "Gym Shop", 2)
        assertEquals(2, m.count)
        m = PayeeLearning.learn(m, key, "id-health", "Fitness", "Gym Shop", 3)
        assertEquals("Fitness", m.label)
        m = PayeeLearning.learn(m, key, "id-health", null, "Gym Shop", 4)
        assertNull("typing the generated name again clears the label", m.label)
        assertEquals(4, m.count)
        repeat(40) { m = PayeeLearning.learn(m, key, "id-health", null, "Gym Shop", 5) }
        assertEquals(PayeeLearning.MAX_COUNT, m.count)
    }

    @Test fun categoryOnlyConfirmationKeepsTheStoredLabel() {
        val m = PayeeLearning.learn(payee("health", "Gym", 1), key, "id-health", null, "Gym Shop", 9, keepLabel = true)
        assertEquals("Gym", m.label)
        assertEquals(2, m.count)
    }

    @Test fun changingMindReplacesAtCountOneAndWeakensOtherwise() {
        val one = PayeeLearning.learn(payee("health", "Gym", 1), key, "id-food", "Lunch", "Gym Shop", 9)
        assertEquals(PayeeMemoryEntity(key, "id-food", "Lunch", "Gym Shop", 1, 9), one)
        val three = PayeeLearning.learn(payee("health", "Gym", 3), key, "id-food", "Lunch", "Gym Shop", 9)
        assertEquals("id-health", three.categoryId)
        assertEquals("Gym", three.label)
        assertEquals(2, three.count)
        var m = three
        m = PayeeLearning.learn(m, key, "id-food", "Lunch", "Gym Shop", 10)
        m = PayeeLearning.learn(m, key, "id-food", "Lunch", "Gym Shop", 11)
        assertEquals("id-food", m.categoryId)
    }

    @Test fun deletedRowIsTreatedAsNothing() {
        val m = PayeeLearning.learn(payee("health").copy(deletedAt = 3, count = 0), key, "id-food", null, "X", 9)
        assertEquals(1, m.count)
        assertEquals("id-food", m.categoryId)
    }

    @Test fun unchangedGeneratedNoteStoresNoLabel() {
        assertNull(PayeeLearning.labelFor("Zomato", "Zomato"))
        assertNull(PayeeLearning.labelFor("  zomato ", "Zomato"))
        assertNull(PayeeLearning.labelFor("", "Zomato"))
        assertNull(PayeeLearning.labelFor("   ", "Payment"))
        assertEquals("Gym", PayeeLearning.labelFor(" Gym ", "Payment"))
        assertEquals("Zomato Gold", PayeeLearning.labelFor("Zomato Gold", "Zomato"))
    }

    @Test fun forgottenRowIsSoftDeletedSoSyncCarriesIt() {
        val f = PayeeLearning.forgotten(payee("health", "Gym", 4), 77)
        assertEquals(0, f.count)
        assertEquals(77L, f.deletedAt)
        assertEquals(77L, f.updatedAt)
    }

    // --- retro-tag candidates ---------------------------------------------------------------------------------------

    @Test fun retroSelectsOnlyOtherEarlierPaymentsThatDiffer() {
        val others = listOf(
            exp("new", "health", "Gym", at = 9),
            exp("same", "health", "Gym", at = 8),
            exp("otherCat", "food", "Payment", at = 7),
            exp("none", null, "Payment", at = 6),
            exp("sameCatOtherNote", "health", "Payment", at = 5),
            exp("received", "health", "Payment", at = 4, kind = "received"),
            exp("gone", "food", "Payment", at = 3).copy(deletedAt = 1),
        )
        val changes = PayeeLogic.retroChanges(others, setOf("new"), "id-health", "Gym")
        assertEquals(listOf("otherCat", "none", "sameCatOtherNote"), changes.map { it.expenseId })
        assertEquals("Gym", changes.first().toNote)
        assertEquals("Payment", changes.first().fromNote)
        assertEquals("id-food", changes.first().fromCategoryId)
    }

    @Test fun retroWithoutLabelKeepsNotesAndSkipsAlreadyFiled() {
        val others = listOf(exp("a", "food", "Payment"), exp("b", "health", "Whatever"))
        val changes = PayeeLogic.retroChanges(others, emptySet(), "id-health", null)
        assertEquals(listOf("a"), changes.map { it.expenseId })
        assertEquals("Payment", changes.single().toNote)
    }

    @Test fun offerTextIsCalmAndCountsCorrectly() {
        assertEquals("Tag 3 earlier payments to this payee too?", PayeeLogic.offerText(3, 1))
        assertEquals("Tag 1 earlier payment to this payee too?", PayeeLogic.offerText(1, 1))
        assertEquals("Tag 5 earlier payments to these payees too?", PayeeLogic.offerText(5, 2))
    }

    // --- learn from my past payments ---------------------------------------------------------------------------------

    @Test fun proposalsNeedTwoConsistentFilingsAndNoExistingMemory() {
        val a = "vpa:a@ybl"; val b = "vpa:b@ybl"; val c = "vpa:c@ybl"; val d = "vpa:d@ybl"; val e = "vpa:e@ybl"
        val list = listOf(
            exp("a1", "food", "Tea", payeeKey = a), exp("a2", "food", "Tea", payeeKey = a), exp("a3", "food", "Chai", payeeKey = a),
            exp("b1", "food", payeeKey = b),
            exp("c1", "food", payeeKey = c), exp("c2", "health", payeeKey = c),
            exp("d1", "health", payeeKey = d), exp("d2", "health", payeeKey = d), exp("d3", "food", payeeKey = d),
            exp("e1", "other", payeeKey = e), exp("e2", "other", payeeKey = e),
            exp("f1", "food", payeeKey = null), exp("f2", "food", payeeKey = null),
            exp("g1", "food", payeeKey = "vpa:g@ybl"), exp("g2", "food", payeeKey = "vpa:g@ybl"),
            exp("h1", "food", payeeKey = "vpa:h@ybl", kind = "received"), exp("h2", "food", payeeKey = "vpa:h@ybl", kind = "received"),
        )
        val out = PayeeLogic.pastProposals(list, setOf("vpa:g@ybl"), cats)
        assertEquals(listOf("vpa:a@ybl", "vpa:d@ybl"), out.map { it.payeeKey })
        assertEquals(PastProposal(a, "id-food", "Tea", 3), out[0])
        assertEquals("id-health", out[1].categoryId)
    }

    @Test fun tiedCategoriesAreNotProposed() {
        val list = listOf(exp("1", "food"), exp("2", "food"), exp("3", "health"), exp("4", "health"))
        assertTrue(PayeeLogic.pastProposals(list, emptySet(), cats).isEmpty())
    }
}
