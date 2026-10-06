package app.cove.companion.data.training

import app.cove.companion.data.backup.ExportBuilder
import app.cove.companion.data.backup.Importer
import app.cove.companion.data.backup.BackupStore
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
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSchemaTest {
    private val tables = listOf("exercises", "workout_plans", "plan_days", "workout_sessions", "set_logs", "body_weights", "training_settings")
    private fun file(vararg c: String) = c.map(::File).first { it.exists() }

    private fun schema() = Json.parseToJsonElement(file("schemas/app.cove.companion.data.local.CoveDatabase/7.json", "app/schemas/app.cove.companion.data.local.CoveDatabase/7.json").readText()).jsonObject
        .getValue("database").jsonObject

    @Test fun migrationSqlIsExactlyWhatRoomGeneratesForVersion7() {
        val db = schema()
        assertEquals(7, db.getValue("version").jsonPrimitive.content.toInt())
        val generated = db.getValue("entities").jsonArray.map { it.jsonObject }.filter { it.getValue("tableName").jsonPrimitive.content in tables }.flatMap { e ->
            val name = e.getValue("tableName").jsonPrimitive.content
            listOf(e.getValue("createSql").jsonPrimitive.content) + e["indices"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("createSql").jsonPrimitive.content }
                .map { it }.map { s -> s }.let { it }.also { _ -> name }
                .map { it }
        }
        val expanded = db.getValue("entities").jsonArray.map { it.jsonObject }.filter { it.getValue("tableName").jsonPrimitive.content in tables }.flatMap { e ->
            val name = e.getValue("tableName").jsonPrimitive.content
            (listOf(e.getValue("createSql").jsonPrimitive.content) + e["indices"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("createSql").jsonPrimitive.content })
                .map { it.replace("\${TABLE_NAME}", name) }
        }
        assertEquals(generated.size, expanded.size)
        assertEquals(expanded, TrainingMigration.STATEMENTS)
    }

    @Test fun everySyncedTrainingColumnExistsOnTheServer() {
        val sql = file("../supabase/setup.sql", "supabase/setup.sql").readText()
        val migration = file("../supabase/migrations/0005_training.sql", "supabase/migrations/0005_training.sql").readText()
        for (e in schema().getValue("entities").jsonArray.map { it.jsonObject }.filter { it.getValue("tableName").jsonPrimitive.content in tables }) {
            val name = e.getValue("tableName").jsonPrimitive.content
            assertTrue(name, SyncTables.find(name) != null)
            val block = sql.substringAfter("create table if not exists public.$name (").substringBefore(");")
            val block2 = migration.substringAfter("create table public.$name (").substringBefore(");")
            for (f in e.getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }) {
                val col = SyncTables.snake(f)
                assertTrue("$name.$col in setup.sql", Regex("""\n\s+$col """).containsMatchIn(block))
                assertTrue("$name.$col in 0005", Regex("""\n\s+$col """).containsMatchIn(block2))
            }
        }
        assertEquals("day", SyncTables.find("body_weights")!!.key)
    }

    private class Mem(val data: MutableMap<String, List<JsonObject>> = HashMap()) : BackupStore {
        override suspend fun readAll(table: SyncTable) = data[table.name].orEmpty()
        override suspend fun isEmpty() = data.filterKeys { it != "settings" }.values.all { it.isEmpty() }
        override suspend fun restore(table: SyncTable, rows: List<JsonObject>) { data[table.name] = rows }
    }

    @Test fun backupRoundTripIncludesTrainingTables() = runBlocking {
        val src = Mem(
            mutableMapOf(
                "exercises" to listOf(buildJsonObject { put("id", "bench"); put("name", "Bench press"); put("increment_kg", 2.5) }),
                "workout_sessions" to listOf(buildJsonObject { put("id", "s1"); put("day_type", "Push"); put("planned_at", 5L) }),
                "set_logs" to listOf(
                    buildJsonObject { put("id", "b"); put("weight_kg", 62.5); put("reps", 8) },
                    buildJsonObject { put("id", "a"); put("weight_kg", 62.5); put("reps", 6) },
                ),
                "body_weights" to listOf(buildJsonObject { put("day", 20_000L); put("kg", 68.4) }),
                "training_settings" to listOf(buildJsonObject { put("id", "me"); put("unit", "kg"); put("weekdays", "2,4,6") }),
            ),
        )
        val json = ExportBuilder.snapshotJson(1, SyncTables.all.associate { it.name to src.readAll(it) }) { SyncTables.find(it)?.key ?: "id" }
        val parsed = ExportBuilder.parse(ExportBuilder.gzip(json))
        val target = Mem()
        assertEquals(6, Importer(target).restore(parsed))
        for (t in listOf("exercises", "workout_sessions", "set_logs", "body_weights", "training_settings")) {
            assertEquals(t, src.data.getValue(t).sortedBy { (it[SyncTables.find(t)!!.key] as JsonPrimitive).content }, target.data.getValue(t))
        }
        assertEquals(listOf("a", "b"), target.data.getValue("set_logs").map { (it["id"] as JsonPrimitive).content })
        assertTrue(!target.isEmpty())
    }
}
