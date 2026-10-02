package de.thermo.lueftung

enum class WeatherLighting { DAY, DUSK, NIGHT }

/** Local clock fallback, not astronomical sunrise/sunset or weather-provider data. */
fun weatherLighting(condition: String, hour: Int): WeatherLighting {
    require(hour in 0..23)
    return when {
        condition=="Nacht" || hour < 6 || hour >= 21 -> WeatherLighting.NIGHT
        hour < 9 || hour >= 18 -> WeatherLighting.DUSK
        else -> WeatherLighting.DAY
    }
}
