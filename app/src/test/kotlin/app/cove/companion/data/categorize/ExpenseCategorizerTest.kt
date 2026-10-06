package app.cove.companion.data.categorize

import app.cove.companion.data.local.entity.CategoryMemoryEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCategorizerTest {
    private fun cat(name: String, keywords: String = "", sort: Int = 0, id: String = "id-${name.lowercase()}", kind: String = "spending") =
        ExpenseCategoryEntity(id, name, kind = kind, keywords = keywords, sort = sort)

    private val defaults = listOf(cat("Food", sort = 0), cat("Home", sort = 1), cat("Transport", sort = 2), cat("Fun", sort = 3), cat("Other", sort = 4))

    private fun id(note: String, cats: List<ExpenseCategoryEntity> = defaults, memory: Map<String, CategoryMemoryEntity> = emptyMap()) =
        ExpenseCategorizer.suggest(note, cats, memory).categoryId

    private fun mem(token: String, categoryId: String, count: Int = 1) = token to CategoryMemoryEntity(token, categoryId, count)

    @Test fun builtInMerchantsMapOntoDefaults() {
        assertEquals("id-food", id("Swiggy 450"))
        assertEquals("id-food", id("Blinkit"))
        assertEquals("id-transport", id("uber to office"))
        assertEquals("id-transport", id("irctc"))
        assertEquals("id-transport", id("petrol"))
        assertEquals("id-fun", id("Netflix"))
        assertEquals("id-home", id("bescom electricity bill"))
        assertEquals("id-food", id("Lunch · Café Ivy"))
    }

    @Test fun nothingMatchedIsNullAndFallbackIsOther() {
        assertEquals(Suggestion.NONE, ExpenseCategorizer.suggest("zxqv", defaults, emptyMap()))
        assertEquals("id-other", ExpenseCategorizer.fallback(defaults)?.id)
        assertNull(ExpenseCategorizer.fallback(defaults.dropLast(1)))
    }

    @Test fun builtInOnlyWhenSuchACategoryExists() {
        assertNull(id("pharmacy", defaults))
        assertEquals("id-health", id("pharmacy", defaults + cat("Health")))
        assertEquals("id-medical", id("apollo", defaults + cat("Medical")))
    }

    @Test fun synonymsAndStemmedNames() {
        assertEquals("id-eating out", id("zomato", listOf(cat("Eating out"), cat("Other"))))
        assertEquals("id-groceries", id("bigbasket", listOf(cat("Eating out"), cat("Groceries"))))
        assertEquals("id-travel", id("uber", listOf(cat("Travel"))))
        assertEquals("id-leisure", id("movie", listOf(cat("Leisure"))))
        assertEquals("id-fuel", id("petrol", listOf(cat("Transport"), cat("Fuel"))))
    }

    @Test fun categoryNameMatchesWithPlural() {
        val cats = defaults + cat("Gifts")
        val s = ExpenseCategorizer.suggest("a gift for dad", cats, emptyMap())
        assertEquals("id-gifts", s.categoryId)
        assertEquals(Reason.Name, s.reason)
    }

    @Test fun userKeywordsBeatBuiltIn() {
        val cats = defaults + cat("Gifts", keywords = "gift, birthday")
        val s = ExpenseCategorizer.suggest("birthday gift 800", cats, emptyMap())
        assertEquals("id-gifts", s.categoryId)
        assertEquals(Reason.Keyword, s.reason)
        val coffee = defaults + cat("Treats", keywords = "coffee")
        assertEquals("id-treats", id("coffee 120", coffee))
    }

    @Test fun keywordPhraseNeedsAllWordsAndScoresHigher() {
        val cats = defaults + cat("Pets", keywords = "dog food")
        assertEquals("id-pets", id("dog food 500", cats))
        assertEquals(0.90f, ExpenseCategorizer.suggest("dog food 500", cats, emptyMap()).confidence)
        assertEquals("id-food", id("lunch food", cats))
    }

    @Test fun learnedMemoryBeatsKeywordsAndBuiltIn() {
        val cats = defaults + cat("Gifts", keywords = "gift")
        val memory = mapOf(mem("gift", "id-fun", 2))
        val s = ExpenseCategorizer.suggest("gift", cats, memory)
        assertEquals("id-fun", s.categoryId)
        assertEquals(Reason.Learned, s.reason)
        assertEquals(0.80f, s.confidence)
    }

    @Test fun memoryOfDeletedCategoryOrZeroCountIsIgnored() {
        assertEquals("id-food", id("swiggy", memory = mapOf(mem("swiggy", "gone", 4))))
        assertEquals("id-food", id("swiggy", memory = mapOf("swiggy" to CategoryMemoryEntity("swiggy", "id-fun", 0))))
        assertEquals("id-food", id("swiggy", memory = mapOf("swiggy" to CategoryMemoryEntity("swiggy", "id-fun", 3, deletedAt = 5))))
    }

    @Test fun memoryScoresSumAcrossTokens() {
        val memory = mapOf(mem("ivy", "id-fun", 1), mem("lunch", "id-home", 1), mem("cafe", "id-home", 1))
        assertEquals("id-home", id("lunch cafe ivy", memory = memory))
    }

    @Test fun confidenceGrowsWithCountAndIsCapped() {
        assertEquals(0.75f, ExpenseCategorizer.suggest("x1 ivy", defaults, mapOf(mem("ivy", "id-fun", 1))).confidence)
        assertEquals(0.95f, ExpenseCategorizer.suggest("ivy", defaults, mapOf(mem("ivy", "id-fun", 9))).confidence)
    }

    @Test fun tiesGoToEarlierCategoryThenName() {
        val a = cat("Zed", keywords = "tip", sort = 0)
        val b = cat("Alpha", keywords = "tip", sort = 1)
        assertEquals("id-zed", id("tip", listOf(a, b)))
        assertEquals("id-alpha", id("tip", listOf(b.copy(sort = 0), a.copy(sort = 0))))
    }

    @Test fun otherIsNeverMatchedByNameOrKeyword() {
        assertNull(id("other stuff", defaults))
        assertNull(id("anything", listOf(cat("Other", keywords = "anything"))))
    }

    @Test fun incomeAndDeletedCategoriesAreNotSuggested() {
        assertNull(id("salary", listOf(cat("Salary", kind = "income"))))
        assertNull(id("gift", listOf(cat("Gifts").copy(deletedAt = 3))))
    }

    @Test fun tokenisation() {
        assertEquals(listOf("lunch", "cafe", "ivy"), CategoryTokens.tokens("Lunch · Café Ivy"))
        assertEquals(listOf("blinkit"), CategoryTokens.tokens("Blinkit ₹450.50"))
        assertEquals(listOf("dominos", "pizza"), CategoryTokens.tokens("paid for the Domino's pizza on UPI"))
        assertTrue(CategoryTokens.tokens("to the 450").isEmpty())
        assertEquals(listOf("tea"), CategoryTokens.tokens("tea tea TEA"))
        assertEquals("gift, Birthday", CategoryTokens.cleanKeywords(" gift ,Birthday,, GIFT ;  "))
    }

    @Test fun stemming() {
        assertTrue(CategoryTokens.same("gifts", "gift"))
        assertTrue(CategoryTokens.same("groceries", "grocery"))
        assertEquals("bus", CategoryTokens.stem("bus"))
        assertEquals("glass", CategoryTokens.stem("glass"))
    }
}
