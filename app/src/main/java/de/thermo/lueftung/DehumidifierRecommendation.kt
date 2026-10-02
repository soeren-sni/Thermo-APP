package de.thermo.lueftung

/** Manual wall readings remain transient and expire; they are not live wall sensors. */
data class WallTemperatureReading(val temperatureC: Double, val measuredAtMillis: Long)

data class DryingRoomSnapshot(
    val roomId: String,
    val temperatureC: Double,
    val humidityPercent: Double,
    val measuredAtMillis: Long,
    val wall: WallTemperatureReading? = null
)

enum class DryingReason { CONDENSATION, HIGH_SURFACE_HUMIDITY, HIGH_AIR_HUMIDITY }
data class DehumidifierSuggestion(val roomId: String, val reason: DryingReason, val estimatedAw: Double?)

object DehumidifierRecommendation {
    const val MAX_AGE_MILLIS = 15 * 60 * 1000L
    private data class Candidate(val suggestion: DehumidifierSuggestion, val tier: Int, val severity: Double)

    /** Advisory priorities only; no moving/assigning a device or sending commands. */
    fun suggest(rooms: List<DryingRoomSnapshot>, nowMillis: Long): DehumidifierSuggestion? {
        require(nowMillis > 0)
        return rooms.mapNotNull { room ->
            if (room.measuredAtMillis <= 0 || nowMillis - room.measuredAtMillis !in 0..MAX_AGE_MILLIS ||
                !room.temperatureC.isFinite() || room.temperatureC !in -80.0..80.0 ||
                !room.humidityPercent.isFinite() || room.humidityPercent !in 0.0..100.0) return@mapNotNull null
            val wall = room.wall?.takeIf {
                it.measuredAtMillis > 0 && nowMillis - it.measuredAtMillis in 0..MAX_AGE_MILLIS &&
                    it.temperatureC.isFinite() && it.temperatureC in -80.0..80.0
            }?.let { ClimateMath.wallSurface(room.temperatureC, room.humidityPercent, it.temperatureC) }
            when {
                wall?.condensationLikely == true -> Candidate(DehumidifierSuggestion(room.roomId,
                    DryingReason.CONDENSATION, wall.estimatedAw), 3, 1.0)
                wall != null && wall.estimatedAw >= .8 -> Candidate(DehumidifierSuggestion(room.roomId,
                    DryingReason.HIGH_SURFACE_HUMIDITY, wall.estimatedAw), 2, wall.estimatedAw)
                room.humidityPercent >= 65.0 -> Candidate(DehumidifierSuggestion(room.roomId,
                    DryingReason.HIGH_AIR_HUMIDITY, wall?.estimatedAw), 1, room.humidityPercent)
                else -> null
            }
        }.sortedWith(compareByDescending<Candidate> { it.tier }.thenByDescending { it.severity }
            .thenBy { it.suggestion.roomId }).firstOrNull()?.suggestion
    }
}
