package de.thermo.lueftung

import kotlin.math.exp
import kotlin.math.ln

/** Magnus approximation over water. Derived values are estimates, not wall-material moisture. */
object ClimateMath {
    fun saturationPressure(temperatureC: Double): Double {
        require(temperatureC.isFinite() && temperatureC in -80.0..80.0)
        return 6.112 * exp(17.62 * temperatureC / (243.12 + temperatureC))
    }

    fun vapourPressure(temperatureC: Double, humidityPercent: Double): Double {
        require(humidityPercent.isFinite() && humidityPercent in 0.0..100.0)
        return saturationPressure(temperatureC) * humidityPercent / 100.0
    }

    fun dewPoint(temperatureC: Double, humidityPercent: Double): Double? {
        val pressure = vapourPressure(temperatureC, humidityPercent)
        if (pressure == 0.0) return null
        val gamma = ln(pressure / 6.112)
        return 243.12 * gamma / (17.62 - gamma)
    }

    fun absoluteHumidity(temperatureC: Double, humidityPercent: Double): Double =
        216.7 * vapourPressure(temperatureC, humidityPercent) / (273.15 + temperatureC)

    fun wallSurface(temperatureC: Double, humidityPercent: Double, wallTemperatureC: Double): WallSurface {
        val saturationRatio = vapourPressure(temperatureC, humidityPercent) /
            saturationPressure(wallTemperatureC)
        return WallSurface(
            relativeHumidityPercent = saturationRatio.coerceIn(0.0, 1.0) * 100.0,
            estimatedAw = saturationRatio.coerceIn(0.0, 1.0),
            condensationLikely = saturationRatio >= 1.0
        )
    }
}

data class WallSurface(
    val relativeHumidityPercent: Double,
    val estimatedAw: Double,
    val condensationLikely: Boolean
)

enum class VentilationAdvice { NO_DRYING_POTENTIAL, WALL_MEASUREMENT_REQUIRED, OUTSIDE_DEW_POINT_TOO_HIGH, DRYING_POTENTIAL }

/** Advisory only. Margins avoid acting on small sensor fluctuations; no actuator commands. */
object DryingAdvice {
    fun ventilation(
        insideTemperatureC: Double, insideHumidityPercent: Double,
        outsideTemperatureC: Double, outsideHumidityPercent: Double,
        basement: Boolean, wallTemperatureC: Double?
    ): VentilationAdvice {
        val insideDewPoint = ClimateMath.dewPoint(insideTemperatureC, insideHumidityPercent)
        val outsideDewPoint = ClimateMath.dewPoint(outsideTemperatureC, outsideHumidityPercent)
        val difference = ClimateMath.absoluteHumidity(insideTemperatureC, insideHumidityPercent) -
            ClimateMath.absoluteHumidity(outsideTemperatureC, outsideHumidityPercent)
        if (insideDewPoint == null || difference < 0.5 ||
            (outsideDewPoint != null && insideDewPoint - outsideDewPoint < 1.0)) {
            return VentilationAdvice.NO_DRYING_POTENTIAL
        }
        if (basement && wallTemperatureC == null) return VentilationAdvice.WALL_MEASUREMENT_REQUIRED
        wallTemperatureC?.let {
            ClimateMath.saturationPressure(it) // Validate even when outdoor humidity is zero.
            if (outsideDewPoint != null && outsideDewPoint >= it - 2.0) {
                return VentilationAdvice.OUTSIDE_DEW_POINT_TOO_HIGH
            }
        }
        return VentilationAdvice.DRYING_POTENTIAL
    }
}
