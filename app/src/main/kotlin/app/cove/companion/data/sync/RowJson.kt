package app.cove.companion.data.sync

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Helpers over server-shaped row JSON. */
object RowJson {
    /** Columns that describe the write, not the data. */
    private val meta = setOf("updated_at", "device_id", "device_name", "user_id")

    fun long(row: JsonObject, key: String): Long = (row[key] as? JsonPrimitive)?.longOrNull ?: 0

    fun string(row: JsonObject, key: String): String? = (row[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    /** True when the two rows carry the same data, ignoring write metadata and columns [local] does not have. */
    fun sameData(local: JsonObject, remote: JsonObject): Boolean =
        local.keys.filter { it !in meta }.all { same(local[it], remote[it]) }

    private fun same(a: JsonElement?, b: JsonElement?): Boolean {
        val x = a as? JsonPrimitive ?: JsonNull
        val y = b as? JsonPrimitive ?: JsonNull
        if (x is JsonNull || y is JsonNull) return x is JsonNull && y is JsonNull
        if (x.isString || y.isString) return x.content == y.content
        val dx = x.doubleOrNull
        val dy = y.doubleOrNull
        return if (dx != null && dy != null) dx == dy else x.content == y.content
    }

    /** Copy of [row] stamped with the writing device. */
    fun stamped(row: JsonObject, deviceId: String, deviceName: String): JsonObject =
        JsonObject(row + mapOf("device_id" to JsonPrimitive(deviceId), "device_name" to JsonPrimitive(deviceName)) - "user_id")
}
