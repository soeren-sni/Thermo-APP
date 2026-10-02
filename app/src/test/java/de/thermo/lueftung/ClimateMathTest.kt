package de.thermo.lueftung

import org.junit.Assert.*
import org.junit.Test

class ClimateMathTest {
    @Test fun knownRoomClimate() {
        assertEquals(9.26, ClimateMath.dewPoint(20.0, 50.0)!!, 0.03)
        assertEquals(8.62, ClimateMath.absoluteHumidity(20.0, 50.0), 0.03)
        assertNull(ClimateMath.dewPoint(20.0, 0.0))
    }

    @Test fun coldWallRaisesSurfaceRiskAndCondensationIsCapped() {
        val cool = ClimateMath.wallSurface(20.0, 60.0, 15.0)
        assertEquals(82.2, cool.relativeHumidityPercent, 0.3)
        assertEquals(0.822, cool.estimatedAw, 0.003)
        assertFalse(cool.condensationLikely)
        val cold = ClimateMath.wallSurface(20.0, 60.0, 10.0)
        assertEquals(1.0, cold.estimatedAw, 0.0)
        assertTrue(cold.condensationLikely)
    }

    @Test fun heatingAloneDoesNotLowerVapourPressureOrWallRisk() {
        val originalPressure = ClimateMath.vapourPressure(16.0, 70.0)
        val warmedRh = originalPressure / ClimateMath.saturationPressure(22.0) * 100.0
        assertTrue(warmedRh < 70.0)
        assertEquals(ClimateMath.wallSurface(16.0, 70.0, 12.0).estimatedAw,
            ClimateMath.wallSurface(22.0, warmedRh, 12.0).estimatedAw, 0.00001)
    }

    @Test fun warmHumidSummerAirCannotDryBasement() {
        assertEquals(VentilationAdvice.NO_DRYING_POTENTIAL,
            DryingAdvice.ventilation(17.0, 65.0, 28.0, 65.0, true, 14.0))
    }

    @Test fun basementRequiresWallMeasurementEvenWithDryOutsideAir() {
        assertEquals(VentilationAdvice.WALL_MEASUREMENT_REQUIRED,
            DryingAdvice.ventilation(17.0, 65.0, 5.0, 60.0, true, null))
        assertEquals(VentilationAdvice.DRYING_POTENTIAL,
            DryingAdvice.ventilation(17.0, 65.0, 5.0, 60.0, true, 14.0))
    }

    @Test fun dryAirCanStillBeTooHumidForVeryColdWall() {
        assertEquals(VentilationAdvice.OUTSIDE_DEW_POINT_TOO_HIGH,
            DryingAdvice.ventilation(22.0, 80.0, 16.0, 65.0, true, 10.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHumidityIsRejected() { ClimateMath.wallSurface(20.0, 101.0, 15.0) }
}
