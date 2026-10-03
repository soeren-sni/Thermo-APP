package de.thermo.lueftung

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One coherent display snapshot. Future device adapters compute/aggregate its metrics upstream. */
data class RoomClimateReading(
    val temperatureC: Float,
    val relativeHumidityPercent: Int,
    val dewPointC: Float,
    val absoluteHumidityGramsPerCubicMeter: Float,
    val measuredAtMillis: Long,
    val sourceSensorIds: Set<String> = emptySet(),
    val isDemo: Boolean = false
)

/** Event-driven latest values for the UI, independent of animation and timer clocks.
 * This is not a historical event log or a connected Tuya implementation.
 */
class RoomClimateStore(private val onAccepted: ((String, RoomClimateReading) -> Unit)? = null) {
    private val mutableReadings = MutableStateFlow<Map<String, RoomClimateReading>>(emptyMap())
    val readings: StateFlow<Map<String, RoomClimateReading>> = mutableReadings.asStateFlow()

    fun accept(roomId: String, reading: RoomClimateReading) {
        require(roomId.isNotBlank())
        require(reading.temperatureC.isFinite() && reading.temperatureC in -80f..80f)
        require(reading.relativeHumidityPercent in 0..100)
        require(reading.dewPointC.isFinite())
        require(reading.absoluteHumidityGramsPerCubicMeter.isFinite() &&
            reading.absoluteHumidityGramsPerCubicMeter >= 0f)
        require(reading.measuredAtMillis > 0)
        val snapshot = reading.copy(sourceSensorIds = reading.sourceSensorIds.toSet())
        var accepted=false
        mutableReadings.update { current ->
            accepted=false
            val previous = current[roomId]
            // A delayed packet must not overwrite a newer measurement.
            if (previous != null && previous.measuredAtMillis >= snapshot.measuredAtMillis) current
            else { accepted=true; current + (roomId to snapshot) }
        }
        if(accepted) onAccepted?.invoke(roomId,snapshot)
    }
}
