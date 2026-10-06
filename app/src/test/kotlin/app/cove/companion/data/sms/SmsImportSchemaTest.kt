package app.cove.companion.data.sms

import app.cove.companion.feature.money.imports.ImportViewModel
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsImportSchemaTest {
    private fun file(vararg c: String) = c.map(::File).first { it.exists() }
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun ms(d: Int, h: Int = 12) = LocalDateTime.of(2026, 10, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun schema8HasLogTableAndExternalRef() {
        val json = file("schemas/app.cove.companion.data.local.CoveDatabase/8.json", "app/schemas/app.cove.companion.data.local.CoveDatabase/8.json").readText()
        assertTrue(json.contains("\"version\": 8"))
        assertTrue(json.contains("\"tableName\": \"sms_import_log\""))
        assertTrue(json.contains("\"columnName\": \"externalRef\""))
    }

    @Test fun logTableIsNeverSynced() {
        val sync = file("src/main/kotlin/app/cove/companion/data/sync/SyncTables.kt", "app/src/main/kotlin/app/cove/companion/data/sync/SyncTables.kt").readText()
        assertTrue(!sync.contains("sms_import_log"))
    }

    @Test fun supabaseKnowsExternalRef() {
        val sql = file("../supabase/setup.sql", "supabase/setup.sql").readText()
        assertTrue(sql.contains("add column if not exists external_ref text"))
        assertTrue(file("../supabase/migrations/0006_expense_external_ref.sql", "supabase/migrations/0006_expense_external_ref.sql").exists())
    }

    @Test fun rangeStarts() {
        val now = ms(20)
        assertEquals(now - 30L * 24 * 3_600_000, ImportRange.start(ImportRange.Last30, now, null, zone))
        assertEquals(ms(1, 0), ImportRange.start(ImportRange.ThisMonth, now, null, zone))
        assertEquals(0L, ImportRange.start(ImportRange.All, now, 5L, zone))
        assertEquals(1001L, ImportRange.start(ImportRange.SinceLast, now, 1000L, zone))
        assertEquals(now - 30L * 24 * 3_600_000, ImportRange.start(ImportRange.SinceLast, now, null, zone))
    }

    @Test fun summaryTexts() {
        assertEquals("Added 14 · skipped 3 duplicates", ImportViewModel.summaryText(14, 3, 0))
        assertEquals("Added 1 · skipped 1 duplicate · left out 2", ImportViewModel.summaryText(1, 1, 2))
        assertEquals("Nothing added", ImportViewModel.summaryText(0, 0, 0))
    }

    @Test fun privacyDocAndManifestMentionSms() {
        val manifest = file("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.READ_SMS"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }
}
