package app.cove.companion.data.config

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupSqlSyncTest {
    private fun file(vararg candidates: String) = candidates.map(::File).first { it.exists() }

    @Test fun bundledCopyMatchesSupabaseFolder() {
        val a = file("../supabase/setup.sql", "supabase/setup.sql").readText()
        val b = file("src/main/assets/setup.sql", "app/src/main/assets/setup.sql").readText()
        assertEquals("Copy supabase/setup.sql to app/src/main/assets/setup.sql", a, b)
    }

    @Test fun coversEveryMigrationAndIsIdempotent() {
        val sql = file("../supabase/setup.sql", "supabase/setup.sql").readText()
        val dir = file("../supabase/migrations", "supabase/migrations")
        val tables = dir.listFiles()!!.flatMap { f -> Regex("""create table (?:if not exists )?public\.(\w+)""").findAll(f.readText()).map { it.groupValues[1] } }
        assertTrue(tables.isNotEmpty())
        tables.forEach { assertTrue(it, sql.contains("create table if not exists public.$it")) }
        Regex("""add column if not exists (\w+)""").findAll(dir.listFiles()!!.joinToString { it.readText() })
            .forEach { assertTrue(it.groupValues[1], sql.contains(it.value)) }
        assertTrue(!Regex("""create table (?!if not exists)""").containsMatchIn(sql))
    }
}
