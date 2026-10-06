package app.cove.companion.data.categorize

import app.cove.companion.data.local.entity.CategoryMemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryLearningTest {
    private fun row(cat: String, count: Int, deleted: Long? = null) = CategoryMemoryEntity("ivy", cat, count, 1, deleted)

    @Test fun firstPickCreatesAndRepeatsRaise() {
        val one = CategoryLearning.learn(null, "ivy", "food", 10)
        assertEquals(CategoryMemoryEntity("ivy", "food", 1, 10), one)
        assertEquals(2, CategoryLearning.learn(one, "ivy", "food", 11).count)
    }

    @Test fun contradictingPicksLowerThenFlip() {
        val strong = row("food", 3)
        val a = CategoryLearning.learn(strong, "ivy", "fun", 5)
        assertEquals("food" to 2, a.categoryId to a.count)
        val b = CategoryLearning.learn(CategoryLearning.learn(a, "ivy", "fun", 6), "ivy", "fun", 7)
        assertEquals("fun" to 1, b.categoryId to b.count)
    }

    @Test fun countIsCapped() {
        assertEquals(CategoryLearning.MAX_COUNT, CategoryLearning.learn(row("food", CategoryLearning.MAX_COUNT), "ivy", "food", 2).count)
    }

    @Test fun unlearnDecrementsAndSoftDeletesAtZero() {
        assertEquals(1, CategoryLearning.unlearn(row("food", 2), "food", 9)!!.count)
        val gone = CategoryLearning.unlearn(row("food", 1), "food", 9)!!
        assertEquals(0 to 9L, gone.count to gone.deletedAt)
    }

    @Test fun unlearnIgnoresOtherCategoriesAndMissingRows() {
        assertNull(CategoryLearning.unlearn(row("food", 2), "fun", 9))
        assertNull(CategoryLearning.unlearn(null, "food", 9))
        assertNull(CategoryLearning.unlearn(row("food", 1, deleted = 3), "food", 9))
    }

    @Test fun deletedRowIsRelearnedFresh() {
        val r = CategoryLearning.learn(row("food", 0, deleted = 3), "ivy", "fun", 20)
        assertEquals("fun" to 1, r.categoryId to r.count)
        assertNull(r.deletedAt)
    }

    @Test fun correctionMovesAMildHabitInOneStep() {
        var r: CategoryMemoryEntity? = CategoryLearning.learn(CategoryLearning.learn(null, "ivy", "food", 1), "ivy", "food", 2)
        r = CategoryLearning.learn(CategoryLearning.unlearn(r, "food", 3), "ivy", "fun", 3)
        assertEquals("fun" to 1, r.categoryId to r.count)
    }

    @Test fun correctionOnlyWeakensAStrongHabit() {
        val r = CategoryLearning.learn(CategoryLearning.unlearn(row("food", 5), "food", 3), "ivy", "fun", 3)
        assertEquals("food" to 3, r.categoryId to r.count)
    }
}
