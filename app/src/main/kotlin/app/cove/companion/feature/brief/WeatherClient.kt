package app.cove.companion.feature.brief

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/** Where the weather is looked up. */
data class Place(val name: String, val lat: Double, val lon: Double)

/** Parsing of Open-Meteo `/v1/forecast` replies. */
object WeatherParser {
    /** Plain word for a WMO weather code. */
    fun describe(code: Int): String = when (code) {
        0 -> "clear"
        1, 2 -> "mostly clear"
        3 -> "cloudy"
        45, 48 -> "foggy"
        in 51..57 -> "drizzly"
        in 61..67, in 80..82 -> "rainy"
        in 71..77, 85, 86 -> "snowy"
        in 95..99 -> "stormy"
        else -> "calm"
    }

    /** Reads `current` and `daily` from the reply, or null when the shape is not what we expect. */
    fun parse(text: String): WeatherFacts? = runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        val current = root["current"] as JsonObject
        val daily = root["daily"] as JsonObject
        val temp = current["temperature_2m"]!!.jsonPrimitive.doubleOrNull!!
        val code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0
        val high = daily["temperature_2m_max"]!!.jsonArray[0].jsonPrimitive.doubleOrNull ?: temp
        val rain = daily["precipitation_probability_max"]?.jsonArray?.getOrNull(0)?.jsonPrimitive?.intOrNull ?: 0
        WeatherFacts(temp.roundToInt(), high.roundToInt(), describe(code), rain)
    }.getOrNull()
}

/** Fetches the forecast from Open-Meteo (no key), caching it for an hour and falling back to a stale copy offline. */
class WeatherClient(
    private val prefs: BriefPrefs,
    private val now: () -> Long,
    private val client: HttpClient = HttpClient(OkHttp),
) {
    /** Current weather for [place], or null when there is neither network nor any cached copy. */
    suspend fun forecast(place: Place): WeatherFacts? {
        val cached = prefs.weatherCache()
        val key = "%.2f,%.2f".format(place.lat, place.lon)
        if (cached != null && cached.key == key && now() - cached.at < CACHE_MS) WeatherParser.parse(cached.json)?.let { return it }
        val fresh = fetch(place)
        if (fresh != null) {
            prefs.saveWeather(CachedWeather(key, now(), fresh))
            return WeatherParser.parse(fresh)
        }
        return cached?.takeIf { it.key == key }?.let { WeatherParser.parse(it.json) }
    }

    private suspend fun fetch(place: Place): String? = try {
        withTimeoutOrNull(6_000) {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,precipitation_probability_max&forecast_days=1&timezone=auto"
            val r = client.get(url)
            if (r.status.isSuccess()) r.bodyAsText() else null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val CACHE_MS = 60 * 60 * 1000L
    }
}
