package app.cove.companion.feature.brief

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.briefStore by preferencesDataStore("brief_prefs")

/** A stored forecast reply for the place identified by [key], fetched at [at]. */
data class CachedWeather(val key: String, val at: Long, val json: String)

/** Brief settings kept outside Room: the default city and the cached weather reply. */
class BriefPrefs(context: Context) {
    private val store = context.applicationContext.briefStore

    /** The configured default city (Bengaluru until changed). */
    suspend fun defaultPlace(): Place {
        val p = store.data.first()
        val lat = p[LAT]
        val lon = p[LON]
        return if (lat != null && lon != null) Place(p[CITY] ?: "Home", lat, lon) else Place("Bengaluru", 12.9716, 77.5946)
    }

    suspend fun setDefaultPlace(place: Place) {
        store.edit { it[CITY] = place.name; it[LAT] = place.lat; it[LON] = place.lon }
    }

    suspend fun weatherCache(): CachedWeather? {
        val p = store.data.first()
        return CachedWeather(p[W_KEY] ?: return null, p[W_AT] ?: return null, p[W_JSON] ?: return null)
    }

    suspend fun saveWeather(c: CachedWeather) {
        store.edit { it[W_KEY] = c.key; it[W_AT] = c.at; it[W_JSON] = c.json }
    }

    private companion object {
        val CITY = stringPreferencesKey("city")
        val LAT = doublePreferencesKey("lat")
        val LON = doublePreferencesKey("lon")
        val W_KEY = stringPreferencesKey("weather_key")
        val W_AT = longPreferencesKey("weather_at")
        val W_JSON = stringPreferencesKey("weather_json")
    }
}
