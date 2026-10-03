package de.thermo.lueftung

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

object WeatherRepository {
    const val LOCATION = "74597 Stimpfach · Rechenberg"
    // Approximate village centre; forecast grid resolution is coarser than a house location.
    const val ENDPOINT = "https://api.open-meteo.com/v1/forecast?latitude=49.065&longitude=10.145&current=temperature_2m,relative_humidity_2m,is_day,weather_code,cloud_cover,rain,showers,snowfall&timezone=UTC"
    suspend fun fetch(): WeatherData = withContext(Dispatchers.IO) {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            check(connection.responseCode == 200) { "Weather unavailable" }
            val current = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
            fun number(name: String): Float = current.getDouble(name).toFloat().also { require(it.isFinite()) }
            WeatherData(
                Instant.parse(current.getString("time") + "Z"), number("temperature_2m"),
                number("relative_humidity_2m").toInt().also { require(it in 0..100) },
                number("cloud_cover").also { require(it in 0f..100f) },
                (number("rain") + number("showers")).also { require(it >= 0) },
                number("snowfall").also { require(it >= 0) },
                current.getInt("weather_code"), current.getInt("is_day") == 1
            ).also { check(it.fresh()) { "Weather stale" } }
        } finally { connection.disconnect() }
    }
}
