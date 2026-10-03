package de.thermo.lueftung

import java.time.Instant
import kotlin.math.sqrt

/** Open-Meteo model data, not a local weather-station measurement. */
data class WeatherData(
    val observedAt: Instant,
    val temperature: Float,
    val humidity: Int,
    val cloudCover: Float,
    val rainMm: Float,
    val snowfallCm: Float,
    val code: Int,
    val isDay: Boolean
) {
    val condition: String get() = when {
        code in 95..99 -> "Gewitter"
        code in listOf(71,73,75,77,85,86) || snowfallCm > 0 -> "Schnee"
        code in listOf(51,53,55,56,57,61,63,65,66,67,80,81,82) || rainMm > 0 -> "Regen"
        cloudCover >= 45 || code in listOf(2,3,45,48) -> "Bewölkt"
        !isDay -> "Nacht"
        else -> "Sonnig"
    }
    fun fresh(now: Instant = Instant.now()): Boolean =
        observedAt <= now.plusSeconds(300) && observedAt >= now.minusSeconds(5400)
    val effects get() = WeatherEffects(cloudCover / 100f, rainMm, snowfallCm)
}

data class WeatherEffects(val clouds: Float, val rainMm: Float, val snowCm: Float) {
    fun rainCount(lowMemory: Boolean): Int =
        if (rainMm <= 0) 0 else (sqrt(rainMm) * 34).toInt().coerceIn(8, if (lowMemory) 40 else 96)
    fun snowCount(lowMemory: Boolean): Int =
        if (snowCm <= 0) 0 else (sqrt(snowCm) * 110).toInt().coerceIn(24, if (lowMemory) 64 else 160)
    companion object {
        fun demo(condition: String) = WeatherEffects(
            when(condition) { "Bewölkt" -> .75f; "Regen", "Schnee" -> .85f; "Gewitter" -> 1f; else -> .12f },
            when(condition) { "Regen" -> 1.5f; "Gewitter" -> 5f; else -> 0f },
            if(condition == "Schnee") 1.4f else 0f)
    }
}
