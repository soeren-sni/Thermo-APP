package de.thermo.lueftung

import org.junit.Assert.*
import org.junit.Test

class DehumidifierRecommendationTest {
    private val now=10_000_000L
    private fun room(id:String,t:Double,rh:Double,wall:Double?=null,age:Long=0)=
        DryingRoomSnapshot(id,t,rh,now-age,wall?.let { WallTemperatureReading(it,now) })

    @Test fun coldWallRiskTakesPriorityOverHigherAmbientHumidity() {
        val result=DehumidifierRecommendation.suggest(listOf(
            room("workshop",20.0,55.0,12.0),room("laundry",20.0,75.0)),now)!!
        assertEquals("workshop",result.roomId)
        assertEquals(DryingReason.HIGH_SURFACE_HUMIDITY,result.reason)
    }
    @Test fun condensationTakesPriorityOverSurfaceRisk() {
        val result=DehumidifierRecommendation.suggest(listOf(
            room("fitness",20.0,60.0,15.0),room("laundry",20.0,60.0,10.0)),now)!!
        assertEquals("laundry",result.roomId)
        assertEquals(DryingReason.CONDENSATION,result.reason)
    }
    @Test fun highHumidityCanBeRankedWithoutInventingWallValue() {
        val result=DehumidifierRecommendation.suggest(listOf(
            room("fitness",18.0,68.0),room("laundry",18.0,78.0)),now)!!
        assertEquals("laundry",result.roomId)
        assertNull(result.estimatedAw)
    }
    @Test fun staleRoomAndWallMeasurementsDoNotCreateFalsePriority() {
        val staleWall=room("fitness",20.0,50.0).copy(wall=WallTemperatureReading(5.0,
            now-DehumidifierRecommendation.MAX_AGE_MILLIS-1))
        val result=DehumidifierRecommendation.suggest(listOf(staleWall,
            room("workshop",20.0,95.0,age=DehumidifierRecommendation.MAX_AGE_MILLIS+1),
            room("laundry",18.0,70.0)),now)!!
        assertEquals("laundry",result.roomId)
    }
    @Test fun normalAirWithoutWallMeasurementDoesNotGuaranteeDryWalls() {
        assertNull(DehumidifierRecommendation.suggest(listOf(room("fitness",17.0,55.0)),now))
    }
}
