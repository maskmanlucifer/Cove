package app.cove.companion.data.categorize

import app.cove.companion.ai.model.AiError
import app.cove.companion.ai.model.AiResult
import app.cove.companion.ai.model.CategorySuggestion
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.repo.CategoryChange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewLogicTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val cats = listOf("Food", "Fun", "Transport", "Other").mapIndexed { i, n -> ExpenseCategoryEntity("id-${n.lowercase()}", n, sort = i) }

    private fun exp(id: String, note: String, cat: String?, daysAgo: Long, kind: String = "spent", deleted: Long? = null) =
        ExpenseEntity(id, 10_000, kind, cat, note, spentAt = now - daysAgo * day, deletedAt = deleted)

    @Test fun unfiledMeansOtherOrNoCategoryWithinSixtyDays() {
        val all = listOf(
            exp("a", "x", "id-other", 1), exp("b", "x", null, 10), exp("c", "x", "id-food", 1),
            exp("d", "x", "id-other", 61), exp("e", "x", "id-other", 59), exp("f", "x", "gone", 2),
            exp("g", "x", "id-other", 1, kind = "received"), exp("h", "x", "id-other", 1, deleted = 5),
        )
        assertEquals(listOf("a", "b", "e", "f").sorted(), ReviewLogic.unfiled(all, cats, now).map { it.id }.sorted())
        assertEquals(listOf("a", "f", "b", "e"), ReviewLogic.unfiled(all, cats, now).map { it.id })
    }

    @Test fun bannerNeedsThree() {
        assertNull(ReviewLogic.bannerText(2))
        assertEquals("3 in Other · Review", ReviewLogic.bannerText(3))
        assertEquals("12 in Other · Review", ReviewLogic.bannerText(12))
    }

    @Test fun rowsCarryOfflineSuggestions() {
        val rows = ReviewLogic.rows(listOf(exp("a", "Swiggy", "id-other", 1), exp("b", "zzz", "id-other", 1)), cats, emptyMap())
        assertEquals("id-food" to Reason.BuiltIn, rows[0].suggestion.categoryId to rows[0].suggestion.reason)
        assertNull(rows[1].targetId)
    }

    @Test fun crossCheckCandidatesAreFiledWithNotesWithinThirtyDays() {
        val all = listOf(
            exp("a", "uber", "id-food", 3), exp("b", "uber", "id-other", 3), exp("c", "uber", "id-food", 31),
            exp("d", "", "id-food", 3), exp("e", "450", "id-food", 3), exp("f", "tea", "id-fun", 29),
        )
        assertEquals(listOf("a", "f"), ReviewLogic.crossCheckCandidates(all, cats, now).map { it.id })
    }

    @Test fun disagreementsListOnlyDifferences() {
        val c = listOf(exp("a", "uber", "id-food", 1), exp("b", "tea", "id-food", 1), exp("c", "x", "id-food", 1))
        val rows = ReviewLogic.disagreements(c, mapOf("a" to "Transport", "b" to "food", "c" to "Unknown"), cats)
        assertEquals(listOf("a"), rows.map { it.expense.id })
        assertEquals("id-transport" to Reason.Ai, rows[0].suggestion.categoryId to rows[0].suggestion.reason)
    }

    @Test fun aiNamesExcludeOther() {
        assertEquals(listOf("Food", "Fun", "Transport"), ReviewLogic.aiCategoryNames(cats))
    }

    @Test fun errorTextsAreFriendly() {
        assertEquals(true, ReviewLogic.errorText(AiError.NeedsConfig("API key")).contains("Gemini key"))
        assertEquals(true, ReviewLogic.errorText(AiError.Offline).contains("offline"))
        assertEquals("On-device", ReviewLogic.provenance(Location.Native))
        assertEquals("Cloud", ReviewLogic.provenance(Location.Cloud))
    }

    private fun state(): ReviewState = ReviewState(
        ReviewLogic.rows(
            listOf(exp("a", "Swiggy", "id-other", 1), exp("b", "zzz", null, 1), exp("c", "Netflix", "id-other", 2)),
            cats, emptyMap(),
        ),
    )

    @Test fun acceptAllFilesOnlyRowsWithATargetAndHonoursPicksAndSkips() {
        val s = state().pick("b", "id-transport").skip("c")
        assertEquals(
            listOf(CategoryChange("a", "id-other", "id-food"), CategoryChange("b", null, "id-transport")),
            s.acceptAllChanges(),
        )
        assertEquals("your pick", s.rows[1].reasonLabel)
    }

    @Test fun unresolvedAreRowsWithNoSuggestionAndAiFillsOnlyThose() {
        val s = state()
        assertEquals(listOf("b"), s.unresolved.map { it.expense.id })
        val withAi = s.withAi(mapOf("a" to "id-fun", "b" to "id-transport"))
        assertEquals("id-food", withAi.rows[0].targetId)
        assertEquals("id-transport" to "AI", withAi.rows[1].targetId to withAi.rows[1].reasonLabel)
    }

    @Test fun acceptAllThenUndoRestoresRows() {
        val s = state()
        val changes = s.acceptAllChanges()
        val done = s.filed(changes.map { it.expenseId }.toSet(), changes)
        assertEquals(emptyList<String>(), done.open.map { it.expense.id }.filter { it != "b" })
        assertEquals(changes, done.undo)
        val back = done.undone()
        assertEquals(emptyList<CategoryChange>(), back.undo)
        assertEquals(3, back.open.size)
    }

    @Test fun batchingAsksOncePerFortyAndStopsOnFirstError() = runBlocking {
        val items = List(95) { "e$it" to "note $it" }
        val sizes = mutableListOf<Int>()
        val ok = suggestInBatches(items, listOf("Food")) { notes, _ ->
            sizes += notes.size
            AiResult.Ok(listOf(CategorySuggestion(0, "Food")), ProviderRef("nano", Location.Native))
        }
        assertEquals(listOf(40, 40, 15), sizes)
        assertEquals(setOf("e0", "e40", "e80"), ok.picks.keys)
        assertEquals(Location.Native, ok.location)
        var n = 0
        val failed = suggestInBatches(items, listOf("Food")) { _, _ ->
            if (n++ == 0) AiResult.Ok(listOf(CategorySuggestion(1, "Food")), ProviderRef("g", Location.Cloud)) else AiResult.Failed(AiError.Offline)
        }
        assertEquals(2, n)
        assertEquals(mapOf("e1" to "Food"), failed.picks)
        assertEquals(AiError.Offline, failed.error)
    }

    @Test fun sendsOnlyNotes() = runBlocking {
        var seen: List<String> = emptyList()
        suggestInBatches(listOf("id1" to "Blinkit"), listOf("Food")) { notes, _ -> seen = notes; AiResult.Failed(AiError.Offline) }
        assertEquals(listOf("Blinkit"), seen)
    }
}
