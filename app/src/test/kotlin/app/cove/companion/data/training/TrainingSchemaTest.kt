package app.cove.companion.data.training

import app.cove.companion.data.backup.BackupStore
import app.cove.companion.data.backup.ExportBuilder
import app.cove.companion.data.backup.Importer
import app.cove.companion.data.local.TrainingMigration
import app.cove.companion.data.sync.SyncTable
import app.cove.companion.data.sync.SyncTables
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSchemaTest {
    private val newTables = listOf("plan_exercises", "day_overrides", "exercise_logs")
    private val oldTables = listOf("exercises", "workout_plans", "plan_days", "workout_sessions", "set_logs")
    private fun file(vararg c: String) = c.map(::File).first { it.exists() }

    private fun schema() = Json.parseToJsonElement(file("schemas/app.cove.companion.data.local.CoveDatabase/8.json", "app/schemas/app.cove.companion.data.local.CoveDatabase/8.json").readText()).jsonObject
        .getValue("database").jsonObject

    private fun entities() = schema().getValue("entities").jsonArray.map { it.jsonObject }
    private fun name(e: JsonObject) = e.getValue("tableName").jsonPrimitive.content

    @Test fun createSqlIsExactlyWhatRoomGeneratesForVersion8() {
        assertEquals(8, schema().getValue("version").jsonPrimitive.content.toInt())
        val expected = entities().filter { name(it) in newTables }.sortedBy { newTables.indexOf(name(it)) }.flatMap { e ->
            (listOf(e.getValue("createSql").jsonPrimitive.content) + e["indices"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("createSql").jsonPrimitive.content })
                .map { it.replace("\${TABLE_NAME}", name(e)) }
        }
        assertEquals(expected, TrainingMigration.CREATE)
    }

    @Test fun oldTablesAreGoneFromTheSchemaAndSettingsKeepOnlyTheUnit() {
        val names = entities().map(::name)
        oldTables.forEach { assertFalse(it, it in names) }
        val fields = entities().first { name(it) == "training_settings" }.getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
        assertEquals(listOf("id", "unit", "updatedAt"), fields)
    }

    @Test fun migrationKeepsHistoryThenDropsOldTablesAndTheirPendingSync() {
        val sql = TrainingMigration.ALL.joinToString("\n")
        oldTables.forEach {
            assertTrue(it, sql.contains("DROP TABLE IF EXISTS `$it`"))
            assertTrue(it, sql.contains("'$it'"))
        }
        assertTrue(sql.contains("INSERT OR REPLACE INTO exercise_logs"))
        assertTrue(sql.contains("ALTER TABLE training_settings_new RENAME TO training_settings"))
        assertTrue(TrainingMigration.ALL.indexOf(TrainingMigration.KEEP_HISTORY.first()) < TrainingMigration.ALL.indexOf("DROP TABLE IF EXISTS `set_logs`"))
    }

    @Test fun everySyncedTrainingColumnExistsOnTheServer() {
        val sql = file("../supabase/setup.sql", "supabase/setup.sql").readText()
        val migration = file("../supabase/migrations/0006_training_simple.sql", "supabase/migrations/0006_training_simple.sql").readText()
        for (e in entities().filter { name(it) in newTables }) {
            val n = name(e)
            assertNotNull(n, SyncTables.find(n))
            val block = sql.substringAfter("create table if not exists public.$n (").substringBefore(");")
            val block2 = migration.substringAfter("create table public.$n (").substringBefore(");")
            for (f in e.getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }) {
                val col = SyncTables.snake(f)
                assertTrue("$n.$col in setup.sql", Regex("""\n\s+$col """).containsMatchIn(block))
                assertTrue("$n.$col in 0006", Regex("""\n\s+$col """).containsMatchIn(block2))
            }
        }
        assertEquals("day", SyncTables.find("body_weights")!!.key)
        assertEquals(setOf("dismissed"), SyncTables.find("day_overrides")!!.bools)
        oldTables.forEach { assertNull(it, SyncTables.find(it)) }
    }

    private class Mem(val data: MutableMap<String, List<JsonObject>> = HashMap()) : BackupStore {
        override suspend fun readAll(table: SyncTable) = data[table.name].orEmpty()
        override suspend fun isEmpty() = data.filterKeys { it != "settings" }.values.all { it.isEmpty() }
        override suspend fun restore(table: SyncTable, rows: List<JsonObject>) { data[table.name] = rows }
    }

    @Test fun backupRoundTripIncludesTrainingTables() = runBlocking {
        val src = Mem(
            mutableMapOf(
                "plan_exercises" to listOf(buildJsonObject { put("id", "b"); put("weekday", 2); put("name", "Bench press"); put("weight_kg", 60.0) }),
                "day_overrides" to listOf(buildJsonObject { put("id", "20000|b"); put("day", 20000L); put("weight_kg", 62.5); put("dismissed", false) }),
                "exercise_logs" to listOf(
                    buildJsonObject { put("id", "20000|bench press"); put("weight_kg", 62.5); put("reps", "8,8,6") },
                    buildJsonObject { put("id", "19993|bench press"); put("weight_kg", 60.0); put("reps", "8,8,8") },
                ),
                "body_weights" to listOf(buildJsonObject { put("day", 20_000L); put("kg", 68.4) }),
                "training_settings" to listOf(buildJsonObject { put("id", "me"); put("unit", "lb") }),
            ),
        )
        val json = ExportBuilder.snapshotJson(1, SyncTables.all.associate { it.name to src.readAll(it) }) { SyncTables.find(it)?.key ?: "id" }
        val parsed = ExportBuilder.parse(ExportBuilder.gzip(json))
        val target = Mem()
        assertEquals(6, Importer(target).restore(parsed))
        for (t in listOf("plan_exercises", "day_overrides", "exercise_logs", "body_weights", "training_settings")) {
            assertEquals(t, src.data.getValue(t).sortedBy { (it[SyncTables.find(t)!!.key] as JsonPrimitive).content }, target.data.getValue(t))
        }
        assertEquals(listOf("19993|bench press", "20000|bench press"), target.data.getValue("exercise_logs").map { (it["id"] as JsonPrimitive).content })
        assertTrue(!target.isEmpty())
    }

    @Test fun anOldBackupWithRetiredTablesStillRestoresWhatItCan() = runBlocking {
        val src = Mem(mutableMapOf("exercises" to listOf(buildJsonObject { put("id", "e") }), "body_weights" to listOf(buildJsonObject { put("day", 1L); put("kg", 70.0) })))
        val json = ExportBuilder.snapshotJson(1, src.data, { SyncTables.find(it)?.key ?: "id" })
        val target = Mem()
        assertEquals(1, Importer(target).restore(ExportBuilder.parse(ExportBuilder.gzip(json))))
        assertNull(target.data["exercises"])
    }
}
