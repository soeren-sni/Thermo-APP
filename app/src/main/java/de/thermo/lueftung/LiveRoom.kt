package de.thermo.lueftung

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun liveRoom(base: Room, vm: ThermoViewModel): Room {
    val stream = remember(vm, base.id) {
        vm.climate.readings.map { it[base.id] }.distinctUntilChanged()
    }
    val reading by stream.collectAsStateWithLifecycle(initialValue = null)
    val mode by vm.tuya.source.collectAsStateWithLifecycle()
    val snapshot by vm.tuya.state.collectAsStateWithLifecycle()
    val selected=if(mode==ClimateSource.TUYA_LIVE) snapshot.climate[base.id] ?: reading?.takeIf { !it.isDemo } else reading?.takeIf { it.isDemo }
    return selected?.let {
        base.copy(temp = it.temperatureC, humidity = it.relativeHumidityPercent,
            dewPoint = it.dewPointC, absHumidity = it.absoluteHumidityGramsPerCubicMeter,
            measuredAtMillis = it.measuredAtMillis, isDemo = it.isDemo)
    } ?: base
}

fun measurementTime(timestampMillis: Long): String = java.time.format.DateTimeFormatter
    .ofPattern("HH:mm:ss")
    .format(java.time.Instant.ofEpochMilli(timestampMillis).atZone(java.time.ZoneId.systemDefault()))

@Composable
fun liveOutdoor(vm:ThermoViewModel):RoomClimateReading? {
    val outside by ThermoRuntime.outdoorClimate.collectAsStateWithLifecycle()
    val snapshot by vm.tuya.state.collectAsStateWithLifecycle()
    return outside?.takeIf { snapshot.error==null && TuyaPolicy.fresh(it.measuredAtMillis,vm.dryingEvaluatedAtMillis) }
}
