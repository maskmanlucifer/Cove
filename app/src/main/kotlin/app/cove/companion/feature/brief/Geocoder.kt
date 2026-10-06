package app.cove.companion.feature.brief

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parsing of Open-Meteo geocoding replies. */
object GeocodeParser {
    /** The best match of a `/v1/search` reply, or null when there is none or the shape is unexpected. */
    fun parse(text: String): Place? = runCatching {
        val hit = Json.parseToJsonElement(text).jsonObject["results"]!!.jsonArray[0].jsonObject
        Place(
            hit["name"]!!.jsonPrimitive.content,
            hit["latitude"]!!.jsonPrimitive.doubleOrNull!!,
            hit["longitude"]!!.jsonPrimitive.doubleOrNull!!,
        )
    }.getOrNull()
}

/** Turns a typed city name into a [Place] with Open-Meteo's free geocoding API (no key). */
class Geocoder(private val client: HttpClient) {
    /** The best match for [name], or null when nothing matches or there is no network. */
    suspend fun find(name: String): Place? = try {
        withTimeoutOrNull(6_000) {
            val r = client.get("https://geocoding-api.open-meteo.com/v1/search") {
                parameter("name", name.trim())
                parameter("count", 1)
            }
            if (r.status.isSuccess()) GeocodeParser.parse(r.bodyAsText()) else null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
