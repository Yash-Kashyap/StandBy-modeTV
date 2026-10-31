package com.yash.standbytv

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.thread
import kotlin.math.roundToInt

data class Weather(
    val place: String,
    val tempC: Double,
    val code: Int,
    val isDay: Boolean,
    val hiC: Double,
    val loC: Double,
    val humidity: Int,
    val windKmh: Double,
    val sunrise: String,
    val sunset: String,
    val fetchedAt: Long,
) {
    val kind: Kind get() = kindOf(code)
    val label: String get() = labelOf(code)

    enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

    companion object {
        fun kindOf(code: Int) = when (code) {
            0 -> Kind.CLEAR
            1, 2 -> Kind.PARTLY
            3 -> Kind.CLOUDY
            45, 48 -> Kind.FOG
            51, 53, 55, 56, 57 -> Kind.DRIZZLE
            61, 63, 65, 66, 67, 80, 81, 82 -> Kind.RAIN
            71, 73, 75, 77, 85, 86 -> Kind.SNOW
            95, 96, 99 -> Kind.THUNDER
            else -> Kind.CLOUDY
        }

        fun labelOf(code: Int) = when (code) {
            0 -> "Clear"
            1 -> "Mostly Clear"
            2 -> "Partly Cloudy"
            3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing Drizzle"
            61, 80 -> "Light Rain"
            63, 81 -> "Rain"
            65, 82 -> "Heavy Rain"
            66, 67 -> "Freezing Rain"
            71, 85 -> "Light Snow"
            73, 75, 86 -> "Snow"
            77 -> "Snow Grains"
            95 -> "Thunderstorm"
            96, 99 -> "Hail Storm"
            else -> "—"
        }
    }
}

/**
 * Weather without an API key: Open-Meteo for forecasts, IP geolocation for "auto" location
 * (TVs have no GPS), Open-Meteo geocoding for a typed city.
 */
object WeatherRepo {
    @Volatile var current: Weather? = null
        internal set
    @Volatile var lastError: String? = null
        private set

    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var fetching = false
    private const val STALE_MS = 30 * 60 * 1000L

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyAll_() = main.post { listeners.forEach { it() } }

    fun temp(c: Double, unit: String): String {
        val v = if (unit == "F") c * 9.0 / 5.0 + 32.0 else c
        return "${v.roundToInt()}°"
    }

    fun loadCache(ctx: Context) {
        if (current != null) return
        val js = Prefs(ctx).sp.getString("weather_cache", null) ?: return
        current = runCatching { fromJson(JSONObject(js)) }.getOrNull()
    }

    fun refresh(ctx: Context, force: Boolean = false) {
        loadCache(ctx)
        val cur = current
        if (!force && cur != null && System.currentTimeMillis() - cur.fetchedAt < STALE_MS) return
        if (fetching) return
        fetching = true
        val prefs = Prefs(ctx)
        thread(name = "weather") {
            try {
                val (lat, lon, place) = resolveLocation(prefs)
                val w = fetchForecast(lat, lon, place)
                current = w
                lastError = null
                prefs.sp.edit().putString("weather_cache", toJson(w).toString()).apply()
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
            } finally {
                fetching = false
                notifyAll_()
            }
        }
    }

    /** Looks up a city by name. Runs on the caller's thread – call from a background thread. */
    fun geocode(name: String): Triple<Double, Double, String>? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
            URLEncoder.encode(name, "UTF-8")
        val js = JSONObject(get(url))
        val arr = js.optJSONArray("results") ?: return null
        if (arr.length() == 0) return null
        val r = arr.getJSONObject(0)
        val label = listOfNotNull(r.optString("name").ifBlank { null }, r.optString("country_code").ifBlank { null })
            .joinToString(", ")
        return Triple(r.getDouble("latitude"), r.getDouble("longitude"), label)
    }

    private fun resolveLocation(p: Prefs): Triple<Double, Double, String> {
        if (p.locMode == "city" && p.cityName.isNotBlank()) {
            return Triple(p.cityLat.toDouble(), p.cityLon.toDouble(), p.cityName.substringBefore(","))
        }
        // Cached IP location for 6 hours
        val t = p.sp.getLong("ip_loc_time", 0L)
        if (System.currentTimeMillis() - t < 6 * 3600 * 1000L && p.sp.contains("ip_lat")) {
            return Triple(p.sp.getFloat("ip_lat", 0f).toDouble(), p.sp.getFloat("ip_lon", 0f).toDouble(),
                p.sp.getString("ip_city", "") ?: "")
        }
        val loc = runCatching {
            val j = JSONObject(get("https://ipwho.is/"))
            if (!j.optBoolean("success", true)) error("ipwho failed")
            Triple(j.getDouble("latitude"), j.getDouble("longitude"), j.optString("city"))
        }.recoverCatching {
            val j = JSONObject(get("https://ipapi.co/json/"))
            Triple(j.getDouble("latitude"), j.getDouble("longitude"), j.optString("city"))
        }.getOrThrow()
        p.sp.edit().putLong("ip_loc_time", System.currentTimeMillis())
            .putFloat("ip_lat", loc.first.toFloat()).putFloat("ip_lon", loc.second.toFloat())
            .putString("ip_city", loc.third).apply()
        return loc
    }

    private fun fetchForecast(lat: Double, lon: Double, place: String): Weather {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,relative_humidity_2m,is_day,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=auto&forecast_days=1",
            lat, lon
        )
        val j = JSONObject(get(url))
        val cur = j.getJSONObject("current")
        val d = j.getJSONObject("daily")
        return Weather(
            place = place,
            tempC = cur.getDouble("temperature_2m"),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            hiC = d.getJSONArray("temperature_2m_max").getDouble(0),
            loC = d.getJSONArray("temperature_2m_min").getDouble(0),
            humidity = cur.optInt("relative_humidity_2m", 0),
            windKmh = cur.optDouble("wind_speed_10m", 0.0),
            sunrise = d.getJSONArray("sunrise").getString(0).substringAfter("T"),
            sunset = d.getJSONArray("sunset").getString(0).substringAfter("T"),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "StandByTV/1.0 (Android TV)")
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun toJson(w: Weather) = JSONObject().apply {
        put("place", w.place); put("tempC", w.tempC); put("code", w.code); put("isDay", w.isDay)
        put("hiC", w.hiC); put("loC", w.loC); put("humidity", w.humidity); put("windKmh", w.windKmh)
        put("sunrise", w.sunrise); put("sunset", w.sunset); put("fetchedAt", w.fetchedAt)
    }

    private fun fromJson(j: JSONObject) = Weather(
        j.getString("place"), j.getDouble("tempC"), j.getInt("code"), j.getBoolean("isDay"),
        j.getDouble("hiC"), j.getDouble("loC"), j.getInt("humidity"), j.getDouble("windKmh"),
        j.getString("sunrise"), j.getString("sunset"), j.getLong("fetchedAt")
    )
}
