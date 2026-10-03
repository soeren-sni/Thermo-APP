package de.thermo.lueftung

import org.junit.Assert.*
import org.junit.Test

class RoomClimateStoreTest {
    private fun reading(time: Long, temperature: Float = 21f) =
        RoomClimateReading(temperature, 50, 10.2f, 9.1f, time, setOf("climate-sensor"))

    @Test fun newestReadingAppearsImmediatelyAndOtherRoomIsPreserved() {
        val store = RoomClimateStore()
        store.accept("office", reading(1000))
        store.accept("living", reading(1000, 22f))
        store.accept("office", reading(2000, 23f))
        assertEquals(23f, store.readings.value.getValue("office").temperatureC)
        assertEquals(22f, store.readings.value.getValue("living").temperatureC)
        assertEquals(2000L, store.readings.value.getValue("office").measuredAtMillis)
    }

    @Test fun delayedPacketCannotReplaceNewerMeasurement() {
        val store = RoomClimateStore()
        store.accept("office", reading(2000, 23f))
        store.accept("office", reading(1000, 17f))
        assertEquals(23f, store.readings.value.getValue("office").temperatureC)
    }

    @Test fun invalidMeasurementDoesNotChangeLastGoodState() {
        val store = RoomClimateStore()
        store.accept("office", reading(1000))
        try {
            store.accept("office", reading(2000).copy(relativeHumidityPercent = 101))
            fail("Invalid humidity must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1000L, store.readings.value.getValue("office").measuredAtMillis)
    }

    @Test fun historyCallbackOnlyReceivesAcceptedNewPackets() {
        val times=mutableListOf<Long>()
        val store=RoomClimateStore { _,reading -> times.add(reading.measuredAtMillis) }
        store.accept("office",reading(2000));store.accept("office",reading(2000));store.accept("office",reading(1000));store.accept("office",reading(3000))
        assertEquals(listOf(2000L,3000L),times)
    }

    @Test fun sourceSensorSetIsCopiedBeforePublication() {
        val store = RoomClimateStore()
        val ids = mutableSetOf("sensor-a")
        store.accept("office", reading(1000).copy(sourceSensorIds = ids))
        ids.add("sensor-b")
        assertEquals(setOf("sensor-a"), store.readings.value.getValue("office").sourceSensorIds)
    }
}
