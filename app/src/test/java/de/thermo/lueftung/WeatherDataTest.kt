package de.thermo.lueftung

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WeatherDataTest {
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private fun data(code: Int = 0, clouds: Float = 0f, rain: Float = 0f, snow: Float = 0f, day: Boolean = true) =
        WeatherData(now, 12f, 80, clouds, rain, snow, code, day)
    @Test fun weatherCodesAndNightKeepPrecipitation() {
        assertEquals("Nacht",data(day=false).condition)
        assertEquals("Bewölkt",data(clouds=90f).condition)
        assertEquals("Regen",data(code=61,day=false,rain=2f).condition)
        assertEquals("Schnee",data(code=85,snow=.5f).condition)
        assertEquals("Gewitter",data(code=95,rain=3f).condition)
    }
    @Test fun precipitationScalesAndRespectsMemoryBudget() {
        assertEquals(0,WeatherEffects(1f,0f,0f).rainCount(false))
        assertEquals(0,WeatherEffects(1f,0f,0f).snowCount(false))
        assertTrue(WeatherEffects(1f,4f,1f).rainCount(false)>WeatherEffects(1f,.1f,.1f).rainCount(false))
        assertTrue(WeatherEffects(1f,4f,1f).snowCount(false)>WeatherEffects(1f,.1f,.1f).snowCount(false))
        assertEquals(64,WeatherEffects(1f,100f,100f).snowCount(true))
        assertEquals(96,WeatherEffects(1f,100f,100f).rainCount(false))
    }
    @Test fun staleAndFutureDataAreRejected() {
        assertTrue(data().fresh(now))
        assertFalse(data().copy(observedAt=now.minusSeconds(5401)).fresh(now))
        assertFalse(data().copy(observedAt=now.plusSeconds(301)).fresh(now))
    }
}
