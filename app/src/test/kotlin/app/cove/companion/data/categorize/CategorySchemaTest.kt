package app.cove.companion.data.categorize

import app.cove.companion.data.sync.SyncTables
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps the exported Room schema, migration, sync mapping and Supabase migration for categorisation in step. */
class CategorySchemaTest {
    private fun file(vararg candidates: String) = candidates.map(::File).first { it.exists() }

    private val schema get() = file(
        "schemas/app.cove.companion.data.local.CoveDatabase/6.json", "app/schemas/app.cove.companion.data.local.CoveDatabase/6.json",
    ).readText()

    @Test fun exportedSchemaHasKeywordsAndMemory() {
        assertTrue(schema.contains("\"version\": 6"))
        assertTrue(schema.contains("\"columnName\": \"keywords\""))
        assertTrue(schema.contains("\"tableName\": \"category_memory\""))
    }

    @Test fun memoryIsSyncedByToken() {
        assertEquals("token", SyncTables.find("category_memory")!!.key)
        assertEquals("category_id", SyncTables.snake("categoryId"))
    }

    @Test fun supabaseMigrationCoversKeywordsAndMemory() {
        val sql = file("../supabase/migrations/0004_categorize.sql", "supabase/migrations/0004_categorize.sql").readText()
        assertTrue(sql.contains("keywords text not null default ''"))
        assertTrue(sql.contains("create table public.category_memory"))
        assertTrue(sql.contains("enable row level security"))
        assertTrue(sql.contains("cove_touch"))
        assertNotNull(Regex("primary key \\(user_id, token\\)").find(sql))
    }

    @Test fun keywordListHandlesMessyInput() {
        assertEquals(listOf("gift", "birthday party"), CategoryTokens.keywordList(" gift,, birthday party ; "))
        assertEquals(emptyList<String>(), CategoryTokens.keywordList(""))
    }
}
