package de.thermo.lueftung

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherLightingTest {
    @Test fun rainAndSnowFollowClockAcrossMidnight() {
        for(condition in listOf("Regen","Schnee","Gewitter","Bewölkt","Sonnig")) {
            assertEquals(WeatherLighting.DAY,weatherLighting(condition,12))
            assertEquals(WeatherLighting.NIGHT,weatherLighting(condition,23))
            assertEquals(WeatherLighting.NIGHT,weatherLighting(condition,0))
            assertEquals(WeatherLighting.DUSK,weatherLighting(condition,19))
        }
    }
    @Test fun explicitNightSceneRemainsNightAtMidday() {
        assertEquals(WeatherLighting.NIGHT,weatherLighting("Nacht",12))
    }
    @Test(expected=IllegalArgumentException::class) fun invalidClockHourRejected() {
        weatherLighting("Regen",24)
    }
}
