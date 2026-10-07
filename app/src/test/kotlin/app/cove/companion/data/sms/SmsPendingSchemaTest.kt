package app.cove.companion.data.sms

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsPendingSchemaTest {
    private fun file(vararg c: String) = c.map(::File).first { it.exists() }
    private val database = file("src/main/kotlin/app/cove/companion/data/local/CoveDatabase.kt", "app/src/main/kotlin/app/cove/companion/data/local/CoveDatabase.kt").readText()
    private val schema = file("schemas/app.cove.companion.data.local.CoveDatabase/11.json", "app/schemas/app.cove.companion.data.local.CoveDatabase/11.json").readText()

    @Test fun schema11HasThePendingTable() {
        assertTrue(schema.contains("\"version\": 11"))
        assertTrue(schema.contains("\"tableName\": \"sms_pending\""))
        for (col in listOf("key", "messageKeys", "amountPaise", "direction", "merchant", "paidWith", "ref", "bank", "payeeKey", "matchExpenseId", "matchNote")) {
            assertTrue(col, schema.contains("`$col`"))
        }
        assertTrue(schema.contains("index_sms_pending_at"))
    }

    @Test fun pendingTableHoldsNoMessageText() {
        val create = Regex("""CREATE TABLE IF NOT EXISTS `\$\{TABLE_NAME}` \(`key` TEXT NOT NULL, `messageKeys`[^"]*""").find(schema)?.value.orEmpty()
        assertTrue(create.isNotEmpty())
        for (word in listOf("body", "text", "message `", "content")) assertTrue(word, !create.contains("`$word`"))
    }

    @Test fun migration10To11MatchesTheExportedSchema() {
        assertTrue(database.contains("version = 12"))
        assertTrue(database.contains("MIGRATION_9_10, MIGRATION_10_11"))
        val columns = "(`key` TEXT NOT NULL, `messageKeys` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `direction` TEXT NOT NULL, `merchant` TEXT, " +
            "`at` INTEGER NOT NULL, `dateFromText` INTEGER NOT NULL, `last4` TEXT, `paidWith` TEXT NOT NULL, `ref` TEXT, `bank` TEXT, " +
            "`confidence` REAL NOT NULL, `payeeKey` TEXT, `matchExpenseId` TEXT, `matchNote` TEXT, `matchAmountPaise` INTEGER, " +
            "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`key`))"
        val flat = database.replace("\" +\n                        \"", "")
        assertTrue(flat.contains("CREATE TABLE IF NOT EXISTS `sms_pending` $columns"))
        assertTrue(schema.contains("`\${TABLE_NAME}` $columns"))
        assertTrue(flat.contains("CREATE INDEX IF NOT EXISTS `index_sms_pending_at` ON `sms_pending` (`at`)"))
    }

    @Test fun pendingTableIsNeverSyncedOrBackedUp() {
        val sync = file("src/main/kotlin/app/cove/companion/data/sync/SyncTables.kt", "app/src/main/kotlin/app/cove/companion/data/sync/SyncTables.kt").readText()
        assertTrue(!sync.contains("sms_pending"))
        val setup = file("../supabase/setup.sql", "supabase/setup.sql").readText()
        assertTrue(!setup.contains("sms_pending"))
    }
}
