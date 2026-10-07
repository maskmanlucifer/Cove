package app.cove.companion.data.categorize

import app.cove.companion.data.backup.ExportBuilder
import app.cove.companion.data.backup.Importer
import app.cove.companion.data.backup.BackupStore
import app.cove.companion.data.sync.SyncTable
import app.cove.companion.data.sync.SyncTables
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps Room schema 10, its migration, the sync mapping, backups and the Supabase SQL for payee memory in step. */
class PayeeSchemaTest {
    private fun file(vararg c: String) = c.map(::File).first { it.exists() }
    private val schema get() = file(
        "schemas/app.cove.companion.data.local.CoveDatabase/10.json", "app/schemas/app.cove.companion.data.local.CoveDatabase/10.json",
    ).readText()
    private val database get() = file("src/main/kotlin/app/cove/companion/data/local/CoveDatabase.kt", "app/src/main/kotlin/app/cove/companion/data/local/CoveDatabase.kt").readText()
    private val migration get() = file("../supabase/migrations/0009_payee_memory.sql", "supabase/migrations/0009_payee_memory.sql").readText()
    private val setup get() = file("../supabase/setup.sql", "supabase/setup.sql").readText()

    @Test fun exportedSchema10HasPayeeKeyColumnIndexAndTable() {
        assertTrue(schema.contains("\"version\": 10"))
        assertTrue(schema.contains("\"tableName\": \"payee_memory\""))
        assertTrue(schema.contains("`payeeKey` TEXT, PRIMARY KEY(`id`)"))
        assertTrue(schema.contains("index_expenses_payeeKey"))
        for (col in listOf("label", "displayName", "count", "categoryId", "deletedAt")) assertTrue(col, schema.contains("`$col`"))
    }

    @Test fun migration9To10MatchesTheExportedSchema() {
        assertTrue(database.contains("version = 12"))
        assertTrue(database.contains("MIGRATION_9_10"))
        assertTrue(database.contains("MIGRATION_8_9, MIGRATION_9_10"))
        assertTrue(database.contains("ALTER TABLE `expenses` ADD COLUMN `payeeKey` TEXT"))
        assertTrue(database.contains("CREATE INDEX IF NOT EXISTS `index_expenses_payeeKey` ON `expenses` (`payeeKey`)"))
        val create = "CREATE TABLE IF NOT EXISTS `payee_memory` (`payeeKey` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `label` TEXT, " +
            "`displayName` TEXT NOT NULL, `count` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`payeeKey`))"
        val columns = "(`payeeKey` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `label` TEXT, `displayName` TEXT NOT NULL, " +
            "`count` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`payeeKey`))"
        val flat = database.replace("\" +\n                        \"", "")
        assertTrue(flat.contains("CREATE TABLE IF NOT EXISTS `payee_memory` $columns"))
        assertTrue(schema.contains("`\${TABLE_NAME}` $columns"))
    }

    @Test fun payeeMemoryIsSyncedByPayeeKey() {
        assertEquals("payee_key", SyncTables.find("payee_memory")!!.key)
        assertEquals("payee_key", SyncTables.snake("payeeKey"))
        assertEquals("display_name", SyncTables.snake("displayName"))
        assertEquals(22, SyncTables.all.size)
    }

    @Test fun supabaseMigrationHasTableRlsTriggerIndexAndGrants() {
        assertTrue(migration.contains("create table public.payee_memory"))
        assertTrue(migration.contains("primary key (user_id, payee_key)"))
        assertTrue(migration.contains("alter table public.payee_memory enable row level security"))
        assertTrue(migration.contains("create policy payee_memory_owner"))
        assertTrue(migration.contains("cove_touch"))
        assertTrue(migration.contains("create index payee_memory_user_updated_idx on public.payee_memory (user_id, updated_at)"))
        assertTrue(migration.contains("revoke all on table public.payee_memory from anon"))
        assertTrue(migration.contains("grant select, insert, update, delete on table public.payee_memory to authenticated"))
        assertTrue(migration.contains("add column if not exists payee_key text"))
        for (col in listOf("category_id text not null", "label text", "display_name text not null", "count integer not null", "deleted_at bigint")) assertTrue(col, migration.contains(col))
    }

    @Test fun setupSqlCarriesTheMigrationIdempotently() {
        assertTrue(setup.contains("-- ===== 0009_payee_memory ====="))
        assertTrue(setup.contains("create table if not exists public.payee_memory"))
        assertTrue(setup.contains("drop policy if exists payee_memory_owner on public.payee_memory"))
        assertTrue(setup.contains("drop trigger if exists payee_memory_touch on public.payee_memory"))
        assertTrue(setup.contains("create index if not exists payee_memory_user_updated_idx"))
        assertTrue(setup.contains("alter table public.expenses add column if not exists payee_key text"))
        assertEquals(setup, file("src/main/assets/setup.sql", "app/src/main/assets/setup.sql").readText())
    }

    @Test fun payeeMemorySurvivesABackupRoundTrip() = runBlocking {
        class Mem(val data: MutableMap<String, List<JsonObject>> = HashMap()) : BackupStore {
            override suspend fun readAll(table: SyncTable) = data[table.name].orEmpty()
            override suspend fun isEmpty() = data.filterKeys { it != "settings" }.values.all { it.isEmpty() }
            override suspend fun restore(table: SyncTable, rows: List<JsonObject>) { data[table.name] = rows }
        }
        val memory = buildJsonObject {
            put("payee_key", "vpa:paytmqr2810050501@paytm"); put("category_id", "id-health"); put("label", "Gym")
            put("display_name", "Paytmqr"); put("count", 2L); put("updated_at", 7L); put("deleted_at", JsonNull)
        }
        val unlabelled = buildJsonObject {
            put("payee_key", "name:CARD:MYNTRA"); put("category_id", "id-fun"); put("label", JsonNull)
            put("display_name", "Myntra"); put("count", 1L); put("updated_at", 8L); put("deleted_at", JsonPrimitive(9L))
        }
        val expense = buildJsonObject { put("id", "e1"); put("amount_paise", 30000L); put("payee_key", "vpa:paytmqr2810050501@paytm"); put("external_ref", "100000000001") }
        val source = Mem(mutableMapOf("payee_memory" to listOf(unlabelled, memory), "expenses" to listOf(expense)))
        val json = ExportBuilder.snapshotJson(1, SyncTables.all.associate { it.name to source.readAll(it) }) { SyncTables.find(it)?.key ?: "id" }
        assertTrue(json.indexOf("name:CARD:MYNTRA") < json.indexOf("\"display_name\":\"Paytmqr\""))
        val target = Mem()
        Importer(target).restore(ExportBuilder.parse(ExportBuilder.gzip(json)))
        assertEquals(listOf(unlabelled, memory), target.data["payee_memory"])
        assertEquals(listOf(expense), target.data["expenses"])
        assertFalse(json.contains("\"sms_import_log\""))
    }
}
